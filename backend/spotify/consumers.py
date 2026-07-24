from urllib.parse import parse_qs
from uuid import UUID

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncJsonWebsocketConsumer
from django.db import transaction
from django.db.models import Q
from django.utils import timezone
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import AccessToken

from .models import DirectConversation, DirectMessage, Song, User


def iso_datetime(value):
    if value is None:
        return None
    return value.isoformat().replace("+00:00", "Z")


def public_user_data(user):
    return {
        "id": str(user.pk),
        "name": user.name,
        "avatar_url": user.avatar_url,
        "has_active_premium": user.has_active_premium,
    }


def song_card_data(song):
    if song is None:
        return None
    return {
        "id": str(song.pk),
        "title": song.title,
        "artist": public_user_data(song.artist),
        "cover_image_url": song.cover_image_url,
        "audio_url": song.audio_url,
        "duration": str(song.duration) if song.duration is not None else None,
    }


def message_data(message):
    return {
        "id": str(message.pk),
        "conversation_id": str(message.conversation_id),
        "client_message_id": str(message.client_message_id),
        "sender": public_user_data(message.sender),
        "message_type": message.message_type,
        "body": message.body,
        "song": song_card_data(message.song),
        "status": message.receipt_status,
        "delivered_at": iso_datetime(message.delivered_at),
        "read_at": iso_datetime(message.read_at),
        "created_at": iso_datetime(message.created_at),
    }


class DirectChatConsumer(AsyncJsonWebsocketConsumer):
    async def connect(self):
        token = parse_qs(self.scope["query_string"].decode()).get("token", [None])[0]
        if not token:
            headers = dict(self.scope.get("headers", []))
            authorization = headers.get(b"authorization", b"").decode()
            if authorization.startswith("Bearer "):
                token = authorization.removeprefix("Bearer ").strip()
        if not token:
            await self.close(code=4401)
            return
        try:
            access_token = AccessToken(token)
            self.user = await self.get_active_user(access_token["user_id"])
        except (TokenError, User.DoesNotExist, ValueError, KeyError):
            await self.close(code=4401)
            return

        target_id = self.scope["url_route"]["kwargs"]["user_id"]
        if self.user.pk == target_id:
            await self.close(code=4403)
            return
        try:
            self.target = await self.get_active_user(target_id)
        except User.DoesNotExist:
            await self.close(code=4404)
            return

        self.conversation = await self.get_or_create_conversation(
            self.user.pk,
            self.target.pk,
        )
        self.group_name = f"chat.{self.conversation.pk}"
        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()
        await self.send_json(
            {
                "type": "conversation.ready",
                "conversation_id": str(self.conversation.pk),
                "other_user": public_user_data(self.target),
            }
        )

        delivered_ids = await self.mark_pending_delivered(
            self.conversation.pk,
            self.user.pk,
        )
        for message_id in delivered_ids:
            await self.channel_layer.group_send(
                self.group_name,
                {
                    "type": "chat.receipt",
                    "receipt": {
                        "message_id": message_id,
                        "status": "delivered",
                        "delivered_at": iso_datetime(timezone.now()),
                        "read_at": None,
                    },
                },
            )

    async def disconnect(self, close_code):
        if hasattr(self, "group_name"):
            await self.channel_layer.group_discard(
                self.group_name,
                self.channel_name,
            )

    async def receive_json(self, content, **kwargs):
        event_type = content.get("type")
        if event_type == "message.send":
            await self.handle_message_send(content)
        elif event_type == "typing":
            await self.channel_layer.group_send(
                self.group_name,
                {
                    "type": "chat.typing",
                    "sender_id": str(self.user.pk),
                    "is_typing": bool(content.get("is_typing", False)),
                },
            )
        elif event_type in {"message.delivered", "message.read"}:
            await self.handle_receipt(content, event_type)
        else:
            await self.send_error("unsupported_event", "Unsupported event type.")

    async def handle_message_send(self, content):
        try:
            client_message_id = UUID(str(content["client_message_id"]))
        except (KeyError, TypeError, ValueError):
            await self.send_error(
                "invalid_client_message_id",
                "client_message_id must be a UUID.",
            )
            return

        message_type = content.get("message_type", "text")
        body = str(content.get("body", "")).strip()
        song_id = content.get("song_id")
        if message_type == "text" and not body:
            await self.send_error("empty_message", "Text messages require body.")
            return
        if message_type == "song" and not song_id:
            await self.send_error("missing_song", "Song messages require song_id.")
            return
        if message_type not in {"text", "song"}:
            await self.send_error("invalid_message_type", "Use text or song.")
            return

        try:
            payload = await self.create_message(
                conversation_id=self.conversation.pk,
                sender_id=self.user.pk,
                client_message_id=client_message_id,
                message_type=message_type,
                body=body,
                song_id=song_id,
            )
        except Song.DoesNotExist:
            await self.send_error(
                "song_not_found",
                "Song does not exist or is not shareable.",
            )
            return

        await self.channel_layer.group_send(
            self.group_name,
            {"type": "chat.message", "message": payload},
        )

    async def handle_receipt(self, content, event_type):
        try:
            message_id = UUID(str(content["message_id"]))
        except (KeyError, TypeError, ValueError):
            await self.send_error("invalid_message_id", "message_id must be a UUID.")
            return
        receipt = await self.update_receipt(
            conversation_id=self.conversation.pk,
            recipient_id=self.user.pk,
            message_id=message_id,
            mark_read=event_type == "message.read",
        )
        if receipt is None:
            await self.send_error(
                "message_not_found",
                "Message does not exist or receipt is not allowed.",
            )
            return
        await self.channel_layer.group_send(
            self.group_name,
            {"type": "chat.receipt", "receipt": receipt},
        )

    async def chat_message(self, event):
        message = event["message"]
        if (
            message["sender"]["id"] != str(self.user.pk)
            and message["status"] == "sent"
        ):
            receipt = await self.update_receipt(
                conversation_id=self.conversation.pk,
                recipient_id=self.user.pk,
                message_id=UUID(message["id"]),
                mark_read=False,
            )
            if receipt:
                await self.channel_layer.group_send(
                    self.group_name,
                    {"type": "chat.receipt", "receipt": receipt},
                )
        await self.send_json({"type": "message.created", "message": message})

    async def chat_receipt(self, event):
        await self.send_json({"type": "message.receipt", **event["receipt"]})

    async def chat_typing(self, event):
        if event["sender_id"] != str(self.user.pk):
            await self.send_json(
                {
                    "type": "typing",
                    "user_id": event["sender_id"],
                    "is_typing": event["is_typing"],
                }
            )

    async def send_error(self, code, detail):
        await self.send_json({"type": "error", "code": code, "detail": detail})

    @database_sync_to_async
    def get_active_user(self, user_id):
        return User.objects.get(pk=user_id, is_active=True)

    @database_sync_to_async
    def get_or_create_conversation(self, first_id, second_id):
        user_one_id, user_two_id = sorted((first_id, second_id), key=str)
        conversation, _ = DirectConversation.objects.get_or_create(
            user_one_id=user_one_id,
            user_two_id=user_two_id,
        )
        return conversation

    @database_sync_to_async
    def create_message(
        self,
        conversation_id,
        sender_id,
        client_message_id,
        message_type,
        body,
        song_id,
    ):
        with transaction.atomic():
            existing = (
                DirectMessage.objects.select_related("sender", "song__artist")
                .filter(
                    sender_id=sender_id,
                    client_message_id=client_message_id,
                )
                .first()
            )
            if existing:
                return message_data(existing)

            song = None
            if message_type == DirectMessage.MessageType.SONG:
                song = Song.objects.select_related("artist").get(
                    pk=song_id,
                    is_published=True,
                )
            message = DirectMessage.objects.create(
                conversation_id=conversation_id,
                sender_id=sender_id,
                client_message_id=client_message_id,
                message_type=message_type,
                body=body,
                song=song,
            )
            DirectConversation.objects.filter(pk=conversation_id).update(
                updated_at=timezone.now()
            )
            message = DirectMessage.objects.select_related(
                "sender",
                "song__artist",
            ).get(pk=message.pk)
            return message_data(message)

    @database_sync_to_async
    def update_receipt(
        self,
        conversation_id,
        recipient_id,
        message_id,
        mark_read,
    ):
        with transaction.atomic():
            message = (
                DirectMessage.objects.select_for_update()
                .filter(
                    pk=message_id,
                    conversation_id=conversation_id,
                )
                .exclude(sender_id=recipient_id)
                .first()
            )
            if message is None:
                return None
            now = timezone.now()
            update_fields = []
            if message.delivered_at is None:
                message.delivered_at = now
                update_fields.append("delivered_at")
            if mark_read and message.read_at is None:
                message.read_at = now
                update_fields.append("read_at")
            if update_fields:
                update_fields.append("updated_at")
                message.save(update_fields=update_fields)
            return {
                "message_id": str(message.pk),
                "status": message.receipt_status,
                "delivered_at": iso_datetime(message.delivered_at),
                "read_at": iso_datetime(message.read_at),
            }

    @database_sync_to_async
    def mark_pending_delivered(self, conversation_id, recipient_id):
        message_ids = list(
            DirectMessage.objects.filter(
                conversation_id=conversation_id,
                delivered_at__isnull=True,
            )
            .exclude(sender_id=recipient_id)
            .values_list("pk", flat=True)
        )
        if message_ids:
            DirectMessage.objects.filter(pk__in=message_ids).update(
                delivered_at=timezone.now(),
                updated_at=timezone.now(),
            )
        return [str(message_id) for message_id in message_ids]

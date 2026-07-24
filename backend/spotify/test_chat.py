import uuid

from asgiref.sync import sync_to_async
from channels.testing import WebsocketCommunicator
from django.test import TestCase, TransactionTestCase, override_settings
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import RefreshToken

from config.asgi import application

from .models import DirectConversation, DirectMessage, Song, User


def conversation_for(first, second):
    user_one, user_two = sorted((first, second), key=lambda user: str(user.pk))
    return DirectConversation.objects.create(user_one=user_one, user_two=user_two)


class ChatApiTests(TestCase):
    def setUp(self):
        self.first = User.objects.create_user(
            email="first@example.com",
            password="password123!",
            name="First User",
        )
        self.second = User.objects.create_user(
            email="second@example.com",
            password="password123!",
            name="Second User",
        )
        self.conversation = conversation_for(self.first, self.second)
        self.client = APIClient()
        self.client.force_authenticate(user=self.second)

    def test_message_history_is_paginated_and_marks_incoming_delivered(self):
        message = DirectMessage.objects.create(
            conversation=self.conversation,
            sender=self.first,
            client_message_id=uuid.uuid4(),
            body="Offline message",
        )

        response = self.client.get(
            f"/api/chat/users/{self.first.pk}/messages/?page=1&page_size=10"
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["count"], 1)
        self.assertEqual(response.data["results"][0]["body"], "Offline message")
        message.refresh_from_db()
        self.assertIsNotNone(message.delivered_at)

    def test_recipient_can_mark_message_read(self):
        message = DirectMessage.objects.create(
            conversation=self.conversation,
            sender=self.first,
            client_message_id=uuid.uuid4(),
            body="Read me",
        )

        response = self.client.post(f"/api/chat/messages/{message.pk}/read/")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["status"], "read")
        self.assertIsNotNone(response.data["read_at"])

    def test_conversation_list_contains_song_mini_card(self):
        song = Song.objects.create(
            title="Shared Song",
            artist=self.first,
            cover_image_url="https://example.com/cover.jpg",
            audio_url="https://example.com/audio.mp3",
            is_published=True,
        )
        DirectMessage.objects.create(
            conversation=self.conversation,
            sender=self.first,
            client_message_id=uuid.uuid4(),
            message_type=DirectMessage.MessageType.SONG,
            song=song,
        )

        response = self.client.get("/api/chat/conversations/?page=1&page_size=10")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        last_message = response.data["results"][0]["last_message"]
        self.assertEqual(last_message["message_type"], "song")
        self.assertEqual(last_message["song"]["id"], str(song.pk))
        self.assertEqual(last_message["song"]["audio_url"], song.audio_url)


@override_settings(
    CHANNEL_LAYERS={"default": {"BACKEND": "channels.layers.InMemoryChannelLayer"}}
)
class ChatWebSocketTests(TransactionTestCase):
    reset_sequences = True

    async def test_jwt_socket_persists_and_echoes_message(self):
        first = await sync_to_async(User.objects.create_user)(
            email="socket.first@example.com",
            password="password123!",
            name="Socket First",
        )
        second = await sync_to_async(User.objects.create_user)(
            email="socket.second@example.com",
            password="password123!",
            name="Socket Second",
        )
        token = await sync_to_async(
            lambda: str(RefreshToken.for_user(first).access_token)
        )()
        communicator = WebsocketCommunicator(
            application,
            f"/ws/chat/{second.pk}/?token={token}",
        )

        connected, _ = await communicator.connect()
        self.assertTrue(connected)
        ready = await communicator.receive_json_from()
        self.assertEqual(ready["type"], "conversation.ready")

        second_token = await sync_to_async(
            lambda: str(RefreshToken.for_user(second).access_token)
        )()
        recipient_socket = WebsocketCommunicator(
            application,
            f"/ws/chat/{first.pk}/?token={second_token}",
        )
        recipient_connected, _ = await recipient_socket.connect()
        self.assertTrue(recipient_connected)
        await recipient_socket.receive_json_from()
        await communicator.send_json_to({"type": "typing", "is_typing": True})
        typing_event = await recipient_socket.receive_json_from()
        self.assertEqual(
            typing_event,
            {
                "type": "typing",
                "user_id": str(first.pk),
                "is_typing": True,
            },
        )
        await recipient_socket.disconnect()

        client_message_id = str(uuid.uuid4())
        await communicator.send_json_to(
            {
                "type": "message.send",
                "client_message_id": client_message_id,
                "message_type": "text",
                "body": "Real-time hello",
            }
        )
        event = await communicator.receive_json_from()
        self.assertEqual(event["type"], "message.created")
        self.assertEqual(event["message"]["body"], "Real-time hello")
        self.assertEqual(event["message"]["status"], "sent")
        exists = await sync_to_async(
            DirectMessage.objects.filter(
                sender=first,
                client_message_id=client_message_id,
            ).exists
        )()
        self.assertTrue(exists)
        await communicator.disconnect()

import calendar
import logging
import random
import uuid
from datetime import datetime
from pathlib import Path
from typing import cast
from urllib.parse import quote

from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer
from django.conf import settings
from django.core.files.storage import default_storage
from django.db import transaction
from django.db.models import Count, Max, Q
from django.shortcuts import get_object_or_404
from django.utils import timezone
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import status, viewsets
from rest_framework.decorators import action
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAdminUser, IsAuthenticated
from rest_framework.exceptions import NotFound, ValidationError
from rest_framework.response import Response

from .models import (
    DirectConversation,
    DirectMessage,
    Playlist,
    PlaylistFollow,
    PlaylistSong,
    Song,
    User,
    UserFollow,
)
from .pagination import StandardResultsSetPagination
from .permissions import (
    IsArtistOrReadOnly,
    IsFollowOwnerOrStaff,
    IsPlaylistOwnerOrReadOnly,
    IsSelfOrStaff,
)
from .serializers import (
    AvatarUploadResponseSerializer,
    AvatarUploadSerializer,
    AddPlaylistSongSerializer,
    DirectConversationSerializer,
    DirectMessageSerializer,
    PlaylistFollowSerializer,
    PlaylistSerializer,
    PlaylistNextSongSerializer,
    PlaylistVisibilitySerializer,
    PopularSongSerializer,
    PublicUserProfileSerializer,
    RandomNextSongSerializer,
    TopArtistSerializer,
    SongSerializer,
    SongSearchQuerySerializer,
    SubscriptionResponseSerializer,
    SubscriptionSerializer,
    UserSerializer,
    UserFollowSerializer,
    UserSearchQuerySerializer,
)

logger = logging.getLogger(__name__)


def add_calendar_months(value: datetime, months: int) -> datetime:
    month_index = value.month - 1 + months
    year = value.year + month_index // 12
    month = month_index % 12 + 1
    day = min(value.day, calendar.monthrange(year, month)[1])
    return value.replace(year=year, month=month, day=day)


class UserViewSet(viewsets.ModelViewSet):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    permission_classes = (IsAuthenticated, IsSelfOrStaff)
    pagination_class = StandardResultsSetPagination

    def get_queryset(self):
        user = cast(User, self.request.user)
        if user.is_staff:
            return User.objects.order_by("-created_at", "pk")
        return User.objects.filter(pk=user.pk).order_by("-created_at", "pk")

    def get_permissions(self):
        if self.action == "create":
            return [AllowAny()]
        if self.action == "list":
            return [IsAdminUser()]
        if self.action in {
            "me",
            "search",
            "follow",
            "followers",
            "following",
            "playlists",
            "top_artists",
        }:
            return [IsAuthenticated()]
        return super().get_permissions()

    @action(detail=False, methods=("get",), url_path="me")
    def me(self, request):
        user = cast(User, request.user)
        return Response(self.get_serializer(user).data, status=status.HTTP_200_OK)

    @extend_schema(
        parameters=[UserSearchQuerySerializer],
        responses={200: PublicUserProfileSerializer(many=True)},
    )
    @action(detail=False, methods=("get",), url_path="search")
    def search(self, request):
        query_serializer = UserSearchQuerySerializer(data=request.query_params)
        query_serializer.is_valid(raise_exception=True)
        query = query_serializer.validated_data["q"]
        users = User.objects.filter(
            is_active=True,
            name__icontains=query,
        ).order_by("name", "pk")
        page = self.paginate_queryset(users)
        if page is not None:
            return self.get_paginated_response(
                PublicUserProfileSerializer(page, many=True).data
            )
        return Response(PublicUserProfileSerializer(users, many=True).data)

    @extend_schema(
        request=None,
        responses={200: UserFollowSerializer, 201: UserFollowSerializer},
    )
    @action(detail=True, methods=("post",), url_path="follow")
    def follow(self, request, pk=None):
        follower = cast(User, request.user)
        following = get_object_or_404(User, pk=pk, is_active=True)
        if follower.pk == following.pk:
            raise ValidationError("You cannot follow yourself.")
        relationship, created = UserFollow.objects.get_or_create(
            follower=follower,
            following=following,
        )
        return Response(
            UserFollowSerializer(relationship).data,
            status=status.HTTP_201_CREATED if created else status.HTTP_200_OK,
        )

    @extend_schema(request=None, responses={204: None})
    @follow.mapping.delete
    def unfollow(self, request, pk=None):
        follower = cast(User, request.user)
        following = get_object_or_404(User, pk=pk, is_active=True)
        UserFollow.objects.filter(
            follower=follower,
            following=following,
        ).delete()
        return Response(status=status.HTTP_204_NO_CONTENT)

    @extend_schema(responses={200: PublicUserProfileSerializer(many=True)})
    @action(detail=True, methods=("get",), url_path="followers")
    def followers(self, request, pk=None):
        target = get_object_or_404(User, pk=pk, is_active=True)
        users = User.objects.filter(
            is_active=True,
            following_relationships__following=target,
        ).order_by("name", "pk")
        page = self.paginate_queryset(users)
        if page is not None:
            return self.get_paginated_response(
                PublicUserProfileSerializer(page, many=True).data
            )
        return Response(PublicUserProfileSerializer(users, many=True).data)

    @extend_schema(responses={200: PublicUserProfileSerializer(many=True)})
    @action(detail=True, methods=("get",), url_path="following")
    def following(self, request, pk=None):
        target = get_object_or_404(User, pk=pk, is_active=True)
        users = User.objects.filter(
            is_active=True,
            follower_relationships__follower=target,
        ).order_by("name", "pk")
        page = self.paginate_queryset(users)
        if page is not None:
            return self.get_paginated_response(
                PublicUserProfileSerializer(page, many=True).data
            )
        return Response(PublicUserProfileSerializer(users, many=True).data)

    @extend_schema(responses={200: PlaylistSerializer(many=True)})
    @action(detail=True, methods=("get",), url_path="playlists")
    def playlists(self, request, pk=None):
        target = get_object_or_404(User, pk=pk, is_active=True)
        playlists = (
            Playlist.objects.filter(owner=target, is_public=True)
            .select_related("owner")
            .annotate(
                follower_count=Count("followers", distinct=True),
                song_count=Count("song_entries", distinct=True),
            )
            .order_by("-created_at", "pk")
        )
        page = self.paginate_queryset(playlists)
        if page is not None:
            return self.get_paginated_response(
                PlaylistSerializer(page, many=True).data
            )
        return Response(PlaylistSerializer(playlists, many=True).data)

    @extend_schema(responses={200: TopArtistSerializer(many=True)})
    @action(detail=False, methods=("get",), url_path="top-artists")
    def top_artists(self, request):
        artists = (
            User.objects.filter(is_active=True, songs__is_published=True)
            .annotate(
                follower_count=Count(
                    "follower_relationships",
                    distinct=True,
                )
            )
            .order_by("-follower_count", "name", "pk")
            .distinct()[:10]
        )
        return Response(
            TopArtistSerializer(artists, many=True).data,
            status=status.HTTP_200_OK,
        )

    @extend_schema(
        request=AvatarUploadSerializer,
        responses={200: AvatarUploadResponseSerializer},
    )
    @action(
        detail=False,
        methods=("post",),
        url_path="avatar",
        parser_classes=(MultiPartParser, FormParser),
    )
    def avatar(self, request):
        received_file = request.FILES.get("avatar")
        if received_file is None:
            logger.warning("Avatar upload received without an avatar file")
        else:
            logger.warning(
                "Avatar upload received: extension=%s, content_type=%s, filename=%s",
                Path(received_file.name).suffix.lower() or "<none>",
                received_file.content_type or "<none>",
                received_file.name,
            )

        serializer = AvatarUploadSerializer(
            data=request.data,
            context=self.get_serializer_context(),
        )
        serializer.is_valid(raise_exception=True)
        uploaded_file = serializer.validated_data["avatar"]
        extension_by_type = {
            "image/jpeg": ".jpg",
            "image/png": ".png",
            "image/webp": ".webp",
        }
        extension = extension_by_type[uploaded_file.content_type]
        logger.info("Avatar upload normalized extension: %s", extension)
        user = cast(User, request.user)
        object_name = f"avatars/{user.pk}/{uuid.uuid4().hex}{extension}"
        saved_name = default_storage.save(object_name, uploaded_file)
        public_endpoint = settings.MINIO_PUBLIC_ENDPOINT.strip().rstrip("/")
        if not public_endpoint.startswith(("http://", "https://")):
            public_endpoint = f"http://{public_endpoint}"
        public_url = (
            f"{public_endpoint}/{settings.MINIO_BUCKET}/{quote(saved_name, safe='/')}"
        )
        user.avatar_url = public_url
        user.save(update_fields=["avatar_url", "updated_at"])
        return Response({"avatar_url": public_url}, status=status.HTTP_200_OK)

    @extend_schema(
        request=SubscriptionSerializer,
        responses={200: SubscriptionResponseSerializer},
    )
    @action(detail=False, methods=("post",), url_path="subscription")
    def subscription(self, request):
        serializer = SubscriptionSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        months = int(serializer.validated_data["months"])

        with transaction.atomic():
            user = User.objects.select_for_update().get(pk=request.user.pk)
            now = timezone.now()
            starts_at = (
                user.premium_expires_at
                if user.premium_expires_at and user.premium_expires_at > now
                else now
            )
            user.premium_expires_at = add_calendar_months(starts_at, months)
            user.save(update_fields=["premium_expires_at", "updated_at"])

        return Response(
            {
                "months_added": months,
                "premium_expires_at": user.premium_expires_at,
                "has_active_premium": user.has_active_premium,
            },
            status=status.HTTP_200_OK,
        )


class SongViewSet(viewsets.ModelViewSet):
    queryset = Song.objects.select_related("artist").all()
    serializer_class = SongSerializer
    permission_classes = (IsAuthenticated, IsArtistOrReadOnly)
    pagination_class = StandardResultsSetPagination

    def get_queryset(self):
        user = cast(User, self.request.user)
        if user.is_staff:
            return Song.objects.select_related("artist").order_by("-created_at", "pk")
        return Song.objects.select_related("artist").filter(
            Q(is_published=True) | Q(artist=user)
        ).order_by("-created_at", "pk")

    def perform_create(self, serializer):
        serializer.save(artist=cast(User, self.request.user))

    @extend_schema(responses={200: SongSerializer(many=True)})
    @action(detail=False, methods=("get",), url_path="recent")
    def recent(self, request):
        songs = self.get_queryset().order_by("-created_at", "pk")[:10]
        return Response(
            self.get_serializer(songs, many=True).data,
            status=status.HTTP_200_OK,
        )

    @extend_schema(responses={200: PopularSongSerializer(many=True)})
    @action(detail=False, methods=("get",), url_path="popular")
    def popular(self, request):
        songs = (
            Song.objects.filter(is_published=True)
            .select_related("artist")
            .annotate(
                like_count=Count(
                    "playlist_entries",
                    filter=Q(playlist_entries__playlist__is_liked=True),
                )
            )
            .filter(like_count__gt=0)
            .order_by("-like_count", "-created_at", "pk")[:10]
        )
        return Response(
            PopularSongSerializer(songs, many=True).data,
            status=status.HTTP_200_OK,
        )

    @extend_schema(
        parameters=[SongSearchQuerySerializer],
        responses={200: SongSerializer(many=True)},
    )
    @action(detail=False, methods=("get",), url_path="search")
    def search(self, request):
        query_serializer = SongSearchQuerySerializer(data=request.query_params)
        query_serializer.is_valid(raise_exception=True)
        query = query_serializer.validated_data["q"]
        songs = self.get_queryset().filter(
            Q(title__icontains=query) | Q(artist__name__icontains=query)
        )
        page = self.paginate_queryset(songs)
        if page is not None:
            return self.get_paginated_response(
                self.get_serializer(page, many=True).data
            )
        return Response(self.get_serializer(songs, many=True).data)

    @extend_schema(
        request=RandomNextSongSerializer,
        responses={200: SongSerializer},
    )
    @action(detail=False, methods=("post",), url_path="random-next")
    def random_next(self, request):
        serializer = RandomNextSongSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        current_song_id = serializer.validated_data["song_id"]
        songs = list(self.get_queryset().exclude(pk=current_song_id))
        if not songs:
            raise NotFound("No other accessible song is available.")
        song = random.choice(songs)
        return Response(self.get_serializer(song).data, status=status.HTTP_200_OK)


class PlaylistViewSet(viewsets.ModelViewSet):
    queryset = Playlist.objects.select_related("owner").prefetch_related(
        "song_entries__song"
    )
    serializer_class = PlaylistSerializer
    permission_classes = (IsAuthenticated, IsPlaylistOwnerOrReadOnly)
    pagination_class = StandardResultsSetPagination

    def get_queryset(self):
        user = cast(User, self.request.user)
        queryset = Playlist.objects.select_related("owner").prefetch_related(
            "song_entries__song"
        )
        if not user.is_staff:
            queryset = queryset.filter(Q(is_public=True) | Q(owner=user))
        return (
            queryset.annotate(
                follower_count=Count("followers", distinct=True),
                song_count=Count("song_entries", distinct=True),
            )
            .order_by("-created_at", "pk")
        )

    def perform_create(self, serializer):
        serializer.save(owner=cast(User, self.request.user))

    @extend_schema(responses={200: PlaylistSerializer(many=True)})
    @action(detail=False, methods=("get",), url_path="me")
    def me(self, request):
        user = cast(User, request.user)
        playlists = (
            Playlist.objects.filter(owner=user)
            .select_related("owner")
            .prefetch_related("song_entries__song")
            .annotate(
                follower_count=Count("followers", distinct=True),
                song_count=Count("song_entries", distinct=True),
            )
            .order_by("-created_at", "pk")
        )
        page = self.paginate_queryset(playlists)
        if page is not None:
            return self.get_paginated_response(
                self.get_serializer(page, many=True).data
            )
        return Response(self.get_serializer(playlists, many=True).data)

    def perform_destroy(self, instance):
        if instance.is_liked:
            raise ValidationError("The Liked Songs playlist cannot be deleted.")
        instance.delete()

    @extend_schema(
        request=PlaylistVisibilitySerializer,
        responses={200: PlaylistSerializer},
    )
    @action(detail=True, methods=("patch",), url_path="visibility")
    def visibility(self, request, pk=None):
        input_serializer = PlaylistVisibilitySerializer(data=request.data)
        input_serializer.is_valid(raise_exception=True)
        playlist = self.get_object()
        is_public = input_serializer.validated_data["is_public"]
        if playlist.is_liked and is_public:
            raise ValidationError("The Liked Songs playlist must remain private.")
        playlist.is_public = is_public
        playlist.save(update_fields=["is_public", "updated_at"])
        refreshed = self.get_queryset().get(pk=playlist.pk)
        return Response(
            self.get_serializer(refreshed).data,
            status=status.HTTP_200_OK,
        )

    @extend_schema(responses={200: SongSerializer(many=True)})
    @action(detail=True, methods=("get",), url_path="songs")
    def songs(self, request, pk=None):
        playlist = self.get_object()
        songs = (
            Song.objects.filter(playlist_entries__playlist=playlist)
            .select_related("artist")
            .order_by(
                "playlist_entries__position",
                "playlist_entries__created_at",
                "pk",
            )
        )
        page = self.paginate_queryset(songs)
        if page is not None:
            return self.get_paginated_response(SongSerializer(page, many=True).data)
        return Response(SongSerializer(songs, many=True).data)

    @extend_schema(
        request=AddPlaylistSongSerializer,
        responses={201: SongSerializer},
    )
    @songs.mapping.post
    def add_song(self, request, pk=None):
        input_serializer = AddPlaylistSongSerializer(data=request.data)
        input_serializer.is_valid(raise_exception=True)
        playlist = self.get_object()
        user = cast(User, request.user)
        song_id = input_serializer.validated_data["song_id"]
        songs = Song.objects.filter(pk=song_id)
        if not user.is_staff:
            songs = songs.filter(Q(is_published=True) | Q(artist=user))
        song = songs.first()
        if song is None:
            raise NotFound("Song does not exist or is not accessible.")

        with transaction.atomic():
            Playlist.objects.select_for_update().get(pk=playlist.pk)
            if PlaylistSong.objects.filter(playlist=playlist, song=song).exists():
                raise ValidationError({"song_id": "Song is already in this playlist."})
            last_position = (
                PlaylistSong.objects.filter(playlist=playlist).aggregate(
                    maximum=Max("position")
                )["maximum"]
            )
            PlaylistSong.objects.create(
                playlist=playlist,
                song=song,
                position=0 if last_position is None else last_position + 1,
            )
        return Response(SongSerializer(song).data, status=status.HTTP_201_CREATED)

    @extend_schema(
        parameters=[
            OpenApiParameter(
                name="song_id",
                type=OpenApiTypes.UUID,
                location=OpenApiParameter.PATH,
                description="UUID of the song to remove from this playlist.",
            )
        ],
        request=None,
        responses={204: None},
    )
    @action(
        detail=True,
        methods=("delete",),
        url_path=r"songs/(?P<song_id>[^/.]+)",
    )
    def remove_song(self, request, pk=None, song_id=None):
        playlist = self.get_object()
        deleted, _ = PlaylistSong.objects.filter(
            playlist=playlist,
            song_id=song_id,
        ).delete()
        if not deleted:
            raise NotFound("Song is not in this playlist.")
        return Response(status=status.HTTP_204_NO_CONTENT)

    @extend_schema(
        request=PlaylistNextSongSerializer,
        responses={200: SongSerializer},
    )
    @action(detail=True, methods=("post",), url_path="next-song")
    def next_song(self, request, pk=None):
        input_serializer = PlaylistNextSongSerializer(data=request.data)
        input_serializer.is_valid(raise_exception=True)
        playlist = self.get_object()
        entries = list(
            PlaylistSong.objects.filter(playlist=playlist)
            .select_related("song", "song__artist")
            .order_by("position", "created_at")
        )
        if not entries:
            raise NotFound("Playlist has no songs.")

        current_song_id = input_serializer.validated_data["song_id"]
        shuffle = input_serializer.validated_data["shuffle"]
        if current_song_id is None:
            next_entry = random.choice(entries) if shuffle else entries[0]
        else:
            current_index = next(
                (
                    index
                    for index, entry in enumerate(entries)
                    if entry.song_id == current_song_id
                ),
                None,
            )
            if current_index is None:
                raise ValidationError(
                    {"song_id": "The current song is not in this playlist."}
                )
            if shuffle and len(entries) > 1:
                next_entry = random.choice(
                    [
                        entry
                        for entry in entries
                        if entry.song_id != current_song_id
                    ]
                )
            elif shuffle:
                next_entry = entries[0]
            else:
                next_entry = entries[(current_index + 1) % len(entries)]

        return Response(
            SongSerializer(next_entry.song).data,
            status=status.HTTP_200_OK,
        )


class PlaylistFollowViewSet(viewsets.ModelViewSet):
    queryset = PlaylistFollow.objects.select_related("user", "playlist")
    serializer_class = PlaylistFollowSerializer
    permission_classes = (IsAuthenticated, IsFollowOwnerOrStaff)

    def get_queryset(self):
        user = cast(User, self.request.user)
        queryset = PlaylistFollow.objects.select_related("user", "playlist")
        if user.is_staff:
            return queryset
        return queryset.filter(user=user)

    def perform_create(self, serializer):
        serializer.save(user=cast(User, self.request.user))


class ChatViewSet(viewsets.ViewSet):
    permission_classes = (IsAuthenticated,)
    pagination_class = StandardResultsSetPagination

    @property
    def paginator(self):
        if not hasattr(self, "_paginator"):
            self._paginator = self.pagination_class()
        return self._paginator

    def paginate_queryset(self, queryset):
        return self.paginator.paginate_queryset(queryset, self.request, view=self)

    def get_paginated_response(self, data):
        return self.paginator.get_paginated_response(data)

    @extend_schema(responses={200: DirectConversationSerializer(many=True)})
    @action(detail=False, methods=("get",), url_path="conversations")
    def conversations(self, request):
        user = cast(User, request.user)
        conversations = (
            DirectConversation.objects.filter(Q(user_one=user) | Q(user_two=user))
            .select_related("user_one", "user_two")
            .order_by("-updated_at", "-id")
        )
        page = self.paginate_queryset(conversations)
        serializer = DirectConversationSerializer(
            page,
            many=True,
            context={"request": request},
        )
        return self.get_paginated_response(serializer.data)

    @extend_schema(
        parameters=[
            OpenApiParameter(
                name="user_id",
                type=OpenApiTypes.UUID,
                location=OpenApiParameter.PATH,
                description="UUID of the other user in the direct conversation.",
            )
        ],
        responses={200: DirectMessageSerializer(many=True)},
    )
    @action(
        detail=False,
        methods=("get",),
        url_path=r"users/(?P<user_id>[^/.]+)/messages",
    )
    def messages(self, request, user_id=None):
        user = cast(User, request.user)
        target = get_object_or_404(User, pk=user_id, is_active=True)
        if user.pk == target.pk:
            raise ValidationError("A direct conversation requires another user.")
        first_id, second_id = sorted((user.pk, target.pk), key=str)
        conversation, _ = DirectConversation.objects.get_or_create(
            user_one_id=first_id,
            user_two_id=second_id,
        )
        now = timezone.now()
        pending_message_ids = list(
            DirectMessage.objects.filter(
                conversation=conversation,
                delivered_at__isnull=True,
            )
            .exclude(sender=user)
            .values_list("pk", flat=True)
        )
        DirectMessage.objects.filter(
            conversation=conversation,
            delivered_at__isnull=True,
        ).exclude(sender=user).update(delivered_at=now, updated_at=now)
        channel_layer = get_channel_layer()
        for message_id in pending_message_ids:
            async_to_sync(channel_layer.group_send)(
                f"chat.{conversation.pk}",
                {
                    "type": "chat.receipt",
                    "receipt": {
                        "message_id": str(message_id),
                        "status": "delivered",
                        "delivered_at": now.isoformat().replace("+00:00", "Z"),
                        "read_at": None,
                    },
                },
            )
        messages = (
            DirectMessage.objects.filter(conversation=conversation)
            .select_related("sender", "song__artist")
            .order_by("-created_at", "-id")
        )
        page = self.paginate_queryset(messages)
        return self.get_paginated_response(
            DirectMessageSerializer(page, many=True).data
        )

    @extend_schema(
        parameters=[
            OpenApiParameter(
                name="message_id",
                type=OpenApiTypes.UUID,
                location=OpenApiParameter.PATH,
                description="UUID of the received message to mark as read.",
            )
        ],
        request=None,
        responses={200: DirectMessageSerializer},
    )
    @action(
        detail=False,
        methods=("post",),
        url_path=r"messages/(?P<message_id>[^/.]+)/read",
    )
    def read_message(self, request, message_id=None):
        user = cast(User, request.user)
        message = get_object_or_404(
            DirectMessage.objects.select_related("sender", "song__artist"),
            pk=message_id,
            conversation__in=DirectConversation.objects.filter(
                Q(user_one=user) | Q(user_two=user)
            ),
        )
        if message.sender_id == user.pk:
            raise ValidationError("A sender cannot mark their own message as read.")
        now = timezone.now()
        if message.delivered_at is None:
            message.delivered_at = now
        if message.read_at is None:
            message.read_at = now
        message.save(update_fields=["delivered_at", "read_at", "updated_at"])
        async_to_sync(get_channel_layer().group_send)(
            f"chat.{message.conversation_id}",
            {
                "type": "chat.receipt",
                "receipt": {
                    "message_id": str(message.pk),
                    "status": "read",
                    "delivered_at": message.delivered_at.isoformat().replace(
                        "+00:00", "Z"
                    ),
                    "read_at": message.read_at.isoformat().replace("+00:00", "Z"),
                },
            },
        )
        return Response(DirectMessageSerializer(message).data)

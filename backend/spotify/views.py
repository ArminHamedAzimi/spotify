import calendar
import logging
import uuid
from datetime import datetime
from pathlib import Path
from typing import cast
from urllib.parse import quote

from django.conf import settings
from django.core.files.storage import default_storage
from django.db import transaction
from django.db.models import Count, Q
from django.utils import timezone
from drf_spectacular.utils import extend_schema
from rest_framework import status, viewsets
from rest_framework.decorators import action
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import AllowAny, IsAdminUser, IsAuthenticated
from rest_framework.response import Response

from .models import Playlist, PlaylistFollow, Song, User
from .permissions import (
    IsArtistOrReadOnly,
    IsFollowOwnerOrStaff,
    IsPlaylistOwnerOrReadOnly,
    IsSelfOrStaff,
)
from .serializers import (
    AvatarUploadResponseSerializer,
    AvatarUploadSerializer,
    PlaylistFollowSerializer,
    PlaylistSerializer,
    SongSerializer,
    SubscriptionResponseSerializer,
    SubscriptionSerializer,
    UserSerializer,
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

    def get_queryset(self):
        user = cast(User, self.request.user)
        if user.is_staff:
            return User.objects.all()
        return User.objects.filter(pk=user.pk)

    def get_permissions(self):
        if self.action == "create":
            return [AllowAny()]
        if self.action == "list":
            return [IsAdminUser()]
        if self.action == "me":
            return [IsAuthenticated()]
        return super().get_permissions()

    @action(detail=False, methods=("get",), url_path="me")
    def me(self, request):
        user = cast(User, request.user)
        return Response(self.get_serializer(user).data, status=status.HTTP_200_OK)

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

    def get_queryset(self):
        user = cast(User, self.request.user)
        if user.is_staff:
            return Song.objects.select_related("artist").all()
        return Song.objects.select_related("artist").filter(
            Q(is_published=True) | Q(artist=user)
        )

    def perform_create(self, serializer):
        serializer.save(artist=cast(User, self.request.user))


class PlaylistViewSet(viewsets.ModelViewSet):
    queryset = Playlist.objects.select_related("owner").prefetch_related("songs")
    serializer_class = PlaylistSerializer
    permission_classes = (IsAuthenticated, IsPlaylistOwnerOrReadOnly)

    def get_queryset(self):
        user = cast(User, self.request.user)
        queryset = Playlist.objects.select_related("owner").prefetch_related("songs")
        if not user.is_staff:
            queryset = queryset.filter(Q(is_public=True) | Q(owner=user))
        return queryset.annotate(follower_count=Count("followers", distinct=True))

    def perform_create(self, serializer):
        serializer.save(owner=cast(User, self.request.user))


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

import logging
import uuid
from pathlib import Path
from typing import cast
from urllib.parse import quote

from django.conf import settings
from django.core.files.storage import default_storage
from django.db.models import Count, Q
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
    UserSerializer,
)

logger = logging.getLogger(__name__)


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

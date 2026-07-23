from typing import cast

from django.db.models import Count, Q
from rest_framework import viewsets
from rest_framework.permissions import AllowAny, IsAuthenticated

from .models import Playlist, PlaylistFollow, Song, User
from .permissions import (
    IsArtistOrReadOnly,
    IsFollowOwnerOrStaff,
    IsPlaylistOwnerOrReadOnly,
    IsSelfOrStaff,
)
from .serializers import (
    PlaylistFollowSerializer,
    PlaylistSerializer,
    SongSerializer,
    UserSerializer,
)


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
        return super().get_permissions()


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

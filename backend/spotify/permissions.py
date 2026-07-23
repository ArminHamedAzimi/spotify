from typing import cast

from rest_framework.permissions import SAFE_METHODS, BasePermission

from .models import User


class IsSelfOrStaff(BasePermission):
    def has_object_permission(self, request, view, obj):
        user = cast(User, request.user)
        return user.is_staff or obj.pk == user.pk


class IsArtistOrReadOnly(BasePermission):
    def has_object_permission(self, request, view, obj):
        user = cast(User, request.user)
        if request.method in SAFE_METHODS:
            return obj.is_published or obj.artist_id == user.id or user.is_staff
        return obj.artist_id == user.id or user.is_staff


class IsPlaylistOwnerOrReadOnly(BasePermission):
    def has_object_permission(self, request, view, obj):
        user = cast(User, request.user)
        if request.method in SAFE_METHODS:
            return obj.is_public or obj.owner_id == user.id or user.is_staff
        return obj.owner_id == user.id or user.is_staff


class IsFollowOwnerOrStaff(BasePermission):
    def has_object_permission(self, request, view, obj):
        user = cast(User, request.user)
        return obj.user_id == user.id or user.is_staff

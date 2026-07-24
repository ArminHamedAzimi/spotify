from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as DjangoUserAdmin

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


@admin.register(User)
class UserAdmin(DjangoUserAdmin):
    model = User
    ordering = ("email",)
    list_display = ("email", "name", "is_staff", "is_active", "premium_expires_at")
    list_filter = ("is_staff", "is_superuser", "is_active")
    search_fields = ("email", "name")
    fieldsets = (
        (None, {"fields": ("email", "password")}),
        ("Profile", {"fields": ("name", "avatar_url", "premium_expires_at")}),
        (
            "Permissions",
            {
                "fields": (
                    "is_active",
                    "is_staff",
                    "is_superuser",
                    "groups",
                    "user_permissions",
                )
            },
        ),
        (
            "Important dates",
            {"fields": ("last_login", "date_joined", "created_at", "updated_at")},
        ),
    )
    readonly_fields = ("last_login", "date_joined", "created_at", "updated_at")
    add_fieldsets = (
        (
            None,
            {
                "classes": ("wide",),
                "fields": (
                    "email",
                    "name",
                    "password1",
                    "password2",
                    "is_staff",
                    "is_active",
                ),
            },
        ),
    )


@admin.register(Song)
class SongAdmin(admin.ModelAdmin):
    list_display = ("title", "artist", "duration", "is_published", "created_at")
    list_filter = ("is_published", "created_at")
    search_fields = ("title", "artist__name", "artist__email")
    autocomplete_fields = ("artist",)
    readonly_fields = ("created_at", "updated_at")


class PlaylistFollowInline(admin.TabularInline):
    model = PlaylistFollow
    extra = 0
    autocomplete_fields = ("user",)


class PlaylistSongInline(admin.TabularInline):
    model = PlaylistSong
    extra = 0
    autocomplete_fields = ("song",)
    ordering = ("position",)


@admin.register(Playlist)
class PlaylistAdmin(admin.ModelAdmin):
    list_display = ("title", "owner", "is_public", "is_liked", "created_at")
    list_filter = ("is_public", "is_liked", "created_at")
    search_fields = ("title", "owner__name", "owner__email")
    autocomplete_fields = ("owner",)
    readonly_fields = ("created_at", "updated_at")
    inlines = (PlaylistSongInline, PlaylistFollowInline)


@admin.register(PlaylistFollow)
class PlaylistFollowAdmin(admin.ModelAdmin):
    list_display = ("user", "playlist", "created_at")
    search_fields = ("user__email", "playlist__title")
    autocomplete_fields = ("user", "playlist")
    readonly_fields = ("created_at", "updated_at")


@admin.register(PlaylistSong)
class PlaylistSongAdmin(admin.ModelAdmin):
    list_display = ("playlist", "song", "position", "created_at")
    search_fields = ("playlist__title", "song__title")
    autocomplete_fields = ("playlist", "song")
    ordering = ("playlist", "position")
    readonly_fields = ("created_at", "updated_at")


@admin.register(UserFollow)
class UserFollowAdmin(admin.ModelAdmin):
    list_display = ("follower", "following", "created_at")
    search_fields = (
        "follower__name",
        "follower__email",
        "following__name",
        "following__email",
    )
    autocomplete_fields = ("follower", "following")
    readonly_fields = ("created_at", "updated_at")


@admin.register(DirectConversation)
class DirectConversationAdmin(admin.ModelAdmin):
    list_display = ("user_one", "user_two", "updated_at")
    search_fields = (
        "user_one__name",
        "user_one__email",
        "user_two__name",
        "user_two__email",
    )
    autocomplete_fields = ("user_one", "user_two")
    readonly_fields = ("created_at", "updated_at")


@admin.register(DirectMessage)
class DirectMessageAdmin(admin.ModelAdmin):
    list_display = (
        "sender",
        "message_type",
        "conversation",
        "delivered_at",
        "read_at",
        "created_at",
    )
    list_filter = ("message_type", "created_at", "delivered_at", "read_at")
    search_fields = ("sender__name", "sender__email", "body")
    autocomplete_fields = ("conversation", "sender", "song")
    readonly_fields = ("created_at", "updated_at")

from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as DjangoUserAdmin

from .models import Playlist, PlaylistFollow, Song, User


@admin.register(User)
class UserAdmin(DjangoUserAdmin):
    model = User
    ordering = ("email",)
    list_display = ("email", "name", "is_staff", "is_active", "premium_time_remaining")
    list_filter = ("is_staff", "is_superuser", "is_active")
    search_fields = ("email", "name")
    fieldsets = (
        (None, {"fields": ("email", "password")}),
        ("Profile", {"fields": ("name", "avatar_url", "premium_time_remaining")}),
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


@admin.register(Playlist)
class PlaylistAdmin(admin.ModelAdmin):
    list_display = ("title", "owner", "is_public", "created_at")
    list_filter = ("is_public", "created_at")
    search_fields = ("title", "owner__name", "owner__email")
    autocomplete_fields = ("owner", "songs")
    readonly_fields = ("created_at", "updated_at")
    inlines = (PlaylistFollowInline,)


@admin.register(PlaylistFollow)
class PlaylistFollowAdmin(admin.ModelAdmin):
    list_display = ("user", "playlist", "created_at")
    search_fields = ("user__email", "playlist__title")
    autocomplete_fields = ("user", "playlist")
    readonly_fields = ("created_at", "updated_at")

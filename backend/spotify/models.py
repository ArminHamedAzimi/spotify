import uuid
from datetime import timedelta
from typing import ClassVar

from django.conf import settings
from django.contrib.auth.models import AbstractUser
from django.core.exceptions import ValidationError
from django.db import models
from django.db.models import Q
from django.utils import timezone

from .managers import UserManager


class TimeStampedModel(models.Model):
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class User(AbstractUser):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    username = None
    name = models.CharField(max_length=150)
    email = models.EmailField(unique=True)
    premium_expires_at = models.DateTimeField(null=True, blank=True)
    avatar_url = models.URLField(max_length=2048, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    USERNAME_FIELD = "email"
    REQUIRED_FIELDS = ["name"]
    objects: ClassVar[UserManager] = UserManager()

    class Meta:
        ordering = ["-created_at"]
        constraints = [
            models.CheckConstraint(
                condition=~Q(name=""),
                name="user_name_not_empty",
            ),
        ]

    def __str__(self):
        return self.email

    @property
    def has_active_premium(self) -> bool:
        return (
            self.premium_expires_at is not None
            and self.premium_expires_at > timezone.now()
        )


class Song(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    title = models.CharField(max_length=255)
    artist = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="songs",
    )
    cover_image_url = models.URLField(max_length=2048)
    audio_url = models.URLField(max_length=2048)
    duration = models.DurationField(null=True, blank=True)
    is_published = models.BooleanField(default=False)

    class Meta:
        ordering = ["-created_at"]
        constraints = [
            models.CheckConstraint(condition=~Q(title=""), name="song_title_not_empty"),
            models.CheckConstraint(
                condition=Q(duration__isnull=True) | Q(duration__gte=timedelta(0)),
                name="song_duration_non_negative",
            ),
            models.UniqueConstraint(
                fields=["artist", "title"],
                name="unique_song_title_per_artist",
            ),
        ]

    def __str__(self):
        return f"{self.title} — {self.artist.name}"


class Playlist(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="playlists",
    )
    title = models.CharField(max_length=255)
    description = models.TextField(blank=True)
    is_public = models.BooleanField(default=False)
    is_liked = models.BooleanField(default=False)
    songs = models.ManyToManyField(
        Song,
        through="PlaylistSong",
        related_name="playlists",
        blank=True,
    )
    followers = models.ManyToManyField(
        settings.AUTH_USER_MODEL,
        through="PlaylistFollow",
        related_name="followed_playlists",
        blank=True,
    )

    class Meta:
        ordering = ["-created_at"]
        constraints = [
            models.CheckConstraint(
                condition=~Q(title=""),
                name="playlist_title_not_empty",
            ),
            models.UniqueConstraint(
                fields=["owner", "title"],
                name="unique_playlist_title_per_owner",
            ),
            models.UniqueConstraint(
                fields=["owner"],
                condition=Q(is_liked=True),
                name="unique_liked_playlist_per_owner",
            ),
        ]

    def __str__(self):
        return self.title


class PlaylistSong(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    playlist = models.ForeignKey(
        Playlist,
        on_delete=models.CASCADE,
        related_name="song_entries",
    )
    song = models.ForeignKey(
        Song,
        on_delete=models.CASCADE,
        related_name="playlist_entries",
    )
    position = models.PositiveIntegerField()

    class Meta:
        ordering = ["position", "created_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["playlist", "song"],
                name="unique_song_per_playlist",
            ),
            models.UniqueConstraint(
                fields=["playlist", "position"],
                name="unique_playlist_song_position",
            ),
        ]

    def __str__(self):
        return f"{self.playlist}: {self.song} ({self.position})"


class PlaylistFollow(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="playlist_follows",
    )
    playlist = models.ForeignKey(
        Playlist,
        on_delete=models.CASCADE,
        related_name="follow_records",
    )

    class Meta:
        ordering = ["-created_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["user", "playlist"],
                name="unique_playlist_follow",
            ),
        ]

    def clean(self):
        super().clean()
        if self.playlist.owner.pk == self.user.pk:
            raise ValidationError(
                {"user": "A playlist owner cannot follow their own playlist."}
            )

    def save(self, *args, **kwargs):
        self.full_clean()
        return super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.user} follows {self.playlist}"

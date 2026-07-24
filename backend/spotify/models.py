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


class UserFollow(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    follower = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="following_relationships",
    )
    following = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="follower_relationships",
    )

    class Meta:
        ordering = ["-created_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["follower", "following"],
                name="unique_user_follow",
            ),
            models.CheckConstraint(
                condition=~Q(follower=models.F("following")),
                name="prevent_self_follow",
            ),
        ]

    def __str__(self):
        return f"{self.follower} follows {self.following}"


class DirectConversation(TimeStampedModel):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user_one = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="direct_conversations_as_one",
    )
    user_two = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="direct_conversations_as_two",
    )

    class Meta:
        ordering = ["-updated_at"]
        constraints = [
            models.UniqueConstraint(
                fields=["user_one", "user_two"],
                name="unique_direct_conversation",
            ),
            models.CheckConstraint(
                condition=Q(user_one__lt=models.F("user_two")),
                name="canonical_direct_conversation_users",
            ),
        ]

    def __str__(self):
        return f"{self.user_one} ↔ {self.user_two}"


class DirectMessage(TimeStampedModel):
    class MessageType(models.TextChoices):
        TEXT = "text", "Text"
        SONG = "song", "Song"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    conversation = models.ForeignKey(
        DirectConversation,
        on_delete=models.CASCADE,
        related_name="messages",
    )
    sender = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="sent_direct_messages",
    )
    client_message_id = models.UUIDField(default=uuid.uuid4)
    message_type = models.CharField(
        max_length=10,
        choices=MessageType.choices,
        default=MessageType.TEXT,
    )
    body = models.TextField(blank=True)
    song = models.ForeignKey(
        "Song",
        on_delete=models.PROTECT,
        related_name="shared_in_messages",
        null=True,
        blank=True,
    )
    delivered_at = models.DateTimeField(null=True, blank=True)
    read_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at", "-id"]
        constraints = [
            models.UniqueConstraint(
                fields=["sender", "client_message_id"],
                name="unique_sender_client_message",
            ),
            models.CheckConstraint(
                condition=(
                    Q(message_type="text", song__isnull=True)
                    & ~Q(body="")
                    | Q(message_type="song", song__isnull=False)
                ),
                name="valid_direct_message_content",
            ),
        ]

    @property
    def receipt_status(self) -> str:
        if self.read_at:
            return "read"
        if self.delivered_at:
            return "delivered"
        return "sent"

    def __str__(self):
        return f"{self.sender}: {self.message_type} ({self.created_at})"


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

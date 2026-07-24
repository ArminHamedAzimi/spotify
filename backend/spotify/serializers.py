from django.contrib.auth.password_validation import validate_password
from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from .models import (
    DirectConversation,
    DirectMessage,
    Playlist,
    PlaylistFollow,
    Song,
    User,
    UserFollow,
)


class UserSerializer(serializers.ModelSerializer):
    password = serializers.CharField(
        write_only=True, required=False, validators=[validate_password]
    )

    class Meta:
        model = User
        fields = (
            "id",
            "name",
            "email",
            "password",
            "premium_expires_at",
            "has_active_premium",
            "avatar_url",
            "created_at",
            "updated_at",
        )
        read_only_fields = (
            "id",
            "premium_expires_at",
            "has_active_premium",
            "created_at",
            "updated_at",
        )

    def create(self, validated_data):
        password = validated_data.pop("password", None)
        if not password:
            raise serializers.ValidationError({"password": "This field is required."})
        return User.objects.create_user(password=password, **validated_data)

    def update(self, instance, validated_data):
        password = validated_data.pop("password", None)
        for field, value in validated_data.items():
            setattr(instance, field, value)
        if password:
            instance.set_password(password)
        instance.save()
        return instance


class PublicUserProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ("id", "name", "avatar_url", "has_active_premium")
        read_only_fields = fields


class UserSearchQuerySerializer(serializers.Serializer):
    q = serializers.CharField(
        required=True,
        allow_blank=False,
        max_length=150,
        trim_whitespace=True,
    )


class UserFollowSerializer(serializers.ModelSerializer):
    follower = PublicUserProfileSerializer(read_only=True)
    following = PublicUserProfileSerializer(read_only=True)

    class Meta:
        model = UserFollow
        fields = ("id", "follower", "following", "created_at")
        read_only_fields = fields


class AvatarUploadSerializer(serializers.Serializer):
    avatar = serializers.ImageField(write_only=True)

    def validate_avatar(self, avatar):
        max_size = 5 * 1024 * 1024
        if avatar.size > max_size:
            raise serializers.ValidationError("Avatar size cannot exceed 5 MB.")
        allowed_types = {"image/jpeg", "image/png", "image/webp"}
        if avatar.content_type not in allowed_types:
            raise serializers.ValidationError("Use a JPEG, PNG, or WebP image.")
        return avatar


class AvatarUploadResponseSerializer(serializers.Serializer):
    avatar_url = serializers.URLField(read_only=True)


class SubscriptionSerializer(serializers.Serializer):
    months = serializers.ChoiceField(choices=(1, 3, 6, 12))


class SubscriptionResponseSerializer(serializers.Serializer):
    months_added = serializers.IntegerField(read_only=True)
    premium_expires_at = serializers.DateTimeField(read_only=True)
    has_active_premium = serializers.BooleanField(read_only=True)


class SongSerializer(serializers.ModelSerializer):
    artist = UserSerializer(read_only=True)

    class Meta:
        model = Song
        fields = (
            "id",
            "title",
            "artist",
            "cover_image_url",
            "audio_url",
            "duration",
            "is_published",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "artist", "created_at", "updated_at")


class SharedSongSerializer(serializers.ModelSerializer):
    artist = PublicUserProfileSerializer(read_only=True)

    class Meta:
        model = Song
        fields = (
            "id",
            "title",
            "artist",
            "cover_image_url",
            "audio_url",
            "duration",
        )
        read_only_fields = fields


class PopularSongSerializer(SharedSongSerializer):
    like_count = serializers.IntegerField(read_only=True)

    class Meta(SharedSongSerializer.Meta):
        fields = (*SharedSongSerializer.Meta.fields, "like_count")
        read_only_fields = fields


class TopArtistSerializer(serializers.ModelSerializer):
    artist = PublicUserProfileSerializer(source="*", read_only=True)
    follower_count = serializers.IntegerField(read_only=True)
    song = serializers.SerializerMethodField()

    class Meta:
        model = User
        fields = ("artist", "follower_count", "song")
        read_only_fields = fields

    @extend_schema_field(SharedSongSerializer)
    def get_song(self, obj):
        song = (
            obj.songs.filter(is_published=True)
            .select_related("artist")
            .order_by("-created_at", "pk")
            .first()
        )
        return SharedSongSerializer(song).data if song else None


class DirectMessageSerializer(serializers.ModelSerializer):
    sender = PublicUserProfileSerializer(read_only=True)
    song = SharedSongSerializer(read_only=True)
    status = serializers.CharField(source="receipt_status", read_only=True)

    class Meta:
        model = DirectMessage
        fields = (
            "id",
            "conversation",
            "client_message_id",
            "sender",
            "message_type",
            "body",
            "song",
            "status",
            "delivered_at",
            "read_at",
            "created_at",
        )
        read_only_fields = fields


class DirectConversationSerializer(serializers.ModelSerializer):
    other_user = serializers.SerializerMethodField()
    last_message = serializers.SerializerMethodField()
    unread_count = serializers.SerializerMethodField()

    class Meta:
        model = DirectConversation
        fields = (
            "id",
            "other_user",
            "last_message",
            "unread_count",
            "created_at",
            "updated_at",
        )
        read_only_fields = fields

    @extend_schema_field(PublicUserProfileSerializer)
    def get_other_user(self, obj):
        request = self.context["request"]
        other = obj.user_two if obj.user_one_id == request.user.pk else obj.user_one
        return PublicUserProfileSerializer(other).data

    @extend_schema_field(DirectMessageSerializer)
    def get_last_message(self, obj):
        message = (
            obj.messages.select_related("sender", "song__artist")
            .order_by("-created_at", "-id")
            .first()
        )
        return DirectMessageSerializer(message).data if message else None

    @extend_schema_field(serializers.IntegerField())
    def get_unread_count(self, obj):
        request = self.context["request"]
        return (
            obj.messages.filter(read_at__isnull=True)
            .exclude(sender=request.user)
            .count()
        )


class PlaylistSerializer(serializers.ModelSerializer):
    owner = UserSerializer(read_only=True)
    follower_count = serializers.IntegerField(read_only=True)
    song_count = serializers.SerializerMethodField()

    class Meta:
        model = Playlist
        fields = (
            "id",
            "owner",
            "title",
            "description",
            "is_public",
            "is_liked",
            "song_count",
            "follower_count",
            "created_at",
            "updated_at",
        )
        read_only_fields = (
            "id",
            "owner",
            "is_liked",
            "song_count",
            "follower_count",
            "created_at",
            "updated_at",
        )

    @extend_schema_field(serializers.IntegerField())
    def get_song_count(self, obj):
        if hasattr(obj, "song_count"):
            return obj.song_count
        return obj.song_entries.count()


class AddPlaylistSongSerializer(serializers.Serializer):
    song_id = serializers.UUIDField()


class PlaylistVisibilitySerializer(serializers.Serializer):
    is_public = serializers.BooleanField()


class PlaylistNextSongSerializer(serializers.Serializer):
    song_id = serializers.UUIDField(required=False, allow_null=True, default=None)
    shuffle = serializers.BooleanField(required=False, default=False)


class RandomNextSongSerializer(serializers.Serializer):
    song_id = serializers.UUIDField(required=False, allow_null=True, default=None)


class SongSearchQuerySerializer(serializers.Serializer):
    q = serializers.CharField(
        required=True,
        allow_blank=False,
        max_length=200,
        trim_whitespace=True,
    )


class PlaylistFollowSerializer(serializers.ModelSerializer):
    user = serializers.PrimaryKeyRelatedField(read_only=True)

    class Meta:
        model = PlaylistFollow
        fields = ("id", "user", "playlist", "created_at", "updated_at")
        read_only_fields = ("id", "user", "created_at", "updated_at")

    def validate_playlist(self, playlist):
        request = self.context["request"]
        if playlist.owner_id == request.user.id:
            raise serializers.ValidationError("You cannot follow your own playlist.")
        if not playlist.is_public:
            raise serializers.ValidationError("You can only follow public playlists.")
        existing = PlaylistFollow.objects.filter(user=request.user, playlist=playlist)
        if self.instance:
            existing = existing.exclude(pk=self.instance.pk)
        if existing.exists():
            raise serializers.ValidationError("You already follow this playlist.")
        return playlist

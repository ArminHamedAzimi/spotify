from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers

from .models import Playlist, PlaylistFollow, Song, User


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


class PlaylistSerializer(serializers.ModelSerializer):
    owner = UserSerializer(read_only=True)
    follower_count = serializers.IntegerField(read_only=True)

    class Meta:
        model = Playlist
        fields = (
            "id",
            "owner",
            "title",
            "description",
            "is_public",
            "songs",
            "follower_count",
            "created_at",
            "updated_at",
        )
        read_only_fields = ("id", "owner", "follower_count", "created_at", "updated_at")


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

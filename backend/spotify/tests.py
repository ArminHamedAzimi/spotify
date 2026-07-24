from io import BytesIO
from datetime import timedelta

from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from django.test.utils import override_settings
from django.utils import timezone
from PIL import Image
from rest_framework import status
from rest_framework.test import APIClient

from .models import Playlist, PlaylistFollow, Song, User


class PlaylistFollowTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(
            email="owner@example.com", password="password123", name="Owner"
        )
        self.listener = User.objects.create_user(
            email="listener@example.com", password="password123", name="Listener"
        )
        self.playlist = Playlist.objects.create(owner=self.owner, title="Favorites")

    def test_listener_can_follow_playlist(self):
        follow = PlaylistFollow.objects.create(
            user=self.listener, playlist=self.playlist
        )
        self.assertEqual(follow.playlist, self.playlist)

    def test_owner_cannot_follow_own_playlist(self):
        with self.assertRaises(ValidationError):
            PlaylistFollow.objects.create(user=self.owner, playlist=self.playlist)

    def test_premium_expiration_defaults_to_none(self):
        self.assertIsNone(self.owner.premium_expires_at)
        self.assertFalse(self.owner.has_active_premium)


class ApiAuthorizationTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(
            email="owner@example.com", password="password123!", name="Owner"
        )
        self.other = User.objects.create_user(
            email="other@example.com", password="password123!", name="Other"
        )
        self.playlist = Playlist.objects.create(
            owner=self.owner, title="Public", is_public=True
        )
        self.api_client = APIClient()

    def test_anonymous_user_cannot_list_songs(self):
        response = self.api_client.get("/api/songs/")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_user_cannot_update_another_user(self):
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.patch(
            f"/api/users/{self.other.pk}/", {"name": "Changed"}, format="json"
        )
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_regular_user_cannot_list_users(self):
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.get("/api/users/")
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_staff_user_can_list_users(self):
        self.owner.is_staff = True
        self.owner.save(update_fields=["is_staff"])
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.get("/api/users/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_authenticated_user_can_get_own_profile(self):
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.get("/api/users/me/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["id"], str(self.owner.pk))
        self.assertEqual(response.data["email"], self.owner.email)

    def test_authenticated_user_can_add_subscription(self):
        self.api_client.force_authenticate(user=self.owner)
        before = timezone.now()
        response = self.api_client.post(
            "/api/users/subscription/",
            {"months": 3},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["months_added"], 3)
        self.assertTrue(response.data["has_active_premium"])
        self.owner.refresh_from_db()
        self.assertGreater(self.owner.premium_expires_at, before + timedelta(days=89))

    def test_subscription_rejects_unsupported_duration(self):
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.post(
            "/api/users/subscription/",
            {"months": 2},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_recent_songs_returns_ten_newest_accessible_songs(self):
        for index in range(12):
            Song.objects.create(
                title=f"Song {index}",
                artist=self.owner,
                cover_image_url=f"https://media.example.com/cover-{index}.jpg",
                audio_url=f"https://media.example.com/audio-{index}.mp3",
                is_published=True,
            )
        Song.objects.create(
            title="Other Artist Draft",
            artist=self.other,
            cover_image_url="https://media.example.com/draft.jpg",
            audio_url="https://media.example.com/draft.mp3",
            is_published=False,
        )
        self.api_client.force_authenticate(user=self.owner)

        response = self.api_client.get("/api/songs/recent/")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 10)
        self.assertEqual(response.data[0]["title"], "Song 11")
        self.assertEqual(response.data[-1]["title"], "Song 2")

    def test_authenticated_user_can_follow_public_playlist(self):
        self.api_client.force_authenticate(user=self.other)
        response = self.api_client.post(
            "/api/playlist-follows/",
            {"playlist": str(self.playlist.pk)},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(PlaylistFollow.objects.get().user, self.other)

    @override_settings(MINIO_PUBLIC_ENDPOINT="localhost:9000")
    def test_authenticated_user_can_upload_avatar(self):
        image_buffer = BytesIO()
        Image.new("RGB", (2, 2), color="blue").save(image_buffer, format="PNG")
        avatar = SimpleUploadedFile(
            "avatar.png",
            image_buffer.getvalue(),
            content_type="image/png",
        )
        self.api_client.force_authenticate(user=self.owner)

        with self.assertLogs("spotify.views", level="WARNING") as captured_logs:
            response = self.api_client.post(
                "/api/users/avatar/",
                {"avatar": avatar},
                format="multipart",
            )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("extension=.png", captured_logs.output[0])
        self.assertIn("content_type=image/png", captured_logs.output[0])
        self.assertIn("avatar_url", response.data)
        self.assertTrue(response.data["avatar_url"].startswith("http://"))
        self.owner.refresh_from_db()
        self.assertEqual(self.owner.avatar_url, response.data["avatar_url"])

from io import BytesIO
from datetime import timedelta
from unittest.mock import patch

from django.core.exceptions import ValidationError
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import TestCase
from django.test.utils import override_settings
from django.utils import timezone
from PIL import Image
from rest_framework import status
from rest_framework.test import APIClient

from .models import Playlist, PlaylistFollow, PlaylistSong, Song, User


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
        self.assertEqual(response.data["count"], 2)
        self.assertEqual(len(response.data["results"]), 2)

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

    def test_song_search_matches_title_and_singer_with_pagination(self):
        singer = User.objects.create_user(
            email="singer@example.com",
            password="password123!",
            name="Aurora Singer",
        )
        title_match = Song.objects.create(
            title="Aurora Lights",
            artist=self.owner,
            cover_image_url="https://media.example.com/title-match.jpg",
            audio_url="https://media.example.com/title-match.mp3",
            is_published=True,
        )
        singer_match = Song.objects.create(
            title="Running",
            artist=singer,
            cover_image_url="https://media.example.com/singer-match.jpg",
            audio_url="https://media.example.com/singer-match.mp3",
            is_published=True,
        )
        Song.objects.create(
            title="Aurora Private",
            artist=self.other,
            cover_image_url="https://media.example.com/private.jpg",
            audio_url="https://media.example.com/private.mp3",
            is_published=False,
        )
        self.api_client.force_authenticate(user=self.owner)

        response = self.api_client.get(
            "/api/songs/search/?q=aurora&page=1&page_size=1"
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["count"], 2)
        self.assertEqual(len(response.data["results"]), 1)
        self.assertIsNotNone(response.data["next"])
        result_ids = {
            response.data["results"][0]["id"],
            self.api_client.get(response.data["next"]).data["results"][0]["id"],
        }
        self.assertEqual(result_ids, {str(title_match.pk), str(singer_match.pk)})

    def test_song_search_requires_non_empty_query(self):
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.get("/api/songs/search/?q=")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_user_search_returns_paginated_public_profiles(self):
        User.objects.create_user(
            email="aurora.one@example.com",
            password="password123!",
            name="Aurora One",
        )
        User.objects.create_user(
            email="aurora.two@example.com",
            password="password123!",
            name="Aurora Two",
        )
        inactive = User.objects.create_user(
            email="aurora.inactive@example.com",
            password="password123!",
            name="Aurora Inactive",
        )
        inactive.is_active = False
        inactive.save(update_fields=["is_active"])
        self.api_client.force_authenticate(user=self.owner)

        response = self.api_client.get(
            "/api/users/search/?q=aurora&page=1&page_size=1"
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["count"], 2)
        self.assertEqual(len(response.data["results"]), 1)
        self.assertIsNotNone(response.data["next"])
        profile = response.data["results"][0]
        self.assertEqual(
            set(profile),
            {"id", "name", "avatar_url", "has_active_premium"},
        )
        self.assertNotIn("email", profile)

    def test_user_search_requires_non_empty_query(self):
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.get("/api/users/search/?q=")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_new_user_has_protected_liked_playlist(self):
        liked = Playlist.objects.get(owner=self.owner, is_liked=True)
        self.assertEqual(liked.title, "Liked Songs")
        self.assertFalse(liked.is_public)
        self.api_client.force_authenticate(user=self.owner)
        response = self.api_client.delete(f"/api/playlists/{liked.pk}/")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_owner_can_create_and_delete_playlist(self):
        self.api_client.force_authenticate(user=self.owner)
        create_response = self.api_client.post(
            "/api/playlists/",
            {
                "title": "Road Trip",
                "description": "Driving music",
                "is_public": False,
            },
            format="json",
        )
        self.assertEqual(create_response.status_code, status.HTTP_201_CREATED)
        playlist_id = create_response.data["id"]
        delete_response = self.api_client.delete(f"/api/playlists/{playlist_id}/")
        self.assertEqual(delete_response.status_code, status.HTTP_204_NO_CONTENT)

    def test_authenticated_user_can_list_only_owned_playlists(self):
        Playlist.objects.create(
            owner=self.other,
            title="Other Public Playlist",
            is_public=True,
        )
        self.api_client.force_authenticate(user=self.owner)

        response = self.api_client.get("/api/playlists/me/?page=1&page_size=10")

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("count", response.data)
        self.assertIn("next", response.data)
        self.assertIn("previous", response.data)
        self.assertIn("results", response.data)
        self.assertTrue(response.data["results"])
        self.assertTrue(
            all(
                item["owner"]["id"] == str(self.owner.pk)
                for item in response.data["results"]
            )
        )
        self.assertTrue(any(item["is_liked"] for item in response.data["results"]))
        self.assertFalse(
            any(
                item["title"] == "Other Public Playlist"
                for item in response.data["results"]
            )
        )
        self.assertTrue(all("song_count" in item for item in response.data["results"]))

    def test_owner_can_add_song_and_get_next_song(self):
        first = Song.objects.create(
            title="First",
            artist=self.owner,
            cover_image_url="https://media.example.com/first.jpg",
            audio_url="https://media.example.com/first.mp3",
            is_published=True,
        )
        second = Song.objects.create(
            title="Second",
            artist=self.owner,
            cover_image_url="https://media.example.com/second.jpg",
            audio_url="https://media.example.com/second.mp3",
            is_published=True,
        )
        self.api_client.force_authenticate(user=self.owner)
        for song in (first, second):
            response = self.api_client.post(
                f"/api/playlists/{self.playlist.pk}/songs/",
                {"song_id": str(song.pk)},
                format="json",
            )
            self.assertEqual(response.status_code, status.HTTP_201_CREATED)

        entries = PlaylistSong.objects.filter(playlist=self.playlist).order_by(
            "position"
        )
        self.assertEqual(list(entries.values_list("position", flat=True)), [0, 1])

        songs_page = self.api_client.get(
            f"/api/playlists/{self.playlist.pk}/songs/?page=1&page_size=1"
        )
        self.assertEqual(songs_page.status_code, status.HTTP_200_OK)
        self.assertEqual(songs_page.data["count"], 2)
        self.assertIsNotNone(songs_page.data["next"])
        self.assertEqual(len(songs_page.data["results"]), 1)
        self.assertEqual(songs_page.data["results"][0]["id"], str(first.pk))
        self.assertIn("artist", songs_page.data["results"][0])
        self.assertIn("audio_url", songs_page.data["results"][0])

        start_response = self.api_client.post(
            f"/api/playlists/{self.playlist.pk}/next-song/",
            {"song_id": None, "shuffle": False},
            format="json",
        )
        self.assertEqual(start_response.data["id"], str(first.pk))

        with patch("spotify.views.random.choice", return_value=entries[1]):
            shuffled_start_response = self.api_client.post(
                f"/api/playlists/{self.playlist.pk}/next-song/",
                {"shuffle": True},
                format="json",
            )
        self.assertEqual(shuffled_start_response.data["id"], str(second.pk))

        next_response = self.api_client.post(
            f"/api/playlists/{self.playlist.pk}/next-song/",
            {"song_id": str(first.pk), "shuffle": False},
            format="json",
        )
        self.assertEqual(next_response.data["id"], str(second.pk))

        with patch("spotify.views.random.choice", side_effect=lambda choices: choices[-1]):
            shuffled_response = self.api_client.post(
                f"/api/playlists/{self.playlist.pk}/next-song/",
                {"song_id": str(first.pk), "shuffle": True},
                format="json",
            )
        self.assertEqual(shuffled_response.data["id"], str(second.pk))

        with patch("spotify.views.random.choice", return_value=second):
            random_response = self.api_client.post(
                "/api/songs/random-next/",
                {"song_id": str(first.pk)},
                format="json",
            )
        self.assertEqual(random_response.status_code, status.HTTP_200_OK)
        self.assertEqual(random_response.data["id"], str(second.pk))

    def test_owner_can_remove_song_from_playlist(self):
        song = Song.objects.create(
            title="Removable",
            artist=self.owner,
            cover_image_url="https://media.example.com/removable.jpg",
            audio_url="https://media.example.com/removable.mp3",
            is_published=True,
        )
        PlaylistSong.objects.create(
            playlist=self.playlist,
            song=song,
            position=0,
        )
        self.api_client.force_authenticate(user=self.owner)

        response = self.api_client.delete(
            f"/api/playlists/{self.playlist.pk}/songs/{song.pk}/"
        )

        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(
            PlaylistSong.objects.filter(playlist=self.playlist, song=song).exists()
        )
        self.assertTrue(Song.objects.filter(pk=song.pk).exists())

    def test_non_owner_cannot_remove_song_from_playlist(self):
        song = Song.objects.create(
            title="Owner Only",
            artist=self.owner,
            cover_image_url="https://media.example.com/owner-only.jpg",
            audio_url="https://media.example.com/owner-only.mp3",
            is_published=True,
        )
        PlaylistSong.objects.create(
            playlist=self.playlist,
            song=song,
            position=0,
        )
        self.api_client.force_authenticate(user=self.other)

        response = self.api_client.delete(
            f"/api/playlists/{self.playlist.pk}/songs/{song.pk}/"
        )

        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertTrue(
            PlaylistSong.objects.filter(playlist=self.playlist, song=song).exists()
        )

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

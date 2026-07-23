from django.core.exceptions import ValidationError
from django.test import TestCase
from rest_framework import status
from rest_framework.test import APIClient

from .models import Playlist, PlaylistFollow, User


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

    def test_authenticated_user_can_follow_public_playlist(self):
        self.api_client.force_authenticate(user=self.other)
        response = self.api_client.post(
            "/api/playlist-follows/",
            {"playlist": str(self.playlist.pk)},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(PlaylistFollow.objects.get().user, self.other)

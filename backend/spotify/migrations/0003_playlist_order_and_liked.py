import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


def copy_songs_and_create_liked_playlists(apps, schema_editor):
    Playlist = apps.get_model("spotify", "Playlist")
    PlaylistSong = apps.get_model("spotify", "PlaylistSong")
    User = apps.get_model("spotify", "User")
    database = schema_editor.connection.alias

    for playlist in Playlist.objects.using(database).all().iterator():
        for position, song in enumerate(playlist.songs.all().order_by("pk")):
            PlaylistSong.objects.using(database).create(
                playlist_id=playlist.pk,
                song_id=song.pk,
                position=position,
            )

    for user in User.objects.using(database).all().iterator():
        liked_playlist, _ = Playlist.objects.using(database).get_or_create(
            owner_id=user.pk,
            title="Liked Songs",
            defaults={
                "description": "Songs liked by this user.",
                "is_public": False,
                "is_liked": True,
            },
        )
        if not liked_playlist.is_liked:
            liked_playlist.is_liked = True
            liked_playlist.is_public = False
            liked_playlist.save(update_fields=["is_liked", "is_public"])


class Migration(migrations.Migration):
    dependencies = [
        ("spotify", "0002_user_premium_expiration"),
    ]

    operations = [
        migrations.AddField(
            model_name="playlist",
            name="is_liked",
            field=models.BooleanField(default=False),
        ),
        migrations.CreateModel(
            name="PlaylistSong",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                    ),
                ),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("position", models.PositiveIntegerField()),
                (
                    "playlist",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="song_entries",
                        to="spotify.playlist",
                    ),
                ),
                (
                    "song",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="playlist_entries",
                        to="spotify.song",
                    ),
                ),
            ],
            options={"ordering": ["position", "created_at"]},
        ),
        migrations.RunPython(
            copy_songs_and_create_liked_playlists,
            reverse_code=migrations.RunPython.noop,
        ),
        migrations.RemoveField(
            model_name="playlist",
            name="songs",
        ),
        migrations.AddField(
            model_name="playlist",
            name="songs",
            field=models.ManyToManyField(
                blank=True,
                related_name="playlists",
                through="spotify.PlaylistSong",
                to="spotify.song",
            ),
        ),
    ]

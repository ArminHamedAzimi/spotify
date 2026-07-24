from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from django.core.files.storage import default_storage
from django.core.management import call_command
from django.test import TestCase
from django.test.utils import override_settings

from .models import Song, User


@override_settings(
    MINIO_BUCKET="spotify-media",
    MINIO_PUBLIC_ENDPOINT="localhost:9000",
)
class ImportSongsCommandTests(TestCase):
    def test_import_uploads_cover_first_and_creates_song(self):
        artist = User.objects.create_user(
            email="artist@example.com",
            password="password123!",
            name="Artist",
        )
        with TemporaryDirectory() as directory:
            songs_dir = Path(directory)
            covers_dir = songs_dir / "covers"
            covers_dir.mkdir()
            (songs_dir / "My_Song.mp3").write_bytes(b"audio")
            (covers_dir / "My_Song.png").write_bytes(b"cover")
            output = StringIO()
            original_save = default_storage.save

            with patch(
                "spotify.management.commands.import_songs.default_storage.save",
                side_effect=original_save,
            ) as save:
                call_command(
                    "import_songs",
                    songs_dir=str(songs_dir),
                    artist_email=artist.email,
                    publish=True,
                    stdout=output,
                )

        song = Song.objects.get(artist=artist, title="My Song")
        self.assertTrue(song.is_published)
        self.assertTrue(song.cover_image_url.startswith("http://localhost:9000/"))
        self.assertTrue(song.audio_url.startswith("http://localhost:9000/"))
        self.assertTrue(save.call_args_list[0].args[0].startswith("songs/covers/"))
        self.assertTrue(save.call_args_list[1].args[0].startswith("songs/audio/"))
        self.assertIn("imported=1", output.getvalue())

import uuid
from pathlib import Path
from urllib.parse import quote

from django.conf import settings
from django.core.files import File
from django.core.files.storage import default_storage
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from spotify.models import Song, User


AUDIO_EXTENSIONS = {".flac", ".m4a", ".mp3", ".ogg", ".wav"}
COVER_EXTENSIONS = {".jpeg", ".jpg", ".png", ".webp"}


def public_storage_url(object_name: str) -> str:
    endpoint = settings.MINIO_PUBLIC_ENDPOINT.strip().rstrip("/")
    if not endpoint.startswith(("http://", "https://")):
        endpoint = f"http://{endpoint}"
    return f"{endpoint}/{settings.MINIO_BUCKET}/{quote(object_name, safe='/')}"


class Command(BaseCommand):
    help = (
        "Upload audio files and matching covers to MinIO, then create Song rows. "
        "A cover must have the same filename stem as its audio file."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "--artist-email",
            required=True,
            help="Email address of the User that owns the imported songs.",
        )
        parser.add_argument(
            "--songs-dir",
            default="songs",
            help="Directory containing audio files and a covers/ subdirectory.",
        )
        parser.add_argument(
            "--publish",
            action="store_true",
            help="Mark imported songs as published.",
        )
        parser.add_argument(
            "--overwrite",
            action="store_true",
            help="Replace URLs and publication state for existing artist/title rows.",
        )
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Validate matches and print planned imports without uploading.",
        )

    def handle(self, *args, **options):
        songs_dir = Path(options["songs_dir"]).expanduser().resolve()
        covers_dir = songs_dir / "covers"
        if not songs_dir.is_dir():
            raise CommandError(f"Songs directory does not exist: {songs_dir}")
        if not covers_dir.is_dir():
            raise CommandError(f"Covers directory does not exist: {covers_dir}")

        try:
            artist = User.objects.get(email__iexact=options["artist_email"])
        except User.DoesNotExist as exc:
            raise CommandError(
                f"No user exists with email {options['artist_email']!r}."
            ) from exc

        cover_by_stem = {
            path.stem.casefold(): path
            for path in covers_dir.iterdir()
            if path.is_file() and path.suffix.lower() in COVER_EXTENSIONS
        }
        audio_files = sorted(
            path
            for path in songs_dir.iterdir()
            if path.is_file() and path.suffix.lower() in AUDIO_EXTENSIONS
        )
        if not audio_files:
            raise CommandError(f"No supported audio files found in {songs_dir}")

        imported = skipped = failed = 0
        for audio_path in audio_files:
            title = audio_path.stem.replace("_", " ").strip()
            cover_path = cover_by_stem.get(audio_path.stem.casefold())
            if cover_path is None:
                self.stderr.write(
                    self.style.WARNING(
                        f"Skipping {audio_path.name}: no same-name cover found."
                    )
                )
                skipped += 1
                continue

            existing = Song.objects.filter(artist=artist, title=title).first()
            if existing and not options["overwrite"]:
                self.stdout.write(
                    f"Skipping {audio_path.name}: song already exists (use --overwrite)."
                )
                skipped += 1
                continue

            if options["dry_run"]:
                self.stdout.write(
                    f"Would import {audio_path.name} with cover {cover_path.name}."
                )
                imported += 1
                continue

            token = uuid.uuid4().hex
            cover_name = (
                f"songs/covers/{artist.pk}/{token}{cover_path.suffix.lower()}"
            )
            audio_name = (
                f"songs/audio/{artist.pk}/{token}{audio_path.suffix.lower()}"
            )
            saved_cover = saved_audio = None
            try:
                # Cover upload intentionally happens first.
                with cover_path.open("rb") as cover_file:
                    saved_cover = default_storage.save(cover_name, File(cover_file))
                with audio_path.open("rb") as audio_file:
                    saved_audio = default_storage.save(audio_name, File(audio_file))

                with transaction.atomic():
                    Song.objects.update_or_create(
                        artist=artist,
                        title=title,
                        defaults={
                            "cover_image_url": public_storage_url(saved_cover),
                            "audio_url": public_storage_url(saved_audio),
                            "is_published": options["publish"],
                        },
                    )
                self.stdout.write(self.style.SUCCESS(f"Imported {title}."))
                imported += 1
            except Exception as exc:
                if saved_audio:
                    default_storage.delete(saved_audio)
                if saved_cover:
                    default_storage.delete(saved_cover)
                self.stderr.write(self.style.ERROR(f"Failed {audio_path.name}: {exc}"))
                failed += 1

        self.stdout.write(
            self.style.SUCCESS(
                f"Import complete: imported={imported}, skipped={skipped}, failed={failed}."
            )
        )
        if failed:
            raise CommandError(f"{failed} song import(s) failed.")

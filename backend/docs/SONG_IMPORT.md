# Importing local songs into MinIO and Django

The `import_songs` management command imports local audio files, uploads their
covers and audio objects to MinIO, and creates `Song` database rows.

## Directory convention

Audio files go directly in `songs/`. Covers go in `songs/covers/`. A cover and
audio file must have the same filename stem:

```text
songs/
├── First_Song.mp3
├── Second Song.flac
└── covers/
    ├── First_Song.jpg
    └── Second Song.webp
```

Supported audio extensions: `.mp3`, `.flac`, `.m4a`, `.ogg`, `.wav`.

Supported cover extensions: `.jpg`, `.jpeg`, `.png`, `.webp`.

The filename stem becomes the song title; underscores are converted to spaces.
The artist must already exist as a Django user.

## Run with Docker

Because `backend/` is mounted at `/app`, place the directory at
`backend/songs/`, then run:

```bash
docker compose exec web python manage.py import_songs \
  --songs-dir /app/songs \
  --artist-email artist@example.com \
  --publish
```

The command uploads the cover first to:

```text
spotify-media/songs/covers/{artist-uuid}/{generated-name}.{extension}
```

It then uploads the audio to:

```text
spotify-media/songs/audio/{artist-uuid}/{generated-name}.{extension}
```

The resulting public MinIO URLs are saved in `cover_image_url` and `audio_url`.

## Options

- `--artist-email EMAIL` — required owner/artist account.
- `--songs-dir PATH` — defaults to `songs`.
- `--publish` — sets `is_published=true`.
- `--overwrite` — updates an existing song with the same artist and title.
- `--dry-run` — checks matches without uploading or writing database rows.

Without `--overwrite`, existing songs are skipped. Songs without a matching
cover are also skipped. If an upload or database write fails, newly uploaded
objects for that song are removed.

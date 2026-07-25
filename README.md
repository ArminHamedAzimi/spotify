# Spotify

A Spotify-like music streaming project with a Jetpack Compose Android client and a
Django REST backend. Users can browse and play songs, manage playlists, follow
other users, chat in real time, and manage premium status and avatars against a
local Docker-backed API.

## Features

- JWT registration, login, and token refresh
- Songs, playlists, Liked Songs, search, and playback helpers
- User follow graph and public profile discovery
- Real-time direct chat over WebSockets with REST history sync
- Premium subscription extension and avatar upload
- Media objects served from MinIO

## Stack

- **Android client:** Kotlin, Jetpack Compose
- **Backend API:** Django, Django REST Framework, Simple JWT
- **Data & realtime:** PostgreSQL, Redis, Django Channels
- **Object storage:** MinIO
- **Local orchestration:** Docker Compose

## Repository structure

```text
.
├── android/          # Jetpack Compose Android client
├── backend/          # Django API, Docker Compose stack, management commands
│   └── docs/         # API integration guide and song-import notes
└── README.md
```

## Backend setup

The backend runs entirely through Docker Compose from `backend/`. The stack
starts Postgres, Redis, MinIO, a one-shot bucket initializer, and the Django
ASGI app (Daphne) on port `8000`.

### Prerequisites

- Docker and Docker Compose v2
- Free local ports: `8000` (API), `9000` (MinIO S3), `9001` (MinIO console)

### Configure environment

```bash
cd backend
cp .env.example .env
```

Key variables from `.env.example`:

| Variable | Purpose |
| --- | --- |
| `DJANGO_SECRET_KEY` | Django secret; replace before any shared/deployed use |
| `DJANGO_DEBUG` | Local debug mode (`true` in the example) |
| `DJANGO_ALLOWED_HOSTS` | Includes `localhost`, `127.0.0.1`, and `10.0.2.2` for the emulator |
| `POSTGRES_*` | Database name, user, password, host, and port |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO root credentials |
| `MINIO_BUCKET` | Media bucket name (`spotify-media` by default) |
| `MINIO_ENDPOINT` | Internal S3 endpoint used by Django (`http://minio:9000`) |
| `MINIO_PUBLIC_ENDPOINT` | Host-reachable base URL embedded in returned media URLs |
| `CHAT_REDIS_URL` | Redis URL for Channels / live chat |

For Android Emulator clients, keep or set:

```bash
MINIO_PUBLIC_ENDPOINT=http://10.0.2.2:9000
```

`localhost` inside the emulator is the emulator itself, so media URLs must use
`10.0.2.2` (the host machine alias). A physical device needs your computer's
LAN IP or a public HTTPS media host instead.

### Start the stack

```bash
cd backend
docker compose up --build
```

On startup the `web` entrypoint applies migrations, collects static files, then
serves `config.asgi:application` with Daphne. The `createbucket` service creates
the MinIO bucket if needed and grants public download access so avatar/cover/
audio URLs load without MinIO credentials.

Useful commands while the stack is running:

```bash
# Follow API logs
docker compose logs -f web

# Django shell
docker compose exec web python manage.py shell

# Stop everything
docker compose down
```

### Local URLs

| Service | URL |
| --- | --- |
| API base | `http://localhost:8000/api/` |
| OpenAPI schema | `http://localhost:8000/api/schema/` |
| Swagger UI | `http://localhost:8000/api/docs/` |
| MinIO S3 API | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |

Compose services: `db` (Postgres 17), `redis`, `minio`, `createbucket`, and
`web`. Persistent volumes keep Postgres, Redis, and MinIO data across restarts.

## Android setup

The Jetpack Compose client lives in `android/`. It talks to the Dockerized API
over Retrofit and uses WebSockets for live chat.

### Prerequisites

- Android Studio (recent stable) with an Android Emulator or a physical device
- Backend Compose stack already running (see [Backend setup](#backend-setup))

### Run the app

1. Open the `android/` directory in Android Studio.
2. Let Gradle sync finish.
3. Start an emulator or connect a device.
4. Run the `app` configuration.

The default API base URL is injected at build time in
`android/app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/api/\"")
```

`10.0.2.2` is the Android Emulator alias for the host machine where Docker
exposes port `8000`. Do not use `localhost` for the API from the emulator.

For a physical device on the same LAN, change `API_BASE_URL` to your computer's
LAN IP, for example `http://192.168.1.20:8000/api/`, and rebuild. Keep
`DJANGO_ALLOWED_HOSTS` and `MINIO_PUBLIC_ENDPOINT` aligned with that host.

### Auth and media

- Register and log in through the app; the client stores JWT access and refresh
  tokens and attaches `Authorization: Bearer <access>` on authenticated calls.
- Token refresh uses `POST /api/auth/token/refresh/`.
- Avatar and song media URLs come from MinIO. With the default emulator setup,
  those URLs should use `http://10.0.2.2:9000/...` via `MINIO_PUBLIC_ENDPOINT`.

### Live chat

Chat WebSocket URLs are derived from `API_BASE_URL` by switching to `ws://` /
`wss://` and replacing the `/api/` suffix with `/ws/chat/{other_user_id}/`.
Conversation lists and message history still use the REST chat endpoints.

Endpoint contracts, request and response shapes, and WebSocket event types are
documented in `backend/docs/API.md`.

## Documentation

- [backend/docs/API.md](backend/docs/API.md) — full HTTP and WebSocket integration
  guide for the Android client (auth, users, songs, playlists, follows, chat,
  errors, and MinIO media URLs)
- [backend/docs/SONG_IMPORT.md](backend/docs/SONG_IMPORT.md) — import local audio
  and covers into MinIO and create Django `Song` rows with the
  `import_songs` management command

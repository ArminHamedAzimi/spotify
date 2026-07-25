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

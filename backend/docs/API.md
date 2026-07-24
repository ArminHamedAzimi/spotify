# Spotify Backend API — Android Integration Guide

This document describes the complete HTTP API exposed by the Django backend.
All request and response bodies use JSON unless an endpoint explicitly has no
body.

## 1. Connection information

Development base URL:

```text
http://localhost:8000/api/
```

Android Emulator base URL when Django is running on the development computer:

```text
http://10.0.2.2:8000/api/
```

`localhost` inside the Android Emulator refers to the emulator itself.
`10.0.2.2` is the emulator alias for the host computer. A physical Android
device must use the computer's LAN IP address or the deployed HTTPS URL.

All endpoint paths below are relative to the server origin. Keep the trailing
slash because Django REST Framework routes use trailing slashes.

### URL placeholders

- `{id}` is the UUID of the requested object, for example
  `550e8400-e29b-41d4-a716-446655440000`.
- URL-valued JSON fields such as `avatar_url`, `cover_image_url`, and
  `audio_url` must contain complete valid URLs, including `http://` or
  `https://`.
- These media URLs normally point to objects stored in the `spotify-media`
  MinIO bucket. The API currently stores URLs; it does not accept binary
  multipart uploads.

### Common headers

```http
Content-Type: application/json
Accept: application/json
Authorization: Bearer <access-token>
```

The `Authorization` header is required everywhere except registration, token
creation, the OpenAPI schema, and Swagger UI.

## 2. Data formats

- IDs are UUID strings.
- Dates are ISO-8601 UTC timestamps, such as
  `"2026-07-23T17:30:00Z"`.
- Durations use Django REST Framework's duration format:
  `"[days] HH:MM:SS[.ffffff]"`. Examples are `"00:03:42"` and
  `"2 04:00:00"`.
- List endpoints currently return a plain JSON array because pagination is not
  enabled.
- Fields documented as read-only must not be relied upon in request bodies.
  The server derives or manages them.

### Premium fields

Every user object contains these two read-only fields:

```json
{
  "premium_expires_at": "2026-08-23T17:30:00Z",
  "has_active_premium": true
}
```

- `premium_expires_at` is the absolute ISO-8601 UTC timestamp at which premium
  access expires. It is `null` when no expiration has been assigned.
- `has_active_premium` is calculated by the server. It is `true` only when the
  expiration timestamp is later than the server's current time.
- At exactly the expiration timestamp, premium is no longer active.
- A past expiration remains visible but returns `has_active_premium: false`.
- Android should use `has_active_premium` for access decisions and
  `premium_expires_at` for displaying an expiration date or countdown.
- Neither field can be changed through registration, `PUT`, or `PATCH`.
  Premium expiration is managed by trusted server-side code or Django admin.

Example without premium:

```json
{
  "premium_expires_at": null,
  "has_active_premium": false
}
```

## 3. Authentication

The backend uses JWT bearer authentication through Simple JWT. Access tokens
are short-lived credentials sent with API requests. Refresh tokens are used
only to obtain new access tokens.

### 3.1 Register a user

```http
POST /api/users/
```

This is the only public CRUD operation. `avatar_url` is optional. Password is
required for creation and is never returned.

Request:

```json
{
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "password": "a-strong-password",
  "avatar_url": "https://media.example.com/avatars/ada.jpg"
}
```

Response — `201 Created`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "premium_expires_at": null,
  "has_active_premium": false,
  "avatar_url": "https://media.example.com/avatars/ada.jpg",
  "created_at": "2026-07-23T17:30:00Z",
  "updated_at": "2026-07-23T17:30:00Z"
}
```

`premium_expires_at`, `has_active_premium`, `id`, and timestamps are read-only.

### 3.2 Log in and obtain tokens

```http
POST /api/auth/token/
```

Request:

```json
{
  "email": "ada@example.com",
  "password": "a-strong-password"
}
```

Response — `200 OK`:

```json
{
  "refresh": "<refresh-jwt>",
  "access": "<access-jwt>"
}
```

Store tokens in encrypted Android storage. Do not log them or put them in URLs.
Use the access token as follows:

```http
Authorization: Bearer <access-jwt>
```

Invalid credentials return `401 Unauthorized`:

```json
{
  "detail": "No active account found with the given credentials"
}
```

### 3.3 Refresh an access token

```http
POST /api/auth/token/refresh/
```

Request:

```json
{
  "refresh": "<refresh-jwt>"
}
```

Response — `200 OK`:

```json
{
  "access": "<new-access-jwt>"
}
```

An invalid or expired refresh token returns `401 Unauthorized`.

## 4. User APIs

Users can access and modify only their own account. Staff users can access all
accounts. Attempts to access another user's UUID normally return `404` because
unauthorized objects are excluded from the queryset.

### 4.1 List accessible users

```http
GET /api/users/
```

This endpoint is restricted to Django staff/admin accounts. Regular
authenticated users receive `403 Forbidden`; they should use
`GET /api/users/me/` to load their own profile.

Response — `200 OK`:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Ada Lovelace",
    "email": "ada@example.com",
    "premium_expires_at": null,
    "has_active_premium": false,
    "avatar_url": "https://media.example.com/avatars/ada.jpg",
    "created_at": "2026-07-23T17:30:00Z",
    "updated_at": "2026-07-23T17:30:00Z"
  }
]
```

### 4.2 Get the authenticated user's profile

```http
GET /api/users/me/
```

This is the recommended profile-page endpoint. It identifies the user from the
JWT, so the Android client does not need to know the user's UUID.

Response — `200 OK`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "premium_expires_at": null,
  "has_active_premium": false,
  "avatar_url": "https://media.example.com/avatars/ada.jpg",
  "created_at": "2026-07-23T17:30:00Z",
  "updated_at": "2026-07-23T17:30:00Z"
}
```

Missing or invalid JWT authentication returns `401 Unauthorized`.

### 4.3 Get one user by UUID

```http
GET /api/users/{id}/
```

`{id}` is the user's UUID. Regular users can retrieve only their own account;
staff/admin users can retrieve any user.

Response — `200 OK`: one user object in the same format shown above.

### 4.4 Replace a user

```http
PUT /api/users/{id}/
```

Request:

```json
{
  "name": "Ada Byron",
  "email": "ada@example.com",
  "password": "an-optional-new-password",
  "avatar_url": "https://media.example.com/avatars/ada-new.jpg"
}
```

The password may be omitted to keep the existing password. It is write-only.
Response — `200 OK`: the updated user without the password.

### 4.5 Partially update a user

```http
PATCH /api/users/{id}/
```

Only changed fields are required:

```json
{
  "avatar_url": "https://media.example.com/avatars/ada-new.jpg"
}
```

To change the password:

```json
{
  "password": "a-new-strong-password"
}
```

Response — `200 OK`: the updated user object.

### 4.6 Delete a user

```http
DELETE /api/users/{id}/
```

There is no request or response JSON body. Success returns `204 No Content`.
Deleting a user also deletes playlists and follow records owned by that user.

### 4.7 Upload or replace the current user's avatar

```http
POST /api/users/avatar/
```

Uploads an avatar for the user identified by the JWT access token. There is no
user UUID in this URL: `/users/avatar/` always updates the currently
authenticated account. A client cannot upload an avatar for another user.

This endpoint uses `multipart/form-data`, not JSON. The form must contain one
file field named `avatar`.

Android/Retrofit example:

```kotlin
@Multipart
@POST("users/avatar/")
suspend fun uploadAvatar(
    @Header("Authorization") authorization: String,
    @Part avatar: MultipartBody.Part
): AvatarUploadResponse
```

Create the multipart field using the exact name `avatar`:

```kotlin
val body = imageFile.asRequestBody("image/jpeg".toMediaType())
val part = MultipartBody.Part.createFormData("avatar", imageFile.name, body)
val response = api.uploadAvatar("Bearer $accessToken", part)
```

Accepted formats are JPEG (`image/jpeg`), PNG (`image/png`), and WebP
(`image/webp`). Maximum file size is 5 MB.

For upload debugging, the backend emits a `WARNING` log before validation
containing the original extension, declared multipart MIME type, and filename.
This log is produced even if image validation later rejects the request. A
second `INFO` log records the normalized stored extension after successful
validation. In Docker, inspect these logs with:

```bash
docker compose logs -f web
```

Example backend output:

```text
Avatar upload received: extension=.jpeg, content_type=image/jpeg, filename=profile.jpeg
Avatar upload normalized extension: .jpg
```

The stored filename uses the normalized extension: JPEG becomes `.jpg`, PNG
becomes `.png`, and WebP becomes `.webp`. Therefore, a received `.jpeg`
filename producing a returned `.jpg` URL is expected and does not change the
image encoding.

Response — `200 OK`:

```json
{
  "avatar_url": "http://localhost:9000/spotify-media/avatars/550e8400-e29b-41d4-a716-446655440000/5f6c9ed714c44a1e93de66dc1f18c77c.jpg"
}
```

The upload is complete when this response is returned. The backend saves the
same URL in the current user's `avatar_url`, so later user, artist, and
playlist-owner responses contain the new avatar.

The returned URL is composed from:

- `MINIO_PUBLIC_ENDPOINT`, such as `http://localhost:9000`
- The configured bucket, normally `spotify-media`
- `avatars/{user-id}/`
- A server-generated random filename and validated extension

`MINIO_PUBLIC_ENDPOINT` may be configured as either `localhost:9000` or
`http://localhost:9000`. If its scheme is omitted, Django automatically
prepends `http://`; consequently, every returned `avatar_url` starts with
`http://` or `https://`.

For the Android Emulator, configure
`MINIO_PUBLIC_ENDPOINT=http://10.0.2.2:9000` so the returned URL is reachable.
A physical device needs the development computer's LAN address or a deployed
HTTPS media hostname.

Example validation error — `400 Bad Request`:

```json
{
  "avatar": [
    "Avatar size cannot exceed 5 MB."
  ]
}
```

An unsupported or invalid image also returns `400`. Missing or invalid JWT
authentication returns `401 Unauthorized`.

## 5. Song APIs

Authenticated users can create songs. The authenticated creator is
automatically assigned as `artist`; clients cannot choose another artist.
Users can read published songs and their own unpublished songs. Only the artist
or a staff user can update or delete a song.

### Song JSON format

```json
{
  "id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19",
  "title": "Example Song",
  "artist": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Ada Lovelace",
    "email": "ada@example.com",
    "premium_expires_at": "2026-08-23T17:30:00Z",
    "has_active_premium": true,
    "avatar_url": "https://media.example.com/avatars/ada.jpg",
    "created_at": "2026-07-23T17:30:00Z",
    "updated_at": "2026-07-23T17:30:00Z"
  },
  "cover_image_url": "https://media.example.com/covers/example.jpg",
  "audio_url": "https://media.example.com/audio/example.mp3",
  "duration": "00:03:42",
  "is_published": true,
  "created_at": "2026-07-23T17:40:00Z",
  "updated_at": "2026-07-23T17:40:00Z"
}
```

- `cover_image_url` is the downloadable/displayable cover artwork URL.
- `audio_url` is the streamable audio-object URL.
- `artist`, `id`, `created_at`, and `updated_at` are read-only.
- `duration` may be `null` if it is not known.

### 5.1 List songs

```http
GET /api/songs/
```

Response — `200 OK`: an array of accessible song objects.

### 5.2 Create a song

```http
POST /api/songs/
```

Request:

```json
{
  "title": "Example Song",
  "cover_image_url": "https://media.example.com/covers/example.jpg",
  "audio_url": "https://media.example.com/audio/example.mp3",
  "duration": "00:03:42",
  "is_published": true
}
```

Response — `201 Created`: the complete song object.

### 5.3 Get one song

```http
GET /api/songs/{id}/
```

`{id}` is the song UUID. Response — `200 OK`: one complete song object.

### 5.4 Replace a song

```http
PUT /api/songs/{id}/
```

Request uses the same writable fields as song creation:

```json
{
  "title": "Renamed Song",
  "cover_image_url": "https://media.example.com/covers/renamed.jpg",
  "audio_url": "https://media.example.com/audio/renamed.mp3",
  "duration": "00:03:45",
  "is_published": true
}
```

Response — `200 OK`: the updated song.

### 5.5 Partially update a song

```http
PATCH /api/songs/{id}/
```

Example:

```json
{
  "is_published": true
}
```

Response — `200 OK`: the updated song.

### 5.6 Delete a song

```http
DELETE /api/songs/{id}/
```

No JSON body. Success returns `204 No Content`.

## 6. Playlist APIs

Authenticated users can create playlists. The authenticated creator becomes
the owner. Public playlists are visible to all authenticated users; private
playlists are visible only to their owner and staff. Only the owner or staff
can update or delete a playlist.

### Playlist JSON format

```json
{
  "id": "f11581a5-a501-427c-b2bd-abbca759097f",
  "owner": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Ada Lovelace",
    "email": "ada@example.com",
    "premium_expires_at": "2026-08-23T17:30:00Z",
    "has_active_premium": true,
    "avatar_url": "https://media.example.com/avatars/ada.jpg",
    "created_at": "2026-07-23T17:30:00Z",
    "updated_at": "2026-07-23T17:30:00Z"
  },
  "title": "Morning Mix",
  "description": "Music for the morning commute",
  "is_public": true,
  "songs": [
    "b90fdd61-f70c-41bf-8ad0-e5059f334c19"
  ],
  "follower_count": 12,
  "created_at": "2026-07-23T18:00:00Z",
  "updated_at": "2026-07-23T18:00:00Z"
}
```

The `songs` field contains song UUIDs, not nested song objects.
`owner`, `follower_count`, IDs, and timestamps are read-only.

### 6.1 List playlists

```http
GET /api/playlists/
```

Response — `200 OK`: an array containing public playlists and the current
user's private playlists.

### 6.2 Create a playlist

```http
POST /api/playlists/
```

Request:

```json
{
  "title": "Morning Mix",
  "description": "Music for the morning commute",
  "is_public": true,
  "songs": [
    "b90fdd61-f70c-41bf-8ad0-e5059f334c19"
  ]
}
```

The `songs` array may be empty. Response — `201 Created`: the complete playlist.

### 6.3 Get one playlist

```http
GET /api/playlists/{id}/
```

`{id}` is the playlist UUID. Response — `200 OK`: one playlist object.

### 6.4 Replace a playlist

```http
PUT /api/playlists/{id}/
```

Request:

```json
{
  "title": "Updated Morning Mix",
  "description": "An updated description",
  "is_public": false,
  "songs": [
    "b90fdd61-f70c-41bf-8ad0-e5059f334c19"
  ]
}
```

Response — `200 OK`: the updated playlist.

### 6.5 Partially update a playlist

```http
PATCH /api/playlists/{id}/
```

Example:

```json
{
  "songs": []
}
```

Response — `200 OK`: the updated playlist.

### 6.6 Delete a playlist

```http
DELETE /api/playlists/{id}/
```

No JSON body. Success returns `204 No Content`. Associated follow records are
deleted automatically.

## 7. Playlist-follow APIs

A follow connects the authenticated user to one public playlist. A user cannot
follow their own playlist, follow a private playlist, or follow the same
playlist twice. The authenticated user is always assigned as `user`; a client
cannot create a follow on behalf of someone else.

### Follow JSON format

```json
{
  "id": "62481fc6-867b-4100-a75e-b784b31a45f3",
  "user": "f3ca373e-c936-4c7c-b649-f915e72e6a85",
  "playlist": "f11581a5-a501-427c-b2bd-abbca759097f",
  "created_at": "2026-07-23T18:10:00Z",
  "updated_at": "2026-07-23T18:10:00Z"
}
```

`user`, `id`, and timestamps are read-only. `playlist` is a playlist UUID.

### 7.1 List current user's follows

```http
GET /api/playlist-follows/
```

Regular users receive only their own follows. Staff receive all follows.
Response — `200 OK`: an array of follow objects.

### 7.2 Create a follow

```http
POST /api/playlist-follows/
```

Request:

```json
{
  "playlist": "f11581a5-a501-427c-b2bd-abbca759097f"
}
```

Response — `201 Created`: the complete follow object.

### 7.3 Get one follow

```http
GET /api/playlist-follows/{id}/
```

`{id}` is the follow-record UUID, not the playlist UUID.
Response — `200 OK`: one follow object.

### 7.4 Replace a follow

```http
PUT /api/playlist-follows/{id}/
```

Request:

```json
{
  "playlist": "8d324512-78cc-484f-bc13-54e96cd1ac4d"
}
```

This changes which public playlist is followed but never changes the follower.
Response — `200 OK`: the updated follow.

### 7.5 Partially update a follow

```http
PATCH /api/playlist-follows/{id}/
```

Request:

```json
{
  "playlist": "8d324512-78cc-484f-bc13-54e96cd1ac4d"
}
```

Response — `200 OK`: the updated follow.

### 7.6 Delete/unfollow

```http
DELETE /api/playlist-follows/{id}/
```

No JSON body. Success returns `204 No Content`.

## 8. API documentation endpoints

### OpenAPI schema

```http
GET /api/schema/
```

Returns the generated OpenAPI schema. No authentication is required.

### Swagger UI

```http
GET /api/docs/
```

Returns an interactive HTML API explorer. No authentication is required.

## 9. Errors and status codes

Common status codes:

| Status | Meaning |
| --- | --- |
| `200 OK` | Successful read or update |
| `201 Created` | Resource created |
| `204 No Content` | Resource deleted |
| `400 Bad Request` | Invalid field, duplicate value, weak password, or invalid relationship |
| `401 Unauthorized` | Missing, invalid, or expired access token |
| `403 Forbidden` | Authenticated but explicitly denied |
| `404 Not Found` | Object does not exist or is hidden by authorization |

Validation errors use field names as keys:

```json
{
  "email": [
    "user with this email already exists."
  ],
  "password": [
    "This password is too common."
  ]
}
```

A missing JWT commonly returns:

```json
{
  "detail": "Authentication credentials were not provided."
}
```

## 10. Password hashing and security

The Android application sends the plaintext password only inside an HTTPS JSON
request during registration, login, or password change. Production traffic
must use HTTPS.

The server never stores plaintext passwords. `User.objects.create_user()` and
`User.set_password()` use Django's password hashing framework. With the current
settings and Django 5.2, the default algorithm is:

```text
PBKDF2-HMAC-SHA256
```

The Django identifier is `pbkdf2_sha256`, currently using 1,000,000 iterations
and a unique random salt per password. The encoded database value has a form
similar to:

```text
pbkdf2_sha256$1000000$<random-salt>$<derived-hash>
```

The iteration count can increase after framework upgrades. Android must never
hash the password itself to match this format; send it over HTTPS and let
Django hash and verify it. Passwords are write-only in API serializers and
never appear in API responses.

JWTs are signed authentication credentials, not password hashes. The Android
client should treat access and refresh tokens as secrets and store them using
Android Keystore-backed encrypted storage.

## 11. MinIO media URLs

MinIO provides S3-compatible object storage:

- S3 API during local Docker development: `http://localhost:9000`
- MinIO console during local Docker development: `http://localhost:9001`
- Bucket: `spotify-media`

The Android Emulator cannot use `localhost` to reach host MinIO; replace it
with `10.0.2.2` for development URLs. Production media URLs should normally use
HTTPS and a public hostname or short-lived presigned URLs.

Avatar objects are uploaded through `POST /api/users/avatar/`. The Compose
bucket initializer grants public download access, allowing Android image
libraries to load returned avatar URLs without MinIO credentials. Song cover
and audio upload endpoints are not implemented yet; `cover_image_url` and
`audio_url` must currently reference existing objects.

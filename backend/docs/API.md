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
- Paginated endpoints accept `page` and `page_size`. The default page size is
  10 and the server-enforced maximum is 100. Their JSON envelope contains
  `count`, `next`, `previous`, and `results`.
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
  Premium is extended through `POST /api/users/subscription/`, trusted
  server-side code, or Django admin.

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

Accepts `page` and `page_size`. Response — `200 OK`:

```json
{
  "count": 1,
  "next": null,
  "previous": null,
  "results": [
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
}
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

### 4.3 Search public user profiles

```http
GET /api/users/search/?q=aurora&page=1&page_size=10
```

Searches active users by display name using case-insensitive matching.

Query parameters:

- `q` — required, non-empty name text with a maximum length of 150 characters.
- `page` — optional page number starting at 1.
- `page_size` — optional result count; defaults to 10 and is capped at 100.

The endpoint requires JWT authentication. Search results intentionally expose
only public-safe profile fields; email addresses, password data, premium
expiration timestamps, permissions, and account flags are not returned.

Response — `200 OK`:

```json
{
  "count": 2,
  "next": "http://10.0.2.2:8000/api/users/search/?page=2&page_size=1&q=aurora",
  "previous": null,
  "results": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Aurora One",
      "avatar_url": "https://example.com/avatars/aurora.jpg",
      "has_active_premium": true
    }
  ]
}
```

Results use stable alphabetical name then UUID ordering. Inactive accounts are
excluded. No matches returns `count: 0` and `results: []`.

An empty or missing `q` returns `400 Bad Request`. Missing or invalid JWT
authentication returns `401 Unauthorized`.

### 4.4 Follow or unfollow a user

Follow:

```http
POST /api/users/{user_id}/follow/
```

There is no request body. `{user_id}` is the active user to follow. The
authenticated JWT user becomes the follower.

Response — `201 Created` for a new relationship, or `200 OK` when it already
exists:

```json
{
  "id": "62481fc6-867b-4100-a75e-b784b31a45f3",
  "follower": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Ada",
    "avatar_url": "https://example.com/ada.jpg",
    "has_active_premium": false
  },
  "following": {
    "id": "f3ca373e-c936-4c7c-b649-f915e72e6a85",
    "name": "Aurora",
    "avatar_url": "https://example.com/aurora.jpg",
    "has_active_premium": true
  },
  "created_at": "2026-07-24T12:00:00Z"
}
```

Following yourself returns `400 Bad Request`.

Unfollow:

```http
DELETE /api/users/{user_id}/follow/
```

There is no request body. It is idempotent and returns `204 No Content`,
including when no relationship currently exists.

### 4.5 List a user's followers

```http
GET /api/users/{user_id}/followers/?page=1&page_size=10
```

Returns active public user profiles that follow `{user_id}`. The response uses
the standard `count`, `next`, `previous`, and `results` pagination envelope.
Results are stably ordered by name then UUID. Page size is capped at 100.

The endpoint requires JWT authentication. Results intentionally expose only
public-safe profile fields; email addresses, password data, and premium
expiration timestamps are not returned.

Response — `200 OK`:

```json
{
  "count": 2,
  "next": null,
  "previous": null,
  "results": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Ada",
      "avatar_url": "https://example.com/avatars/ada.jpg",
      "has_active_premium": false
    }
  ]
}
```

No followers returns `count: 0` and `results: []`. An inactive or unknown
`{user_id}` returns `404 Not Found`. Missing or invalid JWT authentication
returns `401 Unauthorized`.

### 4.6 List users somebody follows

```http
GET /api/users/{user_id}/following/?page=1&page_size=10
```

Returns active public profiles followed by `{user_id}`, using the same
pagination envelope and public profile format as the followers endpoint.
Results are stably ordered by name then UUID. Page size is capped at 100.

Response — `200 OK`:

```json
{
  "count": 1,
  "next": null,
  "previous": null,
  "results": [
    {
      "id": "f3ca373e-c936-4c7c-b649-f915e72e6a85",
      "name": "Aurora",
      "avatar_url": "https://example.com/avatars/aurora.jpg",
      "has_active_premium": true
    }
  ]
}
```

Nobody followed returns `count: 0` and `results: []`. An inactive or unknown
`{user_id}` returns `404 Not Found`. Missing or invalid JWT authentication
returns `401 Unauthorized`.

### 4.7 List somebody else's public playlists

```http
GET /api/users/{user_id}/playlists/?page=1&page_size=10
```

Returns only public playlists owned by the specified active user. Private
playlists and Liked Songs are excluded. Results use stable newest-first
ordering and the standard pagination envelope. Each result includes
`song_count`, so Android does not need to download the songs page merely to
show its size. Page size is capped at 100.

Response — `200 OK`:

```json
{
  "count": 1,
  "next": null,
  "previous": null,
  "results": [
    {
      "id": "f11581a5-a501-427c-b2bd-abbca759097f",
      "owner": {
        "id": "f3ca373e-c936-4c7c-b649-f915e72e6a85",
        "name": "Aurora",
        "email": "aurora@example.com",
        "premium_expires_at": "2026-08-23T17:30:00Z",
        "has_active_premium": true,
        "avatar_url": "https://example.com/avatars/aurora.jpg",
        "created_at": "2026-07-23T17:30:00Z",
        "updated_at": "2026-07-23T17:30:00Z"
      },
      "title": "Morning Mix",
      "description": "Music for the morning commute",
      "is_public": true,
      "is_liked": false,
      "song_count": 28,
      "follower_count": 12,
      "created_at": "2026-07-23T18:00:00Z",
      "updated_at": "2026-07-23T18:00:00Z"
    }
  ]
}
```

No public playlists returns `count: 0` and `results: []`. An inactive or
unknown `{user_id}` returns `404 Not Found`. Missing or invalid JWT
authentication returns `401 Unauthorized`.

### 4.8 Get the top-followed artists

```http
GET /api/users/top-artists/
Authorization: Bearer {access_token}
```

Returns a JSON array containing at most 10 active artists, ordered by follower
count descending. In this API, an artist is an active user who owns at least
one published song. Each result includes one published song—the artist's
newest published song—so the client can immediately show or play an example.

There is no request body and this endpoint is not paginated because its result
is strictly limited to 10 items.

Response — `200 OK`:

```json
[
  {
    "artist": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Aurora",
      "avatar_url": "https://media.example.com/avatars/aurora.jpg",
      "has_active_premium": true
    },
    "follower_count": 2450,
    "song": {
      "id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19",
      "title": "Northern Lights",
      "artist": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Aurora",
        "avatar_url": "https://media.example.com/avatars/aurora.jpg",
        "has_active_premium": true
      },
      "cover_image_url": "https://media.example.com/covers/northern-lights.jpg",
      "audio_url": "https://media.example.com/audio/northern-lights.mp3",
      "duration": "00:03:42"
    }
  }
]
```

Artists with equal follower counts are ordered by name and then UUID. Missing
or invalid JWT authentication returns `401 Unauthorized`.

### 4.9 Get one user by UUID

```http
GET /api/users/{id}/
```

`{id}` is the user's UUID. Regular users can retrieve only their own account;
staff/admin users can retrieve any user.

Response — `200 OK`: one user object in the same format shown above.

### 4.10 Replace a user

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

### 4.11 Partially update a user

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

### 4.12 Delete a user

```http
DELETE /api/users/{id}/
```

There is no request or response JSON body. Success returns `204 No Content`.
Deleting a user also deletes playlists and follow records owned by that user.

### 4.13 Upload or replace the current user's avatar

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

### 4.14 Add or extend a subscription

```http
POST /api/users/subscription/
```

Adds a subscription period to the user identified by the JWT. No user UUID is
accepted, so an authenticated client can modify only its own subscription.

Request JSON:

```json
{
  "months": 3
}
```

`months` is required and accepts only these integer values:

- `1`
- `3`
- `6`
- `12`

Response — `200 OK`:

```json
{
  "months_added": 3,
  "premium_expires_at": "2026-10-24T12:30:00Z",
  "has_active_premium": true
}
```

If the user already has active premium, the selected number of calendar months
is added to the existing `premium_expires_at`. If premium is absent or expired,
the period begins at the server's current time. Calendar-month arithmetic is
used, so adding one month to January 31 expires on the last valid day of
February.

Invalid duration example — `400 Bad Request`:

```json
{
  "months": [
    "\"2\" is not a valid choice."
  ]
}
```

Missing or invalid JWT authentication returns `401 Unauthorized`.

> Security note: this endpoint currently grants premium immediately and does
> not verify a payment receipt. Before production release, call this operation
> only after server-side Google Play purchase verification, or restrict it to
> a trusted payment webhook/admin workflow.

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

Accepts `page` and `page_size`:

```http
GET /api/songs/?page=1&page_size=10
```

Response — `200 OK`: a pagination envelope whose `results` contains complete
accessible song objects. Songs use stable `created_at` descending then UUID
ordering.

### 5.2 Search songs by title or singer name

```http
GET /api/songs/search/?q=aurora&page=1&page_size=10
```

Query parameters:

- `q` — required, non-empty search text with a maximum length of 200
  characters.
- `page` — optional page number starting at 1.
- `page_size` — optional result count; defaults to 10 and is capped at 100.

Search is case-insensitive and matches either:

- The song `title`
- The singer/artist `name`

It uses the normal visibility rules, so regular users receive published songs
and their own unpublished songs, while staff can search all songs. Results use
stable newest-first ordering.

Response — `200 OK`:

```json
{
  "count": 24,
  "next": "http://10.0.2.2:8000/api/songs/search/?page=2&page_size=10&q=aurora",
  "previous": null,
  "results": [
    {
      "id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19",
      "title": "Aurora Lights",
      "artist": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Aurora Singer",
        "email": "artist@example.com",
        "premium_expires_at": null,
        "has_active_premium": false,
        "avatar_url": "https://example.com/avatar.jpg",
        "created_at": "2026-07-23T17:30:00Z",
        "updated_at": "2026-07-23T17:30:00Z"
      },
      "cover_image_url": "https://example.com/cover.jpg",
      "audio_url": "https://example.com/song.mp3",
      "duration": "00:03:42",
      "is_published": true,
      "created_at": "2026-07-24T09:00:00Z",
      "updated_at": "2026-07-24T09:00:00Z"
    }
  ]
}
```

No matches returns the same envelope with `count: 0` and `results: []`.

A missing or empty `q` returns `400 Bad Request`:

```json
{
  "q": [
    "This field may not be blank."
  ]
}
```

Missing or invalid JWT authentication returns `401 Unauthorized`.

### 5.3 Get the 10 most recently added songs

```http
GET /api/songs/recent/
```

Returns at most 10 accessible songs ordered by `created_at` descending, so the
newest song is the first array element. The endpoint requires a JWT access
token and accepts no request body or query parameters.

It follows the normal song visibility rules:

- Regular users receive published songs and their own unpublished songs.
- Staff/admin users can receive all songs.

Response — `200 OK`:

```json
[
  {
    "id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19",
    "title": "Newest Song",
    "artist": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Ada Lovelace",
      "email": "ada@example.com",
      "premium_expires_at": null,
      "has_active_premium": false,
      "avatar_url": "https://media.example.com/avatars/ada.jpg",
      "created_at": "2026-07-23T17:30:00Z",
      "updated_at": "2026-07-23T17:30:00Z"
    },
    "cover_image_url": "https://media.example.com/covers/newest.jpg",
    "audio_url": "https://media.example.com/audio/newest.mp3",
    "duration": "00:03:42",
    "is_published": true,
    "created_at": "2026-07-24T09:00:00Z",
    "updated_at": "2026-07-24T09:00:00Z"
  }
]
```

If no accessible songs exist, the response is an empty array:

```json
[]
```

Missing or invalid JWT authentication returns `401 Unauthorized`.

### 5.4 Get the 10 most popular songs

```http
GET /api/songs/popular/
Authorization: Bearer {access_token}
```

Returns at most 10 published songs ordered by `like_count` descending. A like
means that the song is present in a user's unique Liked Songs playlist
(`is_liked: true`). A user can therefore contribute at most one like to a song
because duplicate songs are prohibited inside a playlist.

Only songs with at least one like are returned. Ties are resolved by newest
song first and then UUID. There is no request body and the response is not
paginated because it is strictly limited to 10 items.

Response — `200 OK`:

```json
[
  {
    "id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19",
    "title": "Northern Lights",
    "artist": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Aurora",
      "avatar_url": "https://media.example.com/avatars/aurora.jpg",
      "has_active_premium": true
    },
    "cover_image_url": "https://media.example.com/covers/northern-lights.jpg",
    "audio_url": "https://media.example.com/audio/northern-lights.mp3",
    "duration": "00:03:42",
    "like_count": 817
  }
]
```

If no published song has been liked, the response is `[]`. Missing or invalid
JWT authentication returns `401 Unauthorized`.

### 5.5 Create a song

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

### 5.6 Get one song

```http
GET /api/songs/{id}/
```

`{id}` is the song UUID. Response — `200 OK`: one complete song object.

### 5.7 Replace a song

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

### 5.8 Partially update a song

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

### 5.9 Delete a song

```http
DELETE /api/songs/{id}/
```

No JSON body. Success returns `204 No Content`.

### 5.10 Get a random next song outside a playlist

```http
POST /api/songs/random-next/
```

Chooses a random accessible song for playback when the current song is not
being played from a playlist. The current song is excluded so the response is
another song.

Request:

```json
{
  "song_id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19"
}
```

`song_id` may be `null` or omitted when there is no current song:

```json
{
  "song_id": null
}
```

Response — `200 OK`: one complete song object using the
[Song JSON format](#song-json-format).

If no other accessible song exists, the endpoint returns `404 Not Found`:

```json
{
  "detail": "No other accessible song is available."
}
```

## 6. Playlist APIs

Authenticated users can create playlists. The authenticated creator becomes
the owner. Public playlists are visible to all authenticated users; private
playlists are visible only to their owner and staff. Only the owner or staff
can update or delete a playlist.

Every user owns exactly one private playlist with `"is_liked": true`. Existing
users receive it through a database migration and new users receive it during
registration. Its default title is `Liked Songs`, and it cannot be deleted.
The visibility migration makes all pre-existing normal playlists public;
Liked Songs remains private.

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
  "is_liked": false,
  "song_count": 28,
  "follower_count": 12,
  "created_at": "2026-07-23T18:00:00Z",
  "updated_at": "2026-07-23T18:00:00Z"
}
```

`song_count` lets Android display the playlist size without downloading song
IDs. `owner`, `is_liked`, `song_count`, `follower_count`, IDs, and timestamps
are read-only. Fetch complete songs through the dedicated paginated playlist
songs endpoint.

### 6.1 List playlists

```http
GET /api/playlists/
```

Accepts `page` and `page_size`. Response — `200 OK`: a pagination envelope
containing public playlists and the current user's private playlists. Ordering
is stable: newest `created_at` first, then UUID.

### 6.2 List the authenticated user's playlists

```http
GET /api/playlists/me/?page=1&page_size=10
```

Returns only playlists owned by the user identified by the JWT. This includes
the user's private `Liked Songs` playlist and excludes public playlists owned
by other users. Results are ordered newest first.

There is no request body or user UUID path parameter.

`page` starts at 1. `page_size` defaults to 10 and is capped at 100.

Response — `200 OK`: a Paging 3-compatible pagination envelope:

```json
{
  "count": 34,
  "next": "http://10.0.2.2:8000/api/playlists/me/?page=2&page_size=10",
  "previous": null,
  "results": [
    {
      "id": "f11581a5-a501-427c-b2bd-abbca759097f",
      "owner": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Ada Lovelace",
        "email": "ada@example.com",
        "premium_expires_at": null,
        "has_active_premium": false,
        "avatar_url": "https://media.example.com/avatars/ada.jpg",
        "created_at": "2026-07-23T17:30:00Z",
        "updated_at": "2026-07-23T17:30:00Z"
      },
      "title": "Liked Songs",
      "description": "Songs liked by this user.",
      "is_public": false,
      "is_liked": true,
      "song_count": 28,
      "follower_count": 0,
      "created_at": "2026-07-24T10:00:00Z",
      "updated_at": "2026-07-24T10:00:00Z"
    }
  ]
}
```

Missing or invalid JWT authentication returns `401 Unauthorized`.

### 6.3 Create a playlist

```http
POST /api/playlists/
```

Request:

```json
{
  "title": "Morning Mix",
  "description": "Music for the morning commute",
  "is_public": true
}
```

The authenticated user becomes the owner. New playlists return
`"song_count": 0`. Response — `201 Created`: the complete playlist.

### 6.4 Get one playlist

```http
GET /api/playlists/{id}/
```

`{id}` is the playlist UUID. Response — `200 OK`: one playlist object.

### 6.5 Replace a playlist

```http
PUT /api/playlists/{id}/
```

Request:

```json
{
  "title": "Updated Morning Mix",
  "description": "An updated description",
  "is_public": false
}
```

Response — `200 OK`: the updated playlist.

### 6.6 Partially update a playlist

```http
PATCH /api/playlists/{id}/
```

Example:

```json
{
  "is_public": false
}
```

Response — `200 OK`: the updated playlist.

### 6.7 Change a playlist between public and private

```http
PATCH /api/playlists/{id}/visibility/
```

Only the playlist owner or staff/admin can change visibility.

Request:

```json
{
  "is_public": true
}
```

Set `is_public` to `false` to make it private. Response — `200 OK`: the updated
playlist object. The special Liked Songs playlist must remain private; trying
to make it public returns `400 Bad Request`.

### 6.8 Delete a playlist

```http
DELETE /api/playlists/{id}/
```

No JSON body. Success returns `204 No Content`. Associated follow records are
and ordered song memberships are deleted automatically. Deleting the special
Liked Songs playlist returns `400 Bad Request`:

```json
[
  "The Liked Songs playlist cannot be deleted."
]
```

### 6.9 List a playlist's songs with pagination

```http
GET /api/playlists/{playlist_id}/songs/?page=1&page_size=10
```

Returns complete song objects in stable playlist-membership position order.
This avoids one additional API request per song. The playlist must be public,
owned by the authenticated user, or accessible to staff.

Response — `200 OK`:

```json
{
  "count": 28,
  "next": "http://10.0.2.2:8000/api/playlists/f11581a5-a501-427c-b2bd-abbca759097f/songs/?page=2&page_size=10",
  "previous": null,
  "results": [
    {
      "id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19",
      "title": "Song title",
      "artist": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Artist name",
        "email": "artist@example.com",
        "premium_expires_at": null,
        "has_active_premium": false,
        "avatar_url": "https://example.com/avatar.jpg",
        "created_at": "2026-07-23T17:30:00Z",
        "updated_at": "2026-07-23T17:30:00Z"
      },
      "cover_image_url": "https://example.com/cover.jpg",
      "audio_url": "https://example.com/song.mp3",
      "duration": "00:03:42",
      "is_published": true,
      "created_at": "2026-07-23T17:40:00Z",
      "updated_at": "2026-07-23T17:40:00Z"
    }
  ]
}
```

`page_size` is capped at 100. An empty playlist returns the same envelope with
`count: 0`, null navigation links, and an empty `results` array.

### 6.10 Add a song to a playlist

```http
POST /api/playlists/{id}/songs/
```

`{id}` is the playlist UUID. Only its owner or a staff/admin user can add
songs. The song is appended to the end of the playlist's playback order.

Request:

```json
{
  "song_id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19"
}
```

The selected song must be published, owned by the current user, or accessible
to staff. Response — `201 Created`: the complete added song object.

Adding the same song twice returns `400 Bad Request`:

```json
{
  "song_id": [
    "Song is already in this playlist."
  ]
}
```

### 6.11 Remove a song from a playlist

```http
DELETE /api/playlists/{playlist_id}/songs/{song_id}/
```

Path fields:

- `{playlist_id}` is the UUID of the playlist being modified.
- `{song_id}` is the UUID of the song membership to remove.

Only the playlist owner or a staff/admin user can perform this action. It
removes the song from the playlist but does not delete the `Song` record or its
MinIO audio and cover objects. It can also be used to unlike a song by removing
it from the user's protected Liked Songs playlist.

There is no request JSON body. Success returns `204 No Content` with an empty
response body.

If the song is not in the playlist, the endpoint returns `404 Not Found`:

```json
{
  "detail": "Song is not in this playlist."
}
```

A non-owner receives `403 Forbidden`. Missing or invalid JWT authentication
returns `401 Unauthorized`.

### 6.12 Get the next song in a playlist

```http
POST /api/playlists/{id}/next-song/
```

This endpoint is available to anyone who can read the playlist: the owner,
staff, or any authenticated user for a public playlist.

Request:

```json
{
  "song_id": "b90fdd61-f70c-41bf-8ad0-e5059f334c19",
  "shuffle": false
}
```

Fields:

- `song_id` is the currently playing song UUID. It may be `null` or omitted.
- `shuffle` is optional and defaults to `false`.

Start a playlist from its first ordered song:

```json
{
  "song_id": null,
  "shuffle": false
}
```

Behavior:

- When `song_id` is `null` or omitted and `shuffle` is `false`, the first
  ordered song is returned.
- When `song_id` is `null` or omitted and `shuffle` is `true`, a random song
  from the playlist is returned. This supports starting playback in shuffle
  mode with only `{"shuffle": true}`.
- With `shuffle: false`, the next ordered song is returned.
- At the end of the playlist, non-shuffle playback wraps to the first song.
- With `shuffle: true`, a random playlist song other than the current one is
  returned when the playlist has multiple songs.
- A one-song playlist returns its only song in either mode.

Response — `200 OK`: one complete song object using the
[Song JSON format](#song-json-format).

An empty playlist returns `404 Not Found`:

```json
{
  "detail": "Playlist has no songs."
}
```

If `song_id` is not part of the playlist, the endpoint returns `400 Bad
Request`:

```json
{
  "song_id": [
    "The current song is not in this playlist."
  ]
}
```

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
This list endpoint is not paginated.

Response — `200 OK`:

```json
[
  {
    "id": "62481fc6-867b-4100-a75e-b784b31a45f3",
    "user": "f3ca373e-c936-4c7c-b649-f915e72e6a85",
    "playlist": "f11581a5-a501-427c-b2bd-abbca759097f",
    "created_at": "2026-07-23T18:10:00Z",
    "updated_at": "2026-07-23T18:10:00Z"
  }
]
```

No follows returns `[]`. Missing or invalid JWT authentication returns
`401 Unauthorized`.

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

The authenticated JWT user becomes `user`. Response — `201 Created`: the
complete follow object using the [Follow JSON format](#follow-json-format).

Validation failures return `400 Bad Request`, for example when following your
own playlist, a private playlist, or a playlist that is already followed:

```json
{
  "playlist": [
    "You cannot follow your own playlist."
  ]
}
```

Missing or invalid JWT authentication returns `401 Unauthorized`.

### 7.3 Get one follow

```http
GET /api/playlist-follows/{id}/
```

`{id}` is the follow-record UUID, not the playlist UUID. Regular users can
retrieve only their own follow records; staff can retrieve any follow.

Response — `200 OK`: one follow object using the
[Follow JSON format](#follow-json-format).

An unknown or inaccessible follow returns `404 Not Found`. Missing or invalid
JWT authentication returns `401 Unauthorized`.

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
Response — `200 OK`: the updated follow using the
[Follow JSON format](#follow-json-format).

The same playlist validation rules as create apply. Missing or invalid JWT
authentication returns `401 Unauthorized`.

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

Response — `200 OK`: the updated follow using the
[Follow JSON format](#follow-json-format).

Missing or invalid JWT authentication returns `401 Unauthorized`.

### 7.6 Delete/unfollow

```http
DELETE /api/playlist-follows/{id}/
```

`{id}` is the follow-record UUID. Only the follow owner or staff can delete it.
There is no request JSON body. Success returns `204 No Content` with an empty
response body.

An unknown or inaccessible follow returns `404 Not Found`. Missing or invalid
JWT authentication returns `401 Unauthorized`.

## 8. Real-time direct chat

Direct messages use a persistent WebSocket connection. Do not poll the REST
history endpoints for live updates. PostgreSQL stores every accepted message;
Redis is used only for real-time channel delivery.

### 8.1 Connect to another user

Development Android Emulator URL:

```text
ws://10.0.2.2:8000/ws/chat/{other_user_id}/
```

Production must use `wss://`. `{other_user_id}` is the UUID of the person being
messaged. Self-conversations are rejected.

Recommended WebSocket handshake header:

```http
Authorization: Bearer <access-jwt>
```

For clients that cannot send handshake headers, a query-token fallback is
supported:

```text
ws://10.0.2.2:8000/ws/chat/{other_user_id}/?token=<access-jwt>
```

Headers are safer because query strings may appear in proxy logs. Invalid or
expired tokens close with code `4401`; self-chat uses `4403`; a missing target
uses `4404`.

After connection, the server sends:

```json
{
  "type": "conversation.ready",
  "conversation_id": "conversation-uuid",
  "other_user": {
    "id": "user-uuid",
    "name": "Friend",
    "avatar_url": "https://example.com/friend.jpg",
    "has_active_premium": false
  }
}
```

### 8.2 Send a text message

Client to server:

```json
{
  "type": "message.send",
  "client_message_id": "52fcf210-a111-4c2e-bb50-98f38de0ad40",
  "message_type": "text",
  "body": "Hello"
}
```

`client_message_id` must be a newly generated UUID. Retrying the same UUID is
idempotent and does not create a duplicate database message.

The server broadcasts:

```json
{
  "type": "message.created",
  "message": {
    "id": "message-uuid",
    "conversation_id": "conversation-uuid",
    "client_message_id": "52fcf210-a111-4c2e-bb50-98f38de0ad40",
    "sender": {
      "id": "sender-uuid",
      "name": "Sender",
      "avatar_url": "https://example.com/sender.jpg",
      "has_active_premium": false
    },
    "message_type": "text",
    "body": "Hello",
    "song": null,
    "status": "sent",
    "delivered_at": null,
    "read_at": null,
    "created_at": "2026-07-24T20:30:00Z"
  }
}
```

### 8.3 Send/share a song

Client to server:

```json
{
  "type": "message.send",
  "client_message_id": "f11e595f-6c9e-43b4-a500-6024e50f92db",
  "message_type": "song",
  "song_id": "song-uuid",
  "body": "Listen to this"
}
```

Only published songs can be shared. The resulting `message.created` includes a
mini-card-ready `song` object:

```json
{
  "id": "song-uuid",
  "title": "Song title",
  "artist": {
    "id": "artist-uuid",
    "name": "Artist",
    "avatar_url": "https://example.com/artist.jpg",
    "has_active_premium": true
  },
  "cover_image_url": "https://example.com/cover.jpg",
  "audio_url": "https://example.com/audio.mp3",
  "duration": "0:03:42"
}
```

Android can render this directly as the custom mini card and pass `audio_url`
to the player when tapped.

### 8.4 Typing indicator

Client to server when typing begins or ends:

```json
{
  "type": "typing",
  "is_typing": true
}
```

The other connected user receives:

```json
{
  "type": "typing",
  "user_id": "typing-user-uuid",
  "is_typing": true
}
```

Typing events are ephemeral and are not stored in PostgreSQL.

### 8.5 Delivery and read receipts

Message UI states map as follows:

- `sending` — Android-local state before `message.created` is received; show
  the clock.
- `sent` — persisted by the server; show one tick.
- `delivered` — delivered to the recipient socket or fetched during offline
  synchronization.
- `read` — explicitly marked by the recipient; show two ticks.

The recipient may send:

```json
{
  "type": "message.delivered",
  "message_id": "message-uuid"
}
```

or:

```json
{
  "type": "message.read",
  "message_id": "message-uuid"
}
```

The server broadcasts:

```json
{
  "type": "message.receipt",
  "message_id": "message-uuid",
  "status": "read",
  "delivered_at": "2026-07-24T20:30:02Z",
  "read_at": "2026-07-24T20:30:05Z"
}
```

Delivery is also marked automatically when the recipient socket receives a
message or reconnects with pending messages.

### 8.6 Paginated conversation list

```http
GET /api/chat/conversations/?page=1&page_size=10
Authorization: Bearer <access-jwt>
```

Returns the standard pagination envelope. Each result contains:

```json
{
  "id": "conversation-uuid",
  "other_user": {
    "id": "user-uuid",
    "name": "Friend",
    "avatar_url": "https://example.com/friend.jpg",
    "has_active_premium": false
  },
  "last_message": {
    "id": "message-uuid",
    "message_type": "text",
    "body": "Hello",
    "status": "read",
    "song": null,
    "created_at": "2026-07-24T20:30:00Z"
  },
  "unread_count": 0,
  "created_at": "2026-07-24T20:00:00Z",
  "updated_at": "2026-07-24T20:30:00Z"
}
```

Only conversations containing the authenticated user are returned. Ordering
is newest activity first. Page size is capped at 100.

### 8.7 Paginated message history and offline synchronization

```http
GET /api/chat/users/{other_user_id}/messages/?page=1&page_size=50
Authorization: Bearer <access-jwt>
```

Returns complete message objects in newest-first stable order using `count`,
`next`, `previous`, and `results`. Page size is capped at 100. Fetching history
marks incoming undelivered messages as delivered and notifies a connected
sender in real time. If no conversation exists yet, the server creates an empty
one before returning an empty `results` array.

REST message objects use a `conversation` UUID field. WebSocket
`message.created` payloads use `conversation_id` for the same value.

Response — `200 OK`:

```json
{
  "count": 1,
  "next": null,
  "previous": null,
  "results": [
    {
      "id": "message-uuid",
      "conversation": "conversation-uuid",
      "client_message_id": "52fcf210-a111-4c2e-bb50-98f38de0ad40",
      "sender": {
        "id": "sender-uuid",
        "name": "Sender",
        "avatar_url": "https://example.com/sender.jpg",
        "has_active_premium": false
      },
      "message_type": "text",
      "body": "Hello",
      "song": null,
      "status": "delivered",
      "delivered_at": "2026-07-24T20:30:02Z",
      "read_at": null,
      "created_at": "2026-07-24T20:30:00Z"
    }
  ]
}
```

A song message includes a mini-card `song` object instead of `null`, matching
the shape shown in [8.3 Send/share a song](#83-sendshare-a-song).

This endpoint is for initial load, reconnect synchronization, and Paging 3—not
live polling. Android should upsert `results` into its Room database. Socket
`message.created` and `message.receipt` events should update the same Room
rows. This preserves chat history when internet connectivity is lost.

Requesting your own `{other_user_id}` returns `400 Bad Request`. An inactive or
unknown user returns `404 Not Found`. Missing or invalid JWT authentication
returns `401 Unauthorized`.

### 8.8 REST read fallback

```http
POST /api/chat/messages/{message_id}/read/
Authorization: Bearer <access-jwt>
```

There is no request body. Only the recipient can mark a message read. The
sender cannot mark their own message. If `delivered_at` was still null, the
server also sets delivery before setting `read_at`.

Response — `200 OK`:

```json
{
  "id": "message-uuid",
  "conversation": "conversation-uuid",
  "client_message_id": "52fcf210-a111-4c2e-bb50-98f38de0ad40",
  "sender": {
    "id": "sender-uuid",
    "name": "Sender",
    "avatar_url": "https://example.com/sender.jpg",
    "has_active_premium": false
  },
  "message_type": "text",
  "body": "Hello",
  "song": null,
  "status": "read",
  "delivered_at": "2026-07-24T20:30:02Z",
  "read_at": "2026-07-24T20:30:05Z",
  "created_at": "2026-07-24T20:30:00Z"
}
```

The sender also receives a real-time `message.receipt` event when connected:

```json
{
  "type": "message.receipt",
  "message_id": "message-uuid",
  "status": "read",
  "delivered_at": "2026-07-24T20:30:02Z",
  "read_at": "2026-07-24T20:30:05Z"
}
```

Marking your own sent message returns `400 Bad Request`. An unknown message or
a message outside the caller's conversations returns `404 Not Found`. Missing
or invalid JWT authentication returns `401 Unauthorized`.

### 8.9 WebSocket errors

Protocol validation errors do not close the connection:

```json
{
  "type": "error",
  "code": "invalid_client_message_id",
  "detail": "client_message_id must be a UUID."
}
```

Possible codes include `unsupported_event`, `invalid_client_message_id`,
`empty_message`, `missing_song`, `invalid_message_type`, `song_not_found`,
`invalid_message_id`, and `message_not_found`.

## 9. API documentation endpoints

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

## 10. Errors and status codes

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

## 11. Password hashing and security

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

## 12. MinIO media URLs

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

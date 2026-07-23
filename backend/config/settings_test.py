from .settings import *  # noqa: F403


DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": ":memory:",
    }
}

# Tests do not read or write media objects.
STORAGES["default"] = {  # noqa: F405
    "BACKEND": "django.core.files.storage.InMemoryStorage",
}

PASSWORD_HASHERS = [
    "django.contrib.auth.hashers.MD5PasswordHasher",
]

from django.apps import AppConfig


class SpotifyConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "spotify"

    def ready(self):
        from . import signals  # noqa: F401

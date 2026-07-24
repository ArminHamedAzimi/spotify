from django.db.models.signals import post_save
from django.dispatch import receiver

from .models import Playlist, User


@receiver(post_save, sender=User)
def create_liked_playlist(sender, instance, created, **kwargs):
    if created:
        Playlist.objects.get_or_create(
            owner=instance,
            is_liked=True,
            defaults={
                "title": "Liked Songs",
                "description": "Songs liked by this user.",
                "is_public": False,
            },
        )

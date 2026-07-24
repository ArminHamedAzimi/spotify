from django.db import migrations, models
from django.db.models import Q


class Migration(migrations.Migration):
    dependencies = [
        ("spotify", "0003_playlist_order_and_liked"),
    ]

    operations = [
        migrations.AddConstraint(
            model_name="playlist",
            constraint=models.UniqueConstraint(
                condition=Q(("is_liked", True)),
                fields=("owner",),
                name="unique_liked_playlist_per_owner",
            ),
        ),
        migrations.AddConstraint(
            model_name="playlistsong",
            constraint=models.UniqueConstraint(
                fields=("playlist", "song"),
                name="unique_song_per_playlist",
            ),
        ),
        migrations.AddConstraint(
            model_name="playlistsong",
            constraint=models.UniqueConstraint(
                fields=("playlist", "position"),
                name="unique_playlist_song_position",
            ),
        ),
    ]

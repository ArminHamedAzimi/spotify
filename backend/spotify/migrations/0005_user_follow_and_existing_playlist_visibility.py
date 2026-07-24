import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models
from django.db.models import F, Q


def make_existing_playlists_public(apps, schema_editor):
    Playlist = apps.get_model("spotify", "Playlist")
    Playlist.objects.using(schema_editor.connection.alias).filter(
        is_liked=False
    ).update(is_public=True)


class Migration(migrations.Migration):
    dependencies = [
        ("spotify", "0004_playlist_constraints"),
    ]

    operations = [
        migrations.CreateModel(
            name="UserFollow",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                    ),
                ),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                (
                    "follower",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="following_relationships",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
                (
                    "following",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="follower_relationships",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={"ordering": ["-created_at"]},
        ),
        migrations.AddConstraint(
            model_name="userfollow",
            constraint=models.UniqueConstraint(
                fields=("follower", "following"),
                name="unique_user_follow",
            ),
        ),
        migrations.AddConstraint(
            model_name="userfollow",
            constraint=models.CheckConstraint(
                condition=~Q(("follower", F("following"))),
                name="prevent_self_follow",
            ),
        ),
        migrations.RunPython(
            make_existing_playlists_public,
            reverse_code=migrations.RunPython.noop,
        ),
    ]

import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models
from django.db.models import F, Q


class Migration(migrations.Migration):
    dependencies = [
        ("spotify", "0005_user_follow_and_existing_playlist_visibility"),
    ]

    operations = [
        migrations.CreateModel(
            name="DirectConversation",
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
                    "user_one",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="direct_conversations_as_one",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
                (
                    "user_two",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="direct_conversations_as_two",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={"ordering": ["-updated_at"]},
        ),
        migrations.CreateModel(
            name="DirectMessage",
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
                ("client_message_id", models.UUIDField(default=uuid.uuid4)),
                (
                    "message_type",
                    models.CharField(
                        choices=[("text", "Text"), ("song", "Song")],
                        default="text",
                        max_length=10,
                    ),
                ),
                ("body", models.TextField(blank=True)),
                ("delivered_at", models.DateTimeField(blank=True, null=True)),
                ("read_at", models.DateTimeField(blank=True, null=True)),
                (
                    "conversation",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="messages",
                        to="spotify.directconversation",
                    ),
                ),
                (
                    "sender",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="sent_direct_messages",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
                (
                    "song",
                    models.ForeignKey(
                        blank=True,
                        null=True,
                        on_delete=django.db.models.deletion.PROTECT,
                        related_name="shared_in_messages",
                        to="spotify.song",
                    ),
                ),
            ],
            options={"ordering": ["-created_at", "-id"]},
        ),
        migrations.AddConstraint(
            model_name="directconversation",
            constraint=models.UniqueConstraint(
                fields=("user_one", "user_two"),
                name="unique_direct_conversation",
            ),
        ),
        migrations.AddConstraint(
            model_name="directconversation",
            constraint=models.CheckConstraint(
                condition=Q(("user_one__lt", F("user_two"))),
                name="canonical_direct_conversation_users",
            ),
        ),
        migrations.AddConstraint(
            model_name="directmessage",
            constraint=models.UniqueConstraint(
                fields=("sender", "client_message_id"),
                name="unique_sender_client_message",
            ),
        ),
        migrations.AddConstraint(
            model_name="directmessage",
            constraint=models.CheckConstraint(
                condition=(
                    Q(("message_type", "text"), ("song__isnull", True))
                    & ~Q(("body", ""))
                    | Q(("message_type", "song"), ("song__isnull", False))
                ),
                name="valid_direct_message_content",
            ),
        ),
    ]

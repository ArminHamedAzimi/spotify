from datetime import timedelta

from django.db import migrations, models
from django.utils import timezone


def convert_remaining_time_to_expiration(apps, schema_editor):
    User = apps.get_model("spotify", "User")
    now = timezone.now()
    users = User.objects.using(schema_editor.connection.alias).exclude(
        premium_time_remaining__lte=timedelta(0)
    )
    for user in users.iterator():
        user.premium_expires_at = now + user.premium_time_remaining
        user.save(update_fields=["premium_expires_at"])


class Migration(migrations.Migration):
    dependencies = [
        ("spotify", "0001_initial"),
    ]

    operations = [
        migrations.AddField(
            model_name="user",
            name="premium_expires_at",
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.RunPython(
            convert_remaining_time_to_expiration,
            reverse_code=migrations.RunPython.noop,
        ),
        migrations.RemoveConstraint(
            model_name="user",
            name="user_premium_time_non_negative",
        ),
        migrations.RemoveField(
            model_name="user",
            name="premium_time_remaining",
        ),
    ]

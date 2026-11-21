from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('whatsapp', '0001_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='conversacion',
            name='carrito',
            field=models.JSONField(blank=True, default=dict),
        ),
    ]

from __future__ import annotations

import logging
import os
import smtplib
from email.mime.text import MIMEText

import pika

from common.models import EmailEvent

LOGGER = logging.getLogger(__name__)


class EmailSender:
    def __init__(
        self,
        smtp_host: str,
        smtp_port: int,
        smtp_user: str,
        smtp_password: str,
        from_email: str,
    ) -> None:
        self.smtp_host = smtp_host
        self.smtp_port = smtp_port
        self.smtp_user = smtp_user
        self.smtp_password = smtp_password
        self.from_email = from_email

    def send_result_email(self, event: EmailEvent) -> None:
        body = (
            f"Hola {event.student_name},\n\n"
            f"Tu evaluación {event.evaluation_id} fue procesada con una calificación de {event.score}.\n"
        )
        message = MIMEText(body)
        message["Subject"] = "Resultado de evaluación"
        message["From"] = self.from_email
        message["To"] = event.student_email

        with smtplib.SMTP(self.smtp_host, self.smtp_port, timeout=20) as smtp:
            smtp.starttls()
            smtp.login(self.smtp_user, self.smtp_password)
            smtp.sendmail(self.from_email, [event.student_email], message.as_string())


def consume_email_notifications(
    rabbitmq_host: str,
    queue_name: str,
    email_sender: EmailSender,
) -> None:
    connection = pika.BlockingConnection(pika.ConnectionParameters(host=rabbitmq_host))
    channel = connection.channel()
    channel.queue_declare(queue=queue_name, durable=True)

    def callback(ch, method, properties, body):
        retry_count = int((properties.headers or {}).get("x-retries", 0)) if properties else 0
        try:
            event = EmailEvent.from_json(body.decode("utf-8"))
            email_sender.send_result_email(event)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception:
            LOGGER.exception("Failed to process email event. retry=%s", retry_count)
            if retry_count < 3:
                ch.basic_publish(
                    exchange="",
                    routing_key=queue_name,
                    body=body,
                    properties=pika.BasicProperties(
                        delivery_mode=2,
                        headers={"x-retries": retry_count + 1},
                    ),
                )
                ch.basic_ack(delivery_tag=method.delivery_tag)
            else:
                ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    channel.basic_qos(prefetch_count=10)
    channel.basic_consume(queue=queue_name, on_message_callback=callback)
    print("Email consumer running")
    channel.start_consuming()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    sender = EmailSender(
        smtp_host=os.getenv("SMTP_HOST", "smtp.gmail.com"),
        smtp_port=int(os.getenv("SMTP_PORT", "587")),
        smtp_user=os.getenv("SMTP_USER", "user@example.com"),
        smtp_password=os.getenv("SMTP_PASSWORD", "changeme"),
        from_email=os.getenv("SMTP_FROM", "noreply@example.com"),
    )
    consume_email_notifications(
        rabbitmq_host=os.getenv("RABBITMQ_HOST", "localhost"),
        queue_name=os.getenv("RABBITMQ_QUEUE", "email_notifications"),
        email_sender=sender,
    )

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List

try:
    import pika
except ModuleNotFoundError:  # pragma: no cover - optional for local tests
    pika = None

from common.models import EmailEvent


class QueuePublisher(ABC):
    @abstractmethod
    def publish_email_result(self, event: EmailEvent) -> None:
        raise NotImplementedError


class RabbitMQPublisher(QueuePublisher):
    def __init__(self, host: str = "localhost", queue_name: str = "email_notifications") -> None:
        self.host = host
        self.queue_name = queue_name

    def publish_email_result(self, event: EmailEvent) -> None:
        if pika is None:
            raise RuntimeError("pika is required to publish messages to RabbitMQ")
        connection = pika.BlockingConnection(pika.ConnectionParameters(host=self.host))
        try:
            channel = connection.channel()
            channel.queue_declare(queue=self.queue_name, durable=True)
            channel.basic_publish(
                exchange="",
                routing_key=self.queue_name,
                body=event.to_json().encode("utf-8"),
                properties=pika.BasicProperties(delivery_mode=2),
            )
        finally:
            connection.close()


class InMemoryPublisher(QueuePublisher):
    def __init__(self) -> None:
        self.messages: List[EmailEvent] = []

    def publish_email_result(self, event: EmailEvent) -> None:
        self.messages.append(event)

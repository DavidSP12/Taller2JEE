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
        self._connection = None
        self._channel = None

    def _ensure_channel(self):
        if pika is None:
            raise RuntimeError("pika is required to publish messages to RabbitMQ")
        if self._connection is None or self._connection.is_closed:
            self._connection = pika.BlockingConnection(pika.ConnectionParameters(host=self.host))
            self._channel = self._connection.channel()
            self._channel.queue_declare(queue=self.queue_name, durable=True)
        return self._channel

    def publish_email_result(self, event: EmailEvent) -> None:
        channel = self._ensure_channel()
        channel.basic_publish(
            exchange="",
            routing_key=self.queue_name,
            body=event.to_json().encode("utf-8"),
            properties=pika.BasicProperties(delivery_mode=2),
        )

    def close(self) -> None:
        if self._connection is not None and not self._connection.is_closed:
            self._connection.close()


class InMemoryPublisher(QueuePublisher):
    def __init__(self) -> None:
        self.messages: List[EmailEvent] = []

    def publish_email_result(self, event: EmailEvent) -> None:
        self.messages.append(event)

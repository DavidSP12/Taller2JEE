package com.taller2jee.email;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.taller2jee.common.model.EmailEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class EmailConsumerApplication {
    private EmailConsumerApplication() {
    }

    public static void main(String[] args) throws Exception {
        String rabbitmqHost = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        String queueName = System.getenv().getOrDefault("RABBITMQ_QUEUE", "email_notifications");

        EmailSender emailSender = new EmailSender(
                System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com"),
                Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "587")),
                System.getenv().getOrDefault("SMTP_USER", "user@example.com"),
                System.getenv().getOrDefault("SMTP_PASSWORD", "changeme"),
                System.getenv().getOrDefault("SMTP_FROM", "noreply@example.com")
        );

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitmqHost);
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(queueName, true, false, false, null);
        channel.basicQos(10);

        DeliverCallback callback = (consumerTag, delivery) -> {
            int retryCount = extractRetryCount(delivery.getProperties());
            try {
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
                EmailEvent event = EmailEvent.fromJson(body);
                emailSender.sendResultEmail(event);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                System.err.printf("Failed to process email event. retry=%d%n", retryCount);
                if (retryCount < 3) {
                    Map<String, Object> headers = new HashMap<>();
                    headers.put("x-retries", retryCount + 1);
                    channel.basicPublish(
                            "",
                            queueName,
                            new AMQP.BasicProperties.Builder().deliveryMode(2).headers(headers).build(),
                            delivery.getBody()
                    );
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } else {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                }
            }
        };

        CancelCallback cancelCallback = consumerTag -> {};

        System.out.println("Email consumer running");
        channel.basicConsume(queueName, false, callback, cancelCallback);
    }

    private static int extractRetryCount(AMQP.BasicProperties properties) {
        if (properties == null || properties.getHeaders() == null) {
            return 0;
        }
        Object raw = properties.getHeaders().get("x-retries");
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(raw.toString());
    }
}

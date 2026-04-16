package com.taller2jee.logic.queue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.taller2jee.common.model.EmailEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class RabbitMQPublisher implements QueuePublisher, AutoCloseable {
    private final String host;
    private final String queueName;
    private Connection connection;
    private Channel channel;

    public RabbitMQPublisher(String host, String queueName) {
        this.host = host;
        this.queueName = queueName;
    }

    @Override
    public synchronized void publishEmailResult(EmailEvent event) {
        try {
            ensureChannel();
            channel.basicPublish(
                    "",
                    queueName,
                    new AMQP.BasicProperties.Builder().deliveryMode(2).build(),
                    event.toJson().getBytes(StandardCharsets.UTF_8)
            );
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Unable to publish email event", e);
        }
    }

    private void ensureChannel() throws IOException, TimeoutException {
        if (connection != null && connection.isOpen() && channel != null && channel.isOpen()) {
            return;
        }
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(host);
        connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        channel.queueDeclare(queueName, true, false, false, null);
    }

    @Override
    public synchronized void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }
}

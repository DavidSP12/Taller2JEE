package com.taller2jee.logic.grpc;

import com.taller2jee.logic.persistence.ExamRepository;
import com.taller2jee.logic.persistence.StudentRepository;
import com.taller2jee.logic.queue.RabbitMQPublisher;
import com.taller2jee.logic.saga.EvaluationSagaOrchestrator;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.Map;

public final class GrpcServerApplication {
    private GrpcServerApplication() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String rabbitmqHost = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        String queueName = System.getenv().getOrDefault("RABBITMQ_QUEUE", "email_notifications");
        int port = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "50051"));

        EvaluationSagaOrchestrator orchestrator = new EvaluationSagaOrchestrator(
                new ExamRepository("data/exam.db"),
                new StudentRepository("data/student.db"),
                new RabbitMQPublisher(rabbitmqHost, queueName),
                Map.of("Q1", "A", "Q2", "C", "Q3", "B", "Q4", "D")
        );

        Server server = ServerBuilder.forPort(port)
                .addService(new EvaluationServiceImpl(orchestrator))
                .build();

        server.start();
        System.out.println("gRPC server listening on " + port);
        server.awaitTermination();
    }
}

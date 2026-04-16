# Taller2JEE — Sistema distribuido de evaluaciones (Jakarta / Java)

[![Tests](https://github.com/DavidSP12/Taller2JEE/actions/workflows/tests.yml/badge.svg)](https://github.com/DavidSP12/Taller2JEE/actions/workflows/tests.yml)

Migración completa del proyecto a **Java 17** usando APIs **Jakarta** y ORM con **Jakarta Persistence (JPA) + Hibernate**.

## Stack

- gRPC (`grpc-java`) para la API síncrona
- RabbitMQ (`amqp-client`) para mensajería asíncrona
- Jakarta Persistence (`jakarta.persistence`) + Hibernate ORM
- SQLite por defecto (`jdbc:sqlite`) con soporte para URL JDBC externa
- Jakarta Mail para envío de correos
- Maven como sistema de build

## Estructura

```text
.
├── proto/
│   └── evaluation.proto
├── src/
│   ├── main/
│   │   ├── java/com/taller2jee/
│   │   │   ├── common/model/          # Modelos de dominio
│   │   │   ├── logic/
│   │   │   │   ├── grpc/              # Servidor gRPC
│   │   │   │   ├── saga/              # Orquestación y compensación
│   │   │   │   ├── queue/             # Publicadores RabbitMQ/InMemory
│   │   │   │   └── persistence/       # Repositorios y entidades JPA
│   │   │   └── email/                 # Consumidor RabbitMQ + SMTP
│   │   └── resources/META-INF/persistence.xml
│   └── test/java/com/taller2jee/logic/saga/
│       └── EvaluationSagaOrchestratorTest.java
├── docker-compose.yml
└── pom.xml
```

## ORM (adaptado a Jakarta)

El ORM se migró de SQLAlchemy a **Jakarta Persistence**:

- `ExamSubmissionEntity` → tabla `exam_submissions`
- `StudentEntity` → tabla `students`
- `GradeEntity` → tabla `grades`

Los repositorios (`ExamRepository`, `StudentRepository`) aceptan:

- Ruta de archivo (`data/exam.db`) → se transforma a `jdbc:sqlite:/...`
- URL JDBC completa (`jdbc:postgresql://host:5432/taller2`)

## Ejecutar pruebas

```bash
mvn test
```

## Ejecutar servicios localmente

### logic_service (gRPC)

```bash
mvn -DskipTests compile exec:java -Dexec.mainClass=com.taller2jee.logic.grpc.GrpcServerApplication
```

### email_service (consumidor RabbitMQ)

```bash
mvn -DskipTests compile exec:java -Dexec.mainClass=com.taller2jee.email.EmailConsumerApplication
```

Variables de entorno soportadas:

- `RABBITMQ_HOST` (default: `localhost`)
- `RABBITMQ_QUEUE` (default: `email_notifications`)
- `GRPC_PORT` (default: `50051`)
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`

## Docker Compose

```bash
docker-compose up --build
```

Levanta:

- `logic_service` (gRPC en `50051`)
- `email_service`
- `rabbitmq` (`5672` AMQP, `15672` UI)

# Taller2JEE — Sistema distribuido de evaluaciones

[![Tests](https://github.com/DavidSP12/Taller2JEE/actions/workflows/tests.yml/badge.svg)](https://github.com/DavidSP12/Taller2JEE/actions/workflows/tests.yml)

Sistema de procesamiento de evaluaciones académicas implementado con arquitectura de microservicios en Python.  
Usa **gRPC** para la API sincrónica, **RabbitMQ** para mensajería asíncrona y **SQLAlchemy** como ORM sobre SQLite (o PostgreSQL en producción).

---

## Diagrama de arquitectura

```
[Cliente gRPC]
      │
      │  SubmitEvaluation (gRPC / puerto 50051)
      ▼
┌─────────────────────────────────────┐
│  logic_service  (WildFly / Docker)  │
│                                     │
│  ┌─────────────────────────────┐    │
│  │  EvaluationSagaOrchestrator │    │
│  │  (Saga de orquestación)     │    │
│  └──────┬──────────────────────┘    │
│         │                           │
│   ┌─────▼──────┐  ┌──────────────┐  │
│   │ExamRepo    │  │StudentRepo   │  │
│   │(SQLAlchemy)│  │(SQLAlchemy)  │  │
│   └─────┬──────┘  └──────┬───────┘  │
└─────────┼────────────────┼──────────┘
          │ SQLite / PostgreSQL        │
          ▼                           ▼
   [exam.db / DB]           [student.db / DB]

          │  (async publish)
          ▼
   [RabbitMQ — cola: email_notifications]
          │
          │  (consume)
          ▼
┌─────────────────────────────┐
│  email_service  (Docker)    │
│  Consumidor RabbitMQ        │
│  Envío de correo vía SMTP   │
└─────────────────────────────┘
          │
          │  SMTP (puerto 587)
          ▼
   [Servidor de correo]
```

**Flujo end-to-end:**

1. El cliente invoca `SubmitEvaluation` por gRPC.
2. `logic_service` calcula la nota con la clave de respuestas configurada.
3. La **Saga** guarda el envío en la DB de exámenes y la nota en la DB de estudiantes.
4. La Saga publica un `EmailEvent` en RabbitMQ (cola `email_notifications`).
5. `logic_service` responde `PROCESSED` al cliente **sin esperar** el correo.
6. `email_service` consume el evento y envía el correo vía SMTP.
7. Si cualquier paso de la Saga falla, se ejecutan **acciones compensatorias** para revertir lo guardado.

---

## Estructura de carpetas

```text
.
├── .github/
│   └── workflows/
│       └── tests.yml          # CI: ejecuta pruebas en cada push/PR
├── proto/
│   └── evaluation.proto       # Contrato gRPC (SubmitEvaluation)
├── src/
│   ├── client/
│   │   └── grpc_client.py     # Cliente de ejemplo
│   ├── common/
│   │   └── models.py          # Dataclasses compartidos (EvaluationSubmission, EmailEvent, etc.)
│   ├── email_service/
│   │   └── consumer.py        # Consumidor RabbitMQ + envío SMTP
│   └── logic_service/
│       ├── db_models.py       # Modelos ORM SQLAlchemy (ExamSubmissionRow, StudentRow, GradeRow)
│       ├── generated/         # Stubs gRPC generados por protoc
│       ├── queue/
│       │   └── producer.py    # RabbitMQPublisher + InMemoryPublisher (para tests)
│       ├── repositories/
│       │   ├── exam_repository.py     # CRUD de envíos de examen (ORM)
│       │   └── student_repository.py  # CRUD de estudiantes y notas (ORM)
│       ├── grpc_server.py     # Servidor gRPC + build_default_orchestrator()
│       └── saga.py            # EvaluationSagaOrchestrator (lógica + compensación)
├── tests/
│   └── test_saga.py           # Pruebas unitarias de la Saga
├── docker-compose.yml         # Levanta logic_service + email_service + rabbitmq
└── requirements.txt
```

---

## ORM — SQLAlchemy

La capa de datos usa **SQLAlchemy 2.x** como ORM.  No hay SQL manual en ningún repositorio.

### Modelos declarativos (`src/logic_service/db_models.py`)

| Clase ORM           | Tabla              | Descripción                              |
|---------------------|--------------------|------------------------------------------|
| `ExamSubmissionRow` | `exam_submissions` | Envío de examen con respuestas y puntaje |
| `StudentRow`        | `students`         | Registro maestro de estudiante           |
| `GradeRow`          | `grades`           | Nota individual por evaluación           |

### Configuración de base de datos

Por defecto los repositorios usan **SQLite** (sin configuración extra):

```
data/exam.db     ← ExamRepository
data/student.db  ← StudentRepository
```

Para usar **PostgreSQL**, pasa la URL de conexión directamente al repositorio o configúrala en `grpc_server.py`:

```python
ExamRepository("postgresql+psycopg2://user:pass@localhost:5432/taller2")
StudentRepository("postgresql+psycopg2://user:pass@localhost:5432/taller2")
```

---

## Transacciones distribuidas — Saga de orquestación

`EvaluationSagaOrchestrator` implementa el patrón **Saga con compensación**:

| Paso | Acción                                    | Compensación si falla        |
|------|-------------------------------------------|------------------------------|
| 1    | Guardar envío en DB de exámenes           | Borrar envío                 |
| 2    | Upsert estudiante en DB de estudiantes    | Borrar estudiante (si nuevo) |
| 3    | Guardar nota en DB de estudiantes         | Borrar nota                  |
| 4    | Publicar `EmailEvent` en RabbitMQ         | —                            |

Si cualquier paso lanza excepción, se aplican las compensaciones en orden inverso y se propaga `DistributedTransactionError`.

---

## Requisitos previos

- Python 3.11 o 3.12
- Docker + Docker Compose (para levantar con contenedores)

---

## Instalación

```bash
python -m pip install -r requirements.txt
```

### Generar stubs gRPC

Solo necesario si modificas `proto/evaluation.proto`:

```bash
python -m grpc_tools.protoc \
  -I./proto \
  --python_out=./src/logic_service/generated \
  --grpc_python_out=./src/logic_service/generated \
  ./proto/evaluation.proto
```

---

## Ejecución local

### 1. Levantar RabbitMQ (Docker)

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management
```

Panel de administración: http://localhost:15672 (guest / guest)

### 2. Iniciar logic_service

```bash
RABBITMQ_HOST=localhost PYTHONPATH=src python -m logic_service.grpc_server
```

Variables de entorno disponibles:

| Variable         | Valor por defecto     | Descripción                       |
|------------------|-----------------------|-----------------------------------|
| `RABBITMQ_HOST`  | `localhost`           | Host del broker                   |
| `RABBITMQ_QUEUE` | `email_notifications` | Nombre de la cola                 |
| `GRPC_PORT`      | `50051`               | Puerto gRPC del servicio          |

### 3. Iniciar email_service

```bash
RABBITMQ_HOST=localhost \
SMTP_HOST=smtp.gmail.com \
SMTP_PORT=587 \
SMTP_USER=tu@email.com \
SMTP_PASSWORD=tu_password \
SMTP_FROM=noreply@example.com \
PYTHONPATH=src python -m email_service.consumer
```

Variables de entorno disponibles:

| Variable        | Valor por defecto     | Descripción                    |
|-----------------|-----------------------|--------------------------------|
| `RABBITMQ_HOST` | `localhost`           | Host del broker                |
| `RABBITMQ_QUEUE`| `email_notifications` | Cola a consumir                |
| `SMTP_HOST`     | `smtp.gmail.com`      | Servidor SMTP                  |
| `SMTP_PORT`     | `587`                 | Puerto SMTP (STARTTLS)         |
| `SMTP_USER`     | `user@example.com`    | Usuario SMTP                   |
| `SMTP_PASSWORD` | `changeme`            | Contraseña SMTP                |
| `SMTP_FROM`     | `noreply@example.com` | Dirección remitente            |

### 4. Enviar una evaluación de prueba

```bash
PYTHONPATH=src python -m client.grpc_client
```

---

## Ejecución con Docker Compose

Levanta `logic_service`, `email_service` y `rabbitmq` en una red interna:

```bash
# Opcional: crear .env con credenciales SMTP reales
cp .env.example .env   # editar SMTP_USER, SMTP_PASSWORD, etc.

docker-compose up --build
```

| Servicio       | Puerto expuesto              |
|----------------|------------------------------|
| `logic_service`| `50051` (gRPC)               |
| `rabbitmq`     | `5672` (AMQP), `15672` (UI)  |

Los archivos SQLite de `logic_service` persisten en el volumen Docker `logic_data`.

---

## Pruebas

```bash
PYTHONPATH=src python -m unittest discover -s tests -v
```

Las pruebas usan **SQLite en memoria temporal** (`tempfile.TemporaryDirectory`) e `InMemoryPublisher` para no requerir RabbitMQ ni SMTP.  El CI ejecuta estas pruebas automáticamente en cada push y pull request (ver badge arriba).

---

## NFRs cubiertos

| Atributo              | Mecanismo                                                        |
|-----------------------|------------------------------------------------------------------|
| Alta disponibilidad   | Servicios independientes desplegables en réplicas               |
| Tolerancia a fallos   | Compensación Saga + cola durable en RabbitMQ                    |
| Escalabilidad         | API gRPC y consumidor escalan horizontalmente                   |
| Bajo acoplamiento     | Contrato gRPC + mensajería asíncrona + repositorios ORM         |
| Portabilidad de DB    | SQLAlchemy admite SQLite (dev) y PostgreSQL (producción)        |


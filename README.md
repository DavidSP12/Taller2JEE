# Taller2JEE - Sistema distribuido para evaluaciones

## Diagrama de arquitectura (texto)

```
[Cliente gRPC]
    |
    | SubmitEvaluation
    v
[Servicio de Lógica (gRPC API)]
    |--(sync)--> [DB Exámenes - SQLite/PostgreSQL]
    |--(sync)--> [DB Estudiantes - SQLite/PostgreSQL]
    |
    |--(async publish)--> [RabbitMQ/Kafka Queue] --(consume)--> [Servicio de Correo]
                                                           |
                                                           --SMTP--> [Servidor de correo]
```

- El cliente solo conoce el contrato gRPC.
- El servicio de lógica implementa la orquestación de negocio.
- La persistencia está desacoplada por repositorios (`ExamRepository`, `StudentRepository`).
- El correo sale del flujo crítico mediante cola asíncrona.

## Estructura de carpetas

```text
.
├── proto/
│   └── evaluation.proto
├── src/
│   ├── client/
│   │   └── grpc_client.py
│   ├── common/
│   │   └── models.py
│   ├── email_service/
│   │   └── consumer.py
│   └── logic_service/
│       ├── generated/            # salida de grpc_tools.protoc
│       ├── queue/
│       │   └── producer.py
│       ├── repositories/
│       │   ├── exam_repository.py
│       │   └── student_repository.py
│       ├── grpc_server.py
│       └── saga.py
└── tests/
    └── test_saga.py
```

## Transacciones distribuidas: estrategia Saga con compensación

Se implementa **Saga de orquestación** en `EvaluationSagaOrchestrator`:

1. Guardar envío en DB de exámenes.
2. Actualizar estudiante y guardar nota en DB de estudiantes.
3. Publicar evento de notificación a la cola.

Si falla cualquier paso, se ejecutan acciones compensatorias:
- borrar nota en DB de estudiantes (si fue creada),
- borrar envío en DB de exámenes.

Esto evita 2PC entre servicios y mantiene bajo acoplamiento, favoreciendo escalabilidad y tolerancia a fallos.

## Código base solicitado

- **Servicio gRPC (lógica):** `src/logic_service/grpc_server.py`
- **Productor de cola:** `src/logic_service/queue/producer.py`
- **Consumidor de cola (correo):** `src/email_service/consumer.py`

## Generar stubs gRPC

```bash
python -m pip install -r requirements.txt
python -m grpc_tools.protoc \
  -I./proto \
  --python_out=./src/logic_service/generated \
  --grpc_python_out=./src/logic_service/generated \
  ./proto/evaluation.proto
```

## Ejecución (ejemplo)

1. Levantar RabbitMQ y un SMTP accesible.
2. Iniciar servicio gRPC:

```bash
PYTHONPATH=src python -m logic_service.grpc_server
```

3. Iniciar consumidor de correos:

```bash
PYTHONPATH=src python -m email_service.consumer
```

4. Enviar evaluación desde cliente:

```bash
PYTHONPATH=src python -m client.grpc_client
```

## Flujo completo end-to-end

1. El cliente invoca `SubmitEvaluation` por gRPC.
2. El servicio calcula la nota.
3. Saga guarda en DB de exámenes y DB de estudiantes.
4. Saga publica `EmailEvent` en RabbitMQ.
5. gRPC responde `PROCESSED` al cliente sin esperar SMTP.
6. El consumidor toma el evento y envía correo vía SMTP.

## NFRs cubiertos

- **Alta disponibilidad:** servicios desplegables en réplicas independientes.
- **Tolerancia a fallos:** compensación de Saga y cola durable.
- **Escalabilidad:** API y consumidor escalan horizontalmente.
- **Bajo acoplamiento:** contrato gRPC + mensajería asíncrona + repositorios.

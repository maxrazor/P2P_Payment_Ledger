# Architecture

## Overview

P2P Payment Ledger is a portfolio application demonstrating a
distributed peer-to-peer transfer workflow using Spring Boot
microservices, PostgreSQL, Apache Kafka, Redis, and a React frontend.

The system separates identity, transfer orchestration, account/ledger
operations, and notifications. The API Gateway is the client-facing
entry point. Business events are exchanged asynchronously through Kafka,
while transactional outboxes help coordinate database changes with event
publication.

This is an educational portfolio project, not a production payment
platform.

## System components

``` mermaid
flowchart TB
    Browser["React / Vite frontend"]
    Gateway["API Gateway<br/>Spring Cloud Gateway / WebFlux"]

    Identity["Identity Service"]
    Transfer["Transfer Service"]
    Account["Account Service"]
    Notification["Notification Service"]

    IdentityDB[("PostgreSQL<br/>identity database")]
    TransferDB[("PostgreSQL<br/>transfer database")]
    AccountDB[("PostgreSQL<br/>account database")]

    Redis[("Redis<br/>idempotency")]
    Kafka[["Apache Kafka"]]

    Prometheus["Prometheus"]
    Grafana["Grafana"]
    OTel["OpenTelemetry Java Agent"]

    Browser --> Gateway
    Gateway --> Identity
    Gateway --> Transfer
    Gateway --> Account
    Gateway --> Notification

    Identity --> IdentityDB
    Transfer --> TransferDB
    Account --> AccountDB
    Transfer --> Redis

    Identity -- "user-registrations-topic" --> Kafka
    Kafka -- "user-registrations-topic" --> Account

    Transfer -- "transfer-requests-topic" --> Kafka
    Kafka -- "transfer-requests-topic" --> Account

    Account -- "transfer-results-topic" --> Kafka
    Kafka -- "transfer-results-topic" --> Transfer
    Kafka -- "transfer-results-topic" --> Notification

    Identity -. metrics .-> Prometheus
    Transfer -. metrics .-> Prometheus
    Account -. metrics .-> Prometheus
    Gateway -. metrics .-> Prometheus
    Notification -. metrics .-> Prometheus
    Prometheus --> Grafana

    Gateway -. instrumentation .-> OTel
    Identity -. instrumentation .-> OTel
    Transfer -. instrumentation .-> OTel
    Account -. instrumentation .-> OTel
```

### Service responsibilities

| Component | Responsibility |
|---|---|
| **API Gateway** | Routes client traffic, validates JWT authentication cookies, removes untrusted client-supplied internal headers, and propagates trusted identity and role headers. |
| **Identity Service** | Registers and authenticates users, issues authentication cookies, and publishes user-registration events. |
| **Transfer Service** | Accepts transfer requests, creates transaction records, applies idempotency controls, writes outbox events, and updates transaction status from transfer-result events. |
| **Account Service** | Creates wallets, resolves accounts, validates transfer requests, writes ledger entries, and publishes transfer results through its outbox. |
| **Notification Service** | Consumes transfer-result events and delivers user notifications through Server-Sent Events (SSE). |
| **PostgreSQL** | Stores service-owned business data in separate identity, transfer, and account databases. |
| **Redis** | Supports transfer request idempotency and in-progress request tracking. |
| **Apache Kafka** | Carries asynchronous events between services. |
| **Prometheus and Grafana** | Collect and visualize application metrics. |
| **OpenTelemetry Java Agent** | Instruments services and propagates trace context, including across Kafka processing. Traces are instrumented but are not currently exported to a dedicated tracing backend. |

## Event flows

The application currently uses three principal Kafka topics.

| Topic | Publisher | Consumers | Purpose |
|---|---|---|---|
| `user-registrations-topic` | Identity Service | Account Service | Creates a default wallet after user registration. |
| `transfer-requests-topic` | Transfer Service, through its outbox | Account Service | Requests validation and processing of a transfer. |
| `transfer-results-topic` | Account Service, through its outbox | Transfer Service and Notification Service | Reports a completed or rejected transfer so the transaction status can be updated and users can be notified. |

## Transfer lifecycle

A transfer is an asynchronous, multi-service workflow. The initial HTTP
request does not mean that the ledger operation has already completed.

``` mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Gateway as API Gateway
    participant Transfer as Transfer Service
    participant TDB as Transfer DB
    participant TOutbox as Transfer Outbox
    participant Kafka
    participant Account as Account Service
    participant ADB as Account DB
    participant AOutbox as Account Outbox
    participant Notify as Notification Service

    Client->>Gateway: POST /transfers
    Gateway->>Gateway: Validate JWT cookie and trusted headers
    Gateway->>Transfer: Forward authenticated request
    Transfer->>TDB: Persist transaction
    Transfer->>TOutbox: Persist transfer-request event
    Note over Transfer,TOutbox: Transaction record and outbox event are committed together
    TOutbox-->>Kafka: Publish transfer request
    Kafka->>Account: Consume transfer request
    Account->>ADB: Validate accounts and transfer constraints
    Account->>ADB: Write debit and credit ledger entries
    Account->>AOutbox: Persist transfer result event
    Note over Account,AOutbox: Ledger changes and result outbox event are committed together
    AOutbox-->>Kafka: Publish transfer result
    Kafka->>Transfer: Consume transfer result
    Transfer->>TDB: Mark transaction completed or rejected
    Kafka->>Notify: Consume transfer result
    Notify-->>Client: Deliver SSE notification when subscribed
```

The precise event payloads and transaction boundaries are implemented in
the service code. The sequence above describes the intended main flow.

### Transfer processing and ledger consistency

1.  The client submits a transfer request through the gateway.
2.  The Transfer Service creates a transaction record and an associated
    outbox event in a database transaction.
3.  An outbox publisher sends the transfer request to Kafka.
4.  The Account Service validates the request, including account
    existence, ownership/eligibility, currency compatibility, and
    sufficient funds.
5.  For an accepted transfer, the Account Service writes the
    corresponding debit and credit ledger entries. Rejected requests
    produce a rejection result rather than successful ledger postings.
6.  The Account Service persists a transfer-result outbox event and
    publishes it asynchronously.
7.  The Transfer Service consumes the result and updates the transaction
    status.
8.  The Notification Service consumes the result and notifies the
    relevant user or users over SSE according to the outcome.

The account ledger is the source used to calculate wallet balances.
Debit entries represent outgoing funds and are negative; credit entries
represent incoming funds and are positive. Database locking is used to
coordinate concurrent operations involving the same accounts, including
protection against concurrent overdrafts.

## Transactional outbox, retries, and duplicate events

The Transfer Service and Account Service use outbox tables for the
transfer workflow. The business database update and corresponding outbox
record are written in the same local database transaction. A background
publisher later sends pending events to Kafka.

The implementation includes:

-   Claiming outbox records for publishing.
-   Published, retry, and failure state tracking.
-   Recovery from temporary publishing failures.
-   A maximum of 10 publishing attempts before an event is treated as
    permanently failed.
-   Routing permanently failed events to a dead-letter topic (DLT),
    according to the configured failure handling.
-   Processed-event tracking to ignore duplicate event deliveries.

These mechanisms improve reliability, but do not make the entire
multi-service workflow one ACID transaction. Kafka delivery and consumer
execution can be repeated, so idempotent event handling and database
transaction boundaries remain important.

## Security model

The API Gateway is the intended entry point for client requests.

-   The gateway checks the JWT carried in an HTTP-only authentication
    cookie.
-   It removes client-supplied trusted/internal headers before creating
    its own.
-   After authentication, it propagates the authenticated user
    identifier through `X-Authenticated-User-Id`.
-   It propagates role information through `X-User-Role`, including for
    administrative authorization decisions.
-   `X-Gateway-Secret` is used to validate requests routed from the
    gateway to internal services.
-   `X-Internal-Service` is used for specific internal service
    communication, including account resolution between the Transfer and
    Account services.

These headers are trusted only when the receiving service can establish
that the request came through an authorized internal path. Internal
secrets must be supplied through environment variables or a secret
manager, not committed to source control. For deployment beyond a local
portfolio environment, network exposure, service-to-service
authentication, secret rotation, and authorization boundaries require
additional security review.

## Observability

### Metrics

Prometheus is configured to scrape `/actuator/prometheus` from the
following services at five-second intervals:

-   API Gateway
-   Identity Service
-   Transfer Service
-   Account Service
-   Notification Service

Custom metrics include outbox events claimed, published, retried, and
failed, along with committed transfers. Grafana is used to visualize the
collected metrics.

### Tracing and logs

The OpenTelemetry Java Agent instruments the services, and trace context
is propagated through the workflow, including Kafka processing. Traces
are currently not exported to a dedicated tracing backend, so a
centralized trace-search UI is not yet part of the setup.

## Local deployment

The application and infrastructure can be run locally with Docker
Compose. The environment includes the application services, PostgreSQL,
Redis, Kafka, Prometheus, and Grafana.

Use the repository's root `docker-compose.yml` and `.env.example` as the
source of truth for startup configuration. Create an untracked `.env`
file with local secrets before starting the stack. Do not commit real
credentials, JWT signing keys, or internal service tokens.

## Current scope and limitations

This is a portfolio and learning project. Known limitations include:

-   No integration with real banking rails or external payment
    providers.
-   No user-facing workflow for adding funds; funding is currently
    handled through a manual administrative endpoint.
-   User profile editing is not implemented.
-   Distributed tracing is instrumented but not exported to a dedicated
    tracing backend.
-   Production readiness, including comprehensive security hardening,
    alerting, disaster recovery, and operational controls, has not been
    claimed.

These limitations are intentionally documented to distinguish
implemented engineering mechanisms from future work.

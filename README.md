P2P Payment Ledger

A distributed peer-to-peer payment ledger built with Java, Spring Boot, Apache Kafka, PostgreSQL, and Redis.

P2P Payment Ledger is a portfolio project that demonstrates how a financial-style transaction system can be designed around microservices, an append-only ledger, asynchronous event processing, and operational observability.

The application supports user authentication, multi-currency wallets, peer-to-peer transfers, transaction history, and real-time notifications. The backend services run in Docker containers and communicate through HTTP and Kafka.

Project status: Functional portfolio project with local Docker Compose deployment. This is an educational application, not a production payment platform. It does not process real money or integrate with banking or payment providers.

Features

Payments and ledger

Peer-to-peer transfers using a recipient's email address.

Multi-currency wallets, including EUR, USD, and RUB.

Append-only ledger entries representing debits and credits.

Wallet balances calculated from ledger entries rather than maintained as mutable balance fields.

Transaction history and account ownership checks.

Idempotency support for transfer requests.

Database locking to coordinate concurrent transfers and protect account consistency.

Authentication and security

User registration and login.

JWT-based authentication using an HTTP-only cookie.

API Gateway authentication and propagation of the authenticated user identity to downstream services.

Internal service authentication mechanisms.

Separation of public API access from internal service communication.

Distributed processing

Asynchronous communication through Apache Kafka.

Transactional outbox pattern for publishing events from database-backed workflows.

Processed-event tracking to prevent duplicate event handling.

Retry and recovery mechanisms for outbox publishing.

Event-driven account initialization after user registration.

Asynchronous transfer processing and transaction status updates.

Server-Sent Events (SSE) for real-time notifications.

Observability

Application logging.

Metrics collection with Micrometer and Prometheus.

Metrics visualization with Grafana.

Distributed tracing instrumentation using the OpenTelemetry Java Agent.

Trace and span context available for investigating request flows.

Architecture

The system is organized as a Maven multi-module project.

flowchart TB
    Client["React Frontend"]
    Gateway["API Gateway<br/>Spring Cloud Gateway"]
    Identity["Identity Service"]
    Transfer["Transfer Service"]
    Account["Account Service"]
    Notification["Notification Service"]

    IdentityDB[("Identity Database")]
    TransferDB[("Transfer Database")]
    AccountDB[("Account Database")]

    Kafka[["Apache Kafka"]]
    Redis[("Redis")]
    Prometheus["Prometheus"]
    Grafana["Grafana"]
    OTel["OpenTelemetry Java Agent"]

    Client --> Gateway
    Gateway --> Identity
    Gateway --> Transfer
    Gateway --> Account
    Gateway --> Notification

    Identity --> IdentityDB
    Transfer --> TransferDB
    Account --> AccountDB

    Identity <--> Kafka
    Transfer <--> Kafka
    Account <--> Kafka
    Notification --> Kafka

    Transfer --> Redis

    Identity -. instrumentation .-> OTel
    Transfer -. instrumentation .-> OTel
    Account -. instrumentation .-> OTel
    Gateway -. instrumentation .-> OTel

    Prometheus --> Grafana

The diagram illustrates the main components and communication paths. Kafka event flows are asynchronous, and the outbox pattern is used where database changes must be reliably coordinated with event publication.

Services

Module

Responsibility

api-gateway

Entry point for client requests, routing, and authentication enforcement

identity-service

User registration, login, and identity management

account-service

Wallet management, ledger entries, balance calculation, and statements

transfer-service

Transfer orchestration, idempotency, and transaction lifecycle

notification-service

Real-time notification delivery through SSE

dfls-shared-dto

Shared data-transfer objects used across modules

Each core business service has a separate PostgreSQL database. Redis supports transfer idempotency, while Kafka handles asynchronous communication between services.

Technology stack

Area

Technologies

Language

Java 21

Backend

Spring Boot, Spring Cloud Gateway, Spring Data JPA

Build

Maven

Databases

PostgreSQL 16

Messaging

Apache Kafka

Caching / idempotency

Redis 7

Schema management

Liquibase

Frontend

React 18, Vite, JavaScript

Metrics

Micrometer, Prometheus, Grafana

Tracing

OpenTelemetry Java Agent

Deployment

Docker, Docker Compose

Key engineering decisions

1. Ledger-based balance calculation

Wallet balances are derived from ledger entries instead of being stored as a mutable account balance.

Each transfer produces corresponding debit and credit entries. Debit entries represent outgoing funds, while credit entries represent incoming funds.

This design makes the transaction history the source of truth for balances and provides an auditable record of account activity.

2. Idempotent transfer requests

Transfer requests support idempotency keys to reduce the risk of processing the same request more than once when clients retry after timeouts or network failures.

Redis is used to track request processing, while database-level consistency mechanisms protect the underlying ledger operations.

3. Transactional outbox pattern

Database changes and Kafka publication cannot generally be committed as one ordinary atomic transaction across both systems.

The outbox pattern addresses this problem by persisting an event in the same database transaction as the relevant business change. A background publisher subsequently sends the event to Kafka.

The publisher supports batch processing, record claiming, retries, and recovery from interrupted processing.

4. Duplicate event handling

Consumers track processed event identifiers to prevent duplicate deliveries from applying the same business operation repeatedly.

This is important because asynchronous messaging can result in redelivery. The database changes and processed-event record must be coordinated transactionally for this protection to work.

5. Concurrency control

Concurrent transfers may attempt to modify the same wallets. Database locking helps serialize conflicting operations and protect against inconsistent ledger updates.

Wallets are locked in a consistent order to reduce the risk of deadlocks when a transfer involves two accounts.

6. Microservices and observability

The application separates identity, account, transfer, and notification responsibilities into distinct services.

Prometheus and Grafana support metrics inspection, while OpenTelemetry instrumentation helps investigate requests across service boundaries. Docker Compose provides a repeatable local environment for running the application and its infrastructure.

Getting started

Prerequisites

Install the following:

Java 21

Maven, or use the project's Maven Wrapper if provided

Docker Engine and Docker Compose

Node.js and npm for the frontend

Git

1. Clone the repository

git clone https://github.com/maxrazor/P2P_Payment_Ledger.git
cd P2P_Payment_Ledger

2. Configure environment variables

Create a local .env file based on .env.example.

cp .env.example .env

On Windows PowerShell:

Copy-Item .env.example .env

Fill in the required local development credentials and secrets. Use unique values for your local environment, and never commit .env.

Ensure the OpenTelemetry Java Agent is available at the path expected by the Compose configuration, or update the configuration to use your chosen agent distribution.

3. Start the backend and infrastructure

docker compose up --build -d

Check the running containers:

docker compose ps

Follow the logs when troubleshooting startup:

docker compose logs -f

Stop the application:

docker compose down

Persistent data is stored in Docker volumes. To reset the local databases and other named volumes, use the following command only if you are happy to lose all persisted development data:

docker compose down -v

4. Start the frontend

Open a separate terminal and navigate to the frontend directory.

cd p2p-ledger-frontend
npm install
npm run dev

Configure the frontend to use the API Gateway at http://localhost:8080 according to the project's frontend configuration.

The frontend and backend must agree on the API base URL and authentication-cookie behavior. If the frontend uses a development proxy, configure it accordingly.

5. Access the application

The following addresses are based on the current local Compose port mappings.

Component

Local address

Frontend

Vite development URL printed in the terminal

API Gateway

http://localhost:8080

Identity Service

http://localhost:8081

Transfer Service

http://localhost:8082

Account Service

http://localhost:8083

Notification Service

http://localhost:8084

Prometheus

http://localhost:9090

Grafana

http://localhost:3000

The backend service ports are published by the current development Compose configuration. In a hardened deployment, consider exposing only the gateway and required monitoring interfaces.

Grafana credentials and any required initial setup should be documented in the local environment configuration.

Testing

The project has been tested through a combination of automated tests and manual end-to-end scenarios, including the main application workflows and Docker-based deployment.

Testing covers the application at multiple levels, with the exact scope depending on the test suite and local infrastructure configuration.

To run the Maven tests from the repository root:

mvn test

To build the project:

mvn clean package

If your project uses the Maven Wrapper, replace mvn with ./mvnw on Unix-like systems or .\mvnw.cmd on Windows.

Some integration tests may require PostgreSQL, Kafka, Redis, or other configured services to be available.

Current limitations and future improvements

This project focuses on core ledger and distributed-processing behavior. Potential areas for further development include:

Adding funds through a user-facing, audited funding workflow rather than a manual administrative endpoint.

Updating user profile information.

Expanding API response DTO consistency where useful.

Strengthening security configuration and deployment hardening.

Adding more automated integration and failure-recovery tests.

Improving operational documentation, dashboards, and alerting.

Evaluating additional production concerns such as rate limiting, secrets management, backups, and disaster recovery.

The application is intended for learning and demonstration. It has not been presented as a certified, production-grade financial system.

Repository structure

P2P_Payment_Ledger/
├── api-gateway/
├── identity-service/
├── account-service/
├── transfer-service/
├── notification-service/
├── dfls-shared-dto/
├── p2p-ledger-frontend/
├── tools/
├── monitoring/
├── docker-compose.yml
├── pom.xml
└── README.md

The structure above illustrates the main modules and infrastructure directories; additional files and directories may be present in the repository.

Testing

The project includes unit tests, integration tests, and end-to-end tests covering core business logic, security boundaries, and distributed transfer processing.

Unit and component tests

Identity and authentication: registration, duplicate email handling, password hashing and verification, login, JWT claims, cookie handling, and authentication failures.

API Gateway: JWT validation, expired and invalid tokens, trusted-header sanitization, role propagation, request routing, and downstream service failures.

Account and ledger: wallet creation, duplicate wallet prevention, account resolution, balance calculation, ledger debit/credit entries, statements, and account ownership checks.

Transfers: request validation, idempotency, insufficient funds, missing accounts, currency mismatches, and transaction status transitions.

Outbox processing: successful publishing, retry behavior, and permanent publishing failures.

Notifications: SSE subscriptions, successful and rejected transfer notifications, malformed events, and concurrent subscriptions.

Integration and end-to-end tests

Integration tests cover concurrent overdraft attempts, user registration and lifecycle behavior, and the transactional outbox pipeline.

End-to-end scenarios verify the distributed transfer workflow across the Transfer and Account services, including transaction processing and final status updates.

Running tests

Run the Maven test suite from the repository root:

mvn test

For verification, including additional Maven lifecycle checks:

mvn verify

Some integration tests may require infrastructure services to be available. Consult the test configuration before running them in a clean environment.
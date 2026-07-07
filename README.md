# queue-master

`queue-master` is the RabbitMQ-facing queue service used by the enhanced Sock
Shop system in the EviRCA microservice root cause analysis benchmark.

In the original Sock Shop demo, this service reads shipping work from the
shipping queue and starts the simulated shipping process. In this benchmark
version, the service is also part of the telemetry-rich RCA environment
described in:

> EviRCA: An Evidence-Aware Skill-Based LLM Agent and a Telemetry-Rich
> Multi-Modal Benchmark for Microservice Root Cause Analysis

## Benchmark Context

EviRCA builds on an enhanced Sock Shop deployment for reproducible
microservice RCA experiments. The benchmark collects synchronized metrics,
logs, traces, topology, Chaos Mesh fault-injection artifacts, upgraded service
implementations, and fine-grained labels.

Within that system, `queue-master` is one of the modernized Java services. It
is especially relevant for RabbitMQ-related diagnosis because it consumes
shipping queue messages, checks RabbitMQ availability, emits dependency-aware
logs, and exports telemetry that can be correlated with RabbitMQ middleware
metrics and distributed traces.

## Service Role

- Consumes shipping tasks from RabbitMQ.
- Represents the queue-processing side of the Sock Shop shipping workflow.
- Exposes application and dependency health information.
- Emits Prometheus-compatible metrics for the benchmark monitoring stack.
- Propagates and exports tracing data for asynchronous RabbitMQ flows.
- Produces trace-aware logs for RCA evidence extraction.

## Modernization and Observability Changes

This repository has been updated from the legacy Sock Shop implementation to
match the enhanced Sock Shop benchmark environment:

- Migrated to Java 17 and Spring Boot 3.4.1.
- Uses Spring Boot Actuator and Micrometer for metrics.
- Exposes Prometheus metrics through both Actuator and the legacy `/metrics`
  endpoint used by existing Sock Shop deployment assets.
- Uses Micrometer Tracing with Brave and Zipkin-compatible export.
- Instruments RabbitMQ message flow with Brave RabbitMQ support.
- Adds trace and span identifiers to application logs.
- Filters health and metrics endpoints from tracing to reduce telemetry noise.
- Adds RabbitMQ health-check failure logging with failure classification and
  span exception tagging.
- Includes Chaos Monkey Spring Boot support for controlled JVM exception
  experiments.

## Runtime Endpoints

| Endpoint | Purpose |
| --- | --- |
| `/health` | Service and RabbitMQ dependency health status. |
| `/metrics` | Prometheus text metrics, retained for compatibility. |
| `/actuator/health` | Spring Boot Actuator health endpoint. |
| `/actuator/metrics` | Spring Boot Actuator metric endpoint. |
| `/actuator/prometheus` | Actuator Prometheus scrape endpoint. |
| `/actuator/chaosmonkey` | Chaos Monkey control endpoint when enabled. |

## Configuration

Important runtime properties are defined in
`src/main/resources/application.properties`.

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port. Can be overridden with `port`. |
| `spring.rabbitmq.host` | `rabbitmq` | RabbitMQ host used by the service. |
| `spring.application.name` | `queue-master` | Service name used in logs and tracing. |
| `management.tracing.sampling.probability` | `1.0` | Full trace sampling for benchmark collection. |
| `management.zipkin.tracing.endpoint` | Jaeger collector Zipkin endpoint | Trace export target. |
| `management.tracing.propagation.type` | `B3` | Trace-context propagation format. |
| `spring.profiles.active` | `chaos-monkey` | Enables Chaos Monkey integration by default. |

The Zipkin endpoint can be changed with `zipkin_host`.

## Build

Build the service JAR with Maven:

```sh
mvn package
```

Build the container image with the existing Sock Shop build script:

```sh
GROUP=weaveworksdemos COMMIT=test ./scripts/build.sh
```

## Test

Run the existing test wrapper with a Python test file:

```sh
./test/test.sh unit.py
./test/test.sh component.py
```

Run Java unit tests directly with Maven:

```sh
mvn test
```

## Push

Push a built image with:

```sh
GROUP=weaveworksdemos COMMIT=test ./scripts/push.sh
```

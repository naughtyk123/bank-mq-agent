# Bank MQ Agent — IBM MQ ↔ Pega Launchpad Integration

Spring Boot 3.2 agent running inside the bank's private infrastructure.
Supports three inbound MQ formats: **MT (SWIFT)**, **MX (ISO 20022)**, **FED (Fedwire)**.

## Architecture

```
BANK.MT.INBOUND.Q  ──MT──┐
BANK.MX.INBOUND.Q  ──MX──┤  Agent  ──Pega DX API──▶  Pega Launchpad
BANK.FED.INBOUND.Q ──FED─┘

BANK.OUTBOUND.Q  ◀─────────  Agent  ◀──REST POST──────  Pega Launchpad
```

### Inbound flow (per queue)
1. Dedicated `@JmsListener` per queue (MT / MX / FED), each with its own thread pool
2. Message received → type-specific parser → mapped to `PegaRequest`
3. Calls Pega DX API `POST /cases` with OAuth2 token
4. On 2xx → ACKs the MQ message
5. On failure → retries 3× with exponential backoff → sends to `BANK.DLQ`

### Outbound flow
1. Pega POSTs to `POST /api/v1/outbound` (secured with X-API-Key)
2. Agent puts message onto `BANK.OUTBOUND.Q`
3. Returns `200 OK` to Pega only after MQ send succeeds

---

## Queue & concurrency configuration

| Queue                  | Format     | Default concurrency |
|------------------------|------------|---------------------|
| `BANK.MT.INBOUND.Q`   | SWIFT MT   | 2–10 threads        |
| `BANK.MX.INBOUND.Q`   | ISO 20022  | 1–5 threads         |
| `BANK.FED.INBOUND.Q`  | Fedwire    | 1–3 threads         |
| `BANK.OUTBOUND.Q`     | Any        | n/a (JmsTemplate)   |
| `BANK.DLQ`            | Any        | n/a (dead-letter)   |

Override concurrency in `application.yml`:
```yaml
agent.mq.mt-concurrency: 2-10
agent.mq.mx-concurrency: 1-5
agent.mq.fed-concurrency: 1-3
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- IBM MQ 9.x accessible from this host
- Pega Launchpad DX API credentials (OAuth2 client_credentials)

---

## Configuration

| Environment Variable  | Description                          |
|-----------------------|--------------------------------------|
| `MQ_PASSWORD`         | IBM MQ user password                 |
| `PEGA_CLIENT_ID`      | Pega OAuth2 client ID                |
| `PEGA_CLIENT_SECRET`  | Pega OAuth2 client secret            |
| `AGENT_API_KEY`       | API key Pega must send in X-API-Key  |

Edit `application.yml` for non-secret config (queue names, MQ host, Pega URL, concurrency).

---

## Build & Run

```bash
mvn clean package -DskipTests

MQ_PASSWORD=secret \
PEGA_CLIENT_ID=my-client \
PEGA_CLIENT_SECRET=my-secret \
AGENT_API_KEY=my-api-key \
java -jar target/bank-mq-agent-1.0.0-SNAPSHOT.jar
```

```bash
mvn test
```

---

## Opening in IntelliJ IDEA

1. **File → Open** → select the `bank-mq-agent` folder (containing `pom.xml`)
2. IntelliJ auto-detects Maven and downloads dependencies
3. **Run → Edit Configurations → Environment Variables** — add the four secrets above
4. Run `BankMqAgentApplication`

---

## Project structure

```
src/main/java/com/bank/agent/
├── BankMqAgentApplication.java
├── config/
│   ├── JmsConfig.java               3 listener factories (MT/MX/FED) + JmsTemplate
│   ├── WebClientConfig.java         WebClient for Pega DX API
│   └── SecurityConfig.java          X-API-Key filter on REST endpoint
├── listener/
│   └── MqInboundListener.java       @JmsListener for MT / MX / FED queues
├── controller/
│   └── OutboundController.java      POST /api/v1/outbound
├── service/
│   ├── InboundMessageService.java   Inbound orchestration + retry + DLQ recovery
│   └── OutboundMessageService.java  Outbound orchestration
├── client/
│   ├── PegaDxApiClient.java         Pega DX API HTTP calls
│   └── OAuth2TokenService.java      OAuth2 token fetch + cache
├── producer/
│   └── MqOutboundProducer.java      JmsTemplate.send() + DLQ send
├── mapper/
│   └── MessageMapper.java           MT / MX / FED parsers → PegaRequest
├── model/
│   ├── MessageType.java             MT | MX | FED enum
│   ├── PegaRequest.java
│   ├── PegaResponse.java
│   └── OutboundRequest.java
└── exception/
    ├── PegaApiException.java
    └── GlobalExceptionHandler.java
```

---

## Extending the parsers

- **MT**: Update `parseMtMessage()` in `MessageMapper` to branch on MT type (MT103, MT202, MT515…) and extract type-specific fields.
- **MX**: Replace the simple regex extraction with JAXB-generated classes from the ISO 20022 XSD schemas for production accuracy.
- **FED**: Add additional Fedwire tag extractions (`{3600}` business function, `{6000}` additional info) as needed.

# Aetheris Orchestrator Service

The orchestrator is the first runtime control-plane service for the Syntra + Aetheris AI-agent platform.

## Current responsibilities

- typed specialist-agent catalog
- division/risk overview
- deterministic owner-policy evaluation
- operation modes (`TURBO`, `BALANCED`, `ECO`, `PRIVATE`, `ZERO_COST`, `FOCUS`)
- task lifecycle/event contracts for future live streaming
- MCP/tool integration contracts
- Actuator health and Prometheus metrics

## Run locally

```bash
mvn spring-boot:run
```

Default port: `8090`.

With the whole platform:

```bash
docker compose up --build
```

## Endpoints

```text
GET  /actuator/health
GET  /actuator/prometheus
GET  /api/orchestrator/agents
GET  /api/orchestrator/agents/{id}
GET  /api/orchestrator/divisions/{division}/agents
GET  /api/orchestrator/overview
POST /api/orchestrator/policy/evaluate
```

### Example: inspect the organization

```bash
curl http://localhost:8090/api/orchestrator/overview
```

### Example: ZERO_COST guard

```bash
curl -X POST http://localhost:8090/api/orchestrator/policy/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "ai-engineer",
    "action": "call-premium-model",
    "riskLevel": "LOW",
    "billable": true,
    "sendsDataOffDevice": true,
    "mode": "ZERO_COST"
  }'
```

Expected result: the action is denied because billable actions are blocked in `ZERO_COST` mode.

## Next implementation slices

1. persistent task state and event journal
2. WebSocket/SSE event streaming for the Syntra live operations UI
3. versioned Owner Rules engine and approval queue
4. local model adapter and model-router health data
5. MCP client manager and sandboxed tool registry
6. engineering + QA + research multi-agent workflow
7. audit-service integration and signed action records

See [`../docs/ai/syntra-aetheris-v2.md`](../docs/ai/syntra-aetheris-v2.md) for the broader architecture.

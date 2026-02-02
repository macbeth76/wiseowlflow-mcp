# WiseOwlFlow MCP Server

A Kotlin MCP (Model Context Protocol) REST server for workflow orchestration that wraps other MCP servers, enabling IFTTT/Zapier-style automation powered by Ollama AI.

## Features

- **MCP Proxy**: Aggregate and route tools from multiple upstream MCP servers
- **Workflow Engine**: Define and execute multi-step workflows with conditions, parallel execution, and retries
- **AI Decision Making**: Integrate Ollama for intelligent routing and decision steps
- **State Machine**: Robust workflow state management with pause/resume/cancel support
- **Authentication**: JWT tokens, OAuth (Google, GitHub), and API keys
- **Billing**: Stripe integration with usage metering and subscription tiers
- **Persistence**: PostgreSQL with Flyway migrations
- **Caching**: Redis for sessions and caching

## Tech Stack

- **Language**: Kotlin 2.2.0 with Coroutines
- **Framework**: Ktor 3.1.3 (Netty engine)
- **MCP SDK**: Official Kotlin SDK 0.8.3
- **Database**: PostgreSQL + Exposed ORM
- **Cache**: Redis (Lettuce client)
- **State Machine**: KStateMachine
- **AI**: Ollama (optional)
- **Billing**: Stripe

## Project Structure

```
wiseowlflow-mcp/
├── core/                 # Domain models & repository interfaces
├── persistence/          # Database layer (Exposed tables, repositories, migrations)
├── session/              # Redis configuration & session management
├── mcp-proxy/            # MCP client factory, upstream manager, tool routing
├── workflow-engine/      # Workflow parser, engine, state machine, AI integration
├── auth/                 # JWT, OAuth, API key services
├── billing/              # Stripe integration & usage enforcement
├── api/                  # REST routes & MCP endpoint
└── app/                  # Main application entry point
```

## Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose
- (Optional) Ollama for AI features

### 1. Start Infrastructure

```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# (Optional) Start Ollama for AI features
docker-compose --profile cpu up -d ollama-cpu
```

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env with your settings
```

Key environment variables:
- `DATABASE_URL` - PostgreSQL connection string
- `REDIS_URL` - Redis connection string
- `JWT_SECRET` - Secret for JWT token signing
- `STRIPE_API_KEY` - Stripe API key (optional)
- `OLLAMA_BASE_URL` - Ollama server URL (optional)

### 3. Run the Server

```bash
# Using Gradle
./gradlew :app:run

# Or build and run the fat JAR
./gradlew shadowJar
java -jar app/build/libs/app-all.jar
```

The server starts at `http://localhost:8080`

## API Endpoints

### Health Check
```bash
curl http://localhost:8080/health
```

### Authentication

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

### Workflows

```bash
# List workflows (requires auth)
curl http://localhost:8080/workflows \
  -H "Authorization: Bearer <token>"

# Create workflow
curl -X POST http://localhost:8080/workflows \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d @examples/pr-notification-workflow.yaml
```

### MCP Endpoint

```bash
# Get MCP server info
curl http://localhost:8080/mcp

# SSE connection for MCP
curl http://localhost:8080/mcp/sse
```

## Workflow Definition

Workflows can be defined in JSON or YAML:

```yaml
name: "PR Notification Workflow"
description: "Summarize PRs and send to Slack"
version: "1.0.0"

trigger:
  type: webhook
  config:
    path: /webhooks/github

steps:
  - id: get_diff
    name: "Get PR Diff"
    type: mcp_tool
    config:
      server: github-mcp
      tool: get_pr_diff
      arguments:
        url: ${inputs.pr_url}

  - id: summarize
    name: "AI Summary"
    type: ai_decision
    config:
      prompt: "Summarize this PR diff: ${steps.get_diff.output}"
      model: llama3.2

  - id: notify
    name: "Send Notification"
    type: mcp_tool
    config:
      server: slack-mcp
      tool: send_message
      arguments:
        channel: "#dev"
        text: ${steps.summarize.output}
```

## MCP Tools Exposed

The server exposes these built-in tools:

- `wiseowlflow.execute_workflow` - Run a saved workflow
- `wiseowlflow.list_workflows` - List available workflows
- `wiseowlflow.get_execution_status` - Check execution state
- `wiseowlflow.save_workflow` - Create/update workflow

Plus all tools aggregated from connected upstream MCP servers.

## Subscription Tiers

| Feature | Free | Pro ($19/mo) | Enterprise ($99/mo) |
|---------|------|--------------|---------------------|
| Workflows | 3 | 25 | Unlimited |
| Executions/month | 100 | 5,000 | Unlimited |
| MCP Servers | 3 | 10 | Unlimited |
| AI Decisions/month | 50 | 1,000 | 10,000 |

## Development

### Build

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Code Style

The project uses Kotlin coding conventions with ktlint.

## Deployment

### Docker

```bash
docker build -t wiseowlflow-mcp .
docker run -p 8080:8080 --env-file .env wiseowlflow-mcp
```

### Render

The project includes `render.yaml` for one-click deployment to Render.

## License

MIT

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

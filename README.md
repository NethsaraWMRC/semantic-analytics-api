# semantic-analytics-api

Ask questions about your business data in plain English and get grounded answers back.

The LLM never writes SQL. It translates your question into a small structured JSON query,
the backend validates every field against a semantic layer, builds parameterised SQL itself,
runs it, and the LLM turns the returned rows into a sentence. Because the LLM only ever
produces JSON from a fixed vocabulary, it cannot reference a column that does not exist or
reach the database directly.

```
question -> LLM -> JSON query -> validate -> build SQL -> database -> rows -> LLM -> answer
```

Datasets live in `agent/src/main/resources/semantic/*.json` — one file per dataset. Adding a metric or dimension
is one line of JSON, not a code change. The LLM picks the right dataset per question.

## Requirements

- Java 21+
- Maven
- Node 18+
- MySQL 8 with your data loaded
- An API key for one of: Gemini, Claude or Groq

## 1. Configure

Create `agent/src/main/resources/application-local.yaml`. It is git-ignored, so real
credentials go here and nowhere else. Fill in the database and **only the key for the
provider you intend to use**:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database
    username: your_user
    password: your_password
llm:
  gemini:
    api-key:
  claude:
    api-key:
  groq:
    api-key:
```

Pick the provider in `agent/src/main/resources/application.yaml`:

```yaml
llm:
  provider: groq        # gemini | claude | groq
```

Each provider has its own `model:` in that same file. Keys: Gemini from
[aistudio.google.com/apikey](https://aistudio.google.com/apikey), Claude from
[console.anthropic.com](https://console.anthropic.com), Groq from
[console.groq.com/keys](https://console.groq.com/keys).

The `agent_chat_messages` table used for conversation history is created automatically
on startup.

## 2. Run the backend

```bash
cd agent
mvn spring-boot:run
```

Serves on http://localhost:8080. Wait for `Started AgentApplication`.

## 3. Run the frontend

```bash
cd ui
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/chat` to the backend, so there is no CORS setup.


## Testing without the LLM

`POST /analytics/descriptive` takes the structured query directly, which is the fastest way
to tell whether a wrong answer came from the model or from the SQL:

```bash
curl -X POST http://localhost:8080/analytics/descriptive \
  -H "Content-Type: application/json" \
  -d '{"dataset":"shopify_sales","metrics":["net_revenue"],"dimensions":[{"field":"vendor"}]}'
```

## Scope

### Descriptive questions only — what happened, not why, not what will happen. 
Results are capped at 100 rows, and the response carries a `truncated` flag when that cap was hit.

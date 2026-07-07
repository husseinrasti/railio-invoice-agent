# Invoice Agent

A chat-first agent that reads invoices from text (and text‑extractable PDFs), decides whether it can
pay them automatically or needs your approval, and drives a **mocked Iranian money‑transfer flow**
(check source balance → create payment to a deposit account + deposit ID → receipt → approve if
needed → execute → final receipt).

The agent is **local‑first**: it talks to a local [Ollama](https://ollama.com) model by default and
never sends data to a cloud provider unless you configure one.

> **Safety by design.** The LLM drives a real Koog tool loop (it reads the invoice and calls the
> tools), but the approval/cap **decision** and the money movement run through deterministic use
> cases inside those tools. The `payNow` tool refuses to transfer when approval is required and not
> yet granted, so a misbehaving model can never bypass the approval gate or the spending cap.

---

## Architecture

Clean Architecture, with the domain at the centre and frameworks at the edges.

```
┌────────────────────────── backend (Ktor + Koog + Koin) ──────────────────────────┐
│                                                                                   │
│  api/            Ktor routes, DTOs, SSE  ── depends on ──▶  usecases + ports      │
│  agent/          Koog AIAgent + tools (LLM-driven), event bus, streaming          │
│  data/           JSON config/invoices, mock payment provider, document parsers    │
│  domain/         models + ports (interfaces) + use cases   ◀── pure Kotlin        │
│  di/             Koin wiring (annotations, KSP-verified)                          │
└───────────────────────────────────────────────────────────────────────────────────┘
                                   │  REST + Server-Sent Events
┌──────────────────────────────────▼────────────── frontend (Next.js) ─────────────┐
│  Chat-first UI · streamed assistant text · invoice/approval/receipt cards         │
│  Desktop-only live agent log panel · configuration page                           │
└───────────────────────────────────────────────────────────────────────────────────┘
```

The `domain` layer has **no framework dependencies**. Every external concern (config store, invoice
source, payment provider, document parsing, event bus) is a **port** with a swappable implementation
— the code is database‑ready without over‑engineering.

### Agent workflow

A Koog `AIAgent` is given three tools and a system prompt; **the LLM decides the calls**. The tools
wrap the deterministic use cases and enforce the gate:

```
user input ─▶ LLM ─▶ readInvoice (records invoice, runs approval policy) ─┬─▶ [within policy]  payNow ─▶ receipt
                                                                          └─▶ [needs approval] requestApproval ─▶ approval card
                                                                                                    │ user approves
                                                                                                    ▼
                                                                                                  payNow ─▶ receipt
```

`payNow` refuses to move money unless the invoice is within policy or the user has approved — the
safety gate lives in the tool, not in the prompt. Every step emits typed events (`tool_call`, `card`,
`log`, `token`, `assistant`, `done`, `error`) over SSE; a Koog `EventHandler` mirrors the LLM/tool
lifecycle to the server log. Cards render inline in the chat; logs stream into the desktop log panel.

> **Note.** Because the model sequences each tool call as a separate LLM turn, end‑to‑end latency
> tracks your model. A small, non‑“thinking” Ollama model keeps runs responsive.

### Approval policy

A payment is auto‑executed **only** when both hold:

- its deposit account name is one of the configured trusted deposit accounts, **and**
- its amount does not exceed the auto‑approval cap.

Otherwise the agent pauses and shows an **approval card**; the transfer runs only after you approve.

### Iranian mock transfer flow

`MockPaymentProvider` simulates a transfer: it reads the source account + balance from configuration,
checks the balance, creates the payment against the deposit account + deposit ID (preview receipt),
and on execution deducts the balance and issues a final receipt with a tracking code. An insufficient
balance fails without moving funds. Because the balance lives in configuration, the config UI always
reflects the live balance.

---

## Tech stack

| Layer     | Choice                                                                 |
|-----------|------------------------------------------------------------------------|
| Language  | Kotlin 2.3.21 (JVM 21)                                                  |
| Agent     | Koog 1.0.0 (Ollama executor, tool-calling AIAgent, EventHandler, streaming) |
| Server    | Ktor 3.5.0 (Netty, SSE, ContentNegotiation, CORS, StatusPages)         |
| DI        | Koin 4.2 with Koin Annotations (KSP, compile‑time verified)            |
| Async     | Kotlin Coroutines 1.11                                                  |
| PDF       | Apache PDFBox 3.0 (text extraction)                                     |
| Frontend  | Next.js 15 (App Router) · React 19 · Tailwind CSS                       |
| Model     | Ollama, default `qwen3.5:4b` (configurable)                              |
| Packaging | Docker + Docker Compose                                                 |

---

## Project structure

```
invoice-agent/
├── backend/
│   └── src/main/kotlin/ai/railio/invoice/
│       ├── domain/{model,port,usecase}   # pure business core
│       ├── data/{config,invoice,payment,document}
│       ├── agent/{tools}                 # Koog AIAgent + tools, event bus, streaming
│       ├── api/{routes,dto}              # Ktor routes + wire DTOs
│       ├── di/                           # Koin module
│       └── Application.kt                # Ktor entry point
├── frontend/
│   ├── app/                              # routes: / (chat), /config
│   ├── components/                       # chat + cards + log panel
│   └── lib/                              # api client, SSE hook, types
├── docker-compose.yml
└── .env.example
```

---

## Running locally

### Prerequisites

- JDK 21, Node.js 20+
- [Ollama](https://ollama.com) running locally, with a model pulled:

  ```bash
  ollama pull qwen3.5:4b
  ```

  Any Ollama chat model works — set `OLLAMA_MODEL` to match what you have pulled. A small,
  non‑“thinking” model gives the fastest responses.

### Backend

```bash
cd backend
OLLAMA_MODEL=qwen3.5:4b ./gradlew run     # serves on http://localhost:8080
```

### Frontend

```bash
cd frontend
npm install
npm run dev                              # serves on http://localhost:3000
```

Open <http://localhost:3000>, insert a sample invoice, and watch the agent work. The log panel (right
side, desktop only) shows the live workflow.

---

## Running with Docker Compose

```bash
cp .env.example .env        # adjust if needed
docker compose up --build
```

This starts Ollama, pulls the default model once, and builds/starts the backend and frontend. Then
open <http://localhost:3000>.

---

## Configuration

All settings have sensible defaults; override via `.env` (see `.env.example`) or the **Config** page:

- **Source account** — name, Sheba/IBAN, and balance funds are drawn from.
- **Deposit accounts** — up to three trusted destinations (name + account). The name on an invoice is
  matched against these.
- **Auto‑approval cap** — payments above this require approval.
- **Agent secret** *(optional, “for later”)* — when set, `/api/**` requires
  `Authorization: Bearer <secret>` (the SSE stream accepts it as a `?token=` query param).

Key environment variables:

| Variable              | Default                   | Purpose                              |
|-----------------------|---------------------------|--------------------------------------|
| `OLLAMA_BASE_URL`     | `http://localhost:11434`  | Ollama server URL                    |
| `OLLAMA_MODEL`        | `qwen3.5:4b`                | Model tag                            |
| `BACKEND_PORT`        | `8080`                    | Backend port                         |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080`   | Backend URL baked into the web client|
| `AGENT_SECRET`        | *(empty)*                 | Optional bearer secret               |

---

## API

| Method | Path                          | Description                                  |
|--------|-------------------------------|----------------------------------------------|
| GET    | `/api/health`                 | Liveness (unauthenticated)                   |
| GET    | `/api/config`                 | Current config (secret masked)               |
| PUT    | `/api/config`                 | Update config (validated)                    |
| GET    | `/api/invoices/samples`       | Seed invoices for the picker                 |
| POST   | `/api/chat`                   | Start an agent run → `{ runId }`             |
| GET    | `/api/chat/{runId}/stream`    | SSE stream of run events                     |
| POST   | `/api/chat/{runId}/approve`   | Approve/reject a pending payment             |

---

## Testing

```bash
cd backend && ./gradlew test
```

- **Domain use cases** — full approval matrix (in/out of trusted list × above/below cap) and config
  validation.
- **Mock payment provider** — balance checks, deduction, insufficient‑balance failure.
- **Repositories** — JSON round‑trip and seed loading.
- **Agent tools** — `readInvoice` / `requestApproval` / `payNow` tested directly: policy decision,
  the approval/cap gate (`payNow` refuses without approval), and post‑approval execution. Deterministic,
  no live LLM.
- **API routes** — `testApplication` covering health, config, samples, and bearer auth.

The frontend builds via `npm run build` (type‑checked).

---

## Design decisions

- **LLM drives tools; tools enforce safety.** The model sequences the Koog tool calls, but money
  movement and the approval/cap gate live inside the tools (deterministic, server‑enforced).
- **Two‑phase approval.** A run that needs approval pauses after creating the payment; its SSE stream
  stays open so the post‑approval receipt reaches the same client.
- **Ports everywhere.** JSON stores and the mock payment provider sit behind interfaces, so a database
  or real gateway drops in without touching callers.
- **Images & scanned PDFs are a future feature.** `DocumentParser` already models them
  (`VisionDocumentParser`); today only plain text and text‑extractable PDFs are supported.

---

## License

Internal project — all rights reserved.

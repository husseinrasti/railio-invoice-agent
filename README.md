# Invoice Agent

A chat-first agent that reads invoices from text (and text‑extractable PDFs) and **proposes** them for
payment to [Railio](https://railio.ai), a financial‑execution layer that decides and executes the
money movement (Iranian bank transfer: PAYA/SATNA to a supplier IBAN).

The agent is **local‑first** for its reasoning: it talks to a local [Ollama](https://ollama.com)
model by default and never sends invoice text to a cloud LLM unless you configure one.

> **The agent does not move money — by design.**
>
> It authenticates to Railio as a *machine identity* with `transfers:create` / `transfers:read`
> scopes. It cannot approve a payment and cannot edit the policies that govern it; Railio rejects
> those calls server-side. So when a policy requires a human, the agent reports and waits — there is
> no code path, and no LLM output, that can approve a payment or raise a limit.
>
> A `201` from Railio means *accepted and evaluated*, **not paid**. The agent branches on `status`.

---

## Architecture

Clean Architecture, with the domain at the centre and frameworks at the edges.

```
┌────────────────────────── backend (Ktor + Koog + Koin) ──────────────────────────┐
│                                                                                   │
│  api/            Ktor routes, DTOs, SSE  ── depends on ──▶  usecases + ports      │
│  agent/          Koog AIAgent + tools (LLM-driven), event bus, streaming          │
│  data/           JSON config/invoices, Railio client, mock provider, parsers      │
│  domain/         models + ports (interfaces) + use cases   ◀── pure Kotlin        │
│  di/             Koin wiring (annotations, KSP-verified)                          │
└───────────────────────────────────────────────────────────────────────────────────┘
          │  REST + Server-Sent Events                │  OAuth2 + REST
┌─────────▼──────────── frontend (Next.js) ──┐   ┌────▼───────────── Railio ─────────┐
│  Chat-first UI · streamed assistant text   │   │  Policy engine → approval/provider │
│  invoice/status/receipt cards              │   │  Executes the money movement       │
│  Desktop-only log panel · config page      │   └────────────────────────────────────┘
└────────────────────────────────────────────┘
```

The `domain` layer has **no framework dependencies**. Every external concern (config store, invoice
source, payment provider, document parsing, event bus) is a **port** with a swappable implementation
— which is exactly why moving money onto Railio was a new `PaymentProvider`, not a rewrite.

### Agent workflow

A Koog `AIAgent` is given two tools and a system prompt; **the LLM decides the calls**, but the tools
are the only way it can act:

```
user input ─▶ LLM ─▶ readInvoice ─▶ payNow ─▶ POST /api/v1/transfers ─▶ Railio policy engine
                                                                             │
                    ┌────────────────────────────┬───────────────────────────┤
                    ▼                            ▼                           ▼
               COMPLETED                     FAILED                  AWAITING_APPROVAL
              receipt card            denial → escalate,          read-only status card;
                                       never retried              a human decides in Railio
                                                                             │ backend polls (2s→30s)
                                                                             ▼
                                                                      final receipt card
```

`payNow` proposes; it cannot execute or approve. Every step emits typed events (`tool_call`, `card`,
`log`, `token`, `assistant`, `done`, `error`) over SSE; a Koog `EventHandler` mirrors the LLM/tool
lifecycle to the server log. Cards render inline in the chat; logs stream into the desktop log panel.

> **Note.** Because the model sequences each tool call as a separate LLM turn, end‑to‑end latency
> tracks your model. A small, non‑“thinking” Ollama model keeps runs responsive.

### Who decides what

| Decision | Owner |
|---|---|
| What the invoice says | The LLM (extraction only) |
| Which IBAN a deposit name maps to | This app's address book (Config page) |
| Which account the money comes from | **Railio** — discovered per payment, per the agent's own visibility |
| Whether the payment is allowed, and its limits | **Railio policy** — set by a human in the dashboard |
| Approving a parked payment | **A human**, in the Railio dashboard |
| Executing the transfer | **Railio** |

The agent holds `transfers:create` and `transfers:read`. It is never granted `payments:approve` or
`policies:write`, so it cannot approve its own payments or raise its own limits — Railio rejects
those calls regardless of what the model attempts.

### Integration details that matter

- **Idempotency** — every proposal sends `Idempotency-Key: invoice-<id>`, keyed to the *business
  event*, not the attempt. A timeout + retry returns the existing transfer instead of paying twice.
  This is not theoretical: against the live API the model called `payNow` twice in *both* test runs.
  The invoice id is therefore derived from the invoice itself (deposit id + amount), never minted
  fresh, so a re-read collapses onto the same key.
- **The source is discovered** (`GET /api/v1/bank-accounts`), never pinned. Railio filters the list
  to shared accounts plus this agent's own, and applies no default of its own — so the agent picks
  (assigned-to-me → tenant default → any ACTIVE match) and always sends the id explicitly.
- **Card numbers never enter the app.** Identifiers come back unmasked; the bank-account DTO omits
  `cardNumber` so a PAN is dropped at the boundary rather than trusted not to reach a log or prompt.
- **Money is a decimal string** (`"5000000"`), never a float; the tenant comes from the token, never
  the body.
- **A policy denial is not retried.** It is deterministic — an identical retry fails identically, and
  a retry loop would trip Velocity limits and mask the real cause. It escalates to a human instead.
- **Errors** are RFC‑7807; the client branches on the stable `code` and logs `requestId`. `403`/`422`
  and denials are non-retryable; a `401` refreshes the token exactly once (a second means the
  credential was rotated or revoked).
- **Token** is cached for its 1-hour TTL, not fetched per request.

### Offline mock

`PAYMENT_PROVIDER=mock` (the default) swaps in `MockPaymentProvider` so the app runs with no Railio
and no credentials. It imitates the parts that shape the code: idempotency, an approval‑threshold
policy that parks a transfer (then resolves it, as if a human approved), and an
insufficient‑balance failure that moves no funds. Its balance lives in config, so the Config page
reflects it.

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
| Model     | Ollama, default `gemma4:12b` (configurable)                              |
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
  ollama pull gemma4:12b
  ```

  Any Ollama chat model works — set `OLLAMA_MODEL` to match what you have pulled. A small,
  non‑“thinking” model gives the fastest responses.

### Backend

```bash
cd backend
OLLAMA_MODEL=gemma4:12b ./gradlew run     # serves on http://localhost:8080
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

- **Railio** — API base URL and client id (`agt_…`). The account payments are drawn from is
  **discovered**, not configured: assignment and defaults change in the dashboard, so a pinned id
  would go stale silently.
- **Deposit accounts** — up to three, an address book mapping the deposit name on an invoice to the
  IBAN to pay. *Not* a trust list.
- **Source account** — name, Sheba/IBAN, balance. **Mock provider only**; with Railio the funds come
  from the linked bank account.
- **Agent secret** *(optional)* — when set, this backend's `/api/**` requires
  `Authorization: Bearer <secret>` (the SSE stream accepts it as a `?token=` query param).

There is **no spending cap setting**. Limits and approval thresholds are Railio policies a human sets
in the dashboard; an agent cannot raise its own limits, so mirroring them here would be a lie.

Key environment variables:

| Variable                 | Default                   | Purpose                                   |
|--------------------------|---------------------------|-------------------------------------------|
| `PAYMENT_PROVIDER`       | `mock`                    | `mock` or `railio`                        |
| `RAILIO_CLIENT_SECRET`   | *(empty)*                 | Railio credential secret — **env only**   |
| `OLLAMA_BASE_URL`        | `http://localhost:11434`  | Ollama server URL                         |
| `OLLAMA_MODEL`           | `gemma4:12b`              | Model tag                                 |
| `BACKEND_PORT`           | `8080`                    | Backend port                              |
| `NEXT_PUBLIC_API_URL`    | `http://localhost:8080`   | Backend URL baked into the web client     |
| `AGENT_SECRET`           | *(empty)*                 | Optional bearer secret for this backend   |
| `MOCK_APPROVAL_THRESHOLD`| `10000000`                | Mock: park above this amount              |
| `MOCK_APPROVAL_DELAY_SECONDS` | `8`                  | Mock: how long a parked transfer waits    |

`RAILIO_CLIENT_SECRET` is read from the environment only — never written to `config.json`, never
returned by the config API. Use a secret manager in production; rotating it in the Railio dashboard
invalidates the old secret immediately.

### Connecting to Railio

A human does these first — the agent cannot:

1. **Agents → New agent** (category `INVOICE`) → **New credential** with scopes `transfers:create`
   and `transfers:read`. The secret is shown once; export it as `RAILIO_CLIENT_SECRET`.
2. **Bank accounts → Add bank account** (needs an IBAN). Choose **Available to** — *All agents*, or
   *One agent* to scope it to this one — and optionally **Make default**. Nothing to copy: the agent
   discovers it. It prefers an account assigned to itself, then the tenant default, then any `ACTIVE`
   account in the right currency.
3. **Policies** — set the guardrails: an `AMOUNT` per-transaction limit, an `APPROVAL_THRESHOLD`, and
   a `PURPOSE` policy allowing `INVOICE`. **Until an `APPROVAL_THRESHOLD` exists, nothing ever parks
   for approval** — every proposal within the other limits simply executes.
4. Set `PAYMENT_PROVIDER=railio`. Start against a **Sandbox** environment: it completes immediately,
   and breaching a limit exercises the `FAILED` / `AWAITING_APPROVAL` paths without real money.

---

## API

| Method | Path                          | Description                                  |
|--------|-------------------------------|----------------------------------------------|
| GET    | `/api/health`                 | Liveness (unauthenticated)                   |
| GET    | `/api/config`                 | Current config (secrets never serialized)    |
| PUT    | `/api/config`                 | Update config (validated)                    |
| GET    | `/api/invoices/samples`       | Seed invoices for the picker                 |
| POST   | `/api/chat`                   | Start an agent run → `{ runId }`             |
| GET    | `/api/chat/{runId}/stream`    | SSE stream of run events                     |

There is deliberately no approve endpoint: a parked payment is approved by a human in the Railio
dashboard, and this agent's credential has no approve scope. The run polls and reports the outcome
on its existing stream.

---

## Testing

```bash
cd backend && ./gradlew test
```

- **Railio client** — drives the real HTTP client against canned responses (Ktor `MockEngine`), so no
  live Railio is needed: `201 AWAITING_APPROVAL` is not treated as paid, the idempotency key and
  decimal‑string amount are actually sent, policy denials / `403` / `422` are non‑retryable, `5xx` is,
  a `401` refreshes the token exactly once, and the token is fetched once and reused.
- **Mock payment provider** — completion + balance deduction, the approval park (no funds move while
  parked, resolves after the delay), same‑key‑never‑pays‑twice, insufficient‑balance failure.
- **Agent tools** — `readInvoice` / `payNow` tested directly, no live LLM: propose‑and‑park, unknown
  deposit account, provider failure, and an assertion that the toolset exposes *no* way to approve.
- **Config** — validation, JSON round‑trip, and that the Railio secret never reaches disk or the API.
- **API routes** — `testApplication` covering health, config, samples, and bearer auth.

The frontend builds via `npm run build` (type‑checked).

---

## Design decisions

- **The agent proposes; Railio decides.** The model sequences the Koog tool calls, but the tools can
  only propose. Approval and limits are enforced by Railio server‑side, not by our prompt — the
  safety property does not depend on the model behaving.
- **The port made this cheap.** Moving money onto Railio meant a new `PaymentProvider` and a reshaped
  port (propose→poll rather than create→execute), not a rewrite. The mock still satisfies the same
  port, so the app runs offline.
- **Parked runs keep their stream.** A run parked on an approval polls with backoff (2s→30s, 10min
  cap) and reports the outcome on the same SSE stream, so the receipt reaches the client that asked.
- **No local mirror of remote policy.** We deleted the cap rather than duplicate a Railio policy we
  cannot enforce; a stale local copy of someone else's rule is worse than none.
- **Images & scanned PDFs are a future feature.** `DocumentParser` already models them
  (`VisionDocumentParser`); today only plain text and text‑extractable PDFs are supported.

---

## License

Internal project — all rights reserved.

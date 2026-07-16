"use client";

import { useEffect, useState } from "react";
import { fetchConfig, saveConfig, setSecret } from "@/lib/api";
import type { ConfigView, DepositAccountView } from "@/lib/types";

const MAX_DEPOSITS = 3;

export default function ConfigPage() {
  const [config, setConfig] = useState<ConfigView | null>(null);
  const [secret, setSecretInput] = useState("");
  const [status, setStatus] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchConfig()
      .then(setConfig)
      .catch((e) => setStatus({ kind: "err", text: String(e) }));
  }, []);

  if (!config) {
    return <div className="p-6 text-sm text-slate-400">Loading configuration…</div>;
  }

  const update = (patch: Partial<ConfigView>) => setConfig({ ...config, ...patch });
  const updateDeposit = (i: number, patch: Partial<DepositAccountView>) =>
    update({ depositAccounts: config.depositAccounts.map((d, j) => (j === i ? { ...d, ...patch } : d)) });

  const save = async () => {
    setSaving(true);
    setStatus(null);
    try {
      const body = {
        sourceAccount: config.sourceAccount,
        depositAccounts: config.depositAccounts,
        railio: {
          baseUrl: config.railio.baseUrl,
          clientId: config.railio.clientId,
        },
        ...(secret ? { agentSecret: secret } : {}),
      };
      const saved = await saveConfig(body);
      setConfig(saved);
      if (secret) setSecret(secret);
      setSecretInput("");
      setStatus({ kind: "ok", text: "Configuration saved." });
    } catch (e) {
      setStatus({ kind: "err", text: String(e) });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-8 p-6">
      <h1 className="text-xl font-semibold">Configuration</h1>

      {/* Railio */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-slate-500">Railio</h2>
        <p className="text-xs text-slate-400">
          Railio executes the payments this agent proposes. Spending limits and approval thresholds
          are Railio <em>policies</em> — a person sets them in the Railio dashboard, and an agent
          cannot raise its own limits, so they are not settings here.
        </p>
        <Field label="API base URL">
          <input
            className="input"
            value={config.railio.baseUrl}
            onChange={(e) => update({ railio: { ...config.railio, baseUrl: e.target.value } })}
          />
        </Field>
        <Field label="Client ID (agt_…)">
          <input
            className="input font-mono"
            value={config.railio.clientId}
            onChange={(e) => update({ railio: { ...config.railio, clientId: e.target.value } })}
          />
        </Field>
        <p className="text-xs text-slate-400">
          The account payments are drawn from is <strong>discovered</strong> from Railio at payment
          time — there is nothing to set here. The agent prefers an account assigned to it, then the
          tenant default. Add and assign accounts in the Railio dashboard.
        </p>
        <p className="text-xs text-slate-400">
          Client secret:{" "}
          {config.railio.hasSecret ? (
            <span className="text-emerald-600">set via RAILIO_CLIENT_SECRET</span>
          ) : (
            <span className="text-amber-600">not set — export RAILIO_CLIENT_SECRET</span>
          )}
          . It is read from the environment only and is never stored or shown here.
        </p>
      </section>

      {/* Source account (mock provider) */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-slate-500">Source account (mock provider only)</h2>
        <p className="text-xs text-slate-400">
          Used when <code>PAYMENT_PROVIDER=mock</code>. With Railio the funds come from the linked
          bank account above.
        </p>
        <Field label="Name">
          <input
            className="input"
            value={config.sourceAccount.name}
            onChange={(e) => update({ sourceAccount: { ...config.sourceAccount, name: e.target.value } })}
          />
        </Field>
        <Field label="Account number (Sheba)">
          <input
            className="input font-mono"
            value={config.sourceAccount.accountNumber}
            onChange={(e) =>
              update({ sourceAccount: { ...config.sourceAccount, accountNumber: e.target.value } })
            }
          />
        </Field>
        <Field label="Balance (IRR)">
          <input
            type="number"
            className="input"
            value={config.sourceAccount.balance}
            onChange={(e) =>
              update({ sourceAccount: { ...config.sourceAccount, balance: Number(e.target.value) } })
            }
          />
        </Field>
      </section>

      {/* Deposit accounts */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-500">Deposit accounts (up to {MAX_DEPOSITS})</h2>
          {config.depositAccounts.length < MAX_DEPOSITS && (
            <button
              className="text-sm text-brand-600 hover:underline"
              onClick={() =>
                update({ depositAccounts: [...config.depositAccounts, { name: "", accountNumber: "" }] })
              }
            >
              + Add
            </button>
          )}
        </div>
        <p className="text-xs text-slate-400">
          An address book: the deposit name printed on an invoice is matched here to find the IBAN to
          pay. It is not a trust list — whether a payment is allowed is Railio&apos;s decision.
        </p>
        {config.depositAccounts.map((d, i) => (
          <div key={i} className="flex items-end gap-2">
            <Field label="Name" className="flex-1">
              <input className="input" value={d.name} onChange={(e) => updateDeposit(i, { name: e.target.value })} />
            </Field>
            <Field label="IBAN" className="flex-1">
              <input
                className="input font-mono"
                value={d.accountNumber}
                onChange={(e) => updateDeposit(i, { accountNumber: e.target.value })}
              />
            </Field>
            <button
              className="mb-1 rounded-md px-2 py-1 text-sm text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950"
              onClick={() => update({ depositAccounts: config.depositAccounts.filter((_, j) => j !== i) })}
            >
              Remove
            </button>
          </div>
        ))}
      </section>

      {/* This backend's own auth */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-slate-500">This backend</h2>
        <Field label={`Agent secret ${config.hasSecret ? "(set — leave blank to keep)" : "(optional)"}`}>
          <input
            type="password"
            className="input"
            placeholder={config.hasSecret ? "••••••••" : "Leave blank to disable auth"}
            value={secret}
            onChange={(e) => setSecretInput(e.target.value)}
          />
        </Field>
      </section>

      <div className="flex items-center gap-3">
        <button
          className="rounded-lg bg-brand-600 px-5 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          onClick={save}
          disabled={saving}
        >
          {saving ? "Saving…" : "Save"}
        </button>
        {status && (
          <span className={status.kind === "ok" ? "text-sm text-emerald-600" : "text-sm text-rose-600"}>
            {status.text}
          </span>
        )}
      </div>

      <style jsx>{`
        :global(.input) {
          width: 100%;
          border-radius: 0.5rem;
          border: 1px solid rgb(203 213 225);
          background: transparent;
          padding: 0.5rem 0.75rem;
          font-size: 0.875rem;
          outline: none;
        }
        :global(.input:focus) {
          border-color: rgb(99 102 241);
        }
      `}</style>
    </div>
  );
}

function Field({
  label,
  className = "",
  children,
}: {
  label: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <label className={`block ${className}`}>
      <span className="mb-1 block text-xs text-slate-400">{label}</span>
      {children}
    </label>
  );
}

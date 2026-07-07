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
        autoApprovalCap: config.autoApprovalCap,
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

      {/* Source account */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-slate-500">Source account</h2>
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
              onClick={() => update({ depositAccounts: [...config.depositAccounts, { name: "", accountNumber: "" }] })}
            >
              + Add
            </button>
          )}
        </div>
        {config.depositAccounts.map((d, i) => (
          <div key={i} className="flex items-end gap-2">
            <Field label="Name" className="flex-1">
              <input className="input" value={d.name} onChange={(e) => updateDeposit(i, { name: e.target.value })} />
            </Field>
            <Field label="Account number" className="flex-1">
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

      {/* Policy */}
      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-slate-500">Payment policy</h2>
        <Field label="Auto-approval cap (IRR) — payments above this need approval">
          <input
            type="number"
            className="input"
            value={config.autoApprovalCap}
            onChange={(e) => update({ autoApprovalCap: Number(e.target.value) })}
          />
        </Field>
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

"use client";

import { useEffect, useState } from "react";
import { fetchSamples } from "@/lib/api";
import type { InvoiceView } from "@/lib/types";

/** Turns a sample invoice into the free-text a user would paste into the chat. */
function invoiceToText(inv: InvoiceView): string {
  const due = inv.expiresAt ? ` Due ${inv.expiresAt.slice(0, 10)}.` : "";
  return (
    `Invoice: ${inv.detail}. Amount: ${inv.amount.toLocaleString()} ${inv.currency}. ` +
    `Pay to ${inv.depositAccountName}. Deposit ID: ${inv.depositId}.${due}`
  );
}

/** Dropdown of seed invoices; selecting one fills the composer via [onPick]. */
export default function SampleInvoicePicker({ onPick }: { onPick: (text: string) => void }) {
  const [samples, setSamples] = useState<InvoiceView[]>([]);

  useEffect(() => {
    fetchSamples().then(setSamples).catch(() => setSamples([]));
  }, []);

  return (
    <select
      aria-label="Insert a sample invoice"
      className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm dark:border-slate-700 dark:bg-slate-800"
      value=""
      onChange={(e) => {
        const inv = samples.find((s) => s.id === e.target.value);
        if (inv) onPick(invoiceToText(inv));
        e.currentTarget.value = "";
      }}
    >
      <option value="">Insert invoice…</option>
      {samples.map((s) => (
        <option key={s.id} value={s.id}>
          {s.detail} — {s.amount.toLocaleString()} {s.currency}
        </option>
      ))}
    </select>
  );
}

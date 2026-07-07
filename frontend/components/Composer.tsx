"use client";

import { useState } from "react";
import SampleInvoicePicker from "./SampleInvoicePicker";

/** Message input with a sample-invoice picker. Enter sends; Shift+Enter adds a newline. */
export default function Composer({
  onSend,
  disabled,
}: {
  onSend: (text: string) => void;
  disabled: boolean;
}) {
  const [text, setText] = useState("");

  const submit = () => {
    if (!text.trim() || disabled) return;
    onSend(text);
    setText("");
  };

  return (
    <div className="border-t border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900">
      <div className="mb-2">
        <SampleInvoicePicker onPick={setText} />
      </div>
      <div className="flex items-end gap-2">
        <textarea
          className="min-h-[44px] flex-1 resize-none rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 dark:border-slate-700 dark:bg-slate-800"
          rows={2}
          placeholder="Paste an invoice or insert a sample…"
          value={text}
          disabled={disabled}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
        />
        <button
          className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          onClick={submit}
          disabled={disabled}
        >
          {disabled ? "Working…" : "Send"}
        </button>
      </div>
    </div>
  );
}

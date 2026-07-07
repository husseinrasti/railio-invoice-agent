"use client";

import { useEffect, useRef } from "react";
import type { LogEntry } from "@/lib/types";

const LEVEL_COLOR: Record<LogEntry["level"], string> = {
  DEBUG: "text-slate-400",
  INFO: "text-emerald-500",
  WARN: "text-amber-500",
  ERROR: "text-rose-500",
};

/**
 * Live agent-workflow log. Rendered only on desktop (the parent hides it below `lg`), matching the
 * requirement to show logs beside the chat on wide screens and never on mobile.
 */
export default function LogPanel({ logs }: { logs: LogEntry[] }) {
  const endRef = useRef<HTMLDivElement>(null);
  useEffect(() => endRef.current?.scrollIntoView({ behavior: "smooth" }), [logs.length]);

  return (
    <aside className="flex h-full w-[360px] shrink-0 flex-col border-l border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
      <div className="border-b border-slate-200 px-4 py-3 text-sm font-semibold dark:border-slate-800">
        Agent logs
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3 font-mono text-xs leading-relaxed">
        {logs.length === 0 ? (
          <p className="text-slate-400">Workflow logs will appear here as the agent runs.</p>
        ) : (
          logs.map((l, i) => (
            <div key={i} className="whitespace-pre-wrap break-words">
              <span className={LEVEL_COLOR[l.level]}>{l.level.padEnd(5)}</span>{" "}
              <span className="text-slate-400">[{l.source}]</span> {l.message}
            </div>
          ))
        )}
        <div ref={endRef} />
      </div>
    </aside>
  );
}

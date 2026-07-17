import type { Thinking } from "@/lib/useChat";

/**
 * Shows that the model is working, with its name. Driven by SSE `thinking` events, so it reflects
 * real LLM activity (on while the model reasons, gone the moment streamed text starts).
 */
export default function ThinkingIndicator({ thinking }: { thinking: Thinking | null }) {
  if (!thinking?.active) return null;

  return (
    <div className="flex justify-start" aria-live="polite">
      <div className="flex items-center gap-2 rounded-2xl bg-white px-4 py-2 text-sm text-slate-500 shadow-sm dark:bg-slate-800 dark:text-slate-400">
        <span className="flex gap-1" aria-hidden>
          <Dot delay="0ms" />
          <Dot delay="150ms" />
          <Dot delay="300ms" />
        </span>
        <span>
          Thinking<span className="mx-1 text-slate-300 dark:text-slate-600">·</span>
          <span className="font-mono text-xs text-slate-400">{thinking.label}</span>
        </span>
      </div>
    </div>
  );
}

function Dot({ delay }: { delay: string }) {
  return (
    <span
      className="inline-block h-1.5 w-1.5 animate-bounce rounded-full bg-brand-500"
      style={{ animationDelay: delay }}
    />
  );
}

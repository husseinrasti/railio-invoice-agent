"use client";

import { useEffect, useRef } from "react";
import type { ChatItem } from "@/lib/useChat";
import ChatCard from "./cards/ChatCard";

/**
 * Renders the chat transcript: user bubbles, streamed assistant text, error notices, and (from the
 * next milestone) inline invoice/approval/receipt cards.
 */
export default function MessageList({
  items,
  onApprove,
}: {
  items: ChatItem[];
  onApprove: (runId: string, approved: boolean) => void;
}) {
  const endRef = useRef<HTMLDivElement>(null);
  useEffect(() => endRef.current?.scrollIntoView({ behavior: "smooth" }), [items]);

  return (
    <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-6">
      {items.length === 0 && (
        <p className="mx-auto max-w-md text-center text-sm text-slate-400">
          Insert a sample invoice or paste one to get started. The agent will read it, decide whether it
          can pay automatically, and ask for approval when needed.
        </p>
      )}

      {items.map((item) => {
        switch (item.type) {
          case "user":
            return (
              <div key={item.id} className="flex justify-end">
                <div className="max-w-[80%] whitespace-pre-wrap rounded-2xl bg-brand-600 px-4 py-2 text-sm text-white">
                  {item.text}
                </div>
              </div>
            );
          case "assistant":
            return (
              <div key={item.id} className="flex justify-start">
                <div className="max-w-[80%] whitespace-pre-wrap rounded-2xl bg-white px-4 py-2 text-sm shadow-sm dark:bg-slate-800">
                  {item.text}
                  {item.streaming && <span className="ml-0.5 animate-pulse">▍</span>}
                </div>
              </div>
            );
          case "error":
            return (
              <div key={item.id} className="flex justify-start">
                <div className="max-w-[80%] rounded-2xl border border-rose-300 bg-rose-50 px-4 py-2 text-sm text-rose-700 dark:border-rose-800 dark:bg-rose-950 dark:text-rose-300">
                  {item.text}
                </div>
              </div>
            );
          case "card":
            return (
              <div key={item.id} className="flex justify-start">
                <ChatCard card={item.card} runId={item.runId} onApprove={onApprove} />
              </div>
            );
        }
      })}
      <div ref={endRef} />
    </div>
  );
}

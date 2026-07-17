"use client";

import { useEffect, useRef } from "react";
import type { ChatItem, Thinking } from "@/lib/useChat";
import ChatCard from "./cards/ChatCard";
import ThinkingIndicator from "./ThinkingIndicator";

/**
 * Renders the chat transcript: user bubbles, streamed assistant text, error notices, and inline
 * invoice/status/receipt cards.
 */
export default function MessageList({
  items,
  thinking,
}: {
  items: ChatItem[];
  thinking?: Thinking | null;
}) {
  const endRef = useRef<HTMLDivElement>(null);
  useEffect(() => endRef.current?.scrollIntoView({ behavior: "smooth" }), [items, thinking]);

  return (
    <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-6">
      {items.length === 0 && (
        <p className="mx-auto max-w-md text-center text-sm text-slate-400">
          Insert a sample invoice or paste one to get started. The agent reads it and proposes the
          payment to Railio, which decides whether it executes or needs a person to approve it.
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
                <ChatCard card={item.card} />
              </div>
            );
        }
      })}
      <ThinkingIndicator thinking={thinking ?? null} />
      <div ref={endRef} />
    </div>
  );
}

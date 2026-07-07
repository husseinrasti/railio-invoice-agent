"use client";

import { useCallback, useRef, useState } from "react";
import { approvePayment, startChat, streamUrl } from "./api";
import type { CardWire, LogEntry } from "./types";

export type ChatItem =
  | { type: "user"; id: string; text: string }
  | { type: "assistant"; id: string; text: string; streaming: boolean }
  | { type: "card"; id: string; runId: string; card: CardWire }
  | { type: "error"; id: string; text: string };

let counter = 0;
const nextId = () => `m${counter++}`;

/**
 * Drives a single chat conversation: posts a message, opens the SSE stream, and turns events into
 * chat items (streamed assistant text + inline cards) and a separate workflow log list.
 */
export function useChat() {
  const [items, setItems] = useState<ChatItem[]>([]);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [busy, setBusy] = useState(false);
  const esRef = useRef<EventSource | null>(null);
  const runIdRef = useRef<string | null>(null);
  const assistantIdRef = useRef<string | null>(null);

  const closeStream = useCallback(() => {
    esRef.current?.close();
    esRef.current = null;
    assistantIdRef.current = null;
    setBusy(false);
  }, []);

  const appendToken = useCallback((text: string) => {
    setItems((prev) => {
      if (assistantIdRef.current) {
        return prev.map((it) =>
          it.id === assistantIdRef.current && it.type === "assistant"
            ? { ...it, text: it.text + text }
            : it,
        );
      }
      const id = nextId();
      assistantIdRef.current = id;
      return [...prev, { type: "assistant", id, text, streaming: true }];
    });
  }, []);

  const finalizeAssistant = useCallback((text: string) => {
    setItems((prev) => {
      const id = assistantIdRef.current;
      assistantIdRef.current = null;
      if (id && prev.some((it) => it.id === id)) {
        return prev.map((it) => (it.id === id ? { ...it, text, streaming: false } : it));
      }
      return [...prev, { type: "assistant", id: nextId(), text, streaming: false }];
    });
  }, []);

  const send = useCallback(
    async (message: string) => {
      const text = message.trim();
      if (!text || busy) return;
      setBusy(true);
      setItems((prev) => [...prev, { type: "user", id: nextId(), text }]);

      let runId: string;
      try {
        runId = await startChat(text);
      } catch (e) {
        setItems((prev) => [...prev, { type: "error", id: nextId(), text: `Failed to reach the agent: ${String(e)}` }]);
        setBusy(false);
        return;
      }
      runIdRef.current = runId;

      const es = new EventSource(streamUrl(runId));
      esRef.current = es;

      es.addEventListener("token", (e) => appendToken(JSON.parse((e as MessageEvent).data).text));
      es.addEventListener("assistant", (e) => finalizeAssistant(JSON.parse((e as MessageEvent).data).text));
      es.addEventListener("tool_call", (e) => {
        const d = JSON.parse((e as MessageEvent).data) as LogEntry;
        setLogs((prev) => [...prev, { ...d, level: "INFO" }]);
      });
      es.addEventListener("log", (e) => {
        setLogs((prev) => [...prev, JSON.parse((e as MessageEvent).data) as LogEntry]);
      });
      es.addEventListener("card", (e) => {
        const card = JSON.parse((e as MessageEvent).data) as CardWire;
        setItems((prev) => [...prev, { type: "card", id: nextId(), runId, card }]);
      });
      es.addEventListener("error", (e) => {
        const msg = (e as MessageEvent).data ? JSON.parse((e as MessageEvent).data).message : "Stream error";
        setItems((prev) => [...prev, { type: "error", id: nextId(), text: msg }]);
        closeStream();
      });
      es.addEventListener("done", () => closeStream());
    },
    [busy, appendToken, finalizeAssistant, closeStream],
  );

  const approve = useCallback(async (runId: string, approved: boolean) => {
    await approvePayment(runId, approved);
  }, []);

  const reset = useCallback(() => {
    closeStream();
    setItems([]);
    setLogs([]);
  }, [closeStream]);

  return { items, logs, busy, send, approve, reset };
}

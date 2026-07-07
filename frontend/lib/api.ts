// Thin API client for the backend. Base URL and optional agent secret come from the environment /
// browser storage so the same build works across deployments.

import type { ConfigView, InvoiceView } from "./types";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

const SECRET_KEY = "invoice-agent-secret";

/** Reads the optional agent secret saved in the browser (empty when auth is disabled). */
export function getSecret(): string {
  if (typeof window === "undefined") return "";
  return window.localStorage.getItem(SECRET_KEY) ?? "";
}

export function setSecret(secret: string): void {
  if (typeof window === "undefined") return;
  if (secret) window.localStorage.setItem(SECRET_KEY, secret);
  else window.localStorage.removeItem(SECRET_KEY);
}

function authHeaders(): Record<string, string> {
  const secret = getSecret();
  return secret ? { Authorization: `Bearer ${secret}` } : {};
}

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  return res.json() as Promise<T>;
}

export async function fetchSamples(): Promise<InvoiceView[]> {
  return json(await fetch(`${API_URL}/api/invoices/samples`, { headers: authHeaders() }));
}

export async function fetchConfig(): Promise<ConfigView> {
  return json(await fetch(`${API_URL}/api/config`, { headers: authHeaders() }));
}

export async function saveConfig(body: unknown): Promise<ConfigView> {
  return json(
    await fetch(`${API_URL}/api/config`, {
      method: "PUT",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify(body),
    }),
  );
}

/** Starts an agent run and returns its id. */
export async function startChat(message: string): Promise<string> {
  const res = await json<{ runId: string }>(
    await fetch(`${API_URL}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ message }),
    }),
  );
  return res.runId;
}

export async function approvePayment(runId: string, approved: boolean): Promise<void> {
  await fetch(`${API_URL}/api/chat/${runId}/approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ approved }),
  });
}

/** SSE stream URL for a run, carrying the secret as a query param (EventSource can't set headers). */
export function streamUrl(runId: string): string {
  const secret = getSecret();
  const suffix = secret ? `?token=${encodeURIComponent(secret)}` : "";
  return `${API_URL}/api/chat/${runId}/stream${suffix}`;
}

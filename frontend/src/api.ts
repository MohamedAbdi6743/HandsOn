// The only file that talks to the backend. Every page goes through these functions, so
// CSRF tokens, JSON and error handling work the same way everywhere.

import type { ApiErrorBody, User } from "./types.js";

/** An error response from the API, or a network failure (status 0). */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function readCookie(name: string): string | null {
  for (const part of document.cookie.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) {
      return decodeURIComponent(rest.join("="));
    }
  }
  return null;
}

/**
 * The CSRF token lives in the XSRF-TOKEN cookie. Requests that change data must copy it
 * into the X-XSRF-TOKEN header. Another website can't read our cookies, so it can't do this.
 * If there's no cookie yet, ask the server for one first.
 */
async function getCsrfToken(): Promise<string> {
  let token = readCookie("XSRF-TOKEN");
  if (token === null) {
    await fetch("/api/auth/csrf", { credentials: "same-origin" });
    token = readCookie("XSRF-TOKEN");
  }
  return token ?? "";
}

async function sendRequest<T>(method: string, path: string, headers: Record<string, string>, body?: string): Promise<T> {
  if (method !== "GET") {
    headers["X-XSRF-TOKEN"] = await getCsrfToken();
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "Can't reach the server. Check your connection and try again.");
  }
  return readResponse<T>(response);
}

async function readResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let data: unknown = null;
  if (text !== "") {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (response.ok) {
    return data as T;
  }
  const error = data as Partial<ApiErrorBody> | null;
  throw new ApiError(
    response.status,
    error?.message ?? `Something went wrong (error ${response.status}).`,
    error?.fieldErrors ?? {},
  );
}

function sendJson<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body === undefined) {
    return sendRequest<T>(method, path, headers);
  }
  headers["Content-Type"] = "application/json";
  return sendRequest<T>(method, path, headers, JSON.stringify(body));
}

export const api = {
  get: <T>(path: string) => sendJson<T>("GET", path),
  post: <T>(path: string, body?: unknown) => sendJson<T>("POST", path, body),
  put: <T>(path: string, body: unknown) => sendJson<T>("PUT", path, body),
  delete: (path: string) => sendJson<void>("DELETE", path),
};

/** Login uses form fields, not JSON, because it goes through Spring Security's login handling. */
export function login(email: string, password: string): Promise<User> {
  const headers = {
    Accept: "application/json",
    "Content-Type": "application/x-www-form-urlencoded",
  };
  const form = new URLSearchParams({ email, password });
  return sendRequest<User>("POST", "/api/auth/login", headers, form.toString());
}

export function logout(): Promise<void> {
  return sendJson<void>("POST", "/api/auth/logout");
}

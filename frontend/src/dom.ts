// Small helpers for building pages safely and showing feedback.

import type { SignupStatus } from "./types.js";

export function byId<T extends HTMLElement = HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (element === null) {
    throw new Error(`The page is missing #${id}`);
  }
  return element as T;
}

interface ElementOptions {
  text?: string;
  className?: string;
  attributes?: Record<string, string>;
}

/**
 * Creates an element. Text always goes in with textContent, never innerHTML, so text typed
 * by users (like a name containing "<script>") is shown as plain text and never runs.
 * That's what prevents cross-site scripting (XSS).
 */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  options: ElementOptions = {},
  children: Array<Node | null> = [],
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  if (options.text !== undefined) {
    element.textContent = options.text;
  }
  if (options.className !== undefined) {
    element.className = options.className;
  }
  for (const [name, value] of Object.entries(options.attributes ?? {})) {
    element.setAttribute(name, value);
  }
  for (const child of children) {
    if (child !== null) {
      element.append(child);
    }
  }
  return element;
}

export function button(text: string, variant: "primary" | "secondary" | "danger" = "primary"): HTMLButtonElement {
  return el("button", { text, className: `button button-${variant}`, attributes: { type: "button" } });
}

// ---------- the message area (role="status", so screen readers announce it) ----------

export type MessageKind = "info" | "success" | "error";

export function showMessage(text: string, kind: MessageKind = "info"): void {
  const box = byId("message");
  box.textContent = text;
  box.className = `message message-${kind}`;
}

export function clearMessage(): void {
  const box = byId("message");
  box.textContent = "";
  box.className = "message";
}

/** Disables a button and changes its text while an action runs, so it can't be clicked twice. */
export async function withLoading<T>(target: HTMLButtonElement, busyText: string, action: () => Promise<T>): Promise<T> {
  const originalText = target.textContent;
  target.disabled = true;
  target.textContent = busyText;
  try {
    return await action();
  } finally {
    target.disabled = false;
    target.textContent = originalText;
  }
}

// ---------- forms ----------

export function formValue(form: HTMLFormElement, name: string): string {
  const value = new FormData(form).get(name);
  return typeof value === "string" ? value.trim() : "";
}

/** Shows the server's per-field messages under the matching inputs. */
export function showFieldErrors(form: HTMLFormElement, fieldErrors: Record<string, string>): void {
  clearFieldErrors(form);
  for (const [field, message] of Object.entries(fieldErrors)) {
    const errorBox = form.querySelector<HTMLElement>(`[data-error-for="${CSS.escape(field)}"]`);
    if (errorBox !== null) {
      errorBox.textContent = message;
    }
    const input = form.elements.namedItem(field);
    if (input instanceof HTMLElement) {
      input.setAttribute("aria-invalid", "true");
    }
  }
}

export function clearFieldErrors(form: HTMLFormElement): void {
  form.querySelectorAll("[data-error-for]").forEach((box) => (box.textContent = ""));
  form.querySelectorAll("[aria-invalid]").forEach((input) => input.removeAttribute("aria-invalid"));
}

// ---------- URLs ----------

export function queryParam(name: string): string | null {
  return new URLSearchParams(window.location.search).get(name);
}

/** A numeric id from the URL, or null if it's missing or not a number. */
export function queryId(name: string): number | null {
  const value = queryParam(name);
  return value !== null && /^\d+$/.test(value) ? Number(value) : null;
}

// ---------- dates and statuses ----------

const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: "short", month: "short", day: "numeric" });
const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });

/** "Sat, Dec 5 · 8:00 AM – 10:00 AM" */
export function formatShiftTime(start: string, end: string): string {
  const startDate = new Date(start);
  return `${dayFormat.format(startDate)} · ${timeFormat.format(startDate)} – ${timeFormat.format(new Date(end))}`;
}

export function hasStarted(start: string): boolean {
  return new Date(start) <= new Date();
}

/** "2026-12-05T08:00:00" -> "2026-12-05T08:00", the format <input type="datetime-local"> uses. */
export function toDateTimeInput(value: string): string {
  return value.slice(0, 16);
}

const STATUS_LABELS: Record<SignupStatus, string> = {
  CONFIRMED: "Confirmed",
  WAITLISTED: "Waitlist",
  CANCELLED: "Cancelled",
  ATTENDED: "Attended",
  NO_SHOW: "No-show",
};

export function statusBadge(status: SignupStatus, waitlistPosition: number | null): HTMLSpanElement {
  let text = STATUS_LABELS[status];
  if (status === "WAITLISTED" && waitlistPosition !== null) {
    text = `Waitlist #${waitlistPosition}`;
  }
  return el("span", { text, className: `badge badge-${status.toLowerCase()}` });
}

export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : pluralForm}`;
}

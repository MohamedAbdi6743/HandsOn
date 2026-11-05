// Who is logged in, the navigation bar, and what to do with errors on a page.

import { api, ApiError, logout } from "./api.js";
import { byId, el, showMessage } from "./dom.js";
import type { Role, User } from "./types.js";

export async function getCurrentUser(): Promise<User | null> {
  try {
    return await api.get<User>("/api/auth/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

/**
 * Where to go after logging in. Only our own page names are allowed, so a crafted link like
 * login.html?next=https://evil.example can't send someone to another website ("open redirect").
 */
export function safeNextPage(next: string | null): string {
  if (next !== null && /^[a-z-]+\.html(\?[A-Za-z0-9=&_-]*)?$/.test(next)) {
    return next;
  }
  return "index.html";
}

function currentPageName(): string {
  return window.location.pathname.split("/").pop() || "index.html";
}

export function goToLogin(): void {
  const next = currentPageName() + window.location.search;
  window.location.assign(`login.html?next=${encodeURIComponent(next)}`);
}

/** A promise that never finishes: it stops a page's code while the browser loads another page. */
function waitForNavigation(): Promise<never> {
  return new Promise(() => {});
}

/**
 * Every protected page starts with this. Not logged in: go to the login page.
 * Wrong role (e.g. a volunteer on a coordinator page): go to the events page.
 * Otherwise draw the navigation bar and return the user.
 *
 * The server checks permissions on every request anyway; this only avoids showing
 * a page the user can't use.
 */
export async function requirePageUser(requiredRole?: Role): Promise<User> {
  let user: User | null;
  try {
    user = await getCurrentUser();
  } catch (error) {
    handleError(error);
    return waitForNavigation();
  }

  if (user === null) {
    goToLogin();
    return waitForNavigation();
  }
  if (requiredRole !== undefined && user.role !== requiredRole) {
    window.location.assign("index.html");
    return waitForNavigation();
  }
  renderNav(user);
  return user;
}

function renderNav(user: User): void {
  const links: Array<[string, string]> = [["index.html", "Events"]];
  if (user.role === "VOLUNTEER") {
    links.push(["my-shifts.html", "My shifts"]);
  } else {
    links.push(["manage-event.html", "New event"], ["reports.html", "Reports"]);
  }

  const list = el("ul", { className: "nav-links" });
  for (const [href, label] of links) {
    const link = el("a", { text: label, attributes: { href } });
    if (href === currentPageName()) {
      link.setAttribute("aria-current", "page");
    }
    list.append(el("li", {}, [link]));
  }

  const logoutButton = el("button", {
    text: "Log out",
    className: "button button-secondary button-small",
    attributes: { type: "button" },
  });
  logoutButton.addEventListener("click", async () => {
    try {
      await logout();
    } finally {
      window.location.assign("login.html");
    }
  });

  byId("nav").replaceChildren(list, el("span", { text: user.fullName, className: "nav-user" }), logoutButton);
}

/** Session expired: go to login. Anything else: show the message at the top of the page. */
export function handleError(error: unknown): void {
  if (error instanceof ApiError && error.status === 401) {
    goToLogin();
    return;
  }
  if (error instanceof ApiError) {
    showMessage(error.message, "error");
    return;
  }
  console.error(error);
  showMessage("Something went wrong. Please refresh the page and try again.", "error");
}

// my-shifts.html: a volunteer's upcoming and past shifts, plus their total hours.

import { api } from "./api.js";
import { handleError, requirePageUser } from "./auth.js";
import {
  button, byId, clearMessage, el, formatShiftTime, hasStarted, plural, showMessage, statusBadge, withLoading,
} from "./dom.js";
import type { MyHours, MySignup } from "./types.js";

async function main(): Promise<void> {
  await requirePageUser("VOLUNTEER");
  await loadPage();
}

async function loadPage(): Promise<void> {
  try {
    // Two independent requests, sent at the same time.
    const [signups, hours] = await Promise.all([
      api.get<MySignup[]>("/api/me/signups"),
      api.get<MyHours>("/api/me/hours"),
    ]);

    byId("hours-summary").textContent =
      `${hours.totalHours} hours volunteered across ${plural(hours.shiftsAttended, "shift")}.`;

    const now = new Date();
    const upcoming = signups.filter((signup) => new Date(signup.endTime) > now);
    const past = signups.filter((signup) => new Date(signup.endTime) <= now).reverse();

    renderList(byId("upcoming-list"), upcoming, "You haven't signed up for any upcoming shifts yet.");
    renderList(byId("past-list"), past, "No past shifts yet.");
  } catch (error) {
    handleError(error);
  }
}

function renderList(container: HTMLElement, signups: MySignup[], emptyText: string): void {
  if (signups.length === 0) {
    container.replaceChildren(el("p", { text: emptyText, className: "empty" }));
    return;
  }
  container.replaceChildren(...signups.map(renderSignup));
}

function renderSignup(signup: MySignup): HTMLElement {
  const actions = el("div", { className: "shift-actions" }, [statusBadge(signup.status, signup.waitlistPosition)]);

  const canCancel = (signup.status === "CONFIRMED" || signup.status === "WAITLISTED") && !hasStarted(signup.startTime);
  if (canCancel) {
    const cancelButton = button("Cancel", "secondary");
    cancelButton.addEventListener("click", () => cancelSignup(cancelButton, signup));
    actions.append(cancelButton);
  }

  return el("article", { className: "card shift-card" }, [
    el("div", { className: "shift-info" }, [
      el("h3", {}, [el("a", { text: signup.eventTitle, attributes: { href: `event.html?id=${signup.eventId}` } })]),
      el("p", { text: `${signup.shiftTitle} · ${formatShiftTime(signup.startTime, signup.endTime)}` }),
      el("p", { text: signup.location, className: "muted" }),
    ]),
    actions,
  ]);
}

async function cancelSignup(target: HTMLButtonElement, signup: MySignup): Promise<void> {
  if (!window.confirm(`Cancel your spot on "${signup.shiftTitle}"?`)) {
    return;
  }
  clearMessage();
  try {
    await withLoading(target, "Cancelling…", () => api.post(`/api/signups/${signup.signupId}/cancel`));
    showMessage(`Cancelled your spot on ${signup.shiftTitle}.`, "success");
    await loadPage();
  } catch (error) {
    handleError(error);
  }
}

void main();

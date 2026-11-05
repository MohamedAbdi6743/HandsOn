// event.html?id=3: one event with its shifts. Volunteers sign up and cancel here;
// coordinators get links to edit the event and see each shift's roster.

import { api } from "./api.js";
import { handleError, requirePageUser } from "./auth.js";
import {
  button, byId, clearMessage, el, formatShiftTime, hasStarted, queryId, showMessage, statusBadge, withLoading,
} from "./dom.js";
import type { EventDetails, Shift, Signup, User } from "./types.js";

const eventId = queryId("id");
let currentUser: User;

async function main(): Promise<void> {
  currentUser = await requirePageUser();
  if (eventId === null) {
    showMessage("This link is missing an event id.", "error");
    return;
  }
  await loadEvent(eventId);
}

async function loadEvent(id: number): Promise<void> {
  try {
    const event = await api.get<EventDetails>(`/api/events/${id}`);
    renderEvent(event);
  } catch (error) {
    handleError(error);
  }
}

function renderEvent(event: EventDetails): void {
  document.title = `${event.title} · HandsOn`;
  byId("event-title").textContent = event.title;
  byId("event-location").textContent = event.location;

  const description = byId("event-description");
  description.textContent = event.description ?? "";
  description.hidden = event.description === null;

  if (currentUser.role === "COORDINATOR") {
    const editLink = byId<HTMLAnchorElement>("edit-event-link");
    editLink.href = `manage-event.html?id=${event.id}`;
    editLink.hidden = false;
  }

  const list = byId("shift-list");
  if (event.shifts.length === 0) {
    list.replaceChildren(el("p", { text: "No shifts have been added yet.", className: "empty" }));
  } else {
    list.replaceChildren(...event.shifts.map(renderShift));
  }
}

function renderShift(shift: Shift): HTMLElement {
  let capacityText = `${shift.confirmedCount} of ${shift.capacity} spots filled`;
  if (shift.waitlistCount > 0) {
    capacityText += ` · ${shift.waitlistCount} on the waitlist`;
  }

  return el("article", { className: "card shift-card" }, [
    el("div", { className: "shift-info" }, [
      el("h3", { text: shift.title }),
      el("p", { text: formatShiftTime(shift.startTime, shift.endTime) }),
      el("p", { text: capacityText, className: "muted" }),
    ]),
    renderShiftActions(shift),
  ]);
}

function renderShiftActions(shift: Shift): HTMLElement {
  const actions = el("div", { className: "shift-actions" });

  if (currentUser.role === "COORDINATOR") {
    actions.append(el("a", {
      text: "Roster",
      className: "button button-secondary",
      attributes: { href: `roster.html?eventId=${shift.eventId}&shiftId=${shift.id}` },
    }));
    return actions;
  }

  const mySignup = shift.mySignup;
  if (mySignup !== null) {
    actions.append(statusBadge(mySignup.status, mySignup.waitlistPosition));
    if (!hasStarted(shift.startTime)) {
      const cancelButton = button("Cancel", "secondary");
      cancelButton.addEventListener("click", () => cancelSignup(cancelButton, shift, mySignup.id));
      actions.append(cancelButton);
    }
    return actions;
  }

  if (hasStarted(shift.startTime)) {
    actions.append(el("span", { text: "Already started", className: "muted" }));
    return actions;
  }

  const signUpButton = button(shift.spotsLeft > 0 ? "Sign up" : "Join waitlist");
  signUpButton.addEventListener("click", () => signUp(signUpButton, shift));
  actions.append(signUpButton);
  return actions;
}

async function signUp(target: HTMLButtonElement, shift: Shift): Promise<void> {
  clearMessage();
  try {
    const result = await withLoading(target, "Signing up…", () => api.post<Signup>(`/api/shifts/${shift.id}/signups`));
    if (result.status === "CONFIRMED") {
      showMessage(`You're confirmed for ${shift.title}.`, "success");
    } else {
      showMessage(`${shift.title} is full. You're #${result.waitlistPosition} on the waitlist.`, "success");
    }
    await loadEvent(shift.eventId);
  } catch (error) {
    handleError(error);
  }
}

async function cancelSignup(target: HTMLButtonElement, shift: Shift, signupId: number): Promise<void> {
  if (!window.confirm(`Cancel your spot on "${shift.title}"?`)) {
    return;
  }
  clearMessage();
  try {
    await withLoading(target, "Cancelling…", () => api.post<Signup>(`/api/signups/${signupId}/cancel`));
    showMessage(`You're no longer signed up for ${shift.title}.`, "success");
    await loadEvent(shift.eventId);
  } catch (error) {
    handleError(error);
  }
}

void main();

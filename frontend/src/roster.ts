// roster.html?eventId=3&shiftId=7: who is on a shift, and (once it starts) who showed up.

import { api } from "./api.js";
import { handleError, requirePageUser } from "./auth.js";
import {
  button, byId, clearMessage, el, formatShiftTime, hasStarted, plural, queryId, showMessage, statusBadge, withLoading,
} from "./dom.js";
import type { EventDetails, RosterEntry, Shift, Signup } from "./types.js";

const eventId = queryId("eventId");
const shiftId = queryId("shiftId");

async function main(): Promise<void> {
  await requirePageUser("COORDINATOR");
  if (eventId === null || shiftId === null) {
    showMessage("This link is missing the event or shift id.", "error");
    return;
  }
  await loadPage(eventId, shiftId);
}

async function loadPage(eventIdValue: number, shiftIdValue: number): Promise<void> {
  try {
    const [event, roster] = await Promise.all([
      api.get<EventDetails>(`/api/events/${eventIdValue}`),
      api.get<RosterEntry[]>(`/api/shifts/${shiftIdValue}/signups`),
    ]);
    const shift = event.shifts.find((candidate) => candidate.id === shiftIdValue);
    if (shift === undefined) {
      showMessage("This shift doesn't belong to that event.", "error");
      return;
    }
    renderHeader(event, shift);
    renderRoster(shift, roster);
  } catch (error) {
    handleError(error);
  }
}

function renderHeader(event: EventDetails, shift: Shift): void {
  document.title = `Roster: ${shift.title} · HandsOn`;
  byId("shift-title").textContent = `Roster: ${shift.title}`;
  const eventLink = byId<HTMLAnchorElement>("event-link");
  eventLink.textContent = `← ${event.title}`;
  eventLink.href = `event.html?id=${event.id}`;
  byId("shift-time").textContent = `${formatShiftTime(shift.startTime, shift.endTime)} · ${event.location}`;

  let summary = `${shift.confirmedCount} of ${shift.capacity} spots filled`;
  if (shift.waitlistCount > 0) {
    summary += ` · ${plural(shift.waitlistCount, "person", "people")} on the waitlist`;
  }
  byId("shift-summary").textContent = summary;
  byId("attendance-note").hidden = hasStarted(shift.startTime);
}

function renderRoster(shift: Shift, roster: RosterEntry[]): void {
  byId("empty-roster").hidden = roster.length > 0;
  byId("roster-table").hidden = roster.length === 0;

  const canRecordAttendance = hasStarted(shift.startTime);
  byId("roster-rows").replaceChildren(...roster.map((entry) => el("tr", {}, [
    el("td", { text: entry.fullName }),
    el("td", {}, [el("a", { text: entry.email, attributes: { href: `mailto:${entry.email}` } })]),
    el("td", {}, [statusBadge(entry.status, entry.waitlistPosition)]),
    el("td", {}, [canRecordAttendance ? attendanceButtons(shift, entry) : null]),
  ])));
}

function attendanceButtons(shift: Shift, entry: RosterEntry): HTMLElement | null {
  if (entry.status === "WAITLISTED") {
    return null;
  }
  const attendedButton = button("Attended", entry.status === "ATTENDED" ? "primary" : "secondary");
  const noShowButton = button("No-show", entry.status === "NO_SHOW" ? "primary" : "secondary");
  attendedButton.classList.add("button-small");
  noShowButton.classList.add("button-small");
  attendedButton.setAttribute("aria-pressed", String(entry.status === "ATTENDED"));
  noShowButton.setAttribute("aria-pressed", String(entry.status === "NO_SHOW"));

  attendedButton.addEventListener("click", () => recordAttendance(attendedButton, shift, entry, true));
  noShowButton.addEventListener("click", () => recordAttendance(noShowButton, shift, entry, false));
  return el("div", { className: "shift-actions" }, [attendedButton, noShowButton]);
}

async function recordAttendance(target: HTMLButtonElement, shift: Shift, entry: RosterEntry, attended: boolean): Promise<void> {
  clearMessage();
  try {
    await withLoading(target, "Saving…", () =>
      api.put<Signup>(`/api/signups/${entry.signupId}/attendance`, { attended }));
    showMessage(`${entry.fullName} marked as ${attended ? "attended" : "no-show"}.`, "success");
    await loadPage(shift.eventId, shift.id);
  } catch (error) {
    handleError(error);
  }
}

void main();

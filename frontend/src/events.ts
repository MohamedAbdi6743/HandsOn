// index.html: the list of upcoming events.

import { api } from "./api.js";
import { handleError, requirePageUser } from "./auth.js";
import { byId, el, formatShiftTime, plural } from "./dom.js";
import type { EventDetails } from "./types.js";

async function main(): Promise<void> {
  const user = await requirePageUser();
  const isCoordinator = user.role === "COORDINATOR";
  byId("new-event-link").hidden = !isCoordinator;

  const list = byId("event-list");
  list.replaceChildren(el("p", { text: "Loading events…", className: "muted" }));

  try {
    const events = await api.get<EventDetails[]>("/api/events");
    if (events.length === 0) {
      const emptyText = isCoordinator
        ? "No upcoming events. Create one to get started."
        : "No upcoming events right now. Check back soon.";
      list.replaceChildren(el("p", { text: emptyText, className: "empty" }));
      return;
    }
    list.replaceChildren(...events.map(renderEventCard));
  } catch (error) {
    list.replaceChildren();
    handleError(error);
  }
}

function renderEventCard(event: EventDetails): HTMLElement {
  // Only shifts that haven't ended matter here: their dates and their open spots.
  const now = new Date();
  const upcomingShifts = event.shifts.filter((shift) => new Date(shift.endTime) > now);

  let openSpots = 0;
  for (const shift of upcomingShifts) {
    openSpots += shift.spotsLeft;
  }

  const nextShift = upcomingShifts[0];
  const when = nextShift ? formatShiftTime(nextShift.startTime, nextShift.endTime) : "No shifts added yet";

  let summary: string | null = null;
  if (upcomingShifts.length > 0) {
    const spots = openSpots === 0 ? "all spots filled" : `${plural(openSpots, "spot")} open`;
    summary = `${plural(upcomingShifts.length, "upcoming shift")} · ${spots}`;
  }

  return el("article", { className: "card" }, [
    el("h2", { className: "card-title" }, [el("a", { text: event.title, attributes: { href: `event.html?id=${event.id}` } })]),
    el("p", { text: event.location, className: "muted" }),
    el("p", { text: when }),
    summary === null ? null : el("p", { text: summary, className: "muted" }),
  ]);
}

void main();

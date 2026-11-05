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
  let openSpots = 0;
  for (const shift of event.shifts) {
    openSpots += shift.spotsLeft;
  }

  const firstShift = event.shifts[0];
  const when = firstShift ? formatShiftTime(firstShift.startTime, firstShift.endTime) : "No shifts added yet";

  let summary: string | null = null;
  if (event.shifts.length > 0) {
    const spots = openSpots === 0 ? "all spots filled" : `${plural(openSpots, "spot")} open`;
    summary = `${plural(event.shifts.length, "shift")} · ${spots}`;
  }

  return el("article", { className: "card" }, [
    el("h2", { className: "card-title" }, [el("a", { text: event.title, attributes: { href: `event.html?id=${event.id}` } })]),
    el("p", { text: event.location, className: "muted" }),
    el("p", { text: when }),
    summary === null ? null : el("p", { text: summary, className: "muted" }),
  ]);
}

void main();

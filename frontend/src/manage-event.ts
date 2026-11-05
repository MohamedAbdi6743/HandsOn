// manage-event.html            -> create a new event
// manage-event.html?id=3       -> edit event 3 and add, edit or remove its shifts

import { api, ApiError } from "./api.js";
import { handleError, requirePageUser } from "./auth.js";
import {
  button, byId, clearFieldErrors, clearMessage, el, formValue, formatShiftTime, queryId, queryParam,
  showFieldErrors, showMessage, toDateTimeInput, withLoading,
} from "./dom.js";
import type { EventDetails, Shift } from "./types.js";

const eventId = queryId("id");

const eventForm = byId<HTMLFormElement>("event-form");
const shiftForm = byId<HTMLFormElement>("shift-form");
const saveShiftButton = byId<HTMLButtonElement>("save-shift-button");
const cancelEditButton = byId<HTMLButtonElement>("cancel-edit-button");

// The shift being edited, or null when the form adds a new shift.
let editingShiftId: number | null = null;

async function main(): Promise<void> {
  await requirePageUser("COORDINATOR");
  eventForm.addEventListener("submit", saveEvent);

  if (eventId === null) {
    byId("page-title").textContent = "New event";
    return;
  }

  byId("page-title").textContent = "Edit event";
  document.title = "Edit event · HandsOn";
  byId("shift-section").hidden = false;
  byId("danger-zone").hidden = false;
  byId<HTMLAnchorElement>("view-event-link").href = `event.html?id=${eventId}`;
  byId("view-event-link").hidden = false;

  shiftForm.addEventListener("submit", saveShift);
  cancelEditButton.addEventListener("click", resetShiftForm);
  byId("delete-event-button").addEventListener("click", deleteEvent);

  if (queryParam("created") === "1") {
    showMessage("Event created. Now add its shifts below.", "success");
  }
  await loadEvent(true);
}

async function loadEvent(fillEventForm: boolean): Promise<void> {
  try {
    const event = await api.get<EventDetails>(`/api/events/${eventId}`);
    if (fillEventForm) {
      setInput(eventForm, "title", event.title);
      setInput(eventForm, "description", event.description ?? "");
      setInput(eventForm, "location", event.location);
    }
    renderShifts(event.shifts);
  } catch (error) {
    handleError(error);
  }
}

// ---------- event ----------

async function saveEvent(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  clearMessage();
  clearFieldErrors(eventForm);

  const body = {
    title: formValue(eventForm, "title"),
    description: formValue(eventForm, "description"),
    location: formValue(eventForm, "location"),
  };
  const submitButton = byId<HTMLButtonElement>("save-event-button");

  try {
    if (eventId === null) {
      const created = await withLoading(submitButton, "Creating…", () => api.post<EventDetails>("/api/events", body));
      window.location.assign(`manage-event.html?id=${created.id}&created=1`);
    } else {
      await withLoading(submitButton, "Saving…", () => api.put<EventDetails>(`/api/events/${eventId}`, body));
      showMessage("Event details saved.", "success");
    }
  } catch (error) {
    showFormError(eventForm, error);
  }
}

async function deleteEvent(): Promise<void> {
  if (!window.confirm("Delete this event and all of its shifts? This can't be undone.")) {
    return;
  }
  clearMessage();
  try {
    await api.delete(`/api/events/${eventId}`);
    window.location.assign("index.html");
  } catch (error) {
    handleError(error);
  }
}

// ---------- shifts ----------

function renderShifts(shifts: Shift[]): void {
  const body = byId("shift-rows");
  byId("no-shifts").hidden = shifts.length > 0;
  byId("shift-table").hidden = shifts.length === 0;

  body.replaceChildren(...shifts.map((shift) => {
    const editButton = button("Edit", "secondary");
    editButton.classList.add("button-small");
    editButton.addEventListener("click", () => startEditingShift(shift));

    const deleteButton = button("Delete", "danger");
    deleteButton.classList.add("button-small");
    deleteButton.addEventListener("click", () => deleteShift(deleteButton, shift));

    const rosterLink = el("a", {
      text: "Roster",
      className: "button button-secondary button-small",
      attributes: { href: `roster.html?eventId=${shift.eventId}&shiftId=${shift.id}` },
    });

    return el("tr", {}, [
      el("td", { text: shift.title }),
      el("td", { text: formatShiftTime(shift.startTime, shift.endTime) }),
      el("td", { text: `${shift.confirmedCount} / ${shift.capacity}`, className: "number" }),
      el("td", { text: String(shift.waitlistCount), className: "number" }),
      el("td", {}, [el("div", { className: "shift-actions" }, [editButton, rosterLink, deleteButton])]),
    ]);
  }));
}

async function saveShift(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  clearMessage();
  clearFieldErrors(shiftForm);

  const capacityText = formValue(shiftForm, "capacity");
  const body = {
    title: formValue(shiftForm, "title"),
    startTime: formValue(shiftForm, "startTime"), // "2026-12-05T08:00", which the API accepts as-is
    endTime: formValue(shiftForm, "endTime"),
    capacity: capacityText === "" ? null : Number(capacityText),
  };

  try {
    if (editingShiftId === null) {
      await withLoading(saveShiftButton, "Adding…", () => api.post<Shift>(`/api/events/${eventId}/shifts`, body));
      showMessage(`Added "${body.title}".`, "success");
    } else {
      const shiftId = editingShiftId;
      await withLoading(saveShiftButton, "Saving…", () => api.put<Shift>(`/api/shifts/${shiftId}`, body));
      showMessage(`Saved "${body.title}".`, "success");
    }
    resetShiftForm();
    await loadEvent(false);
  } catch (error) {
    showFormError(shiftForm, error);
  }
}

function startEditingShift(shift: Shift): void {
  editingShiftId = shift.id;
  setInput(shiftForm, "title", shift.title);
  setInput(shiftForm, "startTime", toDateTimeInput(shift.startTime));
  setInput(shiftForm, "endTime", toDateTimeInput(shift.endTime));
  setInput(shiftForm, "capacity", String(shift.capacity));
  byId("shift-form-title").textContent = `Edit "${shift.title}"`;
  saveShiftButton.textContent = "Save shift";
  cancelEditButton.hidden = false;
  byId<HTMLInputElement>("shift-title").focus();
}

function resetShiftForm(): void {
  editingShiftId = null;
  shiftForm.reset();
  clearFieldErrors(shiftForm);
  byId("shift-form-title").textContent = "Add a shift";
  saveShiftButton.textContent = "Add shift";
  cancelEditButton.hidden = true;
}

async function deleteShift(target: HTMLButtonElement, shift: Shift): Promise<void> {
  if (!window.confirm(`Delete the shift "${shift.title}"?`)) {
    return;
  }
  clearMessage();
  try {
    await withLoading(target, "Deleting…", () => api.delete(`/api/shifts/${shift.id}`));
    showMessage(`Deleted "${shift.title}".`, "success");
    if (editingShiftId === shift.id) {
      resetShiftForm();
    }
    await loadEvent(false);
  } catch (error) {
    handleError(error);
  }
}

// ---------- helpers ----------

function setInput(form: HTMLFormElement, name: string, value: string): void {
  const input = form.elements.namedItem(name);
  if (input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement) {
    input.value = value;
  }
}

/** Validation errors go under the matching fields; everything else goes in the message area. */
function showFormError(form: HTMLFormElement, error: unknown): void {
  if (error instanceof ApiError && error.status === 400) {
    showFieldErrors(form, error.fieldErrors);
    showMessage(error.message, "error");
    return;
  }
  handleError(error);
}

void main();

// reports.html: hours per volunteer for a date range.

import { api } from "./api.js";
import { handleError, requirePageUser } from "./auth.js";
import { byId, clearMessage, el, formValue, withLoading } from "./dom.js";
import type { VolunteerHours } from "./types.js";

const form = byId<HTMLFormElement>("report-form");
const fromInput = byId<HTMLInputElement>("from");
const toInput = byId<HTMLInputElement>("to");

async function main(): Promise<void> {
  await requirePageUser("COORDINATOR");

  // Default range: January 1 of this year through today.
  const today = new Date();
  fromInput.value = `${today.getFullYear()}-01-01`;
  toInput.value = toDateInput(today);

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    void loadReport();
  });
  await loadReport();
}

async function loadReport(): Promise<void> {
  clearMessage();
  const params = new URLSearchParams({ from: formValue(form, "from"), to: formValue(form, "to") });
  const submitButton = byId<HTMLButtonElement>("report-button");

  try {
    const rows = await withLoading(submitButton, "Loading…", () =>
      api.get<VolunteerHours[]>(`/api/reports/volunteer-hours?${params}`));
    renderReport(rows);
  } catch (error) {
    handleError(error);
  }
}

function renderReport(rows: VolunteerHours[]): void {
  byId("empty-report").hidden = rows.length > 0;
  byId("report-table").hidden = rows.length === 0;

  let totalShifts = 0;
  let totalHours = 0;
  for (const row of rows) {
    totalShifts += row.shiftsAttended;
    totalHours += row.totalHours;
  }

  byId("report-rows").replaceChildren(...rows.map((row) => el("tr", {}, [
    el("td", { text: row.fullName }),
    el("td", { text: row.email }),
    el("td", { text: String(row.shiftsAttended), className: "number" }),
    el("td", { text: formatHours(row.totalHours), className: "number" }),
  ])));
  byId("total-shifts").textContent = String(totalShifts);
  byId("total-hours").textContent = formatHours(totalHours);
}

function formatHours(hours: number): string {
  return hours.toFixed(2).replace(/\.?0+$/, "");
}

/** A Date as "2026-11-05" in local time, the format <input type="date"> uses. */
function toDateInput(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

void main();

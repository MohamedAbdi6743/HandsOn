import { api, ApiError, login } from "./api.js";
import { byId, clearFieldErrors, clearMessage, formValue, showFieldErrors, showMessage, withLoading } from "./dom.js";
import type { User } from "./types.js";

const form = byId<HTMLFormElement>("register-form");
const submitButton = byId<HTMLButtonElement>("register-button");

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearMessage();
  clearFieldErrors(form);

  const fullName = formValue(form, "fullName");
  const email = formValue(form, "email");
  const password = String(new FormData(form).get("password") ?? "");

  try {
    await withLoading(submitButton, "Creating account…", async () => {
      await api.post<User>("/api/auth/register", { fullName, email, password });
      await login(email, password);
    });
    window.location.assign("index.html");
  } catch (error) {
    if (error instanceof ApiError) {
      showFieldErrors(form, error.fieldErrors);
      showMessage(error.message, "error");
    } else {
      showMessage("Something went wrong. Please try again.", "error");
    }
  }
});

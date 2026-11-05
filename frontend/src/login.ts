import { ApiError, login } from "./api.js";
import { getCurrentUser, safeNextPage } from "./auth.js";
import { byId, clearMessage, queryParam, showMessage, withLoading } from "./dom.js";

const nextPage = safeNextPage(queryParam("next"));

async function main(): Promise<void> {
  if (queryParam("registered") === "1") {
    showMessage("Account created. You can log in now.", "success");
  }

  // Already logged in? Skip the form.
  try {
    if ((await getCurrentUser()) !== null) {
      window.location.assign(nextPage);
      return;
    }
  } catch {
    // The server may be unreachable; show the form anyway.
  }

  const form = byId<HTMLFormElement>("login-form");
  const submitButton = byId<HTMLButtonElement>("login-button");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage();

    const data = new FormData(form);
    const email = String(data.get("email") ?? "").trim();
    const password = String(data.get("password") ?? ""); // never trim passwords

    try {
      await withLoading(submitButton, "Logging in…", () => login(email, password));
      window.location.assign(nextPage);
    } catch (error) {
      // Show every error here, including 401 ("Email or password is incorrect.").
      showMessage(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "error");
    }
  });
}

void main();

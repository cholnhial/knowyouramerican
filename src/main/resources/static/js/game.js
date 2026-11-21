// Know Your American — the only client script. Keep it small: HTMX does the rest.
// Responsibilities: (1) tell the server the player's *local* date, (2) localStorage game state (round 4).
(function () {
  "use strict";

  /** Browser-local date as yyyy-MM-dd ("today" is the player's local day). */
  function localDate() {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  function loadToday() {
    const game = document.getElementById("game");
    if (!game || game.dataset.preloaded === "true") return;
    // If the browser's clock is wildly off (e.g. set to next year) the server says 404:
    // fall back to the server's date once.
    game.addEventListener("htmx:responseError", function onError() {
      game.removeEventListener("htmx:responseError", onError);
      htmx.ajax("GET", `/play/${game.dataset.fallbackDate}`, { target: "#game", swap: "innerHTML" });
    });
    htmx.ajax("GET", `/play/${localDate()}`, { target: "#game", swap: "innerHTML" });
  }

  document.addEventListener("DOMContentLoaded", loadToday);

  window.KYA = { localDate };
})();

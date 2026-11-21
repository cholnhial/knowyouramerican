// Know Your American — the only client script. HTMX does the talking; this file only:
//   1. tells the server the player's *local* date ("today" is the player's day),
//   2. keeps answers in localStorage (no accounts; the browser is the source of truth),
//   3. shares results, and marks archive days the player has done.
//
// Storage: localStorage["kya:v1"] = { "2026-11-21": { a: [2,0,null,null,null], r: [1,0,null,null,null] } }
//   a = chosen option per question, r = 1 correct / 0 missed.
(function () {
  "use strict";

  const KEY = "kya:v1";
  const SIZE = 5;

  function load() {
    try {
      return JSON.parse(localStorage.getItem(KEY)) || {};
    } catch (e) {
      return {};
    }
  }

  function save(state) {
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch (e) {
      /* private mode / quota: play still works, progress just isn't kept */
    }
  }

  /** Browser-local date as yyyy-MM-dd. */
  function localDate() {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  /** The day being played: from the rendered game, else the page, else local today. */
  function gameDate() {
    const inner = document.querySelector("#game [data-day]");
    if (inner) return inner.dataset.day;
    const game = document.getElementById("game");
    return (game && game.dataset.date) || localDate();
  }

  function day(date) {
    const d = load()[date] || {};
    return { a: d.a || Array(SIZE).fill(null), r: d.r || Array(SIZE).fill(null) };
  }

  /** Answers for a day in the wire format the server reads: "2,0,-,-,-". */
  function answersFor(date) {
    return day(date).a.map((v) => (Number.isInteger(v) ? v : "-")).join(",");
  }

  // The server confirms each answer with an HX-Trigger event; store it (one shot: never overwrite).
  document.addEventListener("kya:answered", (e) => {
    const { date, pos, choice, correct } = e.detail || {};
    if (!date || !pos) return;
    const state = load();
    const d = day(date);
    if (!Number.isInteger(d.a[pos - 1])) {
      d.a[pos - 1] = choice;
      d.r[pos - 1] = correct ? 1 : 0;
    }
    state[date] = d;
    save(state);
  });

  // Share button on the summary card.
  document.addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-share]");
    if (!btn) return;
    const text = btn.dataset.share;
    const label = btn.querySelector("[data-share-label]") || btn;
    try {
      if (navigator.share && matchMedia("(pointer: coarse)").matches) {
        await navigator.share({ text });
        return;
      }
      await navigator.clipboard.writeText(text);
      label.textContent = "Copied! Paste it anywhere 📋";
    } catch (err) {
      window.prompt("Copy your result:", text);
    }
  });

  /** Colour archive calendar days by local progress. */
  function decorate(root) {
    const state = load();
    root.querySelectorAll("[data-kya-day]").forEach((cell) => {
      const d = state[cell.dataset.kyaDay];
      const badge = cell.querySelector("[data-kya-badge]");
      if (!d || !d.a) return;
      const done = d.a.filter(Number.isInteger).length;
      const score = (d.r || []).filter((x) => x === 1).length;
      if (done === SIZE) {
        cell.classList.add("bg-liberty", "text-white", "border-liberty");
        cell.classList.remove("bg-white/70", "text-ink");
        if (badge) badge.textContent = `${score}/5`;
      } else if (done > 0) {
        cell.classList.add("ring-2", "ring-torch");
        if (badge) badge.textContent = `${done}/5`;
      }
    });
  }

  document.addEventListener("DOMContentLoaded", () => decorate(document));
  document.addEventListener("htmx:afterSettle", (e) => decorate(e.target));

  // Scroll the fresh card into view on small screens after answering / moving on.
  document.addEventListener("htmx:afterSettle", (e) => {
    if (e.target.id !== "game" || window.innerWidth >= 640) return;
    const card = document.getElementById("card");
    if (card && e.detail.requestConfig && e.detail.requestConfig.verb !== "get") return;
    if (card) card.scrollIntoView({ behavior: "smooth", block: "start" });
  });

  window.KYA = { localDate, gameDate, answersFor };
})();

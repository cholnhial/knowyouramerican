# KnowYourAmerican

A daily American-idioms game. When you open the site you get **today's 5 idioms** to answer. There's also an **archive** for playing past days. It's meant to be stylish, fun, and quick: one short session a day.

Source of idioms: `docs/idioms.pdf` (*NTC's American Idioms Dictionary*, 3rd ed., McGraw-Hill 2000). It's 641 pages, two columns, about 8k entries.

> **Copyright note:** We store and show the book's definitions and examples verbatim (owner's decision). The book is © McGraw-Hill and licensed for "personal/noncommercial use". **Revisit this before any public or commercial launch.** The fallback is to keep the headwords and rewrite the definitions and examples in our own words.

---

## Product rules (decided, don't change without asking)

- **Daily set:** 5 questions per date. A 5-circle progress row ticks green when correct and marks red when missed, with a label like **"2/5 done"**.
- **One shot per idiom.** Each question is 4-option multiple choice with exactly one correct answer.
  - Right: green tick and a small celebration.
  - Wrong: the card shakes, the correct answer is revealed, the book's example sentence is shown, and the circle is marked missed.
- **Three rotating question types**, mixed within each day:
  - `MEANING`: "What does it mean?" Pick the right definition. The wrong options are definitions of *other* idioms.
  - `FINISH`: "Finish the idiom." One distinctive word is blanked; pick the missing word.
  - `FITS`: "Which idiom fits?" An example sentence with the idiom blanked; pick the idiom.
  - An idiom that can't support a type (no example sentence, say) falls back to `MEANING`.
- **"Today" is the player's local date**, read from the browser.
- **State lives in the browser** (localStorage, keyed by date). There are no accounts. Finished days show your answers again.
- **Archive:** backfilled from **2026-01-01**. You can browse by year and month on a calendar grid that shows each day's status (done/partial/new) from localStorage, and there's a 🎲 **Random day** button.
- **Curated pool:** drop cross-reference-only entries ("Go to …") and generic placeholder phrases. Each idiom gets a `fun_score`, so dull ones rarely appear. Idioms aren't repeated until the pool runs out; after that, the ones unused longest come back first.
- **Look:** a full-bleed random background photo of a US historical place or natural wonder, picked fresh on every page load. Content sits in a centered frosted-glass frame (about `max-w-xl`) with enough margin that the scenery stays visible. Mobile first. Fonts: **Fraunces** (display, idiom headlines) and **Inter** (body, buttons), self-hosted.

## Brand

### Logo — abstract Lady Liberty holding a book
- **Idea in one sentence:** Lady Liberty with a **book** in place of her tablet: freedom through knowing the language.
- **Style:** an abstract, geometric symbol built from a handful of flat shapes, not a drawn statue or clip art. The parts to draw from:
  - the crown's rays (3–5 spikes, set at exact 15°/30° angles);
  - a simple head and shoulder silhouette;
  - the raised torch, whose flame is the only gold element;
  - an **open book** held in the crook of the arm, replacing the tablet.
- **Colour:** works in one colour first (Liberty Teal), with a gold flame as the only accent. It must also hold up in pure black and reversed white.
- **Small sizes:** the full figure has to read at 32 px. A **simplified cut for 16 px and the favicon** shows just the crown rays over an open book.
- **Lockup:** the symbol plus the wordmark "KnowYourAmerican" in Fraunces, converted to outlines. The stacked version reads "Know Your / American".
- **Avoid:** realistic statue detail, US flags, stars-and-stripes clichés, gradients, drop shadows, and anything that looks like an existing Liberty-based logo (insurers, banks, the NYC tourism mark).
- **Process:** use the `logo-design` skill. It builds 3 greyscale concepts and pauses for the owner to pick one before making the kit (favicon, app icons, mono and reversed versions). Assets go in `src/main/resources/static/img/brand/`.

### Colour palette ("Americana, softened")
Warm and friendly, with a nod to red, white and blue that doesn't shout. Everything sits on a frosted cream card over the photo background. Tailwind v4 `@theme` tokens:

| Token | Hex | Role |
|---|---|---|
| `liberty` | `#2F8F83` | Primary: oxidized-copper teal from the statue. Logo, ticked circles, correct-answer fills, large accents |
| `liberty-deep` | `#1F6F66` | Button fills (with white text), links and teal text on cream |
| `torch` | `#F2B33D` | Accent: the torch flame. Progress highlights, celebration, "today" marker |
| `firework` | `#E4572E` | Wrong-answer state, the shake, missed circles. Used sparingly |
| `ink` | `#1E2A44` | Body text and headings (a soft navy, not black) |
| `slate` | `#5B6478` | Secondary text and captions |
| `paper` | `#FBF7EF` | Card surface, used at about 85–90% opacity with `backdrop-blur` |
| `mist` | `#E9EEF2` | Borders, unanswered circles, disabled options |

Rules:
- Correct and wrong must never rely on colour alone. Also use the ✓ / ✗ icon and motion (pop for correct, shake for wrong).
- Body text is `ink` on `paper`. Contrast on `paper`, measured:
  - pass AA: `ink` 13.4:1, `slate` 5.6:1, `liberty-deep` 5.6:1;
  - fail for small text: `liberty` 3.7:1, `firework` 3.5:1, and white on `liberty` 3.9:1.

  Use `liberty` and `firework` for fills, icons, borders and text 24px or larger, never for small text. Check AA for every new text/background pair.
- Keep `torch` and `firework` as accents (roughly ≤10% of any screen). Teal and cream carry the look.

## Stack

| Concern | Choice | Why |
|---|---|---|
| App | Spring Boot 4.x, Java 25, Maven wrapper | Owner's choice |
| Runtime | **GraalVM native image** for Linux | Small, fast-starting single binary |
| DB | Embedded **SQLite** (`xerial sqlite-jdbc`), file at `${KYA_DB_PATH:./data/kya.db}` | Zero-ops |
| Data access | Spring `JdbcClient` (**no JPA/Hibernate**) | Native-friendly, no SQLite dialect issues |
| Migrations | Flyway (`db/migration`) | Schema plus the seed load |
| Views | **Thymeleaf + HTMX** (vendored, no CDN) | Server-rendered fragments. **No SPA.** |
| CSS | **Tailwind v4 standalone CLI** (Maven downloads it) | No Node/npm in the project |
| Client JS | One small `static/js/game.js` | localStorage state and local date only |
| Scheduling | Spring `@Scheduled` | Generates daily puzzles |

## Architecture

Package root `com.knowyouramerican`:

- `idiom/`: the `Idiom` record, repository, and seed loading.
- `puzzle/`: `PuzzleGenerator`, `PuzzleScheduler`, repository.
- `web/`: controllers and view models.
- `background/`: the background image catalog (`backgrounds.json`).

**Schema** (Flyway: `V1__init.sql`, `V2__idiom_senses.sql`, V3 = `IdiomSeedMigration`)
- `idiom(id, headword, alt_forms, definition, notes, examples_json, senses_json, fun_score, source_page, used_on)`. `definition`, `notes`, and `examples_json` hold the **primary sense** (the first sense with example sentences), which questions are built from. `senses_json` keeps every sense.
- `puzzle_day(day_date PK, created_at)`
- `question(id, day_date, position 1..5, idiom_id, type MEANING|FINISH|FITS, prompt, options_json, correct_index)`

**Seed pipeline.** The PDF is never read at runtime. `docs/idioms.pdf` is **gitignored** (copyrighted ebook): a fresh clone needs a local copy only for `make extract` / `make test-extractor`. The app builds and runs from the committed `idioms.jsonl`.
1. `tools/idiom-extractor/` is a standalone Maven module (PDFBox 3) that the app does not depend on. It reads PDF pages 16–460; the Phrase-Finder Index starts on 462. Layout facts it relies on:
   - **Entry boundaries:** an entry head starts at x = 36/54/213/231 (column and page parity); continuation lines sit 9pt further in. Articles ("a labor of love") sit about 3pt in.
   - **Font meanings:**
     - `Formata-Medium` and `Formata-MediumItalic`: heads, alternate forms, sense numbers (`1.`).
     - `Formata-Condensed`: placeholders (someone, something).
     - `Formata-Regular`: cross-references.
     - `Minion-Regular`: definitions.
     - `Minion-RegularSC`: the "and" joining alternate forms.
     - `Minion-Italic`: example sentences.
     - `MathematicalPi` bullet and the boxed "T": each starts a new example.
   - **Text quirks:**
     - The "fl" ligature has a gap of about 1pt (real spaces are 2.3pt or more), so the two letters are joined.
     - A hyphen at the end of a line is dropped, unless that compound also appears hyphenated mid-line somewhere in the book.
     - PDFBox merges the running head with the next page's first line, so lines are split on page boundaries and on vertical jumps.
   - **Cleaning rules:**
     - "Go to …" cross-reference entries are dropped (about 1,100).
     - Sense-specific forms ("kick off … 2. and kick the bucket") are promoted to idioms of their own.
     - Capitalised parentheticals go to `notes`; lowercase ones such as "(something)" stay in the definition.
   - **`FunScorer`** gives each idiom a 0–100 score from non-literalness, imagery words, register (proverb, cliché, folksy, slang), shape, and placeholder count. Anything below 30 is dropped.
2. It writes the committed file `src/main/resources/db/seed/idioms.jsonl`: about 5,870 idioms, roughly 3 years of unique days.
3. `idiom/IdiomSeedMigration` is a Flyway `JavaMigration` registered as a Spring bean, so Boot passes it to Flyway without classpath scanning (native-safe). It runs as **V3** and loads the JSONL once. **Never re-run or edit it**, because question rows reference idiom ids. Ship seed corrections as a new migration that updates rows by headword.

**Daily generation ("the cron").** `PuzzleScheduler` runs at startup (`ApplicationReadyEvent`) and then hourly (`@Scheduled(cron = "0 0 * * * *", zone = "UTC")`). It calls `PuzzleService.ensureThrough(UTC today + 2)`, which fills every missing date from 2026-01-01, oldest first, committing one day per transaction.
- It is **idempotent**: the date is unique, and rerunning creates nothing new. The backfill of about 330 days takes roughly 1.3s.
- It is off when `kya.puzzles.scheduler-enabled=false`. The test helper `TempDatabase` sets that.

**Generator (`PuzzleGenerator`).** Pure and deterministic: the same pool, usage map, and date always give the same day, because all randomness comes from a date-seeded `java.util.Random`.
- **Picking:** weighted by `(fun_score + 10)²` from idioms never used before. Once fewer than 50 remain, it recycles the ones unused longest. Variety rules: no two idioms in a day share a content word, and at most one simile ("(as) X as Y").
- **Type plan:** each day is 2×MEANING, 1×FINISH, 1×FITS, plus one more FINISH or FITS, shuffled. An idiom that can't support its slot's type is set aside and another drawn. A failed FINISH tries FITS (and the reverse) before falling back to MEANING. If a definition echoes the idiom's own words, MEANING is avoided.
- **MEANING:** wrong options are other idioms' definitions with the same grammatical shape ("to …"), the same capitalisation (proverbs are sentences), and a similar length. They share no content words with the answer's idiom or meaning, so they can't also be right.
- **FINISH:** blanks the rarest content word (for similes, the comparison noun), never a contraction, and only when another clue word stays visible. Wrong options come from a context pool: nouns after determiners, verbs at the start of verb idioms. A substitution that forms a real idiom is rejected.
- **FITS:** finds the idiom in an example sentence with `idiomPattern`. That match handles inflections (bit/bite, patted), irregular first verbs, placeholder slots, and optional "(…)" words. Trailing slots stay outside the blank. The sentence needs at least 5 words, and the idiom's rarest word must not survive in it.
- After changing the generator, run `make db-reset` locally: existing days are stored and never regenerated.

**Request flow** (`web/PlayController`, `web/ArchiveController`)
- `GET /` and `GET /play/{date}` (normal navigation) render the **shell** (`index.html`). Its `#game` element loads itself with `hx-trigger="load"` from `/play/today` or `/play/{date}`.
- `#game` carries `hx-vals='js:{a: KYA.answersFor(KYA.gameDate()), today: KYA.localDate()}'`. Every HTMX request inside it inherits these values, so the circles, answer buttons, and "Next" all send the browser's **local date** and **saved answers**. Answers use the wire format `a=2,0,-,-,-`, parsed leniently by `web/Answers`.
- `GET /play/{date|today}` with `HX-Request` returns the `play :: game` fragment. `q=1..5` picks a card; the default is the first unanswered card, or the summary when all are done. `today` resolves to the player's date when it is playable, otherwise UTC today.
- `POST /play/{date}/q/{pos}` (`choice`, plus the inherited `a`) re-renders the game with that card answered.
  - It also sends `HX-Trigger: {"kya:answered":{date,pos,choice,correct}}`, and `game.js` stores that in localStorage.
  - **One shot:** if `a` already has an answer for that card, the new choice is ignored and no event is sent.
- **The correct index never reaches the page** for an unanswered card: `CardView.correctIndex = -1` and `reveal = null`.
- `GameViews` generates a missing day on demand. Dates outside 2026-01-01 … UTC now + 14h return 404, rendered by `error.html`.
- `GET /archive?year=&month=`: month calendar (`fragments/calendar.html`, Sunday first). The selects and arrows swap `#calendar` over HTMX (the controller checks `HX-Target: calendar`) and push the URL. Days run up to UTC today.
- `GET /archive/random` redirects to a random day between 2026-01-01 and UTC today.

**Browser state** (`static/js/game.js`, the only script; HTMX does the rest):
- Storage is `localStorage["kya:v1"] = {"2026-11-21": {a:[2,0,null,null,null], r:[1,0,null,null,null]}}`, where `a` is the chosen option and `r` is 1 for correct, 0 for missed.
- It colours archive cells (`[data-kya-day]`): teal with the score when finished, a gold ring with "n/5" when started.
- The summary's share button uses `navigator.share` on touch devices, otherwise the clipboard.

**Templates:**
- `index.html` (shell), `play.html` (fragments `game`, `card`, `summary`), `archive.html`, `fragments/layout.html` (`head`, `background`, `frame`, `header`, `progress`), `fragments/calendar.html`, `error.html`.
- Call record accessors with `()` (`g.day()`). Every type a template touches must be in `config/TemplateHints` (see Conventions).

## Backgrounds

There are 18 photos in `src/main/resources/static/img/bg/`, each 2560×1600 WebP at about 430 KB. They're CC0, CC BY, or CC BY-SA from Wikimedia Commons; every one has its credit and licence in `backgrounds.json`, and the page caption links to the source (CC BY / BY-SA require that).
- **Places:** Grand Canyon, Statue of Liberty, Golden Gate, Mount Rushmore, Yosemite, Grand Prismatic, Lincoln Memorial, Independence Hall, Monument Valley, Niagara Falls, Delicate Arch, Gateway Arch, Brooklyn Bridge, Mesa Verde, Hoover Dam, the Alamo, Great Smoky Mountains, Horseshoe Bend.
- **Regenerating:** `scripts/fetch-backgrounds.py` holds the list (slug, title, location, exact Commons file). It downloads each photo at 3840px, cover-crops it, encodes it with `cwebp -size 450000`, and rewrites `backgrounds.json`. Run it with `make backgrounds`; to add a place, add a line to the list and rerun.
- **Display:** one photo is picked at random per page load.

## Logo

Concepts are in `docs/brand/` (`logo-concepts.png` plus the greyscale SVGs `a/b/c-symbol.svg`).
- **In use (pending owner sign-off):**
  - Concept **A "Liberty Reads"** as the header mark (`static/img/brand/mark.svg`): crowned Liberty, torch raised, open book held in front. It's teal `#2F8F83`, with a torch-gold flame and cream pages.
  - Concept **B "Crowned Book"**, her crown resting on an open book, as the small-size cut (`favicon.svg`, `favicon-32.png`, `apple-touch-icon.png`). A turns to mush at 16px.
- **Still to do once the owner picks:** final refinement and optical corrections, a lockup with the wordmark in outlines, mono and reversed versions, and the full icon set (`logo-design` skill, phase 7).

## Local development

```bash
make dev          # Tailwind --watch + spring-boot:run (dev profile) → http://localhost:8080
make test         # ./mvnw verify
make extract      # regenerate idioms.jsonl from docs/idioms.pdf (only when the extractor changes)
make test-extractor  # the extractor's golden tests against the real PDF
make idioms       # 10 random idioms from the local DB (N=20 MIN=70 make idioms)
make db-shell     # sqlite3 on ./data/kya.db
make puzzle       # print today's stored questions with answers (DAY=2026-07-04 make puzzle)
make backgrounds  # re-download background photos + regenerate backgrounds.json
make db-reset     # delete ./data/kya.db; next start re-migrates, reseeds, backfills
make native       # ./mvnw -Pnative native:compile → target/knowyouramerican
make run-native   # run the native binary against ./data/kya.db
```

`./data/` holds the local DB and is gitignored. The `dev` profile turns off the Thymeleaf cache and enables devtools.

How changes show up during `make dev`:
- **Templates and static files** are served straight from `src/main/resources`. Just refresh the browser.
- **CSS:** Tailwind watches the templates and rebuilds `static/css/app.css` (gitignored) on its own.
- **Java:** devtools restarts the app once the classes are **recompiled**, either by an IDE build (Ctrl+F9 in IntelliJ) or by `./mvnw -q compile -Dtailwind.skip=true` in another terminal. Saving a file alone doesn't trigger it.

Requires JDK 25 (GraalVM for `make native`). The Tailwind CLI downloads itself into `.tools/` on first use.

## Conventions

- **No SPA, no npm, no frontend framework.** Any interactivity is an HTMX attribute plus a server fragment. Keep `game.js` small.
- **Stay native-safe.** Avoid reflection-heavy libraries. Templates and `static/**` are bundled by Spring AOT; anything else needs hints:
  - **Classpath files** read at runtime (e.g. `backgrounds.json`, `db/seed/idioms.jsonl`) need a `RuntimeHintsRegistrar` resource pattern.
  - **Every type a Thymeleaf template touches** (view-model records, `LocalDate`, `String` methods) must be listed in `config/TemplateHints`. A missing one only fails in the native binary, with HTTP 500 and `EL1008E` / `MissingReflectionRegistrationError`; JVM tests won't catch it.
  - Verify with `make native` plus a quick run. CI also smoke-tests the binary before deploying.
  - `native:compile` needs **GraalVM ≥ 25.0.2**, because 25.0.1 rejects the current reachability-metadata schema. Spring Boot 4's `native` profile doesn't bind native-image to `package`; always call `native:compile`.
- Use Java records for DTOs and view models, and constructor injection.
- SQL lives in repositories (`JdbcClient`). Schema changes go in new Flyway migrations. **Never edit an applied migration.**
- Tests: JUnit 5, a temp-file SQLite per test class, and MockMvc for the web layer. Every generator change must keep these invariants tested:
  - exactly 4 distinct options with exactly 1 correct;
  - no duplicate idioms within a day;
  - deterministic for a given date;
  - FINISH and FITS prompts never leak the answer.
- Keep the extractor's golden tests (`make test-extractor`: `above par`, `according to Hoyle`, `acid test`, page-boundary and ligature regressions) passing whenever you touch parsing. After `make extract`, look at the diff of `idioms.jsonl` before committing it.

## Roadmap

1. ✅ Skeleton: pom, Makefile, Tailwind, Flyway, empty pages, `make dev` and `make test` green.
2. ✅ Extractor plus golden tests, `idioms.jsonl`, and the seed migration.
3. ✅ Generator, scheduler, and their tests (invariants over 400 simulated days).
4. ✅ Play page: HTMX answer flow, localStorage, progress circles, summary and share card.
5. ✅ Archive: calendar, year/month picker, random day.
6. ✅ Backgrounds (18) and polish. 🟡 Logo: concepts done, with A and B in use; waiting for the owner's pick, then the final kit.
7. ✅ Native build, plus a browser-driven smoke test of the full flow against the binary.
8. ✅ Deploy workflow (see **Deployment**).

## Deployment

`.github/workflows/deploy.yml` runs on every push to `main` (and manually). Live site: https://kya.chol.dev.
- **build:**
  - Sets up GraalVM Community 25 (Oracle's "latest 25" download 404s).
  - Runs `./mvnw verify` and `native:compile`.
  - Boots the binary and requests `/`, `/play/…`, and the CSS (native smoke test).
  - Uploads the binary plus `deploy/`.
- **deploy** (GitHub environment **`Deployment`**; secrets `SSH_HOST`, `SSH_USER`, `SSH_PRIVATE_KEY`):
  1. Copies the release to `/opt/knowyouramerican/release/` and swaps the binary in atomically.
  2. Writes `.env` (`PORT=8090`, `SERVER_ADDRESS=127.0.0.1`, `KYA_DB_PATH=/opt/knowyouramerican/data/kya.db`).
  3. Installs `deploy/knowyouramerican.service`, which runs as the system user `kya` with a hardened unit.
  4. Restarts the service and waits for the app to answer.
- **nginx:** the `deploy/nginx/kya.chol.dev.conf` block is appended to `/etc/nginx/sites-available/default` **only if** no `server_name kya.chol.dev;` exists. A backup is kept as `default.bak.<timestamp>`. Later edits to the repo file are **not** re-applied; edit the server by hand.
- **TLS:** `certbot --nginx -d kya.chol.dev --redirect` runs only when `/etc/letsencrypt/live/kya.chol.dev` is missing. It uses the server's existing certbot account.
- **Data:** the SQLite DB in `/opt/knowyouramerican/data/` survives deploys. Startup backfills any missing puzzle days.
- **Ops:**
  - `journalctl -u knowyouramerican -f`
  - `systemctl status knowyouramerican`
  - `sqlite3 /opt/knowyouramerican/data/kya.db`

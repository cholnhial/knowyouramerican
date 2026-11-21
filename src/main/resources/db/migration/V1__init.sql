-- Core schema. Never edit after it has been applied anywhere; add a new migration instead.

CREATE TABLE idiom (
    id            INTEGER PRIMARY KEY,
    headword      TEXT    NOT NULL,
    alt_forms     TEXT,               -- JSON array of alternate forms ("and ..." variants)
    definition    TEXT    NOT NULL,
    notes         TEXT,               -- parenthetical comments from the book
    examples_json TEXT    NOT NULL DEFAULT '[]',
    fun_score     INTEGER NOT NULL DEFAULT 50,  -- 0..100; higher = more colorful
    used_on       TEXT                -- ISO date last used in a puzzle (NULL = never)
);
CREATE INDEX idx_idiom_used_on ON idiom (used_on);

CREATE TABLE puzzle_day (
    day_date   TEXT PRIMARY KEY,      -- ISO yyyy-MM-dd
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE TABLE question (
    id            INTEGER PRIMARY KEY,
    day_date      TEXT    NOT NULL REFERENCES puzzle_day (day_date) ON DELETE CASCADE,
    position      INTEGER NOT NULL CHECK (position BETWEEN 1 AND 5),
    idiom_id      INTEGER NOT NULL REFERENCES idiom (id),
    type          TEXT    NOT NULL CHECK (type IN ('MEANING', 'FINISH', 'FITS')),
    prompt        TEXT    NOT NULL,
    options_json  TEXT    NOT NULL,   -- JSON array of exactly 4 strings
    correct_index INTEGER NOT NULL CHECK (correct_index BETWEEN 0 AND 3),
    UNIQUE (day_date, position)
);

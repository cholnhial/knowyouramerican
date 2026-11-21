-- Keep every numbered sense from the dictionary. definition/notes/examples_json hold the
-- "primary" sense (first one with example sentences) that questions are built from.
ALTER TABLE idiom ADD COLUMN senses_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE idiom ADD COLUMN source_page INTEGER;
CREATE INDEX idx_idiom_fun_score ON idiom (fun_score);

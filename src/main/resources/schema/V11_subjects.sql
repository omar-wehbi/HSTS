-- V11 — Subjects (semester PDF: courses belong to a subject; coordinators are subject-scoped)
-- Also folded into seed_test_scenarios.sql (Courses.subject_id + Subjects DDL).
-- On an old live DB missing subject_id, run once:
--   ALTER TABLE Courses ADD COLUMN subject_id INT NULL;

CREATE TABLE IF NOT EXISTS Subjects (
    id              INT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    code            TINYINT      NOT NULL,
    coordinator_id  INT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subjects_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
--  HSTS — Assignment 3 (full system)
--  Question Bank schema (Data tier)  —  owner: Amjad (DB layer)
--  Run once against your local MySQL: mysql -u root -p < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS hsts_a3_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hsts_a3_db;

-- ------------------------------------------------------------
-- Courses: every question belongs to exactly one course.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS Courses (
    id    INT          NOT NULL AUTO_INCREMENT,
    name  VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Questions: a multiple-choice question with 4 answers, one of
-- which is correct, an optional image, a topic and a difficulty
-- (used later for automatic exam generation), tied to a course.
--
-- VERSIONING (assignment requirement: editing a question keeps
-- the previous version in the bank):
--   * base_id    = the "family" id shared by all versions of the
--                  same logical question (the first version's id).
--   * version    = 1, 2, 3 ... increments on each edit.
--   * is_current = TRUE for the latest version, FALSE for old ones.
-- So "edit" never overwrites: we mark the old row is_current=FALSE
-- and INSERT a new row (same base_id, version+1, is_current=TRUE).
-- The old version stays in the table, exactly as required.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS Questions (
    id             INT          NOT NULL AUTO_INCREMENT,
    course_id      INT          NOT NULL,

    question_text  TEXT         NOT NULL,
    answer_1       TEXT         NOT NULL,
    answer_2       TEXT         NOT NULL,
    answer_3       TEXT         NOT NULL,
    answer_4       TEXT         NOT NULL,
    correct_answer TINYINT      NOT NULL,            -- which answer is correct: 1..4
    image_path     VARCHAR(512) NULL,                -- optional illustration (file path/URL)

    topic          VARCHAR(255) NULL,                -- for auto exam generation by topic
    difficulty     ENUM('EASY','MEDIUM','HARD') NULL,-- for auto exam generation by difficulty

    -- versioning columns
    base_id        INT          NULL,                -- groups all versions of one question
    version        INT          NOT NULL DEFAULT 1,
    is_current     BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_question_course
        FOREIGN KEY (course_id) REFERENCES Courses(id),
    CONSTRAINT chk_correct_answer
        CHECK (correct_answer BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Helpful indexes for the common reads (view current bank, filter for exams).
CREATE INDEX idx_questions_current ON Questions (is_current);
CREATE INDEX idx_questions_course  ON Questions (course_id);
CREATE INDEX idx_questions_filter  ON Questions (course_id, topic, difficulty, is_current);

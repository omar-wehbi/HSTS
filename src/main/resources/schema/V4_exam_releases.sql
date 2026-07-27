-- ============================================================
-- V4 — Exam release from the drawer (Person 4, Scenario 5)
-- Approved exam version + schedule + unique 4-digit code.
-- ============================================================

USE hsts_a3_db;

CREATE TABLE IF NOT EXISTS ExamReleases (
    id              INT         NOT NULL AUTO_INCREMENT,
    exam_id         INT         NOT NULL,
    released_by     INT         NOT NULL,
    execution_code  CHAR(4)     NOT NULL,
    open_time       DATETIME    NOT NULL,
    close_time      DATETIME    NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_release_exam
        FOREIGN KEY (exam_id)
        REFERENCES Exams(id),

    CONSTRAINT fk_release_teacher
        FOREIGN KEY (released_by)
        REFERENCES Users(id),

    CONSTRAINT uq_release_execution_code
        UNIQUE (execution_code),

    CONSTRAINT chk_release_execution_code
        CHECK (execution_code REGEXP '^[0-9]{4}$'),

    CONSTRAINT chk_release_time_range
        CHECK (close_time > open_time),

    INDEX idx_release_exam (exam_id),
    INDEX idx_release_teacher (released_by),
    INDEX idx_release_window (open_time, close_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

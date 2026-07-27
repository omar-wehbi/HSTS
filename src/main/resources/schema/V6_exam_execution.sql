-- ============================================================
-- V6 — Exam execution and live time extension (Person 4, Scenarios 6–7)
-- Run after V4_exam_releases.sql.
-- ============================================================
USE hsts_a3_db;

CREATE TABLE IF NOT EXISTS ExamSessions (
    id                INT NOT NULL AUTO_INCREMENT,
    release_id        INT NOT NULL,
    exam_id           INT NOT NULL,
    student_id        INT NOT NULL,
    started_at        DATETIME NOT NULL,
    deadline          DATETIME NOT NULL,
    submitted_at      DATETIME NULL,
    status            ENUM('IN_PROGRESS','SUBMITTED','TIMED_OUT') NOT NULL DEFAULT 'IN_PROGRESS',
    extension_minutes INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_session_release FOREIGN KEY (release_id) REFERENCES ExamReleases(id),
    CONSTRAINT fk_session_exam FOREIGN KEY (exam_id) REFERENCES Exams(id),
    CONSTRAINT fk_session_student FOREIGN KEY (student_id) REFERENCES Users(id),
    CONSTRAINT uq_session_attempt UNIQUE (release_id, student_id),
    CONSTRAINT chk_session_deadline CHECK (deadline > started_at),
    CONSTRAINT chk_session_extension CHECK (extension_minutes >= 0),
    INDEX idx_session_student_status (student_id, status),
    INDEX idx_session_release_status (release_id, status),
    INDEX idx_session_deadline (deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS StudentAnswers (
    id              INT NOT NULL AUTO_INCREMENT,
    session_id      INT NOT NULL,
    question_id     INT NOT NULL,
    selected_answer TINYINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_answer_session FOREIGN KEY (session_id) REFERENCES ExamSessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES Questions(id),
    CONSTRAINT uq_answer_question UNIQUE (session_id, question_id),
    CONSTRAINT chk_selected_answer CHECK (selected_answer BETWEEN 1 AND 4),
    INDEX idx_answer_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

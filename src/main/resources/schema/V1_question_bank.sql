-- ============================================================
--  V1 — Question bank (owner: Person 2)
--  Courses + versioned multiple-choice questions.
--  Idempotent: CREATE TABLE IF NOT EXISTS only, no data.
-- ============================================================
USE hsts_a3_db;

CREATE TABLE IF NOT EXISTS Courses (
    id    INT          NOT NULL AUTO_INCREMENT,
    name  VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS Questions (
    id             INT          NOT NULL AUTO_INCREMENT,
    course_id      INT          NOT NULL,
    question_text  TEXT         NOT NULL,
    answer_1       TEXT         NOT NULL,
    answer_2       TEXT         NOT NULL,
    answer_3       TEXT         NOT NULL,
    answer_4       TEXT         NOT NULL,
    correct_answer TINYINT      NOT NULL,            -- which answer is correct: 1..4
    image_path     VARCHAR(512) NULL,                -- optional illustration
    topic          VARCHAR(255) NULL,                -- for auto exam generation
    difficulty     ENUM('EASY','MEDIUM','HARD') NULL,
    base_id        INT          NULL,                -- versioning: family id
    version        INT          NOT NULL DEFAULT 1,
    is_current     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_course FOREIGN KEY (course_id) REFERENCES Courses(id),
    CONSTRAINT chk_correct_answer CHECK (correct_answer BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

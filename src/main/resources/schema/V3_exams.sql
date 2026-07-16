-- ============================================================
--  V3 — Exam building and coordinator approval (Person 3)
--  Versioned exams + exam questions + approval workflow.
--  Idempotent: CREATE TABLE IF NOT EXISTS only, no data.
-- ============================================================

USE hsts_a3_db;

CREATE TABLE IF NOT EXISTS Exams (
                                     id                   INT          NOT NULL AUTO_INCREMENT,
                                     base_id              INT          NULL,
                                     version              INT          NOT NULL DEFAULT 1,
                                     is_current           BOOLEAN      NOT NULL DEFAULT TRUE,

                                     course_id            INT          NOT NULL,
                                     teacher_id           INT          NOT NULL,

                                     title                VARCHAR(255) NOT NULL,
    duration_minutes     INT          NOT NULL,
    student_instructions TEXT         NULL,
    teacher_notes        TEXT         NULL,

    status ENUM(
                   'DRAFT',
                   'PENDING_APPROVAL',
                   'APPROVED',
                   'REJECTED'
               ) NOT NULL DEFAULT 'DRAFT',

    rejection_reason     TEXT         NULL,
    coordinator_id       INT          NULL,

    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_exam_course
    FOREIGN KEY (course_id)
    REFERENCES Courses(id),

    CONSTRAINT fk_exam_teacher
    FOREIGN KEY (teacher_id)
    REFERENCES Users(id),

    CONSTRAINT fk_exam_coordinator
    FOREIGN KEY (coordinator_id)
    REFERENCES Users(id),

    CONSTRAINT chk_exam_duration
    CHECK (duration_minutes > 0),

    CONSTRAINT chk_exam_version
    CHECK (version > 0),

    INDEX idx_exam_base_id (base_id),
    INDEX idx_exam_teacher (teacher_id),
    INDEX idx_exam_course (course_id),
    INDEX idx_exam_status (status),
    INDEX idx_exam_current (is_current)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS ExamQuestions (
                                             id           INT NOT NULL AUTO_INCREMENT,
                                             exam_id      INT NOT NULL,
                                             question_id  INT NOT NULL,
                                             points       INT NOT NULL,
                                             position     INT NOT NULL,

                                             PRIMARY KEY (id),

    CONSTRAINT fk_exam_question_exam
    FOREIGN KEY (exam_id)
    REFERENCES Exams(id)
    ON DELETE CASCADE,

    CONSTRAINT fk_exam_question_question
    FOREIGN KEY (question_id)
    REFERENCES Questions(id),

    CONSTRAINT chk_exam_question_points
    CHECK (points > 0),

    CONSTRAINT chk_exam_question_position
    CHECK (position > 0),

    CONSTRAINT uq_exam_question
    UNIQUE (exam_id, question_id),

    CONSTRAINT uq_exam_position
    UNIQUE (exam_id, position),

    INDEX idx_exam_questions_exam (exam_id),
    INDEX idx_exam_questions_question (question_id)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;
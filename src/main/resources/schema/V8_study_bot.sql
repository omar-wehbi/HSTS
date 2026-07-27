-- V8 — Study bot (Scenarios 13–14). Run after V7_grading.sql.
USE hsts_a3_db;

CREATE TABLE IF NOT EXISTS CourseTeachers (
    course_id INT NOT NULL, teacher_id INT NOT NULL,
    PRIMARY KEY(course_id,teacher_id),
    CONSTRAINT fk_course_teacher_course FOREIGN KEY(course_id) REFERENCES Courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_teacher_user FOREIGN KEY(teacher_id) REFERENCES Users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CourseTeachers is authoritative assignment data supplied by the external user-management system.
-- Bootstrap existing exam authors so the current prototype data remains usable.
INSERT IGNORE INTO CourseTeachers(course_id,teacher_id)
SELECT DISTINCT course_id,teacher_id FROM Exams;
-- Import any additional real teacher-course assignments explicitly, including teachers
-- who have not authored an exam. For local testing, for example:
-- INSERT IGNORE INTO CourseTeachers(course_id,teacher_id) VALUES (1,2);

CREATE TABLE IF NOT EXISTS StudyBots (
    course_id INT NOT NULL, name VARCHAR(120) NOT NULL, created_by INT NOT NULL,
    available BOOLEAN NOT NULL DEFAULT FALSE, include_question_bank BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(course_id),
    CONSTRAINT fk_bot_course FOREIGN KEY(course_id) REFERENCES Courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_creator FOREIGN KEY(created_by) REFERENCES Users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS StudyBotSources (
    id INT NOT NULL AUTO_INCREMENT, course_id INT NOT NULL, title VARCHAR(120) NOT NULL,
    content MEDIUMTEXT NOT NULL, source_type ENUM('TEXT','PDF','WORD') NOT NULL DEFAULT 'TEXT',
    original_filename VARCHAR(255) NULL, created_by INT NOT NULL, updated_by INT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(id),
    CONSTRAINT fk_bot_source_course FOREIGN KEY(course_id) REFERENCES StudyBots(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_source_creator FOREIGN KEY(created_by) REFERENCES Users(id),
    CONSTRAINT fk_bot_source_editor FOREIGN KEY(updated_by) REFERENCES Users(id),
    INDEX idx_bot_sources_course(course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS StudyBotHistory (
    id INT NOT NULL AUTO_INCREMENT, course_id INT NOT NULL, student_id INT NOT NULL,
    question TEXT NOT NULL, answer MEDIUMTEXT NOT NULL,
    status ENUM('ANSWERED','NO_ANSWER') NOT NULL DEFAULT 'ANSWERED',
    asked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(id),
    CONSTRAINT fk_bot_history_course FOREIGN KEY(course_id) REFERENCES StudyBots(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_history_student FOREIGN KEY(student_id) REFERENCES Users(id) ON DELETE CASCADE,
    INDEX idx_bot_history_student(student_id,asked_at), INDEX idx_bot_history_course(course_id,asked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

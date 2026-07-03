-- ============================================================
--  V2 — Authentication (owner: Person 1)
--  Users, roles and course enrollment.
--  Idempotent: CREATE TABLE IF NOT EXISTS only, no data.
-- ============================================================
USE hsts_a3_db;

CREATE TABLE IF NOT EXISTS Users (
    id           INT          NOT NULL AUTO_INCREMENT,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(255) NOT NULL,   -- SHA-256 hash (hex), never plaintext
    role         ENUM('STUDENT','TEACHER','COORDINATOR','PRINCIPAL') NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    id_number    VARCHAR(20)  NULL,       -- national ID (ת"ז), for students
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS Enrollments (
    user_id   INT NOT NULL,
    course_id INT NOT NULL,
    PRIMARY KEY (user_id, course_id),
    CONSTRAINT fk_enroll_user   FOREIGN KEY (user_id)   REFERENCES Users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_enroll_course FOREIGN KEY (course_id) REFERENCES Courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

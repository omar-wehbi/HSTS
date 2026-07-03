-- ============================================================
--  HSTS — Authentication schema (Person 1)
--  Users, roles and course enrollment. Run AFTER schema.sql +
--  seed.sql (needs the Courses table for the enrollment FK).
--    mysql -u root -p < auth.sql
-- ============================================================
USE hsts_a3_db;

-- ------------------------------------------------------------
-- Users: one account per person, with a role that decides which
-- menu/features they see (scenario 1). id_number (ת"ז) is used by
-- students when taking an exam (scenario 6).
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- Enrollments: which students belong to which courses. Used to
-- check a student may use a course's study bot (scenario 14).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS Enrollments (
    user_id   INT NOT NULL,
    course_id INT NOT NULL,
    PRIMARY KEY (user_id, course_id),
    CONSTRAINT fk_enroll_user   FOREIGN KEY (user_id)   REFERENCES Users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_enroll_course FOREIGN KEY (course_id) REFERENCES Courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---- Clean re-seed (safe to run repeatedly) ----
DELETE FROM Enrollments;
DELETE FROM Users;
ALTER TABLE Users AUTO_INCREMENT = 1;

-- ---- Seed users: one per role (password 1234, stored as SHA-256 hash) ----
INSERT INTO Users (username, password, role, display_name, id_number) VALUES
    ('teacher',   SHA2('1234', 256), 'TEACHER',     'Dana Cohen (Teacher)',     NULL),
    ('coord',     SHA2('1234', 256), 'COORDINATOR', 'Yossi Levi (Coordinator)', NULL),
    ('principal', SHA2('1234', 256), 'PRINCIPAL',   'Rita Bar (Principal)',     NULL),
    ('maya',      SHA2('1234', 256), 'STUDENT',     'Maya Student',             '207570227'),
    ('noa',       SHA2('1234', 256), 'STUDENT',     'Noa Student',              '315497081');

-- ---- Enroll the two students in courses 1 and 2 (from seed.sql) ----
INSERT INTO Enrollments (user_id, course_id)
SELECT u.id, c.id
FROM Users u JOIN Courses c
WHERE u.username IN ('maya', 'noa') AND c.id IN (1, 2);

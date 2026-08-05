-- ============================================================
--  HSTS — seed_test_scenarios.sql  (Person 1)
--
--  ONE command takes any machine from empty MySQL to a working,
--  deterministic demo/test database:
--
--      mysql -u root -p < src/main/resources/seed_test_scenarios.sql
--
--  Safe to re-run at any time (wipes and re-creates the data).
--
--  Cast is modelled on Assignment 1 acceptance fixtures, mapped onto
--  the CS courses this codebase uses (Algorithms / Databases / Networks
--  instead of Math / Biology / Physics). Password for EVERY user: 1234
--
--  Stable logins required by automated tests (do not rename):
--    teacher / coord / principal / maya / noa
--
--  Full login roster:
--    TEACHERS
--      teacher   Dana Avni          Algorithms, Databases
--      neta      Neta Berkovich     Algorithms          (co-teacher)
--      ronit     Ronit Segev        Databases           (co-teacher)
--    COORDINATOR / PRINCIPAL
--      coord     Yael Golan
--      principal Merav Solomon
--    STUDENTS (id_number = national ID for exam start)
--      maya      Maya Levi      207570227   Algorithms, Databases
--      noa       Noa Barak      315497081   Databases, Networks
--      shira     Shira Cohen    312456789   Algorithms, Networks
--      tamar     Tamar Rosen    311887766   Algorithms, Databases
--      avigail   Avigail Katz   309112233   Algorithms
--
--  Scenario coverage (required features 1-14):
--    1  Login ................. full A1-scale cast (roles + enrollments)
--    2  Question bank ......... questions across 3 courses, incl.
--                               one EDITED question proving versioning
--                               (v1 kept, v2 current)
--    3  Build exams ........... questions have topic + difficulty;
--                               Exams + ExamQuestions tables (V3) included;
--                               sample DRAFT + PENDING_APPROVAL exams
--    4  Approve exam ........... one PENDING_APPROVAL exam for coord
--    5  Release exam ........... ExamReleases (+ snapshot tables)
--    6  Take exam ............. students have id_number; ExamSessions
--    7–10 Grading / results .... Grades, execution statistics
--    11–12 Principal ........... read-only / report DAOs use Grades
--    13–14 Study bot ........... CourseTeachers, StudyBots, sources,
--                               history; Enrollments for access checks
-- ============================================================

CREATE DATABASE IF NOT EXISTS hsts_a3_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hsts_a3_db;

-- ============================================================
-- TABLES (created only if missing — idempotent)
-- ============================================================

CREATE TABLE IF NOT EXISTS Courses (
    id    INT          NOT NULL AUTO_INCREMENT,
    name  VARCHAR(255) NOT NULL,
    subject_id INT     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS Subjects (
    id              INT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    code            TINYINT      NOT NULL,
    coordinator_id  INT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subjects_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS Questions (
    id             INT          NOT NULL AUTO_INCREMENT,
    course_id      INT          NOT NULL,
    question_text  TEXT         NOT NULL,
    answer_1       TEXT         NOT NULL,
    answer_2       TEXT         NOT NULL,
    answer_3       TEXT         NOT NULL,
    answer_4       TEXT         NOT NULL,
    correct_answer TINYINT      NOT NULL,
    image_path     VARCHAR(512) NULL,
    image_data     LONGBLOB     NULL,
    topic          VARCHAR(255) NULL,
    difficulty     ENUM('EASY','MEDIUM','HARD') NULL,
    base_id        INT          NULL,
    version        INT          NOT NULL DEFAULT 1,
    is_current     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_course FOREIGN KEY (course_id) REFERENCES Courses(id),
    CONSTRAINT chk_correct_answer CHECK (correct_answer BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS Users (
    id           INT          NOT NULL AUTO_INCREMENT,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(255) NOT NULL,
    role         ENUM('STUDENT','TEACHER','COORDINATOR','PRINCIPAL') NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    id_number    VARCHAR(20)  NULL,
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

-- V3 — Exam drawer (Person 3 schema, folded into one-command seed for Person 5 UI)
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
    CONSTRAINT fk_exam_course FOREIGN KEY (course_id) REFERENCES Courses(id),
    CONSTRAINT fk_exam_teacher FOREIGN KEY (teacher_id) REFERENCES Users(id),
    CONSTRAINT fk_exam_coordinator FOREIGN KEY (coordinator_id) REFERENCES Users(id),
    CONSTRAINT chk_exam_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_exam_version CHECK (version > 0),
    INDEX idx_exam_base_id (base_id),
    INDEX idx_exam_teacher (teacher_id),
    INDEX idx_exam_course (course_id),
    INDEX idx_exam_status (status),
    INDEX idx_exam_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ExamQuestions (
    id           INT NOT NULL AUTO_INCREMENT,
    exam_id      INT NOT NULL,
    question_id  INT NOT NULL,
    points       INT NOT NULL,
    position     INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_question_exam
        FOREIGN KEY (exam_id) REFERENCES Exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_question_question
        FOREIGN KEY (question_id) REFERENCES Questions(id),
    CONSTRAINT chk_exam_question_points CHECK (points > 0),
    CONSTRAINT chk_exam_question_position CHECK (position > 0),
    CONSTRAINT uq_exam_question UNIQUE (exam_id, question_id),
    CONSTRAINT uq_exam_position UNIQUE (exam_id, position),
    INDEX idx_exam_questions_exam (exam_id),
    INDEX idx_exam_questions_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V4/V10 — Exam releases (alphanumeric 4-char codes; windowed reuse)
CREATE TABLE IF NOT EXISTS ExamReleases (
    id              INT         NOT NULL AUTO_INCREMENT,
    exam_id         INT         NOT NULL,
    released_by     INT         NOT NULL,
    execution_code  CHAR(4)     NOT NULL,
    open_time       DATETIME    NOT NULL,
    close_time      DATETIME    NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_release_exam FOREIGN KEY (exam_id) REFERENCES Exams(id),
    CONSTRAINT fk_release_teacher FOREIGN KEY (released_by) REFERENCES Users(id),
    CONSTRAINT chk_release_execution_code
        CHECK (execution_code REGEXP '^[A-Za-z0-9]{4}$'),
    CONSTRAINT chk_release_time_range CHECK (close_time > open_time),
    INDEX idx_release_exam (exam_id),
    INDEX idx_release_teacher (released_by),
    INDEX idx_release_window (open_time, close_time),
    INDEX idx_release_code_window (execution_code, open_time, close_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V6/V10 — Live exam sessions + answers
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
    actual_duration_minutes INT NULL,
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

-- V7/V10 — Grades
CREATE TABLE IF NOT EXISTS Grades (
    id INT NOT NULL AUTO_INCREMENT,
    session_id INT NOT NULL,
    exam_id INT NOT NULL,
    student_id INT NOT NULL,
    auto_score INT NOT NULL,
    final_score INT NULL,
    status ENUM('AUTO_GRADED','APPROVED','OVERRIDDEN') NOT NULL,
    auto_graded_at DATETIME NOT NULL,
    approved_by INT NULL,
    approved_at DATETIME NULL,
    override_justification VARCHAR(1000) NULL,
    teacher_comment VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_grades_session (session_id),
    KEY idx_grades_exam (exam_id),
    KEY idx_grades_student (student_id),
    CONSTRAINT fk_grades_session FOREIGN KEY (session_id) REFERENCES ExamSessions(id),
    CONSTRAINT fk_grades_exam FOREIGN KEY (exam_id) REFERENCES Exams(id),
    CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES Users(id),
    CONSTRAINT fk_grades_approver FOREIGN KEY (approved_by) REFERENCES Users(id),
    CONSTRAINT chk_grades_auto_score CHECK (auto_score BETWEEN 0 AND 100),
    CONSTRAINT chk_grades_final_score CHECK (final_score IS NULL OR final_score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V10 — Immutable released-question snapshots + per-release stats
CREATE TABLE IF NOT EXISTS ReleasedExamQuestions (
    id INT NOT NULL AUTO_INCREMENT,
    release_id INT NOT NULL,
    question_id INT NOT NULL,
    points INT NOT NULL,
    position INT NOT NULL,
    question_text TEXT NOT NULL,
    answer_1 TEXT NOT NULL,
    answer_2 TEXT NOT NULL,
    answer_3 TEXT NOT NULL,
    answer_4 TEXT NOT NULL,
    correct_answer TINYINT NOT NULL,
    image_path VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_release_snapshot_position (release_id, position),
    CONSTRAINT fk_snapshot_release FOREIGN KEY (release_id) REFERENCES ExamReleases(id) ON DELETE CASCADE,
    CONSTRAINT chk_snapshot_answer CHECK (correct_answer BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ExamExecutionStatistics (
    release_id INT NOT NULL,
    graded_count INT NOT NULL DEFAULT 0,
    mean_score DECIMAL(7,3) NOT NULL DEFAULT 0,
    median_score DECIMAL(7,3) NOT NULL DEFAULT 0,
    d0 INT NOT NULL DEFAULT 0, d1 INT NOT NULL DEFAULT 0, d2 INT NOT NULL DEFAULT 0,
    d3 INT NOT NULL DEFAULT 0, d4 INT NOT NULL DEFAULT 0, d5 INT NOT NULL DEFAULT 0,
    d6 INT NOT NULL DEFAULT 0, d7 INT NOT NULL DEFAULT 0, d8 INT NOT NULL DEFAULT 0,
    d9 INT NOT NULL DEFAULT 0,
    calculated_at DATETIME NOT NULL,
    PRIMARY KEY (release_id),
    CONSTRAINT fk_execution_stats_release FOREIGN KEY (release_id) REFERENCES ExamReleases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V8 — Study bot
CREATE TABLE IF NOT EXISTS CourseTeachers (
    course_id INT NOT NULL,
    teacher_id INT NOT NULL,
    PRIMARY KEY (course_id, teacher_id),
    CONSTRAINT fk_course_teacher_course FOREIGN KEY (course_id) REFERENCES Courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_teacher_user FOREIGN KEY (teacher_id) REFERENCES Users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS StudyBots (
    course_id INT NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_by INT NOT NULL,
    available BOOLEAN NOT NULL DEFAULT FALSE,
    include_question_bank BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (course_id),
    CONSTRAINT fk_bot_course FOREIGN KEY (course_id) REFERENCES Courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_creator FOREIGN KEY (created_by) REFERENCES Users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS StudyBotSources (
    id INT NOT NULL AUTO_INCREMENT,
    course_id INT NOT NULL,
    title VARCHAR(120) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    source_type ENUM('TEXT','PDF','WORD') NOT NULL DEFAULT 'TEXT',
    original_filename VARCHAR(255) NULL,
    created_by INT NOT NULL,
    updated_by INT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_bot_source_course FOREIGN KEY (course_id) REFERENCES StudyBots(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_source_creator FOREIGN KEY (created_by) REFERENCES Users(id),
    CONSTRAINT fk_bot_source_editor FOREIGN KEY (updated_by) REFERENCES Users(id),
    INDEX idx_bot_sources_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS StudyBotHistory (
    id INT NOT NULL AUTO_INCREMENT,
    course_id INT NOT NULL,
    student_id INT NOT NULL,
    question TEXT NOT NULL,
    answer MEDIUMTEXT NOT NULL,
    status ENUM('ANSWERED','NO_ANSWER') NOT NULL DEFAULT 'ANSWERED',
    asked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_bot_history_course FOREIGN KEY (course_id) REFERENCES StudyBots(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_bot_history_student FOREIGN KEY (student_id) REFERENCES Users(id) ON DELETE CASCADE,
    INDEX idx_bot_history_student (student_id, asked_at),
    INDEX idx_bot_history_course (course_id, asked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- DATA (deterministic wipe + re-seed)
-- ============================================================

DELETE FROM StudyBotHistory;
DELETE FROM StudyBotSources;
DELETE FROM StudyBots;
DELETE FROM CourseTeachers;
DELETE FROM ExamExecutionStatistics;
DELETE FROM ReleasedExamQuestions;
DELETE FROM Grades;
DELETE FROM StudentAnswers;
DELETE FROM ExamSessions;
DELETE FROM ExamReleases;
DELETE FROM ExamQuestions;
DELETE FROM Exams;
DELETE FROM Enrollments;
DELETE FROM Users;
DELETE FROM Questions;
UPDATE Courses SET subject_id = NULL;
DELETE FROM Subjects;
DELETE FROM Courses;
-- Recreate Courses so subject_id exists even if this DB was created before V11
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS Courses;
CREATE TABLE Courses (
    id    INT          NOT NULL AUTO_INCREMENT,
    name  VARCHAR(255) NOT NULL,
    subject_id INT     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 1;
ALTER TABLE StudyBotHistory AUTO_INCREMENT = 1;
ALTER TABLE StudyBotSources AUTO_INCREMENT = 1;
ALTER TABLE ReleasedExamQuestions AUTO_INCREMENT = 1;
ALTER TABLE Grades        AUTO_INCREMENT = 1;
ALTER TABLE StudentAnswers AUTO_INCREMENT = 1;
ALTER TABLE ExamSessions  AUTO_INCREMENT = 1;
ALTER TABLE ExamReleases  AUTO_INCREMENT = 1;
ALTER TABLE ExamQuestions AUTO_INCREMENT = 1;
ALTER TABLE Exams         AUTO_INCREMENT = 1;
ALTER TABLE Users         AUTO_INCREMENT = 1;
ALTER TABLE Questions     AUTO_INCREMENT = 1;
ALTER TABLE Subjects      AUTO_INCREMENT = 1;
ALTER TABLE Courses       AUTO_INCREMENT = 1;

-- ---- Scenario 2/3: courses (subject link filled after users/subjects) ----
INSERT INTO Courses (name) VALUES
    ('Algorithms'),         -- id 1
    ('Databases'),          -- id 2
    ('Computer Networks');  -- id 3

-- ---- Scenario 2/3: question bank (topics + difficulties for auto-build) ----
INSERT INTO Questions
    (course_id, question_text, answer_1, answer_2, answer_3, answer_4, correct_answer, topic, difficulty)
VALUES
    (1, 'What is the time complexity of binary search on a sorted array?',
        'O(n)', 'O(log n)', 'O(n log n)', 'O(1)', 2, 'Complexity', 'EASY'),
    (1, 'Which data structure uses FIFO (first-in first-out) ordering?',
        'Stack', 'Queue', 'Tree', 'Graph', 2, 'Data Structures', 'EASY'),
    (1, 'What is the worst-case time complexity of QuickSort?',
        'O(n)', 'O(n log n)', 'O(n^2)', 'O(log n)', 3, 'Sorting', 'MEDIUM'),
    (2, 'Which SQL keyword removes duplicate rows from a result set?',
        'UNIQUE', 'DISTINCT', 'GROUP BY', 'HAVING', 2, 'SQL', 'EASY'),
    (2, 'What does ACID stand for in database transactions?',
        'Atomicity, Consistency, Isolation, Durability', 'A Cached Indexed Database',
        'Automatic Commit In Database', 'Associative Column Index Design', 1, 'Transactions', 'MEDIUM'),
    (2, 'Which normal form removes transitive dependencies?',
        '1NF', '2NF', '3NF', 'BCNF', 3, 'Normalization', 'HARD'),
    (3, 'Which OSI layer is responsible for reliable end-to-end delivery (e.g. TCP)?',
        'Network', 'Transport', 'Data Link', 'Session', 2, 'OSI Model', 'MEDIUM'),
    (3, 'Which protocol translates domain names into IP addresses?',
        'HTTP', 'DNS', 'FTP', 'SMTP', 2, 'Protocols', 'EASY');


-- ---- Scenario 3 prep (Person 2): RICH question pool for course 1 ----
-- Auto-build demo needs breadth: course 1 (Algorithms) gets 10 EASY +
-- 6 MEDIUM + 3 HARD across three topics, so "10 questions, 5 EASY + 5
-- MEDIUM" succeeds. Course 3 (Networks) deliberately stays SMALL (2
-- questions) so the "not enough questions -> no exam" negative demo works.
INSERT INTO Questions
    (course_id, question_text, answer_1, answer_2, answer_3, answer_4, correct_answer, topic, difficulty)
VALUES
    (1, 'What is the time complexity of accessing an array element by index?',
        'O(1)', 'O(log n)', 'O(n)', 'O(n^2)', 1, 'Complexity', 'EASY'),
    (1, 'What is the time complexity of linear search in an unsorted array?',
        'O(1)', 'O(log n)', 'O(n)', 'O(n log n)', 3, 'Complexity', 'EASY'),
    (1, 'Which notation describes an upper bound on running time?',
        'Big-O', 'Big-Omega', 'Big-Theta', 'Little-o', 1, 'Complexity', 'EASY'),
    (1, 'How many comparisons does bubble sort make on an already sorted array of n elements (optimized version)?',
        'n-1', 'n^2', 'n log n', '0', 1, 'Sorting', 'EASY'),
    (1, 'Which sorting algorithm repeatedly selects the minimum element?',
        'Bubble sort', 'Selection sort', 'Merge sort', 'Quick sort', 2, 'Sorting', 'EASY'),
    (1, 'Which sorting algorithm is stable by nature?',
        'Selection sort', 'Heap sort', 'Merge sort', 'Quick sort', 3, 'Sorting', 'EASY'),
    (1, 'Which data structure uses LIFO ordering?',
        'Queue', 'Stack', 'Heap', 'Deque', 2, 'Data Structures', 'EASY'),
    (1, 'What is stored in a binary search tree node relative to its left subtree?',
        'Smaller keys', 'Larger keys', 'Equal keys', 'Random keys', 1, 'Data Structures', 'EASY'),
    (1, 'Which data structure gives O(1) average lookup by key?',
        'Array', 'Linked list', 'Hash table', 'Binary tree', 3, 'Data Structures', 'EASY'),
    (1, 'What is the height of a balanced binary tree with n nodes?',
        'O(1)', 'O(log n)', 'O(n)', 'O(n log n)', 2, 'Data Structures', 'EASY'),
    (1, 'What is the average-case time complexity of QuickSort?',
        'O(n)', 'O(n log n)', 'O(n^2)', 'O(log n)', 2, 'Sorting', 'MEDIUM'),
    (1, 'How much extra memory does standard merge sort require?',
        'O(1)', 'O(log n)', 'O(n)', 'O(n^2)', 3, 'Sorting', 'MEDIUM'),
    (1, 'What is the amortized cost of appending to a dynamic array?',
        'O(1)', 'O(log n)', 'O(n)', 'O(n log n)', 1, 'Complexity', 'MEDIUM'),
    (1, 'Which traversal of a binary search tree yields sorted order?',
        'Pre-order', 'In-order', 'Post-order', 'Level-order', 2, 'Data Structures', 'MEDIUM'),
    (1, 'What is the worst-case complexity of inserting into a binary heap?',
        'O(1)', 'O(log n)', 'O(n)', 'O(n log n)', 2, 'Data Structures', 'MEDIUM'),
    (1, 'Dijkstra''s algorithm with a binary heap runs in:',
        'O(V^2)', 'O((V+E) log V)', 'O(VE)', 'O(E log E)', 2, 'Complexity', 'MEDIUM'),
    (1, 'Which recurrence describes binary search?',
        'T(n)=2T(n/2)+O(n)', 'T(n)=T(n/2)+O(1)', 'T(n)=T(n-1)+O(1)', 'T(n)=2T(n-1)+O(1)', 2, 'Complexity', 'HARD'),
    (1, 'What is the lower bound for comparison-based sorting?',
        'O(n)', 'O(n log n)', 'O(n^2)', 'O(log n)', 2, 'Sorting', 'HARD'),
    (1, 'In a red-black tree, the longest root-to-leaf path is at most:',
        'Equal to the shortest', 'Twice the shortest', 'Three times the shortest', 'Unbounded', 2, 'Data Structures', 'HARD'),
    (2, 'Which SQL clause filters groups after aggregation?',
        'WHERE', 'HAVING', 'GROUP BY', 'ORDER BY', 2, 'SQL', 'MEDIUM'),
    (2, 'Which isolation level allows non-repeatable reads but not dirty reads?',
        'READ UNCOMMITTED', 'READ COMMITTED', 'REPEATABLE READ', 'SERIALIZABLE', 2, 'Transactions', 'HARD'),
    (2, 'A foreign key enforces which kind of integrity?',
        'Entity', 'Referential', 'Domain', 'Semantic', 2, 'SQL', 'EASY');

UPDATE Questions SET base_id = id WHERE base_id IS NULL;

-- ---- Scenario 2 note: one question carries a real ILLUSTRATION ----
-- (tiny generated PNG; proves add-with-image + lazy GET_QUESTION_IMAGE)
UPDATE Questions
   SET image_path = 'fifo-queue.png',
       image_data = 0x89504E470D0A1A0A0000000D49484452000000180000001808020000006F15AAAF0000002E4944415478DA63F8FAFD175510C34830482EE00410A1494304E1085376D4A05183460D1A3588FA068D96D9B81000E96CADCC24DAE8B30000000049454E44AE426082
 WHERE id = 2;

-- ---- Scenario 2.2: versioning demo — question 1 edited TWICE; ----
-- ---- v1 and v2 KEPT (retired), v3 current — feeds the History view ----
UPDATE Questions SET is_current = FALSE WHERE id = 1;
INSERT INTO Questions
    (course_id, question_text, answer_1, answer_2, answer_3, answer_4, correct_answer,
     topic, difficulty, base_id, version, is_current)
VALUES
    (1, 'What is the time complexity of binary search on a sorted array of n elements?',
        'O(n)', 'O(log n)', 'O(n log n)', 'O(1)', 2, 'Complexity', 'EASY', 1, 2, FALSE),
    (1, 'What is the worst-case time complexity of binary search on a sorted array of n elements?',
        'O(n)', 'O(log n)', 'O(n log n)', 'O(1)', 2, 'Complexity', 'EASY', 1, 3, TRUE);

-- ---- Scenario 1: A1-scale cast (password 1234 for all) ----
-- Passwords are stored as SHA-256 hashes (never plaintext); the server hashes
-- the typed password before comparing (see server.db.PasswordHasher).
--
-- First five rows keep fixed usernames (and maya's id_number) that the JUnit
-- suite authenticates against. Extra rows mirror Assignment 1 personas.
INSERT INTO Users (username, password, role, display_name, id_number) VALUES
    ('teacher',   SHA2('1234', 256), 'TEACHER',     'Dana Avni (Teacher)',        NULL),
    ('coord',     SHA2('1234', 256), 'COORDINATOR', 'Yael Golan (Coordinator)',   NULL),
    ('principal', SHA2('1234', 256), 'PRINCIPAL',   'Merav Solomon (Principal)',  NULL),
    ('maya',      SHA2('1234', 256), 'STUDENT',     'Maya Levi',                  '207570227'),
    ('noa',       SHA2('1234', 256), 'STUDENT',     'Noa Barak',                  '315497081'),
    ('shira',     SHA2('1234', 256), 'STUDENT',     'Shira Cohen',                '312456789'),
    ('tamar',     SHA2('1234', 256), 'STUDENT',     'Tamar Rosen',                '311887766'),
    ('avigail',   SHA2('1234', 256), 'STUDENT',     'Avigail Katz',               '309112233'),
    ('neta',      SHA2('1234', 256), 'TEACHER',     'Neta Berkovich (Teacher)',   NULL),
    ('ronit',     SHA2('1234', 256), 'TEACHER',     'Ronit Segev (Teacher)',      NULL);

-- ---- Subjects (semester PDF): courses belong to a subject; Yael coordinates all ----
INSERT INTO Subjects (name, code, coordinator_id)
SELECT 'CS Theory', 1, u.id FROM Users u WHERE u.username = 'coord'
UNION ALL
SELECT 'Data Systems', 2, u.id FROM Users u WHERE u.username = 'coord'
UNION ALL
SELECT 'Computer Systems', 3, u.id FROM Users u WHERE u.username = 'coord';

UPDATE Courses c
JOIN Subjects s ON s.code = c.id
SET c.subject_id = s.id;

-- ---- Enrollments (A1 pattern → CS courses) ----
--   Algorithms (1): maya, shira, tamar, avigail
--   Databases  (2): maya, noa, tamar
--   Networks   (3): noa, shira
-- maya is deliberately NOT in Networks (negative enrollment demo / UserDAOTest).
INSERT INTO Enrollments (user_id, course_id)
SELECT u.id, c.id FROM Users u JOIN Courses c
WHERE (u.username = 'maya'    AND c.id IN (1, 2))
   OR (u.username = 'noa'     AND c.id IN (2, 3))
   OR (u.username = 'shira'   AND c.id IN (1, 3))
   OR (u.username = 'tamar'   AND c.id IN (1, 2))
   OR (u.username = 'avigail' AND c.id = 1);

-- ---- Scenarios 3–4: sample exams for teacher / coordinator UIs ----
-- Exam display id: until Person 3 ships a 6-digit codec, the UI shows
-- "#" + base_id (see docs/PERSON5_UI.md). teacher_id = 1 (Dana / teacher).
-- Four current Algorithms questions × 25 pts = 100.

INSERT INTO Exams
    (course_id, teacher_id, title, duration_minutes,
     student_instructions, teacher_notes, status, version, is_current)
VALUES
    (1, 1, 'Algorithms Midterm (Draft)', 90,
     'Answer all questions. No calculators.',
     'Draft for demo — teacher can edit and submit.',
     'DRAFT', 1, TRUE),
    (1, 1, 'Algorithms Quiz (Pending)', 60,
     'Closed book.',
     'Waiting for coordinator approval.',
     'PENDING_APPROVAL', 1, TRUE);

UPDATE Exams SET base_id = id WHERE base_id IS NULL;

-- Attach 4 current course-1 questions (25 each) to each sample exam.
-- After the inserts above, ids 2, 3, 9, 10 are stable current Algorithms questions
-- (id 1 is retired by the versioning demo; rich pool starts at id 9).
INSERT INTO ExamQuestions (exam_id, question_id, points, position)
SELECT e.id, q.question_id, 25, q.position
FROM Exams e
CROSS JOIN (
    SELECT 2 AS question_id, 1 AS position UNION ALL
    SELECT 3, 2 UNION ALL
    SELECT 9, 3 UNION ALL
    SELECT 10, 4
) q
WHERE e.title IN ('Algorithms Midterm (Draft)', 'Algorithms Quiz (Pending)');

-- ---- Scenario 5 prep: one APPROVED exam ready to release ----
INSERT INTO Exams
    (course_id, teacher_id, title, duration_minutes,
     student_instructions, teacher_notes, status, version, is_current)
VALUES
    (1, 1, 'Algorithms Approved Quiz', 45,
     'Closed book. Enter the 4-character code from your teacher.',
     'Approved — ready for Release Exams screen.',
     'APPROVED', 1, TRUE);

UPDATE Exams SET base_id = id WHERE base_id IS NULL AND title = 'Algorithms Approved Quiz';

INSERT INTO ExamQuestions (exam_id, question_id, points, position)
SELECT e.id, q.question_id, 25, q.position
FROM Exams e
CROSS JOIN (
    SELECT 2 AS question_id, 1 AS position UNION ALL
    SELECT 3, 2 UNION ALL
    SELECT 9, 3 UNION ALL
    SELECT 10, 4
) q
WHERE e.title = 'Algorithms Approved Quiz';

-- ---- Ronit (Databases teacher): draft exam for multi-teacher demos ----
INSERT INTO Exams
    (course_id, teacher_id, title, duration_minutes,
     student_instructions, teacher_notes, status, version, is_current)
SELECT
    2, u.id, 'Databases Spot Check (Draft)', 40,
    'Answer all SQL questions.',
    'Owned by Ronit — proves a second teacher in the drawer.',
    'DRAFT', 1, TRUE
FROM Users u WHERE u.username = 'ronit';

UPDATE Exams SET base_id = id WHERE base_id IS NULL AND title = 'Databases Spot Check (Draft)';

INSERT INTO ExamQuestions (exam_id, question_id, points, position)
SELECT e.id, q.question_id, 25, q.position
FROM Exams e
CROSS JOIN (
    -- Stable current Databases questions from the bank (ids 4–6, 28-ish pool)
    SELECT 4 AS question_id, 1 AS position UNION ALL
    SELECT 5, 2 UNION ALL
    SELECT 6, 3 UNION ALL
    SELECT 28, 4
) q
WHERE e.title = 'Databases Spot Check (Draft)';

-- ---- Scenario 13–14: CourseTeachers (A1: Dana + Neta on Algorithms;
--      Dana + Ronit on Databases). Also fold any exam authors. ----
INSERT IGNORE INTO CourseTeachers (course_id, teacher_id)
SELECT c.id, u.id FROM Courses c JOIN Users u
WHERE (u.username = 'teacher' AND c.id IN (1, 2))
   OR (u.username = 'neta'    AND c.id = 1)
   OR (u.username = 'ronit'   AND c.id = 2);

INSERT IGNORE INTO CourseTeachers (course_id, teacher_id)
SELECT DISTINCT course_id, teacher_id FROM Exams;

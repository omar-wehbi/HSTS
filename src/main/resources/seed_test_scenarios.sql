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
--  Scenario coverage (required features 1-14):
--    1  Login ................. 5 users, one per role (+2nd student)
--    2  Question bank ......... 8 questions across 3 courses, incl.
--                               one EDITED question proving versioning
--                               (v1 kept, v2 current)
--    3  Build exams ........... questions have topic + difficulty so
--                               auto-generation can filter; pool spans
--                               EASY/MEDIUM/HARD (Person 3 adds exam tables)
--    6  Take exam ............. students have id_number (ת"ז)
--    14 Study bot ............. Enrollments let the server check a
--                               student is enrolled in a course
--  Later scenarios (4,5,7-13) plug into these same users/courses;
--  their tables are added by their owners as the features land.
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

-- ============================================================
-- DATA (deterministic wipe + re-seed)
-- ============================================================

DELETE FROM Enrollments;
DELETE FROM Users;
DELETE FROM Questions;
DELETE FROM Courses;
ALTER TABLE Users     AUTO_INCREMENT = 1;
ALTER TABLE Questions AUTO_INCREMENT = 1;
ALTER TABLE Courses   AUTO_INCREMENT = 1;

-- ---- Scenario 2/3: courses ----
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

-- ---- Scenario 1: one user per role (password 1234 for all) ----
-- Passwords are stored as SHA-256 hashes (never plaintext); the server hashes
-- the typed password before comparing (see server.db.PasswordHasher).
INSERT INTO Users (username, password, role, display_name, id_number) VALUES
    ('teacher',   SHA2('1234', 256), 'TEACHER',     'Dana Cohen (Teacher)',     NULL),
    ('coord',     SHA2('1234', 256), 'COORDINATOR', 'Yossi Levi (Coordinator)', NULL),
    ('principal', SHA2('1234', 256), 'PRINCIPAL',   'Rita Bar (Principal)',     NULL),
    ('maya',      SHA2('1234', 256), 'STUDENT',     'Maya Student',             '207570227'),
    ('noa',       SHA2('1234', 256), 'STUDENT',     'Noa Student',              '315497081');

-- ---- Scenario 14: enrollment (maya+noa in Algorithms & Databases,
--      NOT in Computer Networks — gives a negative case to test) ----
INSERT INTO Enrollments (user_id, course_id)
SELECT u.id, c.id
FROM Users u JOIN Courses c
WHERE u.username IN ('maya', 'noa') AND c.id IN (1, 2);

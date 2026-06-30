-- ============================================================
--  HSTS — Assignment 3 : test data for the Question Bank
--  Run after schema.sql:  mysql -u root -p < seed.sql
-- ============================================================
USE hsts_a3_db;

-- Clean re-seed (safe to run repeatedly).
DELETE FROM Questions;
DELETE FROM Courses;
ALTER TABLE Questions AUTO_INCREMENT = 1;
ALTER TABLE Courses   AUTO_INCREMENT = 1;

-- ---- Courses ----
INSERT INTO Courses (name) VALUES
    ('Algorithms'),         -- id 1
    ('Databases'),          -- id 2
    ('Computer Networks');  -- id 3

-- ---- Questions (all start as version 1) ----
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

-- Each original question is the first member of its own version "family".
UPDATE Questions SET base_id = id WHERE base_id IS NULL;

-- ---- Versioning demo ----
-- Edit question #1: the OLD version stays (is_current = FALSE) and a new
-- version 2 is inserted as the current one (same base_id = 1).
UPDATE Questions SET is_current = FALSE WHERE id = 1;

INSERT INTO Questions
    (course_id, question_text, answer_1, answer_2, answer_3, answer_4, correct_answer,
     topic, difficulty, base_id, version, is_current)
VALUES
    (1, 'What is the time complexity of binary search on a sorted array of n elements?',
        'O(n)', 'O(log n)', 'O(n log n)', 'O(1)', 2, 'Complexity', 'EASY', 1, 2, TRUE);

-- V10 — Align Scenarios 5–12 with the official Spring 2026 specification.
USE hsts_a3_db;

-- Four alphanumeric execution fields; a code may be reused when time windows do not overlap.
ALTER TABLE ExamReleases DROP INDEX uq_release_execution_code;
ALTER TABLE ExamReleases DROP CHECK chk_release_execution_code;
ALTER TABLE ExamReleases ADD CONSTRAINT chk_release_execution_code CHECK (execution_code REGEXP '^[A-Za-z0-9]{4}$');
CREATE INDEX idx_release_code_window ON ExamReleases(execution_code,open_time,close_time);

ALTER TABLE ExamSessions ADD COLUMN actual_duration_minutes INT NULL AFTER extension_minutes;
ALTER TABLE Grades ADD COLUMN teacher_comment VARCHAR(2000) NULL AFTER override_justification;

-- Immutable question/correct-answer copy for every released exam sitting.
CREATE TABLE IF NOT EXISTS ReleasedExamQuestions (
 id INT NOT NULL AUTO_INCREMENT, release_id INT NOT NULL, question_id INT NOT NULL,
 points INT NOT NULL, position INT NOT NULL, question_text TEXT NOT NULL,
 answer_1 TEXT NOT NULL, answer_2 TEXT NOT NULL, answer_3 TEXT NOT NULL, answer_4 TEXT NOT NULL,
 correct_answer TINYINT NOT NULL, image_path VARCHAR(512) NULL,
 PRIMARY KEY(id), UNIQUE KEY uq_release_snapshot_position(release_id,position),
 CONSTRAINT fk_snapshot_release FOREIGN KEY(release_id) REFERENCES ExamReleases(id) ON DELETE CASCADE,
 CONSTRAINT chk_snapshot_answer CHECK(correct_answer BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Stored statistics for each administered exam instance.
CREATE TABLE IF NOT EXISTS ExamExecutionStatistics (
 release_id INT NOT NULL, graded_count INT NOT NULL DEFAULT 0,
 mean_score DECIMAL(7,3) NOT NULL DEFAULT 0, median_score DECIMAL(7,3) NOT NULL DEFAULT 0,
 d0 INT NOT NULL DEFAULT 0,d1 INT NOT NULL DEFAULT 0,d2 INT NOT NULL DEFAULT 0,d3 INT NOT NULL DEFAULT 0,d4 INT NOT NULL DEFAULT 0,
 d5 INT NOT NULL DEFAULT 0,d6 INT NOT NULL DEFAULT 0,d7 INT NOT NULL DEFAULT 0,d8 INT NOT NULL DEFAULT 0,d9 INT NOT NULL DEFAULT 0,
 calculated_at DATETIME NOT NULL, PRIMARY KEY(release_id),
 CONSTRAINT fk_execution_stats_release FOREIGN KEY(release_id) REFERENCES ExamReleases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill snapshots for releases created before this upgrade. Future releases are copied by ExamReleaseService.
INSERT IGNORE INTO ReleasedExamQuestions(release_id,question_id,points,position,question_text,answer_1,answer_2,answer_3,answer_4,correct_answer,image_path)
SELECT er.id,eq.question_id,eq.points,eq.position,q.question_text,q.answer_1,q.answer_2,q.answer_3,q.answer_4,q.correct_answer,q.image_path
FROM ExamReleases er JOIN ExamQuestions eq ON eq.exam_id=er.exam_id JOIN Questions q ON q.id=eq.question_id;

UPDATE ExamSessions
SET actual_duration_minutes=TIMESTAMPDIFF(MINUTE,started_at,COALESCE(submitted_at,deadline))
WHERE status<>'IN_PROGRESS' AND actual_duration_minutes IS NULL;

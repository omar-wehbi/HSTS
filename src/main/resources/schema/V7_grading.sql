-- Scenario 8: computerized grading, teacher approval, and justified overrides.
USE hsts_a3_db;

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

    PRIMARY KEY (id),
    UNIQUE KEY uq_grades_session (session_id),
    KEY idx_grades_exam (exam_id),
    KEY idx_grades_student (student_id),

    CONSTRAINT fk_grades_session FOREIGN KEY (session_id) REFERENCES ExamSessions(id),
    CONSTRAINT fk_grades_exam FOREIGN KEY (exam_id) REFERENCES Exams(id),
    CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES Users(id),
    CONSTRAINT fk_grades_approver FOREIGN KEY (approved_by) REFERENCES Users(id),
    CONSTRAINT chk_grades_auto_score CHECK (auto_score BETWEEN 0 AND 100),
    CONSTRAINT chk_grades_final_score CHECK (final_score IS NULL OR final_score BETWEEN 0 AND 100),
    CONSTRAINT chk_grades_override_reason CHECK (
        status <> 'OVERRIDDEN' OR
        (override_justification IS NOT NULL AND CHAR_LENGTH(TRIM(override_justification)) > 0)
    )
);

-- Upgrade an installation that already ran the original V8_study_bot.sql.
USE hsts_a3_db;
ALTER TABLE StudyBots ADD COLUMN name VARCHAR(120) NOT NULL DEFAULT 'Course Study Bot' AFTER course_id;
ALTER TABLE StudyBots ADD COLUMN include_question_bank BOOLEAN NOT NULL DEFAULT FALSE AFTER available;
ALTER TABLE StudyBotSources MODIFY COLUMN content MEDIUMTEXT NOT NULL;
ALTER TABLE StudyBotSources ADD COLUMN source_type ENUM('TEXT','PDF','WORD') NOT NULL DEFAULT 'TEXT' AFTER content;
ALTER TABLE StudyBotSources ADD COLUMN original_filename VARCHAR(255) NULL AFTER source_type;
ALTER TABLE StudyBotSources ADD COLUMN created_by INT NULL AFTER original_filename;
UPDATE StudyBotSources SET created_by=updated_by WHERE created_by IS NULL;
ALTER TABLE StudyBotSources MODIFY COLUMN created_by INT NOT NULL;
ALTER TABLE StudyBotHistory ADD COLUMN status ENUM('ANSWERED','NO_ANSWER') NOT NULL DEFAULT 'ANSWERED' AFTER answer;

ALTER TABLE StudyBotSources ADD CONSTRAINT fk_bot_source_creator FOREIGN KEY(created_by) REFERENCES Users(id);

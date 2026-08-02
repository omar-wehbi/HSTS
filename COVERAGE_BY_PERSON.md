# Coverage by person — 2026-08-02

Generated from a full green suite: **427 tests, 0 failures**.  
Report: `target/site/jacoco/index.html`

## Overall (JaCoCo)

| Metric | Coverage |
|--------|----------|
| Instructions | **53.1%** (16,797 / 31,623) |
| Lines | **49.5%** (3,247 / 6,560) |
| Branches | **43.2%** (1,357 / 3,140) |

JaCoCo only instruments `server.*`, `client.*`, `common.*`, `ocsf.*`.

---

## By person (instruction / line / branch)

Ownership mapped from `TEAM_DIVISION.md`. Shared DTOs/OCSF are separate.

| Person | Instr % | Line % | Branch % | Instr covered/total | Classes |
|--------|---------|--------|----------|---------------------|---------|
| **4 Saleh** (release/exec/grade/bot/principal) | **78.5%** | 77.5% | 53.4% | 5,803 / 7,395 | 18 |
| **3 Hasan** (exam build/approve) | **65.8%** | 61.7% | 61.6% | 1,390 / 2,112 | 4 |
| **2 Naji** (question bank + DB config) | **61.2%** | 60.1% | 77.5% | 1,182 / 1,930 | 9 |
| **1 Amjad** (auth/sessions/infra) | **56.5%** | 54.3% | 45.1% | 794 / 1,405 | 11 |
| **5 UI** (views + sessions + network) | **31.9%** | 32.5% | 31.2% | 4,618 / 14,499 | 41 |
| Shared (DTOs + OCSF) | **70.3%** | 75.7% | 42.9% | 3,010 / 4,282 | 59 |

Person 5 is pulled down by FXML **View** classes (~2.5% in `client.ui`). Session logic in `client.ui.exam` is **82.8%**.

---

## Package highlights

| Package | Instr % |
|---------|---------|
| `common.util` | **100%** |
| `server.config` | **84.5%** |
| `client.ui.exam` | **82.8%** |
| `server` | **77.9%** |
| `common.entities` | **78.1%** |
| `server.db` | **73.3%** |
| `client.config` | **65.3%** |
| `common.network` | **66.1%** |
| `client.events` | **62.5%** |
| `client.network` | **56.0%** |
| `server.bot` | **24.5%** |
| `client.ui` (Views) | **2.5%** |
| `server.ui` | **0%** |

### Person 4 — Saleh (detail)
| Class | Instr % | Missed |
|-------|---------|--------|
| ExamReleaseService | 91.8% | 21 |
| PrincipalReadOnlyDAO | 92.6% | 20 |
| ResultsService | 90.3% | 59 |
| PrincipalReportService | 88.7% | 64 |
| StudyBotDAO | 84.0% | 139 |
| ExamSnapshotDAO | 84.7% | 31 |
| ExamExecutionService | 80.3% | 185 |
| ExamSessionDAO | 77.8% | 135 |
| ExamReleaseDAO | 76.6% | 80 |
| ExecutionReportDAO | 73.6% | 86 |
| StudyBotService | 72.7% | 195 |
| GradeDAO | 71.0% | 94 |
| GradingService | 70.3% | 157 |
| StudyBotDocumentExtractor | 44.8% | 74 |
| ExternalStudyBotApiAdapter | 12.4% | 197 |

---

## Largest uncovered classes (missed instructions)

| Class | Owner | Missed instr |
|-------|-------|--------------|
| QuestionsView | P5 | 1,240 |
| ExamBuilderView | P5 | 1,214 |
| ExamReleaseView | P5 | 615 |
| GradeExamsView | P5 | 597 |
| ServerConnectionDialog | P2 | 566 |
| TakeExamView | P5 | 513 |
| ExamListView | P5 | 484 |
| StudyBotTeacherView | P5 | 418 |
| HomeView | P5 | 323 |
| ExternalStudyBotApiAdapter | P4 | 197 |

---

## How to regenerate

```powershell
.\mvnw.cmd clean test
start target\site\jacoco\index.html
```

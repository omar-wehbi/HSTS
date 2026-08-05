# HSTS demo users

Source of truth: `src/main/resources/seed_test_scenarios.sql`  
Reload anytime:

```powershell
Get-Content src\main\resources\seed_test_scenarios.sql | mysql -u root -p
```

**Password for every account: `1234`**

Courses in this DB: **Algorithms**, **Databases**, **Computer Networks**  
(Assignment 1 Math / Biology / Physics cast mapped onto these CS courses.)

Subjects (semester PDF):

| Subject | Code | Courses | Coordinator |
|---------|------|---------|-------------|
| CS Theory | 1 | Algorithms | `coord` (Yael Golan) |
| Data Systems | 2 | Databases | `coord` |
| Computer Systems | 3 | Computer Networks | `coord` |

Stable usernames required by automated tests (do not rename):  
`teacher`, `coord`, `principal`, `maya`, `noa`

---

## Teachers

| Username | Display name | National ID | Teaches |
|----------|--------------|-------------|---------|
| `teacher` | Dana Avni (Teacher) | — | Algorithms, Databases |
| `neta` | Neta Berkovich (Teacher) | — | Algorithms |
| `ronit` | Ronit Segev (Teacher) | — | Databases |

## Staff

| Username | Display name | National ID | Notes |
|----------|--------------|-------------|--------|
| `coord` | Yael Golan (Coordinator) | — | Approves / rejects exams |
| `principal` | Merav Solomon (Principal) | — | Read-only catalog + reports |

## Students

| Username | Display name | National ID | Enrolled in |
|----------|--------------|-------------|-------------|
| `maya` | Maya Levi | `207570227` | Algorithms, Databases |
| `noa` | Noa Barak | `315497081` | Databases, Networks |
| `shira` | Shira Cohen | `312456789` | Algorithms, Networks |
| `tamar` | Tamar Rosen | `311887766` | Algorithms, Databases |
| `avigail` | Avigail Katz | `309112233` | Algorithms |

---

## Useful negatives for demos

- **Maya** is not in Networks → enrollment rejection there
- **Noa** is not in Algorithms; **Avigail** is only in Algorithms → take-exam enrollment checks
- **Ronit** only teaches Databases → cannot release Algorithms exams

## Sample exams in the seed

| Title | Owner | Course | Status |
|-------|-------|--------|--------|
| Algorithms Midterm (Draft) | Dana (`teacher`) | Algorithms | `DRAFT` |
| Algorithms Quiz (Pending) | Dana (`teacher`) | Algorithms | `PENDING_APPROVAL` |
| Algorithms Approved Quiz | Dana (`teacher`) | Algorithms | `APPROVED` (ready to release) |
| Databases Spot Check (Draft) | Ronit (`ronit`) | Databases | `DRAFT` |

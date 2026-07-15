# AP32 — Curriculum Examples Admin CRUD (Plan / Design)

> **Status: IMPLEMENTED (2026-07-06).** Admin-only CRUD is live: backend endpoints
> `POST /api/curriculum/topics/{topicId}/examples`, `PUT /api/curriculum/examples/{id}`,
> `DELETE /api/curriculum/examples/{id}` (gated on `OwnershipService.isAdmin()`), backed by
> `CurriculumExampleAdminService` + repository write methods; frontend management panel on
> `CurriculumPage` (add/edit/delete, admin-only). This document is kept as the design record;
> the deviations noted inline below (e.g. topic assignment immutable on edit) reflect what shipped.
>
> Out of scope items in §6 remain unbuilt.

## 1. Goal & user story

- **Teacher/Admin:** "I want to add, edit and delete curated code examples for a
  curriculum topic directly in the app (title, language, code, problem statement,
  difficulty, didactic notes), so the curriculum generator has few-shot anchors
  without anyone touching the database."

Currently examples are **read-only** in the app (`GET /curriculum/topics/{id}/examples`)
and can only be created via SQL seed. This closes that gap.

## 2. Architecture fit (per docs/AP30-32 §0.2)

Read side already exists and is the template to mirror:
- Port: `domain.interfaces.CurriculumCodeExampleRepository` (has `findByTopicId`, `findByTopicIdAndLanguage`).
- Adapter: `infrastructure.persistence.dao.PostgreCurriculumCodeExampleDao` (JdbcTemplate + RowMapper).
- DTO: `CurriculumCodeExampleDTO` (id, topicId, language, title, code, problemStatement, difficulty, notes, createdAt).
- Controller: `api.CurriculumController` (`getExamples`).

The write side follows the **same** layering — no new tech, constructor injection only.

## 3. Backend changes

### 3.1 Repository port (`CurriculumCodeExampleRepository`)
Add write methods:
```java
CurriculumCodeExampleDTO findById(UUID id);      // for edit/ownership checks
UUID insert(CurriculumCodeExampleDTO ex);        // returns new id
void update(CurriculumCodeExampleDTO ex);        // by id
void deleteById(UUID id);
```

### 3.2 Adapter (`PostgreCurriculumCodeExampleDao`)
Implement the four methods with raw `JdbcTemplate`:
- `insert`: `INSERT INTO curriculum_code_examples (id, topic_id, language, title, code, problem_statement, difficulty, notes) VALUES (?,?,?,?,?,?,?,?)` with a generated UUID; reuse the existing `RowMapper` for `findById`.
- `update`: `UPDATE ... SET language=?, title=?, code=?, problem_statement=?, difficulty=?, notes=? WHERE id=?` (guard row-count == 1).
- `deleteById`: `DELETE ... WHERE id=?`.
- Validate `difficulty ∈ {easy, medium, hard}` in the service layer (see 3.4), not the DAO.

### 3.3 Application service (new: `CurriculumExampleAdminService` — Use Case layer)
Keeps validation/orchestration out of the controller and DAO:
- `create(CurriculumCodeExampleDTO)` — validate required fields (topicId exists, language, title, code non-blank; difficulty in enum; notes optional), then `repo.insert`.
- `update(UUID id, CurriculumCodeExampleDTO)` — load, validate, `repo.update`.
- `delete(UUID id)` — `repo.deleteById`.
- Topic existence check via `CurriculumTopicRepository.findById`.

### 3.4 Controller (`CurriculumController`) — new endpoints
```
POST   /api/curriculum/topics/{topicId}/examples        -> create (201 + body)
PUT    /api/curriculum/examples/{exampleId}             -> update (200)
DELETE /api/curriculum/examples/{exampleId}             -> delete (204)
```
- **Authorization:** these are content-management ops. Gate to **ADMIN** (and/or TEACHER)
  via `ownershipService` — mirror `denyStudents(...)` plus a role check. Decide: are curated
  examples global (admin-only) or per-teacher? Recommended for pilot: **admin-only** (they are
  shared curriculum assets). Revisit if teachers should own their own example sets (would need
  an `owner_id` column — schema change, out of scope here).
- Request DTO: reuse `CurriculumCodeExampleDTO` (ignore server-managed `id`/`createdAt` on create).

### 3.5 Validation / errors
- Blank required field → 400 `{ error: "validation", message }`.
- Bad difficulty → 400.
- Unknown topic/example id → 404.
- Reuse the existing `DomainValidationException` → error-handler mapping.

## 4. Frontend changes

### 4.1 Where
Extend `CurriculumPage.tsx` (examples are already listed there under a selected topic).
Add an **admin-only** management panel below the examples list, shown when
`auth.role === "ADMIN"`.

### 4.2 UI
- Each existing example row (title · language · difficulty badge) gets **Edit** / **Delete** buttons.
- An **"+ Add example"** button opens a form (modal or inline) with fields:
  title, language (select — reuse language options), code (monospace `<textarea>`),
  problem statement (`<textarea>`), difficulty (select: easy/medium/hard), notes (`<textarea>`).
- On save: `POST`/`PUT`; on delete: confirm then `DELETE`; re-fetch the examples list
  (`getExamples`) after each mutation.
- **Reuse note:** the code editor could reuse `CodeSolutionEditor` if inline gaps/splits ever
  become desired for seed code — but for MVP a plain monospace textarea is enough (seed code is
  reference material, not a Parsons puzzle).

### 4.3 API client + types
- Add `createExample`, `updateExample`, `deleteExample` to the curriculum API calls.
- `CurriculumCodeExample` type already carries all fields.

### 4.4 i18n
New keys under `curriculum.*`: `addExample`, `editExample`, `deleteExample`,
`deleteConfirm`, `exampleTitle`, `exampleCode`, `exampleDifficulty`, `exampleNotes`,
`exampleSaved`, `exampleDeleteError`, difficulty labels (`diffEasy`/`diffMedium`/`diffHard`),
in both `en.json` and `de.json`.

## 5. Effort estimate
- Backend: ~1 repo port + DAO impl + small service + 3 controller endpoints. ~½ day.
- Frontend: examples management panel + form + 3 API calls + i18n. ~½–1 day.
- No migration needed (schema already has all columns). Needs a **backend rebuild** to ship.

## 6. Explicitly out of scope for this plan
- Per-teacher ownership of examples (`owner_id` column + gating) — would be a schema change.
- Bulk import/export of examples.
- Versioning/history of example edits.

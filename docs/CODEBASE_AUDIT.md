# CodeOwl Codebase Audit

**Datum:** 2026-04-16
**Scope:** Backend (Spring Boot / Java), Frontend (React / TypeScript), DB-Schema, Docker, Konfiguration

---

## Legende

| Severity | Bedeutung |
|----------|-----------|
| **BUG** | Laufzeitfehler oder Datenverlust wahrscheinlich |
| **WARN** | Potentielles Problem, funktioniert evtl. nur durch Zufall |
| **STYLE** | Unschoenheit, Inkonsistenz, Refactoring-Bedarf |

---

## 1. Domain Entities / DTOs

### BUG — ConceptCurriculumTopicDTO: Feld `curriculumId` vs. DB-Spalte `topic_id`

**Datei:** `backend/.../domain/entities/ConceptCurriculumTopicDTO.java:30`

Das DTO hat das Feld `curriculumId`, aber die DB-Spalte heisst `topic_id` und der DAO (`PostgreConceptCurriculumTopicDAO`) nutzt `dto.getCurriculumId()` um `topic_id` zu schreiben. Das funktioniert zwar, ist aber semantisch falsch und verwirrend — ein Curriculum-Topic ist kein "Curriculum". Beim Refactoring oder neuen Code wird jemand `getTopicId()` erwarten und es nicht finden.

**Empfehlung:** Feld umbenennen zu `topicId` mit Getter `getTopicId()`.

---

### STYLE — Inkonsistente Lombok-Annotationen auf DTOs

Verschiedene DTOs nutzen unterschiedliche Lombok-Patterns:

| DTO | Pattern |
|-----|---------|
| `TaskDTO`, `TieredTaskSequenceDTO`, `WorksheetDTO` | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` |
| `ConceptCurriculumTopicDTO`, `CurriculumTopicDTO`, `CurriculumCodeExampleDTO` | `@Data` (kein Builder) |
| `GenerationConstraints` | `@Data @Builder` |
| `UserDTO` | `@Getter @Setter @Builder` |

**Empfehlung:** Einheitliches Pattern waehlen. `@Data` + `@Builder` oder `@Getter @Setter @Builder` fuer alle. `@Data` allein fehlt Builder-Support, was bei Service-Code zu `new DTO()` + Setter-Ketten fuehrt statt Builder-Pattern.

---

### STYLE — `TieredTaskSequenceDTO.NUM_OF_TASKS` ist veraltet

**Datei:** `backend/.../domain/entities/TieredTaskSequenceDTO.java:26`

```java
public static final int NUM_OF_TASKS = 3;
```

Seit AP21 (5-Tier-Modus) ist die Anzahl der Tasks variabel (3 oder 5). Diese Konstante wird nirgends referenziert, sollte entfernt werden.

---

## 2. Persistence Layer (DAOs, RowMappers, Schema)

### BUG — `strategyName` Spaltenname-Mismatch (Case-Sensitivity)

**Dateien:**
- `backend/.../persistence/dao/PostgreSequenceDAO.java:37,66,93,183`
- `backend/.../persistence/mapper/TieredTaskSequenceRowMapper.java:31`
- `db/init/z_codeowl_schema.sql:324` (DROP)
- `db/init/codeowl_backup.sql:336` (Definition)

**Problem:** Die Backup-Datei definiert die Spalte als `strategyname` (unquoted = lowercase in PostgreSQL). Das Schema-Script droppt die quoted Variante `"strategyName"`. Aber der gesamte Java-Code referenziert die Spalte als `strategyName` (camelCase, unquoted in SQL).

In PostgreSQL wird ein unquoted Identifier automatisch zu lowercase. Also wird `SELECT strategyName FROM ...` tatsaechlich zu `SELECT strategyname FROM ...` — das **funktioniert**. Aber es ist verwirrend und fragil:

1. Der RowMapper liest `rs.getObject("strategyName", String.class)` — das funktioniert weil JDBC case-insensitive Spalten-Lookup macht.
2. Das `INSERT INTO ... strategyName ...` wird von PostgreSQL als `strategyname` interpretiert.

**Empfehlung:** Alle SQL-Referenzen im Java-Code auf `strategyname` (lowercase) aendern fuer Klarheit und Konsistenz. Alternativ: Spalte via Alias umbenennen zu `strategy_name` (snake_case wie alle anderen Spalten).

---

### BUG — `PostgreWorksheetDao.findByOwnerId` referenziert nicht-existente Spalte `hint_credits_per_task`

**Datei:** `backend/.../persistence/dao/PostgreWorksheetDao.java:289-294`

```sql
SELECT w.id, w.name, w.description, w.owner_id,
       w.hints_enabled, w.hint_credits_per_task,
       COUNT(ws.sequence_id) AS sequence_count
FROM worksheets w ...
GROUP BY w.id, w.name, w.description, w.owner_id, w.hints_enabled
```

Die Spalte `hint_credits_per_task` existiert nicht im Schema (`db/init/z_codeowl_schema.sql`). Die `worksheets`-Tabelle hat nur `id`, `name`, `description`, `owner_id`, `hints_enabled`.

**Zusaetzlich:** Die GROUP BY-Klausel enthaelt `hint_credits_per_task` nicht, aber sie ist im SELECT. Das wird einen SQL-Fehler werfen.

**Und:** Die Spalte `sequence_count` wird gelesen aber nirgends im RowMapper gemappt — sie geht verloren.

**Empfehlung:** `hint_credits_per_task` aus dem SELECT entfernen (oder die Spalte im Schema hinzufuegen, falls AP13 Credits geplant). `sequence_count` optional auf das DTO mappen oder entfernen.

---

### WARN — `PostgreTaskDao.loadTasksBySequenceId` nutzt `SELECT *`

**Datei:** `backend/.../persistence/dao/PostgreTaskDao.java:78-83`

```sql
SELECT * FROM tasks WHERE sequence_id = ?
```

Alle anderen Queries listen Spalten explizit auf. `SELECT *` ist fragil — wenn eine Spalte hinzukommt, koennte der RowMapper brechen (oder umgekehrt).

**Empfehlung:** Explizite Spaltenliste wie in `loadById`.

---

### WARN — Fehlende `created_at` in SequenceDAO SELECT-Queries fuer `findByOwnerId`

**Datei:** `backend/.../persistence/dao/PostgreSequenceDAO.java:178-193`

Die `findByOwnerId`-Query selektiert kein `created_at`, benutzt aber auch nicht den Standard-RowMapper (sondern einen Inline-Lambda-Builder). Das `createdAt`-Feld bleibt `null` im DTO. Das ist kein Crash, aber inkonsistent — die `loadAll` und `loadById` Queries liefern `created_at`.

**Empfehlung:** `created_at` in findByOwnerId aufnehmen und mappen.

---

### STYLE — Fehlende Indizes auf `concept_curriculum_topic`

**Datei:** `db/init/z_codeowl_schema.sql`

Die `concept_curriculum_topic` Join-Tabelle (definiert in `codeowl_backup.sql`) hat keinen Index auf `topic_id`. Queries wie `findAllByCurriculumTopic` (JOIN on `topic_id`) profitieren davon.

**Empfehlung:** `CREATE INDEX IF NOT EXISTS idx_concept_curriculum_topic_topic ON concept_curriculum_topic(topic_id);`

---

## 3. Service Layer

### WARN — `CurriculumGenerationService.generate` persistiert nicht automatisch

**Datei:** `backend/.../application/service/CurriculumGenerationService.java:48-99`

Im Gegensatz zu `TaskGeneratorService.generate`, das intern `taskPersistenceService.persistSequence()` aufruft, tut `CurriculumGenerationService.generate` dies **nicht**. Der `CurriculumController` muss explizit `taskPersistenceService.persistSequence(seq)` aufrufen.

Das funktioniert, ist aber inkonsistent — ein Entwickler koennte erwarten, dass `generate` wie bei `TaskGeneratorService` die Sequenz auch speichert.

**Empfehlung:** Entweder Persistenz in den CurriculumGenerationService verlagern (konsistent mit TaskGeneratorService) oder explizit dokumentieren warum nicht.

---

### STYLE — Overload-Chain in `TaskGeneratorService` ist schwer wartbar

**Datei:** `backend/.../application/service/TaskGeneratorService.java:68-128`

Es gibt 4 Overloads von `tryGenerate` und 4 Overloads von `generate`, die sich kaskadierend aufrufen:

```
tryGenerate(6 params) → tryGenerate(7 params) → tryGenerate(8 params) → tryGenerate(9 params)
generate(7 params)    → generate(8 params)    → generate(9 params)    → generate(10 params)
```

Jede neue Feature-Flag fuegt eine weitere Overload-Stufe hinzu. Das Pattern wurde bewusst gewaehlt (dokumentiert als "Overload-Chain"), aber bei 10+ Parametern wird es unuebersichtlich.

**Empfehlung:** Langfristig ein `GenerationRequest`-Builder-Objekt als einzigen Parameter einfuehren, das alle optionalen Felder kapselt.

---

### WARN — Kein Retry-Loop in `CurriculumGenerationService`

**Datei:** `backend/.../application/service/CurriculumGenerationService.java:82-84`

`TaskGeneratorService.generate` hat einen konfigurierbaren Retry-Loop (`maxAttempts: 3`). Die Curriculum-Variante hat keinen — ein einziger API-Fehler schlaegt direkt durch.

**Empfehlung:** Retry-Logik analog zu TaskGeneratorService einbauen, oder zumindest einen einzelnen Retry bei IOException.

---

## 4. API Controllers / Security

### WARN — `HintController` hat Layer-Violation: direkter `JdbcTemplate`-Zugriff

**Datei:** `backend/.../api/HintController.java:22,42-57`

Der Controller injiziert direkt `JdbcTemplate` und fuehrt eine SQL-Query aus:

```java
private final JdbcTemplate jdbc;
...
String sql = "SELECT COALESCE(w.hints_enabled, true) ...";
Boolean enabled = jdbc.queryForObject(sql, Boolean.class, taskId);
```

Das ist eine Verletzung der Schichtenarchitektur — Controllers sollten nur Services aufrufen, nie direkt die DB.

**Empfehlung:** `getHintConfig`-Logik in HintService oder WorksheetService auslagern.

---

### WARN — `CurriculumController` hat kein `@CrossOrigin` oder CORS ist anderweitig konfiguriert

**Datei:** `backend/.../api/CurriculumController.java:13`

Der Controller hat `@CrossOrigin(origins = "http://localhost:5175")`. Das ist identisch mit `TasksController`, aber hartcodiert. In Produktion muss das angepasst werden.

**Empfehlung:** CORS global via `WebMvcConfigurer` oder Security-Config statt per-Controller `@CrossOrigin`. Die hartcodierte Origin `localhost:5175` funktioniert nur im Dev-Modus.

---

### STYLE — Inkonsistente CORS-Konfiguration

`TasksController` und `CurriculumController` haben `@CrossOrigin(origins = "http://localhost:5175")`, aber `AuthController`, `HintController`, `ClassController`, `LearningEventController` haben **keinen** `@CrossOrigin`. Entweder ist CORS global konfiguriert (dann sind die Annotationen redundant) oder es fehlt bei den anderen Controllern.

---

### STYLE — `AuthController` hat hartcodierte deutsche Strings in Responses

**Datei:** `backend/.../api/AuthController.java:40-41, 75, 84, 113-114`

```java
return ResponseEntity.ok(Map.of(
    "message", "Registrierung erfolgreich. Bitte bestaetige deine Email-Adresse."
));
```

Diese Strings werden direkt an das Frontend geliefert. Das Frontend nutzt i18n — die Backend-Nachrichten sollten Schluesselbegriffe oder Codes sein, keine lokalisierten Texte.

---

### WARN — `CurriculumController` fehlt Error-Response bei nicht gefundenem Topic

**Datei:** `backend/.../api/CurriculumController.java:61-90`

Wenn `CurriculumGenerationService.generate` eine `IllegalArgumentException` wirft (Topic nicht gefunden), kommt eine unformatierte 500-Response. Besser waere ein expliziter 404.

**Empfehlung:** Try-catch mit `ResponseEntity.notFound()` fuer `IllegalArgumentException`.

---

## 5. AI / Prompt Infrastructure

### STYLE — Doppelter ObjectMapper in `OpenAiClient`

**Datei:** `backend/.../infrastructure/ai/openai/OpenAiClient.java:63`

```java
private final ObjectMapper mapper = new ObjectMapper();
```

Der `PromptBuilder` bekommt bereits einen `ObjectMapper` via Constructor Injection (Spring Bean). `OpenAiClient` erstellt einen zweiten, nicht-konfigurierten. Falls Jackson-Konfigurationen (z.B. `FAIL_ON_UNKNOWN_PROPERTIES=false`) nur am Spring-Bean gesetzt sind, verhaelt sich der Client-eigene Mapper anders.

**Empfehlung:** Den Spring-managed `ObjectMapper` durchreichen statt einen neuen zu erstellen.

---

### STYLE — `@TODO` Kommentare in Javadoc

**Datei:** `backend/.../infrastructure/ai/openai/OpenAiClient.java:122`

```java
* @param contract TODO
```

Unvollstaendige Dokumentation.

---

## 6. Frontend (React / TypeScript)

### WARN — `SolvePage`: API-Endpoint `/task/${taskId}` existiert nicht

**Datei:** `frontend/src/SolvePage.tsx:34`

```typescript
api.get(`/task/${taskId}`).then(({ data }) => setLanguage(data.programmingLanguage));
```

Der Backend-Endpoint heisst `/api/tasks/{taskId}` (mit 's'), nicht `/api/task/{taskId}`. Dieser Call wuerde einen 404 werfen. Allerdings ist der `language` State meist bereits via `location.state?.lang` gesetzt, also faellt der Bug nur auf wenn direkt auf `/solve/:taskId` navigiert wird (z.B. Bookmark/Direktlink).

**Empfehlung:** Pfad auf `/tasks/${taskId}` korrigieren.

---

### WARN — `HomePage` useEffect Dependency-Array in Keyboard-Handler

**Datei:** `frontend/src/HomePage.tsx:71-79`

```typescript
useEffect(() => {
    const onKey = (e: KeyboardEvent) => { ... void handleSubmit(...) };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
}, [loading, codeSnippet, problemStatement, language, concepts]);
```

`handleSubmit` ist nicht in den Dependencies, was zu einer stale Closure fuehren kann. Ausserdem fehlen `distractorsEnabled`, `tierCount`, `blacklist` etc. in den Dependencies — wenn sich diese aendern, nutzt der Handler die alten Werte.

**Empfehlung:** `handleSubmit` mit `useCallback` wrappen und in die Dependency-Liste aufnehmen, oder alle relevanten States einschliessen.

---

### STYLE — `SolvePage` nutzt jQuery (`$`) und `ParsonsWidget`

**Datei:** `frontend/src/SolvePage.tsx:6`

```typescript
import $ from "jquery";
```

In einer modernen React-App ist jQuery ein Fremdkoerper. Es wird nur fuer die ParsonsWidget-Integration verwendet (externe Bibliothek). Das ist ein bekanntes Constraint, aber langfristig ein Refactoring-Kandidat.

---

### STYLE — `any` Type-Casts in Error-Handlern

Mehrere Stellen nutzen `catch (e: any)` statt typisierter Error-Handling:

- `frontend/src/HomePage.tsx` (mehrfach)
- `frontend/src/pages/ClassesPage.tsx:36`
- `frontend/src/pages/CurriculumPage.tsx` (mehrfach)

**Empfehlung:** Axios-Error-Typen nutzen: `import { AxiosError } from 'axios'` + `catch (e: unknown)` mit Typguard.

---

### STYLE — `AuthContext` validiert Token-Expiration nicht

**Datei:** `frontend/src/context/AuthContext.tsx:28-41`

Beim Mount wird der Token aus `localStorage` geladen, aber nicht geprueft ob er noch gueltig ist. Der Axios-Interceptor in `api.ts` faengt 401-Responses ab, aber die initiale Ladephase zeigt den User als eingeloggt, bis die erste API-Call fehlschlaegt.

**Empfehlung:** Beim Mount einen `/auth/me` Call machen um den Token zu validieren (oder JWT client-seitig auf Expiration pruefen).

---

## 7. Architektur & Cross-Cutting Concerns

### BUG — Schema-Cleanup droppt `"strategyName"` aber Code referenziert es noch

Wie in Sektion 2 beschrieben: Die Cleanup-Migration (Sektion 12 in `z_codeowl_schema.sql`) droppt die quoted Spalte. Der Backup erstellt sie als unquoted `strategyname`. Der Code nutzt `strategyName` in SQL. Das funktioniert nur weil PostgreSQL unquoted Identifier case-folds.

Wenn jemand ein frisches Schema ohne den Backup aufbaut, existiert die Spalte moeglicherweise gar nicht — sie wird in `codeowl_backup.sql` als Teil der `tiered_task_sequences`-Tabelle definiert, nicht im Extension-Schema.

**Empfehlung:** `CREATE TABLE IF NOT EXISTS tiered_task_sequences` sollte im Extension-Schema existieren (oder ein explizites `ALTER TABLE ADD COLUMN IF NOT EXISTS strategyname`) um nicht vom Backup abhaengig zu sein.

---

### STYLE — Inkonsistente Naming-Konventionen: DAO-Klassennamen

| Klasse | Namensschema |
|--------|--------------|
| `PostgreSequenceDAO` | `Postgre` + PascalCase + `DAO` |
| `PostgreTaskDao` | `Postgre` + PascalCase + `Dao` |
| `PostgreWorksheetDao` | `Postgre` + PascalCase + `Dao` |
| `PostgreCurriculumTopicDAO` | `Postgre` + PascalCase + `DAO` |
| `CurriculumCodeExampleDAO` | Kein `Postgre`-Prefix + `DAO` |
| `PostgreConceptCurriculumTopicDAO` | `Postgre` + PascalCase + `DAO` |

Drei verschiedene Patterns: `DAO` vs `Dao`, und `Postgre`-Prefix fehlt bei `CurriculumCodeExampleDAO`.

**Empfehlung:** Einheitlich `Postgre*Dao` (oder `Postgres*Dao`).

---

### STYLE — `Postgre` statt `Postgres` Prefix

Alle DAOs heissen `Postgre*` statt `Postgres*`. Das ist ein Tippfehler der sich durchzieht. Konsistent falsch ist besser als inkonsistent, aber bei Gelegenheit korrigierbar.

---

### STYLE — `domain/entities` enthaelt DTOs, keine echten Domain-Entities

Das Package `domain.entities` enthaelt ausschliesslich DTOs (Datencontainer mit Gettern/Settern). Ein echtes Domain-Driven-Design haette hier Aggregate mit Business-Logik. Da das Projekt aber kein DDD verfolgt, ist das funktional okay — das Package koennte aber `domain.dto` heissen fuer Klarheit.

---

### WARN — Hartcodierter JWT-Secret in `application.yml`

**Datei:** `backend/src/main/resources/application.yml:29`

```yaml
secret: ${JWT_SECRET:defaultDevSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm!!}
```

Der Default-Wert ist ein statischer String. Wenn `JWT_SECRET` nicht als Umgebungsvariable gesetzt wird, nutzen alle Instanzen denselben Secret. In Produktion ist das ein Sicherheitsrisiko.

**Empfehlung:** In Produktion keinen Default-Wert haben — Startup abbrechen wenn `JWT_SECRET` nicht gesetzt.

---

### STYLE — `docker-compose.yml`: Frontend Healthcheck nutzt `wget` auf nicht-existenten `/health` Endpoint

**Datei:** `docker-compose.yml:64`

```yaml
test: ["CMD", "wget", "-qO-", "http://localhost/health"]
```

Das Frontend ist ein statisches Vite-Build, es gibt keinen `/health` Endpoint. Der Healthcheck wuerde immer fehlschlagen (404 oder Connection Refused je nach nginx-Config).

**Empfehlung:** Auf root-Path pruefen: `wget -qO- http://localhost/` oder entfernen.

---

### STYLE — Mehrere `@CrossOrigin` Hardcoded Origins

Statt per-Controller `@CrossOrigin(origins = "http://localhost:5175")` sollte CORS zentral konfiguriert sein. Die `docker-compose.yml` hat bereits `CORS_ALLOWED_ORIGINS` als Umgebungsvariable — das deutet darauf hin, dass eine globale Config existiert oder geplant ist. Die Controller-Annotationen ueberschreiben dann moeglicherweise die globale Config.

---

## 8. Zusammenfassung nach Prioritaet

### Sofort fixen (BUGs)

1. **`strategyName` Spaltenname** — funktioniert durch Zufall (PostgreSQL case-folding), aber extrem verwirrend und fragil
2. **`hint_credits_per_task` in WorksheetDao** — Query wird mit 100% Sicherheit fehlschlagen wenn `findByOwnerId` aufgerufen wird
3. **`ConceptCurriculumTopicDTO.curriculumId`** — semantisch falscher Feldname, Quelle fuer zukuenftige Bugs

### Bald fixen (WARNs)

4. **`SolvePage` API-Pfad `/task/` statt `/tasks/`** — 404 bei Direktnavigation
5. **`HintController` JdbcTemplate** — Layer Violation
6. **CORS-Konfiguration vereinheitlichen**
7. **`CurriculumGenerationService` Retry-Logik** — ein API-Fehler terminiert die Generierung
8. **JWT-Secret Default entfernen** in Produktion
9. **Frontend Token-Validation beim Mount**
10. **`CurriculumController` Error-Handling** fuer fehlende Topics

### Bei Gelegenheit (STYLE)

11. DAO-Namenskonventionen vereinheitlichen
12. Lombok-Pattern vereinheitlichen
13. `TaskGeneratorService` Overload-Chain refactoren zu Request-Objekt
14. `SELECT *` durch explizite Spaltenlisten ersetzen
15. `any`-Casts im Frontend durch typisiertes Error-Handling ersetzen
16. jQuery-Abhaengigkeit langfristig entfernen
17. Frontend Healthcheck in docker-compose fixen
18. Deutsche Strings in AuthController durch Codes ersetzen
19. `NUM_OF_TASKS`-Konstante entfernen
20. `findByOwnerId` fehlende `created_at`-Spalte ergaenzen

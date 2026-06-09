# Arbeitspaket: Admin Database Access (Backup + Research Export)

> **Status:** Geplant, nicht implementiert
> **Geschätzte Komplexität:** Mittel (4 Phasen, ~15 neue Dateien, ~6 Modifikationen)
> **Priorität:** Hoch (blockiert Daten-Analyse für Paper)

---

## 0. Kontext für den Agent

### Was ist CodeOwl?
Full-Stack-Bildungsplattform für Parsons-Programmieraufgaben. Lehrkräfte generieren KI-gestützte Aufgabensequenzen, Schüler lösen sie als Drag-&-Drop-Puzzles. Backend in Spring Boot 3.x mit Clean Architecture, Frontend in React/TypeScript.

### Tech-Stack
- **Backend:** Spring Boot 3.3, Spring JDBC (kein Hibernate für die Persistenz, nur JdbcTemplate), PostgreSQL 17, JJWT, Lombok, Argon2-Passwort-Hashing
- **Frontend:** React 18, TypeScript, Vite, react-router-dom v6, react-i18next, Bootstrap 5, Axios
- **Deployment:** Docker Compose. DB-Container `codeowl-clean-db` (postgres:17-alpine), Backend `codeowl-clean-backend` (eclipse-temurin:21-jdk auf Debian-Basis), Frontend `codeowl-clean-frontend` (nginx)
- **Production-Server:** Scratch-Server der Uni Passau, läuft hinter `https://scratch.fim.uni-passau.de/codeowl/`. Direkter SSH-Zugriff existiert NICHT — daher dieses Arbeitspaket

### Projektpfade
- **Repo-Root:** `C:\Users\default.MSI\Desktop\EverythingZulaRelated\2-thesis-repo\cisternino-ba\codeowl-clean`
- **Backend-Source:** `backend/src/main/java/com/cisternino/`
- **Backend-Resources:** `backend/src/main/resources/`
- **Frontend-Source:** `frontend/src/`
- **DB-Init:** `db/init/` (Scripts laufen alphabetisch beim ersten Start des Postgres-Containers)

### Backend-Package-Layout (Clean Architecture)
```
com.cisternino
├── api/                              ← REST-Layer (Controller, DTOs)
│   ├── AdminController.java          ← bestehend, /api/admin/users etc.
│   ├── AdminTagController.java       ← bestehend
│   └── dto/                          ← Request/Response DTOs
├── application/                      ← Business-Layer (Services, Use-Cases)
│   └── service/
│       ├── AdminService.java         ← bestehend
│       └── ...
├── domain/                           ← Pure Domain (keine Spring-Abhängigkeiten in Interfaces)
│   ├── entities/                     ← DTOs/Domain-Objekte mit Lombok
│   ├── interfaces/                   ← Repository-Interfaces
│   └── exceptions/
└── infrastructure/                   ← I/O, Persistence, externe Calls
    └── persistence/
        ├── dao/                      ← PostgreSQL-Implementierungen (JdbcTemplate)
        └── mapper/                   ← RowMapper für JdbcTemplate
```

**Wichtig — Architektur-Konventionen:**
- Controller dürfen keine Business-Logik enthalten (nur HTTP-Routing + Auth-Check via SecurityConfig)
- Services sind transaktional (`@Transactional` wenn nötig)
- DAOs sind die einzigen Stellen die `JdbcTemplate` injecten
- Domain-Interfaces (`*Repository`) hängen nicht von Spring ab → testbar
- DTOs nutzen Lombok `@Builder`, `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`
- Exception-Handling: `GlobalExceptionHandler` (im `api/`-Package) übersetzt Domain-Exceptions zu HTTP-Statuscodes
- Logging: `lombok.extern.slf4j.Slf4j` auf Service-Klassen, `log.info(...)` für Admin-Aktionen

### Auth-Konventionen
- JWT in `Authorization: Bearer <token>`-Header
- `@AuthenticationPrincipal UUID userId` im Controller injiziert die User-ID aus dem JWT
- `/api/admin/**` ist in `SecurityConfig` bereits per `hasRole("ADMIN")` geschützt — neue Admin-Endpunkte unter diesem Pfad brauchen keine extra Auth-Annotation
- Aktuelle Admin-Aktionen werden via `log.info("Admin ...")` geloggt, aber NICHT in einer Audit-Tabelle persistiert

---

## 1. Ziel & Scope dieses Arbeitspakets

**Problem:** Der Production-Server ist über die Uni-Infrastruktur unzugänglich. Direkter SSH oder `psql`-Zugriff existiert nicht. Für die Bachelorarbeit / das daraus entstehende Paper braucht der Admin (Luca):
1. **Vollständiges DB-Backup** zur Sicherung und ggf. lokalen Wiederherstellung
2. **Forschungsdaten-Export** (CSV-Bundle) für Pandas/R-Analyse, inklusive `learning_events`, `feedback_logs`, `hint_usage` etc. — pseudonymisiert (UUIDs statt Klarnamen)

**Lösung:** Zwei neue Admin-Endpoints, beide streamen Datei-Downloads direkt im Browser:
- `POST /api/admin/backup` → `.dump`-Datei (pg_dump custom format)
- `GET  /api/admin/research-export` → `.zip` mit CSVs

**Nicht-Ziele:**
- Generic SQL-Query-API (zu hohes Sicherheitsrisiko)
- Automatische periodische Backups (out of scope, on-demand reicht)
- Backup-Versionierung im Server (Admin verwaltet Backups lokal)
- DSGVO-konforme Lösch-/Auskunftsworkflows (separate Phase, später)

---

## 2. Bereits getroffene Design-Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Pseudonymisierung im Research-Export | **UUID 1:1 behalten**, aber `users`-PII komplett strippen | Klein-Projekt, UUIDs sind ausreichend stabile Pseudonyme |
| Backup-Frequenz | **Nur on-demand** | Production-DB ist klein (~30 User, ~1000 Sequenzen) |
| Backup-Storage | **Nicht serverseitig speichern** | Stream → Browser → Admin-Laptop |
| Mehrere Admins | **Erlaubt**, alle sehen alle Audit-Einträge | Admins sind gegenseitig vertrauenswürdig |
| Streaming-Architektur | **In-Memory** akzeptabel | DB so klein dass `setFetchSize` overkill wäre |
| Single-Flight-Lock | **Ja, mit 30s Timeout** für Full-Backup | Schützt vor parallelen `pg_dump`-Aufrufen |
| Backup-Verschlüsselung | **Nein, vorerst nicht** | Production-Verbindung ist HTTPS, Admin-Laptop ist Trust-Boundary |
| GDPR | **Später, separates AP** | Notiz im Code als TODO |
| Generic Query-API | **Verworfen** | SQL-Injection-Risiko, schlechte ROI |

---

## 3. Datenbank-Änderungen

### 3.1 Neue Tabelle: `admin_audit_log`

**Migrations-Datei:** `db/init/zzz_migration_admin_audit_log.sql`

```sql
-- ════════════════════════════════════════════════════════════
-- Migration: admin_audit_log table
-- Tracks all admin-initiated database access operations
-- (full backups, research exports, future high-sensitivity ops)
-- Idempotent — safe to run on existing DBs and fresh installs.
-- ════════════════════════════════════════════════════════════
SET client_encoding = 'UTF8';

DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'admin_audit_log'
    ) THEN
        CREATE TABLE admin_audit_log (
            id          BIGSERIAL PRIMARY KEY,
            admin_id    UUID NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
            action      VARCHAR(64) NOT NULL,
            ip_address  VARCHAR(45),
            user_agent  TEXT,
            bytes_sent  BIGINT,
            rows_sent   BIGINT,
            success     BOOLEAN NOT NULL,
            error_msg   TEXT,
            created_at  TIMESTAMP NOT NULL DEFAULT NOW()
        );
        CREATE INDEX idx_admin_audit_admin_time
            ON admin_audit_log(admin_id, created_at DESC);
        CREATE INDEX idx_admin_audit_action_time
            ON admin_audit_log(action, created_at DESC);
    END IF;
END $$;
```

**Mögliche `action`-Werte (string-Konstanten im Code):**
- `FULL_BACKUP`
- `RESEARCH_EXPORT`
- (zukünftig: `USER_DELETE`, `BULK_RETAG` etc. — out of scope dieses APs)

### 3.2 Production-Migration

Da die Init-Scripts nur auf einer fresh DB laufen, muss der Admin auf dem laufenden Server einmalig ausführen:

```bash
docker exec -i codeowl-clean-db psql -U postgres -d codeowl < db/init/zzz_migration_admin_audit_log.sql
```

Das Script ist idempotent — kann auch auf fresh installs durchlaufen, ohne Schaden anzurichten.

---

## 4. Backend-Implementierung

### 4.1 Phase 1 — Audit-Infrastruktur

#### 4.1.1 Neue Datei: `backend/src/main/java/com/cisternino/domain/entities/AdminAuditLogDTO.java`

```java
package com.cisternino.domain.entities;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogDTO {
    private Long id;
    private UUID adminId;
    private String action;        // "FULL_BACKUP" | "RESEARCH_EXPORT"
    private String ipAddress;
    private String userAgent;
    private Long bytesSent;       // nullable
    private Long rowsSent;        // nullable
    private boolean success;
    private String errorMsg;      // nullable
    private LocalDateTime createdAt;

    /** Action constants — use these instead of magic strings. */
    public static final class Action {
        public static final String FULL_BACKUP      = "FULL_BACKUP";
        public static final String RESEARCH_EXPORT  = "RESEARCH_EXPORT";
        private Action() {}
    }
}
```

#### 4.1.2 Neue Datei: `backend/src/main/java/com/cisternino/domain/interfaces/AdminAuditLogRepository.java`

```java
package com.cisternino.domain.interfaces;

import entities.domain.de.uni_passau.fim.se2.AdminAuditLogDTO;
import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository {
    AdminAuditLogDTO save(AdminAuditLogDTO entry);
    List<AdminAuditLogDTO> findRecent(int limit);
    List<AdminAuditLogDTO> findByAdminId(UUID adminId, int limit);
}
```

#### 4.1.3 Neue Datei: `backend/src/main/java/com/cisternino/infrastructure/persistence/mapper/AdminAuditLogRowMapper.java`

```java
package com.cisternino.infrastructure.persistence.mapper;

import entities.domain.de.uni_passau.fim.se2.AdminAuditLogDTO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class AdminAuditLogRowMapper implements RowMapper<AdminAuditLogDTO> {
    @Override
    public AdminAuditLogDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        long bytesSent = rs.getLong("bytes_sent");
        long rowsSent  = rs.getLong("rows_sent");
        return AdminAuditLogDTO.builder()
                .id(rs.getLong("id"))
                .adminId(UUID.fromString(rs.getString("admin_id")))
                .action(rs.getString("action"))
                .ipAddress(rs.getString("ip_address"))
                .userAgent(rs.getString("user_agent"))
                .bytesSent(rs.wasNull() ? null : bytesSent)
                .rowsSent(rs.wasNull() ? null : rowsSent)
                .success(rs.getBoolean("success"))
                .errorMsg(rs.getString("error_msg"))
                .createdAt(created != null ? created.toLocalDateTime() : null)
                .build();
    }
}
```

#### 4.1.4 Neue Datei: `backend/src/main/java/com/cisternino/infrastructure/persistence/dao/PostgreAdminAuditLogDao.java`

```java
package com.cisternino.infrastructure.persistence.dao;

import entities.domain.de.uni_passau.fim.se2.AdminAuditLogDTO;
import interfaces.domain.de.uni_passau.fim.se2.AdminAuditLogRepository;
import mapper.persistence.infrastructure.de.uni_passau.fim.se2.AdminAuditLogRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgreAdminAuditLogDao implements AdminAuditLogRepository {

    private final JdbcTemplate jdbc;
    private final AdminAuditLogRowMapper rowMapper;

    @Override
    public AdminAuditLogDTO save(AdminAuditLogDTO entry) {
        if (entry.getCreatedAt() == null) entry.setCreatedAt(LocalDateTime.now());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO admin_audit_log
                  (admin_id, action, ip_address, user_agent, bytes_sent, rows_sent, success, error_msg, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, entry.getAdminId());
            ps.setString(2, entry.getAction());
            ps.setString(3, entry.getIpAddress());
            ps.setString(4, entry.getUserAgent());
            if (entry.getBytesSent() != null) ps.setLong(5, entry.getBytesSent()); else ps.setNull(5, java.sql.Types.BIGINT);
            if (entry.getRowsSent()  != null) ps.setLong(6, entry.getRowsSent());  else ps.setNull(6, java.sql.Types.BIGINT);
            ps.setBoolean(7, entry.isSuccess());
            ps.setString(8, entry.getErrorMsg());
            ps.setTimestamp(9, Timestamp.valueOf(entry.getCreatedAt()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) entry.setId(key.longValue());
        return entry;
    }

    @Override
    public List<AdminAuditLogDTO> findRecent(int limit) {
        return jdbc.query(
                "SELECT * FROM admin_audit_log ORDER BY created_at DESC LIMIT ?",
                rowMapper, limit
        );
    }

    @Override
    public List<AdminAuditLogDTO> findByAdminId(UUID adminId, int limit) {
        return jdbc.query(
                "SELECT * FROM admin_audit_log WHERE admin_id = ? ORDER BY created_at DESC LIMIT ?",
                rowMapper, adminId, limit
        );
    }
}
```

#### 4.1.5 Neue Datei: `backend/src/main/java/com/cisternino/application/service/AdminAuditService.java`

```java
package com.cisternino.application.service;

import entities.domain.de.uni_passau.fim.se2.AdminAuditLogDTO;
import interfaces.domain.de.uni_passau.fim.se2.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for logging admin actions to admin_audit_log.
 * Separated from AdminService to avoid mixing concerns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {

    private final AdminAuditLogRepository repository;

    /**
     * Records a successful admin action.
     */
    public void logSuccess(UUID adminId, String action, String ip, String userAgent,
                           Long bytesSent, Long rowsSent) {
        save(adminId, action, ip, userAgent, bytesSent, rowsSent, true, null);
    }

    /**
     * Records a failed admin action with an error message.
     */
    public void logFailure(UUID adminId, String action, String ip, String userAgent,
                           String errorMsg) {
        save(adminId, action, ip, userAgent, null, null, false, errorMsg);
    }

    public List<AdminAuditLogDTO> getRecent(int limit) {
        return repository.findRecent(Math.min(Math.max(limit, 1), 500));
    }

    private void save(UUID adminId, String action, String ip, String userAgent,
                      Long bytesSent, Long rowsSent, boolean success, String errorMsg) {
        try {
            repository.save(AdminAuditLogDTO.builder()
                    .adminId(adminId)
                    .action(action)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .bytesSent(bytesSent)
                    .rowsSent(rowsSent)
                    .success(success)
                    .errorMsg(errorMsg)
                    .build());
        } catch (Exception e) {
            // Audit-Logging darf NIE den eigentlichen Request brechen.
            log.error("Failed to write admin audit log entry: action={}, admin={}, error={}",
                    action, adminId, e.getMessage());
        }
    }
}
```

---

### 4.2 Phase 2 — Research Export (CSV-ZIP)

#### 4.2.1 Strategie für PII-Sanitization

**Tabellen die exportiert werden:**

| Tabelle | Was rausschneiden? |
|---|---|
| `users` | NUR `user_id, role, is_verified, created_at` exportieren — **NICHT** `email`, `first_name`, `last_name`, `password_hash`, `updated_at` |
| `learning_events` | komplett (`user_id` als Pseudonym, `event_data` als JSON) |
| `feedback_logs` | komplett |
| `hint_usage` | komplett |
| `task_change_analyses` | komplett (kein PII drin) |
| `problem_statement_ratings` | komplett |
| `tasks` | komplett |
| `tiered_task_sequences` | komplett (owner_id als Pseudonym) |
| `tiered_task_sequence_tasks` | komplett (Junction Table) |
| `concepts` | komplett |
| `tiered_task_sequence_concepts` | komplett |
| `worksheets` | komplett (owner_id als Pseudonym) |
| `classes` | komplett (owner_id als Pseudonym, name = Klassenname → kein PII von Schülern) |
| `class_students` | komplett |
| `class_worksheets` | komplett |
| `sequence_tags` | komplett |
| `curriculum_topics` | komplett |
| `curriculum_code_examples` | komplett |
| `concept_curriculum_topic` | komplett |
| **NICHT exportiert** | `auth_tokens` (sensitive), `admin_audit_log` (interna) |

#### 4.2.2 Helfer-Klasse: CSV-Writer

**Neue Datei:** `backend/src/main/java/com/cisternino/application/util/CsvWriter.java`

```java
package com.cisternino.application.util;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal RFC 4180 CSV writer.
 * - UTF-8 encoded (no BOM, since Excel can handle it via region settings)
 * - Field separator: comma
 * - Quoting: double-quotes when field contains comma, quote, or newline
 * - Quote escaping: doubled quote ("")
 */
public class CsvWriter implements AutoCloseable {

    private final PrintWriter out;
    private boolean firstFieldOnLine = true;

    public CsvWriter(OutputStream stream) {
        this.out = new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
    }

    public void writeRow(List<?> fields) {
        for (Object f : fields) writeField(f);
        endRow();
    }

    public void writeField(Object value) {
        if (!firstFieldOnLine) out.print(',');
        firstFieldOnLine = false;
        if (value == null) return; // empty field
        String s = value.toString();
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            out.print('"');
            out.print(s.replace("\"", "\"\""));
            out.print('"');
        } else {
            out.print(s);
        }
    }

    public void endRow() {
        out.print("\r\n");
        firstFieldOnLine = true;
    }

    public void flush() { out.flush(); }

    @Override public void close() { out.flush(); /* underlying stream not closed (caller controls ZipOutputStream) */ }
}
```

#### 4.2.3 Reader-Interface

**Neue Datei:** `backend/src/main/java/com/cisternino/domain/interfaces/ResearchDataReader.java`

```java
package com.cisternino.domain.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Read-only access to research-relevant tables.
 * Each method returns a list of column-name → value maps.
 * Implementations MUST exclude PII columns from the users table.
 */
public interface ResearchDataReader {
    /** users: only user_id, role, is_verified, created_at — NO email/name/hash. */
    List<Map<String, Object>> readUsersPseudonymized();

    List<Map<String, Object>> readLearningEvents();
    List<Map<String, Object>> readFeedbackLogs();
    List<Map<String, Object>> readHintUsage();
    List<Map<String, Object>> readTaskChangeAnalyses();
    List<Map<String, Object>> readProblemStatementRatings();
    List<Map<String, Object>> readTasks();
    List<Map<String, Object>> readTieredTaskSequences();
    List<Map<String, Object>> readSequenceTaskJunction();
    List<Map<String, Object>> readConcepts();
    List<Map<String, Object>> readSequenceConceptJunction();
    List<Map<String, Object>> readWorksheets();
    List<Map<String, Object>> readClasses();
    List<Map<String, Object>> readClassStudents();
    List<Map<String, Object>> readClassWorksheets();
    List<Map<String, Object>> readSequenceTags();
    List<Map<String, Object>> readCurriculumTopics();
    List<Map<String, Object>> readCurriculumCodeExamples();
    List<Map<String, Object>> readConceptCurriculumTopicJunction();
}
```

#### 4.2.4 DAO

**Neue Datei:** `backend/src/main/java/com/cisternino/infrastructure/persistence/dao/PostgreResearchDataDao.java`

```java
package com.cisternino.infrastructure.persistence.dao;

import interfaces.domain.de.uni_passau.fim.se2.ResearchDataReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PostgreResearchDataDao implements ResearchDataReader {

    private final JdbcTemplate jdbc;

    @Override
    public List<Map<String, Object>> readUsersPseudonymized() {
        // Whitelist columns explicitly. NEVER SELECT *.
        return jdbc.queryForList(
                "SELECT user_id, role, is_verified, created_at FROM users ORDER BY created_at"
        );
    }

    @Override public List<Map<String, Object>> readLearningEvents() {
        return jdbc.queryForList("SELECT * FROM learning_events ORDER BY created_at");
    }
    @Override public List<Map<String, Object>> readFeedbackLogs() {
        return jdbc.queryForList("SELECT * FROM feedback_logs ORDER BY clicked_at");
    }
    @Override public List<Map<String, Object>> readHintUsage() {
        return jdbc.queryForList("SELECT * FROM hint_usage ORDER BY created_at");
    }
    @Override public List<Map<String, Object>> readTaskChangeAnalyses() {
        return jdbc.queryForList("SELECT * FROM task_change_analyses");
    }
    @Override public List<Map<String, Object>> readProblemStatementRatings() {
        return jdbc.queryForList("SELECT * FROM problem_statement_ratings");
    }
    @Override public List<Map<String, Object>> readTasks() {
        return jdbc.queryForList("SELECT * FROM tasks");
    }
    @Override public List<Map<String, Object>> readTieredTaskSequences() {
        return jdbc.queryForList("SELECT * FROM tiered_task_sequences");
    }
    @Override public List<Map<String, Object>> readSequenceTaskJunction() {
        // Verify exact table name in db/init/1_codeowl_backup.sql before using
        return jdbc.queryForList("SELECT * FROM tiered_task_sequence_tasks");
    }
    @Override public List<Map<String, Object>> readConcepts() {
        return jdbc.queryForList("SELECT * FROM concepts");
    }
    @Override public List<Map<String, Object>> readSequenceConceptJunction() {
        return jdbc.queryForList("SELECT * FROM tiered_task_sequence_concepts");
    }
    @Override public List<Map<String, Object>> readWorksheets() {
        return jdbc.queryForList("SELECT * FROM worksheets");
    }
    @Override public List<Map<String, Object>> readClasses() {
        return jdbc.queryForList("SELECT * FROM classes");
    }
    @Override public List<Map<String, Object>> readClassStudents() {
        return jdbc.queryForList("SELECT * FROM class_students");
    }
    @Override public List<Map<String, Object>> readClassWorksheets() {
        return jdbc.queryForList("SELECT * FROM class_worksheets");
    }
    @Override public List<Map<String, Object>> readSequenceTags() {
        return jdbc.queryForList("SELECT * FROM sequence_tags");
    }
    @Override public List<Map<String, Object>> readCurriculumTopics() {
        return jdbc.queryForList("SELECT * FROM curriculum_topics");
    }
    @Override public List<Map<String, Object>> readCurriculumCodeExamples() {
        return jdbc.queryForList("SELECT * FROM curriculum_code_examples");
    }
    @Override public List<Map<String, Object>> readConceptCurriculumTopicJunction() {
        return jdbc.queryForList("SELECT * FROM concept_curriculum_topic");
    }
}
```

> ⚠️ **HINWEIS für den implementierenden Agent:** Die exakten Tabellennamen müssen mit `db/init/1_codeowl_backup.sql` abgeglichen werden — die obigen sind vermutete Namen. Falls eine Tabelle nicht existiert oder anders heißt, anpassen oder den Eintrag ggf. auslassen.

#### 4.2.5 Service

**Neue Datei:** `backend/src/main/java/com/cisternino/application/service/ResearchExportService.java`

```java
package com.cisternino.application.service;

import util.application.de.uni_passau.fim.se2.CsvWriter;
import interfaces.domain.de.uni_passau.fim.se2.ResearchDataReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a ZIP file containing CSVs of all research-relevant tables.
 * Streams directly to the provided OutputStream (HTTP response).
 *
 * The result is a fully PII-stripped, UUID-pseudonymized data dump.
 * GDPR TODO: define formal data retention/deletion policy before publication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResearchExportService {

    private final ResearchDataReader reader;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Streams a ZIP of CSV exports + a metadata file to the given output.
     * Returns the total number of rows written across all tables.
     */
    public long streamExport(OutputStream output, String adminIdShort) throws Exception {
        long totalRows = 0;
        Map<String, Long> rowCounts = new LinkedHashMap<>();

        try (ZipOutputStream zip = new ZipOutputStream(output)) {

            totalRows += writeTable(zip, "users_pseudonymized.csv", reader.readUsersPseudonymized(), rowCounts);
            totalRows += writeTable(zip, "learning_events.csv", reader.readLearningEvents(), rowCounts);
            totalRows += writeTable(zip, "feedback_logs.csv", reader.readFeedbackLogs(), rowCounts);
            totalRows += writeTable(zip, "hint_usage.csv", reader.readHintUsage(), rowCounts);
            totalRows += writeTable(zip, "task_change_analyses.csv", reader.readTaskChangeAnalyses(), rowCounts);
            totalRows += writeTable(zip, "problem_statement_ratings.csv", reader.readProblemStatementRatings(), rowCounts);
            totalRows += writeTable(zip, "tasks.csv", reader.readTasks(), rowCounts);
            totalRows += writeTable(zip, "tiered_task_sequences.csv", reader.readTieredTaskSequences(), rowCounts);
            totalRows += writeTable(zip, "tiered_task_sequence_tasks.csv", reader.readSequenceTaskJunction(), rowCounts);
            totalRows += writeTable(zip, "concepts.csv", reader.readConcepts(), rowCounts);
            totalRows += writeTable(zip, "tiered_task_sequence_concepts.csv", reader.readSequenceConceptJunction(), rowCounts);
            totalRows += writeTable(zip, "worksheets.csv", reader.readWorksheets(), rowCounts);
            totalRows += writeTable(zip, "classes.csv", reader.readClasses(), rowCounts);
            totalRows += writeTable(zip, "class_students.csv", reader.readClassStudents(), rowCounts);
            totalRows += writeTable(zip, "class_worksheets.csv", reader.readClassWorksheets(), rowCounts);
            totalRows += writeTable(zip, "sequence_tags.csv", reader.readSequenceTags(), rowCounts);
            totalRows += writeTable(zip, "curriculum_topics.csv", reader.readCurriculumTopics(), rowCounts);
            totalRows += writeTable(zip, "curriculum_code_examples.csv", reader.readCurriculumCodeExamples(), rowCounts);
            totalRows += writeTable(zip, "concept_curriculum_topic.csv", reader.readConceptCurriculumTopicJunction(), rowCounts);

            // _metadata.json
            zip.putNextEntry(new ZipEntry("_metadata.json"));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            meta.put("adminIdShort", adminIdShort);
            meta.put("totalRows", totalRows);
            meta.put("perTableRowCounts", rowCounts);
            meta.put("schemaVersion", 1);
            meta.put("piiPolicy", "users table: only user_id/role/is_verified/created_at exported. UUIDs as stable pseudonyms.");
            zip.write(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            zip.closeEntry();
        }

        return totalRows;
    }

    private long writeTable(ZipOutputStream zip, String filename, List<Map<String, Object>> rows,
                            Map<String, Long> rowCounts) throws Exception {
        zip.putNextEntry(new ZipEntry(filename));
        try {
            CsvWriter csv = new CsvWriter(zip) {}; // wrap, don't close underlying
            if (rows.isEmpty()) {
                csv.flush();
                rowCounts.put(filename, 0L);
                return 0L;
            }
            // Header from first row's keyset (preserves order via LinkedHashMap from JdbcTemplate)
            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            csv.writeRow(columns);
            for (Map<String, Object> row : rows) {
                List<Object> values = new ArrayList<>(columns.size());
                for (String col : columns) values.add(stringify(row.get(col)));
                csv.writeRow(values);
            }
            csv.flush();
            rowCounts.put(filename, (long) rows.size());
            return rows.size();
        } finally {
            zip.closeEntry();
        }
    }

    /** Convert JDBC values to CSV-friendly strings. JSONB → JSON-string, byte[] → base64, etc. */
    private String stringify(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
        // PGobject (jsonb) and friends have toString() that returns the raw JSON
        return value.toString();
    }
}
```

> ⚠️ **CSV-Writer-Hinweis:** Der `CsvWriter` darf den `ZipOutputStream` NICHT schließen — sonst wird der ZIP-Stream beendet. Im Code-Skelett ist `close()` ein No-Op auf dem unterliegenden Stream. Beim implementieren bitte testen!

---

### 4.3 Phase 3 — Full Backup (pg_dump)

#### 4.3.1 Dockerfile-Anpassung

**Datei:** `backend/Dockerfile`

Im Runtime-Stage (NACH dem Maven-Build, im finalen Image) `postgresql-client` installieren:

```dockerfile
# Im Runtime-Stage, ungefähr in dieser Sektion:
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-client \
 && rm -rf /var/lib/apt/lists/*
```

> ⚠️ **Versions-Hinweis:** `postgresql-client` (ohne Version) installiert die Default-Version aus den Debian-Repos. Wenn Production-DB Postgres 17 ist, muss der Client mindestens 17 sein, sonst gibt es "server version mismatch"-Warnungen. Falls nötig: explizit das Postgres-APT-Repo einbinden und `postgresql-client-17` nehmen. Das ist der einzige potenziell knifflige Teil dieses Arbeitspakets.

#### 4.3.2 Executor-Interface

**Neue Datei:** `backend/src/main/java/com/cisternino/domain/interfaces/DatabaseDumpExecutor.java`

```java
package com.cisternino.domain.interfaces;

import java.io.OutputStream;

/**
 * Executes a full database dump and writes the binary output to the given stream.
 * Implementation may invoke an external tool (e.g. pg_dump).
 */
public interface DatabaseDumpExecutor {
    /**
     * @param output stream to write the dump bytes to (e.g. HTTP response)
     * @return number of bytes written
     * @throws DumpException if the dump fails for any reason
     */
    long dump(OutputStream output) throws DumpException;

    class DumpException extends Exception {
        public DumpException(String msg) { super(msg); }
        public DumpException(String msg, Throwable cause) { super(msg, cause); }
    }
}
```

#### 4.3.3 PgDump-Implementierung

**Neue Datei:** `backend/src/main/java/com/cisternino/infrastructure/persistence/dao/PgDumpExecutor.java`

```java
package com.cisternino.infrastructure.persistence.dao;

import interfaces.domain.de.uni_passau.fim.se2.DatabaseDumpExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Invokes `pg_dump` via ProcessBuilder and pipes its stdout to the given stream.
 * Requires `postgresql-client` to be installed in the backend container.
 */
@Component
@Slf4j
public class PgDumpExecutor implements DatabaseDumpExecutor {

    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    public PgDumpExecutor(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password}") String dbPassword) {
        // Parse jdbc:postgresql://host:port/dbname
        String stripped = jdbcUrl.replaceFirst("^jdbc:", "");
        URI uri = URI.create(stripped);
        this.dbHost = uri.getHost();
        this.dbPort = String.valueOf(uri.getPort() > 0 ? uri.getPort() : 5432);
        String path = uri.getPath();
        this.dbName = (path != null && path.startsWith("/")) ? path.substring(1) : path;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    @Override
    public long dump(OutputStream output) throws DumpException {
        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "-h", dbHost,
                "-p", dbPort,
                "-U", dbUser,
                "-d", dbName,
                "-Fc",                 // custom format (binary, compressed)
                "--no-owner",
                "--no-privileges",
                "--no-acl"
        );
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(false); // keep stderr separate so we can log it

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new DumpException("Failed to start pg_dump (is postgresql-client installed?)", e);
        }

        // Drain stderr in a separate thread so it doesn't block
        Thread stderrLogger = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                byte[] buf = new byte[4096];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = err.read(buf)) != -1) sb.append(new String(buf, 0, n));
                if (!sb.isEmpty()) log.warn("pg_dump stderr: {}", sb);
            } catch (IOException ignored) {}
        });
        stderrLogger.setDaemon(true);
        stderrLogger.start();

        long bytesWritten = 0;
        try (InputStream in = process.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                output.write(buf, 0, n);
                bytesWritten += n;
            }
            output.flush();
        } catch (IOException e) {
            process.destroy();
            throw new DumpException("Error streaming pg_dump output", e);
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DumpException("pg_dump interrupted", e);
        }

        if (exitCode != 0) {
            throw new DumpException("pg_dump exited with code " + exitCode);
        }

        log.info("pg_dump completed: {} bytes", bytesWritten);
        return bytesWritten;
    }
}
```

#### 4.3.4 Backup-Service (kombiniert Full-Backup + Single-Flight)

**Neue Datei:** `backend/src/main/java/com/cisternino/application/service/BackupService.java`

```java
package com.cisternino.application.service;

import entities.domain.de.uni_passau.fim.se2.AdminAuditLogDTO;
import interfaces.domain.de.uni_passau.fim.se2.DatabaseDumpExecutor;
import interfaces.domain.de.uni_passau.fim.se2.DatabaseDumpExecutor.DumpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates full database backups via pg_dump.
 * Single-flight lock prevents concurrent backups from hammering the DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final DatabaseDumpExecutor dumpExecutor;
    private final AdminAuditService auditService;

    /** Only ONE pg_dump may run at a time. New requests wait up to 30s, then 503. */
    private static final Semaphore BACKUP_LOCK = new Semaphore(1, true);
    private static final long LOCK_TIMEOUT_SECONDS = 30;

    public static class BackupBusyException extends RuntimeException {
        public BackupBusyException() { super("Another backup is already in progress."); }
    }

    /**
     * Streams a full pg_dump to the given output. Acquires the single-flight lock first.
     *
     * @return bytes written to output
     * @throws BackupBusyException if another backup is running and the wait timed out
     * @throws DumpException       on pg_dump failure
     */
    public long streamFullBackup(OutputStream output, UUID adminId, String ip, String userAgent)
            throws DumpException, InterruptedException {

        if (!BACKUP_LOCK.tryAcquire(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            auditService.logFailure(adminId, AdminAuditLogDTO.Action.FULL_BACKUP, ip, userAgent,
                    "BackupBusy: another backup in progress");
            throw new BackupBusyException();
        }
        try {
            long bytes = dumpExecutor.dump(output);
            auditService.logSuccess(adminId, AdminAuditLogDTO.Action.FULL_BACKUP, ip, userAgent,
                    bytes, null);
            return bytes;
        } catch (DumpException e) {
            auditService.logFailure(adminId, AdminAuditLogDTO.Action.FULL_BACKUP, ip, userAgent,
                    e.getMessage());
            throw e;
        } finally {
            BACKUP_LOCK.release();
        }
    }
}
```

---

### 4.4 Phase 4 — Controller & DTOs

#### 4.4.1 Audit-DTO für API-Response

**Neue Datei:** `backend/src/main/java/com/cisternino/api/dto/AdminAuditLogResponseDTO.java`

```java
package com.cisternino.api.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminAuditLogResponseDTO {
    private Long id;
    private String adminId;        // UUID as string
    private String action;
    private String ipAddress;
    private Long bytesSent;
    private Long rowsSent;
    private boolean success;
    private String errorMsg;
    private String createdAt;      // ISO-formatted
}
```

#### 4.4.2 Neuer Controller

**Neue Datei:** `backend/src/main/java/com/cisternino/api/AdminBackupController.java`

```java
package com.cisternino.api;

import dto.api.de.uni_passau.fim.se2.AdminAuditLogResponseDTO;
import service.application.de.uni_passau.fim.se2.AdminAuditService;
import service.application.de.uni_passau.fim.se2.BackupService;
import service.application.de.uni_passau.fim.se2.BackupService.BackupBusyException;
import service.application.de.uni_passau.fim.se2.ResearchExportService;
import entities.domain.de.uni_passau.fim.se2.AdminAuditLogDTO;
import interfaces.domain.de.uni_passau.fim.se2.DatabaseDumpExecutor.DumpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only DB-access endpoints: full backup + research export + audit log viewer.
 * All endpoints under /api/admin/** require ADMIN role (enforced in SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBackupController {

    private final BackupService backupService;
    private final ResearchExportService researchExportService;
    private final AdminAuditService auditService;

    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Streams a full pg_dump backup to the client as a file download.
     * Mutex-locked: only one backup at a time across all admins.
     */
    @PostMapping("/backup")
    public void downloadFullBackup(@AuthenticationPrincipal UUID adminId,
                                   HttpServletRequest req,
                                   HttpServletResponse resp) throws Exception {
        String filename = "codeowl-backup-" + LocalDateTime.now().format(FILE_TS) + ".dump";

        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        // No Content-Length: we stream chunked

        try {
            backupService.streamFullBackup(
                    resp.getOutputStream(),
                    adminId,
                    req.getRemoteAddr(),
                    req.getHeader("User-Agent")
            );
        } catch (BackupBusyException e) {
            // Headers already sent — best we can do is throw, framework will finalize the response
            log.warn("Backup busy for admin {}", adminId);
            throw e;
        } catch (DumpException e) {
            log.error("pg_dump failed for admin {}: {}", adminId, e.getMessage());
            throw e;
        }
    }

    /**
     * Streams a ZIP of research-relevant CSV exports.
     * Pseudonymized — no PII columns.
     */
    @GetMapping("/research-export")
    public void downloadResearchExport(@AuthenticationPrincipal UUID adminId,
                                       HttpServletRequest req,
                                       HttpServletResponse resp) throws Exception {
        String filename = "codeowl-research-" + LocalDateTime.now().format(FILE_TS) + ".zip";
        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        String adminShort = adminId != null ? adminId.toString().substring(0, 8) : "unknown";
        try {
            long rows = researchExportService.streamExport(resp.getOutputStream(), adminShort);
            auditService.logSuccess(adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT,
                    req.getRemoteAddr(), req.getHeader("User-Agent"), null, rows);
        } catch (Exception e) {
            log.error("Research export failed for admin {}: {}", adminId, e.getMessage());
            auditService.logFailure(adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT,
                    req.getRemoteAddr(), req.getHeader("User-Agent"), e.getMessage());
            throw e;
        }
    }

    /**
     * Returns the most recent admin audit log entries (all admins, all actions).
     */
    @GetMapping("/audit-log")
    public ResponseEntity<List<AdminAuditLogResponseDTO>> getAuditLog(
            @RequestParam(defaultValue = "100") int limit) {
        List<AdminAuditLogDTO> entries = auditService.getRecent(limit);
        return ResponseEntity.ok(entries.stream().map(this::toResponseDTO).toList());
    }

    private AdminAuditLogResponseDTO toResponseDTO(AdminAuditLogDTO e) {
        return AdminAuditLogResponseDTO.builder()
                .id(e.getId())
                .adminId(e.getAdminId().toString())
                .action(e.getAction())
                .ipAddress(e.getIpAddress())
                .bytesSent(e.getBytesSent())
                .rowsSent(e.getRowsSent())
                .success(e.isSuccess())
                .errorMsg(e.getErrorMsg())
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .build();
    }
}
```

#### 4.4.3 GlobalExceptionHandler-Erweiterung

**Datei:** `backend/src/main/java/com/cisternino/api/GlobalExceptionHandler.java`

Neuen Handler ergänzen:
```java
@ExceptionHandler(BackupService.BackupBusyException.class)
public ResponseEntity<?> handleBackupBusy(BackupService.BackupBusyException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", e.getMessage()));
}

@ExceptionHandler(DatabaseDumpExecutor.DumpException.class)
public ResponseEntity<?> handleDumpException(DatabaseDumpExecutor.DumpException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Database backup failed: " + e.getMessage()));
}
```

---

## 5. Frontend-Implementierung

### 5.1 Neue Seite: `/admin/database`

**Neue Datei:** `frontend/src/pages/AdminDatabasePage.tsx`

Layout-Plan:
- Header mit Beschreibung
- Card 1: **Vollständiges Backup** — Beschreibung + Button. Beim Klick:
  - `setLoading(true)`
  - axios `POST` mit `responseType: "blob"` und `timeout: 0` (kein Timeout — Backup kann lange dauern)
  - Response → Blob → `URL.createObjectURL` → unsichtbarer `<a download>` → click → revoke URL
  - Bei 503 (BackupBusy): Fehlermeldung „Anderes Backup läuft, bitte später erneut"
- Card 2: **Forschungsdaten-Export** — analog, aber `GET`-Request
- Card 3: **Audit-Log** — Tabelle mit den letzten 100 Einträgen, einmal beim Mount geladen, Refresh-Button. Spalten: Datum, Admin (UUID-Kurzform), Aktion, Status, Bytes/Rows, Fehler

**Code-Skelett:**

```tsx
import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { useAuth } from "../context/AuthContext";

interface AuditEntry {
    id: number;
    adminId: string;
    action: string;
    ipAddress: string | null;
    bytesSent: number | null;
    rowsSent: number | null;
    success: boolean;
    errorMsg: string | null;
    createdAt: string;
}

export default function AdminDatabasePage() {
    const { t } = useTranslation();
    const { isAdmin } = useAuth();

    const [downloading, setDownloading] = useState<"backup" | "research" | null>(null);
    const [error, setError] = useState("");
    const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
    const [loadingAudit, setLoadingAudit] = useState(false);

    if (!isAdmin) return <Navigate to="/" replace />;

    useEffect(() => {
        loadAudit();
    }, []);

    const loadAudit = async () => {
        setLoadingAudit(true);
        try {
            const res = await api.get<AuditEntry[]>("/admin/audit-log?limit=100");
            setAuditEntries(res.data);
        } catch {
            // silently ignore — audit may be empty on fresh installs
        } finally {
            setLoadingAudit(false);
        }
    };

    const triggerDownload = async (
        kind: "backup" | "research",
        method: "post" | "get",
        url: string,
        filenameFallback: string
    ) => {
        setDownloading(kind);
        setError("");
        try {
            const res = method === "post"
                ? await api.post(url, null, { responseType: "blob", timeout: 0 })
                : await api.get(url, { responseType: "blob", timeout: 0 });

            // Try to read filename from Content-Disposition; otherwise use fallback
            const cd = res.headers["content-disposition"] || "";
            const match = cd.match(/filename="?([^";]+)"?/i);
            const filename = match?.[1] || filenameFallback;

            const blobUrl = URL.createObjectURL(res.data as Blob);
            const a = document.createElement("a");
            a.href = blobUrl;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(blobUrl);

            // refresh audit log to show the new entry
            loadAudit();
        } catch (e: any) {
            if (e.response?.status === 503) {
                setError(t("adminDb.backupBusy"));
            } else {
                setError(e.response?.data?.error || t("adminDb.downloadError"));
            }
        } finally {
            setDownloading(null);
        }
    };

    // ... JSX with two buttons + audit table ...
}
```

### 5.2 Route registrieren

**Datei:** `frontend/src/App.tsx`

Import hinzufügen:
```tsx
import AdminDatabasePage from "./pages/AdminDatabasePage";
```

Route hinzufügen (in der Admin-Sektion):
```tsx
<Route path="/admin/database" element={<ProtectedRoute requiredRole="ADMIN"><AdminDatabasePage /></ProtectedRoute>} />
```

Auch in `FloatingHelpButton` einen `useMatch("/admin/database")` ergänzen, damit der Help-Kontext stimmt (Mapping auf `"admin"`).

### 5.3 Navbar-Link

**Datei:** `frontend/src/App.tsx` (im Navbar-Block)

Im `isAdmin`-Conditional einen weiteren Link ergänzen:
```tsx
<Link to="/admin/database" className="btn btn-sm btn-outline-danger">
    <i className="bi bi-database me-1"></i>
    {t("nav.databaseTools")}
</Link>
```

### 5.4 i18n-Keys

**Datei:** `frontend/src/i18n/de.json`

Im `nav`-Block:
```json
"databaseTools": "Datenbank-Tools",
```

Neuer Top-Level-Block:
```json
"adminDb": {
    "title": "Datenbank-Tools",
    "subtitle": "Backup und Forschungsdaten-Export für die Production-DB",
    "backupTitle": "Vollständiges Backup",
    "backupDesc": "Erzeugt ein binäres pg_dump-Backup der gesamten Datenbank. Enthält Personendaten — sicher verwahren.",
    "backupBtn": "Backup herunterladen",
    "backupBusy": "Ein anderes Backup läuft bereits. Bitte einen Moment warten und erneut versuchen.",
    "researchTitle": "Forschungsdaten-Export",
    "researchDesc": "ZIP mit CSV-Tabellen aller Forschungs-Daten. UUIDs als Pseudonyme — keine direkten Personendaten enthalten.",
    "researchBtn": "Forschungsdaten herunterladen",
    "auditTitle": "Audit-Log (letzte 100 Aktionen)",
    "auditRefresh": "Aktualisieren",
    "auditDate": "Datum",
    "auditAdmin": "Admin",
    "auditAction": "Aktion",
    "auditStatus": "Status",
    "auditSize": "Größe",
    "auditEmpty": "Noch keine Einträge.",
    "downloadError": "Fehler beim Download.",
    "downloading": "Wird heruntergeladen..."
}
```

**Datei:** `frontend/src/i18n/en.json` — gleiche Keys, englische Übersetzungen.

### 5.5 HelpPage-Erweiterung

**Datei:** `frontend/src/pages/HelpPage.tsx`

Neuen Case `case "admin"` ergänzen oder erweitern: Dokumentation der neuen Funktionen, inkl. wie man ein Backup wiederherstellt:

```bash
# Wiederherstellung auf einem lokalen Postgres-Container:
docker exec -i my-pg-container pg_restore -U postgres -d codeowl < codeowl-backup-20260427-1430.dump
```

---

## 6. Implementierungs-Reihenfolge

```
┌──────────────────────────────────────────────────────────────┐
│ Phase 1 — DB & Audit (1–2h)                                  │
│   1.1 db/init/zzz_migration_admin_audit_log.sql              │
│   1.2 AdminAuditLogDTO                                       │
│   1.3 AdminAuditLogRepository (Interface)                    │
│   1.4 AdminAuditLogRowMapper                                 │
│   1.5 PostgreAdminAuditLogDao                                │
│   1.6 AdminAuditService                                      │
│                                                              │
│   ✓ Test: Spring-Boot-Start ohne Fehler                      │
│   ✓ Test: SELECT * FROM admin_audit_log; → existiert         │
└──────────────────────────────────────────────────────────────┘
                           ↓
┌──────────────────────────────────────────────────────────────┐
│ Phase 2 — Research Export (2–3h)                             │
│   2.1 CsvWriter (Util)                                       │
│   2.2 ResearchDataReader (Interface)                         │
│   2.3 PostgreResearchDataDao                                 │
│   2.4 ResearchExportService                                  │
│   2.5 AdminBackupController.downloadResearchExport()         │
│   2.6 i18n-Keys (de + en)                                    │
│   2.7 AdminDatabasePage (nur Research-Export-Card erstmal)   │
│   2.8 Route + Navbar-Link                                    │
│                                                              │
│   ✓ Test: ZIP enthält alle CSVs                              │
│   ✓ Test: users_pseudonymized.csv hat KEINE email-Spalte    │
│   ✓ Test: Audit-Eintrag wird geschrieben                     │
└──────────────────────────────────────────────────────────────┘
                           ↓
┌──────────────────────────────────────────────────────────────┐
│ Phase 3 — Full Backup (2h, nur wenn Phase 2 stabil)          │
│   3.1 Dockerfile: postgresql-client                          │
│   3.2 DatabaseDumpExecutor (Interface)                       │
│   3.3 PgDumpExecutor                                         │
│   3.4 BackupService (mit Single-Flight-Lock)                 │
│   3.5 AdminBackupController.downloadFullBackup()             │
│   3.6 GlobalExceptionHandler-Handler für BackupBusy          │
│   3.7 AdminDatabasePage: Backup-Card hinzufügen              │
│                                                              │
│   ✓ Test: pg_dump läuft im Container                         │
│   ✓ Test: Browser-Download startet                           │
│   ✓ Test: Restore funktioniert auf lokalem Container         │
│   ✓ Test: Zwei parallele Klicks → einer bekommt 503          │
└──────────────────────────────────────────────────────────────┘
                           ↓
┌──────────────────────────────────────────────────────────────┐
│ Phase 4 — Audit-Viewer (1h)                                  │
│   4.1 AdminAuditLogResponseDTO                               │
│   4.2 AdminBackupController.getAuditLog()                    │
│   4.3 AdminDatabasePage: Audit-Tabelle                       │
│                                                              │
│   ✓ Test: Tabelle zeigt alle Einträge                        │
│   ✓ Test: Refresh-Button funktioniert                        │
└──────────────────────────────────────────────────────────────┘
```

**Geschätzte Gesamt-Zeit:** 6–8h fokussierte Arbeit.

---

## 7. Akzeptanz-Kriterien

### Backend
- [ ] `admin_audit_log` Tabelle existiert und ist via Migration erstellbar (idempotent)
- [ ] Spring Boot startet ohne neue Fehler/Warnungen
- [ ] `POST /api/admin/backup` ohne ADMIN-JWT → `403`
- [ ] `POST /api/admin/backup` mit ADMIN-JWT → 200 + Stream
- [ ] `GET /api/admin/research-export` mit ADMIN-JWT → 200 + ZIP
- [ ] ZIP enthält alle Tabellen aus 4.2.1, plus `_metadata.json`
- [ ] `users_pseudonymized.csv` enthält **nicht** email/firstName/lastName/passwordHash
- [ ] Jeder Backup/Export-Aufruf produziert genau einen Eintrag in `admin_audit_log`
- [ ] Bei pg_dump-Failure: success=false, errorMsg gefüllt
- [ ] Zweiter paralleler `POST /api/admin/backup` während erster läuft → 503 nach 30s
- [ ] `GET /api/admin/audit-log?limit=10` → max. 10 Einträge, neueste zuerst

### Frontend
- [ ] `/admin/database` nur sichtbar für Admins (Redirect zu `/` bei TEACHER/STUDENT)
- [ ] Beide Buttons triggern echten Browser-Download
- [ ] Während Download: Spinner sichtbar, Buttons deaktiviert
- [ ] Audit-Log-Tabelle zeigt korrekte Daten
- [ ] i18n: alle Texte sowohl auf Deutsch als auch Englisch
- [ ] HelpPage hat Restore-Doku im `admin`-Kontext

### Operations
- [ ] Frische Docker-Compose-Up: Migration läuft automatisch
- [ ] Bestehender Production-Server: Migration kann manuell per `docker exec` ausgeführt werden
- [ ] Heruntergeladenes Backup ist tatsächlich via `pg_restore` wiederherstellbar (manueller Test)

---

## 8. Bekannte Stolperfallen

1. **postgresql-client-Version** — die Default-Debian-Version kann älter als Postgres 17 sein. Symptom: pg_dump-Output unvollständig oder Warnings im Log. Lösung: PGDG-APT-Repo einbinden.

2. **CsvWriter schließt ZipOutputStream** — wenn der `try-with-resources` falsch verschachtelt ist, schließt das innere `close()` den ZIP-Stream. Lösung: `CsvWriter.close()` darf den unterliegenden Stream NICHT schließen.

3. **Content-Disposition vs. Browser-Filename** — manche Browser ignorieren das `filename=`-Attribut wenn es nicht in Quotes steht. Immer `filename="..."` mit Anführungszeichen.

4. **Axios `responseType: "blob"`** — wenn vergessen, kommt der Body als String an und der Download ist korrupt.

5. **`UNIQUE` constraints in pg_dump** — `--no-owner --no-privileges --no-acl` ist wichtig, sonst muss beim Restore exakt der gleiche User vorhanden sein.

6. **JdbcTemplate `queryForList` mit jsonb-Spalten** — der Wert kommt als `org.postgresql.util.PGobject`. Dessen `toString()` gibt die JSON-Repräsentation zurück, also direkt CSV-tauglich.

7. **`@AuthenticationPrincipal UUID`** — funktioniert nur weil der `JwtAuthenticationFilter` die UUID setzt. Wenn das mal nicht klappt, ist `adminId` `null` und der Audit-Log-INSERT failed wegen NOT-NULL-Constraint.

8. **Audit-Log darf den Hauptflow nicht brechen** — wenn das Schreiben in `admin_audit_log` failt (DB voll, Constraint-Verletzung), nur loggen, nicht throwen. Sonst wird ein erfolgreiches Backup zu einem Fehler.

9. **Streaming + Exception nach erstem Byte** — wenn `pg_dump` nach 1 MB stirbt, sind die HTTP-Headers schon raus. Der Browser bekommt eine korrupte Datei. Einzige Defense: gutes Logging serverseitig, damit der Admin sieht dass es nicht geklappt hat.

10. **Connection-Pool-Erschöpfung** — `ResearchExportService` hält ~20 Connections gleichzeitig (eine pro `queryForList`)? Nein, Spring gibt jede Connection nach jedem `query`-Call wieder frei. Aber: einer der Calls kann lange laufen. Bei einer kleinen DB unkritisch.

---

## 9. Manuelle Tests nach Implementierung

### Test 1 — Research-Export Smoke-Test
```bash
# Mit ADMIN-JWT von /api/auth/login
curl -X GET https://scratch.fim.uni-passau.de/codeowl/api/admin/research-export \
  -H "Authorization: Bearer $JWT" \
  -o test.zip

# Sollte gültiges ZIP sein:
unzip -l test.zip
# Erwartung: alle aufgelisteten CSVs + _metadata.json

# users_pseudonymized prüfen — sollte KEINE email-Spalte enthalten:
unzip -p test.zip users_pseudonymized.csv | head -2
# Erwartung: "user_id,role,is_verified,created_at"
```

### Test 2 — Full-Backup Restore-Roundtrip
```bash
# 1. Backup ziehen
curl -X POST https://.../api/admin/backup -H "Authorization: Bearer $JWT" -o backup.dump

# 2. Lokalen Test-Container starten
docker run --rm -d --name pg-restore-test -e POSTGRES_PASSWORD=test -p 5433:5432 postgres:17

# 3. Restore
docker exec -i pg-restore-test pg_restore -U postgres -C -d postgres < backup.dump

# 4. Verifizieren
docker exec pg-restore-test psql -U postgres -d codeowl -c "SELECT COUNT(*) FROM users;"
```

### Test 3 — Single-Flight-Lock
```bash
# In zwei Terminals fast gleichzeitig:
curl -X POST https://.../api/admin/backup -H "Authorization: Bearer $JWT" -o b1.dump &
curl -X POST https://.../api/admin/backup -H "Authorization: Bearer $JWT" -o b2.dump &
wait

# Erwartung: Eines der beiden Files ist die Backup-Datei, das andere ist eine 503-Error-JSON.
```

### Test 4 — Audit-Log
```sql
-- Auf Production-DB nach den obigen Tests:
SELECT created_at, action, success, bytes_sent, rows_sent, error_msg
FROM admin_audit_log
ORDER BY created_at DESC LIMIT 10;
-- Erwartung: 4–5 Einträge mit den ausgeführten Aktionen
```

---

## 10. Was NACH diesem AP zu erledigen wäre (out of scope)

- DSGVO-Lösch-/Auskunftsworkflow (separates AP)
- Audit-Log-Retention (z.B. nach 1 Jahr automatisch alte Einträge löschen)
- Verschlüsselung der Backups bei Erzeugung (`gpg --symmetric`)
- Backup-Schedule (Cron, täglich, Aufbewahrung der letzten 7)
- Granularere Rollen (z.B. „Researcher" der nur Research-Export darf, kein Full-Backup)
- Streaming-Implementierung mit `setFetchSize` falls DB-Größe stark wächst

---

## 11. Referenzen im Code (für den Agent zum Nachschauen)

| Was | Wo |
|---|---|
| Bestehende Admin-Endpoints | `backend/src/main/java/com/cisternino/api/AdminController.java` |
| Bestehende Admin-Service | `backend/src/main/java/com/cisternino/application/service/AdminService.java` |
| Bestehender CSV/JSON-Export | `backend/src/main/java/com/cisternino/api/LearningEventController.java` (Method `exportEvents`) |
| SecurityConfig | `backend/src/main/java/com/cisternino/config/SecurityConfig.java` |
| JwtAuthenticationFilter | `backend/src/main/java/com/cisternino/config/JwtAuthenticationFilter.java` |
| Existierendes RowMapper-Beispiel | `backend/src/main/java/com/cisternino/infrastructure/persistence/mapper/UserRowMapper.java` |
| Existierendes DAO-Beispiel | `backend/src/main/java/com/cisternino/infrastructure/persistence/dao/PostgreUserDao.java` |
| GlobalExceptionHandler | `backend/src/main/java/com/cisternino/api/GlobalExceptionHandler.java` |
| DB-Schema-Referenz | `db/init/1_codeowl_backup.sql` (CREATE TABLE-Statements darin nachschlagen!) |
| ProtectedRoute-Beispiel mit `requiredRole` | `frontend/src/components/ProtectedRoute.tsx` |
| AuthContext (für `isAdmin`) | `frontend/src/context/AuthContext.tsx` |
| Existierende Admin-Frontend-Page | `frontend/src/pages/AdminUsersPage.tsx` |
| Dockerfile Backend | `backend/Dockerfile` |

---

# Arbeitspaket: UX — Öffentlicher Gast-Zugang & Kontextueller Login-Redirect

> **Status:** Geplant, nicht implementiert
> **Geschätzte Komplexität:** Klein (rein Frontend, ~7 Dateien, kein Backend)
> **Priorität:** Mittel (verbessert Nutzererfahrung für anonyme Schüler und neugierige Lehrkräfte)

---

## 0. Kontext für den Agent

### Was ist CodeOwl?
Full-Stack-Bildungsplattform für Parsons-Programmieraufgaben. Lehrkräfte generieren KI-gestützte Aufgabensequenzen, Schüler lösen sie als Drag-&-Drop-Puzzles. Backend in Spring Boot 3.x mit Clean Architecture, Frontend in React/TypeScript.

### Tech-Stack
- **Backend:** Spring Boot 3.3, Spring JDBC, PostgreSQL 17, JJWT, Lombok
- **Frontend:** React 18, TypeScript, Vite, react-router-dom v6, react-i18next, Bootstrap 5, Axios
- **Deployment:** Docker Compose

### Projektpfade
- **Repo-Root:** `C:\Users\default.MSI\Desktop\EverythingZulaRelated\2-thesis-repo\cisternino-ba\codeowl-clean`
- **Frontend-Source:** `frontend/src/`
- **i18n-Dateien:** `frontend/src/i18n/de.json` und `frontend/src/i18n/en.json`

### Auth-Konventionen (Frontend)
- `useAuth()` aus `frontend/src/context/AuthContext.tsx` liefert `{ isLoggedIn, isStudent, isTeacher, isAdmin, user, ... }`
- `ProtectedRoute` in `frontend/src/components/ProtectedRoute.tsx` blockt nicht eingeloggte User mit `<Navigate to="login" replace />`
- Routing in `frontend/src/App.tsx`

---

## 1. Ziel & Scope dieses Arbeitspakets

### Problem (Analyse-Ergebnis)
Bei einer Gast-Zugangs-Prüfung wurden folgende UX-Probleme identifiziert:

| # | Problem | Betroffene Datei(en) |
|---|---|---|
| P1 | `ProtectedRoute` speichert kein Return-URL: nach Login landet der User auf `/` statt auf der ursprünglich aufgerufenen Seite | `ProtectedRoute.tsx`, `LoginPage.tsx` |
| P2 | Library, Curriculum und ManualTaskCreator sind hinter `ProtectedRoute` gesperrt — obwohl anonyme User diese Seiten sinnvoll lesen/erkunden könnten | `App.tsx` |
| P3 | HomePage-Worksheet-Erstellungs-Button ist für anonyme User sichtbar und öffnet ein Modal, schlägt aber beim Submit mit einem opaken Backend-Fehler (401) fehl — statt einer klaren Login-Weiterleitung | `HomePage.tsx` |
| P4 | ManualTaskCreator-Submit und HomePage-Generate zeigen anonymen Usern keinen klaren Login-Hinweis, obwohl beide Aktionen Auth erfordern | `ManualTaskCreator.tsx`, `HomePage.tsx` |
| P5 | LoginPage zeigt keinen Kontext-Hinweis, wenn der User von einer geschützten Aktion weitergeleitet wurde | `LoginPage.tsx` |

### Lösung
- **P1:** `ProtectedRoute` übergibt `location` als `state.from` an `/login`; `LoginPage` liest `state.from` und redirectet nach erfolgreichem Login dorthin.
- **P2:** Library-, Curriculum- und ManualTaskCreator-Routen in `App.tsx` aus `ProtectedRoute` herausnehmen. Innerhalb der Seiten bleiben Mutations/Aktionen (Erstellen, Speichern, Generieren) weiterhin auth-gegatet.
- **P3 + P4:** Wenn ein anonymer User eine "Erstellen"-Aktion auslöst (Generate auf HomePage, Submit in ManualTaskCreator, Worksheet erstellen), wird er via `navigate("/login", { state: { from: location, message: "auth.loginRequiredToCreate" } })` weitergeleitet — nicht via Backend-Fehler.
- **P5:** `LoginPage` liest `state.message` und rendert ein kontextuelles `alert-info`-Banner über dem Login-Formular. Der Text ist internationalisiert.

### Nicht-Ziele
- Kein Backend-Change nötig (alle Änderungen sind rein Frontend)
- Keine Änderung der Navbar-Sichtbarkeit von Library/Curriculum/ManualCreate für Studenten (diese sehen die Links weiterhin nicht in der Navbar — nur der direkte URL-Aufruf ohne Login wird entsperrt)
- Keine Änderung der Hint-/Solve-/Sequence-Public-Flows (funktionieren bereits korrekt für anonyme User)

---

## 2. Design-Entscheidungen

| Frage | Entscheidung | Begründung |
|---|---|---|
| Return-URL Mechanismus | **React Router `state.from`** (nicht URL-Parameter `?next=`) | State wird nicht in der URL sichtbar, kein URL-Encoding-Problem, passt zu bestehendem Routing-Pattern |
| Kontext-Message Übertragung | **`state.message` als i18n-Key-String** (z. B. `"auth.loginRequiredToCreate"`) | LoginPage rendert `t(state.message)` — typsicher, mehrsprachig, kein Freitext im State |
| Library/Curriculum ohne Login | **Route-Level-Entfernung aus ProtectedRoute** | Einfachste Lösung; Mutations innerhalb der Pages bleiben durch `isLoggedIn`-Checks gated |
| ManualTaskCreator öffentlich | **Seite anzeigen, Submit gaten** | User soll das Formular erkunden können; beim Abschicken wird er zum Login weitergeleitet und danach zurückgebracht |
| HomePage Generate | **Generate-Button für anonyme User → Login-Redirect** | Konsistentes Verhalten mit ManualTaskCreator; Sequenz-Generierung ist eine Lehrperson-Aktion |

---

## 3. Implementierung (Schritt für Schritt)

### 3.1 `ProtectedRoute.tsx` — Return-URL speichern

**Datei:** `frontend/src/components/ProtectedRoute.tsx`

`useLocation` importieren und `location` als `state.from` übergeben:

```tsx
import { Navigate, useLocation } from "react-router-dom";
// ...
const location = useLocation();
// ...
if (!isLoggedIn) {
    return <Navigate to="/login" state={{ from: location }} replace />;
}
```

---

### 3.2 `LoginPage.tsx` — Return-URL lesen & Kontext-Message anzeigen

**Datei:** `frontend/src/pages/LoginPage.tsx`

Änderungen:
1. `useLocation` importieren, `state` destructuren
2. Nach erfolgreichem Login: `navigate(from, { replace: true })` statt hartem `navigate("/")`
3. Vor dem Login-Formular: wenn `state?.message` gesetzt → `alert-info`-Banner mit `t(state.message)` rendern

```tsx
const location = useLocation();
const from: string = (location.state as any)?.from?.pathname ?? "/";
const messageKey: string | undefined = (location.state as any)?.message;

// Im JSX vor dem Form:
{messageKey && (
    <div className="alert alert-info mb-3" role="alert">
        <i className="bi bi-info-circle me-2"></i>
        {t(messageKey)}
    </div>
)}

// Nach erfolgreichem Login in handleSubmit / handleLogin:
navigate(from, { replace: true });
```

---

### 3.3 `App.tsx` — Library, Curriculum, ManualTaskCreator aus ProtectedRoute herausnehmen

**Datei:** `frontend/src/App.tsx`

Aktuelle Routen:
```tsx
<Route path="/manual-create" element={<ProtectedRoute><ManualTaskCreator /></ProtectedRoute>} />
<Route path="/curriculum"    element={<ProtectedRoute><CurriculumPage /></ProtectedRoute>} />
<Route path="/library"       element={<ProtectedRoute><LibraryPage /></ProtectedRoute>} />
```

Neue Routen (ProtectedRoute entfernen):
```tsx
<Route path="/manual-create" element={<ManualTaskCreator />} />
<Route path="/curriculum"    element={<CurriculumPage />} />
<Route path="/library"       element={<LibraryPage />} />
```

> **Achtung:** Innerhalb von `ManualTaskCreator`, `CurriculumPage` und `LibraryPage` müssen Mutations (Speichern, Generieren, Löschen) weiterhin per `isLoggedIn`-Check gated sein — siehe 3.4.

---

### 3.4 `ManualTaskCreator.tsx` — Submit für anonyme User abfangen

**Datei:** `frontend/src/pages/ManualTaskCreator.tsx`

In der Submit-Handler-Funktion (wo `POST /api/tasks/...` aufgerufen wird) am Anfang einfügen:

```tsx
const { isLoggedIn } = useAuth();
const navigate = useNavigate();
const location = useLocation();

function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isLoggedIn) {
        navigate("/login", {
            state: { from: location, message: "auth.loginRequiredToCreate" }
        });
        return;
    }
    // ... bestehende Submit-Logik
}
```

---

### 3.5 `HomePage.tsx` — Generate und Worksheet-Erstellung für anonyme User abfangen

**Datei:** `frontend/src/pages/HomePage.tsx`

**a) Generate-Handler** — auth-Guard am Anfang der Funktion einfügen:
```tsx
const { isLoggedIn } = useAuth();
const location = useLocation();

async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isLoggedIn) {
        navigate("/login", {
            state: { from: location, message: "auth.loginRequiredToCreate" }
        });
        return;
    }
    // ... bestehende Generate-Logik
}
```

**b) Worksheet-Erstellungs-Modal-Öffner** — statt Modal öffnen, zu Login weiterleiten:
```tsx
function handleCreateWorksheetOpen() {
    if (!isLoggedIn) {
        navigate("/login", {
            state: { from: location, message: "auth.loginRequiredToCreate" }
        });
        return;
    }
    setShowModal(true);
    setWorksheetName("");
    setWorksheetDesc("");
}
```

---

### 3.6 i18n — Neue Keys

**Datei:** `frontend/src/i18n/de.json`

Im `"auth"`-Block (anlegen falls nicht vorhanden):
```json
"auth": {
    "loginRequiredToCreate": "Um Sequenzen erstellen und danach verwalten zu können, musst du eingeloggt sein."
}
```

**Datei:** `frontend/src/i18n/en.json`

```json
"auth": {
    "loginRequiredToCreate": "You need to be logged in to create and manage sequences."
}
```

> Falls ein `auth`-Block bereits vorhanden ist, die Keys einfach dort einfügen.

---

## 4. Prüfliste nach Implementierung

- [ ] Anonymer User ruft `/library` auf → Seite lädt, kein Login-Redirect
- [ ] Anonymer User ruft `/curriculum` auf → Seite lädt, kein Login-Redirect
- [ ] Anonymer User ruft `/manual-create` auf → Formular wird angezeigt, kein Login-Redirect
- [ ] Anonymer User klickt Submit in ManualTaskCreator → Redirect zu `/login` mit blauem Info-Banner
- [ ] Anonymer User klickt Generate auf HomePage → Redirect zu `/login` mit blauem Info-Banner
- [ ] Anonymer User klickt Worksheet-Button auf HomePage → Redirect zu `/login` mit blauem Info-Banner
- [ ] Info-Banner Text ist in DE und EN korrekt übersetzt
- [ ] Info-Banner erscheint **nicht** bei normalem direktem Login-Aufruf (ohne `state.message`)
- [ ] Nach Login: User landet auf der ursprünglich aufgerufenen Seite (Return-URL), nicht auf `/`
- [ ] Eingeloggte Lehrkraft: Library, Curriculum, ManualCreate funktionieren unverändert
- [ ] Eingeloggter Student: kein Zugang zu Library/Curriculum/ManualCreate in der Navbar (unverändert — nur URL-Direktaufruf ist entsperrt)
- [ ] `ProtectedRoute`-Seiten (z. B. `/classes`, `/profile`) leiten nach Login korrekt zurück

---

## 5. Betroffene Dateien (Übersicht)

| Datei | Art der Änderung |
|---|---|
| `frontend/src/components/ProtectedRoute.tsx` | `useLocation` + `state.from` in `<Navigate>` |
| `frontend/src/pages/LoginPage.tsx` | `state.from` für Return-Redirect; `state.message` als Info-Banner |
| `frontend/src/App.tsx` | `ProtectedRoute` von 3 Routen entfernen |
| `frontend/src/pages/ManualTaskCreator.tsx` | Auth-Guard vor Submit-Handler |
| `frontend/src/pages/HomePage.tsx` | Auth-Guard vor Generate-Handler + Worksheet-Modal-Öffner |
| `frontend/src/i18n/de.json` | Key `auth.loginRequiredToCreate` |
| `frontend/src/i18n/en.json` | Key `auth.loginRequiredToCreate` |

---

**Ende der Spezifikation.** Der implementierende Agent sollte diese Datei vollständig lesen, bei Unklarheiten die referenzierten Code-Stellen konsultieren, und bei wirklich blockierenden Fragen vor der Implementation nachfragen — nicht raten.

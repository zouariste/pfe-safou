# CodeOwl – Arbeitspakete & Spezifikation

## Kontext & Ausgangslage

### Was ist CodeOwl?
CodeOwl ist eine Full-Stack-Bildungsplattform zum Generieren und Lösen von Parsons-Programmieraufgaben. Lehrkräfte können KI-generierte Aufgabensequenzen erstellen, die Schüler in einem interaktiven Puzzle-Format lösen. Die Plattform unterstützt gestufte Aufgaben (Tiers) innerhalb von Sequenzen, organisiert in Worksheets.

### Tech-Stack
- **Backend:** Spring Boot 3.3.0, Spring JDBC, PostgreSQL 17
- **Frontend:** React + TypeScript, Vite, Axios, Bootstrap
- **Datenbank:** PostgreSQL 17 (Docker)
- **AI:** OpenAI (GPT-4.1 default), Claude, Gemini (konfigurierbar)
- **Deployment:** Docker Compose (db, backend:8080, frontend:5175)
- **Passwort-Hashing:** Argon2 (geplant, aus OneSports übernommen)

### Projektpfade
- **CodeOwl (aktuell):** `C:\Users\default.MSI\Desktop\EverythingZulaRelated\2-thesis-repo\cisternino-ba\codeowl-clean`
- **OneSports (Referenz für Auth):** `C:\Users\default.MSI\Desktop\OneSports\Dokumentation`
- **Git Remote:** `https://github.com/cister01/CodeOwl.git`

### Aktueller Zustand (vor Arbeitspaketen)
- Kein Login/Auth-System
- Kein Rollen-System – Teacher/Student-Trennung nur über URL (`/solve/` vs `/tsolve/`)
- Jeder kann alles aufrufen (generieren, lösen, Worksheets erstellen)
- `feedback_logs`-Code existiert im Backend (Service, DAO, DTO, Controller-Endpoint), aber **die DB-Tabelle fehlt im Init-Script**
- API Base-URL hardcoded auf `http://localhost:8080/api`
- Server-URL: `https://scratch.fim.uni-passau.de/codeowl/`

---

## Bestehende Architektur

### Backend-Packages
```
com.cisternino
├── api/                          # REST Controller
│   ├── TasksController.java      # Alle Endpoints (Tasks, Sequences, Worksheets, Feedback)
│   ├── GlobalExceptionHandler.java
│   └── dto/
│       └── ApiErrorResponse.java
├── application/
│   ├── service/
│   │   ├── TaskGeneratorService.java
│   │   ├── TaskPersistenceService.java
│   │   ├── TaskGenerationStrategyFactory.java
│   │   ├── TaskEnricher.java
│   │   ├── CodeSanitizerService.java
│   │   ├── WorksheetService.java
│   │   ├── SequenceValidator.java
│   │   └── FeedbackLogService.java
│   ├── mapper/
│   │   └── TaskSequenceMapper.java
│   └── util/codeanalyzers/
│       ├── JavaCodeAnalyzer.java
│       └── ComplexityAnalyzer.java
├── config/
│   └── CorsConfig.java
├── domain/
│   ├── entities/                  # DTOs
│   │   ├── TaskDTO.java
│   │   ├── TieredTaskSequenceDTO.java
│   │   ├── WorksheetDTO.java
│   │   ├── FeedbackLogDTO.java
│   │   ├── ConceptDTO.java
│   │   ├── SnippetDTO.java
│   │   └── ... (weitere DTOs)
│   ├── interfaces/                # Repository-Interfaces
│   │   ├── TaskRepository.java
│   │   ├── TieredTaskSequenceRepository.java
│   │   ├── WorksheetRepository.java
│   │   ├── FeedbackLogRepository.java
│   │   └── ... (weitere Repos)
│   ├── exceptions/
│   │   ├── DomainValidationException.java
│   │   └── GenerationException.java
│   └── strategy/                  # Task-Generierungs-Strategien
│       ├── TaskGenerationStrategy.java
│       └── ... (7 Strategie-Implementierungen)
└── infrastructure/
    ├── persistence/
    │   ├── dao/                    # JDBC DAOs
    │   │   ├── PostgreTaskDao.java
    │   │   ├── PostgreWorksheetDao.java
    │   │   ├── PostgreFeedbackLogDao.java
    │   │   └── ... (weitere DAOs)
    │   ├── config/
    │   │   └── JdbcConfig.java
    │   └── ... (RowMapper-Klassen)
    └── ai/                        # AI-Client-Abstraktion
        ├── OpenAiClient.java
        ├── ClaudeClient.java
        ├── GeminiClient.java
        └── ... (Transport, Config, Prompt-Builder)
```

### Frontend-Struktur
```
frontend/src/
├── App.tsx                 # Router + Layout
├── HomePage.tsx            # Aufgaben-Generierung
├── SolvePage.tsx           # Parsons-Puzzle lösen (mit Feedback-Button)
├── SequencePage.tsx        # Sequenz-Ansicht
├── WorksheetPage.tsx       # Worksheet-Ansicht
├── HelpPage.tsx            # Hilfeseiten
├── PersistentLogger.ts     # Client-Side Logging
├── components/
│   └── SequenceView.tsx
├── lib/
│   └── api.ts              # Axios-Instance (baseURL: http://localhost:8080/api)
├── types/
│   └── models.ts           # TypeScript-Interfaces
└── hooks/
    └── usePageTitle.ts
```

### Bestehende API-Endpoints
| Method | Endpoint | Beschreibung |
|--------|----------|--------------|
| POST | `/api/tasks/generate` | Generiert Aufgaben-Sequenz via AI |
| GET | `/api/sequence/{id}` | Sequenz mit Tasks laden |
| GET | `/api/tasks/{taskId}` | Einzelne Aufgabe laden |
| GET | `/api/worksheets/{sheetId}` | Worksheet laden |
| POST | `/api/worksheets` | Worksheet erstellen |
| PUT | `/api/worksheets/{sheetId}` | Worksheet aktualisieren |
| DELETE | `/api/worksheets/{sheetId}` | Worksheet löschen |
| POST | `/api/worksheets/{sheetId}/sequences/{sequenceId}` | Sequenz zu Worksheet hinzufügen |
| POST | `/api/worksheets/{sheetId}/sequences` | Mehrere Sequenzen hinzufügen (Bulk) |
| DELETE | `/api/worksheets/{sheetId}/sequences/{sequenceId}` | Sequenz aus Worksheet entfernen |
| POST | `/api/tasks/{taskId}/feedback-log` | Feedback-Klick loggen |

### Bestehende Frontend-Routen
| Route | Komponente | Beschreibung |
|-------|-----------|--------------|
| `/` | HomePage | Aufgaben-Generierung |
| `/sequence/:id` | SequencePage | Schüler-Sequenzansicht |
| `/tsequence/:id` | SequencePage | Teacher-Sequenzansicht |
| `/solve/:taskId` | SolvePage | Schüler-Aufgabe lösen |
| `/tsolve/:taskId` | SolvePage | Teacher-Aufgabe lösen |
| `/worksheets/:sheetId` | WorksheetPage | Schüler-Worksheet |
| `/tworksheets/:sheetId` | WorksheetPage | Teacher-Worksheet |
| `/help` | HelpPage | Hilfe |
| `/help/:context` | HelpPage | Kontext-spezifische Hilfe |

### Bestehender Feedback-Mechanismus (Code vorhanden, DB-Tabelle fehlt)

**Backend:**
- `FeedbackLogDTO`: `id (UUID)`, `taskId (UUID)`, `correct (boolean)`, `clickedAt (LocalDateTime)`
- `FeedbackLogRepository`: Interface mit `save()`, `findByTaskId()`, `deleteByTaskId()`
- `PostgreFeedbackLogDao`: JDBC-Implementierung mit JdbcTemplate
- `FeedbackLogService`: Transaktional, Validierung, Logging
- `TasksController`: `POST /api/tasks/{taskId}/feedback-log` mit `FeedbackLogRequest { correct: boolean }`

**Frontend (SolvePage.tsx):**
- "Feedback"-Button ruft `widget.getFeedback()` auf (Parsons-Widget)
- Bestimmt ob alle Zeilen korrekt sind
- 500ms Debounce zwischen Klicks
- Sendet `POST /api/tasks/{taskId}/feedback-log` mit `{ correct: allCorrect }`
- Non-blocking, silent on failure

---

## Referenz: OneSports Auth-System

Das OneSports-Projekt (Jakarta EE/JSF) dient als Vorlage für das Auth-System. Folgende Konzepte werden adaptiert:

### OneSports Rollen
1. **Anonymous** – nicht eingeloggt
2. **Registered User** – eingeloggt, Basiszugriff
3. **Organizer** – verwaltet eigene Wettbewerbe
4. **Administrator** – voller Zugriff + Userverwaltung

### OneSports DB-Schema (Referenz)
```sql
CREATE TABLE users (
  userId UUID PRIMARY KEY,
  email VARCHAR(320) UNIQUE NOT NULL,
  firstName VARCHAR(50),
  lastName VARCHAR(50),
  passwordHash VARCHAR(255),
  birthdate DATE,
  gender genderType,
  isAuthenticated BOOLEAN DEFAULT false,
  isAdmin BOOLEAN DEFAULT false
);

CREATE TABLE tokens (
  tokenId UUID PRIMARY KEY,
  userId UUID NOT NULL REFERENCES users(userId) ON DELETE CASCADE,
  expiration DATE NOT NULL,
  newPassword BOOLEAN NOT NULL,
  emailInQueue UUID DEFAULT NULL,
  newEmail VARCHAR(320)
);

CREATE TABLE mailsInQueue (
  mailKey UUID PRIMARY KEY,
  retryCount INT DEFAULT 0,
  userId UUID NOT NULL REFERENCES users(userId) ON DELETE CASCADE,
  text TEXT NOT NULL,
  subjectLine VARCHAR(50) NOT NULL
);
```

### OneSports Features die übernommen werden
- Argon2 Password Hashing
- Token-basierte Email-Verifikation
- Passwort-Reset per Email
- Admin-Userverwaltung (Tabelle mit Suche, Sortierung, Pagination)
- Profilbearbeitung (Name, Email, Passwort ändern)
- Email-Queue mit Retry-Mechanismus

### OneSports Features die NICHT übernommen werden
- JSF/CDI Session Beans → wird Spring Security + JWT
- `isAdmin` Boolean → wird `role` ENUM (ADMIN, TEACHER, STUDENT)
- Contest/Organizer-System → wird Klassen-System

---

## Rollen-System für CodeOwl

### Drei Rollen
| Rolle | Beschreibung |
|-------|-------------|
| **ADMIN** | Voller Zugriff auf alles + Userverwaltung + Admin-Ernennung |
| **TEACHER** | Erstellt Klassen, Schüler, Sequenzen, Worksheets – nur eigene Ressourcen (Ownership) |
| **STUDENT** | Löst zugewiesene Aufgaben, sieht eigenes Profil, kein Zugriff auf Generierung |

### Registrierung & Account-Erstellung
- **Selbstregistrierung** führt zur Lehrerrolle
- **Schüler** werden von Lehrern erstellt (Anton-Prinzip, siehe unten)
- **Admins** werden von bestehenden Admins ernannt

### Anton-Prinzip (Schüler-Erstellung)
1. Lehrer erstellt Schüler mit Vorname + Nachname
2. System generiert kurzen Token (z.B. `ABC-123-XYZ`)
3. Lehrer gibt Token dem Schüler (in der Schule, auf Papier)
4. Schüler geht auf Login-Seite und gibt Token ein
5. Schüler wird aufgefordert: Email-Adresse hinzufügen + bestätigen + Passwort setzen
6. Erst wenn alle Schritte abgeschlossen: Token wird ungültig
7. Ab dann Login mit Email + Passwort

### Ausprobier-Modus (ohne Login)
- Nicht-eingeloggte User können weiterhin Aufgaben **generieren** und **lösen**
- Feedback wird anonym gespeichert (`user_id = NULL`)
- In Analytics werden diese als "Unangemeldet" gruppiert

### Zugriffskontrolle
| Aktion | Anonym | Student | Teacher | Admin |
|--------|--------|---------|---------|-------|
| Aufgaben lösen (via Link) | ✅ | ✅ | ✅ | ✅ |
| Sequenzen generieren | ✅ | ❌ | ✅ | ✅ |
| Worksheets erstellen/bearbeiten | ❌ | ❌ | ✅ (eigene) | ✅ (alle) |
| Fremde Worksheets sehen | ✅ (via Link) | ✅ (via Link) | ✅ | ✅ |
| Fremde Worksheets Klasse zuweisen | ❌ | ❌ | ✅ | ✅ |
| Fremde Worksheets kopieren | ❌ | ❌ | ✅ | ✅ |
| Fremde Worksheets bearbeiten | ❌ | ❌ | ❌ | ✅ |
| Tasks bearbeiten | ❌ | ❌ | ✅ (eigene) | ✅ (alle) |
| Klassen verwalten | ❌ | ❌ | ✅ (eigene) | ✅ (alle) |
| Schüler erstellen | ❌ | ❌ | ✅ | ✅ |
| Userverwaltung | ❌ | ❌ | ❌ | ✅ |
| Admin ernennen | ❌ | ❌ | ❌ | ✅ |

---

## Ownership-Konzept

### Welche Tabellen brauchen Ownership
Folgende Tabellen erhalten eine `owner_id UUID REFERENCES users(userid)` Spalte:
- `tiered_task_sequences` – Sequenz gehört dem Lehrer der sie generiert hat
- `worksheets` – Worksheet gehört dem Lehrer der es erstellt hat
- `tasks` – Task gehört zur Sequenz, Ownership wird über die Sequenz vererbt

### Ownership-Regeln
- **Lehrer sieht/bearbeitet nur eigene** Sequenzen, Worksheets, Tasks
- **Admin sieht/bearbeitet alles**
- **Ohne Ownership** (bestehende Daten vor Auth-System): nur via direktem Link oder für Admins sichtbar
- **Fremde Worksheets:** sehen (ja), Klasse zuweisen (ja), bearbeiten (nein → erst kopieren)

---

## Klassen-System

### Datenbank-Tabellen
```sql
-- Klassen
CREATE TABLE classes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  owner_id UUID NOT NULL REFERENCES users(userid) ON DELETE CASCADE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Schüler-Klassen-Zuordnung (n:m)
CREATE TABLE class_students (
  class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
  student_id UUID NOT NULL REFERENCES users(userid) ON DELETE CASCADE,
  PRIMARY KEY (class_id, student_id)
);

-- Worksheet-Klassen-Zuordnung (n:m)
CREATE TABLE class_worksheets (
  class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
  worksheet_id UUID NOT NULL REFERENCES worksheets(id) ON DELETE CASCADE,
  assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (class_id, worksheet_id)
);
```

### Features
- Lehrer erstellt Klasse (Name)
- Lehrer fügt Schüler hinzu (bestehende oder neu erstellen mit Token)
- Lehrer entfernt Schüler aus Klasse
- Lehrer weist Worksheets einer oder mehreren Klassen zu
- Ein Worksheet kann mehreren Klassen zugewiesen werden
- Schüler sieht nach Login sein Profil (Klassen-Dashboard ist Zukunft)

---

## Worksheet Deep-Copy

### Anwendungsfall
Lehrer A hat ein gutes Worksheet. Lehrer B will es für seine Klasse anpassen. Lehrer B kopiert es → erhält eine eigene Version mit eigenem Ownership.

### Was wird kopiert (Deep-Copy)
1. **Worksheet** → neues Worksheet mit `owner_id` = kopierender Lehrer
2. **Alle Sequenzen** im Worksheet → neue Sequenzen mit neuem `owner_id`
3. **Alle Tasks** in den Sequenzen → neue Tasks

### Was wird NICHT kopiert
- Feedback-Logs (gehören zum Original)
- Zuweisungen zu Klassen

---

## Task-Bearbeitung & Versionierung (AP 9)

### Funktionsweise
1. Lehrer öffnet Task im Teacher-View
2. "Bearbeiten"-Button öffnet Editor für Problemstatement und Code
3. Lehrer bearbeitet und klickt "Speichern"
4. **Beim ersten Speichern:** System prüft ob `task_originals` für diese `task_id` existiert
   - Falls nein: Aktuelle Version wird in `task_originals` gespeichert (einmalig, unveränderlich)
   - Falls ja: Kein erneutes Speichern des Originals
5. **Immer:** Bearbeitete Version überschreibt die `tasks`-Tabelle
6. **Einmalig (nur wenn Original neu gespeichert wurde):** Modal fragt "Wie anpassungsbedürftig war der generierte Content?" (Skala 1-5, wenig bis viel)

### Datenbank
```sql
-- Speichert die ursprünglich generierte Version (einmalig, unveränderlich)
CREATE TABLE task_originals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID UNIQUE NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  original_problem_statement TEXT,
  original_code TEXT,
  adjustment_rating INT CHECK (adjustment_rating BETWEEN 1 AND 5),
  saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Regeln
- **Ohne Ownership:** Bearbeiten-Button ist gesperrt → Lehrer muss erst Worksheet kopieren (AP 4)
- **Rating:** Nur einmal pro Task (beim ersten Speichern/Original-Erstellung)
- **Zweck:** Später analysieren wie stark KI-generierter Content angepasst werden muss

---

## Learning Analytics – Welle 1 (AP 10)

### Design-Entscheidung: Event-basiertes Modell mit JSONB

Statt vieler spezialisierter Tabellen ein generisches Event-System. Die festen Spalten (user_id, task_id, event_type) bleiben relational und indexierbar. Event-spezifische Daten kommen als JSONB, sodass neue Datentypen ohne DB-Migration hinzugefügt werden können – besonders wichtig für Forschung/Paper.

### Datenbank
```sql
CREATE TABLE learning_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID DEFAULT NULL REFERENCES users(userid) ON DELETE SET NULL,
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  sequence_id UUID REFERENCES tiered_task_sequences(id) ON DELETE SET NULL,
  tier_level INT,
  event_type VARCHAR(50) NOT NULL,
  event_data JSONB DEFAULT '{}',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_learning_events_user ON learning_events(user_id);
CREATE INDEX idx_learning_events_task ON learning_events(task_id);
CREATE INDEX idx_learning_events_type ON learning_events(event_type);
CREATE INDEX idx_learning_events_created ON learning_events(created_at);
```

### Event-Typen (Welle 1)

#### `task_opened`
- Trigger: Schüler öffnet eine Aufgabe
- JSONB: `{ "reopened": false }`

#### `task_closed`
- Trigger: Schüler verlässt die Aufgabe (beforeunload / navigation)
- JSONB: `{ "was_correct": false, "time_on_task_ms": 45000 }`

#### `feedback_requested`
- Trigger: Klick auf "Feedback"-Button
- JSONB:
```json
{
  "correct": false,
  "blocks_total": 8,
  "blocks_correct": 5,
  "blocks_incorrect": 3,
  "block_order": ["line3", "line1", "line5", ...],
  "moves_since_last_feedback": 4,
  "attempt_number": 3
}
```

#### `task_correct`
- Trigger: Feedback zeigt 100% korrekt
- JSONB: `{ "total_attempts": 5, "total_moves": 23, "time_to_correct_ms": 120000 }`

#### `block_moved` (aggregiert)
- Wird NICHT bei jedem einzelnen Drag gesendet
- Stattdessen: Zähler wird im Frontend hochgezählt, Wert wird bei `feedback_requested` mitgesendet als `moves_since_last_feedback`

### Schüler-Feedback (Affektiv)
Nach Abschluss einer Sequenz wird der Schüler gefragt:
- "Hat dir die Aufgabe gefallen?" (Likert 1-5)
- "War die Schwierigkeit angemessen?" (Likert 1-5)

Event-Typ: `sequence_rating`
```json
{
  "enjoyment": 4,
  "difficulty_perception": 3
}
```

### Bestehende feedback_logs Tabelle
Die bestehende `feedback_logs`-Tabelle wird um `user_id` erweitert und bleibt als einfacher Korrektheits-Log bestehen. Die `learning_events`-Tabelle ist die detailliertere Ergänzung für Forschungszwecke.

```sql
CREATE TABLE feedback_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  user_id UUID DEFAULT NULL REFERENCES users(userid) ON DELETE SET NULL,
  correct BOOLEAN NOT NULL,
  clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Learning Analytics – Welle 2 (AP 11)

### Zusätzliche Event-Typen

#### `tier_opened` / `tier_closed`
- Genaue Zeitstempel pro Tier-Wechsel
- Erkennung: Hat Schüler Tiers übersprungen? Ist er zu niedrigerem Tier zurückgekehrt?
- JSONB: `{ "from_tier": 1, "to_tier": 3, "skipped": true }`

#### `task_abandoned`
- Schüler verlässt Task ohne correct = true und kommt nicht zurück (Timeout-basiert)
- JSONB: `{ "last_attempt_number": 3, "last_correct_blocks": 4 }`

#### `task_reopened`
- Schüler öffnet einen zuvor besuchten/abgebrochenen Task erneut
- JSONB: `{ "previous_best_correct_blocks": 5, "was_previously_correct": false }`

#### `task_reset`
- Schüler klickt Reset/Shuffle-Button oder lädt Seite neu
- JSONB: `{ "attempt_number_at_reset": 4 }`

### Erweiterte Metriken im Frontend

#### Active-Time-Tracking
- Inaktivitätsgrenze: 90 Sekunden ohne Interaktion
- Frontend trackt aktive Zeitfenster
- Bei `task_closed` wird `active_time_ms` (echte Arbeitszeit) und `total_time_ms` (Kalenderzeit) mitgesendet

#### Backtracking-Erkennung
- System erkennt wenn zuvor korrekt platzierte Blöcke wieder verschoben werden
- Wird bei `feedback_requested` mitgesendet: `"backtrack_count": 2`

#### Block-Level Fehlermuster
- Pro Block tracken: wie oft falsch platziert, welche Paare verwechselt
- Snapshot der Blockpositionen bei jedem `feedback_requested` Event
- Ermöglicht spätere Analyse: Kontrollfluss-Blöcke vs. einfache Statements

#### Fortschritt zwischen Feedback-Ereignissen
- Bei `feedback_requested`: Delta zu vorherigem Feedback
- `"improvement": 2` (2 Blöcke mehr korrekt als beim letzten Mal)
- Oder `"regression": -1` (1 Block weniger korrekt)

### Ableitbare Kennwerte (für Paper)
```
time_to_first_action          – Zeit bis erste Interaktion
time_to_first_feedback        – Zeit bis erster Feedback-Klick
active_time_until_correct     – Echte Arbeitszeit bis korrekt
feedbacks_until_correct       – Anzahl Feedback-Klicks bis korrekt
moves_until_correct           – Gesamte Block-Bewegungen bis korrekt
mean_moves_between_feedback   – Durchschnittliche Moves pro Feedback-Intervall
improvement_rate_after_feedback – Verbesserungsrate nach Feedback
backtracking_count            – Wie oft korrekte Blöcke wieder verschoben
abandonment_rate              – Abbruchrate pro Tier
tier_skip_rate                – Wie oft werden Tiers übersprungen
return_to_lower_tier_rate     – Wie oft Rückkehr zu niedrigerem Tier
error_concentration           – Welche Blöcke besonders fehleranfällig
```

---

## Seiten-Übersicht (nach Umsetzung aller APs)

| Seite | Route | Zugriff | Beschreibung |
|-------|-------|---------|-------------|
| Login | `/login` | Alle | Email+PW oder Token-Einlösung |
| Registrierung | `/register` | Öffentlich | Nur Lehrer-Accounts |
| Passwort vergessen | `/forgot-password` | Öffentlich | Email-basiert |
| Passwort zurücksetzen | `/reset-password` | Via Email-Link | Neues Passwort setzen |
| Email verifizieren | `/verify-email` | Via Email-Link | Email bestätigen |
| Token-Onboarding | `/onboarding` | Schüler mit Token | Email + PW setzen |
| Profil | `/profile` | Eingeloggt | Eigene Daten, Email, PW ändern |
| Homepage / Generierung | `/` | Alle außer eingeloggte Schüler | Aufgaben generieren |
| Sequenz-Ansicht | `/sequence/:id` | Alle | Schüler-Ansicht |
| Teacher-Sequenz | `/tsequence/:id` | Teacher, Admin | Mit Bearbeiten-Button |
| Aufgabe lösen | `/solve/:taskId` | Alle | Parsons-Puzzle |
| Teacher-Aufgabe | `/tsolve/:taskId` | Teacher, Admin | Mit Musterlösung + Bearbeiten |
| Worksheet | `/worksheets/:sheetId` | Alle | Schüler-Ansicht |
| Teacher-Worksheet | `/tworksheets/:sheetId` | Teacher, Admin | Mit Verwaltung |
| Klassen-Übersicht | `/classes` | Teacher, Admin | Eigene Klassen |
| Klassen-Detail | `/classes/:classId` | Teacher (eigene), Admin | Schüler + Worksheets |
| Userverwaltung | `/admin/users` | Nur Admin | Alle User verwalten |
| Hilfe | `/help/:context` | Alle | Kontextbasierte Hilfe |

---

## Mail-Konfiguration

### SMTP-Setup
```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=noreply.codeowl@gmail.com
MAIL_PASSWORD=<Google App-Passwort>
```

**Wichtig:** Gmail erfordert ein **App-Passwort** (Google Konto → Sicherheit → 2FA aktivieren → App-Passwörter). Normales Gmail-Passwort funktioniert nicht mit SMTP.

### Verwendungszwecke
- Email-Verifikation nach Registrierung (Lehrer)
- Email-Verifikation nach Schüler-Onboarding
- Passwort-Reset-Links

---

## AP 12: Change-Analysis-Engine (Task-Veränderungsanalyse)

**Ziel:** Automatisierte, mehrdimensionale Analyse der Unterschiede zwischen KI-generiertem Original und Lehrer-Bearbeitung — für quantitative Auswertung im Paper.

**Abhängigkeiten:** AP 9 (Task-Bearbeitung + `task_originals` Tabelle)

### Zwei Analyse-Ebenen

#### Ebene 1: Syntaktisch (String/Token-basiert)
Reine Text-Operationen, kein externer Service nötig.

**Problem Statement (Aufgabentext):**
- Levenshtein-Distanz (normalisiert 0.0-1.0)
- Wort-Level Diff (added / removed / modified)
- Jaccard-Similarity auf Wortmengen
- Satz-Level Diff (welche Sätze neu, welche entfernt, welche umformuliert)
- Textlängen-Delta (Wörter, Zeichen)

**Code (Solution):**
- Zeilen-Level Diff (added / removed / modified / reordered)
- Token-Level Diff (sprachunabhängig, Whitespace-normalisiert)
- Cyclomatic-Complexity-Delta (existiert schon im System)
- Lines-of-Code-Delta
- Indentation-Change-Detection (nur Formatting vs. echte Änderung)

#### Ebene 2: Strukturell (AST-basiert)
Parser-basierte Analyse — erkennt ob eine Änderung kosmetisch oder strukturell ist.

**Pro Sprache ein AST-Parser:**
- Java: JavaParser (Open Source, Maven-Dependency)
- Python: tree-sitter-python oder javacc-basiert
- Erweiterbar für weitere Sprachen

**AST-Metriken:**
- AST-Node-Diff: Welche Nodes hinzugefügt/entfernt/verändert
- Structural Change Score: Gewichtete Summe (Kontrollfluss-Änderung wiegt schwerer als Variablenumbenennung)
- Change-Klassifikation pro Node:
  - `RENAME` — Variable/Funktion umbenannt
  - `REORDER` — Reihenfolge geändert (bei Parsons besonders relevant!)
  - `ADD_LOGIC` — Neuer Kontrollfluss (if/for/while hinzugefügt)
  - `REMOVE_LOGIC` — Kontrollfluss entfernt
  - `MODIFY_EXPRESSION` — Ausdruck geändert
  - `COSMETIC` — Nur Whitespace/Kommentare
- Distractor-Analyse: Hat der Lehrer Distraktoren (falsche Zeilen) verändert/hinzugefügt/entfernt?
- Parsons-spezifisch: Block-Reihenfolge Original vs. Bearbeitung — hat der Lehrer die Lösungsstruktur verändert?

### Datenbank

```sql
CREATE TABLE task_change_analyses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id UUID UNIQUE NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  task_original_id UUID NOT NULL REFERENCES task_originals(id) ON DELETE CASCADE,
  analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  -- Ebene 1: Syntaktisch (Text)
  text_levenshtein_ratio DOUBLE PRECISION,
  text_jaccard_similarity DOUBLE PRECISION,
  text_words_added INT,
  text_words_removed INT,
  text_words_modified INT,
  text_length_delta INT,

  -- Ebene 1: Syntaktisch (Code)
  code_levenshtein_ratio DOUBLE PRECISION,
  code_lines_added INT,
  code_lines_removed INT,
  code_lines_modified INT,
  code_lines_reordered INT,
  code_loc_delta INT,
  code_complexity_delta INT,
  code_token_change_ratio DOUBLE PRECISION,

  -- Ebene 2: Strukturell (AST)
  ast_nodes_added INT,
  ast_nodes_removed INT,
  ast_nodes_modified INT,
  ast_structural_score DOUBLE PRECISION,
  ast_change_types JSONB DEFAULT '[]',
  ast_distractor_changes JSONB DEFAULT '{}',

  -- Composite
  overall_change_score DOUBLE PRECISION,
  change_category VARCHAR(20),

  -- Subjektiv (Referenz)
  teacher_rating INT
);

CREATE INDEX idx_change_analyses_task ON task_change_analyses(task_id);
CREATE INDEX idx_change_analyses_category ON task_change_analyses(change_category);
```

### Composite Score Formel

```
overall_change_score =
    w1 * text_change_component +
    w2 * code_change_component +
    w3 * structural_change_component

Gewichte (konfigurierbar):
  w1 = 0.25  (Aufgabentext)
  w2 = 0.35  (Code syntaktisch)
  w3 = 0.40  (Code strukturell / AST)
```

### Change-Kategorien (ableitbar)

| Kategorie | Kriterium |
|-----------|-----------|
| `NONE` | Diff = 0 |
| `COSMETIC` | Nur Whitespace/Formatting/Kommentare |
| `MINOR` | Variablennamen, Tippfehler, kleine Textänderungen |
| `MAJOR` | Logik-Änderungen, Zeilen hinzugefügt/entfernt |
| `REWRITE` | >70% des Codes geändert |

### Backend-Architektur

```
com.cisternino.application.service.analysis/
├── ChangeAnalysisService.java          -- Orchestriert alle Ebenen
├── SyntacticAnalyzer.java              -- Ebene 1: Levenshtein, Jaccard, Diff
├── AstAnalyzer.java                    -- Ebene 2: AST-Parsing + Diff
│   ├── JavaAstAnalyzer.java            -- JavaParser-basiert
│   └── PythonAstAnalyzer.java          -- tree-sitter
├── ChangeScoreCalculator.java          -- Composite Score berechnen
└── dto/
    └── TaskChangeAnalysisDTO.java
```

### API-Endpoints

| Method | Endpoint | Beschreibung |
|--------|----------|-------------|
| POST | `/api/analysis/task/{taskId}` | Analyse für einen Task auslösen (on-demand) |
| POST | `/api/analysis/batch` | Batch-Analyse über alle bearbeiteten Tasks (Admin only) |
| GET | `/api/analysis/task/{taskId}` | Analyse-Ergebnis abrufen |
| GET | `/api/analysis/export` | CSV/JSON Export aller Analysen (für SPSS/R) |
| GET | `/api/analysis/summary` | Aggregierte Statistiken (Durchschnitte, Verteilungen) |

### Beantwortbare Forschungsfragen

1. **Korrelation subjektiv ↔ objektiv:** Stimmt das Lehrer-Rating (1-5) mit dem automatischen Change-Score überein?
2. **Änderungstypen:** Werden primär Aufgabentexte oder Code geändert?
3. **Tier-Abhängigkeit:** Werden Tier-1-Tasks (einfach) mehr oder weniger bearbeitet als Tier-3?
4. **Sprachabhängigkeit:** Werden Java-Tasks mehr bearbeitet als Python?
5. **Strukturell vs. Kosmetisch:** Wie viel % der Änderungen sind echte Logik-Änderungen?
6. **Distractor-Qualität:** Wie oft werden KI-generierte Distraktoren vom Lehrer geändert?
7. **LLM-Qualität über Zeit:** Werden neuere Generierungen weniger bearbeitet?

---

## JWT-Konfiguration

### Was ist der JWT Secret?
Ein geheimer Schlüssel mit dem das Backend JWT-Tokens signiert. Wer das Secret kennt, kann gültige Tokens für jeden User erzeugen.

### Anforderungen
- Mindestens 256 Bit (für HS256)
- Zufällig generiert, kein Wort/Muster
- Nie in Git committen

### Generierung
```powershell
# PowerShell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Max 256 }) -as [byte[]])
```

### Konfiguration
```env
JWT_SECRET=<generierter-key>
APP_BASE_URL=https://scratch.fim.uni-passau.de/codeowl/
CORS_ALLOWED_ORIGINS=https://scratch.fim.uni-passau.de
VITE_API_BASE_URL=/codeowl/api
```

---

## Arbeitspakete

### AP 1: Auth-System (Backend + DB) ✅
**Ziel:** Benutzer können sich registrieren, einloggen und ihre Email/Passwort verwalten.

**DB-Änderungen:**
- `users` Tabelle: `userid (UUID PK)`, `email (VARCHAR UNIQUE)`, `first_name`, `last_name`, `password_hash`, `role (ENUM: ADMIN, TEACHER, STUDENT)`, `is_authenticated (BOOLEAN)`, `created_at`
- `tokens` Tabelle: Für Email-Verifikation, Passwort-Reset, Schüler-Einladung
- `mails_in_queue` Tabelle: Email-Queue mit Retry (aus OneSports)

**Backend:**
- Spring Security + JWT-Filter
- Argon2 Password Hashing (aus OneSports)
- `AuthController` mit Endpoints:
  - `POST /api/auth/register` (nur Lehrer)
  - `POST /api/auth/login` (Email + PW)
  - `POST /api/auth/token-login` (Schüler-Token einlösen)
  - `POST /api/auth/verify-email`
  - `POST /api/auth/forgot-password`
  - `POST /api/auth/reset-password`
  - `POST /api/auth/onboarding` (Schüler: Email + PW setzen)
- `SecurityConfig`: Welche Endpoints public, welche authentifiziert
- `JwtService`: Token generieren + validieren
- `EmailService`: Verifikation + Reset Mails senden

**Abhängigkeiten:** Keine (Basis für alles andere)

---

### AP 2: Rollen & Ownership (DB + Backend) ✅
**Ziel:** Zugriffskontrolle basierend auf Rollen und Ressourcen-Ownership.

**DB-Änderungen:**
- `owner_id UUID REFERENCES users(userid)` auf:
  - `tiered_task_sequences`
  - `worksheets`
- Bestehende Daten: `owner_id = NULL` (nur via Link oder Admin zugänglich)

**Backend:**
- Role-basierte Method-Security (`@PreAuthorize`)
- Ownership-Check in Services: Lehrer darf nur eigene Ressourcen bearbeiten
- Eingeloggte Schüler: Generierungs-Endpoints gesperrt
- Admin: Alle Checks überspringen

**Abhängigkeiten:** AP 1

---

### AP 3: Klassen-System (DB + Backend + Frontend) ✅
**Ziel:** Lehrer können Klassen erstellen, Schüler verwalten und Worksheets zuweisen.

**DB-Änderungen:**
- `classes` Tabelle
- `class_students` Tabelle (n:m)
- `class_worksheets` Tabelle (n:m)

**Backend-Endpoints:**
- `POST /api/classes` – Klasse erstellen
- `GET /api/classes` – Eigene Klassen laden
- `GET /api/classes/{id}` – Klasse mit Schülern + Worksheets
- `PUT /api/classes/{id}` – Klasse bearbeiten
- `DELETE /api/classes/{id}` – Klasse löschen
- `POST /api/classes/{id}/students` – Schüler erstellen (gibt Token zurück)
- `DELETE /api/classes/{id}/students/{studentId}` – Schüler entfernen
- `POST /api/classes/{id}/worksheets/{worksheetId}` – Worksheet zuweisen
- `DELETE /api/classes/{id}/worksheets/{worksheetId}` – Zuweisung entfernen

**Frontend:**
- Klassen-Übersicht Seite
- Klassen-Detail Seite (Schülerliste mit Token-Anzeige, Worksheet-Liste)

**Abhängigkeiten:** AP 1, AP 2

---

### AP 4: Worksheet-Kopie & Fremd-Zugriff ✅
**Ziel:** Lehrer können fremde Worksheets sehen, ihrer Klasse zuweisen und kopieren.

**Backend:**
- `POST /api/worksheets/{id}/copy` – Deep-Copy erstellen
  - Kopiert Worksheet + alle Sequenzen + alle Tasks
  - Neuer `owner_id` = aktueller User
  - Neue UUIDs für alles
- Fremd-Worksheets: Sehen ja, Klasse zuweisen ja, Bearbeiten nein

**Frontend:**
- "Kopieren"-Button auf fremden Worksheets
- "Zuweisen"-Button (wähle Klasse)

**Abhängigkeiten:** AP 2, AP 3

---

### AP 5: Feedback-Logs erweitern ✅
**Ziel:** Feedback-Logs mit User-Zuordnung für Fortschritts-Tracking.

**DB-Änderungen:**
- `feedback_logs` Tabelle erstellen (fehlt im Init-Script!) mit `user_id UUID DEFAULT NULL`
- Index auf `user_id`

**Backend:**
- `FeedbackLogDTO` um `userId` erweitern
- `PostgreFeedbackLogDao`: `user_id` beim INSERT mitgeben
- `FeedbackLogService.logFeedbackClick()`: `userId` Parameter (nullable)
- Controller: `user_id` aus JWT extrahieren (oder NULL wenn nicht eingeloggt)

**Frontend:**
- Keine sichtbare Änderung – JWT wird automatisch mitgesendet (AuthContext)

**Teacher-View:**
- Fortschritt pro Schüler pro Task anzeigen
- Anonyme Einträge (`user_id = NULL`) als "Unangemeldet" gruppieren

**Abhängigkeiten:** AP 1

---

### AP 6: Frontend Auth-Seiten ✅
**Ziel:** Alle Auth-bezogenen Frontend-Seiten und Flows.

**Neue Seiten:**
- `LoginPage` – Email+PW Login + Token-Einlösung
- `RegisterPage` – Lehrer-Registrierung
- `ForgotPasswordPage` – Email eingeben
- `ResetPasswordPage` – Neues PW setzen (via Token-Link)
- `VerifyEmailPage` – Email bestätigen (via Token-Link)
- `OnboardingPage` – Schüler-Flow: Email hinzufügen + bestätigen + PW setzen
- `ProfilePage` – Eigene Daten, Email ändern, PW ändern

**AuthContext:**
- JWT in localStorage / Memory
- Auto-Refresh
- `useAuth()` Hook mit `user`, `isLoggedIn`, `role`, `login()`, `logout()`
- Axios-Interceptor: JWT automatisch an Requests hängen

**ProtectedRoute:**
- Wrapper-Komponente die auf Rolle prüft
- Redirect zu `/login` wenn nicht eingeloggt

**Abhängigkeiten:** AP 1

---

### AP 7: Admin-Bereich ✅
**Ziel:** Admins können User verwalten und Admins ernennen.

**Frontend:**
- Userverwaltung-Seite (adaptiert aus OneSports):
  - Tabelle aller User mit Suche, Sortierung, Pagination
  - User bearbeiten (Name, Email, Rolle)
  - User löschen
  - Admin-Status vergeben/entziehen
  - Passwort zurücksetzen (sendet Reset-Email)

**Backend-Endpoints:**
- `GET /api/admin/users` – Alle User (paginiert, suchbar)
- `PUT /api/admin/users/{id}` – User bearbeiten
- `DELETE /api/admin/users/{id}` – User löschen
- `POST /api/admin/users/{id}/make-admin` – Admin ernennen
- `POST /api/admin/users/{id}/reset-password` – PW-Reset auslösen

**Abhängigkeiten:** AP 1, AP 2

---

### AP 8: Navigation & Routing ✅
**Ziel:** Rollenbasierte Navigation und korrekte Redirects.

**Änderungen:**
- Navbar/Header mit rollenbasierten Links:
  - Anonym: Home, Login
  - Student: Profil, Logout (kein "Generieren")
  - Teacher: Home, Klassen, Profil, Logout
  - Admin: Home, Klassen, Userverwaltung, Profil, Logout
- ProtectedRoutes für alle geschützten Seiten
- Redirect nach Login:
  - Student → `/profile`
  - Teacher → `/` (Homepage)
  - Admin → `/` (Homepage)

**Abhängigkeiten:** AP 6

---

### AP 9: Task-Bearbeitung & Versionierung ✅
**Ziel:** Lehrer können Tasks bearbeiten, Originale werden für Forschung gespeichert.

**DB-Änderungen:**
- `task_originals` Tabelle (siehe oben)

**Backend:**
- `PUT /api/tasks/{taskId}` – Task bearbeiten (Problemstatement + Code)
  - Ownership-Check
  - Beim ersten Mal: Original in `task_originals` speichern
  - Rating (1-5) als Parameter beim ersten Speichern
- `GET /api/tasks/{taskId}/original` – Original-Version laden (für Vergleich)

**Frontend:**
- "Bearbeiten"-Button im Teacher-View (nur bei Ownership)
- Editor/Modal für Problemstatement (Textarea) und Code (Code-Editor)
- "Speichern"-Button
- Beim ersten Speichern: Modal "Wie anpassungsbedürftig war der generierte Content?" (1-5)
- Button gesperrt bei fremden Tasks → Hinweis "Erst kopieren"

**Abhängigkeiten:** AP 2, AP 4

---

### AP 10: Learning Analytics – Welle 1 (Event-System + Kern-Events) ✅
**Ziel:** Event-basiertes Tracking-System für Forschungsdaten aufbauen.

**DB-Änderungen:**
- `learning_events` Tabelle mit JSONB (siehe oben)

**Backend:**
- `POST /api/events` – Learning Event speichern
- `LearningEventService` + `LearningEventRepository` + DAO
- User-ID aus JWT (nullable)

**Frontend:**
- Event-Tracking in `SolvePage.tsx`:
  - `task_opened` bei Mount
  - `task_closed` bei Unmount / beforeunload
  - `feedback_requested` bei Feedback-Klick (mit Snapshot)
  - `task_correct` bei erster korrekter Lösung
  - Move-Counter zwischen Feedback-Klicks
- `sequence_rating` nach Sequenz-Abschluss (Likert-Skala Modal)

**Abhängigkeiten:** AP 1 (für user_id), AP 5 (ergänzt feedback_logs)

---

### AP 11: Learning Analytics – Welle 2 (Erweiterte Metriken) ✅
**Ziel:** Tiefere Verhaltensanalyse für Paper und Forschung.

**Neue Event-Typen:**
- `tier_opened` / `tier_closed` – Tier-Transitionen
- `task_abandoned` – Abbruch-Erkennung
- `task_reopened` – Wiederbesuche
- `task_reset` – Reset/Shuffle/Reload

**Frontend-Erweiterungen:**
- Active-Time-Tracking (Inaktivitätsgrenze 90s)
- Backtracking-Erkennung (korrekte Blöcke wieder verschoben)
- Block-Level Fehlermuster (welche Blöcke oft falsch)
- Fortschritt/Rückschritt zwischen Feedback-Events (Delta)
- `time_to_first_action` tracken (erster Drag nach Task-Open)

**Ableitbare Kennwerte:**
- `time_to_first_action`, `time_to_first_feedback`, `active_time_until_correct`
- `feedbacks_until_correct`, `moves_until_correct`, `mean_moves_between_feedback`
- `improvement_rate_after_feedback`, `backtracking_count`
- `abandonment_rate`, `tier_skip_rate`, `return_to_lower_tier_rate`
- `error_concentration` (pro Block)

**Abhängigkeiten:** AP 10

---

### AP 13: Hint-System (Deterministische Static-Analysis + LLM-Erklaerung) ✅
**Ziel:** Schueler erhalten bei Schwierigkeiten gestufte, zuverlaessige Hilfestellungen. Die Diagnose erfolgt deterministisch ueber Static-Analysis-Tools (kein LLM-Halluzinationsrisiko). Optional kann ein LLM die Diagnose schuelergerecht erklaeren. Help-Seeking-Verhalten wird strukturiert erfasst fuer Paper-v2-Analysen.

**Design-Entscheidung (nach Ruecksprache mit Florian):**
> "Ich habs in Lernszenarien gern moeglichst deterministisch, dass man Schuelern keinen Muell erzaehlt."

Deshalb: **Deterministische Tools zuerst, LLM nur fuer Erklaerung.**
Die Architektur orientiert sich an Artemis (TU Muenchen), das ebenfalls statische Codeanalyse als Feedback-Kanal in Programmieraufgaben nutzt.

#### Wie funktionieren Hints bei Parsons-Problemen?

Bei Parsons-Problemen ist der Code bereits geschrieben — Schueler ordnen Bloecke an. Die Schuelerloesung ist also eine **konkrete Code-Datei** (die Bloecke konkateniert in der gewaehlten Reihenfolge). Diese Datei wird durch Static-Analysis-Tools geschickt:

1. **Schuelerloesung zusammenbauen:** Block-Reihenfolge → vollstaendige Code-Datei
2. **Tool laufen lassen:** Compiler / Ruff / SpotBugs → strukturierte Findings (SARIF)
3. **Findings auf Bloecke zurueckmappen:** Zeilennummern → Block-IDs
4. **Hint generieren:** Finding → paedagogischer Hint + betroffene Bloecke highlighten
5. **Optional "Erklaer es genauer":** Finding + Code an LLM → schuelergerechte Erklaerung

#### Analyse-Pipeline pro Sprache

##### Python: Ruff
[Ruff](https://docs.astral.sh/ruff/) ist ein extrem schneller Python-Linter.

```
Schuelerloesung.py → ruff check --output-format sarif → SARIF parsen → Hints
```

**Relevante Ruff-Rules fuer Anfaenger:**
| Rule ID | Name | Parsons-Relevanz |
|---------|------|-----------------|
| F821 | `undefined-name` | Variable verwendet bevor definiert → **Block-Reihenfolge falsch** |
| F823 | `undefined-local` | Lokale Variable vor Zuweisung referenziert → **Block fehlt oder zu spaet** |
| F841 | `unused-variable` | Variable definiert aber nie verwendet → **Block zu frueh oder ueberfluessig (Distractor?)** |
| E999 | `syntax-error` | Syntaxfehler → **Block fehlt oder falsche Position** |
| F811 | `redefined-unused` | Variable doppelt definiert → **Distractor-Block in Loesung** |
| W291 | `trailing-whitespace` | (ignorieren — nicht paedagogisch relevant) |

**Beispiel-Flow:**
```python
# Schueler-Anordnung (falsch):
result = x + y        # Block 2 (benutzt x, y)
x = 5                 # Block 0
y = 10                # Block 1
print(result)         # Block 3
```
→ Ruff Finding: `F821: Undefined name 'x'` auf Zeile 1
→ Zurueckmappen: Zeile 1 = Block 2
→ Hint: "Ein Block verwendet eine Variable, die zu diesem Zeitpunkt noch nicht existiert."
→ Block 2 wird **rot hervorgehoben** (pulsierender Rahmen)

##### Java: javac (Compiler) + SpotBugs (Bytecode-Analyse)

Java erfordert **zwei Stufen**, weil SpotBugs auf Bytecode (.class) arbeitet:

**Stufe 1: Compiler-Diagnosen (javac)**
```
Schuelerloesung.java → javac → Compiler-Errors/-Warnings → Hints
```

Der Java-Compiler erzwingt **definite assignment** — genau das Beispiel "Variable vor Initialisierung verwendet" wird hier gefangen:
```
error: variable x might not have been initialized
```

Wenn der Code nicht kompiliert, kommt SpotBugs gar nicht zum Einsatz. Compiler-Fehler sind fuer Anfaenger ohnehin die haeufigsten Probleme.

**Stufe 2: SpotBugs (nur wenn kompilierbar)**
```
Schuelerloesung.java → javac → .class → java -jar spotbugs.jar -sarif → SARIF → Hints
```

SpotBugs prueft 400+ Bug Patterns auf Bytecode-Ebene:
| Bug Pattern | Beschreibung | Parsons-Relevanz |
|-------------|-------------|-----------------|
| NP_NULL_ON_SOME_PATH | Null-Pointer-Zugriff moeglich | Block-Reihenfolge: null-check fehlt vor Zugriff |
| DLS_DEAD_LOCAL_STORE | Wert wird zugewiesen aber nie gelesen | Distractor-Block oder Block zu frueh |
| UC_USELESS_CONDITION | Bedingung ist immer true/false | Logik-Block an falscher Stelle |
| RCN_REDUNDANT_NULLCHECK | Redundanter Null-Check | Distractor-Block |

**SpotBugs wird als JAR im Docker-Container mitgeliefert** und per ProcessBuilder aufgerufen.

##### SARIF als einheitliches Austauschformat

Sowohl Ruff als auch SpotBugs koennen **SARIF 2.1.0** (Static Analysis Results Interchange Format) ausgeben. Das ermoeglicht eine einheitliche Parser-Pipeline:

```
Ruff    → SARIF JSON  ─┐
javac   → (eigener Parser) ──→  Unified Finding ──→ Hint-Generator ──→ Hint + Block-Highlight
SpotBugs → SARIF JSON  ─┘
```

```java
// Unified Finding (sprachunabhängig)
public record AnalysisFinding(
    String ruleId,           // "F821", "NP_NULL_ON_SOME_PATH", "compiler:not-initialized"
    String message,          // "Undefined name 'x'"
    String severity,         // "error", "warning", "info"
    int startLine,           // Zeilennummer im zusammengebauten Code
    int endLine,
    String tool,             // "ruff", "javac", "spotbugs"
    String category          // "undefined_variable", "null_safety", "dead_code", "syntax"
) {}
```

#### Hint-Stufen

| Stufe | Name | Was passiert | Quelle |
|-------|------|-------------|--------|
| 0 | Standard-Feedback | Richtig/Falsch pro Block (existiert schon) | js-parsons Widget |
| 1 | **Linter-Hint** | Zeigt an WELCHE Bloecke problematisch sind + **highlightet sie** + kurze Meldung ("Hier wird eine Variable verwendet, die noch nicht definiert ist") | Ruff / javac / SpotBugs → Finding → Hint-Template |
| 2 | **"Erklaer es genauer"** | LLM erklaert das Finding schuelergerecht + highlightet die betroffenen Bloecke mit Erklaerungstext | Finding + Code → LLM → Erklaerung |

**Stufe 1 ist deterministisch** — kein LLM, kein Halluzinationsrisiko.
**Stufe 2 ist LLM-basiert** — aber das LLM bekommt das bereits verifizierte Finding als Input, nicht den rohen Code. Es erklaert nur, es diagnostiziert nicht.

#### Block-Highlighting (Frontend)

Bei Stufe 1 und 2 werden die betroffenen Bloecke visuell hervorgehoben:

**Stufe 1 (Linter-Hint):**
- Betroffene Bloecke bekommen einen **roten pulsierenden Rahmen**
- Tooltip beim Hover zeigt die Kurzversion des Findings
- Banner oben: "Es gibt ein Problem mit der Reihenfolge. Schau dir die markierten Bloecke an."

**Stufe 2 ("Erklaer es genauer"):**
- Gleiche Block-Highlights wie Stufe 1
- Zusaetzlich: **Erklaerungsbox** unter dem Puzzle mit LLM-generierter Erklaerung
- Erklaerung referenziert die markierten Bloecke: "Der rot markierte Block **`result = x + y`** verwendet die Variablen `x` und `y`. Aber `x` wird erst weiter unten definiert. In Python wird Code von oben nach unten ausgefuehrt — du musst also erst `x` einen Wert geben, bevor du ihn verwenden kannst."
- Die in der Erklaerung referenzierten Bloecke bekommen zusaetzlich ein **Label** (z.B. "A", "B") das in der Erklaerung verwendet wird

**CSS-Klassen:**
```css
.block-hint-error {
  border: 2px solid #dc3545;
  animation: pulse-red 1.5s infinite;
  position: relative;
}
.block-hint-error::after {
  content: attr(data-hint-label);
  position: absolute;
  top: -8px;
  right: -8px;
  background: #dc3545;
  color: white;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
```

#### Backend-Architektur

```
com.cisternino.application.service.hints/
├── HintService.java                    -- Orchestriert die Pipeline
├── CodeAssembler.java                  -- Block-Reihenfolge → Code-Datei + Zeilen-Mapping
├── analysis/
│   ├── AnalysisFinding.java            -- Einheitliches Finding-Format
│   ├── SarifParser.java                -- SARIF JSON → List<AnalysisFinding>
│   ├── RuffAnalyzer.java               -- Python: ruft ruff check auf
│   ├── JavacAnalyzer.java              -- Java: ruft javac auf, parst Compiler-Output
│   └── SpotBugsAnalyzer.java           -- Java: ruft SpotBugs JAR auf (nur wenn kompilierbar)
├── HintGenerator.java                  -- Finding → paedagogischer Hint-Text (Templates)
├── HintExplainer.java                  -- Finding + Code → LLM-Erklaerung (Stufe 2)
└── BlockMapper.java                    -- Zeilennummern → Block-IDs zurueckmappen
```

**HintService.java Flow:**
```java
public HintResponse getHint(UUID taskId, List<String> blockOrder, int hintLevel, UUID userId) {
    // 1. Task laden (Sprache, Loesung, Bloecke)
    TaskDTO task = taskRepository.findById(taskId);
    String language = task.getLanguage(); // "java" oder "python"

    // 2. Schuelerloesung zusammenbauen
    CodeAssembly assembly = codeAssembler.assemble(blockOrder, task);
    // assembly.code = vollstaendiger Code
    // assembly.lineToBlockMap = {1: "block_2", 2: "block_0", ...}

    // 3. Static Analysis ausfuehren
    List<AnalysisFinding> findings;
    if ("python".equals(language)) {
        findings = ruffAnalyzer.analyze(assembly.code);
    } else if ("java".equals(language)) {
        findings = javacAnalyzer.analyze(assembly.code);
        if (findings.stream().noneMatch(f -> "error".equals(f.severity()))) {
            // Nur wenn kompilierbar: SpotBugs zusaetzlich
            findings.addAll(spotBugsAnalyzer.analyze(assembly.code));
        }
    }

    // 4. Findings auf Bloecke zurueckmappen
    List<BlockFinding> blockFindings = blockMapper.map(findings, assembly.lineToBlockMap);

    // 5. Hint generieren
    if (hintLevel == 1) {
        String hintText = hintGenerator.generate(blockFindings);
        return new HintResponse(hintText, blockFindings.affectedBlockIds(), null);
    } else if (hintLevel == 2) {
        String explanation = hintExplainer.explain(blockFindings, assembly.code, language);
        return new HintResponse(null, blockFindings.affectedBlockIds(), explanation);
    }
}
```

#### Endpoints

| Method | Endpoint | Beschreibung | Auth |
|--------|----------|-------------|------|
| POST | `/api/hints/{taskId}` | Hint anfordern | Public (anonym erlaubt) |
| | Request: `{ "blockOrder": [...], "hintLevel": 1 }` | | |
| | Response: `{ "hintText": "...", "affectedBlocks": ["block_2"], "explanation": null, "findings": [...], "creditsRemaining": 4 }` | | |
| GET | `/api/hints/{taskId}/config` | Hint-Konfiguration laden | Public |
| | Response: `{ "hintsEnabled": true, "totalCredits": 5, "creditsUsed": 2 }` | | |

#### Hint-Credit-System (konfigurierbar)

- Pro Task: N Hint-Credits (Default: 5)
- Stufe 1 (Linter-Hint): **1 Credit**
- Stufe 2 (LLM-Erklaerung): **2 Credits**
- Lehrer konfiguriert pro Worksheet: Hints an/aus, Credits pro Task
- Credits aufgebraucht → keine weiteren Hints (erzwingt eigenstaendiges Denken)
- Credits werden **nicht** sequenzuebergreifend uebertragen

#### Datenbank

```sql
-- Hint-Konfiguration pro Worksheet
ALTER TABLE worksheets ADD COLUMN IF NOT EXISTS hints_enabled BOOLEAN DEFAULT true;
ALTER TABLE worksheets ADD COLUMN IF NOT EXISTS hint_credits_per_task INT DEFAULT 5;

-- Hint-Nutzung und Findings
CREATE TABLE IF NOT EXISTS hint_usage (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID DEFAULT NULL REFERENCES users(userid) ON DELETE SET NULL,
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  hint_level INT NOT NULL CHECK (hint_level BETWEEN 1 AND 2),
  credits_spent INT NOT NULL,
  credits_remaining INT NOT NULL,
  block_order_at_request JSONB DEFAULT '[]',
  findings JSONB DEFAULT '[]',
  hint_text TEXT,
  llm_explanation TEXT,
  affected_block_ids JSONB DEFAULT '[]',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hint_usage_user ON hint_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_hint_usage_task ON hint_usage(task_id);
```

#### Docker-Integration

**Ruff:** Bereits als Python-Package verfuegbar, wird im Backend-Container installiert:
```dockerfile
RUN pip install ruff
```

**SpotBugs:** Als JAR im Container:
```dockerfile
RUN curl -L -o /opt/spotbugs.jar https://repo1.maven.org/maven2/com/github/spotbugs/spotbugs/4.8.6/spotbugs-4.8.6.jar
```

**javac:** Bereits im JDK 21 (Backend-Container basiert auf Eclipse Temurin JDK 21).

Alle Tools werden per `ProcessBuilder` aufgerufen mit Timeout (5 Sekunden) und in temporaeren Verzeichnissen ausgefuehrt.

#### Frontend

**SolvePage Erweiterungen:**
- "Hint"-Button (nur sichtbar wenn `hintsEnabled`)
  - Erster Klick → Stufe 1 (Linter-Hint): Banner + betroffene Bloecke rot highlighten
  - "Erklaer es genauer"-Button erscheint → Stufe 2: LLM-Erklaerung in Box unter dem Puzzle
- Credit-Anzeige: "💡 3/5"
- Hint verschwindet nach naechstem Block-Move (Schueler soll aktiv werden)
- Wenn keine Findings (Code ist korrekt angeordnet aber anders als Musterloesung): Fallback auf positionsbasierten Hint ("Alle Bloecke sehen syntaktisch korrekt aus — pruefe die Reihenfolge der Ausfuehrung")

**Block-Highlighting via js-parsons Integration:**
- `widget.getBlockElements()` liefert die DOM-Elemente der Bloecke
- Nach Hint-Response: `affectedBlocks` IDs → DOM-Elemente → CSS-Klasse `.block-hint-error` hinzufuegen
- Bei Stufe 2: zusaetzlich `data-hint-label="A"` Attribut setzen

**Lehrer-Konfiguration (WorksheetPage Teacher-View):**
- Checkbox "Hints aktivieren"
- Number-Input "Credits pro Task" (1-10, Default: 5)

#### Learning Events (automatisch geloggt)

##### `hint_requested`
```json
{
  "hintLevel": 1,
  "creditsSpent": 1,
  "creditsRemaining": 4,
  "timeSinceLastAction": 15000,
  "timeSinceLastFeedback": 8000,
  "feedbackCountBeforeHint": 3,
  "attemptCountBeforeHint": 3,
  "correctBlocksBefore": 4,
  "totalBlocks": 8,
  "blockOrderAtRequest": ["block_2", "block_0", "block_1", "block_3"],
  "findingsCount": 2,
  "findingRuleIds": ["F821", "F841"],
  "affectedBlockIds": ["block_2", "block_0"],
  "tool": "ruff"
}
```

##### `hint_explanation_requested` (Stufe 2)
```json
{
  "hintLevel": 2,
  "creditsSpent": 2,
  "creditsRemaining": 2,
  "findingRuleIds": ["F821"],
  "llmModel": "gpt-4.1",
  "explanationLengthChars": 280
}
```

##### `hint_viewed`
```json
{
  "hintLevel": 1,
  "viewDurationMs": 5500,
  "dismissedBy": "block_moved",
  "movedHintedBlock": true
}
```

##### `post_hint_action`
```json
{
  "hintLevel": 1,
  "actionType": "moved_hinted_block",
  "timeSinceHintMs": 3000,
  "correctBlocksAfter": 5,
  "improvement": 1,
  "movedBlockId": "block_2",
  "blockWasHighlighted": true
}
```

#### Sonderfaelle

1. **Keine Findings (Code syntaktisch korrekt aber falsche Reihenfolge):**
   Manche Parsons-Probleme haben eine falsche Reihenfolge die trotzdem kompiliert/linted. In dem Fall: Fallback auf **Vergleich mit Musterloesung** — "Die Analyse hat keine Fehler gefunden, aber die Reihenfolge stimmt noch nicht. Schau dir an, was zuerst passieren muss."

2. **Distractor-Bloecke in der Loesung:**
   Wenn ein Distractor-Block in der Schuelerloesung steckt, erzeugt er oft spezifische Findings (z.B. Syntaxfehler, unused variable). Der Hint weist dann indirekt auf den Distractor hin, ohne ihn explizit als "falsch" zu benennen.

3. **Mehrere Findings gleichzeitig:**
   Stufe 1 zeigt maximal **die 2 wichtigsten** Findings (sortiert nach Severity: error > warning > info). Zu viele Hints gleichzeitig ueberfordern.

4. **Timeout/Fehler der Tools:**
   Bei Timeout oder Crash der Analysis-Tools: Graceful Degradation → Fallback auf positionsbasierten Hint oder "Hints sind gerade nicht verfuegbar".

#### Beantwortbare Forschungsfragen
1. **Wann suchen Schueler Hilfe?** — Nach wie vielen Fehlversuchen? Sofort oder als letztes Mittel?
2. **Help-Seeking-Stile:** Proaktiv (frueh, strategisch) vs. reaktiv (spaet, verzweifelt) vs. vermeidend (nie)
3. **Hint-Effektivitaet:** Verbessert sich die Performance nach Hints? Wie schnell?
4. **Linter vs. LLM-Erklaerung:** Reicht Stufe 1 oder brauchen Schueler Stufe 2? Bei welchen Aufgabentypen?
5. **Stuckness-Indikator:** Ist Hint-Anforderung ein besserer Stuckness-Proxy als wiederholte falsche Feedbacks?
6. **Dependency-Risiko:** Nutzen manche Schueler Hints exzessiv statt selbst nachzudenken?
7. **Finding-Typen:** Welche Fehlertypen treten am haeufigsten auf? (undefined_variable, null_safety, dead_code...)
8. **Determinismus vs. LLM:** Wie oft genuegt die deterministische Diagnose, wie oft wird "Erklaer es genauer" geklickt?

**Abhaengigkeiten:** AP 10 (Learning Events), AP 2 (Ownership fuer Konfiguration)

---

### AP 14: Advanced Learning Analytics — Paper v2 (Strategie, Stuckness, Micro-Interactions) ✅
**Ziel:** Tiefe Verhaltensanalyse fuer Paper v2 — ueber einfache Metriken hinaus. Kombiniert Process-Data-Forschung, SRL-Theorie und Parsons-spezifische Muster. Dieses AP enthaelt **keine** Distractor-Events (siehe AP 15).

**Abhaengigkeiten:** AP 10, AP 11, AP 13 (Hint-System)

#### Neue Event-Typen

##### 1. Block-Level Micro-Interactions: `block_moved`
Jede einzelne Drag-and-Drop Aktion (nicht aggregiert wie in AP10/11).
```json
{
  "eventType": "block_moved",
  "eventData": {
    "blockId": "block_3",
    "blockContent": "for (int i = 0; ...)",
    "fromIndex": 5,
    "toIndex": 2,
    "fromZone": "source",
    "toZone": "solution",
    "moveNumber": 7,
    "timeSinceLastMoveMs": 3200,
    "wasCorrectBefore": false,
    "totalCorrectBlocks": 3,
    "totalBlocks": 8
  }
}
```
**Forschungswert:**
- Loesungsstrategien identifizieren (top-down, bottom-up, random)
- Hesitation-Muster (lange Pausen = hoher Cognitive Load)
- First-move-Analyse: welcher Block wird zuerst bewegt?
- Cluster-Moves vs. Einzelzuege

##### 2. Post-Feedback Repair Strategy: `repair_strategy`
Automatisch klassifiziert nach jedem `feedback_requested` Event.
```json
{
  "eventType": "repair_strategy",
  "eventData": {
    "strategyType": "local_repair",
    "feedbackNumber": 3,
    "blocksMoved": 2,
    "blocksMovedCorrectly": 1,
    "blocksMovedIncorrectly": 1,
    "correctUndone": 0,
    "timeBetweenFeedbackAndFirstMoveMs": 2500,
    "timeBetweenFeedbackAndNextFeedbackMs": 18000
  }
}
```
**Strategie-Klassifikation (Frontend-Logik):**
| Typ | Kriterium |
|-----|-----------|
| `no_change` | 0 Moves nach Feedback → sofort erneut Feedback geklickt |
| `immediate_refeedback` | < 2 Sekunden zwischen Feedbacks, 0-1 Moves |
| `local_repair` | 1-2 Bloecke bewegt, keine korrekten Bloecke angefasst |
| `targeted_fix` | Genau die als falsch markierten Bloecke bewegt |
| `global_reshuffle` | > 50% aller Bloecke bewegt |
| `undo_correct` | Mindestens 1 zuvor korrekter Block wurde verschoben (= Regression) |

**Forschungswert:**
- "Bei schwierigen Tasks kippen Schueler haeufiger in globale Neuordnung statt lokale Reparatur"
- Korrelation Strategie-Typ ↔ Lernerfolg
- Strategiewechsel ueber eine Sequenz hinweg (adaptives Verhalten)

##### 3. Stuckness Detection: `stuckness_detected`
Automatisch vom Frontend erkannt und geloggt.
```json
{
  "eventType": "stuckness_detected",
  "eventData": {
    "stucknessType": "oscillation",
    "stateHash": "a3f8d2...",
    "repeatCount": 3,
    "feedbacksSinceProgress": 4,
    "timeSinceLastProgressMs": 45000,
    "correctBlocks": 5,
    "totalBlocks": 8,
    "hintAvailable": true,
    "hintUsed": false
  }
}
```
**Stuckness-Typen:**
| Typ | Erkennung |
|-----|-----------|
| `repeat_state` | Exakt gleiche Block-Anordnung wie bei einem frueheren Feedback |
| `oscillation` | A → B → A Muster (State wechselt hin und her) |
| `no_progress` | 3+ Feedbacks ohne Verbesserung (`improvement = 0` oder negativ) |
| `long_idle` | > 60 Sekunden ohne Aktion nach negativem Feedback |
| `rapid_clicking` | 3+ Feedbacks in < 10 Sekunden (Frustrations-Klicken) |

**Forschungswert:**
- Echte Sackgassen vs. produktives Probieren unterscheiden
- Stuckness als Trigger fuer Hint-Nutzung analysieren
- Korrelation Stuckness-Haeufigkeit ↔ Tier-Level ↔ Aufgabenkomplexitaet

##### 4. Focus & Context Switching: `focus_changed`
```json
{
  "eventType": "focus_changed",
  "eventData": {
    "action": "tab_hidden",
    "durationMs": null,
    "duringAttempt": true,
    "feedbackCountAtSwitch": 3,
    "correctBlocksAtSwitch": 5
  }
}
```
Beim Zurueckkehren:
```json
{
  "eventType": "focus_changed",
  "eventData": {
    "action": "tab_visible",
    "awayDurationMs": 45000,
    "duringAttempt": true
  }
}
```
**Forschungswert:**
- Inaktivität differenzieren: Nachdenken vs. Tab-Wechsel vs. extern Hilfe suchen
- Korrelation Tab-Wechsel ↔ Schwierigkeitsgrad
- Nicht-invasiv (nur visibility API, kein Tracking externer Inhalte)

##### 6. Solution Path Snapshots: `solution_snapshot`
Periodisch oder bei Schlüsselmomenten (nach jedem 5. Move, vor Feedback).
```json
{
  "eventType": "solution_snapshot",
  "eventData": {
    "trigger": "pre_feedback",
    "snapshotNumber": 3,
    "solutionZone": ["block_0", "block_3", "distractor_1", "block_1"],
    "sourceZone": ["block_2", "block_4"],
    "correctPositions": 2,
    "stateHash": "b7c9e1..."
  }
}
```
**Forschungswert:**
- Lösungspfade clustern → Schülertypen ableiten
- Produktive vs. regressive Züge quantifizieren
- Visualisierung: Sankey-Diagramm von State-Transitionen

##### 7. Mini-Self-Reports: `self_report`
Sparsam eingesetzt an definierten Punkten (konfigurierbar, default: nach Abschluss + nach Abbruch).
```json
{
  "eventType": "self_report",
  "eventData": {
    "trigger": "after_correct",
    "confidence": 4,
    "frustration": null,
    "perceived_difficulty": 3,
    "perceived_learning": 4
  }
}
```
**Trigger-Punkte:**
| Wann | Was fragen | Format |
|------|-----------|--------|
| Nach `task_correct` | "Wie sicher bist du, dass du es beim nächsten Mal wieder könntest?" (1-5) | 1-Klick Smiley |
| Nach `task_abandoned` | "Wie frustrierend war diese Aufgabe?" (1-5) | 1-Klick Smiley |
| Nach Sequenz | "Wie viel hast du gelernt?" (1-5) | 1-Klick Smiley |

**Forschungswert:**
- Verhaltensmuster mit subjektivem Erleben triangulieren
- Frustration als Prädiktor für Abbruch
- Confidence ↔ tatsächliche Performance

##### 8. Teacher Edit Process Logging: `teacher_edit_*`
Ergänzt AP9/AP12 — nicht nur Vorher/Nachher, sondern der **Bearbeitungsprozess** selbst.
```json
{ "eventType": "teacher_edit_started", "eventData": { "taskId": "...", "tierLevel": 2 } }
{ "eventType": "teacher_edit_saved", "eventData": { "taskId": "...", "editDurationMs": 45000, "fieldsChanged": ["statement", "code"], "saveCount": 1 } }
{ "eventType": "teacher_edit_cancelled", "eventData": { "taskId": "...", "editDurationMs": 12000 } }
{ "eventType": "teacher_edit_reverted", "eventData": { "taskId": "...", "revertedTo": "original" } }
```
**Forschungswert:**
- Teacher Adaptation Workflows (nicht nur Ergebnis, sondern Prozess)
- Time-to-Decision: Wie schnell entscheidet ein Lehrer?
- Welche Felder werden am häufigsten bearbeitet?
- Revert-Rate: Wie oft wird zum Original zurückgekehrt?

##### 9. Device & UI Metadata: `session_context`
Einmal pro Session geloggt.
```json
{
  "eventType": "session_context",
  "eventData": {
    "viewportWidth": 1920,
    "viewportHeight": 1080,
    "isMobile": false,
    "inputType": "mouse",
    "userAgent": "...",
    "screenOrientation": "landscape"
  }
}
```
**Forschungswert:**
- Bedienprobleme (Touch vs. Mouse) nicht mit Lernschwierigkeit verwechseln
- Mobile-Nutzungsrate bei Parsons-Problemen
- Kovariate für alle anderen Analysen

#### Feinere Timing-Daten (in bestehende Events integriert)

In `feedback_requested` und `task_correct` zusätzlich:
```json
{
  "latencyToFirstDragMs": 8500,
  "meanPauseBetweenMovesMs": 3200,
  "pauseAfterNegativeFeedbackMs": 5100,
  "pauseBeforeFirstFeedbackMs": 25000,
  "longestIdlePhaseMs": 18000,
  "shortBursts": 5,
  "longBursts": 2
}
```
Burst-Definition:
- Short Burst: < 5 Sekunden zwischen Moves (schnelles Arbeiten)
- Long Burst: > 15 Sekunden Pause → dann mehrere schnelle Moves (Nachdenken → Handeln)

#### Zusammenfassung: Alle neuen Event-Typen (AP 14)

| Event | Trigger | Datenmenge | Prio |
|-------|---------|-----------|------|
| `block_moved` | Jeder Drag-and-Drop | Hoch (5-30 pro Task) | **1** |
| `repair_strategy` | Nach jedem Feedback | Mittel (= Feedback-Anzahl) | **1** |
| `stuckness_detected` | Automatische Erkennung | Niedrig (0-2 pro Task) | **1** |
| `focus_changed` | Tab/Window-Wechsel | Niedrig-Mittel | **2** |
| `solution_snapshot` | Vor Feedback / alle 5 Moves | Mittel-Hoch | **2** |
| `self_report` | Nach Correct/Abandon/Sequenz | Niedrig (1-2 pro Task) | **2** |
| `teacher_edit_*` | Lehrer oeffnet/speichert/bricht ab | Niedrig | **2** |
| `session_context` | Einmal pro Session | Sehr niedrig (1x) | **3** |
| Feinere Timing-Daten | In bestehende Events | Kein neues Event | **1** |

#### Was NICHT geloggt wird (bewusste Entscheidung)
- **Keine** rohen Mausbewegungen / Hover-Tracking
- **Keine** externe URL-Erfassung (welche Tabs besucht wurden)
- **Kein** Keylogging
- **Keine** Webcam/Mikrofon-Daten

**Begruendung:** Transparenz, Consent und Datensparsamkeit. Die Literatur zu Process Data und Learning Analytics mahnt klar zu Validitaet und ethischer Verhaeltnismaessigkeit.

#### Beantwortbare Forschungsfragen (Paper v2)

**Strategie & Problemloesung:**
1. Welche Loesungsstrategien nutzen Novizen bei Parsons-Problemen? (block_moved Sequenzen)
2. Unterscheiden sich Strategien zwischen Tier 1/2/3? (Adaptivitaet)
3. Korreliert Strategie-Konsistenz mit Lernerfolg?

**Help-Seeking & Self-Regulation:**
4. Wann und warum nutzen Schueler Hints vs. Feedback? (AP13 + repair_strategy)
5. Gibt es Help-Seeking-Stile? (Proaktiv, reaktiv, vermeidend)
6. Verbessern Hints die Repair-Strategie? (post_hint_action → repair_strategy)

**Stuckness & Struggle:**
7. Wie haeufig geraten Schueler in Sackgassen? (stuckness_detected)
8. Welche Stuckness-Typen fuehren zu Abbruch vs. Durchhalten?
9. Ist Stuckness ein besserer Schwierigkeitsindikator als Wrong-Rate allein?

**Teacher Adaptation:**
10. Wie lange brauchen Lehrer fuer Anpassungen? (teacher_edit_*)
11. Korreliert Edit-Dauer mit objektivem Change-Score? (AP12)

**Abhaengigkeiten:** AP 10, AP 11, AP 13

---

### AP 15: Distractor-System (Generierung, Infrastruktur, Hint-Integration, Analytics) ✅

**Ziel:** Evidenzbasierte Distractor-Bloecke (falsche Codezeilen) automatisch per LLM generieren, in Parsons-Probleme einbauen, das Hint-System distractor-aware machen und die Schueler-Interaktion vollstaendig tracken.

**Abhaengigkeiten:** AP 14 (block_moved Infrastruktur), AP 9 (Task-Bearbeitung), AP 13 (Hint-System), AP 16 (Interchangeable Groups — Marker-Koexistenz)

**Wissenschaftliche Grundlage:** Clancy (2004, Misconceptions), Pea (1986, Superbugs), Sleeman (1986, Mal-rules), Sirkia & Sorva (2012, Initialwert-Fehler), Denny et al. (2008, Parsons Problems), Sweller (1988, Cognitive Load Theory). Siehe `distractor-architecture.md`.

---

#### Teil 1: Distractor-Taxonomie

Feste Taxonomie mit optionalem Freitext-Rationale (Hybrid-Ansatz). Die Taxonomie ermoeglicht statistische Auswertung, Prompt-Stabilitaet und Lehrer-Steuerung.

| Typ-ID | Beschreibung | Paedagogischer Fokus | Referenz |
|---|---|---|---|
| `LOGIC_FLIP` | Vertauschen von Operatoren (`<` statt `>`, `==` statt `!=`) | Praedikative Logik, Bedingungsverstaendnis | Clancy (2004) |
| `OFF_BY_ONE` | Fehler in Schleifengrenzen (`range(n)` statt `range(n+1)`) | Grenzwertverstaendnis, Iterationsintervalle | Dijkstra / Knuth |
| `VARIABLE_MISMATCH` | Nutzung einer existierenden aber falschen Variable | Datenfluss-Analyse, Variablenbindung | Sleeman (1986) |
| `BOUNDARY_IDENTITY` | Falsche Initialwerte/Base-Cases (`return 0` statt `return 1`) | Rekursives Denken, Identitaetselemente | Sirkia & Sorva (2012) |
| `STRUCTURAL_ILLOGICAL` | Code der im Kontext illegal ist (`break` ausserhalb Schleife) | Kontrollfluss-Regeln, "Superbugs" | Pea (1986) |
| `REDUNDANT_ZOMBIE` | Logisch klingende aber ueberfluessige/verfaelschende Zeilen | Identifikation von unnoetigem Ballast | Parsons & Haden |

---

#### Teil 2: Automatische LLM-Generierung (3-Stufen-Pipeline)

Architekturentscheidung: **Option B — Separater Antagonist-Call** (nicht im Solver-Prompt). Begruendung: Schuetzt Integritaet der Loesungssuche, ermoeglicht Lehrer-Steuerung, fokussiert LLM-Kreativitaet auf Fehlersuche. Siehe `distractor-architecture.md` Abschnitt 3.2.

##### Stufe 1: Strategische Planung (Erweiterung Sequence-Generation)

Das bestehende `buildSequenceRequest()` Response-Schema wird um ein optionales `distractorPlan`-Objekt pro Task erweitert:

```json
{
  "tasks": [
    {
      "tier": 1,
      "statement": "...",
      "contract": { "..." },
      "distractorPlan": {
        "type": "LOGIC_FLIP",
        "rationale": "Focus on Boolean inversion for early exits to test condition understanding."
      }
    }
  ]
}
```

- `distractorPlan` ist **optional** (nur wenn `distractorsEnabled == true`)
- `type` muss aus der Taxonomie stammen (Enum-Validierung)
- `rationale` ist Freitext fuer kontextspezifische Hinweise an den Antagonisten
- Stellt paedagogische Kohaerenz sicher: Distraktoren passen zum Tier und Lernziel
- Sequence-Identity-Prompt erhaelt Taxonomie-Definition als Referenz

##### Stufe 2: Solver (UNVERAENDERT)

Der Solver bleibt eine "Pure Function". Er generiert nur die korrekte Loesung ohne Distractor-Bezug. Keine Aenderungen an `buildSolveRequest()` oder am Solver-System-Prompt.

##### Stufe 3: Antagonist-Call (NEUER LLM-Schritt)

Neuer `buildDistractorRequest()` in `PromptBuilder`. Wird im `TaskEnricher` nach dem Solve-Step und nach der Interchangeable-Annotation (AP16) getriggert.

**System-Prompt ("CodeOwl Antagonist"):**
```
You are CodeOwl (Distractor Expert). Your goal is to generate plausible but
incorrect code lines (distractors) for Parsons problems.

A Parsons problem requires students to arrange code blocks. Distractors are
"trap" lines that look correct but introduce logical, boundary, or syntax errors.

RULES:
1. Generate distractors highly similar in style and variable naming to the solution.
2. Each distractor must be a SINGLE line or a small coherent block (max 2 lines).
3. Every distractor MUST end with the suffix " #distractor".
4. Do NOT use #swap or #group markers on distractor lines.
5. Ensure distractors are functionally incorrect for the given task.
6. The distractors must be valid syntax for the given programming language.
7. Focus on common student misconceptions matching the requested taxonomy type.
8. Match the indentation level as if the distractor replaced an existing line.

TAXONOMY TYPES:
- LOGIC_FLIP: Invert operators (<→>, ==→!=, &&→||)
- OFF_BY_ONE: Wrong loop bounds or range limits
- VARIABLE_MISMATCH: Use existing but wrong variable in expression
- BOUNDARY_IDENTITY: Wrong initial values or base cases
- STRUCTURAL_ILLOGICAL: Code illegal in context (break outside loop, etc.)
- REDUNDANT_ZOMBIE: Plausible but unnecessary/harmful extra lines
```

**User-Prompt Template:**
```
TASK CONTEXT:
Language: {language}
Task: {taskStatement}
Tier: {tierLevel}
Correct Solution:
"""
{solutionCode}
"""

DISTRACTOR STRATEGY:
Type: {distractorPlan.type}
Rationale: {distractorPlan.rationale}

GOAL:
Generate {count} distractors for this task following the strategy above.

OUTPUT:
Return ONLY a JSON object with a "distractors" array containing the strings.
```

**Response-Schema (strict JSON):**
```json
{
  "distractors": ["string (each ending with #distractor)"]
}
```

**Mengen-Heuristik (count-Berechnung):**
- Tier 1: 1–2 Distraktoren (grobe Logikfehler)
- Tier 2: 2–3 Distraktoren (Operatoren & Variablen)
- Tier 3: 3–4 Distraktoren (Boundary-Errors & strukturelle Fallen)
- Cap: Anzahl Distraktoren ≤ 30% der Solution-LOC

**Tier-Differenzierung (paedagogische Skalierung):**
- Tier 1 (Anfaenger): "Grobe Schnitzer" — offensichtlich falsch bei aufmerksamem Lesen
- Tier 2 (Fortgeschrittene): "Detail-Fehler" — `i` vs `j`, `>` vs `>=`
- Tier 3 (Experten): "Subtile Fallen" — falsche Akkumulatoren, fehlerhafte Rekursions-Anker

---

#### Teil 3: Distractor-Infrastruktur (Persistenz & UI)

##### Backend — Datenbank

```sql
ALTER TABLE tasks ADD COLUMN distractors TEXT;
ALTER TABLE tasks ADD COLUMN distractor_plan TEXT;  -- JSON: {type, rationale}
```

- `distractors`: Zeilen getrennt durch `\n`, jede Zeile endet mit `#distractor`
- `distractor_plan`: JSON-String aus Stufe 1 (fuer Nachgenerierung und Analytics)
- Wird erst nach erfolgreichem Antagonist-Call befuellt (Transaktionssicherheit)

##### Backend — Domain & API

- `TaskDTO`: neue Felder `String distractors`, `String distractorPlan`
- Task-API liefert `distractors` und `distractorPlan` mit aus
- `PostgreTaskDao`: SELECT/INSERT/UPDATE um beide Felder erweitern
- `TaskEnricher`: neuer Schritt nach Solve+Annotate → Antagonist-Call (wenn enabled)

##### Backend — Validierung (QA-Heuristiken im Code)

- **Identitaets-Check**: Distractor-Zeile darf nicht identisch mit Solution-Zeile sein (nach Whitespace-Stripping und Entfernen von `#distractor`/`#swap`/`#group` Markern)
- **Syntax-Validierung (optional)**: Distractor-Zeile durch tree-sitter parsen um Kauderwelsch zu verhindern
- **Marker-Exklusivitaet**: Distractor-Zeilen duerfen NUR `#distractor` haben, NIE `#swap` oder `#group`

##### Frontend — SolvePage

- Beim Laden: `task.distractors`-Zeilen an `task.solution`-String anhaengen
- jsParsons erkennt `#distractor`-Suffix nativ → `extra_lines` wird automatisch befuellt
- Distractor-Bloecke visuell nicht unterscheidbar von echten Bloecken
- `max_wrong_lines` aus Worksheet-Config oder Tier-basiert

##### Frontend — TaskEditModal (Lehrer)

- Textarea fuer manuelle Distractor-Bearbeitung (Hinzufuegen/Entfernen/Editieren)
- Toggle: "Distraktoren aktivieren" pro Task oder pro Sequence
- Anzeige des `distractorPlan` (Typ + Rationale) als Kontext fuer den Lehrer
- Button: "Distraktoren neu generieren" (triggert Antagonist-Call erneut)

##### Datenformat-Beispiel (TaskDTO)

```json
{
  "solution": "def add(a, b):\n    return a + b #swap:commutative",
  "distractors": "    return a - b #distractor\n    return a * b #distractor",
  "distractorPlan": "{\"type\":\"LOGIC_FLIP\",\"rationale\":\"Operator inversion on arithmetic\"}"
}
```

---

#### Teil 4: Hint-System Distractor-Awareness (AP13 Erweiterung)

AP15 und AP13 sind **hochgradig gekoppelt**. Ein Rollout von Distraktoren ohne Anpassung des Hint-Mechanismus wuerde zu inkonsistenter UX fuehren.

##### 4a: CodeAssembler — Distractor-Awareness

Der `CodeAssembler` muss beim Zusammenbau wissen, welche Bloecke Distraktoren sind. Er sollte die Analyse-Pipeline idealerweise zweimal triggern:
1. Mit dem aktuellen Stand (inkl. Distraktoren)
2. Intern ohne die Distraktoren → pruefen ob der restliche Code strukturell korrekt waere

Damit wird verhindert, dass ein Distractor im Workspace voellig andere Compiler-Fehler erzeugt und die echten Probleme maskiert.

##### 4b: HintService — Step-0 Distractor Check

**Neue Prioritaetsregel:** Bevor semantische Hinweise gegeben werden, prueft das System: "Befindet sich ein Distraktor im Workspace?"

- Wenn ja → Level-1 Hint zeigt direkt auf den ueberfluessigen Block
- Hint-Text: "Einer der Bloecke in deinem Workspace gehoert nicht zur Loesung."
- Erst wenn keine Distraktoren im Workspace → normale Analyse-Pipeline

##### 4c: BlockMapper — Metadata-Integration

Der `BlockMapper` muss Zugriff auf die Distractor-Metadaten haben:
- Welche Block-IDs sind Distraktoren?
- Welcher Taxonomie-Typ gehoert zum jeweiligen Distractor?
- Wenn ein Finding eine Distractor-Zeile betrifft → Mapping liefert `distractorTaxonomy` statt generischem Finding

##### 4d: HintExplainer (Level-2) — Antagonist-Prompting

Der System-Prompt des `HintExplainers` muss angepasst werden:
- **Ohne Distractor**: "Erklaere den Fehler und wie man ihn repariert"
- **Mit Distractor**: "Erklaere warum dieser spezifische Fehlertyp ({taxonomy}) im Kontext dieser Aufgabe logisch keinen Sinn ergibt — erklaere NICHT wie man die Zeile repariert, sondern warum sie entfernt werden muss"

---

#### Teil 5: Distractor-Analytics (Learning Events)

##### 5a: Bestehende Events erweitern

`feedback_requested` und `task_correct` erhalten zusaetzliche Felder im `eventData`:

```json
{
  "distractorCountInWorkspace": 1,
  "activeDistractorIds": ["dist_logic_456"],
  "distractorTaxonomyTypes": ["LOGIC_FLIP"],
  "isDistractorOnlyError": true
}
```

- `isDistractorOnlyError`: Waere der Rest korrekt wenn die Distraktoren weg waeren?
- Ermoeglicht Unterscheidung: Scheitern an Logik vs. Hereinfallen auf Falle

##### 5b: Neue Events

###### `distractor_placed`
```json
{
  "eventType": "distractor_placed",
  "eventData": {
    "blockId": "distractor_1",
    "blockContent": "for (int i = 0; i <= n; i++) {",
    "distractorType": "OFF_BY_ONE",
    "placedAtIndex": 3,
    "moveNumber": 5,
    "totalDistractorsInSolution": 1,
    "timeSinceTaskOpenedMs": 25000
  }
}
```

###### `distractor_removed`
```json
{
  "eventType": "distractor_removed",
  "eventData": {
    "blockId": "distractor_1",
    "distractorType": "OFF_BY_ONE",
    "wasInSolutionMs": 12000,
    "removedBeforeFeedback": true,
    "removedAfterHint": false,
    "moveNumber": 8
  }
}
```

Event-Typen in `LearningEventService.ALLOWED_EVENT_TYPES` ergaenzen:
`distractor_placed`, `distractor_removed`

##### 5c: Erweiterte Stuckness-Erkennung (AP14)

Neue Stuckness-Typen in SolvePage-Logik:
- **`distractor_loop`**: Schueler oszilliert zwischen zwei Zustaenden, in beiden liegt ein Distraktor im Workspace
- **`trap_blindness`**: Schueler aendert 3x die Reihenfolge korrekter Bloecke (`local_repair`), laesst den Distraktor aber unberuehrt an falscher Stelle

##### 5d: Erweiterte Repair-Strategien (AP14)

Neue Repair-Strategy-Typen:
- **`distractor_discard`**: Aktion nach Feedback war das Entfernen eines Distraktors
- **`distractor_substitution`**: Distraktor wird durch anderen Block ersetzt (Anzeichen fuer Raten)

##### 5e: Lehrer-Tracking (Change Analysis Erweiterung)

In `ast_distractor_changes` (JSONB, bereits vorhanden in `task_change_analyses`):
- **Distractor-Retention-Rate**: Wie viele KI-generierte Distraktoren behaelt der Lehrer?
- **Manual Overrides**:
  - `DISTRACTOR_REMOVED`: KI-Vorschlag war zu schwer/unplausibel
  - `DISTRACTOR_EDITED`: KI-Vorschlag brauchte Anpassung
  - `DISTRACTOR_ADDED`: Lehrer vermisste spezifische Fehlkonzeption

---

#### Teil 6: Beantwortbare Forschungsfragen

**Distractor-Design:**
1. Welche Taxonomie-Typen fangen Schueler am haeufigsten? (via `distractorTaxonomyTypes` in Events)
2. Korreliert Distractor-Fallrate mit Programmierkonzept?
3. Erkennen Schueler Distraktoren vor oder nach Feedback? (via `removedBeforeFeedback`)
4. Wie lange bleiben Distraktoren in der Loesung? (via `wasInSolutionMs`)
5. Korrelation Distractor-Nutzung ↔ Tier-Level

**Hint-Interaktion:**
6. Helfen Hints beim Erkennen von Distraktoren? (via `post_hint_action` nach Distractor-Hint)
7. Fuehrt ein Distractor-Hint schneller zur Loesung als ein Einrueckungs-Hint? (Hint-Efficacy)
8. Sequenz Distractor im Workspace → Hint → Distractor entfernt = "Erfolgreiche Fehlkonzeptions-Korrektur"

**Schueler-Profil:**
9. Individuelles Misconception-Profil: Welche Taxonomie-Typen bereiten welchem Schueler Probleme? (`userId` + `distractorTaxonomyTypes`)
10. Distractor Attraction Score: Welche Fehlertypen sind fuer welche Altersgruppe am "verfuehrerischsten"?

**Generative Qualitaet:**
11. Welche KI-generierten Distraktoren werden von Lehrern am haeufigsten akzeptiert? (Retention-Rate)
12. Korrelation zwischen Taxonomie-Typ und Lehrer-Akzeptanz

**Stuckness:**
13. Sind Distraktoren die Hauptursache fuer Stuckness? (`distractor_loop`, `trap_blindness`)
14. Unterschied der Stuck-Dauer mit vs. ohne Distraktoren

---

#### Implementierungsreihenfolge

1. **DB + DTO + API** (Teil 3: Persistenz) — Felder anlegen, CRUD erweitern
2. **Antagonist-Prompt + PromptBuilder** (Teil 2: Stufe 3) — `buildDistractorRequest()`
3. **Sequence-Schema erweitern** (Teil 2: Stufe 1) — `distractorPlan` im Response
4. **TaskEnricher Integration** (Teil 2: Pipeline) — Antagonist-Call wenn enabled
5. **Frontend SolvePage** (Teil 3: Rendering) — Distractor-Zeilen an Solution anhaengen
6. **Frontend TaskEditModal** (Teil 3: Lehrer-UI) — Textarea + Toggle + Regeneration
7. **Validierung** (Teil 3: QA) — Identitaets-Check, Marker-Exklusivitaet
8. **Hint-Awareness** (Teil 4) — CodeAssembler, HintService, BlockMapper, HintExplainer
9. **Analytics Events** (Teil 5: Events) — Neue Events + erweiterte Payloads
10. **Stuckness/Repair** (Teil 5: AP14) — Neue Typen in SolvePage-Logik

**Abhaengigkeiten:** AP 14, AP 9, AP 13, AP 16

---

### AP 15b: Distractor-Nachgenerierung (Batch & On-Demand)

**Ziel:** Distraktoren nachtraeglich zu bestehenden Tasks, Sequenzen oder ganzen Worksheets hinzufuegen (Batch-Generierung). Unabhaengig von der initialen Generierungs-Pipeline.

**Abhaengigkeiten:** AP 15 (Distractor-Infrastruktur muss stehen)

#### Umfang

- **Einzelner Task**: Lehrer kann fuer einen einzelnen Task ohne Distraktoren den Antagonist-Call triggern
- **Ganze Sequenz**: Lehrer kann fuer alle Tasks einer Sequenz auf einmal Distraktoren generieren lassen
- **Ganzes Worksheet**: Lehrer kann fuer alle Tasks aller Sequenzen eines Worksheets Batch-Distraktoren generieren
- **Ueberschreiben/Neu-Generieren**: Bereits vorhandene Distraktoren koennen ersetzt werden
- **API-Endpoints**: `POST /api/tasks/{taskId}/generate-distractors`, `POST /api/sequences/{seqId}/generate-distractors`, `POST /api/worksheets/{sheetId}/generate-distractors`
- **Frontend**: Buttons in TaskEditModal, SequencePage und WorksheetPage

#### Status: Offen (wird nach AP 15 implementiert)

---

### AP 16: Austauschbare Block-Gruppen (Interchangeable Groups) ✅
**Ziel:** Lehrer koennen Codezeilen als austauschbare Gruppen markieren. Das Grading akzeptiert alle gueltigen Permutationen dieser Gruppen. Loest das Problem, dass manche Zeilen in beliebiger Reihenfolge korrekt sind (z.B. unabhaengige Variablendeklarationen, vertauschbare if-Bloecke).

**Abhaengigkeiten:** AP 9 (Task-Bearbeitung)

#### Syntax (im Solution-String)

Zwei neue Marker am Zeilenende, analog zu `#distractor`:

**`#group:NAME`** — Aufeinanderfolgende Zeilen mit gleichem Gruppennamen bilden einen **festen Block-Cluster**. Der Cluster bleibt intern zusammen, aber Cluster mit gleichem Gruppennamen duerfen untereinander getauscht werden.

**`#swap:NAME`** — Jede einzelne Zeile ist unabhaengig tauschbar mit allen anderen `#swap:NAME` Zeilen.

##### Beispiel 1: Feste Cluster (tauschbar als Paar)
```
def fibonacci(n):
    if n == 0: #group:base
        return 0 #group:base
    if n == 1: #group:base
        return 1 #group:base
    return fibonacci(n-1) + fibonacci(n-2)
```
Zwei Cluster erkannt: `[if n==0, return 0]` und `[if n==1, return 1]`. Jeder Cluster bleibt zusammen, aber die zwei Cluster duerfen getauscht werden.

##### Beispiel 2: Einzelzeilen frei tauschbar
```
x = 10 #swap:vars
y = 20 #swap:vars
z = x + y
```
`x = 10` und `y = 20` sind einzelne Zeilen → frei tauschbar. `z = x + y` hat keinen Marker → positionsfest.

**Semantik-Zusammenfassung:**
| Marker | Intern tauschbar? | Cluster tauschbar? |
|--------|-------------------|--------------------|
| `#group:NAME` | Nein (Reihenfolge innerhalb Cluster fest) | Ja (Cluster untereinander tauschbar) |
| `#swap:NAME` | Ja (jede Zeile einzeln) | — (kein Cluster-Konzept) |
| kein Marker | — | Nein (positionsfest wie bisher) |

#### Umsetzung

##### 1. parsons.js — `parseCode()` erweitern
- `#group:NAME` und `#swap:NAME` Marker erkennen und strippen (wie `#distractor`)
- Auf jedem `ParsonsCodeline` Objekt: `line.groupName = "base"`, `line.groupType = "group"|"swap"|null`
- Fuer `#group`: Aufeinanderfolgende Zeilen mit gleichem Namen bilden einen **Block-Cluster**
- Fuer `#swap`: Jede Zeile ist ein eigener Einzel-Cluster

##### 2. parsons.js — LIS-Grading anpassen
Vor dem LIS-Vergleich: **Position-Normalisierung**.

**Aktuell:** Jede Zeile bekommt `position = solutionIndex`. LIS prueft ob Positionen aufsteigend sind.

**Neu:** Zeilen in austauschbaren Gruppen bekommen flexible Positionen:

**Algorithmus fuer `#group`:**
1. Identifiziere Gruppen-Cluster im Student-Code (z.B. `[if n==0, return 0]` bei Index 2-3, `[if n==1, return 1]` bei Index 0-1)
2. Pruefe: Sind die Cluster intern korrekt geordnet? (Zeilen innerhalb eines Clusters muessen in Originalreihenfolge sein)
3. Pruefe: Ist die Cluster-Reihenfolge eine gueltige Permutation? (Cluster mit gleichem Gruppennamen duerfen in beliebiger Reihenfolge stehen)
4. Falls ja: Weise normalisierte aufsteigende Positionen zu → LIS sieht alles als korrekt
5. Falls nein: Markiere nur die tatsaechlich falsch platzierten Bloecke

**Algorithmus fuer `#swap`:**
1. Identifiziere alle `#swap:NAME` Zeilen im Student-Code
2. Pruefe: Stehen sie alle im selben Bereich wie im Original? (zwischen denselben Nicht-Swap-Nachbarn)
3. Falls ja: Jede Permutation ist gueltig → normalisiere Positionen aufsteigend
4. Falls nein: Markiere falsch platzierte Zeilen

##### 3. SolvePage.tsx — Custom Grader
- `isCorrect` Logik respektiert Gruppen-Aequivalenz
- Bloecke in gueltiger Gruppen-Permutation = `line-correct` (gruen)

##### 4. Backend — Keine DB-Aenderung noetig
- `solution` Feld enthaelt den vollen Text mit Markern
- `#group:X` und `#swap:X` werden wie `#distractor` im String gespeichert
- TaskEditModal: Hinweis-Text dass `#group:NAME` und `#swap:NAME` verfuegbar sind

##### 5. Task-Edit-Modal
- Kurze Hilfe-Info mit Erklaerung beider Marker
- Optional: Farbige Hervorhebung der Gruppen im Code-Editor

##### 6. Automatische Annotation bei Task-Generierung (Zwei-Pass-System)

Die LLM-basierte Task-Generierung laeuft in zwei Passes:

**Pass 1** (existiert bereits): LLM generiert sauberen Code → `CodeSanitizerService` entfernt Kommentare → Solution gespeichert.

**Pass 2** (neu): Zweiter LLM-Call analysiert den fertigen (sanitisierten!) Code und gibt zurueck welche Zeilen tauschbar sind. Das Backend fuegt dann `#group:` und `#swap:` Marker direkt in den Solution-String ein. Da der Sanitizer bereits gelaufen ist, bleiben die Marker erhalten.

**Fallback:** Wenn Pass 2 fehlschlaegt oder nichts findet → Solution bleibt unveraendert (keine Marker = alles positionsfest wie bisher).

###### System Message (Identity-Datei)
```
You are a code analysis assistant for Parsons programming puzzles.

Your job is to identify lines in a code solution that are INTERCHANGEABLE in their ordering.
You must be conservative — only mark lines as interchangeable when swapping them
produces semantically identical behavior. If in doubt, do NOT mark them.

Two annotation types exist:

1. "#swap:NAME" — Individual lines that are independently interchangeable.
   Each line marked with the same swap name can appear in any order relative to the others.
   Use for: independent variable declarations, independent assignments with no
   cross-dependencies, independent function calls with no side-effect ordering.

2. "#group:NAME_N" — Multi-line blocks that must stay together as a unit.
   Each cluster gets a unique name: same prefix, different number (e.g. base_1, base_2).
   Lines with the SAME full name (e.g. base_1) form one cluster and stay together internally.
   Clusters whose names share the SAME PREFIX (e.g. base_1 and base_2) are interchangeable
   with each other — their relative order does not matter.
   This distinction is necessary because without numbering there is no way to know which
   lines belong to which cluster.
   Use for: independent if-blocks (not elif chains!), independent try-except blocks,
   independent case handlers.

Rules:
- Only mark lines where the order is TRULY irrelevant (no data dependency between them).
- Variables used later must be declared before use — do NOT mark as swappable if one
  depends on another.
- elif/else chains are NOT interchangeable (they depend on evaluation order).
- Independent if-statements (no else) that test different conditions ARE interchangeable.
- Lines without any annotation remain position-fixed (the default).
- Return ONLY valid JSON matching the schema. No markdown, no explanation.
```

###### User Message Template
```
Analyze this {language} code and identify interchangeable lines.

CODE:
{sanitized_solution_code}

Return JSON matching this schema:
{
  "annotations": [
    {"line": <1-based line number>, "type": "swap"|"group", "name": "<group_name>"}
  ]
}

If no lines are interchangeable, return: {"annotations": []}

EXAMPLES:

--- Example 1: Independent variable declarations → #swap ---
Code:
def calculate(a, b):
    x = a * 2
    y = b * 3
    return x + y

Response:
{"annotations": [
  {"line": 2, "type": "swap", "name": "vars"},
  {"line": 3, "type": "swap", "name": "vars"}
]}
Reason: x and y have no dependency on each other. Either can be computed first.

--- Example 2: Independent if-blocks → #group ---
Code:
def fibonacci(n):
    if n == 0:
        return 0
    if n == 1:
        return 1
    return fibonacci(n-1) + fibonacci(n-2)

{"annotations": [
  {"line": 2, "type": "group", "name": "base_1"},
  {"line": 3, "type": "group", "name": "base_1"},
  {"line": 4, "type": "group", "name": "base_2"},
  {"line": 5, "type": "group", "name": "base_2"}
]}

Reason: Lines 2-3 form cluster base_1 (if n==0 + return 0), lines 4-5 form cluster base_2 (if n==1 + return 1). Same prefix "base" means the clusters are interchangeable as units. Different numbers mean lines within each cluster stay together internally.

--- Example 3: Dependent variables → NO annotation ---
Code:
def process(n):
    doubled = n * 2
    result = doubled + 1
    return result

Response:
{"annotations": []}
Reason: result depends on doubled. Order matters.

--- Example 4: Mixed ---
Code:
def greet_all(names):
    greeting = "Hello"
    separator = ", "
    results = []
    for name in names:
        results.append(greeting + separator + name)
    return results

Response:
{"annotations": [
  {"line": 2, "type": "swap", "name": "init"},
  {"line": 3, "type": "swap", "name": "init"}
]}
Reason: greeting and separator are independent. results = [] must come before the loop, so it is NOT swappable.

Now analyze the code above and return the JSON.
```

###### Backend-Integration
- Neuer Service: `InterchangeableAnnotator` — ruft Pass 2 auf, parst JSON-Response, fuegt Marker in Solution-String ein
- Aufgerufen in `TaskEnricher` nach `CodeSanitizerService` und `ComplexityAnalyzer`
- Marker werden ans Zeilenende angefuegt: `"    if n == 0:"` → `"    if n == 0: #group:base"`

#### Beispiele: Vollstaendiger Flow

##### Beispiel A: `#group` — Feste Cluster tauschen

**Lehrer speichert:**
```
def fibonacci(n):
    if n == 0: #group:base
        return 0 #group:base
    if n == 1: #group:base
        return 1 #group:base
    return fibonacci(n-1) + fibonacci(n-2)
```

**Schueler ordnet (gueltig):**
```
def fibonacci(n):
    if n == 1:        ← Cluster 2 zuerst
        return 1
    if n == 0:        ← Cluster 1 danach
        return 0
    return fibonacci(n-1) + fibonacci(n-2)
```
→ **Korrekt** (Cluster-Tausch, intern jeweils richtig)

**Schueler ordnet (ungueltig):**
```
def fibonacci(n):
    if n == 1:
        return 0      ← FALSCH: return 0 gehoert zum anderen Cluster!
    if n == 0:
        return 1
    return fibonacci(n-1) + fibonacci(n-2)
```
→ **Falsch** (Cluster intern kaputt: Zeilen aus verschiedenen Clustern gemischt)

##### Beispiel B: `#swap` — Einzelzeilen frei tauschbar

**Lehrer speichert:**
```
x = 10 #swap:vars
y = 20 #swap:vars
z = 30 #swap:vars
result = x + y + z
```

**Schueler ordnet (gueltig):**
```
z = 30
x = 10
y = 20
result = x + y + z
```
→ **Korrekt** (jede Permutation von x/y/z ist gueltig, `result` bleibt am Ende)

#### Aufwand
- `parsons.js` parseCode + Grader: **Kern-Arbeit** (~60%)
- SolvePage Custom Grader: ~20%
- TaskEditModal Hilfe-Text: ~10%
- Testing: ~10%

---

### AP 17: Click-to-Move & Touch-Interaktion ✅

**Quelle:** Thesis Future Work S.99 + Studentenfeedback (`UX_prefer_click_interaction`, `UX_dragdrop_trackpad_hard`)

**Ziel:** Alternative Interaktionsmodi neben Drag-and-Drop, damit Schueler auch auf Trackpads, Tablets und Phones effektiv arbeiten koennen.

**Problem:** Drag-and-Drop ist auf Trackpads muehsam und auf Tablets/Phones kaum nutzbar. Mehrere Studierende wuenschten sich explizit Click-basierte Platzierung.

**Umfang:**
1. **Click-to-Move Modus:** Click auf Block = selektiert (visuell hervorgehoben), Click auf Zielposition = Block wird dort eingefuegt
2. **Keyboard-Navigation:** Pfeiltasten hoch/runter zum Verschieben selektierter Bloecke, Tab zum Wechseln zwischen Source/Target
3. **Touch-Support:** Long-press to select, Tap to place (fuer Tablets/Phones)
4. **Modus-Toggle:** Schalter im Solve-UI zwischen "Drag" und "Click" Modus, Praeferenz wird in LocalStorage gespeichert
5. **Visuelles Feedback:** Selektierter Block bekommt `selected`-CSS-Klasse, moegliche Drop-Positionen werden als Insertion-Marker angezeigt

**Technische Details:**
- `parsons.js`: Neuer Event-Handler-Layer neben dem bestehenden jQuery-UI sortable
- Click-Modus deaktiviert `sortable` und nutzt eigene Click-Event-Listener
- Touch-Events via `touchstart`/`touchend` mit Gesture-Detection (long-press vs. tap)
- Accessibility: ARIA-Labels fuer Screen-Reader-Kompatibilitaet

**Abhaengigkeiten:** Keine

---

### AP 18: Expliziter Completion-Indikator & Post-Solution-Review ✅

**Quelle:** Thesis Future Work S.99 + Teacher-Feedback (`UX_feedback_issues`)

**Ziel:** Eindeutiges visuelles "Geschafft!"-Signal bei korrekter Loesung und eine deterministische Nachbesprechung.

**Problem:** Lehrer beschrieben das Farb-Feedback (gruen/gelb/rot) als gelegentlich verwirrend. Es fehlt ein eindeutiges Task-Completed-Signal und eine Erklaerung nach dem Loesen.

**Umfang:**
1. **Completion-Banner:** Bei `task_correct` wird ein grosses, eindeutiges "✓ Richtig geloest!" Banner/Overlay angezeigt (nicht nur gruene Bloecke)
2. **Confetti-Animation:** Optionaler visueller Belohnungseffekt (kann vom Lehrer deaktiviert werden)
3. **Post-Solution-Review (deterministisch, kein LLM):**
   - Zeigt den korrekten Code nochmal formatiert und syntax-highlighted an
   - Zeigt Loesungsstatistik: Anzahl Versuche, benoetigte Zeit, Hints genutzt ja/nein
   - Optional: Vergleich mit "optimaler" Loesung (wenigste Zuege)
4. **"Naechste Aufgabe"-Button:** Direkter Uebergang zum naechsten Task/Tier
5. **Self-Report Integration:** Der bestehende Smiley-Report (AP 14) wird in den Completion-Flow integriert statt als separates Popup

**Technische Details:**
- `SolvePage.tsx`: Neuer State `showCompletionOverlay` der bei `task_correct` getriggert wird
- Completion-Overlay als eigene React-Komponente `CompletionOverlay.tsx`
- Confetti via leichtgewichtiges npm-Paket (z.B. `canvas-confetti`, ~3KB)
- Statistik-Daten aus bestehenden Refs (moveCountRef, feedbackCountRef, etc.)

**Abhaengigkeiten:** AP 14 (Self-Report-Integration)

---

### AP 19: Curriculum-Filter, Themen-Bibliothek & Lehrplan-Integration (Beta)

**Quelle:** Thesis Future Work S.99 — Wie mit Markus Wuerdinger besprochen. Erweitert um Lehrplan-Integration bayerischer Gymnasien.

**Ziel:** Lehrer koennen (a) bestehende Tasks/Sequences nach Thema und Sprache filtern, durchsuchen und wiederverwenden, und (b) direkt einen Lehrplanbereich des bayerischen Gymnasiums anklicken, um dazu passende Tasks angezeigt zu bekommen ODER automatisch generieren zu lassen — basierend auf einem hinterlegten Datensatz echter Code-Beispiele pro Lehrplanbereich.

**Problem:** Jede Sequence ist isoliert, es gibt keine thematische Organisation oder Wiederverwendbarkeit. Lehrer koennen nicht nach "alle Loop-Aufgaben in Python" filtern. Zusaetzlich fehlt eine direkte Anbindung an den Lehrplan — Lehrer muessen selbst wissen, welche Konzepte zu welchem Lehrplanbereich gehoeren, und Code-Beispiele selbst mitbringen.

---

#### Teil A: Themen-Bibliothek & Filter (Basis)

**Grundidee:** Aktuell ist jede Sequence isoliert — einmal generiert, schwer wiederzufinden. Teil A macht aus der Sequence-Sammlung eine durchsuchbare Bibliothek, in der Lehrer nach Thema, Sprache, Schwierigkeit filtern und mit einem Klick fremde/eigene Sequences in ihr Worksheet kopieren koennen.

**Umfang (Uebersicht):**
1. **Zwei-Ebenen-Tagging** (Struktur-Tags + Semantische LLM-Tags)
2. **Auto-Tagging** (synchron fuer AP25-Concepts, asynchron fuer LLM-Tags)
3. **Filter-UI** mit `LibraryPage.tsx`
4. **Volltextsuche** ueber Problem-Statements und Code-Inhalte
5. **One-Click-Kopie** aus der Bibliothek

---

##### A.1 Zwei-Ebenen-Tag-Architektur

Tags zerfallen in zwei qualitativ unterschiedliche Gruppen, die beide in derselben Tabelle landen, aber ueber `source` unterschieden werden:

| Ebene | Beispiele | Quelle | Dauer | Sync? |
|-------|-----------|--------|-------|-------|
| **Struktur-Tags** | `for-loop`, `recursion`, `if-else`, `arrays` | AP 25 Concept Detection | ~50ms DB-INSERT | Synchron in TaskEnricher |
| **Semantische Tags** | `fibonacci`, `binary-search`, `euklid`, `caesar-chiffre`, `warenkorb-berechnung` | LLM-Analyse (eigener Prompt) | 3-10 Sekunden | **Zwingend asynchron** |

**Warum zwei Ebenen?** Struktur-Tags beantworten "welche Konstrukte kommen vor". Semantische Tags beantworten "worum geht es inhaltlich". Ohne semantische Tags waere eine Suche nach `fibonacci` auf reine Volltextsuche im Problem-Statement angewiesen — und die findet nichts, wenn der Lehrer die Aufgabe anders formuliert hat ("Berechne die n-te Zahl der Folge, wo jede Zahl die Summe der beiden vorherigen ist").

**DB-Schema:**
```sql
CREATE TABLE IF NOT EXISTS sequence_tags (
    sequence_id UUID NOT NULL REFERENCES tiered_task_sequences(id) ON DELETE CASCADE,
    tag_name    VARCHAR(64) NOT NULL,
    source      VARCHAR(16) NOT NULL DEFAULT 'auto',  -- 'auto' | 'llm' | 'manual'
    confidence  REAL DEFAULT NULL,                    -- nur fuer 'llm', 0.0-1.0
    created_at  TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (sequence_id, tag_name)
);
CREATE INDEX idx_sequence_tags_tag ON sequence_tags (tag_name);
CREATE INDEX idx_sequence_tags_sequence ON sequence_tags (sequence_id);
```

**LLM-Tagging-Prompt (eigenes Identity-File `SequenceSemanticTagger`):**
```
System: Analysiere Code + Problem-Statement. Gib als JSON 1-5 semantische Tags zurueck:
  - Algorithmus-Namen (Fibonacci, Quicksort, Binaersuche, Euklid, ...)
  - Konkrete Datenstrukturen (Stack, Queue, LinkedList, ...)
  - Domaene (Spielelogik, Geldbetrag, Kalender, Geometrie, ...)
NIEMALS Syntax-Konstrukte (keine for-loop, if-else, recursion).
```

**Response-Format:**
```json
{
  "primary_tags": ["fibonacci", "rekursion-klassiker"],
  "aliases": ["fibonacci-folge", "goldener-schnitt"],
  "short_description": "Rekursive Berechnung der n-ten Fibonacci-Zahl"
}
```

Aliases werden als eigene `sequence_tags`-Zeilen mit `source='llm'` gespeichert, sodass Suche nach jedem Alias die Sequence findet. `short_description` landet als optionales Feld auf der Sequence selbst (neue Spalte `semantic_summary`).

---

##### A.2 Topic-Tags (detailliert)

**Was es ist:** Jede Sequence bekommt eine Liste von Tags — strukturell (z.B. `"loops"`, `"recursion"`) und semantisch (z.B. `"fibonacci"`). Tags sind das Fundament fuer Filter und Suche.

**Was fehlt konkret:**
- DB-Tabelle `sequence_tags` (noch nicht im Schema).
- Entity, DTO, DAO, RowMapper, Service — alles nicht vorhanden.
- Keine REST-Endpoints fuer Tag-CRUD.

**Convenience & Super-User-Fuehrung:**

1. **`source`-Spalte (auto / llm / manual):** Frontend zeigt die Herkunft optisch unterscheidbar — automatische Tags grau, LLM-Tags mit einem Funkel-Icon (leicht andere Hintergrundfarbe), manuelle Lehrer-Tags blau. Bei Auto-/LLM-Tags Hover-Tooltip "Warum dieses Tag?" mit dem ausloesenden Code-Snippet bzw. LLM-Begruendung.

2. **Normalisierung:** In DB nur Slugs (`"for-loop"`), im UI uebersetzte Labels (`"For-Schleife"`) via bestehende i18n. Verhindert parallel existierende Varianten.

3. **Lehrer-editierbare Tags inline auf der Sequence-Karte:** Pencil-Icon beim Hover, Pills werden zu Chips mit "×" zum Loeschen, Enter fuegt neues Tag hinzu. Kein Modal, keine Extra-Seite.

4. **"Vorgeschlagene Tags"-Autocomplete:** Beim Eintippen eines neuen Tags Dropdown aus:
   - Bestehende `concepts.name`-Slugs
   - Alle bereits systemweit verwendeten Tags
   - Alias-Auffloesung ("schleife" zeigt "for-loop / while-loop" als Vorschlag)
   Verhindert, dass `loops`, `Loops`, `loop`, `schleife` parallel entstehen.

5. **Tag-Synonyme-Kollaps (Admin-Feature):** Wenn LLM `"fibonacci"` und `"fibonacci-zahlen"` parallel erzeugt, normalisiert ein Admin-Tag-Manager-Endpoint die auf einen Kanon (`POST /admin/tags/merge?from=fibonacci-zahlen&to=fibonacci`). Sonst hat man nach 100 Sequences 15 Varianten desselben Tags.

---

##### A.3 Auto-Tagging (Struktur + Semantik, mit Async-Pattern)

**Was es ist:** Nach jeder Sequence-Generierung schreibt das System erkannte Tags automatisch in `sequence_tags`. Der Lehrer muss nichts tun.

**Zwei parallele Pfade:**

| Pfad | Technik | Wo eingehaengt |
|------|---------|----------------|
| Struktur-Tags | Nach Persist, direkter DB-INSERT | Synchron in `TaskEnricher` nach `sequence.save()` |
| Semantische Tags | LLM-Call via eigene Identity-Prompt | Asynchron via Spring Event |

**Async-Infrastruktur (neu im Projekt):**

Das Projekt nutzt aktuell weder `@EnableAsync` noch `ApplicationEventPublisher` (geprueft — null Treffer). Minimale neue Konfig:

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taggingExecutor")
    public Executor taggingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

Virtual Threads sind im Projekt bereits etabliert (OPT-1 in `TaskEnricher`), die Wiederverwendung ist konsistent.

**Im `TaskEnricher` direkt vor dem Return:**
```java
// 1. Struktur-Tags sofort (synchron, ~50ms)
sequenceTagService.autoTagStructural(sequenceId, detectedConcepts);

// 2. Semantische Tags via Event (async, Response geht sofort raus)
eventPublisher.publishEvent(new SequenceGeneratedEvent(sequenceId, code, problemStatement));
return sequence;
```

**Listener separat:**
```java
@Component
class SemanticTaggingListener {
    @Async("taggingExecutor")
    @EventListener
    public void onSequenceGenerated(SequenceGeneratedEvent e) {
        try {
            var result = llmTagger.generateSemanticTags(e.code(), e.problemStatement());
            sequenceTagService.writeLlmTags(e.sequenceId(), result);
        } catch (Exception ex) {
            log.warn("Semantic tagging failed for sequence {}", e.sequenceId(), ex);
        }
    }
}
```

**Fehler-Isolation:** Tagging darf die Generation niemals rueckabwickeln. Exceptions werden geloggt, die Sequence bleibt untagged — nachtraeglich ueber den Bulk-Retag-Admin-Endpoint zu heilen.

**Convenience & Super-User-Fuehrung:**

1. **Re-Tagging nach Task-Edit:** Wenn ein Lehrer ueber AP 9 eine Task editiert und der Code wesentlich geaendert wird → Trigger eines neuen `SequenceGeneratedEvent` → neue Tag-Analyse laeuft. Manuelle Tags (`source='manual'`) werden **nie ueberschrieben**.

2. **"Warum hat das System `recursion` getaggt?"** Hover auf einem Auto-Tag zeigt Tooltip mit dem Code-Schnipsel, der das Concept getriggert hat. Transparenz + niederschwellige Bug-Meldungen.

3. **Bulk-Retag-Admin-Endpoint fuer Legacy-Sequences:** `POST /admin/sequences/retag` laeuft als Hintergrund-Job (antwortet sofort `202 Accepted` + Job-ID), Admin-Dashboard zeigt Progress (`347/892 sequences processed`). Ohne diesen Job waere die Bibliothek am Launch-Tag leer.

4. **Confidence-Threshold fuer LLM-Tags:** Tags mit `confidence < 0.6` werden als "Vorschlag" angezeigt (Strichrahmen, nicht solid) und erst nach Lehrer-Bestaetigung promotet. Reduziert Noise-Tags.

5. **LLM-Tagging-Retry mit Lehrer-Korrektur als Few-Shot:** Wenn Lehrer das Auto-Tag `fibonacci` gegen `fakultaet` korrigiert, wird diese Korrektur beim naechsten Bulk-Retag derselben Sequence als Example mitgesendet. Billiger als Fine-Tuning, Qualitaet verbessert sich ueber Zeit.

6. **Frontend-Progressive-Enhancement:**
   - **Direkt nach Generation:** Tags-Bereich zeigt die Struktur-Tags (schon da) + Skeleton-Chip "wird analysiert…".
   - **Nach 3-5s:** Die LLM-Tags fliessen nach — visuell unterscheidbar, damit Lehrer sie bei Bedarf direkt wegklicken koennen.
   - **Kein Race:** LibraryPage refresht automatisch beim Fokus-Wechsel (`window.onfocus`), sodass kurz nach der Generation angezeigte Tags dort auch erscheinen.

**Kosten:** ~500 Token in + ~80 Token out pro Sequence → unter 0,5 Cent bei GPT-4o-mini. Legacy-Retag fuer 500 Sequences: ~2,50 Euro.

---

##### A.4 Filter-UI (LibraryPage)

**Was es ist:** Browse-Seite (`LibraryPage.tsx`) mit Filtern: Thema (Tags), Sprache (`target_language`), Tier-Anzahl (3 oder 5), Erstellungsdatum, Owner. Pagination.

**Was fehlt konkret:**
- `LibraryPage.tsx` existiert nicht.
- Kein Filter-Endpoint im Backend mit dynamischem WHERE.
- Keine Route im `App.tsx` fuer `/library`.
- Kein Nav-Link in der Navbar.

**Backend-Skizze:**
```java
@GetMapping("/sequences/search")
public PagedResponse<SequenceSummaryDTO> search(
    @RequestParam(required = false) List<String> tags,
    @RequestParam(required = false) String language,
    @RequestParam(required = false) Integer tierCount,
    @RequestParam(required = false) String targetLocale,
    @RequestParam(required = false) LocalDate createdAfter,
    @RequestParam(required = false) String ownerFilter,  // "mine" | "school" | "all"
    @RequestParam(required = false) String query,        // Volltextsuche
    @RequestParam(defaultValue = "relevance") String sortBy,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) { ... }
```

**Convenience & Super-User-Fuehrung:**

1. **URL-State:** Filter in Query-String (`/library?tags=loops,arrays&lang=python&tier=5`). Teilbar per Link, Browser-Back funktioniert, kein verlorener Filter nach Refresh.

2. **Facetten-Counts neben jedem Filter:** Tag-Cloud zeigt `Loops (23)`, `Recursion (7)` — Lehrer sieht sofort, wo Material existiert. Backend liefert Counts in einer einzigen aggregierenden Query mit.

3. **"Zuletzt verwendet" als Default-Sortierung fuer `ownerFilter=mine`:** Neue Spalte `last_used_at` auf `worksheet_sequences` (wird beim Einfuegen in Worksheet aktualisiert). Wenn ein Lehrer seine eigenen Sequences sucht, will er meistens die zuletzt verwendeten.

4. **Quick-Filter-Chips oben:** Drei bis vier Shortcuts — "Nur meine", "Nur Java", "Nur Anfaenger (Tier 3)", "Mit Distractor". Reduziert die Filter-UI fuer 80% der Faelle auf einen Klick.

5. **Leerer Zustand als Chance:** Bei 0 Treffern nicht "No results" — sondern: *"Keine Treffer fuer `Python` + `Recursion`. Moechtest du eine neue Sequence dazu generieren?"* mit Button, der Generation-UI mit pre-filled Constraints oeffnet.

6. **Sticky Filter-Sidebar, scrollbare Ergebnisliste:** Auf Desktop Filter links fixiert, Ergebnisse rechts. Auf Mobile als Slide-over-Drawer.

7. **"Schule teilt" vs. "Nur ich":** Standard-Ansicht zeigt alle Sequences der Schule (via `owner_id` + `class_members`-Join). Toggle auf "Nur meine". So entsteht eine Community ohne aktives Teilen.

8. **"Aehnliche Sequences"-Empfehlungen:** Sidebar mit *"Andere Rekursions-Klassiker: Fakultaet, Tuerme von Hanoi"* — basierend auf Tag-Overlap. Kein zweiter LLM-Call, rein aus der Tag-DB.

9. **"Neu entdeckte Themen"-Admin-Report:** Wochenweise Dashboard *"Diese Woche neu getaggt: `merge-sort (3x)`, `dijkstra (2x)`, `caesar-chiffre (1x)`"* — gibt Einblick ins gewachsene Material.

---

##### A.5 Volltextsuche

**Was es ist:** Suchfeld ueber Problem-Statements und Code-Inhalte aller Tasks/Tiers einer Sequence.

**Was fehlt konkret:**
- Keine FTS-Indizes im Schema.
- Kein Search-Endpoint.
- Kein Suchfeld im Frontend.

**Postgres-FTS-Skizze:**
```sql
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(problem_statement, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(code, '')), 'B')
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_tasks_fts ON tasks USING GIN (search_vector);
```

`'simple'` damit Code-Tokens nicht gestemmt werden (`print` soll nicht als `pr` indiziert werden).

**Ranking-Kombination mit Tags:**

Suche nach "Fibonacci" laeuft auf drei Wegen parallel:
1. **Tag-Match** (primary_tag = `fibonacci`) → hoechste Prioritaet
2. **Alias-Match** (alias = `fibonacci-folge`) → mittlere Prioritaet
3. **FTS-Match** (Wort "Fibonacci" im Problem-Statement) → niedrigste Prioritaet

Alle drei fliessen in ein gemeinsames Ranking. Der Lehrer tippt einmal "Fibonacci" und findet alle relevanten Sequences, egal wie sie formuliert waren.

**Convenience & Super-User-Fuehrung:**

1. **Debounced Instant-Search:** Kein Submit-Button, 300ms Delay nach Tastenanschlag, Live-Update der Ergebnisliste.

2. **Highlight des Treffers:** In der Ergebnis-Karte matchender Snippet mit `<mark>`-Hervorhebung. Postgres `ts_headline()` liefert das nativ.

3. **Code-vs-Prose-Toggle:** Ueber dem Suchfeld zwei Chips "In Beschreibungen" / "Im Code". Beide default an. Lehrer koennen gezielt nur Prosa oder nur Code-Tokens durchsuchen.

4. **Suchverlauf persistent (localStorage):** Dropdown unter dem Suchfeld mit letzten 5 Suchanfragen. Fast-Repeat von gestern.

5. **"Meintest du…" bei null Treffern:** Trigram-Similarity via `pg_trgm`-Extension findet aehnliche Strings. `recursiv` zeigt dann `recursive` als Vorschlag.

6. **Kombinierbar mit Filtern:** Suche ueberschreibt die Filter-Chips nicht, sondern schraenkt ein. Filter auf "Python + Tier 3", dann darin nach "matrix" suchen.

---

##### A.6 One-Click-Kopie

**Was es ist:** In der Ergebnisliste hat jede Sequence-Karte einen "In mein Worksheet"-Button. Klick → Sequence wird (ueber die bestehende AP-4-Copy-Logik) in ein Worksheet kopiert.

**Was fehlt konkret:**
- Copy-Logik selbst existiert (AP 4 ist abgeschlossen), der UI-Einhaengepunkt in der Library fehlt.
- Kein kontextuelles "Worksheet-Auswahl-Dropdown" als wiederverwendbare Komponente.

**Minimal-UI:**
```
[Sequence-Karte]
+---------------------------------+
| Array-Summe mit for-Schleife    |
| #loops #arrays | Java | 3 Tiers |
| [Ziel: Klasse 9b - Woche 3 v]   |
| [In Worksheet kopieren] [Preview]|
+---------------------------------+
```

**Convenience & Super-User-Fuehrung:**

1. **"Default-Worksheet"-Persistenz:** Wenn Lehrer 4x in Folge in dasselbe Worksheet kopiert, merkt sich die UI das und macht den naechsten Klick zum Ein-Klick statt Dropdown. Badge "Default: Klasse 9b - Woche 3" mit Reset-Option.

2. **Optimistische Toast-Confirmation mit Undo:** Sofort nach Klick Toast *"Kopiert in Klasse 9b - Woche 3"* mit **Undo-Button** (5 Sekunden sichtbar). Undo ruft DELETE auf die gerade angelegte `worksheet_sequences`-Zeile.

3. **Drag-&-Drop-Alternative:** Sequence-Karte ins Worksheet-Dropdown ziehbar. Fuer Power-User die mehrere Sequences auf einmal organisieren.

4. **"Kopieren und bearbeiten…":** Dritter Dropdown-Eintrag → oeffnet Sequence direkt in der Task-Edit-Ansicht (AP 9), bevor sie ins Worksheet geht. Nuetzlich wenn Lehrer Variablennamen ans Niveau der Klasse anpassen will.

5. **"Bereits kopiert"-Indikator:** Sequences, die bereits in irgendeinem eigenen Worksheet liegen, bekommen einen kleinen gruenen Haken + Hover-Tooltip "Bereits in 2 deiner Worksheets". Verhindert Duplikate und gibt Gefuehl fuer eigenen Bestand.

6. **Bulk-Copy:** Checkboxes links an jeder Karte → Floating-Action-Bar unten *"3 ausgewaehlt - In Worksheet kopieren"*. Fuer den Use-Case "ich baue eine neue 9b-Stoffwoche aus mehreren Bibliotheks-Sequences auf einmal".

---

##### A.7 Integration mit bestehendem System

Damit Teil A sauber ins bestehende System einfliesst, kleine Hooks an drei Stellen:

| Existierende Stelle | Minimale Aenderung fuer Teil A |
|---------------------|-------------------------------|
| `TaskEnricher.java:130` | Nach `sequence.save()` → `sequenceTagService.autoTagStructural(...)` + `eventPublisher.publishEvent(new SequenceGeneratedEvent(...))` |
| `App.tsx:240` (Routen) | Neue `<Route path="/library" element={<LibraryPage/>} />` |
| Navbar in `App.tsx:~122` | Neuer `<Link to="/library">Bibliothek</Link>` zwischen Curriculum und Classes |
| `CurriculumPage.tsx` | "Vorhandene Sequences"-Button → redirect zur LibraryPage mit `?curriculumTopic={id}` vorbefuellt |
| AP 4 Copy-Service | Neuer Public-Endpoint `POST /sequences/{id}/copy-to-worksheet/{wsId}` falls noch nicht vorhanden |

---

##### A.8 Technische Details (konsolidiert)

**DB:**
- Neue Tabelle `sequence_tags` (`sequence_id`, `tag_name`, `source`, `confidence`, `created_at`)
- Neue Spalte `tiered_task_sequences.semantic_summary` (VARCHAR, optional)
- Neue Spalte `worksheet_sequences.last_used_at` (fuer "Zuletzt verwendet"-Sortierung)
- FTS-Spalte `tasks.search_vector` (generated, `tsvector`) + GIN-Index
- Optional `pg_trgm`-Extension fuer Fuzzy-"Meintest du…"-Suche

**Backend (neu):**
- `SequenceTagDTO`, `SequenceTagDAO`, `SequenceTagRowMapper`, `SequenceTagRepository`
- `SequenceTagService` (autoTagStructural + writeLlmTags + manuelle CRUD)
- `SemanticTaggingListener` (@Async via Spring Event)
- `LlmSemanticTagger` (wrapt OpenAI-Call mit `SequenceSemanticTagger`-Identity)
- `AsyncConfig` (@EnableAsync + Virtual-Thread-Executor)
- `SequenceGeneratedEvent` (Event-Record)
- `SequenceSearchController` mit `/sequences/search`
- `AdminTagController` mit `/admin/tags/merge`, `/admin/sequences/retag`
- Neues Identity-File `PromptStuff/Identity/SequenceSemanticTagger`
- Erweiterung `PromptBuilder.buildSemanticTaggingRequest(code, problemStatement)`

**Frontend (neu):**
- `LibraryPage.tsx` mit Filtern, Tag-Cloud, Volltextsuche, Pagination
- `SequenceCard.tsx` (wiederverwendbare Komponente)
- `TagChip.tsx` (mit Source-Varianten: auto / llm / manual)
- `WorksheetTargetDropdown.tsx` (wiederverwendbar fuer One-Click-Kopie)
- URL-State-Hook (`useLibraryFilters`) fuer Query-String-Sync
- i18n-Keys fuer alle Labels + Tooltips

**Kostenabschaetzung LLM-Tagging:** ~0,5 Cent pro Sequence, ~2,50 Euro fuer Bulk-Retag von 500 Sequences.

---

##### A.9 Implementierungsreihenfolge

Rangfolge nach "meistem UX-Gewinn pro Implementierungsstunde":

| Schritt | Beschreibung | Dateien |
|---------|-------------|---------|
| A1 | DB: `sequence_tags` Tabelle + FTS-Spalte + `last_used_at` | `z_codeowl_schema.sql` |
| A2 | Backend: DTO, DAO, Repository, RowMapper fuer `sequence_tags` | `domain/`, `infrastructure/persistence/` |
| A3 | Backend: `SequenceTagService` + synchrones Struktur-Auto-Tagging | `application/service/` |
| A4 | Backend: `AsyncConfig` + `SequenceGeneratedEvent` + `SemanticTaggingListener` | `infrastructure/config/`, `application/events/` |
| A5 | Backend: `LlmSemanticTagger` + Identity `SequenceSemanticTagger` + PromptBuilder-Erweiterung | `infrastructure/ai/`, `resources/PromptStuff/` |
| A6 | Backend: Hook in `TaskEnricher` (Event-Publish + strukturelles Tagging) | `application/service/TaskEnricher.java` |
| A7 | Backend: Admin-Bulk-Retag-Endpoint (async Job) | `api/AdminTagController.java` |
| A8 | Backend: `SequenceSearchController` mit dynamischem WHERE + Facetten-Counts | `api/`, `infrastructure/persistence/dao/` |
| A9 | Frontend: `LibraryPage.tsx` Skeleton (Liste ohne Filter) + Route + Navbar-Link | `frontend/src/pages/`, `App.tsx` |
| A10 | Frontend: Filter + URL-State + Quick-Filter-Chips | `LibraryPage.tsx`, `useLibraryFilters.ts` |
| A11 | Frontend: `TagChip` mit Source-Varianten + Inline-Edit + Autocomplete | `components/TagChip.tsx` |
| A12 | Frontend: One-Click-Kopie mit Default-Worksheet + Undo-Toast | `components/WorksheetTargetDropdown.tsx` |
| A13 | Frontend: Progressive Enhancement fuer LLM-Tags (Skeleton → Nachfliessen) | `SequenceCard.tsx` |
| A14 | Volltextsuche: FTS-Query, `ts_headline`-Highlighting, "Meintest du…" | `SequenceSearchController`, `LibraryPage.tsx` |
| A15 | Frontend: "Aehnliche Sequences", "Bereits kopiert"-Indikator, Bulk-Copy | `SequenceCard.tsx`, `LibraryPage.tsx` |
| A16 | Admin-Dashboard: Neu-entdeckte-Themen-Report + Tag-Merge-UI | `pages/AdminTagsPage.tsx` |

Diese Reihenfolge stellt sicher, dass nach Schritt A9 schon eine funktionale (wenn auch minimalistische) Library existiert und alle weiteren Schritte sichtbaren Mehrwert liefern, statt Grundlagenarbeit ohne User-Feedback.

---

#### Teil B: Lehrplan-Integration Bayern Gymnasium (Beta)

**Ziel:** Lehrer klicken auf einen Lehrplanbereich (z.B. "Jgst. 9 — Datenstrukturen: Arrays und Listen") und bekommen entweder vorhandene Tasks dazu angezeigt oder koennen per Klick neue generieren lassen. Im Hintergrund liegt ein kuratierter Datensatz echter Code-Beispiele pro Lehrplanbereich, der als Seed/Referenz in eine dedizierte Beta-Prompt einfliesst.

**Umfang Teil B:**

1. **Lehrplan-Datensatz befuellen:**
   - `curriculum_topics`-Tabelle (existiert bereits, ist leer) mit den echten Lehrplanbereichen des bayerischen Gymnasiums Informatik befuellen
   - Struktur: Jahrgangsstufe → Lernbereich → Thema (hierarchisch via `parent_id` oder flach mit Praefix)
   - Quelle: LehrplanPLUS Bayern Informatik (Jgst. 7, 9, 10, 11, 12/13)
   - `concept_curriculum_topic`-Mapping befuellen: welche Programmierkonzepte (aus `concepts`-Tabelle) gehoeren zu welchem Lehrplanbereich

2. **Code-Beispiel-Datensatz (neue Tabelle):**
   - Neue Tabelle `curriculum_code_examples`:
     ```sql
     CREATE TABLE curriculum_code_examples (
         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
         topic_id UUID NOT NULL REFERENCES curriculum_topics(id) ON DELETE CASCADE,
         language VARCHAR(50) NOT NULL,         -- z.B. "Java", "Python"
         title VARCHAR(255) NOT NULL,           -- z.B. "Array-Summe mit for-Schleife"
         code TEXT NOT NULL,                    -- der eigentliche Code-Beispiel
         problem_statement TEXT,                -- optionale Aufgabenbeschreibung
         difficulty VARCHAR(20) DEFAULT 'medium', -- easy/medium/hard
         notes TEXT,                            -- Anmerkungen fuer Lehrer
         created_at TIMESTAMP DEFAULT now()
     );
     ```
   - Pro Lehrplanbereich mindestens 3-5 kuratierte Java-Beispiele (Hauptsprache bayerisches Gymnasium)
   - Optional Python-Beispiele fuer Jgst. 11+ (wo Python im Lehrplan vorkommt)

3. **Backend-Infrastruktur:**
   - `PostgreCurriculumTopicDAO` ausprogrammieren (aktuell Stubs)
   - Neues `CurriculumCodeExampleDAO` + DTO + RowMapper
   - Neuer `CurriculumController` mit Endpoints:
     - `GET /curriculum/topics` — alle Lehrplanbereiche (hierarchisch)
     - `GET /curriculum/topics/{id}/examples` — Code-Beispiele zu einem Bereich
     - `GET /curriculum/topics/{id}/sequences` — vorhandene Sequences die zu diesem Bereich passen (via concept_curriculum_topic Mapping + sequence concepts)
     - `POST /curriculum/topics/{id}/generate` — generiert neue Sequence basierend auf Lehrplanbereich + Seed-Code
   - Neuer `CurriculumGenerationService`:
     - Waehlt 1-3 passende Code-Beispiele aus `curriculum_code_examples` als Seed
     - Baut eine dedizierte "Curriculum-Prompt" (Beta) die den Lehrplankontext + Code-Beispiele enthaelt
     - Nutzt bestehende Generation-Pipeline (Strategy → OpenAiClient → PromptBuilder)

4. **Dedizierte Curriculum-Prompt (Beta):**
   - Neues Identity-File: `backend/src/main/resources/PromptStuff/Identity/SequenceCurriculum`
   - Unterschied zur normalen Sequence-Prompt:
     - Enthaelt expliziten Lehrplankontext (Jahrgangsstufe, Lernbereich, erwartete Vorkenntnisse)
     - Bekommt 1-3 echte Code-Beispiele als Referenz-Material injiziert
     - Instruiert das LLM die Aufgaben AUF DIESEN Beispielen aufzubauen oder aehnliche zu generieren
     - Schwierigkeitsgrad wird an die Jahrgangsstufe angepasst (Jgst. 7 = einfacher als Jgst. 11)
     - Verwendet deutsche Aufgabenstellungen als Default (Lehrplan ist deutsch)
   - Neuer User-Prompt-Template in `PromptBuilder`:
     - `buildCurriculumSequenceRequest(topic, examples, language, tierCount, constraints)`

5. **Frontend: CurriculumPage (Beta):**
   - Neue Route `/curriculum` (oder Tab in LibraryPage)
   - Baumstruktur oder Akkordeon: Jahrgangsstufe → Lernbereich → Themen
   - Pro Thema:
     - Badge mit Anzahl vorhandener Sequences
     - "Anzeigen"-Button → filtert LibraryPage auf diesen Bereich
     - "Generieren"-Button → oeffnet Generation-Dialog mit vorausgefuelltem Kontext:
       - Konzepte automatisch gesetzt (aus concept_curriculum_topic)
       - Code-Beispiel als Vorschlag im Snippet-Feld (Lehrer kann aendern/entfernen)
       - Programmiersprache vorausgefuellt (Java als Default)
       - Beta-Badge sichtbar
   - i18n: Komplett deutsch (Lehrplan ist deutsch), englische Fallbacks

6. **Daten-Kuration (ausserhalb Code):**
   - SQL-Seed-Datei: `db/init/curriculum_seed.sql`
   - Enthaelt INSERT-Statements fuer:
     - Lehrplanbereiche (curriculum_topics)
     - Concept-Mappings (concept_curriculum_topic)
     - Code-Beispiele (curriculum_code_examples)
   - Wird beim Docker-Init automatisch geladen

**Technische Details Teil B:**
- Bestehende Tabellen: `curriculum_topics`, `concept_curriculum_topic`, `concepts` (alle existieren, Mapping-Tabellen leer)
- Neue Tabelle: `curriculum_code_examples`
- Bestehender Code: DTOs, Interfaces, RowMapper, Stub-DAO (alles vorhanden, DAO leer)
- Neuer Code: CurriculumCodeExampleDTO, CurriculumCodeExampleDAO, CurriculumController, CurriculumGenerationService, Curriculum-Prompt
- PromptBuilder: Neuer `buildCurriculumSequenceRequest()` Overload
- OpenAiClient: Neuer `generateCurriculumSequence()` Overload (oder Reuse von generateTieredTaskSequence mit erweitertem Prompt)

**Abhaengigkeiten:** AP 4 (Worksheet-Kopie ✅), AP 9 (Task-Bearbeitung ✅), AP 25 (Concept Detection ✅), AP 20 (Constraints ✅), AP 21 (Tier-Count ✅)

**Implementierungsreihenfolge:**

| Schritt | Beschreibung | Dateien |
|---------|-------------|---------|
| B1 | DB: `curriculum_code_examples` Tabelle + Schema-Erweiterung `curriculum_topics` (ggf. parent_id) | `z_codeowl_schema.sql` |
| B2 | Seed-Daten: Lehrplanbereiche + Concept-Mappings + Code-Beispiele | `curriculum_seed.sql` (neu) |
| B3 | Backend DTOs: `CurriculumCodeExampleDTO` | `domain/entities/` |
| B4 | Backend DAO: `PostgreCurriculumTopicDAO` ausprogrammieren + neues `CurriculumCodeExampleDAO` | `infrastructure/persistence/dao/` |
| B5 | Backend Service: `CurriculumGenerationService` (Seed-Auswahl + Prompt-Aufbau) | `application/service/` |
| B6 | Backend Prompt: Identity `SequenceCurriculum` + `PromptBuilder.buildCurriculumSequenceRequest()` | `resources/PromptStuff/`, `infrastructure/ai/` |
| B7 | Backend Controller: `CurriculumController` mit REST-Endpoints | `api/` |
| B8 | Frontend: `CurriculumPage.tsx` + Routing + i18n | `frontend/src/` |
| A1 | DB: `sequence_tags` Tabelle | `z_codeowl_schema.sql` |
| A2 | Backend: Tag-Service + Auto-Tagging in TaskEnricher | `application/service/` |
| A3 | Backend: Filter-Endpoints mit dynamischem WHERE | `api/` |
| A4 | Frontend: `LibraryPage.tsx` mit Filtern, Suche, Tag-Cloud | `frontend/src/` |

---

### AP 20: Constraint-Based Generation (Konstrukt-Einschraenkungen) ✅

**Quelle:** Thesis Future Work S.99 + Lehrer-Feedback (`DI_curriculum_mismatch`)

**Ziel:** Lehrer koennen einschraenken welche Programmierkonstrukte in generierten Aufgaben vorkommen duerfen.

**Problem:** LLM generiert manchmal Konstrukte die Schueler noch nicht kennen (Ternaeroperatoren, Lambda, List Comprehensions etc.). Lehrer haben aktuell keine Moeglichkeit das einzuschraenken.

**Umfang:**
1. **Blacklist-Feld:** "Nicht verwenden: ternary, lambda, list comprehension" — wird in Prompt gespliced
2. **Whitelist-Option:** "Nur verwenden: if/else, for, while, print" — restriktiver Modus
3. **Difficulty-Slider:** Steuert die Groesse des Schwierigkeitssprungs zwischen Tiers (z.B. "sanft" vs "steil")
4. **Zusaetzliches Kontext-Feld:** Freitext der direkt in den LLM-Prompt eingefuegt wird (z.B. "Verwende nur deutsche Variablennamen")
5. **Locale-Verbesserung:** Zuverlaessigere nicht-englische Generierung durch explizite Locale-Constraints im Prompt
6. **Validierung:** Nach Generation pruefen ob Blacklist-Konstrukte im Code vorkommen → ggf. Regeneration

**Technische Details:**
- Generation-UI: Neue Felder `blacklist`, `whitelist`, `difficultySlope`, `additionalContext`, `locale`
- `TaskGeneratorService.java`: Prompt-Template erweitert um Constraint-Abschnitt
- Post-Generation-Check: AST-basierte Pruefung ob Blacklist-Konstrukte vorhanden (Python: `ast` module, Java: regex-basiert)
- DB: Neue Felder in `sequences` Tabelle fuer Constraint-Konfiguration

**Abhaengigkeiten:** AP 9 (Prompt-Pipeline)

---

### AP 21: Fuenf-Tier-Modus & Erweiterte Parsons-Varianten ✅

**Quelle:** Thesis Future Work S.99-100

**Ziel:** Erweiterung von 3 auf 5 Tiers mit neuen Aufgabentypen fuer hoehere Differenzierung.

**Problem:** 3 Tiers reichen nicht immer fuer die volle Bandbreite einer Klasse. Fuer sehr starke und sehr schwache Schueler braucht es mehr Stufen.

**Umfang:**
1. **Tier 4: Distractor-Heavy** — Parsons-Puzzle mit mehreren Distractor-Bloecken (abhaengig von AP 15)
2. **Tier 5: "Explain-Your-Parsons"** — Schueler loesen das Puzzle UND muessen ihre Loesung erklaeren (Freitext oder Multiple-Choice)
3. **Fill-in-the-Blanks Variante:** Bloecke mit Luecken die ausgefuellt werden muessen (z.B. `for i in range(___):`)
4. **Optionaler 5-Tier Toggle:** Pro Worksheet konfigurierbar (3 oder 5 Tiers)
5. **Generation-Pipeline-Erweiterung:** LLM-Prompts fuer Tier 4/5 Generierung

**Technische Details:**
- `parsons.js`: Neuer Block-Typ "fill-in-blank" mit Eingabefeld innerhalb des Blocks
- Tier 5: Neue `ExplainPanel.tsx` Komponente unter dem geloesten Puzzle
- Generation: Erweiterte Prompts fuer Distractor-Generierung und Explain-Aufgaben
- DB: `sequences.tier_count` Feld (default 3, optional 5)

**Abhaengigkeiten:** AP 15 (Distractor-System fuer Tier 4), AP 20 (Generation-Erweiterung)

---

### AP 22: Kontext-Code (Laengere Aufgaben mit nicht-puzzlebarem Rahmen) ✅

**Quelle:** Thesis Future Work S.99 ("smarter handling of boilerplate")

**Ziel:** Aufgaben koennen laenger als 20 Zeilen sein, wobei nur ein Teil als Parsons-Puzzle geloest wird und der Rest als unveraenderlicher Kontext angezeigt wird.

**Problem:** Aktuell sind Parsons-Puzzles auf ~15-20 Zeilen beschraenkt weil zu viele Bloecke unuebersichtlich werden. Reale Programme sind aber laenger — Schueler sollen lernen Code IM KONTEXT zu lesen und zu ordnen.

**Umfang:**
1. **Kontext-Marker:** `#context` Marker fuer Zeilen die NICHT Teil des Puzzles sind sondern als fester Rahmen angezeigt werden
2. **Anzeige:** Kontext-Code wird ober- und/oder unterhalb des Puzzle-Bereichs angezeigt, ausgegraut und nicht verschiebbar
3. **Syntax-Highlighting:** Kontext-Code wird syntax-highlighted dargestellt (read-only)
4. **Generierung:** LLM wird angewiesen bei laengeren Programmen Kontext-Zeilen zu markieren (z.B. Imports, Klassen-Deklaration, Main-Methode als Kontext, nur der relevante Algorithmus als Puzzle)
5. **Zeilenreferenz:** Puzzle-Bloecke bekommen visuelle Zeilennummer-Hinweise damit Schueler wissen wo im Gesamtprogramm sie sich befinden

**Beispiel:**
```python
# === KONTEXT (nicht verschiebbar) ===
import math
import sys

class Calculator:
    def __init__(self):
        self.history = []

# === PUZZLE (zu ordnen) ===
    def add(self, a, b):        # Puzzle-Block 1
        result = a + b          # Puzzle-Block 2
        self.history.append(result)  # Puzzle-Block 3
        return result           # Puzzle-Block 4

# === KONTEXT (nicht verschiebbar) ===
    def get_history(self):
        return self.history

if __name__ == "__main__":
    calc = Calculator()
    print(calc.add(3, 4))
```

**Technische Details:**
- `parsons.js` `ParsonsCodeline`: Neues Flag `this.isContext = true/false`, `#context` Marker wird gestrippt
- `parseCode()`: Kontext-Zeilen werden NICHT in die Puzzle-Liste aufgenommen, sondern in separate `contextBefore`/`contextAfter` Arrays
- Frontend: `SolvePage.tsx` rendert Kontext-Bloecke als `<pre>` Elemente ober-/unterhalb der Sortable-Bereiche
- Grading: Kontext-Zeilen werden beim Grading komplett ignoriert (nur Puzzle-Zeilen zaehlen)
- Generation: Neuer Prompt-Abschnitt der bei Aufgaben >15 Zeilen Kontext-Markierung anweist
- LLM Pass 2 (analog zu AP 16 Interchangeable-Annotator): Identifiziert automatisch Boilerplate als Kontext

**Abhaengigkeiten:** AP 9 (Task-Bearbeitung), AP 16 (Marker-Infrastruktur als Vorbild)

---

### AP 23: Manueller Task-Ersteller (ohne LLM) ✅

**Quelle:** Thesis Future Work S.100

**Ziel:** Lehrer koennen eigenen Code direkt als Parsons-Puzzle verwenden — ohne LLM-Generierung.

**Problem:** Manche Lehrer wollen spezifischen Code aus ihrem Unterricht verwenden, nicht LLM-generierten Code. Aktuell gibt es keinen Weg eine Aufgabe ohne LLM-Call zu erstellen.

**Umfang:**
1. **Manueller Editor:** Code eingeben → wird automatisch in Parsons-Bloecke zerlegt
2. **Marker-Unterstuetzung:** Direkte Eingabe von `#distractor`, `#swap:NAME`, `#group:NAME_N`, `#context` im Editor mit Syntax-Highlighting
3. **Live-Preview:** Echtzeit-Vorschau wie das fertige Puzzle aussehen wird (Parsons-Widget inline)
4. **Problem-Statement-Editor:** Aufgabenstellung als Freitext eingeben
5. **Tier-Zuweisung:** Manuell Tier-Level setzen oder automatisch aus Complexity-Metrics ableiten

**Technische Details:**
- Neue `ManualTaskCreator.tsx` Seite mit Code-Editor (Monaco oder CodeMirror)
- Backend: Neuer Endpoint `POST /tasks/manual` der Task ohne LLM-Call erstellt
- Marker-Syntax-Highlighting im Editor (Custom Language Mode fuer `#distractor`, `#swap:`, `#group:`, `#context`)
- Complexity-Berechnung on-the-fly ueber bestehenden `TaskEnricher`
- Validierung: Prueft ob Solution parsbar ist, Marker korrekt gesetzt, keine doppelten Zeilen

**Abhaengigkeiten:** AP 9 (Task-Bearbeitung), AP 15 (Distractor-Marker), AP 16 (Group/Swap-Marker)

---

### AP 24: Worksheet- & Task-Export ✅

**Quelle:** Thesis Future Work S.100

**Ziel:** Lehrer koennen Worksheets und einzelne Tasks in verschiedenen Formaten exportieren fuer Offline-Nutzung, Klausuren und Dokumentation.

**Problem:** Aktuell existieren Aufgaben nur innerhalb der Web-Plattform. Fuer Klausuren, Uebungsblaetter oder Offline-Nutzung brauchen Lehrer druckbare Versionen.

**Umfang:**
1. **PDF-Export (Puzzle-Format):** ✅ Druckbare Version eines Worksheets mit allen Tasks als Parsons-Puzzle (Bloecke zum Ausschneiden und Kleben)
2. **PDF-Export (Free-Form):** ✅ Nur Aufgabenstellung + Code als Referenz, ohne Puzzle-Format (fuer schriftliche Pruefungen)
3. **CSV/JSON-Export:** ✅ Rohdaten-Export aller Tasks eines Worksheets (fuer eigene Auswertungen)
4. **Einzeltask-Export:** ✅ Download eines einzelnen Tasks/Sequence als JSON/CSV/PDF
5. **Batch-Export:** ✅ Ganzes Worksheet mit allen Sequences auf einmal exportieren

**Technische Details:**
- PDF-Generierung: Client-side via `jsPDF` (v4.2.1, installiert via npm)
- Puzzle-PDF: Rendert Code-Zeilen als ausschneidbare Kaertchen mit gestrichelten Linien, zufaellig gemischt
- Free-Form-PDF: Rendert Aufgabenstellung + Code in grauer Monospace-Box (fuer Klausuren)
- Kein Backend-Endpoint noetig — Export laeuft komplett client-side
- Export-Utility: `frontend/src/lib/export.ts` — zentrale Funktionen fuer JSON, CSV, PDF
- Frontend: Export-Dropdown in `WorksheetPage.tsx` (Teacher-Toolbar) und `SequencePage.tsx` (Teacher-View)
- WorksheetPage laedt alle Sequences via API parallel (`Promise.all`) bevor Export startet
- SequencePage nutzt bereits geladene Sequence-Daten direkt

**Abhaengigkeiten:** Keine (nur bestehende Task/Worksheet-Daten)

---

### AP 25: Concept Detection verbessern ✅

**Quelle:** Thesis Kapitel 5.1 (Concept Detection Prompt-Design), Kapitel 7.8 / RQ6 (Concept Detection Accuracy Evaluation), Appendix A.2.6 (Annotation Guidelines for Programming Concepts), Appendix B.2 (Concept Detection Dataset). Direkte Voraussetzung fuer AP 19 (Auto-Tagging).

**Ziel:** Die automatische Erkennung von Programmierkonzepten in generiertem Code zuverlaessiger, konsistenter und sprachuebergreifend machen.

**Ist-Zustand (aus der Thesis):**
- **Implementierung:** `generateConcepts()` in `AbstractAiClient.java` — ein einzelner LLM-Call bekommt einen Code-Snippet und gibt eine freie Liste von Konzept-Strings zurueck (Section 5.1).
- **Rolle im System:** Concepts sind FR5 (Functional Requirement) und fliessen in die Problem-Statement-Generierung ein. Sie werden in der DB als `concepts TEXT[]` auf der Sequence gespeichert und im Frontend angezeigt. Strategies wie `CodeOnlyStrategy`, `CodeAndConceptStrategy`, `TaskAndConceptStrategy` nutzen sie.
- **Evaluation (RQ6, Section 7.8):** Gegen manuell annotierte Ground-Truth (Appendix A.2.6 Taxonomy mit 9 Kategorien) wurde Precision, Recall, F1 und Subset Accuracy gemessen. Die Thesis stellt fest dass die Erkennung "reasonable" ist aber unter inkonsistenter Benennung leidet — off-taxonomy Varianten wurden separat gelistet (Appendix C.6).
- **Annotation Guidelines (A.2.6):** Die Thesis hat bereits eine Taxonomie fuer die Evaluation erstellt mit Kategorien wie Control Flow, Data Structures, Functions, OOP, Error Handling, I/O, String Operations, etc. Diese Taxonomie existiert aber NUR als Paper-Artefakt und ist NICHT im Code implementiert.

**Konkrete Probleme:**
- **Inkonsistente Benennung:** Gleiche Konzepte werden unterschiedlich benannt ("for loop" vs "for-loop" vs "iteration" vs "loops"). Die RQ6-Evaluation musste dafuer manuell Varianten zuordnen.
- **Granularitaet unkontrolliert:** Mal sehr spezifisch ("list comprehension with conditional"), mal zu breit ("data structures")
- **Keine Taxonomie im Code:** Die in A.2.6 entwickelte Annotation-Taxonomie ist nicht in die Software integriert — der LLM erfindet bei jedem Call eigene Begriffe
- **Keine Validierung:** Output wird ungeprueft uebernommen, kann Halluzinationen enthalten
- **Sprachabhaengig:** Python-spezifische Begriffe ("list comprehension") erscheinen auch bei Java-Code
- **Kein deterministischer Fallback:** Wenn der LLM-Call scheitert oder halluziniert, gibt es keine Absicherung

**Umfang:**
1. **Fester Concept-Katalog (basierend auf Thesis A.2.6 Taxonomie):** Die in der ZuLa entwickelte Annotation-Taxonomie wird als fester Katalog in den Code ueberfuehrt (~50-80 Konzepte in Kategorien):
   - **Control Flow:** if/else, for-loop, while-loop, do-while, switch/match, recursion, break/continue
   - **Data Structures:** array/list, dictionary/map, set, tuple, stack, queue, linked-list
   - **Functions:** function-definition, parameters, return-value, default-parameters, varargs, lambda/anonymous
   - **OOP:** class-definition, inheritance, polymorphism, encapsulation, constructor, method-overriding
   - **Error Handling:** try/catch, throw/raise, finally, custom-exceptions
   - **I/O:** console-output, console-input, file-read, file-write
   - **String Operations:** string-concatenation, string-formatting, string-methods, regex
   - **Advanced:** generator, decorator, async/await, type-hints, pattern-matching, list-comprehension
2. **Constrained LLM-Call:** Prompt gibt den Katalog explizit vor und verlangt NUR Begriffe daraus (JSON-Array). Eliminiert das off-taxonomy Problem das in RQ6/C.6 dokumentiert wurde.
3. **Deterministischer Fallback:** Regex/AST-basierte Erkennung als Backup:
   - Python: `ast.parse()` → Knoten-Typen zaehlen (For, While, FunctionDef, ClassDef, Try, etc.)
   - Java: Regex-Patterns fuer `for(`, `while(`, `class `, `interface `, `try {`, etc.
   - JavaScript/TypeScript: Regex fuer `function`, `=>`, `class`, `async`, `for...of`, etc.
4. **Hybrid-Ansatz:** Deterministisch erkannte Concepts werden mit LLM-Ergebnis gemerged (Union), Konflikte werden deterministisch aufgeloest
5. **Normalisierung:** Alle eingehenden Concept-Strings werden auf Katalog-Namen gemappt (Levenshtein-Distance oder Alias-Tabelle). Dies haette die manuelle Zuordnungsarbeit aus RQ6 automatisiert.
6. **Sprach-Mapping:** Sprachspezifische Konzepte werden auf generische gemappt (z.B. Python "list comprehension" → "list-comprehension", Java "enhanced for" → "for-each-loop")

**Technische Details:**
- Neue Klasse `ConceptCatalog.java`: Statische Taxonomie als `Map<String, List<String>>` (Kategorie → Concepts), abgeleitet aus Thesis A.2.6
- Neue Klasse `DeterministicConceptDetector.java`: Regex/Pattern-basierte Erkennung pro Sprache
- `AbstractAiClient.generateConcepts()`: Prompt umschreiben — Katalog als Enum-Liste mitgeben, JSON-Output erzwingen
- `ConceptNormalizer.java`: Alias-Tabelle + Fuzzy-Match eingehender Strings auf Katalog-Eintraege
- DB: `sequence_concepts` Tabelle (sequence_id, concept_name, detection_method ENUM('llm','deterministic','hybrid'))
- Rueckwaerts-kompatibel: Bestehende Sequences mit Freitext-Concepts werden bei naechstem Zugriff normalisiert
- **Evaluation-ready:** Neuer Endpoint `GET /concepts/validate` der die Erkennung gegen den Katalog prueft (fuer kuenftige RQ6-Reproduktion)

**Abhaengigkeiten:** Keine (verbessert bestehendes System). Stark empfohlen VOR AP 19 (Auto-Tagging braucht konsistente Concepts).

---

### AP 27: Internationalisierung (i18n) & Zielsprache fuer Generierung ✅

**Quelle:** Thesis Teacher-Feedback (`TECH_language_limitations`: "Deutsch funktioniert nicht"), Student-Feedback, Browsersprache als natuerlicher Trigger

**Ziel:** Die gesamte Plattform-Oberflaeche ist internationalisiert (Deutsch/Englisch) und wechselt automatisch anhand der Browsersprache. Zusaetzlich kann bei der Generierung eine "Zielsprache" fuer Problem-Statements und Kommentare gewaehlt werden.

**Problem:**
- Aktuell ist die UI ein Mix aus Deutsch und Englisch (Buttons auf Englisch, manche Labels auf Deutsch)
- Problem-Statements werden manchmal auf Deutsch generiert, manchmal auf Englisch — ohne Kontrolle
- Lehrer aus dem DACH-Raum brauchen deutsche Oberflaeche, internationale Nutzer englische
- Die Thesis dokumentiert (T15): "Deutsch funktioniert nicht ... Bei exotischeren Programmiersprachen ... macht er trotzdem manchmal Python"

**Umfang:**

**Teil 1: Frontend-Internationalisierung (String-Externalisierung)**
1. **i18n-Framework:** `react-i18next` + `i18next` einbinden
2. **String-Externalisierung:** Alle hardcodierten Strings in `SolvePage.tsx`, `WorksheetPage.tsx`, `SequencePage.tsx`, `ClassPage.tsx`, `AdminPage.tsx`, `ProfilePage.tsx`, Navigation, Buttons, Labels, Fehlermeldungen etc. in Uebersetzungsdateien auslagern
3. **Uebersetzungsdateien:** `frontend/src/i18n/de.json` und `frontend/src/i18n/en.json`
4. **Automatische Spracherkennung:** `i18next-browser-languagedetector` erkennt Browsersprache (`navigator.language`), Fallback: Deutsch
5. **Manueller Sprachwechsel:** Kleiner Toggle (DE/EN) in der Navbar, Praeferenz in `localStorage`
6. **Parsons-spezifische Texte:** "Source", "Construct your solution here", "Shuffle lines", "Feedback", Hint-Buttons, Completion-Banner, Self-Report-Text — alles uebersetzt

**Teil 2: Zielsprache fuer Generierung**
1. **Neues Feld `targetLanguage`:** Im Generation-UI (wo Lehrer Aufgaben generieren) ein Dropdown: "Sprache der Aufgabenstellung" → Deutsch / Englisch / (spaeter weitere)
2. **Prompt-Integration:** `targetLanguage` wird explizit in alle LLM-Prompts eingefuegt:
   - Problem-Statement-Generierung: "Generate the problem statement in {targetLanguage}"
   - Concept-Detection: Concept-Namen bleiben immer Englisch (Katalog aus AP 25)
   - Code-Kommentare: "Write inline comments in {targetLanguage}"
3. **DB-Feld:** `sequences.target_language VARCHAR(5) DEFAULT 'de'`
4. **Validierung:** Nach Generierung pruefen ob Problem-Statement tatsaechlich in der gewaehlten Sprache ist (einfacher Heuristik-Check)

**Technische Details:**
- `react-i18next` (~10KB): Bewaehartes i18n-Framework fuer React
- Namespace-Struktur: `common` (Buttons, Navigation), `solve` (SolvePage), `worksheet`, `admin`, `auth`
- Pluralisierung und Interpolation: `t('attempts', { count: 5 })` → "5 Versuche" / "5 attempts"
- Backend-Strings (Fehlermeldungen in API-Responses): Bleiben auf Englisch (Standard fuer APIs), Frontend uebersetzt sie
- `TaskGeneratorService.java`: `targetLanguage` Parameter durchreichen an alle Prompt-Templates
- Bestehende `locale`-Logik in Prompts wird durch explizites `targetLanguage` ersetzt

**Abhaengigkeiten:** Keine (kann jederzeit gebaut werden)

---

### AP 26: Multi-Language Code Analytics (tree-sitter) ✅

**Quelle:** Erweiterung der Complexity-Metriken und Change-Analysis auf alle unterstuetzten Programmiersprachen.

**Ziel:** Complexity Metrics und Change-Analysis funktionieren fuer **alle 9 Sprachen** (Java, Python, JavaScript, TypeScript, C, C++, C#, Rust, Go) — ohne zusaetzliche Container, komplett im bestehenden Backend via **tree-sitter**.

**Scope:**
1. **Complexity Metrics (RQ1, Generation-Zeit):** LOC, Cyclomatic Complexity (CC), Maximum Nesting Depth (MND), Method-Call Count (MCC), Distinct Identifier Count (DIC) fuer alle 9 Sprachen.
2. **Change-Analysis / AST-Diff (AP 12, Edit-Zeit):** Strukturelle Analyse von Teacher-Edits (original vs. bearbeitet) fuer alle 9 Sprachen.

**Explizit NICHT im Scope:** Hints (AP 13) fuer neue Sprachen. Hints bleiben auf Java (javac + SpotBugs) und Python (Ruff) beschraenkt — der Aufwand fuer sprachspezifische Linter-Integration (ESLint, clippy, clang-tidy etc.) uebersteigt den Nutzen.

---

**Problem (Ist-Zustand — funktional gebrochen fuer nicht-Java/Python):**
- **Complexity:** `TaskEnricher.java` berechnet Metriken nur fuer Java (via `JavaCodeAnalyzer`). Alle anderen Sprachen geben **Nullwerte** zurueck (`otherLanguageComplexity()`) → cz-Score ist 0, RQ1-Auswertung unmoeglich.
- **Change-Analysis:** `AstAnalyzer.java` nutzt JavaParser fuer Java und Zeilen-Heuristiken fuer Python. Alle anderen Sprachen werden **komplett ignoriert** → keine strukturelle Erfassung von Lehrer-Edits.
- Beide Implementierungen sind sprachspezifisch hartcodiert — jede neue Sprache erfordert eine komplett eigene Analyse-Implementierung.
- **Clean-Architecture-Verstoss:** `TaskEnricher` (Application) nutzt `JavaCodeAnalyzer` (externe JavaParser-Lib) direkt, ohne Domain-Interface. `AstAnalyzer` (Application) nutzt JavaParser und OS-Regex direkt. Keine Abstraktion der Analyse-Faehigkeit.

**Dieses AP behebt sowohl den funktionalen Bruch (Nullwerte/fehlende Analyse) als auch den architektonischen Verstoss (fehlende Abstraktion).**

**Loesung: tree-sitter als universeller Parser**

[tree-sitter](https://tree-sitter.github.io/) ist eine universelle Parser-Library die 40+ Sprachen mit einer **einheitlichen API** parsen kann. Der Analyse-Code wird einmal geschrieben und funktioniert fuer alle Sprachen — der einzige Unterschied pro Sprache sind die **Node-Type-Namen** im AST (z.B. `for_statement` in Java vs. `for_expression` in Rust).

Java-Bindings: `org.treesitter:jtreesitter` (JNI-basiert, Maven Central).

**Architektur (Clean Architecture):**

tree-sitter ist eine externe Library (JNI). Nach Clean Architecture gehoert die konkrete Anbindung in die **Infrastructure-Schicht**, nicht in Application. Die Application-Schicht arbeitet nur gegen Domain-Interfaces.

```
Domain (interfaces/)
  ├── CodeAnalyzer.java              ← Interface: computeComplexity(), analyzeChanges()
  │                                     Definiert WAS die Analyse kann, nicht WIE.
  └── (bestehende Repository-Interfaces bleiben)

Application (service/)
  ├── TaskEnricher.java              ← injiziert CodeAnalyzer (statt JavaCodeAnalyzer direkt)
  ├── analysis/
  │     ├── ChangeAnalysisService    ← injiziert CodeAnalyzer (statt AstAnalyzer direkt)
  │     ├── SyntacticAnalyzer        ← bleibt (rein Java, sprachunabhaengig)
  │     └── ChangeScoreCalculator    ← bleibt (rein Java, sprachunabhaengig)
  └── (JavaCodeAnalyzer, ComplexityAnalyzer, AstAnalyzer werden entfernt)

Infrastructure (analysis/treesitter/)   ← NEU
  ├── TreeSitterCodeAnalyzer.java    ← implementiert CodeAnalyzer-Interface
  │                                     Einzige Klasse die tree-sitter-API kennt.
  └── LanguageRegistry.java          ← Node-Type-Mappings pro Sprache
                                        (welche AST-Knoten = function, loop, etc.)
```

**Warum so?**
- `TaskEnricher` und `ChangeAnalysisService` sind **austauschbar** — sie kennen nur das Interface, nicht tree-sitter.
- tree-sitter koennte theoretisch durch ANTLR, JavaParser, oder etwas anderes ersetzt werden — nur die Infrastructure-Schicht aendert sich.
- Bestehende Hint-Analyzer (`RuffAnalyzer`, `JavacAnalyzer`, `SpotBugsAnalyzer`) bleiben wo sie sind — sie sind nicht im Scope von AP 26.

Kein zusaetzlicher Container. Alles laeuft im bestehenden Backend-Prozess.

**Unterstuetzte Sprachen:**

| Sprache | tree-sitter Grammar | Complexity | Change-Analysis | Hints |
|---------|-------------------|------------|-----------------|-------|
| **Java** | `tree-sitter-java` | ✅ (ersetzt JavaCodeAnalyzer) | ✅ (ersetzt JavaParser) | ✅ (javac + SpotBugs, bestehend) |
| **Python** | `tree-sitter-python` | ✅ (ersetzt Regex-Heuristik) | ✅ (ersetzt Zeilen-Heuristik) | ✅ (Ruff, bestehend) |
| **JavaScript** | `tree-sitter-javascript` | ✅ neu | ✅ neu | ❌ |
| **TypeScript** | `tree-sitter-typescript` | ✅ neu | ✅ neu | ❌ |
| **C** | `tree-sitter-c` | ✅ neu | ✅ neu | ❌ |
| **C++** | `tree-sitter-cpp` | ✅ neu | ✅ neu | ❌ |
| **C#** | `tree-sitter-c-sharp` | ✅ neu | ✅ neu | ❌ |
| **Rust** | `tree-sitter-rust` | ✅ neu | ✅ neu | ❌ |
| **Go** | `tree-sitter-go` | ✅ neu | ✅ neu | ❌ |

**Aufwand pro neue Sprache:** ~1 Maven-Dependency + ~20-50 Zeilen Node-Type-Mapping (z.B. welcher Node-Typ ist eine Funktion, eine Schleife, ein Conditional etc.).

**Implementierungsplan:**

**Phase 1: Domain-Interface + Infrastructure-Grundlage**
1. `CodeAnalyzer.java` Interface in `domain/interfaces/`:
   ```java
   public interface CodeAnalyzer {
       ComplexityResult computeComplexity(String code, String language);
       AstAnalysisResult analyzeChanges(String original, String edited, String language);
       boolean supportsLanguage(String language);
   }
   ```
2. Maven-Dependencies: `org.treesitter:jtreesitter` + Grammar-JARs fuer alle 9 Sprachen
3. `LanguageRegistry.java` in `infrastructure/analysis/treesitter/`:
   - Mapping von Sprache → Grammar + Node-Type-Konfiguration
   - Pro Sprache: welche Node-Types zaehlen als `function`, `loop`, `conditional`, `method_call`, `identifier`, etc.
   - Datenstruktur: `Map<String, LanguageConfig>` mit Listen von Node-Type-Strings
4. `TreeSitterCodeAnalyzer.java` in `infrastructure/analysis/treesitter/`:
   - Implementiert `CodeAnalyzer`-Interface
   - Einzige Klasse die die tree-sitter-API direkt nutzt

**Phase 2: Complexity-Metriken migrieren**
1. `TaskEnricher.java` refactoren: `private final CodeAnalyzer codeAnalyzer` statt direkter `JavaCodeAnalyzer`-Nutzung
2. Spring injiziert `TreeSitterCodeAnalyzer` als einzige `CodeAnalyzer`-Implementierung
3. Alle 9 Sprachen liefern dieselben 5 Metriken (LOC, CC, MND, MCC, DIC)
4. Z-Standardisierung pro Sprache bleibt wie bisher

**Phase 3: Change-Analysis migrieren**
1. `ChangeAnalysisService.java` refactoren: nutzt `CodeAnalyzer.analyzeChanges()` statt `AstAnalyzer` direkt
2. AST-Profile (functions, variables, control_flow, etc.) werden aus tree-sitter-Knoten gezaehlt
3. Diff-Logik (nodes_added, nodes_removed, structural_change_score) bleibt identisch
4. `SyntacticAnalyzer.java` und `ChangeScoreCalculator.java` bleiben unveraendert (sprachunabhaengig)

**Phase 4: Aufraeum-Arbeiten**
1. `JavaCodeAnalyzer.java` + `ComplexityAnalyzer.java` entfernen (durch `TreeSitterCodeAnalyzer` ersetzt)
2. `AstAnalyzer.java` entfernen — Java-spezifische JavaParser-Logik und Python-Regex-Heuristik nicht mehr noetig
3. JavaParser-Dependency aus `pom.xml` entfernen (falls nicht anderweitig genutzt)
4. Sprach-Dropdown oder Validierung im Frontend pruefen — Freitext-Eingabe bleibt, aber unterstuetzte Sprachen koennten hervorgehoben werden

**Abhaengigkeiten:** AP 12 (Change-Analysis-Engine)

---

## Abhaengigkeitsdiagramm

```
AP 1 (Auth)
├── AP 2 (Rollen & Ownership)
│   ├── AP 3 (Klassen)
│   │   └── AP 4 (Worksheet-Kopie)
│   │       └── AP 19 (Curriculum-Filter & Bibliothek) ← auch AP 25
│   ├── AP 7 (Admin)
│   └── AP 9 (Task-Bearbeitung) ← auch AP 4
│       ├── AP 12 (Change-Analysis-Engine)
│       │   └── AP 26 (Multi-Language Analytics via tree-sitter)
│       ├── AP 16 (Austauschbare Block-Gruppen)
│       ├── AP 20 (Constraint-Based Generation)
│       │   └── AP 21 (Fuenf-Tier-Modus) ← auch AP 15
│       ├── AP 22 (Kontext-Code)
│       └── AP 23 (Manueller Task-Ersteller) ← auch AP 15, AP 16
├── AP 5 (Feedback-Logs)
├── AP 6 (Frontend Auth)
│   └── AP 8 (Navigation)
├── AP 17 (Click-to-Move & Touch)
├── AP 24 (Worksheet- & Task-Export)
├── AP 25 (Concept Detection verbessern)
│   └── AP 19 (Curriculum-Filter & Bibliothek)
├── AP 28 (Refactoring & Code-Qualitaet) ← keine Abhaengigkeiten, empfohlen VOR Feature-APs
└── AP 10 (Analytics Welle 1)
    └── AP 11 (Analytics Welle 2)
        ├── AP 13 (Hint-System)
        │   └── AP 14 (Advanced Analytics Paper v2)
        │       └── AP 15 (Distractor-System & Analytics)
        └── AP 14 (Advanced Analytics Paper v2)
            └── AP 18 (Completion-Indikator & Post-Solution-Review)
```

## Empfohlene Reihenfolge
1. ✅ **AP 1** → Auth-System (Fundament)
2. ✅ **AP 6** → Frontend Auth-Seiten (parallel zu AP 2)
3. ✅ **AP 2** → Rollen & Ownership
4. ✅ **AP 5** → Feedback-Logs erweitern
5. ✅ **AP 8** → Navigation & Routing
6. ✅ **AP 3** → Klassen-System
7. ✅ **AP 7** → Admin-Bereich
8. ✅ **AP 4** → Worksheet-Kopie
9. ✅ **AP 9** → Task-Bearbeitung & Versionierung
10. ✅ **AP 10** → Learning Analytics Welle 1
11. ✅ **AP 11** → Learning Analytics Welle 2
12. ✅ **AP 12** → Change-Analysis-Engine
13. ✅ **AP 13** → Hint-System
14. ✅ **AP 14** → Advanced Learning Analytics Paper v2
15. ✅ **AP 15** → Distractor-System & Distractor-Analytics
16. ✅ **AP 16** → Austauschbare Block-Gruppen
17. ✅ **AP 17** → Click-to-Move & Touch-Interaktion
18. ✅ **AP 18** → Completion-Indikator & Naechste-Aufgabe
19. ✅ **AP 25** → Concept Detection verbessern
20. ⬜ **AP 19** → Curriculum-Filter & Themen-Bibliothek (braucht AP 25)
21. ✅ **AP 20** → Constraint-Based Generation
22. ✅ **AP 22** → Kontext-Code
23. ✅ **AP 21** → Fuenf-Tier-Modus & Erweiterte Parsons-Varianten
24. ✅ **AP 23** → Manueller Task-Ersteller (ohne LLM)
25. ✅ **AP 24** → Worksheet- & Task-Export
26. ✅ **AP 26** → Multi-Language Code Analytics (tree-sitter)
27. ✅ **AP 27** → Internationalisierung (i18n) & Zielsprache
28. ✅ **AP 28** → Refactoring & Code-Qualitaet
29. ✅ **AP 29** → Weitere Findings & Verbesserungen

---

### AP 28: Refactoring & Code-Qualitaet ✅

**Quelle:** Code-Audit vom 30.03.2026

**Ziel:** Bestehende Compile-Errors beseitigen, Clean-Architecture-Verstoesse beheben, Bugs und Sicherheitsluecken fixen, Code-Qualitaet projektuebergreifend auf ein einheitliches Niveau bringen.

**Problem:** Durch das schnelle Hinzufuegen neuer APs haben sich technische Schulden angesammelt: mehrere Dateien kompilieren nicht, Dependency-Injection ist inkonsistent, es gibt potentielle Sicherheitsluecken und die Clean-Architecture-Schichtentrennung wird an mehreren Stellen verletzt.

---

#### 28.1 Compile-Errors beheben (Kritisch)

Die folgenden Dateien kompilieren aktuell NICHT — fehlende Getter/Setter, fehlende Methoden auf DTOs, fehlende Klassen:

| Datei | Problem |
|-------|---------|
| `AuthController.java` | Ruft `getFirstName()`, `getLastName()` auf `UserDTO` und `getNewEmail()` auf `UpdateEmailRequest`, `getCurrentPassword()` auf `UpdatePasswordRequest` — Methoden existieren nicht (fehlende Felder oder fehlende `@Getter` Annotation) |
| `AdminController.java` | Nutzt `Map<String, String>` statt typisierter DTOs; aufgerufene Methoden auf `AdminService`-Rueckgabe fehlen |
| `AdminService.java` | Referenziert fehlende Methoden auf `UserDTO` (`getFirstName`, `setFirstName`, `getRole`, etc.) und `UserRepository` |
| `EmailService.java` | Referenziert nicht-existierende Methoden auf `AuthTokenDTO` oder Konfiguration |
| `TokenService.java` | Referenziert fehlende Methoden auf `AuthTokenDTO` (`isUsed`, `getTokenType`, `getExpiresAt`, etc.) |
| `ChangeAnalysisService.java` | Referenziert fehlende Methoden auf `TaskOriginalDTO` und `TaskChangeAnalysisDTO`; ungueltige Methoden-Referenzen |
| `TieredTaskSequenceDTO.java` (Zeile 102, 115) | `TaskDTO::getTierLevel` wird als ungueltige Methoden-Referenz erkannt — vermutlich Lombok-Annotation-Processor-Problem im Build |

**Aufgaben:**
1. Fehlende Felder auf DTOs ergaenzen (`UserDTO`, `AuthTokenDTO`, `TaskOriginalDTO`, `TaskChangeAnalysisDTO`) ODER bestehende Felder mit `@Getter`/`@Setter` annotieren
2. Fehlende Methoden auf Repository-Interfaces und DAOs ergaenzen (`UserRepository.findById`, `tokenRepository.findByTokenValue`, `tokenRepository.markUsed`, etc.)
3. Typisierte Request-DTOs erstellen fuer `AdminController.updateUser()` (statt `Map<String, String>`)
4. Lombok Annotation-Processor im Maven-Build pruefen (`maven-compiler-plugin` mit `annotationProcessorPaths` fuer Lombok)
5. Nach Fix: `mvn compile` muss fehlerfrei durchlaufen

---

#### 28.2 Sicherheitsluecken fixen (Kritisch)

| # | Problem | Datei | Zeile | Fix |
|---|---------|-------|-------|-----|
| S1 | **XSS via `dangerouslySetInnerHTML`** — Backend-Feedback-HTML wird unsanitized gerendert | `SolvePage.tsx` | ~1101 | DOMPurify einsetzen: `dangerouslySetInnerHTML={{__html: DOMPurify.sanitize(feedbackHTML)}}` |
| S2 | **Hardcoded Root-Admin-Email** in Sourcecode | `AuthService.java` | 24 | Auslagern nach `application.yml` als `app.admin.root-email` |
| S3 | **Tokens in Logs** — Email-Tokens werden vollstaendig in Klartext geloggt | `EmailService.java` | ~59 | Nur maskierte Token loggen: `token.substring(0,8) + "..."` |
| S4 | **Race Condition bei Token-Verbrauch** — Validate + Consume nicht atomar | `TokenService.java` | ~53-68 | Atomares SQL: `UPDATE auth_tokens SET used=true WHERE token_value=? AND used=false RETURNING *` |

---

#### 28.3 Clean Architecture: Dependency Injection vereinheitlichen (Hoch)

**Ist-Zustand:** Mischung aus `@Autowired` Field-Injection und Constructor-Injection.

| Datei | Aktuell | Soll |
|-------|---------|------|
| `TasksController.java` | 8x `@Autowired` auf Feldern (Zeilen 22-38, 287-293) | Constructor-Injection via `@RequiredArgsConstructor` + `private final` |
| `ManualTaskService.java` | Bereits Constructor-Injection | Kein Handlungsbedarf |
| `TaskPersistenceService.java` | Bereits Constructor-Injection | Kein Handlungsbedarf |
| `TaskGeneratorService.java` | Mix: Constructor + `@Value` | OK (Spring `@Value` benoetigt Setter oder Constructor) |

**Aufgabe:** Alle Controller und Services auf Constructor-Injection mit `@RequiredArgsConstructor` umstellen. Felder als `private final` deklarieren.

---

#### 28.4 Clean Architecture: Schichtverletzungen beheben (Hoch)

| # | Verstoss | Datei | Fix |
|---|----------|-------|-----|
| A1 | **Service castet auf konkretes DAO** — `instanceof PostgreClassDao dao` im Service bricht Abstraktion | `ClassService.java` (~191) | `updateTrackingEnabled()` als Methode auf `ClassRepository`-Interface deklarieren |
| A2 | **DTO-Benennung in Domain-Schicht** — Domain-Entities heissen `TaskDTO`, `WorksheetDTO` etc.; in Clean Architecture sollten Domain-Entities keine "DTO"-Suffix tragen | Alle `domain/entities/*.java` | Langfristig: Rename zu `Task`, `Worksheet`, `TieredTaskSequence` etc. Kurzfristig: Dokumentieren als bewusste Entscheidung |
| A3 | **Controller macht Business-Logik** — `TasksController.logFeedbackClick()` enthaelt Tracking-Logik (classId Lookup, Tracking-Check) statt an Service zu delegieren | `TasksController.java` (~281-298) | Logik in `FeedbackLogService` verschieben |
| A4 | **Inkonsistente SQL-Spaltennamen** — `strategyName` in `tiered_task_sequences` ist camelCase statt snake_case (`strategy_name`) | `z_codeowl_schema.sql`, `PostgreSequenceDAO.java` | Spalte umbenennen auf `strategy_name` + alle SQL-Queries anpassen |

---

#### 28.5 Fehlende Transaktionsgrenzen (Hoch)

Mehrere Service-Methoden fuehren mehrere DB-Operationen aus ohne `@Transactional`:

| Datei | Methode | Risiko |
|-------|---------|--------|
| `AdminService.resetPassword()` | Token erstellen + Email senden | Token erstellt aber Email fehlgeschlagen → verwaister Token |
| `WorksheetService.copyWorksheet()` | Worksheet + Sequences + Tasks kopieren | Teilkopie bei Fehler |
| `ClassService.addStudent()` | User erstellen + Klasse zuweisen | User ohne Klasse |

**Aufgabe:** `@Transactional` auf alle mehrstufigen Service-Methoden.

---

#### 28.6 Null-Safety & Validierung (Mittel)

| # | Problem | Datei | Fix |
|---|---------|-------|-----|
| N1 | `AdminController.updateUser()` nutzt `Map.get()` ohne Null-Check → NPE wenn Felder fehlen | `AdminController.java` (~32-39) | Typisiertes DTO mit `@Valid` + `@NotBlank` |
| N2 | `ClassService` nutzt `new UUID(0,0)` als Sentinel fuer Anonymous-User | `ClassService.java` (~234) | `null` nutzen + Null-Check |
| N3 | `TasksController` wildcard-Import `java.io.*` obwohl keine IO-Operationen | `TasksController.java` | Ungenutzte Imports entfernen |

---

#### 28.7 Frontend-Qualitaet (Mittel)

| # | Problem | Datei | Fix |
|---|---------|-------|-----|
| F1 | **Kein Error Boundary** — ein React-Fehler crasht die ganze App (weisser Bildschirm) | `App.tsx` | `ErrorBoundary`-Komponente um `<Routes>` wrappen |
| F2 | **Doppelter useEffect** in SolvePage — identische API-Calls in zwei Effects | `SolvePage.tsx` (~29 und ~125) | Zu einem Effect konsolidieren |
| F3 | **localStorage ohne try-catch** an manchen Stellen (Private-Mode wirft) | `AuthContext.tsx` (~29-30) | Alle `localStorage`-Zugriffe in try-catch wrappen |
| F4 | **Kein Runtime-Typing** fuer API-Responses — `as AuthUser` ohne Validierung | `AuthContext.tsx`, `SolvePage.tsx` | Langfristig: `zod` oder aehnliches; Kurzfristig: Defensive Null-Checks |

---

#### 28.8 Logging & Observability (Niedrig)

| # | Problem | Datei | Fix |
|---|---------|-------|-----|
| L1 | `TaskPersistenceService` loggt auf `INFO`-Level in Read-Methoden (`loadTaskById`, `getTasksForSequence`) — zu verbose fuer Produktion | `TaskPersistenceService.java` (~89, ~155) | Auf `DEBUG` herabstufen |
| L2 | Deutsche UI-Strings in Backend-Responses (`"Registrierung erfolgreich..."`) | `AuthController.java` | In `application.yml` auslagern oder nur Codes zurueckgeben, Frontend uebersetzt |

---

**Abhaengigkeiten:** Keine — kann jederzeit parallel zu anderen APs bearbeitet werden. Empfohlen VOR weiteren Feature-APs, da Compile-Errors andere Entwicklung blockieren.

**Priorisierte Reihenfolge innerhalb AP 28:**
1. 28.1 — Compile-Errors (blockiert Build)
2. 28.2 — Sicherheitsluecken
3. 28.3 + 28.4 — Clean Architecture
4. 28.5 — Transaktionen
5. 28.6 + 28.7 — Null-Safety + Frontend
6. 28.8 — Logging

**Status:** ✅ Abgeschlossen

---

### AP 29: Weitere Findings & Verbesserungen (aus AP 28 entstanden) ✅

**Ziel:** Zusaetzliche Verbesserungen, die waehrend der Ausfuehrung von AP 28 identifiziert, aber nicht im Scope von AP 28 umgesetzt wurden.

**29.1 — Frontend: SolvePage Optimierung**
- **Doppelter API-Call:** `SolvePage.tsx` holt `/sequence/{id}` zweimal (einmal fuer `ownerId`, einmal fuer `nextTaskId`). Zusammenfuehren in einen einzigen Call.
- **Error Boundary:** React Error Boundary um die SolvePage (und andere Hauptseiten) wrappen, damit Render-Fehler nicht die gesamte App crashen.
- **useEffect-Cleanup:** Viele useEffects in SolvePage — pruefen ob einige zusammengefasst werden koennen.

**29.2 — Backend: WorksheetService Verbose Logging**
- `WorksheetService` hat try-catch-rethrow Pattern in fast jeder Methode mit redundantem Error-Logging. Vereinfachen: Exceptions hochblubbern lassen, zentrale Fehlerbehandlung via `GlobalExceptionHandler`.

**29.3 — Backend: AdminService.toMap() → DTO**
- `AdminService.getUsers()` gibt `Map<String, Object>` zurueck statt eines typisierten Response-DTOs. Refactoring zu einem `AdminUserResponse` Record fuer Typsicherheit.

**29.4 — Datenbank: strategyName Column Naming**
- `strategy_name` Column im DB-Schema verwendet bereits snake_case, aber der Java-Feldname `strategyName` wird in Queries als camelCase referenziert. Konsistenz pruefen.

**29.5 — Frontend: PersistentLogger Noise**
- `SolvePage.tsx` hat viele `PersistentLogger.logWithContext()` Calls auf `info`-Level. In Produktion stoerend — pruefen ob auf `debug` umgestellt werden kann.

**Prioritaet:** Niedrig — alles funktional, nur Code-Qualitaet.
**Abhaengigkeiten:** Keine.

**Status:** ✅ Abgeschlossen (29.1-29.3 + 29.5 umgesetzt, 29.4 als bekanntes DB-Schema-Issue dokumentiert — benoetigt Migration)

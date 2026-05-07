# CodeOwl Logging Data Guide

## Overview

CodeOwl erfasst drei Kategorien von Daten, die fuer die Auswertung im Paper relevant sind:

1. **Learning Events** (learning_events) — Feingranulare Interaktionsdaten
2. **Feedback Logs** (feedback_logs) — Korrektheitsprüfungen pro Klick
3. **Task Originals** (task_originals) — Versionierung der Lehrer-Bearbeitungen

---

## 1. Learning Events (`learning_events`)

### Tabellen-Schema

| Spalte | Typ | Beschreibung |
|--------|-----|-------------|
| id | UUID | Primaerschluessel |
| user_id | UUID (nullable) | NULL wenn Tracking deaktiviert oder anonym |
| task_id | UUID | Referenz auf die Aufgabe |
| sequence_id | UUID (nullable) | Referenz auf die Sequenz |
| tier_level | INT (nullable) | Tier 1/2/3 |
| event_type | VARCHAR(50) | Typ des Events (siehe unten) |
| event_data | JSONB | Strukturierte Zusatzdaten |
| created_at | TIMESTAMP | Zeitstempel |

### Event-Typen und ihre Daten

#### `task_opened`
Gefeuert wenn ein Schueler eine Aufgabe oeffnet.
```json
{ "sequenceId": "...", "tierLevel": 2 }
```

#### `feedback_requested`
Gefeuert bei jedem Klick auf den Feedback-Button.
```json
{
  "attemptNumber": 3,
  "feedbackCount": 3,
  "blockOrder": ["block_0", "block_3", "block_1", "block_2"],
  "correctCount": 2,
  "totalBlocks": 5,
  "improvement": 1,
  "backtrackCount": 0
}
```

#### `task_correct`
Gefeuert wenn alle Bloecke korrekt angeordnet sind.
```json
{
  "attempts": 5,
  "feedbackCount": 5,
  "activeTimeMs": 45000,
  "totalTimeMs": 62000,
  "backtrackCount": 1
}
```

#### `task_closed`
Gefeuert beim Verlassen der Aufgabe (Navigation oder Tab-Wechsel).
```json
{
  "totalTimeMs": 62000,
  "completed": true,
  "totalAttempts": 5
}
```

#### `task_abandoned`
Gefeuert wenn Schueler die Aufgabe verlasst ohne sie zu loesen (min. 1 Versuch).
```json
{
  "attempts": 3,
  "feedbackCount": 3,
  "activeTimeMs": 30000,
  "totalTimeMs": 45000,
  "backtrackCount": 0
}
```

#### `task_reset`
Gefeuert beim Klick auf "Shuffle" (Bloecke neu mischen).
```json
{
  "attemptCount": 2,
  "feedbackCount": 2,
  "activeTimeMs": 15000,
  "totalTimeMs": 20000
}
```

#### `tier_opened`
Gefeuert wenn Schueler einen Tier-Tab oeffnet.
```json
{ "tierLevel": 2 }
```

#### `sequence_rating`
Gefeuert nach Abschluss — Schueler bewertet die Sequenz.
```json
{
  "enjoymentRating": 4,
  "difficultyRating": 3
}
```

#### `sequence_completed`
Gefeuert wenn alle Tasks einer Sequenz geloest wurden.
```json
{}
```

### Anonymisierung

Wenn `tracking_enabled = false` fuer die Klasse des Schuelers, wird `user_id` auf NULL gesetzt. Die Event-Daten werden trotzdem gespeichert — nur die Zuordnung zum Schueler entfaellt.

---

## 2. Feedback Logs (`feedback_logs`)

Einfache Tabelle die jeden einzelnen Feedback-Klick erfasst.

| Spalte | Typ | Beschreibung |
|--------|-----|-------------|
| id | UUID | Primaerschluessel |
| task_id | UUID | Welche Aufgabe |
| correct | BOOLEAN | War die Loesung zum Zeitpunkt des Klicks korrekt? |
| user_id | UUID (nullable) | Wer hat geklickt (wenn eingeloggt) |
| class_id | UUID (nullable) | In welcher Klasse (wenn zugewiesen) |
| clicked_at | TIMESTAMP | Zeitstempel |

### Unterschied zu learning_events

`feedback_logs` ist die **einfache, flache** Version: ein Boolean pro Klick.
`learning_events` mit `feedback_requested` ist die **reichhaltige** Version: Block-Positionen, Verbesserung, Backtracking.

Im Paper von 2025 wurde `feedback_logs` fuer die **wrong-rate** Berechnung verwendet (RQ4, Figure 8).

---

## 3. Task Originals (`task_originals`)

Speichert das KI-generierte Original bevor der Lehrer es bearbeitet.

| Spalte | Typ | Beschreibung |
|--------|-----|-------------|
| id | UUID | Primaerschluessel |
| task_id | UUID (unique) | 1:1 Zuordnung zur bearbeiteten Aufgabe |
| original_problem_statement | TEXT | Originaler Aufgabentext |
| original_code | TEXT | Originaler Code |
| adjustment_rating | INT (1-5) | Lehrer-Einschaetzung der Anpassungsbeduerfigkeit |
| saved_at | TIMESTAMP | Wann wurde erstmals bearbeitet |

---

## Aussagekraft fuer das Paper

### RQ: Complexity Progression (bestehend, erweitert)

**Datenquelle:** `learning_events` (task_correct, feedback_requested)

| Metrik | Berechnung | Aussage |
|--------|-----------|---------|
| **Wrong-Rate pro Task** | `feedback_requested` Events mit `correctCount < totalBlocks` / total | Verhaltensbasierte Schwierigkeitsmetrik |
| **Wrong-Rate pro Tier** | Aggregiert ueber alle Tasks eines Tiers | Steigt Wrong-Rate von T1→T3? (Bestaetigt cz) |
| **Attempts-to-Completion** | `task_correct.attempts` | Wie viele Versuche bis zur Loesung? |

**Paper-Relevanz:** Triangulation von cz (statisch) mit verhaltenbasierten Schwierigkeitsindikatoren.

### RQ: Lehrer-Anpassungsverhalten (NEU, AP12)

**Datenquelle:** `task_originals` + `tasks` (Vorher/Nachher)

| Metrik | Berechnung | Aussage |
|--------|-----------|---------|
| **Change Ratio (Text)** | Levenshtein(original, edited) / max(len) | Wie stark wird der Aufgabentext geaendert? |
| **Change Ratio (Code)** | Levenshtein(original_code, edited_code) / max(len) | Wie stark wird der Code geaendert? |
| **AST Structural Score** | AST-Diff zwischen Original und Bearbeitung | Kosmetisch vs. strukturelle Aenderung |
| **Adjustment Rating** | Direkt aus `adjustment_rating` (1-5) | Subjektive Einschaetzung des Lehrers |
| **Korrelation subjektiv/objektiv** | Pearson/Spearman(adjustment_rating, change_score) | Stimmt Selbsteinschaetzung mit Realitaet ueberein? |

**Paper-Relevanz:** "Wie viel muessen Lehrer an KI-generierten Aufgaben aendern?" — zentrale Frage fuer LLM-in-Education.

### RQ: Student Learning Behaviour (NEU)

**Datenquelle:** `learning_events`

| Metrik | Berechnung | Aussage |
|--------|-----------|---------|
| **Active vs. Idle Time** | `activeTimeMs` vs. `totalTimeMs` | Wie fokussiert arbeiten Schueler? |
| **Backtracking Rate** | `backtrackCount` / `feedbackCount` | Wie oft werden korrekte Bloecke wieder verschoben? |
| **Improvement Curve** | `improvement` ueber aufeinanderfolgende Feedbacks | Lernen Schueler zwischen Feedback-Zyklen? |
| **Abandon Rate** | `task_abandoned` / `task_opened` | Wie viele Schueler geben auf? |
| **Reset Rate** | `task_reset` / `task_opened` | Wie oft wird komplett neu angefangen? |
| **Tier Skip Patterns** | `tier_opened` Reihenfolge | Starten Schueler bei T1 oder ueberspringen sie? |
| **Sequence Satisfaction** | `sequence_rating` (enjoyment + difficulty) | Subjektive Bewertung der Sequenz |

**Paper-Relevanz:** Feingranulare Verhaltensanalyse — ueber simple Wrong-Rate hinaus.

### RQ: Engagement & Self-Regulation

**Datenquelle:** `learning_events` + `feedback_logs`

| Metrik | Berechnung | Aussage |
|--------|-----------|---------|
| **Time-on-Task Distribution** | Histogramm von `totalTimeMs` pro Tier | Wird mehr Zeit in hoeheren Tiers verbracht? |
| **Feedback-Seeking Frequency** | `feedbackCount` pro Task | Nutzen Schueler Feedback strategisch? |
| **Completion Funnel** | task_opened → feedback_requested → task_correct | Wo brechen Schueler ab? |
| **Cross-Tier Persistence** | Wie viele loesen T1 UND T2 UND T3? | Selbstreguliertes Lernen |

---

## Export & Analyse-Workflow

### Daten exportieren

```bash
# Alle Learning Events als JSON (Admin-Endpoint)
curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/events/export > events.json

# Sequence-Summary
curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/events/summary/{sequenceId}

# Direkt aus der DB exportieren
docker exec codeowl-clean-db psql -U postgres -d codeowl \
  -c "\COPY (SELECT * FROM learning_events) TO STDOUT WITH CSV HEADER" > learning_events.csv

docker exec codeowl-clean-db psql -U postgres -d codeowl \
  -c "\COPY (SELECT * FROM feedback_logs) TO STDOUT WITH CSV HEADER" > feedback_logs.csv

docker exec codeowl-clean-db psql -U postgres -d codeowl \
  -c "\COPY (SELECT to2.*, t.statement, t.solution FROM task_originals to2 JOIN tasks t ON to2.task_id = t.id) TO STDOUT WITH CSV HEADER" > task_changes.csv
```

### Analyse in Python/R

Die exportierten CSV/JSON-Dateien koennen direkt mit den bestehenden Python-Scripts oder in R/SPSS analysiert werden:

- `learning_events.csv` → Verhaltensanalyse (Attempts, Time, Backtracking)
- `feedback_logs.csv` → Wrong-Rate Berechnung (kompatibel mit bestehendem Paper-Code)
- `task_changes.csv` → Lehrer-Anpassungsanalyse (AP12)

### Nuetzliche SQL-Queries

```sql
-- Wrong-Rate pro Task
SELECT task_id,
       COUNT(*) FILTER (WHERE NOT correct) AS wrong,
       COUNT(*) AS total,
       ROUND(COUNT(*) FILTER (WHERE NOT correct)::numeric / COUNT(*), 3) AS wrong_rate
FROM feedback_logs GROUP BY task_id;

-- Durchschnittliche Attempts pro Tier
SELECT e.tier_level,
       AVG((e.event_data->>'attempts')::int) AS avg_attempts,
       AVG((e.event_data->>'totalTimeMs')::int) / 1000.0 AS avg_time_seconds
FROM learning_events e
WHERE e.event_type = 'task_correct'
GROUP BY e.tier_level ORDER BY e.tier_level;

-- Abandon-Rate pro Tier
SELECT tier_level,
       COUNT(*) FILTER (WHERE event_type = 'task_abandoned') AS abandoned,
       COUNT(*) FILTER (WHERE event_type = 'task_opened') AS opened,
       ROUND(COUNT(*) FILTER (WHERE event_type = 'task_abandoned')::numeric /
             NULLIF(COUNT(*) FILTER (WHERE event_type = 'task_opened'), 0), 3) AS abandon_rate
FROM learning_events
WHERE event_type IN ('task_opened', 'task_abandoned')
GROUP BY tier_level ORDER BY tier_level;

-- Backtracking-Haeufigkeit
SELECT tier_level,
       AVG((event_data->>'backtrackCount')::int) AS avg_backtracks,
       MAX((event_data->>'backtrackCount')::int) AS max_backtracks
FROM learning_events
WHERE event_type = 'task_correct'
GROUP BY tier_level ORDER BY tier_level;
```

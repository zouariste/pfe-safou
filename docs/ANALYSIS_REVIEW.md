# Analyse-Review: Vereinfachung & Aufraeum-Plan

## 1. Ueberlappung: feedback_logs vs. learning_events

### Problem

Es gibt **zwei Systeme** die dasselbe erfassen:

| System | Tabelle | Event |
|--------|---------|-------|
| Alt (Paper v1) | `feedback_logs` | Einfacher Boolean pro Feedback-Klick |
| Neu (AP10/11) | `learning_events` | `feedback_requested` mit Blockposition, Attempts, Improvement |

Die `learning_events`-Version ist eine **strikte Obermenge** — alles was `feedback_logs` kann, kann `learning_events` besser:

| Feature | feedback_logs | learning_events |
|---------|--------------|-----------------|
| Korrektheit | `correct: boolean` | `correctCount` + `totalBlocks` (granularer) |
| Zeitstempel | `clicked_at` | `created_at` |
| User-Zuordnung | `user_id` | `user_id` (mit Anonymisierung) |
| Klassen-Zuordnung | `class_id` | Ueber `user_id → class_students` ableitbar |
| Block-Positionen | Nein | `blockOrder: [...]` |
| Verbesserung | Nein | `improvement: delta` |
| Backtracking | Nein | `backtrackCount` |
| Versuchsnummer | Nein | `attemptNumber` |

### Empfehlung

**feedback_logs behalten** — aber nur als Rueckwaertskompatibilitaet fuer das bestehende Paper.
Die Python-Scripts fuer RQ4 (wrong-rate, Figure 8) lesen `feedback_logs` direkt.

Fuer das **neue Paper**: ausschliesslich `learning_events` verwenden.

Langfristig: `feedback_logs` in `learning_events` migrieren und Tabelle entfernen.

---

## 2. Python-Scripts: Was wird gebraucht, was nicht?

### Aktiv genutzt (behalten)

| Script | Zweck | Benutzt fuer |
|--------|-------|-------------|
| `fetch_data.py` | Google Sheets API Zugriff | Alle RQ-Auswertungen |
| `fetch_eval_questions.py` | Fragebogen-Header laden | RQ3/RQ4 Plots |
| `rq1new.py` | RQ1 Complexity-Analyse | Paper Table I |
| `rq1_vargha_delaney.py` | Effektstaerken-Berechnung | Paper Table II |
| `rq2lightweight.py` | RQ2 Problem Statement Quality | Paper Figure 5 |
| `bootstrap_analysis.py` | Bootstrap-Konfidenzintervalle | Paper RQ2 |
| `plots_teacher_questions_standalone.py` | Lehrer-Likert-Plots | Paper Figure 6 |
| `plots_student_questions_standalone.py` | Schueler-Likert-Plots | Paper Figure 7 |
| `feedback_plots.py` | Wrong-Rate Plots | Paper Figure 8 |
| `plot_style.py` | Gemeinsame Plotkonfiguration | Alle Plots |

### Vermutlich redundant / veraltet

| Script | Grund |
|--------|-------|
| `categorization.py` | **Leer** (0 Bytes) — kann geloescht werden |
| `rq2.py` | Ersetzt durch `rq2lightweight.py` |
| `plots.py` | Alte Plot-Library, ersetzt durch `*_standalone.py` Versionen |
| `plots_students.py` | Ersetzt durch `plots_student_questions_standalone.py` |
| `plots_codes.py` | Unklar ob noch referenziert |
| `plots_codes_students.py` | Unklar ob noch referenziert |
| `fetch_data_students.py` | Moeglicherweise durch `fetch_data.py` abgedeckt |
| `fetch_data_codes.py` | Moeglicherweise durch `fetch_data.py` abgedeckt |
| `fetch_data_codes_students.py` | Moeglicherweise durch `fetch_data.py` abgedeckt |
| `rq1_plot_vargha_delaney.py` | Unklar ob Ergebnis im Paper verwendet wird |
| `rq1_mode_comparisons.py` | Moeglicherweise in `rq1new.py` integriert |
| `export_rq1_wide.py` | Einmal-Export, kein wiederkehrender Bedarf |
| `conceptplots.py` | Concept-Verteilung — nicht im Paper erwaehnt |

### Empfehlung

Vor dem Loeschen: **Pruefen ob die Output-Dateien** (PDFs/PNGs) im Paper-LaTeX referenziert werden.
Im Zweifel: in einen `_archive/` Ordner verschieben statt loeschen.

---

## 3. DB-Tabellen: Was wird gebraucht?

### Kern-Tabellen (unveraenderlich)

| Tabelle | Zweck | Status |
|---------|-------|--------|
| `users` | Alle User | Aktiv |
| `auth_tokens` | JWT/Invite/Reset Tokens | Aktiv |
| `tasks` | Generierte Aufgaben | Aktiv |
| `tiered_task_sequences` | Sequenzen mit 3 Tiers | Aktiv |
| `tiered_task_sequence_concepts` | Konzept-Tags pro Sequenz | Aktiv |
| `worksheets` | Arbeitsblaetter | Aktiv |
| `worksheets_sequence` | N:M Worksheet ↔ Sequence | Aktiv |
| `classes` | Klassen | Aktiv |
| `class_students` | N:M Klasse ↔ Schueler | Aktiv |
| `class_worksheets` | N:M Klasse ↔ Worksheet | Aktiv |

### Analyse-Tabellen

| Tabelle | Zweck | Status |
|---------|-------|--------|
| `learning_events` | Feingranulare Interaktionsdaten | **Aktiv** (AP10/11) |
| `feedback_logs` | Einfache Korrektheit pro Klick | **Redundant** (durch learning_events abgedeckt) |
| `task_originals` | Original vor Bearbeitung | **Aktiv** (AP9, benoetigt fuer AP12) |

### Eval-Tabellen (aus Paper v1)

| Tabelle | Zweck | Status |
|---------|-------|--------|
| `eval_input` | Evaluierungs-Inputs (99 Code-Snippets) | **Nur fuer Paper-Reproduktion** |
| `eval_run` | Evaluierungs-Durchlaeufe | **Nur fuer Paper-Reproduktion** |
| `eval_result` | Evaluierungs-Ergebnisse (Metriken) | **Nur fuer Paper-Reproduktion** |
| `solve_code_generations` | Code-Generierungen beim Loesen | **Nur fuer Paper-Reproduktion** |
| `solve_eval_run_metrics` | Metriken pro Evaluierungs-Lauf | **Nur fuer Paper-Reproduktion** |
| `problem_statement_generations` | PS-Generierungen | **Nur fuer Paper-Reproduktion** |
| `problem_statement_ratings` | PS-Bewertungen (RQ2) | **Nur fuer Paper-Reproduktion** |
| `prompt_templates` | Prompt-Versionen | **Nur fuer Paper-Reproduktion** |

### Referenz-Tabellen

| Tabelle | Zweck | Status |
|---------|-------|--------|
| `concepts` | Programmierkonzepte (8 Stueck) | Aktiv (Tag-System) |
| `concept_curriculum_topic` | Konzept ↔ Lehrplan | Aktiv |
| `curriculum_topics` | Lehrplanthemen | Aktiv |
| `snippets` | Code-Snippets Bibliothek | Aktiv |
| `snippet_concept` | N:M Snippet ↔ Konzept | Aktiv |
| `snippet_sources` | Quellen der Snippets | Aktiv |
| `strategies` | Generierungsstrategien | Aktiv |
| `versions` | Versionierung | Aktiv |

### Empfehlung

Die **eval_***, **solve_***, **problem_statement_*** Tabellen sind Artefakte der Paper-v1-Evaluation.
Sie werden im laufenden Betrieb **nicht beschrieben** — nur fuer Reproduktion der Ergebnisse benoetigt.

Option A: Behalten (kein Schaden, ~50 Zeilen SQL)
Option B: In separates `eval_schema.sql` auslagern und nur bei Bedarf laden

---

## 4. Backend-Code: Vereinfachungspotential

### FeedbackLogService redundant?

`FeedbackLogService` und `LearningEventService` loggen beide Feedback-Klicks.
Aktuell werden bei jedem Feedback-Klick **beide** Systeme gefuellt.

**Empfehlung:** Im Frontend `SolvePage.tsx` wird beim Feedback-Klick sowohl
`POST /api/tasks/{taskId}/feedback-log` als auch ein `feedback_requested` Learning-Event gefeuert.
Langfristig: `feedback-log` Endpoint entfernen und nur `learning_events` nutzen.

### OwnershipService

Funktioniert gut, aber die Methode `checkOwnership()` hat unterschiedliche Signaturen
je nach Ressource (Worksheet, Sequence, Task). Koennte vereinheitlicht werden.

### TaskEditService + TaskGeneratorService

Klar getrennte Verantwortlichkeiten. Kein Vereinfachungsbedarf.

---

## 5. Erkenntnisse aus dem Paper

### Was das Paper **bereits nutzt** und weiter braucht:

1. **Composite Complexity Score (cz)** — z-standardisierte Metriken (LOC, CC, MND, MCC, DIC)
   → In `tasks` Tabelle gespeichert, berechnet beim Generieren
   → **Muss bleiben**

2. **Wrong-Rate aus feedback_logs** — `share_correct = correct_clicks / total_clicks`
   → Paper Figure 8a, 8b
   → Kann kuenftig aus `learning_events` berechnet werden

3. **Tier-Progression-Metriken** — `pass_basic`, `pass_step`, `pass_advanced`
   → Berechnet aus cz-Differenzen zwischen Tiers
   → Paper Table I
   → Berechnung erfolgt in Python, nicht im Backend

4. **Mode-Vergleiche** (full/code/meta) — Vargha-Delaney A_paired
   → Paper Table II
   → Einmal-Analyse, kein laufender Bedarf

### Was das Paper **nicht nutzt** aber wir jetzt haben:

1. **Active Time Tracking** — NEU, nicht im Paper
2. **Backtracking Detection** — NEU, nicht im Paper
3. **Block-Order Snapshots** — NEU, nicht im Paper
4. **Improvement Curves** — NEU, nicht im Paper
5. **Sequence Ratings** — NEU, nicht im Paper
6. **Task Originals / Adjustment Rating** — NEU, nicht im Paper

Diese neuen Daten ermoeglichen eine **deutlich tiefere Analyse** fuer das naechste Paper.

---

## 6. Umsetzungsplan

### Phase 1: Aufraemen (Low Risk, sofort machbar)

| # | Aktion | Aufwand |
|---|--------|---------|
| 1.1 | `categorization.py` loeschen (leer) | 1 min |
| 1.2 | Redundante Python-Scripts in `_archive/` verschieben (nach Pruefung) | 30 min |
| 1.3 | Eval-Tabellen (`eval_*`, `solve_*`, `problem_statement_*`) in separates `eval_schema.sql` auslagern | 15 min |

### Phase 2: Redundanz beseitigen (Medium Risk, nach Testphase)

| # | Aktion | Aufwand |
|---|--------|---------|
| 2.1 | Frontend: Doppeltes Feedback-Logging entfernen (nur noch `learning_events`) | 30 min |
| 2.2 | Backend: `FeedbackLogService` als `@Deprecated` markieren | 5 min |
| 2.3 | Python: Neues Script `analyse_learning_events.py` schreiben das wrong-rate aus `learning_events` berechnet (Ersatz fuer `feedback_logs`-basierte Berechnung) | 2h |
| 2.4 | Sicherstellen dass Paper-Reproduktion weiterhin funktioniert (feedback_logs fuer alte Daten behalten) | 1h |

### Phase 3: AP12 implementieren (Change-Analysis-Engine)

| # | Aktion | Aufwand |
|---|--------|---------|
| 3.1 | `task_change_analyses` Tabelle erstellen | 15 min |
| 3.2 | `SyntacticAnalyzer.java` — Levenshtein, Jaccard, Wort-Diff | 3h |
| 3.3 | `AstAnalyzer.java` — JavaParser + tree-sitter Integration | 6h |
| 3.4 | `ChangeAnalysisService.java` — Orchestrierung + Composite Score | 2h |
| 3.5 | API-Endpoints fuer On-Demand und Batch-Analyse | 1h |
| 3.6 | CSV/JSON-Export fuer R/Python | 1h |

### Phase 4: Neue Python-Analyse-Scripts (fuer naechstes Paper)

| # | Aktion | Aufwand |
|---|--------|---------|
| 4.1 | `analyse_teacher_adjustments.py` — Auswertung task_originals + change_analyses | 4h |
| 4.2 | `analyse_student_behaviour.py` — Auswertung learning_events (Time, Backtracking, Abandon) | 4h |
| 4.3 | `analyse_correlation.py` — Korrelation subjektiv (adjustment_rating) ↔ objektiv (change_score) | 2h |
| 4.4 | Publikationsreife Plots fuer alle neuen Metriken | 4h |

### Reihenfolge

```
Phase 1 (sofort) → Phase 2 (nach naechstem Deployment) → Phase 3 (vor Datensammlung) → Phase 4 (nach Datensammlung)
```

### Abhaengigkeiten

```
Phase 3 haengt ab von: AP9 (task_originals) ✅ bereits implementiert
Phase 4 haengt ab von: Phase 3 + echte Nutzungsdaten aus Classroom-Studie
Phase 2 kann unabhaengig von Phase 3 durchgefuehrt werden
```

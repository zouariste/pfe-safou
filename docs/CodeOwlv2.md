# CodeOwl v2 — Was ist neu und warum

> Dieses Dokument gibt einen groben Überblick über alle Neuerungen in CodeOwl v2 gegenüber der ursprünglichen Bachelor-Thesis-Version. Der Fokus liegt auf dem "Was" und "Warum", insbesondere auf allem, was für den weiteren Forschungsverlauf relevant ist.

---

## Motivation

Die Thesis-Version (v1) war ein Proof-of-Concept: ein einzelner LLM-Aufruf generiert drei Parsons-Tiers, das Ergebnis wird anonym im Browser gelöst. Für echten Unterrichtsbetrieb fehlten: Auth, Klassen, Datenpersistenz, Forschungsdaten-Infrastruktur, didaktische Qualitätswerkzeuge (Hints, Distractors) und Curriculum-Einbettung.

v2 adressiert all das systematisch — als vollständige, deploybare Plattform für bayerische Gymnasien.

---

## 1. Plattform-Grundlage

### 1.1 Authentifizierung & Rollen (AP1, AP2, AP6)

**Was:** Spring Security + JWT (HS256), Argon2-Passwort-Hashing, drei Rollen: `ADMIN`, `TEACHER`, `STUDENT`.

**Warum:** Ohne Auth sind keine nutzer-gebundenen Forschungsdaten möglich (Lernverhalten kann nicht Schüler-IDs zugeordnet werden). Ohne Rollen können Schüler auf Generierungsendpoints zugreifen.

**Besonderheit — "Anton-Prinzip":** Schüler müssen sich nicht selbst registrieren. Der Lehrer erstellt Schülerkonten innerhalb einer Klasse; der Schüler erhält einen Einmal-Token-Link, über den er ein Passwort und optional eine E-Mail hinterlegt. Senkt die Onboarding-Hürde im Klassenzimmer drastisch.

**Auth-Flows:** Login (Email+PW), Token-Login (Schüler-Link), E-Mail-Verifikation, Passwort-Reset, Onboarding (Schüler setzt eigenes PW), Profil (E-Mail/PW ändern).

**Forschungsrelevanz:** Alle Learning Events sind ab sofort an eine `user_id` gebunden. Anonyme Nutzer (user_id = NULL) bleiben weiter möglich — so können auch nicht-eingeloggte Nutzer Aufgaben lösen.

---

### 1.2 Klassen-System (AP3)

**Was:** Lehrer erstellen Klassen, fügen Schüler hinzu, weisen Worksheets zu. DB-Tabellen: `classes`, `class_students` (n:m), `class_worksheets` (n:m).

**Warum:** Schüler sollen Aufgaben im Kontext ihrer Klasse lösen — nicht als anonyme Gäste. Lehrkräfte brauchen Struktur, um Worksheet-Zuweisungen zu verwalten.

---

### 1.3 Worksheets & Deep-Copy (AP4)

**Was:** Worksheets gruppieren Sequences. Ein Worksheet kann fremden Sequences enthalten (sehen: ja, zuweisen: ja, bearbeiten: nein). `POST /worksheets/{id}/copy` erstellt eine vollständige Tiefenkopie (neuer Owner, neue UUIDs für Worksheet + Sequences + Tasks).

**Warum:** Lehrer sollen aus der Bibliothek anderer Lehrer kopieren und anpassen können, ohne das Original zu verändern. Schafft eine Community ohne explizites Teilen.

---

### 1.4 Admin-Bereich (AP7)

**Was:** Vollständige Nutzerverwaltung (Suche, Pagination, Bearbeiten, Löschen, Admin ernennen, PW-Reset auslösen). Zusätzlich: Bulk-Retag-Tool (asynchroner Hintergrundjob, der alle Sequences re-taggt — nötig für die Bibliothek beim erstmaligen Launch).

**Forschungsrelevanz:** Bulk-Retag ermöglicht es, Legacy-Sequences nachträglich mit strukturellen und semantischen Tags zu versehen, sodass die Bibliothek beim Launch vollständig befüllbar ist.

---

### 1.5 Internationalisierung (AP27)

**Was:** `react-i18next` mit automatischer Browserspracherkennung (Fallback: Deutsch). Alle UI-Strings in `de.json` / `en.json` ausgelagert. Manueller DE/EN-Toggle in der Navbar.

Zusätzlich: `targetLanguage`-Feld bei der Generierung — steuert die Sprache von Problem-Statements und Code-Kommentaren zuverlässig via expliziter Prompt-Instruktion.

**Warum:** Die Thesis dokumentiert (T15): "Deutsch funktioniert nicht" — die LLM-Generierung wich häufig ins Englische aus. Das `targetLanguage`-Feld löst das strukturell.

---

## 2. Bibliothek & Curriculum

### 2.1 Themen-Bibliothek (AP19 Teil A)

**Was:** `LibraryPage` mit Volltextsuche (PostgreSQL `tsvector` + GIN-Index + `ts_headline`) und Zwei-Ebenen-Tagging:

| Ebene | Beispiele | Quelle | Timing |
|-------|-----------|--------|--------|
| Struktur-Tags | `for-loop`, `recursion`, `arrays` | AP25 Concept Detection | Synchron (~50ms) |
| Semantische Tags | `fibonacci`, `binary-search`, `caesar-chiffre` | LLM-Analyse (async Spring Event) | 3–10s nach Generation |

Tags werden in `sequence_tags` gespeichert (Felder: `tag_name`, `source: auto/llm/manual`, `confidence`). Lehrer können Tags inline bearbeiten, ein Autocomplete verhindert Duplikate.

Filter-UI: Sprache, Tier-Anzahl, Tags (Facetten mit Counts), Erstellungsdatum, "Nur meine / Alle". URL-State für teilbare Filterlinks. One-Click-Copy aus der Bibliothek in ein eigenes Worksheet.

**Forschungsrelevanz:** 
- **Tag-Qualität** (LLM vs. deterministisch): Wie präzise erkennt das System Algorithmen und Domänen? Welche Tags werden von Lehrern manuell korrigiert?
- **Wiederverwendbarkeit**: Welche Sequences werden wie oft kopiert? Welche Tags dominieren die Bibliothek?

---

### 2.2 Lehrplan-Integration Bayern (AP19 Teil B)

**Was:** Integration des bayerischen LehrplanPLUS G9 Informatik (Jgst. 7, 9, 10, 11, 12/13) als navigierbare Baumstruktur. Drei neue DB-Tabellen:

- `curriculum_topics` — Lehrplanbereiche hierarchisch (parent_id)
- `concept_curriculum_topic` — Mapping Thema ↔ Programmierkonzepte
- `curriculum_code_examples` — kuratierte Java-Codebeispiele pro Thema (Schwierigkeitsgrad, Aufgabenbeschreibung)

**CurriculumPage:** Lehrer klickt auf einen Lehrplanbereich → sieht vorhandene Sequences oder kann direkt generieren. Bei Generierung werden Konzepte und ein passendes Codebeispiel automatisch als Kontext injiziert.

**Dedizierter Prompt (`SequenceCurriculum`):** 164 Zeilen System-Prompt mit 8 Jahrgangsstufen/Track-Kalibrierungen. Steuert: Schwierigkeitsniveau, erwartete Vorkenntnisse, Zielsprache (Deutsch als Default), Integration der Referenz-Code-Beispiele.

**Forschungsrelevanz:**
- Erlaubt **lehrplankonforme Aufgaben-Generierung** ohne manuelle Eingabe durch den Lehrer.
- Ermöglicht zu messen: Wie gut passt das LLM Schwierigkeit und Konzepte an den Lehrplan an? Benötigen Lehrer viele manuelle Anpassungen (→ Change-Analysis AP12)?
- Der kuratierte Code-Beispiel-Datensatz ist ein **Qualitätsanker** für die LLM-Generierung.

---

## 3. Aufgabengenerierung — Erweiterungen

### 3.1 Constraint-Based Generation (AP20)

**Was:** Lehrer können bei der Generierung einschränken, welche Konstrukte vorkommen dürfen:

- **Blacklist**: "Nicht verwenden: ternary, lambda, list comprehension"
- **Whitelist**: "Nur verwenden: if/else, for, while, print"
- **difficultySlope**: Steuert den Schwierigkeitssprung zwischen Tiers (sanft → steil)
- **additionalContext**: Freitext direkt in den Prompt injiziert ("Verwende nur deutsche Variablennamen")

**Warum:** Teachers-Feedback `DI_curriculum_mismatch`: LLM generierte manchmal Konstrukte, die Schüler noch nicht kennen. Constraints lösen das direkt im Prompt.

**Forschungsrelevanz:** Korreliert Constraint-Nutzung mit geringerer Teacher-Edit-Rate (AP12)? Welche Constraints verwenden Lehrer am häufigsten?

---

### 3.2 Fünf-Tier-Modus (AP21)

**Was:** Erweiterung von 3 auf 5 Tiers, konfigurierbar pro Worksheet:

- **Tier 1–3**: wie bisher (aufsteigende Komplexität)
- **Tier 4**: Distractor-Heavy — Puzzle mit mehreren eingestreuten Distractor-Blöcken
- **Tier 5**: "Explain-Your-Parsons" — Schüler löst das Puzzle UND erklärt die Lösung (Freitext oder Multiple-Choice)

Zusätzlich: **Fill-in-the-Blanks**-Variante (Blöcke mit Lücken, z.B. `for i in range(___):`)

**Forschungsrelevanz:** Reicht das aktuelle 3-Tier-System für die Bandbreite einer Klasse? Tier 5 kombiniert Parsons-Problemlösung mit Erklärfähigkeit (metakognitive Kompetenz).

---

### 3.3 Verbessertes Concept Detection (AP25)

**Was:** Die v1-Konzepterkennung war ein freier LLM-Call ohne Katalog → inkonsistente Benennung ("for loop" vs. "for-loop" vs. "iteration"). v2 implementiert:

1. **Fester Konzept-Katalog** (~50–80 Konzepte in 8 Kategorien, abgeleitet aus den Thesis-Annotationsrichtlinien A.2.6): Control Flow, Data Structures, Functions, OOP, Error Handling, I/O, String Operations, Advanced
2. **Constrained LLM-Call**: Prompt gibt den Katalog vor, Antwort muss daraus wählen (JSON-Array)
3. **Deterministischer Fallback**: Regex/AST-basierte Erkennung als Backup (Python: `ast.parse()`, Java: Regex, JS/TS: Patterns)
4. **Hybrid-Ansatz**: Union aus deterministischem + LLM-Ergebnis, deterministisch gewinnt bei Konflikten
5. **ConceptNormalizer**: Alias-Tabelle + Fuzzy-Match → "schleife" → "for-loop"

**Forschungsrelevanz:** Löst das "off-taxonomy"-Problem aus RQ6/Anhang C.6 der Thesis. Macht RQ6 (Konzepterkennung) reproduzierbar und vergleichbar. Struktur-Tags fließen direkt in die Bibliothek-Tagging-Pipeline (AP19A).

---

### 3.4 Kontext-Code (AP22)

**Was:** `#context`-Marker für Zeilen, die nicht Teil des Puzzles sind (z.B. Imports, Klassendefinition, Main-Methode). Diese werden ober-/unterhalb des Puzzles als ausgegrautem, nicht-verschiebbarem Code angezeigt.

**Warum:** Beschränkung auf ~15–20 Zeilen entfällt. Schüler sehen reale Programme im Kontext.

---

### 3.5 Manueller Task-Ersteller (AP23)

**Was:** Lehrer können Parsons-Puzzles direkt aus eigenem Code erstellen (kein LLM-Call). Code-Editor mit Marker-Syntax-Highlighting (`#distractor`, `#swap:NAME`, `#group:NAME_N`, `#context`). Live-Preview des Puzzles in Echtzeit.

**Warum:** Manche Lehrer wollen spezifischen Unterrichtscode verwenden. Senkt LLM-Abhängigkeit.

---

### 3.6 Export (AP24)

**Was:** Vollständig client-seitig via `jsPDF`:
- **Puzzle-PDF**: Codezeilen als ausschneidbare Kärtchen mit gestrichelten Linien (für Papier-Parsons)
- **Free-Form-PDF**: Aufgabenstellung + Code in Monospace-Box (für Klausuren)
- **CSV/JSON**: Rohdaten aller Tasks eines Worksheets (für eigene Auswertungen / SPSS / R)

---

## 4. Parsons-Puzzle Erweiterungen

### 4.1 Distractor-System (AP15)

**Was:** LLM-generierte Distractor-Blöcke — plausibel aussehende, aber falsche Codezeilen, die im Puzzle-Pool auftauchen. Taxonomie mit 6 Typen:

| Typ | Beschreibung | Didaktischer Fokus |
|-----|-------------|-------------------|
| `LOGIC_FLIP` | Operatoren vertauscht (`<` → `>`, `==` → `!=`) | Prädikative Logik |
| `OFF_BY_ONE` | Falsche Schleifengrenzen (`range(n)` statt `range(n+1)`) | Grenzwertverständnis |
| `VARIABLE_MISMATCH` | Existierende, aber falsche Variable | Datenfluss-Analyse |
| `BOUNDARY_IDENTITY` | Falsche Initialwerte / Base-Cases | Rekursives Denken |
| `STRUCTURAL_ILLOGICAL` | Kontextillegaler Code (`break` außerhalb Schleife) | Kontrollfluss-Regeln |
| `REDUNDANT_ZOMBIE` | Plausibler, aber überflüssiger Code | Erkennen von Ballast |

**3-Stufen-Pipeline:** (1) Strategischer Plan im Sequence-Prompt (Typ + Rationale), (2) Solver bleibt unverändert, (3) separater "Antagonist-Call" generiert die eigentlichen Distractor-Zeilen.

**Forschungsrelevanz (sehr hoch):**
- Welche Distractor-Typen fangen Schüler am häufigsten?
- Erkennen Schüler Distractors vor oder nach Feedback?
- Korrelation Distractor-Typ ↔ Jahrgangsstufe / Programmiersprache?
- Retention-Rate: Welche KI-generierten Distractors behalten Lehrer unverändert?
- Distractor als Misconception-Diagnose-Werkzeug (Fehlertyp = vermutliches Fehlkonzept)

---

### 4.2 Austauschbare Block-Gruppen (AP16)

**Was:** Zwei neue Marker im Solution-String:
- `#swap:NAME` — einzelne Zeilen frei vertauschbar mit gleichem Namen
- `#group:NAME_N` — mehrzeilige Cluster, die intern zusammenbleiben aber als Cluster frei vertauschbar sind

Beispiel: Zwei unabhängige Base-Cases in einer rekursiven Funktion können in beliebiger Reihenfolge korrekt sein.

Ein LLM-Pass-2 (InterchangeableAnnotator) annotiert generierte Solutions automatisch. Das `parsons.js`-Grading wurde entsprechend erweitert (flexible Positionsnormalisierung vor dem LIS-Algorithmus).

**Forschungsrelevanz:** Löst ein fundamentales Korrektheitsproblem: v1 markierte semantisch korrekte Lösungen als falsch, wenn Schüler kommutative Blöcke vertauschten. Forschungsfrage: Wie häufig kommen austauschbare Blöcke in typischen Schulaufgaben vor?

---

### 4.3 Click-to-Move & Touch-Interaktion (AP17)

**Was:** Alternative zu Drag-and-Drop:
- Click-to-Select (Block visuell hervorheben) + Click-to-Place
- Keyboard-Navigation (Pfeiltasten, Tab)
- Touch-Support (Long-press to select, Tap to place)
- Modus-Toggle (Drag / Click), Präferenz in localStorage

**Forschungsrelevanz:** Schüler-Feedback T4: "Drag-and-Drop auf Trackpads schwierig." Forschungsfrage: Beeinflusst der Interaktionsmodus Lösungszeit und Fehlerrate? (via `session_context`-Event + Interaktionsmuster)

---

### 4.4 Completion & Post-Solution-Review (AP18)

**Was:**
- Explizites "✓ Richtig gelöst!"-Banner bei korrekter Lösung (statt nur grüner Blöcke)
- Optionale Confetti-Animation (vom Lehrer deaktivierbar)
- Lösungsstatistik: Anzahl Versuche, benötigte Zeit, Hints genutzt ja/nein
- "Nächste Aufgabe"-Button für direkten Tier-Übergang
- Self-Report im Completion-Flow integriert (nicht als separate Popup)

---

## 5. Task-Bearbeitung & Versionierung (AP9)

**Was:** Lehrer können Problem-Statement und Code eines generierten Tasks bearbeiten. Beim **ersten** Speichern wird die Originalversion in `task_originals` gesichert und der Lehrer bewertet die LLM-Qualität (1–5 Sterne). Fremde Tasks: erst kopieren, dann bearbeiten.

**Forschungsrelevanz:**
- Vorher/Nachher-Vergleich (→ Change-Analysis AP12)
- LLM-Qualitätsrating als subjektive Baseline
- Welche Felder werden am häufigsten bearbeitet (Statement vs. Code)?

---

## 6. Hint-System (AP13)

**Was:** Gestufte Hilfestellungen bei Parsons-Problemen. Designprinzip nach Florian: **"Möglichst deterministisch — kein LLM-Halluzinationsrisiko."**

**Ablauf:**
1. Schülerlösung aus Block-Reihenfolge zusammenbauen (vollständige Code-Datei)
2. Statische Analyse: **Python → Ruff** (F821 undefined-name, F841 unused-var, E999 syntax-error, F811 redefined) | **Java → javac** (Stufe 1) + **SpotBugs** (Stufe 2, nur wenn kompilierbar)
3. Findings auf Blöcke zurückmappen (Zeilennummern → Block-IDs)
4. Hint generieren + betroffene Blöcke visuell hervorheben (pulsierender roter Rahmen)
5. Optional "Erkläre es genauer": Finding + Code → LLM → schülergerechte Erklärung

**Drei Stufen:**
| Stufe | Name | Quelle |
|-------|------|--------|
| 0 | Standard-Feedback (grün/gelb/rot) | js-parsons Widget (bestehend) |
| 1 | Linter-Hint (deterministisch) | Ruff / javac / SpotBugs |
| 2 | LLM-Erklärung | Finding → LLM (erklärt, diagnostiziert nicht) |

**Credit-System:** Konfigurierbar pro Worksheet. Standard: 5 Credits pro Task, Stufe 1 kostet 1, Stufe 2 kostet 2.

**SARIF** als einheitliches Austauschformat zwischen allen Tools und dem Hint-Generator.

**Forschungsrelevanz (sehr hoch):**
- Wann suchen Schüler Hilfe? Nach wie vielen Fehlversuchen?
- Help-Seeking-Stile: Proaktiv (früh, strategisch) / Reaktiv (spät, verzweifelt) / Vermeidend (nie)
- Hint-Effektivität: Verbessert sich Performance nach Hint? Wie schnell?
- Reicht Stufe 1 oder brauchen Schüler Stufe 2? Bei welchen Aufgabentypen?
- Ist Hint-Anforderung ein besserer Stuckness-Proxy als wiederholte falsche Feedbacks?
- Dependency-Risiko: Nutzen manche Schüler Hints exzessiv?
- Welche Fehlertypen treten am häufigsten auf? (undefined_variable vs. null_safety vs. dead_code)

---

## 7. Learning Analytics — Forschungsdaten-Infrastruktur

Das ist der zentrale Forschungs-Mehrwert von v2. Alle Events landen in der `learning_events`-Tabelle als JSONB (`event_type VARCHAR`, `event_data JSONB`, `user_id UUID nullable`, `task_id UUID`, `timestamp`).

### 7.1 Wave 1 — Kern-Events (AP10)

Grundlegendes Event-Tracking in der SolvePage:

| Event | Inhalt |
|-------|--------|
| `task_opened` | Task-ID, Tier, Timestamp |
| `task_closed` | Lösungsstatus, Zeit auf Task |
| `feedback_requested` | Block-Snapshot vor Feedback, Anzahl korrekte Blöcke, Move-Count |
| `task_correct` | Versuche bis Korrektheit, Gesamtzeit |
| `sequence_rating` | Likert-Skala (1–5) nach Sequenzabschluss |

---

### 7.2 Wave 2 — Erweiterte Metriken (AP11)

| Event / Metrik | Beschreibung |
|----------------|-------------|
| `tier_opened` / `tier_closed` | Tier-Transitionen |
| `task_abandoned` | Abbruch-Erkennung |
| `task_reopened` | Wiederbesuche |
| `task_reset` | Shuffle/Reload |
| Active-Time-Tracking | Inaktivitätsgrenze 90s, unterscheidet Nachdenken von Abwesenheit |
| Backtracking-Erkennung | Korrekte Blöcke wieder verschoben |
| Block-Level Fehlermuster | Welche spezifischen Blöcke oft falsch |
| time_to_first_action | Latenz erster Drag nach Task-Open |

**Ableitbare Kennwerte:** `feedbacks_until_correct`, `moves_until_correct`, `improvement_rate_after_feedback`, `backtracking_count`, `abandonment_rate`, `tier_skip_rate`, `return_to_lower_tier_rate`

---

### 7.3 Advanced Analytics — Paper v2 (AP14)

Tiefe Verhaltensanalyse für Paper v2. Kombiniert Process-Data-Forschung, SRL-Theorie und Parsons-spezifische Muster.

**Neue Event-Typen:**

| Event | Inhalt | Forschungswert |
|-------|--------|---------------|
| `block_moved` | Jede einzelne Drag-and-Drop-Aktion (fromIndex, toIndex, fromZone, toZone, timeSinceLastMove) | Lösungsstrategien identifizieren (top-down, bottom-up, random) |
| `repair_strategy` | Automatische Klassifikation nach jedem Feedback: `local_repair`, `targeted_fix`, `global_reshuffle`, `undo_correct`, `no_change`, `immediate_refeedback` | Korrelation Strategie-Typ ↔ Lernerfolg |
| `stuckness_detected` | `repeat_state`, `oscillation`, `no_progress`, `long_idle`, `rapid_clicking` | Echte Sackgassen vs. produktives Probieren |
| `focus_changed` | Tab-Wechsel (Visibility API), Rückkehr-Dauer | Inaktivität differenzieren: Nachdenken vs. extern Hilfe suchen |
| `solution_snapshot` | Periodischer Zustandsabzug (jeder 5. Move, vor Feedback) | Lösungspfade clustern → Schülertypen ableiten |
| `self_report` | Nach `task_correct` / `task_abandoned` / Sequenz: Confidence, Frustration, Perceived Difficulty (1-Klick Smiley) | Verhaltensmuster mit subjektivem Erleben triangulieren |
| `teacher_edit_*` | `started`, `saved`, `cancelled`, `reverted` mit Dauer + veränderten Feldern | Teacher Adaptation Workflows — nicht nur Ergebnis, sondern Prozess |
| `session_context` | Einmal pro Session: Viewport, isMobile, inputType, userAgent | Kovariate für alle anderen Analysen |
| Feinere Timing-Daten | In bestehende Events: latencyToFirstDrag, meanPauseBetweenMoves, shortBursts/longBursts | Burst-Analyse: schnelles Arbeiten vs. Denk-Pause |

**Beantwortbare Forschungsfragen (Paper v2):**
1. Welche Lösungsstrategien nutzen Novizen bei Parsons-Problemen? Unterscheiden sie sich zwischen Tiers?
2. Korreliert Strategie-Konsistenz mit Lernerfolg?
3. Wann und warum nutzen Schüler Hints vs. Feedback?
4. Gibt es Help-Seeking-Stile? (Proaktiv / Reaktiv / Vermeidend)
5. Wie häufig geraten Schüler in Sackgassen? Welche Typen führen zu Abbruch?
6. Ist Stuckness ein besserer Schwierigkeitsindikator als Wrong-Rate allein?
7. Frustration als Prädiktor für Abbruch?
8. Wie lange brauchen Lehrer für Anpassungen? Korreliert mit Change-Score?

---

### 7.4 Distractor-Analytics (AP15 Teil 5)

Erweitert alle bestehenden Events um Distractor-Felder (`distractorCountInWorkspace`, `activeDistractorIds`, `distractorTaxonomyTypes`, `isDistractorOnlyError`).

Neue Events: `distractor_placed`, `distractor_removed` (mit `distractorType`, `wasInSolutionMs`, `removedBeforeFeedback`, `removedAfterHint`).

Neue Stuckness-Typen: `distractor_loop` (Schüler oszilliert, in beiden Zuständen liegt ein Distractor im Workspace), `trap_blindness` (Schüler ändert 3× die Reihenfolge korrekter Blöcke, lässt den Distractor aber unberührt).

**Beantwortbare Forschungsfragen:**
- Welche Taxonomie-Typen fangen Schüler am häufigsten?
- Erkennen Schüler Distractors vor oder nach Feedback?
- Sequence Distractor im Workspace → Hint → Distractor entfernt = "Erfolgreiche Fehlkonzeptionskorrektur"
- Individuelles Misconception-Profil pro Schüler (userId + distractorTaxonomyTypes)

---

### 7.5 Change-Analysis-Engine (AP12)

**Was:** Vollautomatische Analyse der Lehreredits (Original vs. Bearbeitet) auf drei Ebenen:

| Ebene | Metriken |
|-------|----------|
| Text (Aufgabentext) | Levenshtein-Ratio, Jaccard-Similarity |
| Syntaktisch (Code) | Levenshtein-Ratio, Lines Added/Removed/Modified/Reordered, LOC-Delta, Token-Change-Ratio |
| Strukturell (AST) | Nodes Added/Removed/Modified, Structural Score, Change Types (JSONB) |

**Composite Score** (gewichtete Summe): `0.25 × Text + 0.35 × Code-syntaktisch + 0.40 × Code-strukturell`

**Change-Kategorien:** `NONE`, `COSMETIC`, `MINOR`, `MAJOR`, `REWRITE` (>70% geändert)

Ergebnisse in `task_change_analyses`. Export via `GET /api/analysis/export` (CSV/JSON für SPSS/R). Lehrer-Rating (1–5) als subjektive Referenz.

**Beantwortbare Forschungsfragen:**
1. Stimmt das Lehrer-Rating mit dem automatischen Change-Score überein?
2. Werden primär Aufgabentexte oder Code geändert?
3. Werden Tier-1-Tasks mehr oder weniger bearbeitet als Tier-3?
4. Werden Java-Tasks mehr bearbeitet als Python-Tasks?
5. Wie viel % der Änderungen sind echte Logik-Änderungen (vs. kosmetisch)?
6. Wie oft werden KI-generierte Distractors vom Lehrer geändert? (Retention-Rate)
7. Werden neuere Generierungen (bessere Modelle) weniger bearbeitet?

---

### 7.6 Multi-Language Code Analytics — tree-sitter (AP26)

**Was:** Komplexitätsmetriken (LOC, Cyclomatic Complexity, Max. Nesting Depth, Method-Call Count, Distinct Identifier Count) und AST-basierte Change-Analysis für alle 9 unterstützten Sprachen (Java, Python, JavaScript, TypeScript, C, C++, C#, Rust, Go) via tree-sitter als universellen Parser.

Clean-Architecture-Konformität: `CodeAnalyzer`-Interface in der Domain-Schicht, `TreeSitterCodeAnalyzer` in Infrastructure. Application-Code kennt kein tree-sitter.

v1-Problem: Komplexitätsmetriken gaben für alle Nicht-Java/Python-Sprachen Nullwerte zurück → RQ1-Auswertung für diese Sprachen war unmöglich.

---

## 8. Feedback-Logs mit User-Zuordnung (AP5)

**Was:** `feedback_logs`-Tabelle erweitert um `user_id UUID DEFAULT NULL`. Ermöglicht Fortschritts-Tracking pro Schüler pro Task. Anonyme Einträge (user_id = NULL) bleiben möglich.

---

## Zusammenfassung: Forschungsrelevante Datenpunkte

| Forschungsbereich | Datenquelle |
|------------------|-------------|
| Lösungsstrategien | `block_moved`, `repair_strategy` (AP14) |
| Help-Seeking | `hint_requested`, `hint_explanation_requested`, `hint_viewed`, `post_hint_action` (AP13/14) |
| Stuckness | `stuckness_detected` (AP14) |
| Lernfortschritt | `feedback_requested`, `task_correct`, `task_abandoned` (AP10/11) |
| Misconceptions | `distractor_placed`, `distractor_removed`, `distractorTaxonomyTypes` (AP15) |
| LLM-Qualität | `task_originals`, Lehrer-Rating (AP9), Change-Analysis (AP12) |
| Teacher Adaptation | `teacher_edit_*`, Change-Analysis Export CSV/JSON (AP12/14) |
| Curriculum-Alignment | CurriculumPage-Nutzung, concept_curriculum_topic (AP19B) |
| Concept Detection | sequence_tags, ConceptCatalog (AP25) |
| Technische Kovariate | `session_context` (AP14), `feedback_logs.user_id` (AP5) |

---

## Tech-Stack-Überblick

| Schicht | Technologie |
|---------|-------------|
| Backend | Spring Boot 3, Java 21 (Virtual Threads), PostgreSQL 15 |
| Auth | Spring Security, JWT (HS256), Argon2 |
| KI | OpenAI API (GPT-4.1), PromptBuilder-Patterns, SARIF |
| Frontend | React 18, TypeScript, Vite, react-i18next, Bootstrap 5 |
| Parsons | js-parsons (fork, erweitert um #swap/#group/#distractor/#context, Click-to-Move) |
| Code-Analyse | tree-sitter (9 Sprachen), Ruff (Python Linter), javac + SpotBugs (Java) |
| Export | jsPDF (client-seitig) |
| Deployment | Docker Compose |

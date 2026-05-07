# Forschungsfragen — Distractor-System (AP 15, Teil 6)

Dieses Dokument beschreibt die durch das Distractor-System beantwortbaren Forschungsfragen.
Alle Fragen setzen die Datenerhebung aus AP 15 Teil 5 (Distractor-Analytics) voraus.

---

## 1. Distractor-Design

### RQ1: Welche Taxonomie-Typen fangen Schueler am haeufigsten?
- **Datenquelle:** `distractor_placed` Events → `distractorType` Feld
- **Analyse:** Haeufigkeitsverteilung ueber die 6 Taxonomie-Typen (LOGIC_FLIP, OFF_BY_ONE, VARIABLE_MISMATCH, BOUNDARY_IDENTITY, STRUCTURAL_ILLOGICAL, REDUNDANT_ZOMBIE)
- **Erwartung:** LOGIC_FLIP und BOUNDARY_IDENTITY duerfte am haeufigsten verfangen, da Operatorverstaendnis und Initialwerte typische Anfaengerfehler sind (Clancy 2004, Sirkia & Sorva 2012)

### RQ2: Korreliert die Distractor-Fallrate mit dem Programmierkonzept?
- **Datenquelle:** `distractor_placed` Events + Task-Metadata (Konzepte aus Sequenz)
- **Analyse:** Kreuztabelle Taxonomie-Typ x Programmierkonzept (Schleifen, Rekursion, Bedingungen, ...)
- **Erwartung:** OFF_BY_ONE korreliert stark mit Schleifenkonzepten, BOUNDARY_IDENTITY mit Rekursion

### RQ3: Erkennen Schueler Distraktoren vor oder nach Feedback?
- **Datenquelle:** `distractor_removed` Events → `removedBeforeFeedback` Boolean
- **Analyse:** Anteil der Distraktoren die ohne Feedback-Nutzung erkannt werden vs. erst nach Feedback
- **Erwartung:** Tier 1 Distraktoren werden haeufiger vor Feedback erkannt, Tier 3 erst danach

### RQ4: Wie lange bleiben Distraktoren in der Loesung?
- **Datenquelle:** `distractor_removed` Events → `wasInSolutionMs`
- **Analyse:** Verweildauer-Verteilung, Median und Ausreisser
- **Erwartung:** Subtile Distraktoren (VARIABLE_MISMATCH, BOUNDARY_IDENTITY) haben laengere Verweildauer

### RQ5: Korrelation Distractor-Nutzung und Tier-Level
- **Datenquelle:** `distractor_placed` Events + `task.tierLevel`
- **Analyse:** Fallrate pro Tier-Stufe
- **Erwartung:** Hoehere Tiers haben hoehere Fallraten, da Distraktoren dort subtiler sind

---

## 2. Hint-Interaktion

### RQ6: Helfen Hints beim Erkennen von Distraktoren?
- **Datenquelle:** `post_hint_action` Events nach einem Distractor-bezogenen Hint
- **Analyse:** Anteil der Faelle in denen nach Hint der Distraktor entfernt wurde
- **Erwartung:** Level-2 Hints (mit LLM-Erklaerung) fuehren haeufiger zur Entfernung als Level-1

### RQ7: Fuehrt ein Distractor-Hint schneller zur Loesung als ein Einrueckungs-Hint?
- **Datenquelle:** Hint-Efficacy Metriken (Zeit von Hint bis korrekte Loesung)
- **Analyse:** Vergleich Distractor-Hints vs. Order/Indentation-Hints hinsichtlich Time-to-Solve
- **Erwartung:** Distractor-Hints haben staerkeren Effekt, da das Entfernen eines Blocks einfacher ist als Umordnen

### RQ8: Erfolgreiche Fehlkonzeptions-Korrektur Sequenz
- **Datenquelle:** Event-Sequenz: `distractor_placed` → `hint_requested` (Distractor-Hint) → `distractor_removed`
- **Analyse:** Haeufigkeit dieser vollstaendigen Korrektur-Sequenz
- **Erwartung:** Diese Sequenz ist ein starker Indikator fuer erfolgreiches Scaffold-basiertes Lernen

---

## 3. Schueler-Profil

### RQ9: Individuelles Misconception-Profil
- **Datenquelle:** `distractor_placed` Events gruppiert nach `userId` + `distractorType`
- **Analyse:** Pro Schueler ein Vektor der Anfaelligkeiten fuer jeden Taxonomie-Typ
- **Anwendung:** Adaptive Distractor-Auswahl in zukuenftigen Aufgaben (personalisiertes Lernen)

### RQ10: Distractor Attraction Score nach Altersgruppe/Erfahrungslevel
- **Datenquelle:** `distractor_placed` Events + Klassen-Metadata (Altersgruppe/Schulstufe)
- **Analyse:** Welche Fehlertypen sind fuer welche Altersgruppe am "verfuehrerischsten"?
- **Erwartung:** Juengere Schueler fallen eher auf STRUCTURAL_ILLOGICAL herein, aeltere auf VARIABLE_MISMATCH

---

## 4. Generative Qualitaet

### RQ11: Lehrer-Akzeptanzrate der KI-generierten Distraktoren
- **Datenquelle:** `task_change_analyses` → `ast_distractor_changes` (Retention-Rate)
- **Analyse:** Anteil der KI-generierten Distraktoren die vom Lehrer beibehalten werden
- **Erwartung:** >70% Retention bei gut kalibriertem Antagonist-Prompt

### RQ12: Korrelation zwischen Taxonomie-Typ und Lehrer-Akzeptanz
- **Datenquelle:** `task_change_analyses` + `distractor_plan.type`
- **Analyse:** Akzeptanzrate pro Taxonomie-Typ
- **Erwartung:** LOGIC_FLIP und OFF_BY_ONE haben hohe Akzeptanz, REDUNDANT_ZOMBIE geringere

---

## 5. Stuckness

### RQ13: Sind Distraktoren die Hauptursache fuer Stuckness?
- **Datenquelle:** `stuckness_detected` Events → `stucknessType` (neu: `distractor_loop`, `trap_blindness`)
- **Analyse:** Anteil der Stuckness-Events die distractor-bezogen sind vs. order/indentation-bezogen
- **Erwartung:** Bei Tasks mit Distraktoren sind ca. 30-50% der Stuckness-Events distractor-bezogen

### RQ14: Unterschied der Stuck-Dauer mit vs. ohne Distraktoren
- **Datenquelle:** `stuckness_detected` Events → `stuckDurationMs`, verglichen zwischen Tasks mit und ohne Distraktoren
- **Analyse:** Vergleich der medianen Stuck-Dauer
- **Erwartung:** Tasks mit Distraktoren haben laengere Stuck-Phasen, aber nach Hint-Nutzung kuerzere Recovery

---

## Voraussetzungen

| Voraussetzung | Status |
|---|---|
| AP 15 Teil 1-4 (Taxonomie, Pipeline, Infrastruktur, Hints) | Implementiert |
| AP 15 Teil 5 (Analytics Events: `distractor_placed`, `distractor_removed`, erweiterte Stuckness) | Implementierung ausstehend |
| Echte Schueler-Nutzungsdaten (Studie) | Ausstehend |
| Datenexport/Auswertungsskripte | Ausstehend |

# Architektur-Dokument: Evidenzbasierte Distraktor-Generierung in CodeOwl

## 1. Einleitung
Dieses Dokument beschreibt die theoretische und technische Fundierung der automatisierten Distraktor-Generierung innerhalb der CodeOwl-Plattform. Die Architektur folgt dem Prinzip der "Separation of Concerns", indem sie die Planung der pädagogischen Falle von der technischen Lösungstrennung entkoppelt.

## 2. Die CodeOwl-Taxonomie (basierend auf Fehlermustern)
Um die Qualität der Distraktoren sicherzustellen, nutzt CodeOwl eine hybride Taxonomie, die auf etablierten Forschungsarbeiten zur Informatikdidaktik basiert:

| Kategorie | Wissenschaftliche Referenz | Pädagogischer Fokus |
| :--- | :--- | :--- |
| **Logic Flip** | Clancy (2004) | Prüfung der prädikativen Logik (Inversion von Bedingungen). |
| **Off-by-One** | Dijkstra / Knuth | Verständnis von Grenzwerten und Iterationsintervallen. |
| **Variable Mismatch** | Sleeman (1986) | Analyse des Datenflusses und der Variablenbindung ("Mal-rules"). |
| **Boundary/Identity** | Sirkia & Sorva (2012) | Verständnis von Initialisierungs- und Identitätselementen. |
| **Structural Illogical** | Pea (1986) | Aufdecken von "Superbugs" (falsche Annahmen über den Kontrollfluss). |

## 3. Begründung der Architektur (Separated Pipeline)

### 3.1 Pädagogische Kohärenz (Top-Down Planung)
Durch die Integration des `distractorPlan` in Schritt 1 (Sequence Generation) wird sichergestellt, dass die Schwierigkeit der Distraktoren mit der Komplexität der Aufgabe skaliert. Gemäß der **Cognitive Load Theory (Sweller, 1988)** dürfen Distraktoren die kognitive Last nicht wahllos erhöhen, sondern müssen gezielt "Germane Load" (lernbezogene Last) erzeugen.

### 3.2 Modulstabilität & Inferenz-Integrität
Die Trennung von Solver und Antagonist verhindert "Model Drift". Ein LLM, das gleichzeitig eine korrekte Lösung und falsche Alternativen generieren soll, neigt dazu, die Qualität der Lösung zu kompromittieren oder zu einfache/unsinnige Distraktoren (z.B. reine Syntaxfehler) zu erstellen. Der separate Antagonist-Call ermöglicht:
- **Präzision:** Fokus auf semantische statt rein syntaktische Fehler.
- **Validierung:** Einfacher Abgleich, dass Distraktoren != Lösungsteile sind.
- **Flexibilität:** Lehrer-gesteuerte Aktivierung/Deaktivierung ohne Neu-Generierung der Aufgabe.

## 4. Wissenschaftliche Referenzliste
- **Clancy, M. J. (2004).** *Misconceptions and attitudes.* In: Computer Science Education Research.
- **Denny, P., Luxton-Reilly, A., & Simon, B. (2008).** *Evaluating a new type of automated assessment question.* (Forschung zu Parsons Problems).
- **Ericson, B. J., et al. (2017).** *Solving Parsons Problems: It's Not as Easy as It Looks.*
- **Pea, R. D. (1986).** *Language-independent conceptual "bugs" in novice programming.*
- **Sleeman, D., et al. (1986).** *Diagnosis and remediation of students' misconceptions in BASIC.*
- **Sorva, J., Karavirta, V., & Malmi, L. (2013).** *A Review of Generic Program Visualization Systems for Education.*
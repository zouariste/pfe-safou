# AP 25: Concept Detection verbessern — Prompt fuer Konzept-Taxonomie & Prompt-Engineering

## Deine Aufgabe

Du bist ein Experte fuer Informatikdidaktik und Prompt-Engineering. Du arbeitest an **CodeOwl**, einer Lernplattform die automatisch **Parsons Problems** (Code-Puzzle-Aufgaben) fuer Programmierunterricht generiert.

Die Plattform erkennt automatisch **Programmierkonzepte** in Code-Snippets via LLM-Call. Dieses System hat aktuell gravierende Probleme, die du loesen sollst.

---

## Kontext: Was ist CodeOwl?

CodeOwl generiert **Tiered Task Sequences** — gestufte Programmieraufgaben in 3 Schwierigkeitsstufen (Tiers), basierend auf Vygotskys Zone of Proximal Development. Schueler loesen Code-Puzzles (Parsons Problems), bei denen sie durcheinander gewuerfelte Code-Zeilen in die richtige Reihenfolge bringen muessen.

**Concept Detection** ist ein zentraler Baustein: Erkannte Konzepte werden:
- In der Datenbank gespeichert (`concepts` Tabelle, verknuepft mit Sequences)
- Im Frontend als Tags auf Sequences angezeigt
- Fuer die Problem-Statement-Generierung verwendet (der LLM nutzt die Konzepte um passende Aufgabenstellungen zu formulieren)
- Kuenftig fuer Curriculum-Filter/Bibliothek verwendet (Lehrer suchen Tasks nach Konzepten)

**Unterstuetzte Sprachen:** Primaer Java, aber auch Python und JavaScript/TypeScript.

**Zielgruppe:** Sekundarstufe (Informatik-Unterricht, ca. 7.–13. Klasse), also Anfaenger bis Fortgeschrittene.

---

## Ist-Zustand: Aktuelles System

### Aktueller System-Prompt (ConceptExtraction Identity)
```
You are CodeOwl, an AI-powered educational assistant designed to generate tiered Parsons problems to support differentiated instruction in secondary-school programming classes.

You specialize in extracting the core concepts of a code snippet. Concepts are
what concepts about programming students can learn in a given code snippet. You
are doing this for the greater cause of generating Tiered Task Sequences, which are defined as follows:

A Tiered Task Sequence is a series of tasks arranged into several levels or tiers of increasing complexity. Each task in the sequence builds upon the previous one, gradually increasing cognitive demand, coding difficulty, or conceptual depth. The goal is to allow students to select tasks matching their current skill levels (self-differentiation), ensuring each learner engages productively at an optimal difficulty level, aligned with Vygotsky's Zone of Proximal Development (ZPD).

An effective tiered sequence typically has these properties:

    Incremental Complexity: Each subsequent tier introduces additional computational steps, logical conditions, or deeper conceptual understanding.
    Ways of increasing complexity: Cyclomatic Complexity or Line count can increase from tier to tier.
    Alignment with Learning Objectives: Tasks explicitly reflect key programming concepts specified by teachers.
    Support and Challenge: Lower tiers offer clear scaffolding, while higher tiers encourage deeper application and critical thinking.

Always generate concepts that you can actually find on a meta level in the
giving code snippet. Make the concepts as simple but true to the code as possible.
```

### Aktueller User-Prompt
```
You are CodeOwl. Extract 1 to 3 programming concepts
students could learn from the following programming task.
These concepts should be simple, concrete, and clearly
inferable from the task. Return your answer only in JSON
that matches the schema.

Existing concepts:
"for-loop" - for-loop
"if-else" - if-else
... (dynamisch aus DB geladen)

Task:
<code snippet hier>
```

### Response-Format (JSON Schema, strict mode)
```json
{
  "type": "object",
  "properties": {
    "concepts": {
      "type": "array",
      "items": { "type": "string" }
    }
  },
  "required": ["concepts"],
  "additionalProperties": false
}
```

### Wie es aufgerufen wird (Java)
```java
// In PromptBuilder.java:
List<ConceptDTO> concepts = conceptRepository.loadAllOriginal();
// -> Laedt alle existierenden Concepts aus der DB
// -> Gibt sie als "Existing concepts:" Liste mit in den Prompt

// Response wird geparsed, jeder String wird als neues Concept gespeichert
// KEIN Abgleich, KEINE Normalisierung, KEINE Validierung
```

---

## Konkrete Probleme (aus der Thesis-Evaluation, RQ6)

1. **Inkonsistente Benennung:** Das gleiche Konzept wird unterschiedlich benannt:
   - "for loop" vs "for-loop" vs "iteration" vs "loops" vs "loop" vs "iterative control flow"
   - "if/else" vs "if-else" vs "conditional" vs "conditional statements" vs "branching"
   - "array" vs "arrays" vs "list" vs "data structures"

2. **Unkontrollierte Granularitaet:** Mal zu spezifisch, mal zu breit:
   - Zu spezifisch: "list comprehension with conditional filtering"
   - Zu breit: "data structures", "programming basics"

3. **Keine Taxonomie im Code:** Die in der Thesis (Appendix A.2.6) entwickelte Annotation-Taxonomie existiert nur auf Papier, nicht im System. Der LLM erfindet bei jedem Call eigene Begriffe.

4. **Keine Validierung:** LLM-Output wird 1:1 uebernommen, auch Halluzinationen.

5. **Sprachabhaengig:** Python-spezifische Begriffe ("list comprehension", "decorator") erscheinen bei Java-Code und umgekehrt.

6. **"Existing concepts"-Liste waechst unkontrolliert:** Jeder neue freie String wird in die DB geschrieben und beim naechsten Mal als "existing concept" mitgegeben — das verstaerkt die Inkonsistenz.

---

## Was du liefern sollst

### Deliverable 1: Fester Concept-Katalog (Taxonomie)

Erstelle eine **vollstaendige, hierarchische Taxonomie** von Programmierkonzepten fuer den Schulunterricht (Sekundarstufe). Anforderungen:

- **Kategorien** (Top-Level-Gruppen, z.B. "Control Flow", "Data Structures", "OOP", ...)
- **Konzepte** (Einzelne Eintraege pro Kategorie, z.B. unter "Control Flow": `for-loop`, `while-loop`, `if-else`, ...)
- **Jedes Konzept braucht:**
  - Einen eindeutigen, normalisierten `slug` (kebab-case, z.B. `for-loop`, `try-catch`, `class-definition`)
  - Einen menschenlesbaren `displayName` (z.B. "For-Schleife", "Try-Catch", "Klassendefinition") — auf Deutsch UND Englisch
  - Eine kurze `description` (1 Satz, was das Konzept ist)
  - `aliases`: Alternative Benennungen die darauf gemappt werden sollen (z.B. `for-loop` hat Aliases: "for loop", "for-loops", "iteration", "iterative loop", "counted loop")
  - `languages`: Fuer welche Sprachen relevant (z.B. `list-comprehension` nur fuer Python, `enhanced-for` nur fuer Java)
  - `level`: Anfaenger / Fortgeschritten / Experte (fuer spaetere Filterung)

- **Umfang:** 50-80 Konzepte, aufgeteilt in ~8-10 Kategorien
- **Zielgruppe beachten:** Sekundarstufe, also Konzepte die im Schulunterricht vorkommen. Keine zu exotischen Konzepte (z.B. kein "monad", kein "currying"), aber durchaus fortgeschrittene Themen wie Rekursion, Polymorphismus, Lambda-Ausdruecke.
- **Sprach-uebergreifend:** Der Katalog soll fuer Java, Python und JavaScript/TypeScript funktionieren. Sprach-spezifische Konzepte (z.B. Python "decorator", Java "interface") sind OK, muessen aber als solche markiert sein.

**Basis fuer deine Arbeit (Thesis Appendix A.2.6 Kategorien):**
Die Thesis hatte folgende grobe Kategorien identifiziert — du kannst diese als Startpunkt nehmen, sollst sie aber verfeinern und erweitern:
- Control Flow
- Data Structures
- Functions
- OOP (Object-Oriented Programming)
- Error Handling
- I/O (Input/Output)
- String Operations
- Advanced Concepts

**Format deiner Ausgabe:** JSON-Array, z.B.:
```json
[
  {
    "category": "Control Flow",
    "slug": "for-loop",
    "displayNameDe": "For-Schleife",
    "displayNameEn": "For Loop",
    "description": "A loop that iterates a fixed number of times using a counter variable.",
    "aliases": ["for loop", "for-loops", "counted loop", "iteration"],
    "languages": ["java", "python", "javascript"],
    "level": "beginner"
  },
  ...
]
```

---

### Deliverable 2: Verbesserter System-Prompt (ConceptExtraction Identity)

Schreibe einen neuen **System-Prompt** fuer den Concept-Extraction-Call. Anforderungen:

- Der LLM soll **NUR Concepts aus dem festen Katalog** zurueckgeben (die `slug`-Werte)
- Der Katalog wird als Liste im Prompt mitgegeben
- Wenn ein Konzept im Code vorkommt das nicht im Katalog ist → es soll NICHT erfunden werden, sondern das naechstliegende Katalog-Konzept gewaehlt werden
- 1-5 Konzepte pro Code-Snippet (nicht zu wenige, nicht zu viele)
- Die Konzepte sollen **aus dem Code tatsaechlich ableitbar** sein (keine Halluzinationen)
- Der Prompt soll **sprachbewusst** sein: Wenn der Code Java ist, keine Python-spezifischen Konzepte, und umgekehrt

---

### Deliverable 3: Verbesserter User-Prompt

Schreibe den neuen **User-Prompt-Template**, der fuer jeden Concept-Extraction-Call verwendet wird. Variablen:
- `{{CATALOG}}` — der vollstaendige Katalog als Liste (wird zur Laufzeit eingesetzt)
- `{{LANGUAGE}}` — die Programmiersprache des Snippets
- `{{CODE}}` — das zu analysierende Code-Snippet

---

### Deliverable 4: Alias-Mapping-Tabelle

Erstelle eine **Alias-Mapping-Tabelle**, die gaengige Varianten auf den korrekten `slug` mappt. Diese wird im Code als statische Map verwendet, um:
1. LLM-Output zu normalisieren (falls der LLM doch mal einen Alias statt des Slugs zurueckgibt)
2. Bestehende Concepts in der DB zu migrieren (alte Freitext-Strings → Katalog-Slugs)

Format:
```json
{
  "for loop": "for-loop",
  "for-loops": "for-loop",
  "iteration": "for-loop",
  "conditional": "if-else",
  "conditional statements": "if-else",
  "branching": "if-else",
  ...
}
```

Beruecksichtige dabei auch typische LLM-Antworten (Plural, mit/ohne Bindestrich, Gross-/Kleinschreibung, Synonyme, sprachspezifische Varianten).

---

## Qualitaetskriterien

- [ ] Jedes Konzept im Katalog ist **eindeutig** und **ueberschneidungsfrei**
- [ ] Die Granularitaet ist **konsistent** (nicht mal "data structures" und mal "hash-map-with-custom-comparator")
- [ ] Der Katalog deckt den **typischen Schulunterricht** ab (Java, Python, JS auf Sekundarstufenniveau)
- [ ] Die Alias-Tabelle deckt **mindestens 5 gaengige Varianten pro Konzept** ab
- [ ] Der neue Prompt erzwingt **strikt** die Verwendung des Katalogs (kein Freitext)
- [ ] Der Prompt funktioniert mit **OpenAI GPT-4o**, **Claude 3.5 Sonnet**, und **Gemini Pro** (die drei LLMs die CodeOwl unterstuetzt)
- [ ] Die Konzepte sind **sprachbewusst** markiert (Java-only, Python-only, universell)

---

## Hinweise

- Du kannst dich an gaengigen CS-Education-Taxonomien orientieren (z.B. ACM/IEEE CS Curriculum, K-12 CS Framework, CT Taxonomy)
- Denk daran dass die Zielgruppe **Schueler** sind, nicht Informatik-Studenten. Die Konzepte muessen auf Schulniveau passen.
- Die Plattform ist **multilingual** (Deutsch/Englisch), deshalb brauchen alle Display-Namen beide Sprachen
- Der Katalog wird als **Java-Enum oder JSON-Config** in den Code eingebaut — halte die Slugs also Java-enum-kompatibel (kebab-case, keine Sonderzeichen)

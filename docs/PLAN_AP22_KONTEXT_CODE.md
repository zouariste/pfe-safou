# Implementierungsplan: AP 22 — Kontext-Code (Beta)

## Ueberblick

**Ziel:** Aufgaben koennen weit ueber 20 LOC haben, wobei nur ein Teil als Parsons-Puzzle geloest wird und der Rest als unveraenderlicher Kontext angezeigt wird. Puzzle-Anteil bleibt ≤ 20 Zeilen.

**Modus:** Aktivierbar (Beta). Bricht nichts an bestehendem 3-Tier-System.

**Marker:** `#context` — analog zu `#distractor`, nativ in parsons.js verarbeitet.

---

## Architektur

```
Generierung/Edit                parsons.js                   SolvePage.tsx
┌──────────────┐          ┌─────────────────────┐       ┌───────────────────┐
│ LLM markiert │          │ parseCode()         │       │ Kontext-Blöcke    │
│ #context     │──init──> │  ├─ contextBefore[] │──────>│ ┌──────────────┐  │
│ Zeilen       │          │  ├─ solution[]      │       │ │ import math  │  │
│              │          │  ├─ distractors[]   │       │ │ class Calc:  │  │
│ ODER         │          │  └─ contextAfter[]  │       │ └──(ausgegraut)┘  │
│ Lehrer setzt │          │                     │       │ ┌──────────────┐  │
│ manuell im   │          │ Context-Zeilen      │       │ │  PUZZLE      │  │
│ Edit-Modal   │          │ NICHT in             │       │ │  (sortierbar)│  │
│              │          │ modified_lines       │       │ └──────────────┘  │
└──────────────┘          │ → Grading ignoriert  │       │ ┌──────────────┐  │
                          └─────────────────────┘       │ │ if __name__  │  │
                                                        │ │ ...          │  │
                                                        │ └──(ausgegraut)┘  │
                                                        └───────────────────┘
```

---

## Teil 1: parsons.js — `#context` Marker

**Datei:** `frontend/public/jsparsons/parsons.js`

### 1.1 ParsonsCodeline: Neues Flag

```javascript
// Im Constructor (nach distractor-Flag):
this.isContext = false;

// Marker-Erkennung (nach #distractor, vor Trim):
this.isContext = codestring.search(/#context\s*$/) >= 0;

// Marker strippen:
this.code = codestring
    .replace(/#group:\S+\s*$/, "")
    .replace(/#swap:\S+\s*$/, "")
    .replace(/#distractor\s*$/, "")
    .replace(/#context\s*$/, "")      // NEU
    .replace(trimRegexp, "$1")
    .replace(/\\n/g, "\n");
```

### 1.2 parseCode(): Context-Separation

Kontext-Zeilen werden in separate Arrays sortiert statt in die Puzzle-Listen:

```javascript
parseCode: function(lines, max_distractors) {
    var distractors = [], indented = [], widgetData = [];
    var contextBefore = [], contextAfter = [];  // NEU
    var puzzleStarted = false;

    $.each(lines, function(index, item) {
        var lineObject = new ParsonsCodeline(item, that);
        lineObject.orig = index;

        if (item.search(/#context\s*$/) >= 0) {
            // Context-Zeile → NICHT ins Puzzle
            if (!puzzleStarted) {
                contextBefore.push(lineObject);
            } else {
                contextAfter.push(lineObject);
            }
            return; // skip
        }

        puzzleStarted = true;
        // ... bestehende distractor/solution Logik unverändert ...
    });

    return {
        solution: ...,
        distractors: ...,
        widgetInitial: ...,
        contextBefore: $.extend(true, [], contextBefore),   // NEU
        contextAfter: $.extend(true, [], contextAfter),     // NEU
        errors: ...
    };
}
```

### 1.3 init(): Context speichern

```javascript
init: function(text) {
    var initial = this.parseCode(text.split("\n"), this.options.max_wrong_lines);

    this.model_solution = initial.solution;
    this.extra_lines = initial.distractors;
    this.modified_lines = initial.widgetInitial;
    this.contextBefore = initial.contextBefore || [];  // NEU
    this.contextAfter = initial.contextAfter || [];    // NEU

    // ... restliche ID-Vergabe etc. unverändert ...
};
```

**Grading:** Keine Aenderung noetig — Context-Zeilen sind nicht in `modified_lines`, werden vom LIS-Grader komplett ignoriert.

---

## Teil 2: Frontend — SolvePage.tsx

### 2.1 Kontext-Rendering

Nach `widget.init()` + `widget.shuffleLines()` werden die Context-Bloecke als read-only `<pre>` gerendert:

```tsx
// Nach widget init:
const contextBefore = widgetRef.current?.contextBefore ?? [];
const contextAfter = widgetRef.current?.contextAfter ?? [];

// Refs fuer DOM-Injection:
const contextBeforeRef = useRef<HTMLDivElement>(null);
const contextAfterRef = useRef<HTMLDivElement>(null);

useEffect(() => {
    // ... widget init ...

    // Context-Bloecke rendern
    if (contextBeforeRef.current && widget.contextBefore?.length > 0) {
        contextBeforeRef.current.innerHTML = widget.contextBefore
            .map(l => escapeHtml(l.code)).join("\n");
        contextBeforeRef.current.style.display = "block";
    }
    if (contextAfterRef.current && widget.contextAfter?.length > 0) {
        contextAfterRef.current.innerHTML = widget.contextAfter
            .map(l => escapeHtml(l.code)).join("\n");
        contextAfterRef.current.style.display = "block";
    }
}, [task?.solution]);
```

### 2.2 Layout

Der Target-Bereich wird erweitert:

```
┌──────────────────────────────────────────────┐
│  Source (Bloecke zum Ziehen)                 │
│  ┌────────────────────────────────────────┐  │
│  │ result = a + b                         │  │
│  │ self.history.append(result)            │  │
│  │ return result                          │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│  ┌─ CONTEXT (read-only, ausgegraut) ──────┐  │
│  │ import math                            │  │
│  │ class Calculator:                      │  │
│  │     def __init__(self):                │  │
│  │         self.history = []              │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌─ PUZZLE (sortierbar) ──────────────────┐  │
│  │  Construct your solution here          │  │
│  │  [jsparsons-target]                    │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌─ CONTEXT (read-only, ausgegraut) ──────┐  │
│  │ if __name__ == "__main__":             │  │
│  │     calc = Calculator()                │  │
│  │     print(calc.add(3, 4))              │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

### 2.3 CSS

```css
.context-block {
    background: #f0f0f0;
    border: 1px dashed #ccc;
    border-radius: 6px;
    padding: 0.75rem 1rem;
    font-family: monospace;
    font-size: 0.85rem;
    color: #666;
    white-space: pre;
    opacity: 0.7;
    pointer-events: none;   /* Nicht klickbar/selektierbar */
    user-select: none;
}

.context-block::before {
    content: "Context (read-only)";
    display: block;
    font-size: 0.7rem;
    color: #999;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 0.25rem;
    font-family: sans-serif;
}
```

### 2.4 Kontext-Info-Banner

Analog zum Distractor-Banner, nur wenn Context vorhanden:

```tsx
{hasContext && (
    <div className="alert alert-info d-flex align-items-center gap-2 mb-3 py-2">
        <span>{t("solve.contextBanner")}</span>
    </div>
)}
```

**i18n:**
- EN: `"The grey code blocks above and below are context — they are already in place. Arrange only the highlighted blocks."`
- DE: `"Die grauen Code-Bloecke oben und unten sind Kontext — sie stehen bereits fest. Ordne nur die hervorgehobenen Bloecke."`

---

## Teil 3: Backend — Generierung

### 3.1 Neues DB-Feld

```sql
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS context_code TEXT DEFAULT NULL;
```

`context_code` speichert die Kontext-Zeilen (jede mit `#context` Suffix), getrennt von der Puzzle-Solution.

**Alternative:** Kontext-Zeilen direkt in `solution` einbetten (mit `#context` Marker). Das ist einfacher und konsistent mit `#distractor`/`#swap`/`#group`.

**Entscheidung: Eingebettet in `solution`.** Kein neues DB-Feld noetig. Die solution enthaelt dann:

```
import math #context
class Calculator: #context
    def __init__(self): #context
        self.history = [] #context
    def add(self, a, b):
        result = a + b
        self.history.append(result)
        return result
    def get_history(self): #context
        return self.history #context
if __name__ == "__main__": #context
    calc = Calculator() #context
    print(calc.add(3, 4)) #context
```

parsons.js parsed das automatisch — kein Backend-Spezialfeld noetig.

### 3.2 ContextAnnotator (LLM-Pass)

**Datei:** `backend/src/main/java/com/cisternino/application/service/ContextAnnotator.java`

Analog zu `InterchangeableAnnotator`, aber markiert Boilerplate/Rahmen-Code:

```java
@Service
public class ContextAnnotator {

    private static final String SYSTEM_PROMPT = """
        You are CodeOwl (Context Annotator).
        Given a code solution, identify which lines are CONTEXT (boilerplate,
        imports, class declarations, main blocks) and which are the PUZZLE
        (the core algorithm the student should arrange).

        Rules:
        - The PUZZLE portion MUST be ≤ 20 lines
        - CONTEXT = imports, class/function scaffolding, main blocks, test code
        - PUZZLE = the core logic the student needs to understand and arrange
        - Context lines should form contiguous blocks (before and/or after puzzle)
        - Preserve logical grouping — don't split a method between context and puzzle

        Return JSON: {"contextLines": [1, 2, 3, 10, 11, 12]}
        (1-based line numbers that should be marked as #context)
    """;

    public String annotate(String solution, String language) {
        // 1. Call LLM
        // 2. Parse response → List<Integer> contextLineNumbers
        // 3. Append " #context" to those lines
        // 4. Validate: puzzle portion ≤ 20 lines
        // 5. Return annotated solution (or original on failure)
    }
}
```

### 3.3 Integration in TaskEnricher

```java
// In enrichWithSolutions(), nach Sanitize + InterchangeableAnnotator:
if (contextModeEnabled) {
    annotatedCode = contextAnnotator.annotate(annotatedCode, lang);
}
```

**Reihenfolge der Passes:**
1. `solveProblem()` → Roher Code
2. `codeSanitizerService.removeComments()` → Sauberer Code
3. `interchangeableAnnotator.annotate()` → `#swap`/`#group` Marker
4. **`contextAnnotator.annotate()`** → `#context` Marker (NEU)
5. `generateDistractorsForTask()` → `#distractor` Zeilen (angehaengt)

**Marker-Koexistenz:** Eine Zeile kann NICHT gleichzeitig `#context` und `#swap`/`#group` sein. Context-Zeilen sind nicht Teil des Puzzles, also brauchen sie keine Interchangeable-Marker.

### 3.4 Generation-Toggle

**TaskGenerationRequest erweitern:**
```java
private boolean contextModeEnabled; // AP22: Beta
```

**HomePage.tsx:** Neuer Toggle neben `distractorsEnabled`:
```tsx
<div className="form-check form-switch">
    <input type="checkbox" checked={contextMode}
           onChange={e => setContextMode(e.target.checked)} />
    <label>Context-Modus (Beta) — Ermoeglicht laengere Aufgaben</label>
</div>
```

### 3.5 Prompt-Anpassung

Wenn `contextModeEnabled=true`, wird der Sequenz-Prompt angepasst:

```
CONTEXT MODE (BETA):
Generate longer, realistic code solutions (30-60 lines).
The solution should include boilerplate (imports, class declarations,
main blocks) alongside the core algorithm.
A separate pass will mark context vs puzzle lines.
The core puzzle portion will be ≤ 20 lines — the rest becomes
read-only context for the student.
```

---

## Teil 4: Frontend — Lehrer-Editing

### 4.1 TaskEditModal: Context-Marker editieren

Der Lehrer sieht die Solution mit allen Markern und kann `#context` manuell hinzufuegen/entfernen:

```tsx
// Im Code-Textarea:
<label>Solution Code</label>
<textarea value={code} onChange={...} rows={15} />
<small className="text-muted">
    Marker: <code>#swap:NAME</code>, <code>#group:NAME_N</code>,
    <code>#distractor</code>, <code>#context</code> (neu)
</small>

// Zusaetzlich: Quick-Toggle pro Zeile (optional, nice-to-have):
// Jede Zeile bekommt einen kleinen [C]-Toggle-Button
```

### 4.2 SequencePage Teacher View: Context anzeigen

Im Solution-Accordion neben den Distraktoren:

```tsx
{/* Kontext-Zeilen hervorheben */}
{task.solution && task.solution.includes("#context") && (
    <div className="mt-2">
        <small className="text-info">
            Context-Modus aktiv —
            {task.solution.split("\n").filter(l => l.includes("#context")).length} Kontext-Zeilen,
            {task.solution.split("\n").filter(l => !l.includes("#context") && l.trim()).length} Puzzle-Zeilen
        </small>
    </div>
)}
```

---

## Teil 5: Durchstich-Reihenfolge

### Schritt 1: parsons.js Modifikation (1-2h)
1. `ParsonsCodeline`: `isContext` Flag + `#context` Stripping
2. `parseCode()`: Context-Separation in `contextBefore`/`contextAfter`
3. `init()`: `this.contextBefore`/`this.contextAfter` speichern
4. Manueller Test mit hardcodiertem `#context` Code

### Schritt 2: SolvePage Rendering (1-2h)
1. Context-Bloecke ober-/unterhalb des Puzzle-Bereichs rendern
2. CSS Styling (ausgegraut, read-only, dashed border)
3. Context-Info-Banner (i18n)
4. Verifizieren: Grading ignoriert Context korrekt

### Schritt 3: Lehrer-Editing (1h)
1. TaskEditModal: Hilfetext fuer `#context` Marker
2. SequencePage: Context-Zeilen-Count in Teacher View
3. Manueller Test: Lehrer fuegt `#context` hinzu → Student sieht Context

### Schritt 4: Backend ContextAnnotator (2-3h)
1. `ContextAnnotator.java` Service (LLM-Call, JSON-Parsing, Marker-Anbringung)
2. ≤ 20-Zeilen-Validierung fuer Puzzle-Anteil
3. Integration in `TaskEnricher` Pipeline
4. `contextModeEnabled` Toggle durch Stack durchreichen

### Schritt 5: Generation-UI (1h)
1. `HomePage.tsx`: Context-Modus Toggle (Beta)
2. Prompt-Anpassung fuer laengere Aufgaben
3. E2E-Test: Generierung mit Context → SolvePage zeigt Context + Puzzle

**Geschaetzter Gesamtaufwand: 6-9h**

---

## Risiken & Mitigationen

| Risiko | Mitigation |
|--------|-----------|
| LLM markiert zu viel/wenig als Context | ≤ 20 Puzzle-Zeilen Validierung + Lehrer kann manuell korrigieren |
| Context mitten im Puzzle (kein sauberer Split) | Prompt forciert contiguous Context-Bloecke (before/after) |
| Indentation-Kontext bei Python (Context-Zeilen beeinflussen Indent) | Puzzle-Zeilen behalten relative Indentation; Context zeigt absolute |
| Bestehende Aufgaben brechen | Beta-Flag, komplett opt-in, kein Einfluss auf Aufgaben ohne `#context` |
| parsons.js Grading-Fehler | Context-Zeilen nie in `modified_lines` → Grading unveraendert |

---

## Abgrenzung

**In-Scope:**
- `#context` Marker in parsons.js
- Read-only Context-Bloecke auf SolvePage
- LLM-basierter ContextAnnotator
- Manuelles Editing durch Lehrer
- Generation-Toggle (Beta)
- ≤ 20 Puzzle-Zeilen Constraint

**Out-of-Scope (spaeter):**
- Syntax-Highlighting fuer Context-Bloecke (CodeMirror/Prism Integration)
- Zeilenreferenz-Nummern (wo im Gesamtprogramm bin ich?)
- Context-Zeilen mit Luecken (Hybrid Context + Fill-in-Blanks)
- Automatische Context-Erkennung bei bestehenden Aufgaben ohne LLM

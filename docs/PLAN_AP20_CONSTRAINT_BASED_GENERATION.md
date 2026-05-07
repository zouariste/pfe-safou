# Implementierungsplan: AP 20 — Constraint-Based Generation

## Kontext

Lehrer koennen aktuell nicht einschraenken, welche Programmierkonstrukte in generierten Aufgaben vorkommen. Das LLM generiert manchmal Konstrukte, die Schueler noch nicht kennen (Ternaeroperatoren, Lambda, List Comprehensions). AP20 gibt Lehrern die Kontrolle ueber erlaubte/verbotene Konstrukte.

**Quelle:** Thesis Future Work S.99 + Lehrer-Feedback (`DI_curriculum_mismatch`)
**Abhaengigkeiten:** AP 9 (Prompt-Pipeline) — bereits implementiert

---

## Architektur-Uebersicht

```
[HomePage.tsx]                    [PromptBuilder.java]              [ConstraintValidator.java]
  Constraint-UI   ──POST──>   buildSequenceRequest()   ──>   Post-Generation Check
  - blacklist                    + constraintSection              - AST/Regex Pruefung
  - whitelist                                                     - Regeneration bei Verletzung
  - difficultySlope
  - additionalContext
  - locale
        │
        v
[TaskGenerationRequest]  ──>  [TaskGeneratorService]  ──>  [Strategy.generate()]
  + constraints DTO               tryGenerate() mit               OpenAiClient mit
                                  Retry bei Constraint-            Constraint-Prompt
                                  Verletzung
        │
        v
[sequences Tabelle]
  + constraint_config JSONB
```

---

## Teil 1: Datenmodell & DTO

### 1.1 Neues DTO: `GenerationConstraints`

**Datei:** `backend/src/main/java/com/cisternino/domain/entities/GenerationConstraints.java`

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GenerationConstraints {
    private List<String> blacklist;       // z.B. ["ternary", "lambda", "list comprehension"]
    private List<String> whitelist;       // z.B. ["if/else", "for", "while", "print"]
    private String difficultySlope;       // "gentle", "moderate", "steep"
    private String additionalContext;     // Freitext
    private String locale;               // "de", "en", "fr" etc.
}
```

### 1.2 DB-Schema: `sequences` Tabelle erweitern

```sql
ALTER TABLE sequences ADD COLUMN constraint_config JSONB DEFAULT NULL;
```

**Inhalt:** Serialisiertes `GenerationConstraints` Objekt, z.B.:
```json
{
  "blacklist": ["ternary", "lambda"],
  "whitelist": [],
  "difficultySlope": "gentle",
  "additionalContext": "Verwende nur deutsche Variablennamen",
  "locale": "de"
}
```

### 1.3 `TieredTaskSequenceDTO` erweitern

```java
// Neues Feld
private GenerationConstraints constraints;
```

### 1.4 `PostgreSequenceDao` erweitern

- `save()`: `constraint_config` als JSONB speichern via `ObjectMapper`
- `loadById()`: `constraint_config` aus JSONB parsen

---

## Teil 2: Backend — Prompt-Injection

### 2.1 `PromptBuilder.buildSequenceRequest()` erweitern

**Datei:** `backend/src/main/java/com/cisternino/infrastructure/ai/PromptBuilder.java`

Neuer Parameter: `GenerationConstraints constraints`

Neue Prompt-Sektion (nach `targetLanguageInstruction`, vor closing):

```java
private String buildConstraintSection(GenerationConstraints constraints) {
    if (constraints == null) return "";
    StringBuilder sb = new StringBuilder();

    if (constraints.getBlacklist() != null && !constraints.getBlacklist().isEmpty()) {
        sb.append("""

        CONSTRAINT — BLACKLIST (STRICT):
        You MUST NOT use any of these programming constructs in the generated code:
        %s
        If a construct on this list is essential for the task, redesign the task to avoid it.
        This is a HARD constraint — violating it makes the output invalid.
        """.formatted(String.join(", ", constraints.getBlacklist())));
    }

    if (constraints.getWhitelist() != null && !constraints.getWhitelist().isEmpty()) {
        sb.append("""

        CONSTRAINT — WHITELIST (STRICT):
        You may ONLY use these programming constructs:
        %s
        Do not introduce any construct not on this list. This is a HARD constraint.
        """.formatted(String.join(", ", constraints.getWhitelist())));
    }

    if (constraints.getDifficultySlope() != null) {
        String desc = switch (constraints.getDifficultySlope()) {
            case "gentle" -> "The difficulty increase between tiers should be very small. " +
                             "Tier 1 and Tier 2 should feel almost equally easy.";
            case "moderate" -> "The difficulty should increase noticeably but not dramatically between tiers.";
            case "steep" -> "Each tier should be significantly harder than the previous one. " +
                            "Tier 3 should be challenging even for experienced students.";
            default -> "";
        };
        sb.append("\nDIFFICULTY PROGRESSION:\n").append(desc).append("\n");
    }

    if (constraints.getAdditionalContext() != null && !constraints.getAdditionalContext().isBlank()) {
        sb.append("""

        ADDITIONAL TEACHER INSTRUCTIONS (follow exactly):
        %s
        """.formatted(constraints.getAdditionalContext()));
    }

    return sb.toString();
}
```

### 2.2 Solver-Prompt ebenfalls constrainen

Nicht nur die Sequenz-Generierung, sondern auch der `solveProblem()`-Call muss die Constraints kennen. Aktuell loest der Solver basierend auf dem `contract` — dort muessen Blacklist/Whitelist-Hinweise in `prePostConditions` oder als separates Feld eingefuegt werden.

**Ansatz:** `contract` JSON um optionales `constraints`-Feld erweitern:
```json
{
  "language": "python",
  "taskStatement": "...",
  "constraints": {
    "blacklist": ["ternary", "lambda"]
  }
}
```

Der Solver-Prompt in `PromptBuilder.buildSolveRequest()` liest dieses Feld und fuegt die Constraints ein.

---

## Teil 3: Post-Generation Validation

### 3.1 Neuer Service: `ConstraintValidator`

**Datei:** `backend/src/main/java/com/cisternino/application/service/ConstraintValidator.java`

```java
@Service
public class ConstraintValidator {

    /**
     * Prueft ob der generierte Code gegen Blacklist-Constraints verstoesst.
     * @return Liste der verletzten Constraints (leer = alles OK)
     */
    public List<String> validateBlacklist(String code, List<String> blacklist, String language) {
        if (blacklist == null || blacklist.isEmpty()) return List.of();

        List<String> violations = new ArrayList<>();
        String codeLower = code.toLowerCase();

        for (String construct : blacklist) {
            String normalized = construct.trim().toLowerCase();
            List<String> patterns = getPatterns(normalized, language);
            for (String pattern : patterns) {
                if (codeLower.matches("(?s).*" + pattern + ".*")) {
                    violations.add(construct);
                    break;
                }
            }
        }
        return violations;
    }

    private List<String> getPatterns(String construct, String language) {
        // Sprachspezifische Pattern-Bibliothek
        return switch (construct) {
            case "ternary" -> switch (language.toLowerCase()) {
                case "python" -> List.of("\\bif\\b.*\\belse\\b(?!\\s*:)"); // x if cond else y
                case "java", "javascript", "c" -> List.of("\\?.*:");        // cond ? a : b
                default -> List.of();
            };
            case "lambda" -> switch (language.toLowerCase()) {
                case "python" -> List.of("\\blambda\\b");
                case "java" -> List.of("->\\s*\\{?", "\\bFunction\\b", "\\bConsumer\\b");
                default -> List.of();
            };
            case "list comprehension" -> List.of("\\[.*\\bfor\\b.*\\bin\\b.*\\]");
            case "dict comprehension" -> List.of("\\{.*\\bfor\\b.*\\bin\\b.*\\}");
            case "recursion" -> List.of(); // Schwer per Regex — Fallback auf AST noetig
            case "while" -> List.of("\\bwhile\\b");
            case "for" -> List.of("\\bfor\\b");
            case "try" -> List.of("\\btry\\b");
            case "class" -> List.of("\\bclass\\b");
            case "import" -> List.of("\\bimport\\b");
            case "global" -> List.of("\\bglobal\\b");
            case "match", "switch" -> List.of("\\b(match|switch)\\b");
            case "walrus", ":=" -> List.of(":=");
            case "f-string" -> List.of("f['\"]");
            case "decorator" -> List.of("@\\w+");
            default -> List.of(java.util.regex.Pattern.quote(construct));
        };
    }
}
```

### 3.2 Bekannte Construct-Patterns (Bibliothek)

| Construct | Python Pattern | Java Pattern |
|---|---|---|
| ternary | `x if cond else y` | `? :` |
| lambda | `lambda` keyword | `->` operator |
| list comprehension | `[... for ... in ...]` | N/A |
| recursion | Funktionsname in eigenem Body | Methodenname in eigenem Body |
| while loop | `while` keyword | `while` keyword |
| for loop | `for` keyword | `for` keyword |
| try/catch | `try` keyword | `try` keyword |
| class | `class` keyword | `class` keyword |
| f-string | `f"..."` / `f'...'` | N/A |
| walrus | `:=` | N/A |
| switch/match | `match` keyword (3.10+) | `switch` keyword |

### 3.3 Integration in TaskEnricher

```java
// Nach Code-Generierung (solveProblem), vor Annotation:
if (constraints != null && constraints.getBlacklist() != null) {
    List<String> violations = constraintValidator.validateBlacklist(
        sanitizedCode, constraints.getBlacklist(), lang);

    if (!violations.isEmpty()) {
        logger.warn("Constraint violations found: {}. Requesting regeneration.", violations);
        throw new ConstraintViolationException(violations);
    }
}
```

### 3.4 Retry-Logik in TaskGeneratorService

```java
// In tryGenerate():
int maxRetries = 2;
for (int attempt = 0; attempt <= maxRetries; attempt++) {
    try {
        var raw = strategy.generate(gptClient, distractorsEnabled);
        var enriched = taskEnricher.enrichWithSolutions(raw, distractorsEnabled, constraints);
        return enriched;
    } catch (ConstraintViolationException e) {
        if (attempt == maxRetries) {
            logger.error("Constraint violations persist after {} retries: {}", maxRetries, e.getViolations());
            throw e; // Gibt Fehlermeldung an Frontend zurueck
        }
        logger.info("Retrying generation due to constraint violations: {}", e.getViolations());
    }
}
```

---

## Teil 4: Frontend — Constraint-UI

### 4.1 `HomePage.tsx` erweitern

Neue Felder im Generation-Formular (unterhalb der bestehenden Felder, in einem aufklappbaren "Advanced Options" Bereich):

```tsx
{/* AP20: Constraint-Based Generation */}
<details className="mb-3">
    <summary className="fw-bold mb-2">Advanced Options</summary>

    {/* Blacklist */}
    <div className="mb-3">
        <label className="form-label">Blacklist (nicht verwenden)</label>
        <input type="text" className="form-control"
               placeholder="z.B. ternary, lambda, list comprehension"
               value={blacklist} onChange={e => setBlacklist(e.target.value)} />
        <small className="text-muted">Komma-getrennte Liste von Konstrukten</small>
    </div>

    {/* Whitelist */}
    <div className="mb-3">
        <label className="form-label">Whitelist (nur verwenden)</label>
        <input type="text" className="form-control"
               placeholder="z.B. if/else, for, while, print"
               value={whitelist} onChange={e => setWhitelist(e.target.value)} />
        <small className="text-muted">Restriktiver Modus — nur diese Konstrukte erlaubt</small>
    </div>

    {/* Difficulty Slope */}
    <div className="mb-3">
        <label className="form-label">Schwierigkeitsverlauf</label>
        <select className="form-select" value={difficultySlope}
                onChange={e => setDifficultySlope(e.target.value)}>
            <option value="">Standard</option>
            <option value="gentle">Sanft (kleine Spruenge)</option>
            <option value="moderate">Moderat</option>
            <option value="steep">Steil (grosse Spruenge)</option>
        </select>
    </div>

    {/* Additional Context */}
    <div className="mb-3">
        <label className="form-label">Zusaetzliche Anweisungen</label>
        <textarea className="form-control" rows={2}
                  placeholder="z.B. 'Verwende nur deutsche Variablennamen'"
                  value={additionalContext}
                  onChange={e => setAdditionalContext(e.target.value)} />
    </div>
</details>
```

### 4.2 `TaskGenerationRequest` erweitern

```java
// In TasksController.TaskGenerationRequest:
private List<String> blacklist;
private List<String> whitelist;
private String difficultySlope;
private String additionalContext;
```

Frontend sendet:
```json
{
  "problemStatement": "...",
  "language": "python",
  "concepts": ["loops"],
  "distractorsEnabled": true,
  "blacklist": ["ternary", "lambda"],
  "whitelist": [],
  "difficultySlope": "gentle",
  "additionalContext": "Deutsche Variablennamen"
}
```

---

## Teil 5: Durchstich-Reihenfolge

### Schritt 1: Backend-Fundament (geschaetzter Aufwand: 2-3h)
1. `GenerationConstraints` DTO erstellen
2. `ConstraintValidator` Service implementieren (Regex-Bibliothek)
3. `TaskGenerationRequest` um Constraint-Felder erweitern
4. DB-Migration: `constraint_config JSONB` Spalte

### Schritt 2: Prompt-Integration (geschaetzter Aufwand: 2h)
1. `PromptBuilder.buildSequenceRequest()` um `buildConstraintSection()` erweitern
2. `PromptBuilder.buildSolveRequest()` um Blacklist/Whitelist-Hinweise erweitern
3. Constraints durch Strategy-Kette durchreichen

### Schritt 3: Validation & Retry (geschaetzter Aufwand: 1-2h)
1. `ConstraintViolationException` erstellen
2. Validation in `TaskEnricher` nach `solveProblem()` einbauen
3. Retry-Logik in `TaskGeneratorService.tryGenerate()` erweitern
4. Fehlermeldung an Frontend bei persistenten Violations

### Schritt 4: Frontend (geschaetzter Aufwand: 1-2h)
1. Constraint-Felder in `HomePage.tsx` Generation-Formular
2. State-Management fuer neue Felder
3. Payload an Backend senden
4. Constraint-Info auf SequencePage anzeigen (Teacher View)

### Schritt 5: Persistenz & Anzeige (geschaetzter Aufwand: 1h)
1. `PostgreSequenceDao` JSONB Lese/Schreib-Logik
2. Constraints in Sequence-Export (JSON/CSV/PDF) anzeigen
3. i18n Keys fuer Constraint-UI

---

## Risiken & Mitigationen

| Risiko | Mitigation |
|---|---|
| LLM ignoriert Blacklist trotz Prompt | Post-Generation Validation + Retry (max 2x) |
| Whitelist zu restriktiv fuer sinnvolle Aufgabe | Frontend-Warnung bei <3 Konstrukten in Whitelist |
| Regex-Erkennung ungenau (false positives) | Konservative Patterns, Lehrer kann Validierung ueberstimmen |
| Rekursion schwer per Regex erkennbar | Als "best effort" markieren, AST-basierte Erkennung als zukuenftige Erweiterung |
| Performance bei Retry-Schleife | Max 2 Retries, danach Fehlermeldung mit konkreten Violations |

---

## Testplan

1. **Unit-Tests ConstraintValidator:**
   - Jedes Pattern einzeln testen (Python + Java)
   - False-Positive-Tests (z.B. "for" in Variablenname "information")
   - Leere Blacklist/Whitelist

2. **Integration-Test Prompt-Injection:**
   - Generierung mit Blacklist ["lambda", "ternary"] → Code darf kein Lambda/Ternary enthalten
   - Generierung mit Whitelist ["if", "for", "print"] → Code nutzt nur diese Konstrukte

3. **E2E-Test:**
   - HomePage: Constraint-Felder ausfuellen → Generieren → SequencePage pruefen
   - Retry-Fall: Konstrukt trotz Blacklist → Regeneration → Clean Output

---

## Abgrenzung

**In-Scope:**
- Blacklist/Whitelist fuer Programmierkonstrukte
- Difficulty-Slider
- Freitext-Kontext-Feld
- Post-Generation Regex-Validierung
- Retry bei Violation

**Out-of-Scope (spaeter):**
- AST-basierte Tiefenanalyse (z.B. Rekursionserkennung)
- Automatische Construct-Erkennung aus Lehrplan-Import
- Constraint-Templates ("Klasse 7 Informatik", "AP Comp Sci A")
- Constraint-Sharing zwischen Lehrern

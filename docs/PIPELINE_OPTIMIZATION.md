# Pipeline-Optimierungsanalyse — Parallelisierungspotenziale

**Stand:** 2026-04-17
**Scope:** Task-Generation-Pipeline (Strategy → Enrichment → Persistence)
**Fokus:** Parallelisierung, Latenzreduktion, Durchsatz

---

## 1. Executive Summary

Die Haupt-Latenztreiber sind **sequentielle LLM-Calls**. Für eine typische Sequenz mit 3 Tiers und Distractors+Context-Mode fallen **bis zu 12 blockierende HTTP-Calls hintereinander** an (3 × solveProblem + 3 × interchangeableAnnotator + 3 × contextAnnotator + 3 × generateDistractors), jeweils ~2–8s.

**Gesamtdauer heute (Worst Case):** ~60–90s pro Sequenz.
**Durch Task-Parallelisierung erreichbar:** ~20–30s (Faktor 3×).
**Durch Intra-Task-Overlap zusätzlich:** ~15–25s (Faktor 4×).

Die Pipeline ist **nahezu 100% I/O-bound** — Parallelisierung ist mit überschaubarem Aufwand möglich, da keine Zwischen-Abhängigkeiten zwischen den Tasks einer Sequenz bestehen.

---

## 2. Ist-Zustand: Pipeline-Architektur

### 2.1 Call-Graph einer Generation

```
TaskGeneratorService.generate(…)
  ├─ [Retry-Loop, maxAttempts=3]
  │    ├─ TaskGenerationStrategyFactory.choose(…)           [CPU, <1ms]
  │    ├─ strategy.generate(gptClient, …)                    [LLM, 3–6s]   ← 1 Call: Tasks-Grundgerüst
  │    └─ taskEnricher.enrichWithSolutions(raw, …)           [LLM × N Tasks, sequentiell]
  │         └─ für jeden Task (stream().map, sequentiell):
  │              ├─ client.solveProblem(contract)             [LLM, 2–5s]  ← Call 2…N+1
  │              ├─ codeSanitizerService.removeComments(…)    [CPU, <5ms]
  │              ├─ interchangeableAnnotator.annotate(…)      [LLM, 2–4s]  ← Call N+2…2N+1
  │              ├─ contextAnnotator.annotate(…)  [opt.]      [LLM, 2–4s]  ← Call 2N+2…3N+1
  │              ├─ codeAnalyzer.computeComplexity(…)         [CPU, <50ms]
  │              └─ generateDistractorsForTask(…)  [opt.]     [LLM, 2–4s]  ← Call 3N+2…4N+1
  └─ taskPersistenceService.persistSequence(seq)              [DB, ~100–300ms]
       ├─ saveSequence(seq)
       ├─ saveAllGeneratedTasks(seq)          [batch insert]
       └─ linkConcepts(seq)                    [sequenziell, 1 SELECT+1 INSERT pro Concept]
```

### 2.2 Kennzahlen (approximativ, OpenAI gpt-4o-mini)

| Schritt                       | Typ   | Latenz    | Anzahl bei N=3 Tasks |
|-------------------------------|-------|-----------|----------------------|
| Strategy.generate (Grundgerüst) | LLM  | 3–6s      | 1                    |
| solveProblem                  | LLM   | 2–5s      | N                    |
| interchangeableAnnotator      | LLM   | 2–4s      | N                    |
| contextAnnotator (opt.)       | LLM   | 2–4s      | N (wenn aktiv)       |
| generateDistractors (opt.)    | LLM   | 2–4s      | N (wenn aktiv)       |
| codeSanitizer                 | CPU   | <5ms      | N                    |
| codeAnalyzer                  | CPU   | 10–50ms   | N                    |
| DB-Persistence                | DB    | 100–300ms | 1                    |

**Erwartete Gesamtdauer (heute, sequenziell, alle Features an, N=3):**
`3–6s + 3 × (2–5 + 2–4 + 2–4 + 2–4)s + 0.3s ≈ 30–60s`

**Mit 5 Tiers + Distractors + Context (N=5):** `~50–100s`.

### 2.3 Hot Paths (TaskEnricher.java:80)

```java
List<TaskDTO> enrichedTasks = incompleteSequence.getTasks().stream().map(task -> {
    // … 2–4 LLM-Calls pro Task, sequenziell …
}).collect(Collectors.toList());
```

Das ist das **zentrale Parallelisierungs-Target**. Ein einzelnes Token-Replace (`.stream()` → `.parallelStream()`) würde bereits ~3× Speedup auf N=3 bringen — aber nicht ohne Nebenwirkungen (siehe §5).

---

## 3. Optimierungsopportunities (priorisiert)

### OPT-1 ⭐⭐⭐ Inter-Task-Parallelisierung in `enrichWithSolutions`

**File:** `backend/…/application/service/TaskEnricher.java:80`

**Heute:** Tasks werden sequenziell angereichert (`stream().map()`).
**Ziel:** Alle N Tasks einer Sequenz parallel anreichern.

**Warum möglich:** Tasks sind nach `strategy.generate(…)` **unabhängig voneinander** — keine Read-/Write-Konflikte, keine geteilten mutablen Zustände. Jeder Task hat eigenen `contract`, eigenes `sanitized`, eigene `complexity`.

**Empfohlene Umsetzung:**

```java
// Dedizierter Executor (nicht ForkJoinPool.commonPool, das ist CPU-Sized)
ExecutorService llmPool = Executors.newFixedThreadPool(8);  // oder VirtualThread-Executor (JDK 21+)

List<CompletableFuture<TaskDTO>> futures = incompleteSequence.getTasks().stream()
    .map(task -> CompletableFuture.supplyAsync(() -> enrichSingleTask(task, …), llmPool))
    .toList();

List<TaskDTO> enrichedTasks = futures.stream()
    .map(CompletableFuture::join)
    .toList();
```

**Speedup:** Bei N=3 Tasks und ~15s pro Task: 45s → 15s (**3×**).
**Bei N=5 Tasks:** 75s → 15s (**5×**).

**Risiken:**
- **OpenAI-Rate-Limits:** `tier-1` erlaubt 3500 RPM / 200K TPM — bei 5 parallelen Calls unkritisch, aber Retries mit Backoff nötig (haben wir bereits in `OpenAiClient.sendRequest`).
- **Exception-Handling:** `CompletableFuture.join()` wirft `CompletionException`, die die ursprüngliche `GenerationException` wrappt — Error-Mapping anpassen.
- **Logging-Reihenfolge:** Traces erscheinen interleaved. Mit MDC (`MDC.put("taskId", …)`) lösbar.
- **Transaction-Isolation:** `enrichWithSolutions` ist **nicht** `@Transactional` — keine Connection-Held-Over-IO-Gefahr. ✓

**Aufwand:** S (1–2h).
**Priorität:** HIGH. Der mit Abstand größte Hebel.

---

### OPT-2 ⭐⭐ Intra-Task Overlap (LLM-Call + CPU parallel)

**File:** `backend/…/application/service/TaskEnricher.java:100–134`

**Heute:** Innerhalb eines Tasks: `solveProblem` (LLM) → `removeComments` (CPU) → `interchangeableAnnotator` (LLM, benötigt sanitized) → `computeComplexity` (CPU, benötigt sanitized) → `generateDistractors` (LLM, benötigt sanitized+complexity).

**Erkenntnis:** `computeComplexity` und `interchangeableAnnotator` arbeiten beide auf dem **gleichen Input** (sanitizedCode) — können parallel laufen!

```
      solveProblem
           │
      removeComments       (CPU, <5ms)
           │
     ┌─────┴──────┐
     ▼            ▼
 computeCompl.   interchangeableAnnotator  ← parallelisierbar
 (CPU, 50ms)    (LLM, 2–4s)
     │            │
     └────┬───────┘
          ▼
  contextAnnotator (opt., braucht annotatedCode)
          │
  generateDistractors (opt., braucht complexity + sanitizedCode)
```

**Speedup:** Minimal (~50ms/Task), da `computeComplexity` CPU-bound und sehr schnell.
**Lohnt sich:** Nur in Kombination mit OPT-3.

**Priorität:** LOW — nice to have, aber der Return ist klein verglichen mit OPT-1.

---

### OPT-3 ⭐⭐ Distractor + Context Parallel pro Task

**File:** `backend/…/application/service/TaskEnricher.java:114–134`

**Heute:** `contextAnnotator` läuft vor `generateDistractors`.

**Analyse der Abhängigkeiten:**
- `contextAnnotator.annotate(annotatedCode, lang)` → braucht `annotatedCode` (Output von interchangeableAnnotator)
- `generateDistractors(sanitizedCode, …, complexity.linesOfCode())` → braucht `sanitizedCode` (Output von removeComments) + `complexity.linesOfCode()`

**Erkenntnis:** Die beiden LLM-Calls sind **voneinander unabhängig**! Kein Call braucht den Output des anderen.

```java
CompletableFuture<ContextAnnotator.ContextResult> ctxFut =
    contextModeEnabled
        ? CompletableFuture.supplyAsync(() -> contextAnnotator.annotate(annotatedCode, lang), llmPool)
        : CompletableFuture.completedFuture(null);

CompletableFuture<String> distFut =
    distractorsEnabled
        ? CompletableFuture.supplyAsync(() -> generateDistractorsForTask(
              sanitizedCode, lang, task.getStatement(), task.getTierLevel(),
              task.getDistractorPlan(), complexity.linesOfCode()), llmPool)
        : CompletableFuture.completedFuture(null);

ContextAnnotator.ContextResult ctxResult = ctxFut.join();
String distractors = distFut.join();
```

**Speedup:** `max(ctxLatenz, distLatenz)` statt `ctxLatenz + distLatenz` → ~3s pro Task gespart bei beiden Features aktiv.
**Pro Sequenz (N=3):** ~9s.

**Priorität:** MEDIUM — Codeänderung minimal, Gewinn signifikant bei Context+Distractor gleichzeitig aktiv.

---

### OPT-4 ⭐⭐ Async HTTP-Client in OpenAiClient

**File:** `backend/…/infrastructure/ai/openai/OpenAiClient.java`

**Heute:** `java.net.http.HttpClient` wird **synchron blocking** verwendet (`HttpClient.send(…)` statt `sendAsync(…)`).

**Problem:** Jeder LLM-Call hält einen Worker-Thread für 2–5s. Bei OPT-1 mit 5 parallelen Calls blockieren 5 Threads im Pool.

**Lösung A — Async API:**
```java
client.sendAsync(request, BodyHandlers.ofString())
      .thenApply(response -> …)
```

**Lösung B — Virtual Threads (JDK 21+, empfohlen):**
```java
ExecutorService vThreads = Executors.newVirtualThreadPerTaskExecutor();
// Bleibt synchron, aber Thread ist günstig
```

**Warum Virtual Threads besser:** Code bleibt synchron-lesbar, `CompletableFuture.supplyAsync(…, vThreads)` skaliert trotzdem auf hunderte parallele Calls ohne Thread-Starvation.

**Prüfen:** `<java.version>21</java.version>` in `pom.xml`? Aktuell vermutlich 17 — dann ist ein Bump nötig.

**Priorität:** MEDIUM — nicht dringend, aber Voraussetzung für wirklich skalierbare Inter-Task-Parallelität bei großen Lehrplanmengen.

---

### OPT-5 ⭐ Retry-Backoff mit Jitter

**File:** `backend/…/infrastructure/ai/openai/OpenAiClient.sendRequest` (nicht im aktuellen Read, aber aus Kontext bekannt: 3 Retries, 3s fixed sleep bei 503)

**Heute:** Fixed 3s Sleep. Bei parallelen Calls (OPT-1) können alle gleichzeitig fehlschlagen und exakt nach 3s gemeinsam neu versuchen → Thundering Herd.

**Empfehlung:** Exponential Backoff mit Jitter:
```java
long delayMs = (long) (Math.pow(2, attempt) * 1000L * (0.5 + Math.random() * 0.5));
Thread.sleep(Math.min(delayMs, 30_000));
```

**Priorität:** LOW-MEDIUM (wird kritisch sobald OPT-1 live ist).

---

### OPT-6 ⭐ Sequenziertes `linkConcepts` → Batch-Insert

**File:** `backend/…/application/service/TaskPersistenceService.java:221–247`

**Heute:**
```java
sequence.getConcepts().forEach(concept -> {
    ConceptDTO dto = new ConceptDTO();
    dto.setName(concept);
    UUID link = conceptRepo.save(dto);         // 1 SELECT + ggf. INSERT
    TieredTaskSequenceConceptDTO linkDTO = …;
    sequenceRepo.linkConcept(linkDTO);          // 1 INSERT
});
```

Bei z.B. 8 Concepts pro Sequenz: **16 Roundtrips à ~10ms = 160ms.**

**Lösung:** `JdbcTemplate.batchUpdate(…)` oder `INSERT … ON CONFLICT (name) DO UPDATE RETURNING id` mit einem einzigen Statement.

**Speedup:** ~100–150ms pro Sequenz.
**Priorität:** LOW — absolute Zahl klein, aber „quick win" bei Refactor.

---

### OPT-7 ⭐ Parallel-Safe Caching von `solveProblem`-Ergebnissen

**File:** `backend/…/application/service/TaskEnricher.java` + neu

**Heute:** Bei `generate` mit `maxAttempts=3` wird bei Retry die **gesamte Pipeline** von vorne aufgerufen — inklusive aller LLM-Calls. Wenn z.B. die finale `ConstraintValidator`-Prüfung fehlschlägt, werden bereits generierte, gültige Task-Solutions weggeworfen.

**Idee:** Ein **generation-scope-cache** (ConcurrentHashMap keyed by `contract.hashCode()`) könnte auf dem zweiten Attempt bereits gelöste Tasks wiederverwenden. **Aber Vorsicht:** Wenn Retry aufgrund von unzureichender Diversität erfolgt, ist Cache-Reuse kontraproduktiv.

**Priorität:** LOW — architektonisch unsicher, besser vertagen.

---

### OPT-8 ⭐⭐ Strategy.generate: Prompt-Caching

**File:** `backend/…/infrastructure/ai/openai/OpenAiClient.java`

**Heute:** Jeder Call sendet den gesamten Prompt (System + Few-Shot + User) erneut.

**OpenAI-Feature:** `cached_tokens` seit Okt 2024 — Prompts >1024 Tokens werden automatisch gecached, **aber nur wenn sie mit identischem Prefix beginnen.**

**Prüfen:**
1. Stehen variable Teile (Task-Beschreibung) am **Ende** des Prompts? Wenn ja: ✓
2. Enthält der Prompt dynamische Elemente am Anfang (z.B. Timestamp, UUID)? Wenn ja: Refactor nötig.

**Speedup:** Bis zu 50% Latenzreduktion + 50% Kostenersparnis für Input-Tokens.
**Aufwand:** Prompt-Struktur-Review + ggf. Refactoring in `PromptBuilder`.

**Priorität:** MEDIUM — reiner Organisations-Gewinn, keine neue Infrastruktur nötig.

---

### OPT-9 ⭐ CurriculumGenerationService: Retry analog zu TaskGeneratorService

**File:** `backend/…/application/service/CurriculumGenerationService.java`

**Heute (laut Audit):** Keine Retry-Logik — einzelner fehlgeschlagener LLM-Call → Totalausfall der Curriculum-Generation.

**Empfehlung:** Gleiches Retry-Pattern wie `TaskGeneratorService.generate` (maxAttempts=3, exponentielles Backoff).

**Priorität:** MEDIUM (kein Parallelisierungs-Issue, aber Robustheit).

---

### OPT-10 ⭐ Streaming-Response für `solveProblem`

**File:** `backend/…/infrastructure/ai/openai/OpenAiClient.java`

**Heute:** Synchroner Call, voller Response wird auf einmal gelesen.

**Idee:** OpenAI unterstützt `stream: true` — erste Tokens kommen in ~200ms statt 2–5s. Für **UI-Feedback** (der Lehrer sieht erste Zeilen bereits) wäre das ein UX-Win.

**Aber:** Für die Pipeline selbst nutzlos, da wir den **gesamten** Code brauchen (sanitize/annotate arbeiten nur auf Vollversion).

**Nur sinnvoll** für UI-Streaming-Preview (größerer Umbau: SSE-Endpoint, Frontend-Stream-Reader).
**Priorität:** LOW — „nice to have", nicht zentrales Pipeline-Optimierungsziel.

---

## 4. Empfohlene Umsetzungs-Reihenfolge

| Priorität | OPT  | Aufwand | Erwarteter Gewinn           |
|-----------|------|---------|-----------------------------|
| **P1**    | OPT-1 | S (1–2h)  | 3–5× Sequenz-Durchsatz      |
| **P1**    | OPT-5 | XS (30min) | Verhindert Retry-Kollaps    |
| **P2**    | OPT-3 | S (1h)    | ~3s × N Tasks bei Ctx+Dist  |
| **P2**    | OPT-8 | M (2–4h)  | 30–50% Latenz pro LLM-Call  |
| **P2**    | OPT-9 | XS (30min) | Robustheit Curriculum-Gen   |
| **P3**    | OPT-4 | M (2–4h)  | Skalierung für >10 Tasks    |
| **P3**    | OPT-6 | XS (30min) | ~150ms/Sequenz              |
| **P4**    | OPT-2 | XS        | ~50ms/Task (marginal)       |
| **P4**    | OPT-7 | M         | Konzeptionell riskant       |
| **P4**    | OPT-10 | L        | UI-only Benefit             |

---

## 5. Nebenbedingungen & Fallstricke

### 5.1 OpenAI-Rate-Limits

Bei Parallelisierung (OPT-1) läuft man in **RPM/TPM-Limits**:
- Tier 1: 3500 RPM, 200K TPM
- Tier 2: 5000 RPM, 450K TPM

Für realistische Bachelor-Arbeits-Demo-Loads (<50 Sequenzen/Tag) unkritisch. In Produktion:
- Semaphore mit `maxConcurrentCalls` konfigurierbar.
- Response-Header `x-ratelimit-remaining-requests` beobachten.

### 5.2 Transaktion-Grenzen

`@Transactional`-Methoden dürfen **keine langen IOs** enthalten (Connection-Pool-Starvation).
**Aktuell OK:** `enrichWithSolutions` ist NICHT transactional.
**Bei OPT-1:** Falls später `@Transactional` hinzukommt: unbedingt LLM-Calls davor/danach!

### 5.3 Deterministic Ordering

`enrichedTasks` muss die gleiche Reihenfolge wie `incompleteSequence.getTasks()` haben (Tier 1, 2, 3, …).
`CompletableFuture.join()` in der ursprünglichen Stream-Reihenfolge erhält das (solange `List` geordnet ist) — `.parallelStream()` auch, aber implizit über Collector.

### 5.4 Exception-Propagation

`CompletableFuture` wrappt Exceptions in `CompletionException`. Existierendes Error-Handling (z.B. `GenerationException`-Catch in Controllern) muss um `ex.getCause()` erweitert werden.

### 5.5 Observability

Bei Parallelisierung werden Logs interleaved. Empfehlung: **MDC**-Kontext mit `sequenceId` und `taskId`, damit Log-Aggregation (ELK/Loki) weiterhin Zusammenhänge zeigt.

---

## 6. Quick-Win-Vorschlag für Bachelorarbeit

Wenn nur **eine** Optimierung gemacht wird: **OPT-1 mit VirtualThread-Executor** (falls JDK 21 verfügbar, sonst FixedThreadPool(8)).

- ~3–5× Speedup pro Sequenz
- Diff ca. 20 Zeilen in `TaskEnricher.java`
- Messbar in Arbeit einbaubar („Vorher/Nachher"-Tabelle)
- Wissenschaftlich sauber beschreibbar als „Async-Pipeline für unabhängige Enrichment-Schritte"

**Empfohlener Messaufbau:**
- 3 Sequenzen à 3 Tiers, je mit/ohne Distractors, je mit/ohne Context
- Wall-Clock-Time mit `Micrometer` Timer
- Diff sequenziell vs. parallel in Log

---

## 7. Nicht-Parallelisierbares

Zur Vollständigkeit: Folgende Schritte sind **nicht** sinnvoll parallelisierbar:

- `strategy.generate(…)`: Liefert den Grundbauplan für alle Tasks; **muss** vor Enrichment laufen.
- `persistSequence`: Transactional, Order-Dependent (Sequence vor Tasks vor Concepts).
- `ConstraintValidator`: Pure CPU, keine Latenz.
- `TaskGeneratorService.generate`-Retry-Loop: Seriell by design.

---

**Autor:** Pipeline-Audit (2026-04-17)
**Nächster Schritt:** OPT-1 in Spike-Branch implementieren und messen.

# NoteNest — Technical Design Document

| Field | Value |
| --- | --- |
| **Project Name** | NoteNest |
| **Document Type** | Technical Design Document |
| **Version** | 1.0 |
| **Date** | 2026-05-20 |
| **Author** | Prachi Dnyaneshwar Parate |
| **Status** | Released — deployed on Render |

---

## 1. Architecture Overview

NoteNest follows a classic **three-tier MVC architecture** built on Spring Boot 3, with the addition of a stateless integration tier for external AI calls.

```
┌──────────────────────────────────────────────────────────────────┐
│  BROWSER (Chrome / Edge / Firefox / Safari)                       │
│  • Thymeleaf-rendered HTML + Bootstrap 5 + Inter font             │
│  • Vanilla JS modules (no framework)                              │
│  • Web Speech API for voice input                                 │
└────────────────────────────┬─────────────────────────────────────┘
                             │ HTTPS
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│  SPRING BOOT 3.4 (single JAR, deployed on Render)                 │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │  Controller layer (Spring MVC + @ControllerAdvice)            │ │
│  │  NoteController, AiController, GlobalExceptionHandler         │ │
│  └────────────────────────────┬─────────────────────────────────┘ │
│  ┌────────────────────────────▼─────────────────────────────────┐ │
│  │  Service layer (@Service, @Transactional)                     │ │
│  │  NoteService, SearchService, GeminiService, RateLimitService  │ │
│  └────────┬──────────────────────────────┬─────────────────────┘ │
│  ┌────────▼──────────────┐  ┌────────────▼─────────────────────┐ │
│  │  Repository layer     │  │  RestTemplate (Gemini API client) │ │
│  │  Spring Data JPA      │  └────────────┬─────────────────────┘ │
│  └────────┬──────────────┘               │                        │
└───────────┼──────────────────────────────┼────────────────────────┘
            │                              │
            ▼                              ▼
   ┌────────────────────┐         ┌────────────────────────┐
   │ PostgreSQL (Neon)  │         │  Google Gemini 2.5     │
   │ — notes, tags,     │         │  (generativelanguage.  │
   │   flashcards       │         │   googleapis.com)      │
   └────────────────────┘         └────────────────────────┘
```

### Key architectural decisions

1. **Server-rendered HTML** (Thymeleaf) over SPA — keeps the project small and the SEO/accessibility story simple. Vanilla JS provides progressive enhancements (live search, AI buttons, voice).
2. **Single shared `RestTemplate`** for Gemini calls, configured via `RestTemplateConfig` with explicit timeouts (10 s connect, 30 s read).
3. **In-memory rate limiting** (`RateLimitService`) — no external dependency, sufficient for a single-instance free-tier deployment.
4. **Profile-based DB switching** — H2 file-based for dev (fast, zero setup), PostgreSQL on Neon for prod.
5. **DTO segregation** — `NoteRequestDTO` for inbound, `NoteResponseDTO` for general response, `NoteDetailDTO` for the AI-enhanced detail view, `NoteSearchResultDTO` for search results. Avoids leaking entities into the view layer.

---

## 2. Tech Stack

| Layer | Technology | Version | Purpose |
| --- | --- | --- | --- |
| Language | Java | 17 | Application language |
| Framework | Spring Boot | 3.4.5 | Web + JPA + dependency injection |
| Web | Spring MVC | 6.x (bundled) | HTTP request handling, Thymeleaf integration |
| Templating | Thymeleaf | 3.x (bundled) | Server-side HTML rendering |
| Persistence | Spring Data JPA + Hibernate | 6.x (bundled) | ORM, query derivation |
| Validation | Jakarta Bean Validation | 3.x (bundled) | Form input validation |
| Boilerplate | Lombok | 1.18.x | Reduces getter/setter/builder code |
| DB (dev) | H2 | 2.x (bundled) | File-based local DB |
| DB (prod) | PostgreSQL | 16 (Neon-managed) | Production DB |
| Logging | SLF4J + Logback | bundled | Structured app logs |
| Build | Maven | 3.9 | Build, dependency, packaging |
| HTTP client | Spring `RestTemplate` | bundled | Synchronous Gemini API calls |
| JSON | Jackson | bundled | Gemini request/response serialization |
| Testing | JUnit 5 + Mockito + MockMvc | bundled | Unit + integration tests |
| Frontend CSS | Bootstrap | 5.3.3 | UI framework (CDN) |
| Frontend icons | Bootstrap Icons | 1.11.3 | Icon set (CDN) |
| Frontend font | Inter | latest | Modern UI typography (Google Fonts) |
| Frontend charts | Chart.js | 4.4 | Activity heatmap on dashboard |
| Frontend JS | Vanilla ES2017+ | n/a | All client-side logic |
| Voice input | Web Speech API | browser-native | Dictation in Chrome/Edge |
| AI | Google Gemini 2.5-flash | REST API | All AI features |
| Containerization | Docker | multi-stage build | Render deployment |
| Hosting | Render | Web Service (Free) | Production hosting |
| Source control | Git + GitHub | n/a | Version control |

---

## 3. Package Structure

```
src/main/java/com/notenest/
├── NoteNestApplication.java            # @SpringBootApplication entry point
│
├── config/                              # Spring @Configuration classes
│   ├── RestTemplateConfig.java          # RestTemplate bean (timeouts)
│   ├── WebConfig.java                   # Static resource handlers
│   └── DataSeeder.java                  # @Profile("dev") sample-data loader
│
├── controller/                          # Spring MVC controllers
│   ├── NoteController.java              # /notes routes (CRUD, search, folder, pin)
│   └── AiController.java                # /ai routes (chat, summarize, tools, etc.)
│
├── dto/                                 # Data Transfer Objects
│   ├── NoteRequestDTO.java              # Inbound (form POST)
│   ├── NoteResponseDTO.java             # General-purpose outbound
│   ├── NoteDetailDTO.java               # NoteResponseDTO + AI summary
│   ├── NoteSearchResultDTO.java         # Search hit projection
│   ├── FlashcardDTO.java                # Record: question, answer
│   ├── GeminiRequest.java               # Outbound Gemini JSON shape
│   └── GeminiResponse.java              # Inbound Gemini JSON shape
│
├── model/                               # JPA entities
│   ├── Note.java                        # notes table
│   ├── Tag.java                         # tags table (many-to-many via note_tags)
│   └── Flashcard.java                   # flashcards table (FK → notes)
│
├── repository/                          # Spring Data JPA repositories
│   ├── NoteRepository.java              # Note CRUD + custom @Query
│   ├── TagRepository.java               # Tag CRUD + findByName
│   └── FlashcardRepository.java         # Flashcard CRUD + findBySourceNoteId
│
├── service/                             # Business logic
│   ├── NoteService.java                 # CRUD, search, activity stats, flashcard orchestration
│   ├── SearchService.java               # AI-relevance ranking for /ai/ask
│   ├── GeminiService.java               # All Gemini API calls (7 features)
│   └── RateLimitService.java            # In-memory hourly limiter
│
└── exception/                           # Exception types + handler
    ├── ResourceNotFoundException.java
    ├── GeminiApiException.java
    └── GlobalExceptionHandler.java       # @ControllerAdvice → 404/500/ai-error pages
```

```
src/main/resources/
├── application.properties               # Dev profile (H2, verbose logging)
├── application-prod.properties          # Prod profile (Postgres, quiet logging)
│
├── static/
│   ├── js/
│   │   ├── notes.js                     # Note form: counter, draft, tag/title suggest, confirm delete
│   │   ├── dashboard.js                 # Live search, filter bar, sort toggle
│   │   └── voice-notes.js               # Web Speech API integration + AI cleanup
│   └── (no CSS files — all styles inline in base.html)
│
└── templates/
    ├── layout/
    │   └── base.html                    # Global layout (navbar, sidebar, FAB)
    ├── fragments/
    │   └── note-card.html               # Reusable note tile
    ├── notes/
    │   ├── dashboard.html               # All notes + heatmap + filters
    │   ├── detail.html                  # Single note + AI actions
    │   ├── form.html                    # Create/edit form
    │   ├── search.html                  # Full search results
    │   └── fragments.html               # AJAX search dropdown
    ├── ai/
    │   ├── tools.html                   # Landing page (7 feature cards)
    │   ├── chat.html                    # Q&A chat UI
    │   └── flashcards.html              # Study mode UI
    └── error/
        ├── 404.html
        ├── 500.html
        └── ai-error.html
```

---

## 4. Data Model

### 4.1 Entity-Relationship Diagram

```
┌───────────────────────┐         ┌─────────────────┐
│ notes                 │         │ tags            │
├───────────────────────┤         ├─────────────────┤
│ id (PK, identity)     │◄────┐   │ id (PK)         │
│ title (200)           │     │   │ name (unique)   │
│ content (TEXT)        │     │   └─────────────────┘
│ folder                │     │            │
│ is_pinned             │     │            │
│ created_at            │     │   ┌────────▼────────┐
│ updated_at            │     │   │ note_tags       │
└───────────────────────┘     │   ├─────────────────┤
        ▲                     │   │ note_id (FK)    │
        │ ManyToOne           │   │ tag_id (FK)     │
        │ (source_note_id)    │   └─────────────────┘
┌───────┴───────────────┐     │   (M:N join table)
│ flashcards            │     │
├───────────────────────┤     │
│ id (PK)               │     │
│ question (TEXT)       │     │
│ answer (TEXT)         │     │
│ source_note_id (FK) ──┘     │
│ created_at            │
└───────────────────────┘
```

### 4.2 Entity field reference

#### `Note` (table: `notes`)
| Column | Type | Constraints |
| --- | --- | --- |
| id | BIGINT | PK, identity |
| title | VARCHAR(200) | NOT NULL |
| content | TEXT | NOT NULL |
| folder | VARCHAR(255) | NOT NULL, default "General" |
| is_pinned | BOOLEAN | NOT NULL, default false |
| created_at | TIMESTAMP | NOT NULL, set on @PrePersist |
| updated_at | TIMESTAMP | NOT NULL, refreshed on @PreUpdate |

#### `Tag` (table: `tags`)
| Column | Type | Constraints |
| --- | --- | --- |
| id | BIGINT | PK, identity |
| name | VARCHAR(255) | UNIQUE, NOT NULL |

#### `note_tags` (join table)
| Column | Type | Constraints |
| --- | --- | --- |
| note_id | BIGINT | FK → notes.id |
| tag_id | BIGINT | FK → tags.id |

#### `Flashcard` (table: `flashcards`)
| Column | Type | Constraints |
| --- | --- | --- |
| id | BIGINT | PK, identity |
| question | TEXT | NOT NULL |
| answer | TEXT | NOT NULL |
| source_note_id | BIGINT | FK → notes.id, NOT NULL |
| created_at | TIMESTAMP | NOT NULL, @PrePersist |

### 4.3 Cascade and fetch strategies
- `Note ↔ Tag`: `@ManyToMany` with `CascadeType.PERSIST, MERGE`. Tag deletion does not cascade to notes.
- `Flashcard → Note`: `@ManyToOne(fetch = FetchType.LAZY)`. Flashcards are deleted explicitly via `FlashcardRepository.deleteBySourceNoteId(...)` on regeneration; not via cascade.
- Schema is created/migrated automatically by Hibernate (`ddl-auto=update`).

---

## 5. REST API Endpoints

### 5.1 Page routes (return HTML via Thymeleaf)

| Method | Path | Controller method | View |
| --- | --- | --- | --- |
| GET | `/notes` | `NoteController.dashboard` | `notes/dashboard` |
| GET | `/notes/{id}` | `NoteController.detail` | `notes/detail` |
| GET | `/notes/new` | `NoteController.newForm` | `notes/form` |
| POST | `/notes` | `NoteController.create` | redirect → `/notes/{id}` |
| GET | `/notes/{id}/edit` | `NoteController.editForm` | `notes/form` |
| POST | `/notes/{id}/update` | `NoteController.update` | redirect → `/notes/{id}` |
| POST | `/notes/{id}/delete` | `NoteController.delete` | redirect → `/notes` |
| POST | `/notes/{id}/pin` | `NoteController.togglePin` | redirect → validated Referer |
| GET | `/notes/folder/{folderName}` | `NoteController.byFolder` | `notes/dashboard` |
| GET | `/notes/search?q=...` | `NoteController.search` | `notes/search` |
| GET | `/ai` | `AiController.aiTools` | `ai/tools` |
| GET | `/ai/chat` | `AiController.chat` | `ai/chat` |
| POST | `/ai/ask` | `AiController.ask` | `ai/chat` (with answer in model) |
| POST | `/ai/summarize/{folder}` | `AiController.summarize` | redirect → `/notes/folder/{folder}` |
| GET | `/ai/flashcards/{noteId}` | `AiController.viewFlashcards` | `ai/flashcards` |
| POST | `/ai/flashcards/{noteId}` | `AiController.generateFlashcards` | redirect → `/ai/flashcards/{noteId}` |

### 5.2 AJAX endpoints (return plain text or JSON)

| Method | Path | Body | Returns | Notes |
| --- | --- | --- | --- | --- |
| GET | `/notes/search?keyword=X&ajax=true` | — | HTML fragment (`notes/fragments :: searchResults`) | Live search dropdown |
| POST | `/ai/suggest-tags/{noteId}` | `content=...` (optional) | `text/plain` (comma-separated tags) | Pass `content` for new notes (noteId=0) |
| POST | `/ai/suggest-titles` | `content=...` | `application/json` (array of strings) | 3 titles |
| POST | `/ai/summarize/{noteId:[0-9]+}` | — | `text/plain` | Disambiguated from `/ai/summarize/{folder}` via regex |
| POST | `/ai/fix-grammar/{noteId}` | `content=...` (optional) | `text/plain` | Pass `content` for new notes |
| POST | `/ai/format-meeting/{noteId}` | — | `text/plain` (markdown) | |
| GET | `/ai/test` | — | `text/plain` (`Result: OK \| Time: Xms`) | Diagnostic; counts toward rate limit |

### 5.3 Routing nuances worth noting
- `POST /ai/summarize/{folder}` and `POST /ai/summarize/{noteId}` would collide on the same URL pattern. They're disambiguated using a regex path-variable constraint: `{noteId:[0-9]+}`. Numeric paths route to the AJAX `summarizeNote` handler; string paths to the folder-summarize handler.
- `POST /notes/{id}/pin` accepts a `Referer` header and validates it against the request's own host to avoid open redirects.

---

## 6. Service Layer Responsibilities

### `NoteService`
- All CRUD for `Note` and orchestration of `Tag` resolution
- `getAllNotes()` — repository call with `Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("updatedAt"))`
- `searchNotes(keyword)` — search by title/content (case-insensitive `LIKE`)
- `getActivityData()` — JPQL query grouped by `YEAR/MONTH/DAY(createdAt)`; returns `Map<LocalDate, Long>`
- `calculateCurrentStreak(activityData)` — counts consecutive prior days with at least one note (tolerates today being empty)
- `countNotesInLastDays(activityData, 30)` — sums entries within the trailing window
- `getNoteWithSummary(id)` — fetches note + invokes `GeminiService.summarizeNote` and builds `NoteDetailDTO`
- `fixNoteContent(id)`, `formatAsMeeting(id)` — content transforms (DB read + Gemini call, no save)
- `generateAndSaveFlashcards(noteId)` — `@Transactional`; deletes existing flashcards, calls Gemini, persists 5 new
- `getFlashcardsByNote(noteId)` — read-only fetch

### `SearchService`
- `searchNotes(keyword)` — UI-driven full-text search
- `getRelevantNotesForAI(question, maxNotes)` — extracts keywords > 3 chars, deduplicates results across keyword queries using a `LinkedHashMap`, sorts by updated date desc, limits to `maxNotes` (5 for `/ai/ask`)

### `GeminiService`
- Single private `call(prompt, fallback)` method centralizes:
  - **API key check** — throws `GeminiApiException` if blank (surface via `GlobalExceptionHandler`)
  - **Rate limit check** — returns `"AI limit reached for this hour. Try again later."` if quota exceeded
  - REST call via `RestTemplate.exchange`
  - Error handling: `HttpClientErrorException`, `ResourceAccessException`, generic — each logs and returns the `fallback` string
- Public methods:
  - `answerFromNotes(question, notes)` — builds notes block + question prompt
  - `summarizeFolder(notes, folderName)` — folder-level summary
  - `summarizeNote(content)` — single-note summary
  - `suggestTags(content)` — comma-separated tags
  - `suggestTitles(content)` — JSON array of 3 strings (parsed via Jackson)
  - `fixGrammarAndClarity(content)` — improved text
  - `formatMeetingNotes(rawNotes)` — markdown with `## Summary`, `## Key Decisions`, etc.
  - `generateFlashcards(content)` — JSON array of `FlashcardDTO`
- Defensive `stripHtml(text)` helper applied to all plain-text outputs — Gemini occasionally wraps responses in `<p>`/`<strong>` despite instructions; we strip those tags before returning

### `RateLimitService`
- `MAX_CALLS_PER_HOUR = 12`
- `synchronized tryAcquire()` — handles atomic rollover and increment
- `remaining()` — exposed for UI display (unused currently)

---

## 7. Gemini Integration Detail

### 7.1 Request shape
Outgoing payload uses Gemini's `:generateContent` shape:

```json
{
  "contents": [
    { "parts": [ { "text": "<prompt>" } ] }
  ]
}
```

Modeled as `GeminiRequest` with nested `Content` and `Part` records. URL is constructed as:

```
{apiUrl}?key={apiKey}
```

where `apiUrl` is `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`.

### 7.2 Response shape
Incoming `GeminiResponse` provides an `extractText()` convenience that navigates `candidates[0].content.parts[0].text`, returning `null` if any link in the chain is missing.

### 7.3 Failure modes and handling

| Failure | Handling |
| --- | --- |
| Missing/blank API key | Throws `GeminiApiException` → `error/ai-error` page (HTTP 503) |
| Rate limit exceeded (in-app) | Returns sentinel string, no HTTP call made |
| HTTP 4xx/5xx from Google | Logs status + response body; returns method's fallback string |
| `ResourceAccessException` (network/timeout) | Logs error; returns fallback |
| Generic `Exception` | Logs error class + message; returns fallback |
| JSON-returning methods receive non-JSON (or rate-limit string) | Returns method-specific default: `["Could not suggest titles"]` or empty flashcard list |

### 7.4 Prompt engineering choices
- All plain-text outputs explicitly request "PLAIN TEXT only — no HTML tags, no markdown, no code fences" as a defense against model auto-formatting.
- JSON outputs (`suggestTitles`, `generateFlashcards`) include an example structure and a defensive `stripCodeFences` regex client-side to handle ```json wrappers.
- `formatMeetingNotes` is the one exception — it **intentionally** returns markdown, since the UI renders it via a JS markdown-to-HTML transform.

---

## 8. Frontend Architecture

### 8.1 No SPA, no build tools
All frontend code is loaded as static assets:
- Bootstrap + Bootstrap Icons + Inter font — via CDN.
- Chart.js — via CDN, only on the dashboard.
- Three vanilla JS files (`notes.js`, `dashboard.js`, `voice-notes.js`) — loaded with `defer` in `base.html`.

### 8.2 Module responsibilities

**`notes.js`** (form pages)
- `initCharCounter` — character count under the textarea.
- `initAutoSaveDraft` — `localStorage` write every 30 s; restore prompt on empty form load.
- `initTagSuggestion` — POST to `/ai/suggest-tags/{noteId}` with content; populates the tags input.
- `initTitleSuggestion` — POST to `/ai/suggest-titles`; renders 3 clickable chips.
- `initConfirmDelete` — wires browser `confirm()` to delete forms (skipped inside modals).
- `initPrefillFromSession` — pre-fills title/content from `sessionStorage` (used by "Save as New Note" from the meeting-format flow).

**`dashboard.js`** (dashboard only)
- `initLiveSearch` — debounced 400 ms input → fetch `/notes/search?keyword=…&ajax=true` → inject HTML fragment into dropdown; cancel via `AbortController` on subsequent input.
- `initFilterBar` — All / Pinned / per-folder buttons (folders dynamically built from `data-folder` attrs on tiles).
- `initSortToggle` — re-sort `.note-col` elements in-place by reading `data-updated` or `data-title`.

**`voice-notes.js`** (form pages — Chrome/Edge only)
- Feature-detects `SpeechRecognition` / `webkitSpeechRecognition`.
- `SpeechRecognition` configured with `lang='en-IN'`, `continuous=true`, `interimResults=true`.
- On `onresult`: separates interim (gray italic preview) from final (appended to textarea).
- On manual stop: invokes `/ai/fix-grammar/{noteId}` with current content; shows a Bootstrap toast for Accept / Keep Original.
- Auto-restart on browser-side pauses (when `isRecording` is still true).

### 8.3 Inline scripts
A few features use page-local inline scripts (in `detail.html` and `flashcards.html`) because they're tightly coupled to that page's DOM:
- Summarize with AI, Fix Grammar diff, Meeting Format preview, Save-as-New-Note — all in `detail.html`.
- Flashcard study mode (navigation, show-answer, mark-known, keyboard shortcuts) — in `flashcards.html`.

---

## 9. Build and Deployment

### 9.1 Local build
```powershell
# Set the API key once (per user, persistent)
[System.Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "AIzaSy...", "User")

# Build + run with Maven wrapper
.\mvnw.cmd spring-boot:run
# → http://localhost:8081
```

### 9.2 Docker image
Multi-stage Dockerfile at the repo root:
- **Stage 1** (`maven:3.9-eclipse-temurin-17-alpine`): copies `pom.xml`, runs `mvn dependency:go-offline`, copies `src/`, runs `mvn clean package -DskipTests`.
- **Stage 2** (`eclipse-temurin:17-jre-alpine`): copies the JAR, exposes 8080, runs `java -Xmx200m -XX:+UseSerialGC -jar /app/app.jar`.
- Heap capped at 200 MB to fit Render's 512 MB free-tier limit comfortably.

### 9.3 Render deployment (current production)
- **Runtime**: Docker
- **Branch**: `main` — auto-deploy on every push
- **Environment variables** (set in the Render dashboard):
  - `SPRING_PROFILES_ACTIVE=prod`
  - `DB_URL=jdbc:postgresql://<neon-host>/neondb?sslmode=require`
  - `DB_USERNAME=neondb_owner`
  - `DB_PASSWORD=<from Neon panel>`
  - `GEMINI_API_KEY=<from aistudio.google.com>`
- **Hibernate `ddl-auto=update`** auto-creates the schema on first boot.

### 9.4 Java version pinning
`system.properties` at the repo root sets `java.runtime.version=17` for Render's native Java buildpack (used if Docker runtime is ever switched off).

---

## 10. Configuration Reference

### 10.1 Dev profile (`application.properties`)
| Property | Value | Purpose |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:h2:file:./data/notenest` | Persistent H2 file |
| `spring.h2.console.enabled` | `true` | `/h2-console` UI for inspection |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-create/migrate schema |
| `spring.jpa.show-sql` | `true` | SQL in logs |
| `server.port` | `8081` | Avoids common 8080 conflicts |
| `logging.level.com.notenest.service.GeminiService` | `DEBUG` | Surface Gemini request URLs |
| `gemini.api.key` | `${GEMINI_API_KEY:}` | Pulled from env (empty fallback) |
| `gemini.api.url` | `…gemini-2.5-flash:generateContent` | API endpoint |

### 10.2 Prod profile (`application-prod.properties`)
| Property | Value | Purpose |
| --- | --- | --- |
| `spring.datasource.url` | `${DB_URL}` | Postgres JDBC URL |
| `spring.datasource.username` | `${DB_USERNAME}` | |
| `spring.datasource.password` | `${DB_PASSWORD}` | |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | |
| `spring.jpa.database-platform` | `org.hibernate.dialect.PostgreSQLDialect` | |
| `spring.datasource.hikari.maximum-pool-size` | `5` | Conservative for free-tier connection cap |
| `spring.jpa.hibernate.ddl-auto` | `update` | Hands-off schema management |
| `spring.h2.console.enabled` | `false` | Disabled in prod |
| `server.port` | `${PORT:8080}` | Render injects `PORT` |
| `server.error.include-stacktrace` | `never` | Don't leak stack traces |
| `logging.level.root` | `INFO` | Quieter logs |

---

## 11. Testing Strategy

### 11.1 Test files
- `NoteServiceTest` — `@ExtendWith(MockitoExtension.class)`, mocks `NoteRepository` + `TagRepository`. Covers: createNote tag resolution (new + existing), updateNote field updates, togglePin both directions, getAllNotes sort verification via `ArgumentCaptor<Sort>`, error cases for missing IDs.
- `SearchServiceTest` — Mocks repository. Covers: blank-keyword guards, getRelevantNotesForAI deduplication, max-notes limiting, short-keyword filtering, updated-at sort.
- `GeminiServiceTest` — Mocks `RestTemplate` and uses `ReflectionTestUtils.setField` to inject `@Value` fields. Covers: prompt structure verification via `ArgumentCaptor<HttpEntity>`, response parsing, fallback strings on `HttpClientErrorException` / `ResourceAccessException`, URL/header construction.
- `NoteControllerTest` — `@WebMvcTest(NoteController.class)`, mocks all dependencies. Covers: dashboard render, valid POST redirects to `/notes/{id}`, validation errors keep user on form, delete redirects to dashboard, togglePin redirects.
- `AiControllerTest` — `@WebMvcTest(AiController.class)`. Covers: chat view, ask invokes both services and surfaces the answer, suggest-tags routes correctly between content-param and DB lookup paths.

### 11.2 Running tests
```bash
mvn test
```

---

## 12. Security Considerations

### Already addressed
- **No secrets in source/git history** — verified via repository scan.
- **HTML escaping** in templates uses Thymeleaf's auto-escaping `th:text`.
- **Search highlighting** escapes `&<>` in `NoteController.highlight()` before injecting `<strong>`.
- **Markdown rendering** for meeting format also escapes input before transforming.
- **Open-redirect prevention** — `togglePin` validates the `Referer` header host against the request host.
- **No SQL injection** — all queries use Spring Data parameter binding.
- **HTTPS** enforced by Render in production.
- **API key never logged** — only the URL (without query string) is logged at DEBUG.
- **Stack traces hidden** in production responses.

### Known limitations (by design)
- **No authentication** — single-user public app. Anyone with the URL can CRUD all notes.
- **No CSRF protection** — no Spring Security, so state-changing endpoints rely on same-origin convention.
- **In-memory rate limit only** — restart resets the counter.
- **`/ai/test` endpoint** reveals whether the Gemini key works (acceptable as a diagnostic).

---

## 13. Performance Considerations

### Cold-start latency
Render's free tier sleeps after 15 minutes idle. First request after sleep wakes the container (~30 s). The Docker image is optimized to minimize this:
- Multi-stage build keeps the runtime image small (~200 MB total).
- Heap is capped at 200 MB; the rest of the 512 MB container is available for OS + class loading.
- `XX:+UseSerialGC` is used (single-threaded GC) for lower memory overhead on a 0.1-vCPU instance.

### Database round-trips
- Dashboard load issues approximately:
  - 1 SELECT for notes (with Sort)
  - 1 SELECT for all folders
  - 1 SELECT for all tags
  - 1 SELECT for pinned notes
  - 1 SELECT for folder-counts
  - 1 SELECT for activity data (the new JPQL group-by)
- Total: ~6 queries per dashboard request. Acceptable for personal scale; would need N+1 fixes (`@EntityGraph` or DTO projections) for higher load.

### AI call cost (latency + quota)
- Each Gemini call: 1–5 seconds typical, dominated by model latency.
- Rate limit caps usage at 12 calls/hour to stay well within the free tier's daily quota.

---

## 14. Logging and Monitoring

### Application logs
- **Dev**: `INFO` for app code, `DEBUG` for `GeminiService` (surfaces request URLs but not keys), full SQL output via `spring.jpa.show-sql=true`.
- **Prod**: `INFO` root, `WARN` for Hibernate SQL.

### What's logged
- All Gemini failures with status + response body (no secrets in body).
- Rate limit exceeded events at `WARN`.
- Unhandled exceptions at `ERROR` via `GlobalExceptionHandler`.

### What's not logged
- API keys
- Database credentials
- User-supplied content (only structural events)

### Production monitoring
- **Render Logs** tab — streaming log access.
- **Neon Console** — DB-side query metrics + suspend/wake events.
- No external APM (New Relic, Datadog, etc.) — out of scope for v1.

---

## 15. Design Decisions and Rationale

### Why Spring Boot + Thymeleaf instead of React + REST?
The application is a personal tool with no client-side complexity beyond progressive enhancements. Server-rendered Thymeleaf provides:
- Faster initial page load (no client-side hydration)
- Simpler deployment (single artifact, no separate frontend build)
- Better SEO and accessibility out of the box
- Lower hosting cost on free tiers

A React SPA would have added build complexity, slower cold starts, and no functional benefit for this use case.

### Why Gemini 2.5-flash specifically?
- Free tier covers personal use without billing setup.
- Fast inference (under 3 s typical) — important for interactive AI features.
- Strong instruction-following — handles the "return plain text only" constraint reliably.
- 1.5-flash was the original choice but was deprecated mid-2026; 2.5-flash is the current recommendation.

### Why no Spring Security?
- Single-user personal app.
- Render's HTTPS + obscure subdomain provide sufficient practical security.
- Adding Spring Security would require user registration, password storage, sessions, CSRF tokens — significant scope that doesn't match the product's purpose.

### Why in-memory rate limiting instead of Redis?
- Single-instance deployment on Render.
- Personal use — quota is bound by Gemini's free tier, not horizontal scale.
- Adding Redis would require external dependency provisioning, which contradicts the "personal app" simplicity goal.

### Why H2 in dev, Postgres in prod?
- H2 file-based is zero-config — `git clone && mvn spring-boot:run` works immediately.
- Postgres in prod matches Neon's offering and is the natural choice for hosted SQL.
- Profile-based switching keeps both options first-class.

---

## 16. Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 1.0 | 2026-05-20 | Prachi Dnyaneshwar Parate | Initial release covering current production state. |

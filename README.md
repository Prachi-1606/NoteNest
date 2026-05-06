# NoteNest

A modern, AI-powered note-taking web application built with Spring Boot. Organize your notes into folders, tag them, pin the important ones, and let Google's Gemini AI help you summarize folders, suggest tags, and answer questions across your entire knowledge base.

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.4.5 |
| Web | Spring MVC + Thymeleaf 3 |
| Persistence | Spring Data JPA + Hibernate |
| Database (dev) | H2 (file-based, persists across restarts) |
| Database (prod) | MySQL 8 |
| Validation | Jakarta Bean Validation |
| Frontend | Bootstrap 5.3 + Bootstrap Icons + vanilla JS |
| AI | Google Gemini 1.5 Flash via REST |
| HTTP Client | Spring `RestTemplate` |
| Build | Maven 3.x |
| Testing | JUnit 5, Mockito, Spring MockMvc |
| Boilerplate | Lombok |

---

## Features

### Note management
- Create, edit, delete notes with rich content
- Organize into folders with auto-suggested folder names
- Tag notes with comma-separated tags (reused across notes)
- Pin notes to the top of every view
- Word count and read time estimate on the detail page

### Search & discovery
- Full-text search across titles and content (case-insensitive)
- Live search dropdown with debounced input (400 ms)
- Per-folder and per-tag filtering on the dashboard
- Client-side sort: last updated or title A–Z

### AI features (Google Gemini)
- **Ask AI**: ask any question about your notes; the answer is grounded in the most relevant matches
- **Summarize folder**: one-click summary of every note in a folder
- **Suggest tags**: AI-powered tag suggestions based on note content (works for new notes too)

### UX polish
- Auto-save drafts to `localStorage` every 30 seconds — restore prompt if the browser closes
- Live character counter on the editor
- Confirm-before-delete dialog
- Custom 404 / 500 error pages
- Sticky navbar with global search
- Sidebar with All Notes, Folders (with counts), Tags, and Pinned
- Floating "Ask AI" button on every page

---

## Running locally

### 1. Prerequisites
- Java 17 (`java --version`)
- Maven 3.6+ (`mvn --version`)
- A free Google AI Studio API key (see below)

### 2. Get a free Gemini API key

1. Open <https://aistudio.google.com/app/apikey> and sign in with a Google account.
2. Click **Create API key** (you can use an existing Google Cloud project or let it create a new one).
3. Copy the generated key — it starts with `AIza...`.
4. Open `src/main/resources/application.properties` and replace the value of `gemini.api.key`:

   ```properties
   gemini.api.key=AIzaSy_paste_your_key_here
   ```

   The free tier on `gemini-1.5-flash` is generous (multiple requests per minute and thousands per day) — plenty for personal use.

### 3. Build and run

```bash
git clone <your-fork-url> notenest
cd notenest

# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

The app starts on **<http://localhost:8081>**. The H2 database file is created at `./data/notenest.mv.db` and persists across restarts.

### 4. (Optional) Browse the H2 console

Visit <http://localhost:8081/h2-console> with:
- JDBC URL: `jdbc:h2:file:./data/notenest`
- User: `sa`
- Password: *(empty)*

### 5. Verify the AI integration

Visit <http://localhost:8081/ai/test> — you should see `Result: OK | Time: <ms>`. If you see `Result: FAILED`, check the application log for the underlying error (most often an invalid or rate-limited API key).

---

## Running in production (MySQL)

The repo ships with `application-prod.properties` for a MySQL deployment. Set the following environment variables, then run with the `prod` profile:

```bash
export DB_URL="jdbc:mysql://your-db-host:3306/notenest?useSSL=true&serverTimezone=UTC"
export DB_USERNAME="notenest_app"
export DB_PASSWORD="********"
export GEMINI_API_KEY="AIzaSy..."

java -jar target/notenest-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

The prod profile expects the schema to already exist (`ddl-auto=validate`). Generate it with `ddl-auto=create` once on a staging DB, dump it, and apply via Flyway/Liquibase or your usual migration tooling.

You'll also need to add the MySQL driver to `pom.xml`:

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Running the tests

```bash
mvn test
```

The test suite covers:
- `NoteServiceTest` — CRUD, tag resolution, pin toggle, sort order
- `SearchServiceTest` — keyword extraction, deduplication, result limiting
- `GeminiServiceTest` — prompt construction, response parsing, fallback behavior
- `NoteControllerTest` — `@WebMvcTest` for dashboard, create/update/delete flows
- `AiControllerTest` — `@WebMvcTest` for chat, ask, suggest-tags

---

## Screenshots

> _Drop screenshots into `docs/screenshots/` and reference them here._

| View | Screenshot |
| --- | --- |
| Dashboard | `docs/screenshots/dashboard.png` |
| Note detail | `docs/screenshots/detail.png` |
| Ask AI | `docs/screenshots/ai-chat.png` |
| Folder summary | `docs/screenshots/summary.png` |
| Live search | `docs/screenshots/search.png` |

---

## Project structure

```
src/main/java/com/notenest/
├── NoteNestApplication.java
├── config/          # RestTemplate, WebConfig, DataSeeder
├── controller/      # NoteController, AiController
├── dto/             # Request/Response DTOs + Gemini API DTOs
├── exception/       # ResourceNotFoundException, GlobalExceptionHandler
├── model/           # Note, Tag (JPA entities)
├── repository/      # NoteRepository, TagRepository
└── service/         # NoteService, SearchService, GeminiService

src/main/resources/
├── application.properties        # dev profile (H2)
├── application-prod.properties   # prod profile (MySQL)
├── static/                       # CSS, JS
└── templates/                    # Thymeleaf views
    ├── ai/        # AI chat
    ├── error/     # 404, 500
    ├── fragments/ # note-card
    ├── layout/    # base layout
    └── notes/     # dashboard, detail, form, search
```

---

## Troubleshooting

- **AI returns "I couldn't find an answer in your notes."** — visit `/ai/test`. A fast `FAILED` (< 500 ms) means the API key was rejected; regenerate it at Google AI Studio. A slow `FAILED` usually means a network/proxy issue.
- **Port 8081 already in use** — change `server.port` in `application.properties`.
- **H2 lock file** — if the app crashes, delete `./data/notenest.mv.db.lock.db` before restarting.

---

## License

MIT — do whatever you want with it.

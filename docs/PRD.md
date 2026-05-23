# NoteNest — Project Requirement Document (PRD)

| Field | Value |
| --- | --- |
| **Project Name** | NoteNest |
| **Document Type** | Project Requirements Document |
| **Version** | 1.0 |
| **Date** | 2026-05-20 |
| **Author** | Prachi Dnyaneshwar Parate |
| **Status** | Released — deployed on Render |

---

## 1. Executive Summary

NoteNest is a modern, AI-powered note-taking web application built with Spring Boot 3 and Thymeleaf. It allows a single user to create, organize, and search personal notes — augmented by seven Google Gemini AI features that summarize, transform, and generate content from those notes. The application is deployed on Render's free tier with a managed PostgreSQL database on Neon.

The product is designed as a personal productivity tool and portfolio showcase that demonstrates full-stack development competence across backend (Spring MVC, JPA, REST), frontend (Thymeleaf, Bootstrap, vanilla JS), AI integration (Gemini REST API), database design (PostgreSQL with auto-generated schema), and DevOps (Docker, environment-based configuration, CI-free continuous deployment).

---

## 2. Goals and Objectives

### Primary Goals
1. Provide a clean, modern interface for capturing and organizing personal notes.
2. Demonstrate practical AI integration — not as a gimmick, but as functional enhancements (summarization, grammar correction, flashcards for studying, etc.).
3. Serve as a deployable, end-to-end portfolio project showcasing full-stack engineering skills.

### Success Metrics
- Application runs on the Render free tier without breaking quotas.
- All seven AI features return useful, non-error responses for typical note content.
- Page-load times remain under 2 seconds on the free tier (excluding cold-start wake).
- Code passes all unit and integration tests.
- Zero secrets committed to the public Git repository.

---

## 3. Target Users

### Primary Persona — Personal Knowledge Worker
- **Profile**: An individual (student, developer, professional) who takes notes during meetings, study sessions, or daily work.
- **Pain points**: Notes accumulate quickly; finding the right one later is hard; rewriting messy notes is tedious; summarizing a folder of related notes is manual work.
- **Goals**: Capture ideas quickly, retrieve them by search/folder/tag, and use AI to clean up or reformat content without leaving the app.

### Secondary Persona — Portfolio Reviewer
- **Profile**: A recruiter, hiring manager, or fellow developer evaluating the author's skills.
- **Goals**: Understand the project's scope and quality within a few minutes; verify that the code is well-structured, tested, and production-ready.

---

## 4. Functional Requirements

### 4.1 Note Management (FR-NOTE)

| ID | Requirement |
| --- | --- |
| FR-NOTE-01 | The system shall allow a user to create a note with a title (required, max 200 chars), content (required), folder (optional, defaults to "General"), tags (optional, comma-separated), and pin state. |
| FR-NOTE-02 | The system shall display all notes on a dashboard, sorted with pinned notes first, then by last-updated descending. |
| FR-NOTE-03 | The system shall allow the user to edit any existing note, updating title, content, folder, tags, or pin state. |
| FR-NOTE-04 | The system shall allow the user to delete a note with a confirmation dialog. |
| FR-NOTE-05 | The system shall allow the user to toggle the pin state of any note from either the dashboard or the detail view. |
| FR-NOTE-06 | The system shall display word count and an estimated read time (words ÷ 200) on the note detail page. |
| FR-NOTE-07 | The system shall support tags as reusable entities — multiple notes can share the same tag, and tag names are deduplicated case-sensitively. |

### 4.2 Search & Discovery (FR-SEARCH)

| ID | Requirement |
| --- | --- |
| FR-SEARCH-01 | The system shall provide a full-text search across note titles and content (case-insensitive). |
| FR-SEARCH-02 | The system shall provide a live (AJAX-based) search dropdown in the navbar, debounced at 400 ms, displaying up to six results with title, folder, and matched tags. |
| FR-SEARCH-03 | The system shall highlight matched keywords in search result previews. |
| FR-SEARCH-04 | The system shall provide client-side filter buttons on the dashboard for "All", "Pinned", and per-folder views. |
| FR-SEARCH-05 | The system shall provide a client-side sort dropdown on the dashboard for "Last updated" and "Title A–Z". |
| FR-SEARCH-06 | The system shall display per-folder note counts in the sidebar. |
| FR-SEARCH-07 | The system shall display an activity heatmap on the All Notes dashboard showing notes-created-per-day for the last 30 days, plus a "current streak" counter. |

### 4.3 AI Features (FR-AI)

All AI features are powered by Google Gemini 2.5-flash via REST.

| ID | Requirement |
| --- | --- |
| FR-AI-01 | **Q&A from Notes** — the user shall be able to ask a natural-language question and receive an answer grounded in their most relevant notes (top 5 by keyword match). |
| FR-AI-02 | **Note Summarizer** — the user shall be able to generate a 3–4 sentence summary of any individual note. |
| FR-AI-03 | **Folder Summarizer** — the user shall be able to generate a structured summary of all notes within a specific folder. |
| FR-AI-04 | **Tag Suggester** — when creating or editing a note, the user shall be able to request 3–5 AI-suggested tags based on the note's content. |
| FR-AI-05 | **Title Suggester** — when creating or editing a note, the user shall be able to request three creative title suggestions presented as clickable chips. |
| FR-AI-06 | **Grammar Fixer** — the user shall be able to request grammar and clarity improvements for any note, presented as a side-by-side diff with Accept / Dismiss actions. |
| FR-AI-07 | **Meeting Formatter** — the user shall be able to convert any note's raw content into a structured markdown format with Summary, Key Decisions, Action Items, and Next Steps sections. The result is shown as a preview with an option to save as a new note. |
| FR-AI-08 | **Flashcard Generator** — the user shall be able to auto-generate five question/answer flashcards from any note for active recall study, with a built-in study mode (Show Answer, Mark as Known, Previous/Next, keyboard shortcuts). |
| FR-AI-09 | The system shall enforce an in-application rate limit of **12 Gemini API calls per hour** per JVM instance. Calls exceeding the limit shall return the message "AI limit reached for this hour. Try again later." without invoking the API. |
| FR-AI-10 | The system shall provide a unified `/ai` landing page listing all seven AI features as discoverable cards. |

### 4.4 Voice Input (FR-VOICE)

| ID | Requirement |
| --- | --- |
| FR-VOICE-01 | The system shall provide a microphone button on the note form (new/edit) to dictate note content using the browser's built-in Web Speech API. |
| FR-VOICE-02 | The system shall display a live interim transcript below the textarea while the user is speaking, and append finalized transcripts to the textarea. |
| FR-VOICE-03 | The system shall automatically invoke the AI Grammar Fixer on the dictated content when the user stops recording, presenting the cleaned-up version as a Bootstrap toast with Accept / Keep Original actions. |
| FR-VOICE-04 | The system shall gracefully degrade in browsers without Web Speech API support (Firefox, Safari) by disabling the microphone button and showing a tooltip "Requires Chrome or Edge". |

### 4.5 Configuration & Error Handling (FR-CONFIG)

| ID | Requirement |
| --- | --- |
| FR-CONFIG-01 | The system shall load the Gemini API key from the `GEMINI_API_KEY` environment variable in both dev and prod profiles. |
| FR-CONFIG-02 | The system shall switch between H2 (dev) and PostgreSQL (prod) database backends based on the active Spring profile. |
| FR-CONFIG-03 | The system shall display a custom 404 page when a requested note ID does not exist. |
| FR-CONFIG-04 | The system shall display a custom 500 page for unhandled server errors. |
| FR-CONFIG-05 | The system shall display a dedicated AI-error page when the Gemini API key is missing or misconfigured. |
| FR-CONFIG-06 | The system shall not include stack traces in production error responses. |

---

## 5. Non-Functional Requirements

### 5.1 Performance
- Dashboard render time: ≤ 2 seconds on the Render free tier (after cold-start wake).
- AI feature response time: ≤ 5 seconds per call (Gemini API + network).
- Live search dropdown: results visible within 600 ms of user input (400 ms debounce + ~200 ms server response).

### 5.2 Usability
- Responsive layout supporting screens from 360 px (mobile) to 1920 px (desktop) wide.
- All destructive actions (Delete, Accept Changes) require explicit user confirmation.
- AI features show loading spinners during the API call to set expectations.

### 5.3 Security
- No secrets (API keys, DB passwords) committed to the source repository.
- All form posts are protected against open-redirect attacks (Referer validated against same origin).
- All user-supplied content rendered in templates is HTML-escaped (Thymeleaf default `th:text`).
- Production HTTPS enforced via Render's managed TLS.

### 5.4 Reliability
- Gemini API failures (network, quota, auth) shall not crash the application — they fall back to user-friendly default strings.
- A missing Gemini API key shall surface as a dedicated error page rather than a generic 500.

### 5.5 Maintainability
- Code organized by layer: `controller` / `service` / `repository` / `model` / `dto` / `exception` / `config`.
- All public service methods covered by unit tests (Mockito).
- Controllers covered by `@WebMvcTest` integration tests.

### 5.6 Portability
- Application packaged as a single executable JAR.
- Configurable entirely through environment variables — no source changes required to switch deployment targets.
- Docker image build supported for deployment on container platforms.

---

## 6. User Stories

### Epic 1 — Note Capture
- **US-01**: As a user, I want to write a new note quickly so I can capture an idea before I forget it.
- **US-02**: As a user, I want to dictate notes using my voice so I can capture content hands-free.
- **US-03**: As a user, I want my draft auto-saved so I don't lose work if my browser crashes.

### Epic 2 — Note Organization
- **US-04**: As a user, I want to organize notes into folders so related notes stay together.
- **US-05**: As a user, I want to tag notes so I can find them across folder boundaries.
- **US-06**: As a user, I want to pin important notes so they always appear first.

### Epic 3 — Note Retrieval
- **US-07**: As a user, I want to search notes by keyword so I can find what I wrote weeks ago.
- **US-08**: As a user, I want a live search preview while typing so I don't have to navigate to a separate page.
- **US-09**: As a user, I want to filter notes by folder or pinned state without a page reload.

### Epic 4 — AI Assistance
- **US-10**: As a user, I want a summary of a long note so I can refresh my memory quickly.
- **US-11**: As a user, I want AI to fix the grammar of my dictated notes so they read professionally.
- **US-12**: As a user, I want AI to suggest titles when I'm stuck so I can keep writing.
- **US-13**: As a user, I want AI to generate flashcards from study notes so I can do active recall.
- **US-14**: As a user, I want to ask questions in plain English about all my notes so I can use them as a personal knowledge base.

### Epic 5 — Insights
- **US-15**: As a user, I want to see my note-taking activity over time so I stay motivated by visible progress.

---

## 7. Out of Scope (v1)

- **Multi-user accounts / authentication**: NoteNest v1 is a single-user application. Spring Security and user management are not implemented.
- **Real-time collaboration**: No multi-user editing, comments, or sharing.
- **Mobile applications**: Web-only. The interface is responsive but no native iOS/Android apps.
- **File attachments**: Notes are text-only. Image, PDF, and file uploads are not supported.
- **Rich-text editor**: Content is plain text. No WYSIWYG formatting.
- **Export functionality**: No bulk export to PDF, Markdown files, or other formats.
- **Offline support**: No service worker, no offline mode.
- **Analytics / external monitoring**: No integration with analytics platforms.

---

## 8. Assumptions and Dependencies

### Assumptions
- The user has a Google account to obtain a free Gemini API key.
- The user has a modern browser (Chrome, Edge, Firefox, Safari) released within the last two years.
- Voice notes are used only in Chrome or Edge (per Web Speech API support).
- The free tier of Gemini 2.5-flash (multiple requests per minute, thousands per day) is sufficient for personal use.

### External Dependencies
- **Google Gemini API** — required for all AI features.
- **Neon PostgreSQL** — production database.
- **Render** — production hosting platform.
- **Bootstrap 5.3** — CSS framework (loaded via CDN).
- **Bootstrap Icons** — icon set (loaded via CDN).
- **Inter font** — typography (loaded from Google Fonts).
- **Chart.js 4.4** — activity heatmap rendering (loaded via CDN, only on the dashboard).

---

## 9. Acceptance Criteria

The product is considered complete when:

1. ✅ A user can create, view, edit, delete, pin, search, and filter notes through the web UI.
2. ✅ All seven AI features (Q&A, Summarize, Tag Suggest, Title Suggest, Grammar Fix, Meeting Format, Flashcards) are functional end-to-end.
3. ✅ Voice notes work in Chrome and Edge with live transcription and AI cleanup.
4. ✅ The activity heatmap renders accurately for the last 30 days.
5. ✅ The application is deployed and accessible on the public internet.
6. ✅ Unit and integration tests pass.
7. ✅ No secrets are committed to the Git repository.
8. ✅ The application gracefully handles Gemini API failures and rate limits.
9. ✅ Custom 404, 500, and AI-error pages are shown for the relevant error conditions.
10. ✅ Documentation (README, PRD, Technical Design) is up to date.

---

## 10. Future Enhancements (Backlog)

- **Markdown rendering** in note content (currently plain text with line breaks preserved).
- **Persistent "Known" state** for flashcards (currently client-side only — lost on page refresh).
- **Per-user accounts** with Spring Security + OAuth (Google login).
- **Note sharing** via short URLs.
- **Export to PDF / Markdown** bulk action.
- **Dark mode** toggle.
- **Spaced-repetition scheduling** for flashcards (SM-2 algorithm).
- **Browser extension** for clipping web content into notes.
- **Distributed rate limiting** using Redis (for multi-instance deployments).
- **GitHub Actions CI** running tests on every push.
- **Activity heatmap** as a true year-long GitHub-style grid (currently a 30-day bar chart).

---

## 11. Revision History

| Version | Date | Author | Changes |
| --- | --- | --- | --- |
| 1.0 | 2026-05-20 | Prachi Dnyaneshwar Parate | Initial release of PRD covering all implemented features. |

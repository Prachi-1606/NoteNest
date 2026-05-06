package com.notenest.config;

import com.notenest.model.Note;
import com.notenest.model.Tag;
import com.notenest.repository.NoteRepository;
import com.notenest.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (noteRepository.count() > 0) {
            log.info("DataSeeder: database already seeded, skipping.");
            return;
        }

        log.info("DataSeeder: seeding sample notes and tags...");

        Tag springBoot  = tag("spring-boot");
        Tag java        = tag("java");
        Tag backend     = tag("backend");
        Tag journal     = tag("journal");
        Tag personal    = tag("personal");
        Tag goals       = tag("goals");
        Tag study       = tag("study");
        Tag algorithms  = tag("algorithms");
        Tag database    = tag("database");
        Tag productivity = tag("productivity");
        Tag ideas       = tag("ideas");


        List<Note> notes = List.of(

            // ── Work ─────────────────────────────────────────────────────────
            note("Spring Boot 3 Migration Guide",
                """
                Key changes when migrating from Spring Boot 2.x to 3.x:
                - Replaced javax.* imports with jakarta.*
                - Spring Security 6 requires SecurityFilterChain bean instead of WebSecurityConfigurerAdapter
                - Actuator endpoints now require explicit exposure via management.endpoints.web.exposure.include
                - Hibernate 6 changes: @Column(columnDefinition) still works but some HQL syntax differs
                - Use spring-boot-starter-parent 3.4.x for latest stable baseline
                Action: update all POM dependencies and run full regression suite.
                """,
                "Work", true, springBoot, java),

            note("REST API Design Checklist",
                """
                Before shipping any new REST endpoint, verify:
                1. Proper HTTP verb (GET reads, POST creates, PUT replaces, PATCH partial update, DELETE removes)
                2. Meaningful status codes — 201 for creation, 204 for no-content deletes
                3. Pagination on any list endpoint (page + size params)
                4. Input validation via @Valid and a global @ControllerAdvice error handler
                5. API versioning strategy agreed with team (/v1/ prefix currently)
                6. OpenAPI / Swagger doc updated
                7. Integration test covering happy path + at least one error case
                """,
                "Work", true, backend, java),

            note("JPA Performance Tips",
                """
                Common JPA pitfalls and fixes encountered this sprint:
                - N+1 problem: use @EntityGraph or JOIN FETCH in JPQL to eager-load associations only when needed
                - Avoid loading entire entities for read-only views — use projections or @NamedNativeQuery
                - Enable second-level cache with Caffeine for reference data (tags, categories)
                - Set spring.jpa.open-in-view=false to avoid lazy-load surprises in controllers
                - Use batch inserts: spring.jpa.properties.hibernate.jdbc.batch_size=30
                Profiling tool: p6spy or datasource-proxy for query logging in dev.
                """,
                "Work", false, database, springBoot),

            note("Weekly Sprint Review — Week 18",
                """
                Completed:
                - Implemented note search endpoint with keyword + folder filtering
                - Fixed tag deduplication bug on note save
                - Added pagination to the /notes list API

                In Progress:
                - Thymeleaf UI for note listing and detail view (60% done)
                - DataSeeder polish for dev profile

                Blockers:
                - Waiting for UX sign-off on the folder sidebar design

                Next week: complete UI, write integration tests, deploy to staging.
                """,
                "Work", false, productivity, backend),

            note("Team Onboarding Notes",
                """
                New developer setup checklist for the NoteNest project:
                1. Install JDK 17 (Eclipse Temurin recommended)
                2. Clone repo and run `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
                3. H2 console available at http://localhost:8080/h2-console (JDBC URL: jdbc:h2:file:./data/notenest)
                4. Lombok plugin required in IDE (IntelliJ: Settings > Plugins > Lombok)
                5. Code style: Google Java Format enforced via pre-commit hook
                6. PR template in .github/PULL_REQUEST_TEMPLATE.md — fill it out completely
                Reach out to team lead for Linear board access and Slack channel invites.
                """,
                "Work", false, java, productivity),

            // ── Personal ──────────────────────────────────────────────────────
            note("Morning Pages — May 1",
                """
                Woke up at 6:30, sky was overcast but the air felt fresh.
                Had coffee on the balcony and thought about how much energy I waste on low-priority tasks.
                Started experimenting with time-blocking this week — it genuinely helps to see the day as fixed slots.
                Grateful for: the new project finally getting traction, a good workout yesterday, a long call with an old friend.
                One thing I want to be more intentional about: reading before bed instead of scrolling.
                """,
                "Personal", false, journal, personal),

            note("2025 Goals Tracker",
                """
                Health:
                ✅ Run 3x per week (holding steady since February)
                ⬜ Complete a 10K race — registered for June event
                ⬜ Sleep by 11pm consistently (work in progress)

                Learning:
                ✅ Finish Spring Boot deep-dive course
                ⬜ Read 12 books this year (currently at 4/12)
                ⬜ Complete AWS Solutions Architect exam prep

                Personal Finance:
                ✅ Build 3-month emergency fund
                ⬜ Start SIP for long-term investment goal

                Review monthly — last reviewed: April 28.
                """,
                "Personal", true, goals, personal),

            note("Book Notes — Atomic Habits",
                """
                Core idea: small 1% improvements compound dramatically over time.

                The Four Laws of Behavior Change:
                1. Make it obvious — environment design matters more than motivation
                2. Make it attractive — temptation bundling (pair what you need to do with what you want to do)
                3. Make it easy — reduce friction; two-minute rule for starting habits
                4. Make it satisfying — immediate reward reinforces the loop

                Personal application:
                - Placed running shoes next to the bed (cue)
                - Prep tomorrow's work tasks the night before (reduce friction)
                - Habit stack: after morning coffee → 10 minutes of reading

                Re-read chapter 5 on identity-based habits — very powerful framing.
                """,
                "Personal", false, personal, goals),

            note("Weekend Trip Ideas",
                """
                Places shortlisted for a quick getaway:

                1. Coorg (2-3 days) — coffee estates, Abbey Falls, misty roads
                   Best time: post-monsoon (Oct–Feb)

                2. Hampi (3 days) — ancient ruins, boulder landscapes, Virupaksha Temple
                   Tip: rent a bicycle for the ruins circuit

                3. Pondicherry (2 days) — French Quarter, Auroville, beach sunrise
                   Stay: heritage homestay near the promenade

                Budget estimate per trip: ₹8,000–₹12,000 including travel, stay, food.
                Next planned trip: Coorg in October with college friends group.
                """,
                "Personal", false, personal, ideas),

            note("Grocery & Meal Prep Plan",
                """
                Weekly meal prep (Sunday batch cooking):
                - Overnight oats (5 jars) — base: oats, chia, almond milk; toppings vary
                - Brown rice + roasted veggies for weekday lunches
                - Dal tadka in bulk — freezes well

                Grocery list:
                Oats, chia seeds, almond milk, spinach, bell peppers, carrots, onions, tomatoes,
                masoor dal, garlic, ginger, olive oil, Greek yogurt, eggs, bananas, apples.

                Cost estimate: ₹1,200/week. Save time by chopping all veggies at once.
                """,
                "Personal", false, personal, productivity),

            // ── Study ─────────────────────────────────────────────────────────
            note("Data Structures — Trees & Graphs",
                """
                Binary Search Tree (BST):
                - Insert, search, delete: O(log n) average, O(n) worst (degenerate tree)
                - In-order traversal of BST yields sorted output

                Balanced trees: AVL and Red-Black maintain O(log n) by rotation

                Graph representations:
                - Adjacency matrix: O(V²) space, O(1) edge lookup
                - Adjacency list: O(V+E) space, better for sparse graphs

                BFS vs DFS:
                - BFS: shortest path in unweighted graph, uses queue, O(V+E)
                - DFS: cycle detection, topological sort, uses stack/recursion, O(V+E)

                Practice problems done: 5 BST problems on LeetCode, 3 graph BFS/DFS.
                Next: Dijkstra's algorithm and dynamic programming on trees.
                """,
                "Study", true, study, algorithms),

            note("SQL & Database Design",
                """
                Normalization recap:
                - 1NF: atomic values, no repeating groups
                - 2NF: no partial dependencies (all non-keys depend on entire PK)
                - 3NF: no transitive dependencies

                Indexing strategy:
                - Index columns used in WHERE, JOIN ON, and ORDER BY
                - Composite index column order: most selective column first
                - EXPLAIN ANALYZE is your friend before optimising

                ACID properties:
                Atomicity — all or nothing
                Consistency — constraints always satisfied
                Isolation — concurrent transactions don't interfere
                Durability — committed data survives crashes

                Current exercise: design schema for a social media app; 3NF compliant.
                """,
                "Study", false, study, database),

            note("Spring Security Concepts",
                """
                Authentication vs Authorization:
                - Authentication: who are you? (UsernamePasswordAuthenticationToken, JWT, OAuth2)
                - Authorization: what can you do? (roles, authorities, method-level @PreAuthorize)

                Spring Security 6 filter chain (replaces WebSecurityConfigurerAdapter):
                @Bean
                SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    return http
                        .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/public/**").permitAll()
                            .anyRequest().authenticated())
                        .formLogin(Customizer.withDefaults())
                        .build();
                }

                JWT flow: login → server signs token → client sends in Authorization header → server validates on each request

                TODO: implement JWT auth in NoteNest once core CRUD is stable.
                """,
                "Study", false, study, springBoot),

            note("Design Patterns — Creational",
                """
                Singleton: one instance per JVM; Spring beans are singletons by default (@Scope("singleton"))

                Factory Method: delegate instantiation to subclasses; decouple client from concrete types

                Builder: construct complex objects step by step — Lombok @Builder does this for us

                Prototype: clone existing object; useful when creation is expensive

                Abstract Factory: factory of factories; used in Spring's ApplicationContext for bean creation

                When to use which:
                - Builder → objects with many optional params (avoid telescoping constructors)
                - Factory Method → when subclass should decide what to instantiate
                - Singleton → shared stateless services

                Practice: identify patterns used in Spring Framework source code.
                """,
                "Study", false, study, java),

            note("HTTP & Networking Fundamentals",
                """
                HTTP Methods: GET (idempotent, safe), POST (non-idempotent), PUT (idempotent), DELETE (idempotent), PATCH

                Status codes to know cold:
                200 OK, 201 Created, 204 No Content
                301 Moved Permanently, 304 Not Modified
                400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 422 Unprocessable Entity
                500 Internal Server Error, 503 Service Unavailable

                HTTPS: TLS handshake → symmetric session key → encrypted payload

                REST constraints: stateless, uniform interface, client-server, cacheable, layered system

                Content negotiation: Accept header (client) ↔ Content-Type header (server response)

                Next: study WebSockets and SSE for real-time use cases.
                """,
                "Study", false, study, backend)
        );

        noteRepository.saveAll(notes);
        log.info("DataSeeder: inserted {} notes.", notes.size());
    }

    private Tag tag(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
    }

    @SafeVarargs
    private Note note(String title, String content, String folder, boolean pinned, Tag... tags) {
        Note note = Note.builder()
                .title(title)
                .content(content.stripIndent().strip())
                .folder(folder)
                .isPinned(pinned)
                .build();
        note.getTags().addAll(Arrays.stream(tags).collect(Collectors.toSet()));
        return note;
    }
}

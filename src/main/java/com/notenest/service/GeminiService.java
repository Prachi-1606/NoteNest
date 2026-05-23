package com.notenest.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notenest.dto.FlashcardDTO;
import com.notenest.dto.GeminiRequest;
import com.notenest.dto.GeminiResponse;
import com.notenest.exception.GeminiApiException;
import com.notenest.model.Note;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    static final String RATE_LIMIT_MESSAGE = "AI limit reached for this hour. Try again later.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    // ── Public methods ────────────────────────────────────────────────────────

    public String answerFromNotes(String userQuestion, List<Note> relevantNotes) {
        String notesBlock = buildNotesBlock(relevantNotes);
        String prompt = """
                You are a personal notes assistant. Answer ONLY based on the following notes.
                If the answer is not in the notes, say so clearly.
                Return PLAIN TEXT only — no HTML tags, no markdown, no code fences.

                Notes:
                %s

                Question: %s
                """.formatted(notesBlock, userQuestion);

        return stripHtml(call(prompt, "I couldn't find an answer in your notes. Please try a different question."));
    }

    public String summarizeFolder(List<Note> notes, String folderName) {
        if (notes.isEmpty()) {
            return "No notes found in folder \"" + folderName + "\" to summarize.";
        }
        String notesBlock = buildNotesBlock(notes);
        String prompt = """
                You are a personal notes assistant. Summarize the key themes, topics, and insights
                from the following notes in the "%s" folder. Be concise and structured.
                Return PLAIN TEXT only — no HTML tags, no markdown, no code fences.

                Notes:
                %s
                """.formatted(folderName, notesBlock);

        return stripHtml(call(prompt, "Unable to summarize the folder at this time. Please try again later."));
    }

    public String suggestTags(String noteContent) {
        String prompt = """
                Analyze the following note content and suggest 3 to 5 relevant tags.
                Return ONLY a comma-separated list of lowercase tag names, no explanations.
                Example output: spring-boot, java, backend

                Note content:
                %s
                """.formatted(noteContent);

        return call(prompt, "java, notes, general");
    }

    public String summarizeNote(String content) {
        String prompt = """
                Summarize the following note in 3-4 concise sentences. Be clear and capture
                the key points only.
                Return PLAIN TEXT only — absolutely no HTML tags, no markdown, no code fences,
                no bullet points, no headings. Just sentences separated by single spaces.

                Note: %s
                """.formatted(content);

        return stripHtml(call(prompt, "Unable to summarize this note at the moment."));
    }

    public String fixGrammarAndClarity(String content) {
        String prompt = """
                Improve the grammar, clarity, and readability of the following note.
                Keep the original meaning and tone intact. Do not add new information.
                Return only the improved text with no explanation or preamble.
                Return PLAIN TEXT only — no HTML tags, no markdown, no code fences.

                Note: %s
                """.formatted(content);

        return stripHtml(call(prompt, "Unable to improve the note at the moment. Please try again later."));
    }

    public String formatMeetingNotes(String rawNotes) {
        String prompt = """
                Format the following raw meeting notes into a clean structured format with
                these sections:
                ## Summary (2-3 sentences)
                ## Key Decisions
                ## Action Items (with owner if mentioned)
                ## Next Steps

                Use simple markdown formatting.

                Raw notes: %s
                """.formatted(rawNotes);

        return call(prompt, "Unable to format the meeting notes at the moment. Please try again later.");
    }

    public List<String> suggestTitles(String content) {
        String snippet = content == null ? "" :
                (content.length() > 500 ? content.substring(0, 500) : content);

        String prompt = """
                Suggest exactly 3 short, creative, and relevant titles for the following note.
                Return ONLY a JSON array of 3 strings with no extra text, no markdown, no backticks.
                Example format: ["title1", "title2", "title3"]

                Note content: %s
                """.formatted(snippet);

        String raw = call(prompt, "");
        return parseTitleArray(raw);
    }

    public List<FlashcardDTO> generateFlashcards(String noteContent) {
        String prompt = """
                Generate 5 question-answer flashcard pairs from the following note content
                for active recall studying.

                Return ONLY a valid JSON array with no extra text, no markdown, no backticks.
                Each object must have exactly two fields: question and answer.
                Format: [{"question": "...", "answer": "..."}, ...]

                Note: %s
                """.formatted(noteContent);

        String raw = call(prompt, "");
        return parseFlashcardArray(raw);
    }

    private List<FlashcardDTO> parseFlashcardArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.trim()
                .replaceAll("(?s)^```(?:json)?\\s*", "")
                .replaceAll("(?s)\\s*```$", "")
                .trim();
        try {
            return objectMapper.readValue(cleaned, new TypeReference<List<FlashcardDTO>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse flashcard list as JSON. Raw response: {}", raw);
            return List.of();
        }
    }

    private List<String> parseTitleArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("Could not suggest titles");
        }
        // Defensive: strip code fences the model sometimes adds despite instructions
        String cleaned = raw.trim()
                .replaceAll("(?s)^```(?:json)?\\s*", "")
                .replaceAll("(?s)\\s*```$", "")
                .trim();
        try {
            return objectMapper.readValue(cleaned, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse title suggestions as JSON. Raw response: {}", raw);
            return List.of("Could not suggest titles");
        }
    }

    // ── Core API call ─────────────────────────────────────────────────────────

    private String call(String prompt, String fallback) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiApiException(
                "Gemini API key is not configured. Set the GEMINI_API_KEY environment variable " +
                "or update gemini.api.key in application.properties.");
        }

        if (!rateLimitService.tryAcquire()) {
            log.warn("Gemini rate limit exceeded ({} calls/hour). Skipping API call.",
                     RateLimitService.MAX_CALLS_PER_HOUR);
            return RATE_LIMIT_MESSAGE;
        }

        try {
            String url = apiUrl + "?key=" + apiKey;
            log.debug("Calling Gemini at URL: {}", apiUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            GeminiRequest request = GeminiRequest.of(prompt);
            HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<GeminiResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, GeminiResponse.class);

            log.debug("Gemini response status: {}", response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String text = response.getBody().extractText();
                if (text != null && !text.isBlank()) return text.trim();
                log.warn("Gemini responded 200 but extractText() returned null/blank. Body: {}", response.getBody());
            } else {
                log.warn("Gemini non-2xx status: {}", response.getStatusCode());
            }

            return fallback;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Gemini API HTTP error {} — response body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return fallback;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("Gemini API network error (check proxy/firewall): {}", e.getMessage());
            return fallback;
        } catch (Exception e) {
            log.error("Gemini API call failed [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            return fallback;
        }
    }

    public String testConnection() {
        return call("Say exactly: OK", "FAILED");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Defensive: even when the prompt forbids HTML, models occasionally wrap
     * responses in {@code <p>…</p>} or sprinkle {@code <strong>}/{@code <br>}
     * tags. The frontend renders these methods' output via {@code .textContent},
     * so any leaked tag shows up as visible text. Strip tags and decode the
     * handful of common entities here.
     */
    private String stripHtml(String text) {
        if (text == null || text.isBlank()) return text;
        String noTags = text.replaceAll("<[^>]+>", "");
        return noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+\\n", "\n")
                .trim();
    }

    private String buildNotesBlock(List<Note> notes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            sb.append("--- Note ").append(i + 1).append(": ").append(note.getTitle()).append(" ---\n");
            sb.append(note.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }
}

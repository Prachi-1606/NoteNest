package com.notenest.service;

import com.notenest.dto.GeminiRequest;
import com.notenest.dto.GeminiResponse;
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

    private final RestTemplate restTemplate;

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

                Notes:
                %s

                Question: %s
                """.formatted(notesBlock, userQuestion);

        return call(prompt, "I couldn't find an answer in your notes. Please try a different question.");
    }

    public String summarizeFolder(List<Note> notes, String folderName) {
        if (notes.isEmpty()) {
            return "No notes found in folder \"" + folderName + "\" to summarize.";
        }
        String notesBlock = buildNotesBlock(notes);
        String prompt = """
                You are a personal notes assistant. Summarize the key themes, topics, and insights
                from the following notes in the "%s" folder. Be concise and structured.

                Notes:
                %s
                """.formatted(folderName, notesBlock);

        return call(prompt, "Unable to summarize the folder at this time. Please try again later.");
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

    // ── Core API call ─────────────────────────────────────────────────────────

    private String call(String prompt, String fallback) {
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

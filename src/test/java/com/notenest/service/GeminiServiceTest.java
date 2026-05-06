package com.notenest.service;

import com.notenest.dto.GeminiRequest;
import com.notenest.dto.GeminiResponse;
import com.notenest.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock RestTemplate restTemplate;
    @InjectMocks GeminiService geminiService;

    private static final String TEST_API_URL = "https://generativelanguage.test/v1/models/gemini:generateContent";
    private static final String TEST_API_KEY = "test-api-key-123";

    @BeforeEach
    void injectConfigValues() {
        ReflectionTestUtils.setField(geminiService, "apiKey", TEST_API_KEY);
        ReflectionTestUtils.setField(geminiService, "apiUrl", TEST_API_URL);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<GeminiResponse> successResponse(String text) {
        GeminiResponse.Part part = new GeminiResponse.Part();
        part.setText(text);
        GeminiResponse.Content content = new GeminiResponse.Content();
        content.setParts(List.of(part));
        GeminiResponse.Candidate candidate = new GeminiResponse.Candidate();
        candidate.setContent(content);
        GeminiResponse response = new GeminiResponse();
        response.setCandidates(List.of(candidate));
        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    private String extractPromptText(HttpEntity<?> entity) {
        GeminiRequest request = (GeminiRequest) entity.getBody();
        return request.getContents().get(0).getParts().get(0).getText();
    }

    private Note buildNote(String title, String content) {
        return Note.builder().title(title).content(content).folder("General").build();
    }

    // ── answerFromNotes ───────────────────────────────────────────────────────

    @Test
    void answerFromNotes_includesNoteTitleAndContentInPrompt() {
        Note note = buildNote("Spring Tips", "Use @Transactional for DB operations.");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("Use @Transactional."));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        geminiService.answerFromNotes("How do I use Spring?", List.of(note));

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(GeminiResponse.class));
        String prompt = extractPromptText(entityCaptor.getValue());

        assertThat(prompt).contains("Spring Tips");
        assertThat(prompt).contains("Use @Transactional for DB operations.");
    }

    @Test
    void answerFromNotes_includesUserQuestionInPrompt() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("Answer."));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        geminiService.answerFromNotes("What is dependency injection?", List.of());

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(GeminiResponse.class));
        String prompt = extractPromptText(entityCaptor.getValue());

        assertThat(prompt).contains("What is dependency injection?");
    }

    @Test
    void answerFromNotes_formatsMultipleNotesWithNumberedHeaders() {
        Note note1 = buildNote("Note One", "First note content.");
        Note note2 = buildNote("Note Two", "Second note content.");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("Combined answer."));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        geminiService.answerFromNotes("Question?", List.of(note1, note2));

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(GeminiResponse.class));
        String prompt = extractPromptText(entityCaptor.getValue());

        assertThat(prompt).contains("--- Note 1: Note One ---");
        assertThat(prompt).contains("--- Note 2: Note Two ---");
        assertThat(prompt).contains("First note content.");
        assertThat(prompt).contains("Second note content.");
    }

    @Test
    void answerFromNotes_returnsExtractedTextOnSuccessfulResponse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("This is the answer."));

        String result = geminiService.answerFromNotes("What?", List.of());

        assertThat(result).isEqualTo("This is the answer.");
    }

    @Test
    void answerFromNotes_returnsFallbackOnHttpClientError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        String result = geminiService.answerFromNotes("Question?", List.of());

        assertThat(result).isEqualTo("I couldn't find an answer in your notes. Please try a different question.");
    }

    @Test
    void answerFromNotes_returnsFallbackOnNetworkError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        String result = geminiService.answerFromNotes("Question?", List.of());

        assertThat(result).isEqualTo("I couldn't find an answer in your notes. Please try a different question.");
    }

    // ── suggestTags ───────────────────────────────────────────────────────────

    @Test
    void suggestTags_includesNoteContentInPrompt() {
        String noteContent = "This note covers Spring Boot REST APIs and JUnit testing.";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("spring-boot, rest-api, testing"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        geminiService.suggestTags(noteContent);

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(GeminiResponse.class));
        String prompt = extractPromptText(entityCaptor.getValue());

        assertThat(prompt).contains(noteContent);
    }

    @Test
    void suggestTags_returnsParsedTagsFromApiResponse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("spring-boot, rest-api, testing"));

        String result = geminiService.suggestTags("some content");

        assertThat(result).isEqualTo("spring-boot, rest-api, testing");
    }

    @Test
    void suggestTags_returnsFallbackTagsOnApiFailure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenThrow(new RuntimeException("Network error"));

        String result = geminiService.suggestTags("some content");

        assertThat(result).isEqualTo("java, notes, general");
    }

    // ── API URL / key ─────────────────────────────────────────────────────────

    @Test
    void call_appendsApiKeyToUrl() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("OK"));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        geminiService.testConnection();

        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class));
        assertThat(urlCaptor.getValue()).contains("key=" + TEST_API_KEY);
        assertThat(urlCaptor.getValue()).startsWith(TEST_API_URL);
    }

    @Test
    void call_setsJsonContentTypeHeader() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GeminiResponse.class)))
                .thenReturn(successResponse("OK"));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        geminiService.testConnection();

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(GeminiResponse.class));
        assertThat(entityCaptor.getValue().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
}

package com.notenest.service;

import com.notenest.dto.NoteSearchResultDTO;
import com.notenest.model.Note;
import com.notenest.repository.NoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock NoteRepository noteRepository;
    @InjectMocks SearchService searchService;

    private Note buildNote(Long id, String title, String content, LocalDateTime updatedAt) {
        Note note = Note.builder()
                .title(title)
                .content(content)
                .folder("General")
                .build();
        ReflectionTestUtils.setField(note, "id", id);
        ReflectionTestUtils.setField(note, "createdAt", updatedAt);
        ReflectionTestUtils.setField(note, "updatedAt", updatedAt);
        return note;
    }

    // ── searchNotes ───────────────────────────────────────────────────────────

    @Test
    void searchNotes_returnsEmptyListForBlankKeyword() {
        assertThat(searchService.searchNotes("")).isEmpty();
        assertThat(searchService.searchNotes("   ")).isEmpty();
        assertThat(searchService.searchNotes(null)).isEmpty();
        verifyNoInteractions(noteRepository);
    }

    @Test
    void searchNotes_delegatesToRepositoryWithTrimmedKeyword() {
        Note note = buildNote(1L, "Spring Boot Guide", "Content about Spring", LocalDateTime.now());
        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase("spring", "spring"))
                .thenReturn(List.of(note));

        List<NoteSearchResultDTO> results = searchService.searchNotes("spring");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Spring Boot Guide");
        assertThat(results.get(0).getFolder()).isEqualTo("General");
    }

    @Test
    void searchNotes_buildsPreviewTruncatedAt150Chars() {
        String longContent = "A".repeat(200);
        Note note = buildNote(1L, "Title", longContent, LocalDateTime.now());
        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(anyString(), anyString()))
                .thenReturn(List.of(note));

        List<NoteSearchResultDTO> results = searchService.searchNotes("keyword");

        assertThat(results.get(0).getPreview()).endsWith("…");
        assertThat(results.get(0).getPreview().length()).isLessThanOrEqualTo(151); // 150 + ellipsis char
    }

    @Test
    void searchNotes_returnsEmptyListWhenNoMatchesFound() {
        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(anyString(), anyString()))
                .thenReturn(List.of());

        assertThat(searchService.searchNotes("nonexistent")).isEmpty();
    }

    // ── getRelevantNotesForAI ─────────────────────────────────────────────────

    @Test
    void getRelevantNotesForAI_deduplicatesNotesFoundByMultipleKeywords() {
        LocalDateTime now = LocalDateTime.now();
        Note sharedNote    = buildNote(1L, "Spring Testing",  "Spring Boot and JUnit", now);
        Note onlyInSpring  = buildNote(2L, "Spring Overview", "Introduction to Spring", now.minusHours(1));

        // "spring" and "boot" are both > 3 chars → two separate repo queries
        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                eq("spring"), eq("spring"))).thenReturn(List.of(sharedNote, onlyInSpring));
        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                eq("boot"), eq("boot"))).thenReturn(List.of(sharedNote));

        List<Note> results = searchService.getRelevantNotesForAI("spring boot", 10);

        assertThat(results).hasSize(2);
        long sharedCount = results.stream().filter(n -> n.getId().equals(1L)).count();
        assertThat(sharedCount).isEqualTo(1); // sharedNote appears only once despite two matches
    }

    @Test
    void getRelevantNotesForAI_limitsResultsToMaxNotes() {
        LocalDateTime now = LocalDateTime.now();
        Note n1 = buildNote(1L, "Note 1", "content about testing frameworks", now);
        Note n2 = buildNote(2L, "Note 2", "content about unit testing practices", now.minusMinutes(1));
        Note n3 = buildNote(3L, "Note 3", "content about integration testing", now.minusMinutes(2));

        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                anyString(), anyString())).thenReturn(List.of(n1, n2, n3));

        List<Note> results = searchService.getRelevantNotesForAI("testing", 2);

        assertThat(results).hasSize(2);
    }

    @Test
    void getRelevantNotesForAI_filtersOutKeywordsShorterThanFourChars() {
        // "the", "is", "at" are all <= 3 chars and should be excluded
        List<Note> results = searchService.getRelevantNotesForAI("the is at", 5);

        assertThat(results).isEmpty();
        verifyNoInteractions(noteRepository);
    }

    @Test
    void getRelevantNotesForAI_deduplicatesKeywordsBeforeSearching() {
        Note note = buildNote(1L, "Java Guide", "Java programming", LocalDateTime.now());
        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                eq("java"), eq("java"))).thenReturn(List.of(note));

        // "java" appears twice in question — should only trigger one repo query
        searchService.getRelevantNotesForAI("java java", 5);

        verify(noteRepository, times(1))
                .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase("java", "java");
    }

    @Test
    void getRelevantNotesForAI_returnsEmptyForBlankOrNullQuestion() {
        assertThat(searchService.getRelevantNotesForAI("", 5)).isEmpty();
        assertThat(searchService.getRelevantNotesForAI(null, 5)).isEmpty();
        verifyNoInteractions(noteRepository);
    }

    @Test
    void getRelevantNotesForAI_sortsResultsByUpdatedAtDescending() {
        LocalDateTime older = LocalDateTime.now().minusDays(2);
        LocalDateTime newer = LocalDateTime.now();
        Note olderNote = buildNote(1L, "Older Note", "content about frameworks", older);
        Note newerNote = buildNote(2L, "Newer Note", "content about frameworks overview", newer);

        when(noteRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                anyString(), anyString())).thenReturn(List.of(olderNote, newerNote));

        List<Note> results = searchService.getRelevantNotesForAI("frameworks", 10);

        assertThat(results.get(0).getId()).isEqualTo(2L); // newer first
        assertThat(results.get(1).getId()).isEqualTo(1L);
    }
}

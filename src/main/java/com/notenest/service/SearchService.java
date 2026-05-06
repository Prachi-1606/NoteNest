package com.notenest.service;

import com.notenest.dto.NoteSearchResultDTO;
import com.notenest.model.Note;
import com.notenest.model.Tag;
import com.notenest.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final NoteRepository noteRepository;

    @Transactional(readOnly = true)
    public List<NoteSearchResultDTO> searchNotes(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();

        return noteRepository
                .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
                        keyword.trim(), keyword.trim())
                .stream()
                .sorted(Comparator.comparing(Note::getUpdatedAt).reversed())
                .map(this::toSearchResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Note> getRelevantNotesForAI(String userQuestion, int maxNotes) {
        if (userQuestion == null || userQuestion.isBlank()) return List.of();

        List<String> keywords = Arrays.stream(userQuestion.split("\\s+"))
                .map(String::toLowerCase)
                .map(w -> w.replaceAll("[^a-z0-9]", ""))
                .filter(w -> w.length() > 3)
                .distinct()
                .toList();

        if (keywords.isEmpty()) return List.of();

        // Search per keyword, collect into a map keyed by note id to deduplicate
        Map<Long, Note> deduplicated = new LinkedHashMap<>();
        for (String keyword : keywords) {
            noteRepository
                    .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(keyword, keyword)
                    .forEach(note -> deduplicated.putIfAbsent(note.getId(), note));
        }

        return deduplicated.values().stream()
                .sorted(Comparator.comparing(Note::getUpdatedAt).reversed())
                .limit(maxNotes)
                .toList();
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private NoteSearchResultDTO toSearchResult(Note note) {
        String flat = note.getContent().replaceAll("\\s+", " ").trim();
        String preview = flat.length() <= 150 ? flat : flat.substring(0, 150) + "…";

        return NoteSearchResultDTO.builder()
                .id(note.getId())
                .title(note.getTitle())
                .preview(preview)
                .folder(note.getFolder())
                .updatedAt(note.getUpdatedAt())
                .matchedTags(note.getTags().stream().map(Tag::getName).sorted().toList())
                .build();
    }
}

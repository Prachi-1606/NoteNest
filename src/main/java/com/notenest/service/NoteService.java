package com.notenest.service;

import com.notenest.dto.FlashcardDTO;
import com.notenest.dto.NoteDetailDTO;
import com.notenest.dto.NoteRequestDTO;
import com.notenest.dto.NoteResponseDTO;
import com.notenest.dto.NoteSearchResultDTO;
import com.notenest.exception.ResourceNotFoundException;
import com.notenest.model.Flashcard;
import com.notenest.model.Note;
import com.notenest.model.Tag;
import com.notenest.repository.FlashcardRepository;
import com.notenest.repository.NoteRepository;
import com.notenest.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final FlashcardRepository flashcardRepository;
    private final GeminiService geminiService;

    @Transactional(readOnly = true)
    public List<NoteResponseDTO> getAllNotes() {
        return noteRepository
                .findAll(Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("updatedAt")))
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteResponseDTO getNoteById(Long id) {
        return toResponseDTO(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public NoteDetailDTO getNoteWithSummary(Long id) {
        Note note = findOrThrow(id);
        String aiSummary = geminiService.summarizeNote(note.getContent());

        return NoteDetailDTO.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .preview(buildPreview(note.getContent()))
                .folder(note.getFolder())
                .isPinned(note.isPinned())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .tagNames(note.getTags().stream().map(Tag::getName).sorted().toList())
                .aiSummary(aiSummary)
                .build();
    }

    @Transactional
    public NoteResponseDTO createNote(NoteRequestDTO dto) {
        Note note = Note.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .folder(dto.getFolder() != null ? dto.getFolder() : "General")
                .isPinned(dto.isPinned())
                .build();

        note.getTags().addAll(resolveOrCreateTags(dto.getTagNames()));
        return toResponseDTO(noteRepository.save(note));
    }

    @Transactional
    public NoteResponseDTO updateNote(Long id, NoteRequestDTO dto) {
        Note note = findOrThrow(id);

        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        note.setFolder(dto.getFolder() != null ? dto.getFolder() : "General");
        note.setPinned(dto.isPinned());
        note.getTags().clear();
        note.getTags().addAll(resolveOrCreateTags(dto.getTagNames()));

        return toResponseDTO(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(Long id) {
        noteRepository.delete(findOrThrow(id));
    }

    @Transactional
    public NoteResponseDTO togglePin(Long id) {
        Note note = findOrThrow(id);
        note.setPinned(!note.isPinned());
        return toResponseDTO(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<Note> getNoteEntitiesByFolder(String folder) {
        return noteRepository.findByFolder(folder);
    }

    @Transactional(readOnly = true)
    public String getNoteContentById(Long id) {
        return findOrThrow(id).getContent();
    }

    // ── AI content transforms (return improved text — caller decides whether to save) ──

    public String fixNoteContent(Long id) {
        String content = getNoteContentById(id);
        return geminiService.fixGrammarAndClarity(content);
    }

    public String formatAsMeeting(Long id) {
        String content = getNoteContentById(id);
        return geminiService.formatMeetingNotes(content);
    }

    // ── Flashcards ────────────────────────────────────────────────────────────

    @Transactional
    public List<Flashcard> generateAndSaveFlashcards(Long noteId) {
        Note note = findOrThrow(noteId);

        // Clear existing flashcards so "Regenerate" truly replaces rather than accumulates
        flashcardRepository.deleteBySourceNoteId(noteId);

        List<FlashcardDTO> dtos = geminiService.generateFlashcards(note.getContent());

        List<Flashcard> flashcards = dtos.stream()
                .filter(d -> d.question() != null && !d.question().isBlank())
                .filter(d -> d.answer()   != null && !d.answer().isBlank())
                .map(dto -> Flashcard.builder()
                        .question(dto.question())
                        .answer(dto.answer())
                        .sourceNote(note)
                        .build())
                .toList();

        return flashcardRepository.saveAll(flashcards);
    }

    @Transactional(readOnly = true)
    public List<Flashcard> getFlashcardsByNote(Long noteId) {
        return flashcardRepository.findBySourceNoteId(noteId);
    }

    // ── Activity heatmap data ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getActivityData() {
        LocalDateTime since = LocalDate.now().minusDays(364).atStartOfDay();
        List<Object[]> rows = noteRepository.findActivityCountsSince(since);

        Map<LocalDate, Long> result = new TreeMap<>();
        for (Object[] row : rows) {
            int year  = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            int day   = ((Number) row[2]).intValue();
            long count = ((Number) row[3]).longValue();
            result.put(LocalDate.of(year, month, day), count);
        }
        return result;
    }

    /** Consecutive days ending today (or yesterday if no note today) with at least one note. */
    public int calculateCurrentStreak(Map<LocalDate, Long> activityData) {
        LocalDate cursor = LocalDate.now();
        // Don't break the streak just because today isn't over yet
        if (!activityData.containsKey(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (activityData.getOrDefault(cursor, 0L) > 0) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public long countNotesInLastDays(Map<LocalDate, Long> activityData, int days) {
        LocalDate threshold = LocalDate.now().minusDays(days - 1L);
        return activityData.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(threshold))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    @Transactional(readOnly = true)
    public List<String> getAllFolders() {
        return noteRepository.findAll()
                .stream()
                .map(Note::getFolder)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoteResponseDTO> getNotesByFolder(String folder) {
        return noteRepository.findByFolder(folder)
                .stream()
                .sorted(Comparator.comparing(Note::isPinned).reversed()
                        .thenComparing(Comparator.comparing(Note::getUpdatedAt).reversed()))
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoteResponseDTO> getNotesByTag(String tagName) {
        return noteRepository.findByTagsName(tagName)
                .stream()
                .sorted(Comparator.comparing(Note::isPinned).reversed()
                        .thenComparing(Comparator.comparing(Note::getUpdatedAt).reversed()))
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoteSearchResultDTO> searchNotes(String keyword) {
        return noteRepository
                .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(keyword, keyword)
                .stream()
                .map(note -> NoteSearchResultDTO.builder()
                        .id(note.getId())
                        .title(note.getTitle())
                        .preview(buildPreview(note.getContent()))
                        .folder(note.getFolder())
                        .updatedAt(note.getUpdatedAt())
                        .matchedTags(note.getTags().stream().map(Tag::getName).sorted().toList())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoteResponseDTO> getPinnedNotes() {
        return noteRepository.findByIsPinnedTrue().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getFolderNoteCounts() {
        return noteRepository.findAll().stream()
                .collect(Collectors.groupingBy(Note::getFolder, Collectors.counting()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Note findOrThrow(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note", id));
    }

    private Set<Tag> resolveOrCreateTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return Set.of();
        return tagNames.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())))
                .collect(Collectors.toSet());
    }

    private NoteResponseDTO toResponseDTO(Note note) {
        return NoteResponseDTO.builder()
                .id(note.getId())
                .title(note.getTitle())
                .content(note.getContent())
                .preview(buildPreview(note.getContent()))
                .folder(note.getFolder())
                .isPinned(note.isPinned())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .tagNames(note.getTags().stream().map(Tag::getName).sorted().toList())
                .build();
    }

    private String buildPreview(String content) {
        if (content == null) return "";
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 150 ? flat : flat.substring(0, 150) + "…";
    }
}

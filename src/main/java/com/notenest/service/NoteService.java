package com.notenest.service;

import com.notenest.dto.NoteRequestDTO;
import com.notenest.dto.NoteResponseDTO;
import com.notenest.dto.NoteSearchResultDTO;
import com.notenest.exception.ResourceNotFoundException;
import com.notenest.model.Note;
import com.notenest.model.Tag;
import com.notenest.repository.NoteRepository;
import com.notenest.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;

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

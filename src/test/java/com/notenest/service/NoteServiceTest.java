package com.notenest.service;

import com.notenest.dto.NoteRequestDTO;
import com.notenest.dto.NoteResponseDTO;
import com.notenest.exception.ResourceNotFoundException;
import com.notenest.model.Note;
import com.notenest.model.Tag;
import com.notenest.repository.NoteRepository;
import com.notenest.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock NoteRepository noteRepository;
    @Mock TagRepository tagRepository;
    @InjectMocks NoteService noteService;

    private Note buildNote(Long id, String title, String content, boolean pinned, LocalDateTime updatedAt) {
        Note note = Note.builder()
                .title(title)
                .content(content)
                .folder("General")
                .build();
        note.setPinned(pinned);
        ReflectionTestUtils.setField(note, "id", id);
        ReflectionTestUtils.setField(note, "createdAt", updatedAt);
        ReflectionTestUtils.setField(note, "updatedAt", updatedAt);
        return note;
    }

    // ── createNote ────────────────────────────────────────────────────────────

    @Test
    void createNote_createsNewTagsWhenNoneExistInRepository() {
        NoteRequestDTO dto = NoteRequestDTO.builder()
                .title("My Note")
                .content("Content")
                .folder("Work")
                .tagNames(List.of("java", "spring"))
                .build();

        when(tagRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", 1L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(n, "updatedAt", LocalDateTime.now());
            return n;
        });

        NoteResponseDTO result = noteService.createNote(dto);

        ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(noteCaptor.capture());
        Note saved = noteCaptor.getValue();

        assertThat(saved.getTags()).hasSize(2);
        assertThat(saved.getTags()).extracting(Tag::getName)
                .containsExactlyInAnyOrder("java", "spring");
        verify(tagRepository, times(2)).save(any(Tag.class));
        assertThat(result.getTitle()).isEqualTo("My Note");
        assertThat(result.getFolder()).isEqualTo("Work");
    }

    @Test
    void createNote_reusesExistingTagInsteadOfCreatingDuplicate() {
        Tag existingTag = Tag.builder().name("java").build();
        ReflectionTestUtils.setField(existingTag, "id", 10L);

        NoteRequestDTO dto = NoteRequestDTO.builder()
                .title("My Note")
                .content("Content")
                .tagNames(List.of("java"))
                .build();

        when(tagRepository.findByName("java")).thenReturn(Optional.of(existingTag));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", 2L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(n, "updatedAt", LocalDateTime.now());
            return n;
        });

        noteService.createNote(dto);

        verify(tagRepository, never()).save(any(Tag.class));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).containsExactly(existingTag);
    }

    @Test
    void createNote_defaultsFolderToGeneralWhenNotProvided() {
        NoteRequestDTO dto = NoteRequestDTO.builder()
                .title("No Folder Note")
                .content("Content")
                .folder(null)
                .build();

        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", 3L);
            ReflectionTestUtils.setField(n, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(n, "updatedAt", LocalDateTime.now());
            return n;
        });

        noteService.createNote(dto);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getFolder()).isEqualTo("General");
    }

    // ── updateNote ────────────────────────────────────────────────────────────

    @Test
    void updateNote_updatesAllChangedFields() {
        Note existing = buildNote(1L, "Old Title", "Old Content", false, LocalDateTime.now().minusDays(1));

        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tagRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "updatedAt", LocalDateTime.now());
            return n;
        });

        NoteRequestDTO dto = NoteRequestDTO.builder()
                .title("New Title")
                .content("New Content")
                .folder("Work")
                .isPinned(true)
                .tagNames(List.of("updated-tag"))
                .build();

        noteService.updateNote(1L, dto);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        Note saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("New Title");
        assertThat(saved.getContent()).isEqualTo("New Content");
        assertThat(saved.getFolder()).isEqualTo("Work");
        assertThat(saved.isPinned()).isTrue();
        assertThat(saved.getTags()).extracting(Tag::getName).containsExactly("updated-tag");
    }

    @Test
    void updateNote_clearsOldTagsBeforeApplyingNew() {
        Note existing = buildNote(1L, "Title", "Content", false, LocalDateTime.now());
        Tag oldTag = Tag.builder().name("old-tag").build();
        existing.getTags().add(oldTag);

        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tagRepository.findByName("new-tag")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteRequestDTO dto = NoteRequestDTO.builder()
                .title("Title").content("Content").tagNames(List.of("new-tag")).build();

        noteService.updateNote(1L, dto);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getTags())
                .extracting(Tag::getName)
                .containsExactly("new-tag")
                .doesNotContain("old-tag");
    }

    // ── togglePin ─────────────────────────────────────────────────────────────

    @Test
    void togglePin_flipsFromFalseToTrue() {
        Note note = buildNote(1L, "Note", "Content", false, LocalDateTime.now());
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteResponseDTO result = noteService.togglePin(1L);

        assertThat(result.isPinned()).isTrue();
    }

    @Test
    void togglePin_flipsFromTrueToFalse() {
        Note note = buildNote(1L, "Note", "Content", true, LocalDateTime.now());
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteResponseDTO result = noteService.togglePin(1L);

        assertThat(result.isPinned()).isFalse();
    }

    // ── getAllNotes ───────────────────────────────────────────────────────────

    @Test
    void getAllNotes_passesDescIsPinnedThenDescUpdatedAtSortToRepository() {
        Note pinned   = buildNote(1L, "Pinned",   "Content", true,  LocalDateTime.now());
        Note unpinned = buildNote(2L, "Unpinned", "Content", false, LocalDateTime.now().minusHours(1));

        when(noteRepository.findAll(any(Sort.class))).thenReturn(List.of(pinned, unpinned));

        List<NoteResponseDTO> results = noteService.getAllNotes();

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(noteRepository).findAll(sortCaptor.capture());

        Sort captured = sortCaptor.getValue();
        assertThat(captured.getOrderFor("isPinned")).isNotNull();
        assertThat(captured.getOrderFor("isPinned").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(captured.getOrderFor("updatedAt")).isNotNull();
        assertThat(captured.getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getAllNotes_returnsMappedDTOsInRepositoryOrder() {
        Note pinned   = buildNote(1L, "Pinned",   "Content", true,  LocalDateTime.now());
        Note unpinned = buildNote(2L, "Unpinned", "Content", false, LocalDateTime.now().minusHours(1));

        // Repository returns pinned first (as it would with the DESC sort)
        when(noteRepository.findAll(any(Sort.class))).thenReturn(List.of(pinned, unpinned));

        List<NoteResponseDTO> results = noteService.getAllNotes();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isPinned()).isTrue();
        assertThat(results.get(0).getTitle()).isEqualTo("Pinned");
        assertThat(results.get(1).isPinned()).isFalse();
        assertThat(results.get(1).getTitle()).isEqualTo("Unpinned");
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    void getNoteById_throwsResourceNotFoundExceptionForUnknownId() {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNoteById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deleteNote_callsRepositoryDeleteWithCorrectEntity() {
        Note note = buildNote(1L, "Note", "Content", false, LocalDateTime.now());
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        noteService.deleteNote(1L);

        verify(noteRepository).delete(note);
    }

    @Test
    void deleteNote_throwsWhenNoteNotFound() {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

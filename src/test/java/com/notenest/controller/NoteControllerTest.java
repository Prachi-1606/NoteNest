package com.notenest.controller;

import com.notenest.dto.NoteRequestDTO;
import com.notenest.dto.NoteResponseDTO;
import com.notenest.repository.TagRepository;
import com.notenest.service.NoteService;
import com.notenest.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NoteController.class)
class NoteControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean NoteService noteService;
    @MockBean SearchService searchService;
    @MockBean TagRepository tagRepository;

    private NoteResponseDTO sampleNote;

    @BeforeEach
    void setUp() {
        sampleNote = NoteResponseDTO.builder()
                .id(1L).title("Test Note").content("Test content").preview("Test content")
                .folder("General").isPinned(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .tagNames(List.of())
                .build();

        // Stub sidebar data populated by @ModelAttribute on every request
        when(noteService.getAllFolders()).thenReturn(List.of("General", "Work"));
        when(tagRepository.findAll()).thenReturn(List.of());
        when(noteService.getPinnedNotes()).thenReturn(List.of());
        when(noteService.getFolderNoteCounts()).thenReturn(Map.of("General", 1L));
    }

    // ── GET /notes ────────────────────────────────────────────────────────────

    @Test
    void dashboard_returnsOkAndDashboardView() throws Exception {
        when(noteService.getAllNotes()).thenReturn(List.of(sampleNote));

        mockMvc.perform(get("/notes"))
                .andExpect(status().isOk())
                .andExpect(view().name("notes/dashboard"));
    }

    @Test
    void dashboard_addsNotesListToModel() throws Exception {
        when(noteService.getAllNotes()).thenReturn(List.of(sampleNote));

        mockMvc.perform(get("/notes"))
                .andExpect(model().attribute("notes", List.of(sampleNote)));
    }

    @Test
    void dashboard_rendersEmptyStateWhenNoNotes() throws Exception {
        when(noteService.getAllNotes()).thenReturn(List.of());

        mockMvc.perform(get("/notes"))
                .andExpect(status().isOk())
                .andExpect(view().name("notes/dashboard"))
                .andExpect(model().attribute("notes", List.of()));
    }

    // ── POST /notes (create) ──────────────────────────────────────────────────

    @Test
    void createNote_redirectsToDetailPageOnValidSubmission() throws Exception {
        NoteResponseDTO saved = NoteResponseDTO.builder()
                .id(42L).title("New Note").content("Content").preview("Content")
                .folder("General").isPinned(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .tagNames(List.of())
                .build();
        when(noteService.createNote(any(NoteRequestDTO.class))).thenReturn(saved);

        mockMvc.perform(post("/notes")
                        .param("title", "New Note")
                        .param("content", "Some content here")
                        .param("folder", "General")
                        .param("tagsInput", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes/42"));
    }

    @Test
    void createNote_callsNoteServiceOnValidSubmission() throws Exception {
        NoteResponseDTO saved = NoteResponseDTO.builder()
                .id(1L).title("T").content("C").preview("C")
                .folder("General").isPinned(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .tagNames(List.of())
                .build();
        when(noteService.createNote(any(NoteRequestDTO.class))).thenReturn(saved);

        mockMvc.perform(post("/notes")
                        .param("title", "My Title")
                        .param("content", "My Content")
                        .param("tagsInput", ""));

        verify(noteService).createNote(any(NoteRequestDTO.class));
    }

    @Test
    void createNote_showsFormWithValidationErrorsForBlankTitle() throws Exception {
        mockMvc.perform(post("/notes")
                        .param("title", "")
                        .param("content", "Some content")
                        .param("tagsInput", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("notes/form"))
                .andExpect(model().attributeHasFieldErrors("noteForm", "title"));

        verify(noteService, never()).createNote(any());
    }

    @Test
    void createNote_showsFormWithValidationErrorsForBlankContent() throws Exception {
        mockMvc.perform(post("/notes")
                        .param("title", "My Title")
                        .param("content", "")
                        .param("tagsInput", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("notes/form"))
                .andExpect(model().attributeHasFieldErrors("noteForm", "content"));

        verify(noteService, never()).createNote(any());
    }

    @Test
    void createNote_showsFormWithErrorsWhenBothTitleAndContentAreBlank() throws Exception {
        mockMvc.perform(post("/notes")
                        .param("title", "")
                        .param("content", "")
                        .param("tagsInput", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("notes/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("noteForm", "title", "content"));
    }

    // ── POST /notes/{id}/delete ───────────────────────────────────────────────

    @Test
    void deleteNote_redirectsToDashboardAfterDeletion() throws Exception {
        doNothing().when(noteService).deleteNote(1L);

        mockMvc.perform(post("/notes/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"));
    }

    @Test
    void deleteNote_callsNoteServiceWithCorrectId() throws Exception {
        doNothing().when(noteService).deleteNote(7L);

        mockMvc.perform(post("/notes/7/delete"));

        verify(noteService).deleteNote(7L);
    }

    // ── POST /notes/{id}/pin ──────────────────────────────────────────────────

    @Test
    void togglePin_redirectsBackToNoteDetail() throws Exception {
        NoteResponseDTO pinned = NoteResponseDTO.builder()
                .id(1L).title("Note").content("C").preview("C")
                .folder("General").isPinned(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .tagNames(List.of())
                .build();
        when(noteService.togglePin(1L)).thenReturn(pinned);

        mockMvc.perform(post("/notes/1/pin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes/1"));
    }

    @Test
    void togglePin_callsNoteServiceWithCorrectId() throws Exception {
        when(noteService.togglePin(3L)).thenReturn(sampleNote);

        mockMvc.perform(post("/notes/3/pin"));

        verify(noteService).togglePin(3L);
    }
}

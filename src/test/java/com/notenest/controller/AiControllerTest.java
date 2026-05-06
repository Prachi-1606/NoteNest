package com.notenest.controller;

import com.notenest.model.Note;
import com.notenest.repository.TagRepository;
import com.notenest.service.GeminiService;
import com.notenest.service.NoteService;
import com.notenest.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiController.class)
class AiControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean NoteService noteService;
    @MockBean SearchService searchService;
    @MockBean GeminiService geminiService;
    @MockBean TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        // Stub sidebar data populated by @ModelAttribute on every request
        when(noteService.getAllFolders()).thenReturn(List.of("General"));
        when(tagRepository.findAll()).thenReturn(List.of());
        when(noteService.getPinnedNotes()).thenReturn(List.of());
        when(noteService.getFolderNoteCounts()).thenReturn(Map.of());
    }

    // ── GET /ai/chat ──────────────────────────────────────────────────────────

    @Test
    void chat_returnsOkWithChatView() throws Exception {
        mockMvc.perform(get("/ai/chat"))
                .andExpect(status().isOk())
                .andExpect(view().name("ai/chat"));
    }

    @Test
    void chat_addsFoldersToModel() throws Exception {
        when(noteService.getAllFolders()).thenReturn(List.of("General", "Work", "Study"));

        mockMvc.perform(get("/ai/chat"))
                .andExpect(model().attribute("folders", hasItems("General", "Work", "Study")));
    }

    @Test
    void chat_rendersWithoutQuestionOrAnswer() throws Exception {
        mockMvc.perform(get("/ai/chat"))
                .andExpect(model().attributeDoesNotExist("question"))
                .andExpect(model().attributeDoesNotExist("answer"));
    }

    // ── POST /ai/ask ──────────────────────────────────────────────────────────

    @Test
    void ask_callsSearchServiceWithQuestionAndMaxFiveNotes() throws Exception {
        when(searchService.getRelevantNotesForAI(anyString(), anyInt())).thenReturn(List.of());
        when(geminiService.answerFromNotes(anyString(), anyList())).thenReturn("Answer");

        mockMvc.perform(post("/ai/ask").param("question", "spring boot tips"));

        verify(searchService).getRelevantNotesForAI("spring boot tips", 5);
    }

    @Test
    void ask_callsGeminiServiceWithQuestionAndRelevantNotes() throws Exception {
        List<Note> relevantNotes = List.of(
                Note.builder().title("Spring Note").content("Content").folder("General").build()
        );
        when(searchService.getRelevantNotesForAI("spring boot", 5)).thenReturn(relevantNotes);
        when(geminiService.answerFromNotes("spring boot", relevantNotes)).thenReturn("Here are tips.");

        mockMvc.perform(post("/ai/ask").param("question", "spring boot"))
                .andExpect(status().isOk());

        verify(geminiService).answerFromNotes("spring boot", relevantNotes);
    }

    @Test
    void ask_addsAnswerAndQuestionToModel() throws Exception {
        when(searchService.getRelevantNotesForAI("spring boot tips", 5)).thenReturn(List.of());
        when(geminiService.answerFromNotes("spring boot tips", List.of())).thenReturn("Here are some tips.");

        mockMvc.perform(post("/ai/ask").param("question", "spring boot tips"))
                .andExpect(status().isOk())
                .andExpect(view().name("ai/chat"))
                .andExpect(model().attribute("answer", "Here are some tips."))
                .andExpect(model().attribute("question", "spring boot tips"));
    }

    @Test
    void ask_addsSourceNotesToModel() throws Exception {
        Note note = Note.builder().title("Spring Tips").content("Content").folder("General").build();
        List<Note> relevantNotes = List.of(note);
        when(searchService.getRelevantNotesForAI(anyString(), eq(5))).thenReturn(relevantNotes);
        when(geminiService.answerFromNotes(anyString(), anyList())).thenReturn("Answer");

        mockMvc.perform(post("/ai/ask").param("question", "anything"))
                .andExpect(model().attribute("sourceNotes", relevantNotes));
    }

    @Test
    void ask_returnsChatViewWithAnswer() throws Exception {
        when(searchService.getRelevantNotesForAI(anyString(), anyInt())).thenReturn(List.of());
        when(geminiService.answerFromNotes(anyString(), anyList())).thenReturn("Some answer");

        mockMvc.perform(post("/ai/ask").param("question", "test question"))
                .andExpect(status().isOk())
                .andExpect(view().name("ai/chat"));
    }

    @Test
    void ask_addsFoldersToModelForSummarizeWidget() throws Exception {
        when(noteService.getAllFolders()).thenReturn(List.of("General", "Work"));
        when(searchService.getRelevantNotesForAI(anyString(), anyInt())).thenReturn(List.of());
        when(geminiService.answerFromNotes(anyString(), anyList())).thenReturn("Answer");

        mockMvc.perform(post("/ai/ask").param("question", "test"))
                .andExpect(model().attribute("folders", hasItems("General", "Work")));
    }

    // ── POST /ai/suggest-tags/{noteId} ────────────────────────────────────────

    @Test
    void suggestTags_usesProvidedContentParamDirectly() throws Exception {
        when(geminiService.suggestTags("My note about Java and Spring."))
                .thenReturn("java, spring, backend");

        mockMvc.perform(post("/ai/suggest-tags/0")
                        .param("content", "My note about Java and Spring."))
                .andExpect(status().isOk())
                .andExpect(content().string("java, spring, backend"));

        verify(geminiService).suggestTags("My note about Java and Spring.");
        verify(noteService, never()).getNoteContentById(any());
    }

    @Test
    void suggestTags_fallsBackToNoteContentFromDbWhenContentParamAbsent() throws Exception {
        when(noteService.getNoteContentById(5L)).thenReturn("Stored note content.");
        when(geminiService.suggestTags("Stored note content.")).thenReturn("storage, database");

        mockMvc.perform(post("/ai/suggest-tags/5"))
                .andExpect(status().isOk())
                .andExpect(content().string("storage, database"));

        verify(noteService).getNoteContentById(5L);
    }
}

package com.notenest.controller;

import com.notenest.model.Note;
import com.notenest.repository.TagRepository;
import com.notenest.service.GeminiService;
import com.notenest.service.NoteService;
import com.notenest.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final NoteService noteService;
    private final SearchService searchService;
    private final GeminiService geminiService;
    private final TagRepository tagRepository;

    // ── Sidebar data for all views in this controller ─────────────────────────

    @ModelAttribute
    public void populateSidebar(Model model) {
        model.addAttribute("sidebarFolders", noteService.getAllFolders());
        model.addAttribute("sidebarTags", allTagNames());
        model.addAttribute("sidebarPinned", noteService.getPinnedNotes());
        model.addAttribute("folderCounts", noteService.getFolderNoteCounts());
    }

    // ── GET /ai — AI Tools landing ────────────────────────────────────────────

    @GetMapping({"", "/"})
    public String aiTools(Model model) {
        return "ai/tools";
    }

    // ── GET /ai/chat ──────────────────────────────────────────────────────────

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("folders", noteService.getAllFolders());
        model.addAttribute("allTags", allTagNames());
        return "ai/chat";
    }

    // ── POST /ai/ask ──────────────────────────────────────────────────────────

    @PostMapping("/ask")
    public String ask(@RequestParam("question") String question, Model model) {
        List<Note> relevantNotes = searchService.getRelevantNotesForAI(question, 5);
        String answer = geminiService.answerFromNotes(question, relevantNotes);

        model.addAttribute("answer", answer);
        model.addAttribute("question", question);
        model.addAttribute("sourceNotes", relevantNotes);   // List<Note> — id + title for links
        model.addAttribute("folders", noteService.getAllFolders());
        model.addAttribute("allTags", allTagNames());
        return "ai/chat";
    }

    // ── POST /ai/summarize/{folder} ───────────────────────────────────────────

    @PostMapping("/summarize/{folder}")
    public String summarize(@PathVariable String folder, RedirectAttributes redirectAttrs) {
        List<Note> notes = noteService.getNoteEntitiesByFolder(folder);
        String summary = geminiService.summarizeFolder(notes, folder);
        redirectAttrs.addFlashAttribute("aiSummary", summary);
        redirectAttrs.addFlashAttribute("summaryFolder", folder);
        return "redirect:/notes/folder/" + folder;
    }

    // ── POST /ai/summarize/{noteId} (AJAX, numeric path) ──────────────────────
    // Regex constraint disambiguates from /summarize/{folder} above:
    //  - /ai/summarize/123  → this handler (Long noteId)
    //  - /ai/summarize/Work → the folder handler above (String folder)

    @PostMapping("/summarize/{noteId:[0-9]+}")
    @ResponseBody
    public String summarizeNote(@PathVariable Long noteId) {
        String content = noteService.getNoteContentById(noteId);
        return geminiService.summarizeNote(content);
    }

    // ── POST /ai/suggest-titles (AJAX) ────────────────────────────────────────

    @PostMapping("/suggest-titles")
    @ResponseBody
    public List<String> suggestTitles(@RequestParam("content") String content) {
        return geminiService.suggestTitles(content);
    }

    // ── POST /ai/fix-grammar/{noteId} (AJAX) ──────────────────────────────────
    // If `content` is supplied as a form param, use it directly (supports new
    // unsaved notes — pass noteId=0). Otherwise load content from the stored note.

    @PostMapping("/fix-grammar/{noteId}")
    @ResponseBody
    public String fixGrammar(@PathVariable Long noteId,
                             @RequestParam(required = false) String content) {
        if (content != null && !content.isBlank()) {
            return geminiService.fixGrammarAndClarity(content);
        }
        return noteService.fixNoteContent(noteId);
    }

    // ── POST /ai/format-meeting/{noteId} (AJAX) ───────────────────────────────

    @PostMapping("/format-meeting/{noteId}")
    @ResponseBody
    public String formatMeeting(@PathVariable Long noteId) {
        return noteService.formatAsMeeting(noteId);
    }

    // ── POST /ai/flashcards/{noteId} — generate (and replace) flashcards ──────

    @PostMapping("/flashcards/{noteId}")
    public String generateFlashcards(@PathVariable Long noteId, RedirectAttributes redirectAttrs) {
        noteService.generateAndSaveFlashcards(noteId);
        redirectAttrs.addFlashAttribute("successMessage", "Flashcards generated.");
        return "redirect:/ai/flashcards/" + noteId;
    }

    // ── GET /ai/flashcards/{noteId} — study view ──────────────────────────────

    @GetMapping("/flashcards/{noteId}")
    public String viewFlashcards(@PathVariable Long noteId, Model model) {
        var note = noteService.getNoteById(noteId); // throws ResourceNotFoundException if missing
        model.addAttribute("flashcards", noteService.getFlashcardsByNote(noteId));
        model.addAttribute("noteId", noteId);
        model.addAttribute("noteTitle", note.getTitle());
        return "ai/flashcards";
    }

    // ── POST /ai/suggest-tags/{noteId} (AJAX) ────────────────────────────────

    @PostMapping("/suggest-tags/{noteId}")
    @ResponseBody
    public String suggestTags(@PathVariable Long noteId,
                               @RequestParam(required = false) String content) {
        if (content != null && !content.isBlank()) {
            return geminiService.suggestTags(content);
        }
        return geminiService.suggestTags(noteService.getNoteContentById(noteId));
    }

    // ── Diagnostic (dev only) ─────────────────────────────────────────────────

    @GetMapping("/test")
    @ResponseBody
    public String testGemini() {
        long start = System.currentTimeMillis();
        String result = geminiService.testConnection();
        long ms = System.currentTimeMillis() - start;
        return "Result: " + result + " | Time: " + ms + "ms";
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private List<String> allTagNames() {
        return tagRepository.findAll().stream()
                .map(t -> t.getName())
                .sorted()
                .toList();
    }
}

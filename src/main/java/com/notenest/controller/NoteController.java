package com.notenest.controller;

import com.notenest.dto.NoteRequestDTO;
import com.notenest.dto.NoteResponseDTO;
import com.notenest.repository.TagRepository;
import com.notenest.service.NoteService;
import com.notenest.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final SearchService searchService;
    private final TagRepository tagRepository;

    // ── Sidebar data for every view in this controller ───────────────────────

    @ModelAttribute
    public void populateSidebar(Model model) {
        model.addAttribute("sidebarFolders", noteService.getAllFolders());
        model.addAttribute("sidebarTags", allTagNames());
        model.addAttribute("sidebarPinned", noteService.getPinnedNotes());
        model.addAttribute("folderCounts", noteService.getFolderNoteCounts());
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model) {
        List<NoteResponseDTO> allNotes = noteService.getAllNotes();
        model.addAttribute("notes", allNotes);
        model.addAttribute("pinnedNotes", allNotes.stream().filter(NoteResponseDTO::isPinned).toList());
        model.addAttribute("folders", noteService.getAllFolders());
        model.addAttribute("allTags", allTagNames());
        return "notes/dashboard";
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        NoteResponseDTO note = noteService.getNoteById(id);
        int wordCount = countWords(note.getContent());
        int readTime = Math.max(1, (int) Math.ceil(wordCount / 200.0));
        model.addAttribute("note", note);
        model.addAttribute("wordCount", wordCount);
        model.addAttribute("readTime", readTime);
        return "notes/detail";
    }

    private int countWords(String content) {
        if (content == null || content.isBlank()) return 0;
        return (int) Arrays.stream(content.trim().split("\\s+"))
                .filter(w -> !w.isBlank())
                .count();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("noteForm", NoteRequestDTO.builder().build());
        model.addAttribute("tagsInput", "");
        model.addAttribute("folders", noteService.getAllFolders());
        model.addAttribute("allTags", allTagNames());
        model.addAttribute("isEdit", false);
        return "notes/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("noteForm") NoteRequestDTO dto,
                         BindingResult result,
                         @RequestParam(value = "tagsInput", required = false, defaultValue = "") String tagsInput,
                         Model model,
                         RedirectAttributes redirectAttrs) {

        dto.setTagNames(parseTagNames(tagsInput));

        if (result.hasErrors()) {
            model.addAttribute("tagsInput", tagsInput);
            model.addAttribute("folders", noteService.getAllFolders());
            model.addAttribute("allTags", allTagNames());
            model.addAttribute("isEdit", false);
            return "notes/form";
        }

        NoteResponseDTO saved = noteService.createNote(dto);
        redirectAttrs.addFlashAttribute("successMessage", "Note created successfully.");
        return "redirect:/notes/" + saved.getId();
    }

    // ── Edit / Update ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        NoteResponseDTO note = noteService.getNoteById(id);

        NoteRequestDTO form = NoteRequestDTO.builder()
                .title(note.getTitle())
                .content(note.getContent())
                .folder(note.getFolder())
                .isPinned(note.isPinned())
                .tagNames(note.getTagNames())
                .build();

        model.addAttribute("noteForm", form);
        model.addAttribute("noteId", id);
        model.addAttribute("tagsInput", String.join(", ", note.getTagNames()));
        model.addAttribute("folders", noteService.getAllFolders());
        model.addAttribute("allTags", allTagNames());
        model.addAttribute("isEdit", true);
        return "notes/form";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("noteForm") NoteRequestDTO dto,
                         BindingResult result,
                         @RequestParam(value = "tagsInput", required = false, defaultValue = "") String tagsInput,
                         Model model,
                         RedirectAttributes redirectAttrs) {

        dto.setTagNames(parseTagNames(tagsInput));

        if (result.hasErrors()) {
            model.addAttribute("noteId", id);
            model.addAttribute("tagsInput", tagsInput);
            model.addAttribute("folders", noteService.getAllFolders());
            model.addAttribute("allTags", allTagNames());
            model.addAttribute("isEdit", true);
            return "notes/form";
        }

        noteService.updateNote(id, dto);
        redirectAttrs.addFlashAttribute("successMessage", "Note updated successfully.");
        return "redirect:/notes/" + id;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        noteService.deleteNote(id);
        redirectAttrs.addFlashAttribute("successMessage", "Note deleted.");
        return "redirect:/notes";
    }

    // ── Pin toggle ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/pin")
    public String togglePin(@PathVariable Long id,
                            @RequestHeader(value = "Referer", required = false) String referer,
                            RedirectAttributes redirectAttrs) {
        NoteResponseDTO updated = noteService.togglePin(id);
        redirectAttrs.addFlashAttribute("successMessage",
                updated.isPinned() ? "Note pinned." : "Note unpinned.");
        return referer != null ? "redirect:" + referer : "redirect:/notes/" + id;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false, defaultValue = "") String q,
                         @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
                         @RequestParam(value = "ajax", required = false) String ajax,
                         Model model) {
        String term = keyword.isBlank() ? q : keyword;
        var results = searchService.searchNotes(term);

        if ("true".equals(ajax)) {
            model.addAttribute("results", results.stream().limit(6).toList());
            model.addAttribute("keyword", term);
            return "notes/fragments :: searchResults";
        }

        Map<Long, String> highlighted = results.stream()
                .collect(Collectors.toMap(
                        r -> r.getId(),
                        r -> highlight(r.getPreview(), term)));
        model.addAttribute("results", results);
        model.addAttribute("highlightedPreviews", highlighted);
        model.addAttribute("keyword", term);
        return "notes/search";
    }

    // ── Folder filter ─────────────────────────────────────────────────────────

    @GetMapping("/folder/{folderName}")
    public String byFolder(@PathVariable String folderName, Model model) {
        model.addAttribute("notes", noteService.getNotesByFolder(folderName));
        model.addAttribute("currentFolder", folderName);
        model.addAttribute("folders", noteService.getAllFolders());
        model.addAttribute("allTags", allTagNames());
        return "notes/dashboard";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> parseTagNames(String tagsInput) {
        if (tagsInput == null || tagsInput.isBlank()) return List.of();
        return Arrays.stream(tagsInput.split("[,;]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private String highlight(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) return text == null ? "" : text;
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String pattern = "(?i)(" + Pattern.quote(keyword.trim()) + ")";
        return safe.replaceAll(pattern, "<strong class=\"bg-warning bg-opacity-75 rounded px-1\">$1</strong>");
    }

    private List<String> allTagNames() {
        return tagRepository.findAll()
                .stream()
                .map(t -> t.getName())
                .sorted()
                .toList();
    }
}

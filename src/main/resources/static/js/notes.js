'use strict';

document.addEventListener('DOMContentLoaded', () => {
    initPrefillFromSession();   // must run before initAutoSaveDraft so its "empty form" check sees the prefilled values
    initCharCounter();
    initAutoSaveDraft();
    initTagSuggestion();
    initTitleSuggestion();
    initConfirmDelete();
});

// ── 0. One-shot prefill from sessionStorage (e.g. "Save as New Note" from detail) ──
function initPrefillFromSession() {
    const titleEl   = document.getElementById('title');
    const contentEl = document.getElementById('content');
    if (!titleEl || !contentEl) return;

    const prefillTitle   = sessionStorage.getItem('notenest-prefill-title');
    const prefillContent = sessionStorage.getItem('notenest-prefill-content');
    if (!prefillTitle && !prefillContent) return;

    // Only prefill on a fresh new-note form (don't overwrite existing edit values)
    if (titleEl.value.trim() || contentEl.value.trim()) return;

    if (prefillTitle)   titleEl.value   = prefillTitle;
    if (prefillContent) contentEl.value = prefillContent;

    sessionStorage.removeItem('notenest-prefill-title');
    sessionStorage.removeItem('notenest-prefill-content');

    // Refresh the char counter
    contentEl.dispatchEvent(new Event('input'));
}

// ── 1. Character counter ───────────────────────────────────────────────────
function initCharCounter() {
    const content = document.getElementById('content');
    const counter = document.getElementById('charCount');
    if (!content || !counter) return;

    const update = () => {
        const len = content.value.length;
        counter.textContent = len.toLocaleString() + ' character' + (len !== 1 ? 's' : '');
    };
    content.addEventListener('input', update);
    update();
}

// ── 2. Auto-save draft ─────────────────────────────────────────────────────
const DRAFT_KEY = 'notenest-draft';

function initAutoSaveDraft() {
    const titleEl   = document.getElementById('title');
    const contentEl = document.getElementById('content');
    const noteForm  = document.querySelector('form[data-note-form]');
    const banner    = document.getElementById('draftBanner');
    if (!titleEl || !contentEl || !noteForm) return;

    // Show restore banner only when form is empty (new note) and a draft exists
    const saved = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null');
    if (saved && !titleEl.value.trim() && !contentEl.value.trim() && banner) {
        banner.classList.remove('d-none');

        document.getElementById('draftRestoreYes')?.addEventListener('click', () => {
            titleEl.value   = saved.title   || '';
            contentEl.value = saved.content || '';
            contentEl.dispatchEvent(new Event('input')); // refresh char counter
            banner.classList.add('d-none');
        });

        document.getElementById('draftRestoreNo')?.addEventListener('click', () => {
            localStorage.removeItem(DRAFT_KEY);
            banner.classList.add('d-none');
        });
    }

    // Persist every 30 seconds if there is anything to save
    setInterval(() => {
        if (titleEl.value.trim() || contentEl.value.trim()) {
            localStorage.setItem(DRAFT_KEY, JSON.stringify({
                title:   titleEl.value,
                content: contentEl.value
            }));
        }
    }, 30000);

    // Clear draft when the form is submitted successfully
    noteForm.addEventListener('submit', () => {
        localStorage.removeItem(DRAFT_KEY);
    });
}

// ── 3. AI tag suggestion ───────────────────────────────────────────────────
function initTagSuggestion() {
    const btn       = document.getElementById('suggestTagsBtn');
    const tagsInput = document.getElementById('tagsInput');
    const contentEl = document.getElementById('content');
    const badge     = document.getElementById('aiSuggestedBadge');
    const noteIdEl  = document.getElementById('noteId');
    if (!btn || !tagsInput || !contentEl) return;

    btn.addEventListener('click', async () => {
        const content = contentEl.value.trim();
        if (!content) {
            const orig = btn.innerHTML;
            btn.textContent = 'Write some content first';
            setTimeout(() => { btn.innerHTML = orig; }, 2000);
            return;
        }

        const noteId = noteIdEl?.value || '0';
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span>Suggesting…';
        if (badge) badge.classList.add('d-none');

        try {
            const body = new FormData();
            body.append('content', content);
            const res = await fetch('/ai/suggest-tags/' + noteId, { method: 'POST', body });
            if (res.ok) {
                const tags = (await res.text()).trim();
                if (tags) {
                    tagsInput.value = tags;
                    if (badge) badge.classList.remove('d-none');
                }
            }
        } catch (err) {
            console.error('Tag suggestion error:', err);
        } finally {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-stars me-1"></i>Suggest Tags';
        }
    });
}

// ── 4. AI title suggestion ─────────────────────────────────────────────────
function initTitleSuggestion() {
    const btn       = document.getElementById('suggestTitlesBtn');
    const titleEl   = document.getElementById('title');
    const contentEl = document.getElementById('content');
    const chipBar   = document.getElementById('titleSuggestions');
    if (!btn || !titleEl || !contentEl || !chipBar) return;

    btn.addEventListener('click', async () => {
        const content = contentEl.value.trim();
        if (!content) {
            const orig = btn.innerHTML;
            btn.textContent = 'Write some content first';
            setTimeout(() => { btn.innerHTML = orig; }, 2000);
            return;
        }

        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span>Suggesting…';
        chipBar.classList.add('d-none');

        try {
            const body = new URLSearchParams();
            body.append('content', content);
            const res = await fetch('/ai/suggest-titles', {
                method:  'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
            });
            if (!res.ok) throw new Error('Request failed: ' + res.status);

            const titles = await res.json();
            renderChips(chipBar, titleEl, titles);
        } catch (err) {
            console.error('Title suggestion error:', err);
        } finally {
            btn.disabled  = false;
            btn.innerHTML = '<i class="bi bi-stars me-1"></i>Suggest Titles';
        }
    });
}

function renderChips(chipBar, titleEl, titles) {
    // Clear any existing chips but keep the "Suggested:" label (first child)
    while (chipBar.children.length > 1) chipBar.removeChild(chipBar.lastChild);

    titles.forEach(title => {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'btn btn-sm py-0 px-2 rounded-pill';
        chip.style.cssText = 'background:#eef2ff; color:#4361ee; font-size:.78rem; border:none;';
        chip.textContent = title;
        chip.addEventListener('click', () => {
            titleEl.value = title;
            chipBar.classList.add('d-none');
            titleEl.focus();
        });
        chipBar.appendChild(chip);
    });
    chipBar.classList.remove('d-none');
}

// ── 5. Confirm delete ──────────────────────────────────────────────────────
function initConfirmDelete() {
    // Skip forms inside Bootstrap modals — they already show a confirmation dialog
    document.querySelectorAll('form[action*="/delete"]').forEach(form => {
        if (form.closest('.modal')) return;
        form.addEventListener('submit', e => {
            if (!confirm('Delete this note? This action cannot be undone.')) {
                e.preventDefault();
            }
        });
    });
}

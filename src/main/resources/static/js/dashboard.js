'use strict';

document.addEventListener('DOMContentLoaded', () => {
    initLiveSearch();
    initFilterBar();
    initSortToggle();
});

// ── 1. Live search with debounce ───────────────────────────────────────────
function initLiveSearch() {
    const input    = document.getElementById('navSearchInput');
    const dropdown = document.getElementById('searchDropdown');
    if (!input || !dropdown) return;

    let debounceTimer   = null;
    let activeRequest   = null;

    input.addEventListener('input', () => {
        clearTimeout(debounceTimer);
        const q = input.value.trim();

        if (!q) {
            hideDropdown();
            return;
        }

        debounceTimer = setTimeout(async () => {
            if (activeRequest) activeRequest.abort();
            activeRequest = new AbortController();

            try {
                const res = await fetch(
                    `/notes/search?keyword=${encodeURIComponent(q)}&ajax=true`,
                    { signal: activeRequest.signal }
                );
                if (res.ok) {
                    dropdown.innerHTML = await res.text();
                    dropdown.style.display = 'block';
                }
            } catch (err) {
                if (err.name !== 'AbortError') console.error('Live search error:', err);
            }
        }, 400);
    });

    // Hide on outside click
    document.addEventListener('click', e => {
        if (!input.closest('form').contains(e.target)) hideDropdown();
    });

    // Hide on Escape
    input.addEventListener('keydown', e => {
        if (e.key === 'Escape') { hideDropdown(); input.blur(); }
    });

    function hideDropdown() {
        dropdown.style.display = 'none';
        dropdown.innerHTML = '';
    }
}

// ── 2. Client-side filter bar ───────────────────────────────────────────────
function initFilterBar() {
    const grid    = document.getElementById('noteGrid');
    const bar     = document.getElementById('filterBar');
    const emptyEl = document.getElementById('filterEmpty');
    if (!grid || !bar) return;

    const cols = Array.from(grid.querySelectorAll('.note-col'));
    if (!cols.length) return;

    // Build per-folder buttons from data attrs already on the cards
    const folders = [...new Set(cols.map(c => c.dataset.folder).filter(Boolean))].sort();
    folders.forEach(folder => {
        const btn = document.createElement('button');
        btn.className = 'btn btn-sm btn-outline-secondary filter-btn';
        btn.dataset.filter = 'folder:' + folder;
        btn.innerHTML = `<i class="bi bi-folder me-1"></i>${folder}`;
        bar.appendChild(btn);
    });

    bar.addEventListener('click', e => {
        const btn = e.target.closest('.filter-btn');
        if (!btn) return;

        // Update button styles
        bar.querySelectorAll('.filter-btn').forEach(b => {
            const isActive = b === btn;
            b.classList.toggle('btn-primary', isActive);
            b.classList.toggle('btn-outline-secondary', !isActive);
        });

        // Show/hide cards
        const filter = btn.dataset.filter;
        let visible = 0;
        cols.forEach(col => {
            let show = true;
            if (filter === 'pinned')            show = col.dataset.pinned === 'true';
            else if (filter.startsWith('folder:')) show = col.dataset.folder === filter.slice(7);
            col.style.display = show ? '' : 'none';
            if (show) visible++;
        });

        if (emptyEl) emptyEl.classList.toggle('d-none', visible > 0);
    });
}

// ── 3. In-place sort toggle ─────────────────────────────────────────────────
function initSortToggle() {
    const grid    = document.getElementById('noteGrid');
    const sortEl  = document.getElementById('sortSelect');
    if (!grid || !sortEl) return;

    sortEl.addEventListener('change', () => {
        const cols = Array.from(grid.querySelectorAll('.note-col'));

        cols.sort((a, b) => {
            if (sortEl.value === 'title') {
                return (a.dataset.title || '').localeCompare(b.dataset.title || '');
            }
            // Default: last updated descending (ISO string comparison works here)
            return (b.dataset.updated || '').localeCompare(a.dataset.updated || '');
        });

        cols.forEach(col => grid.appendChild(col));
    });
}

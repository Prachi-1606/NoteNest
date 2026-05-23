'use strict';

// ── Voice notes via the browser's Web Speech API ───────────────────────────
//   Browser support: Chrome, Edge (Firefox/Safari mostly unsupported as of 2026)
//   We feature-detect on load and disable the mic button gracefully if missing.

(function () {
    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;

    document.addEventListener('DOMContentLoaded', () => {
        const micBtn = document.getElementById('voiceNoteBtn');
        if (!micBtn) return; // Only present on the note form

        if (!SpeechRec) {
            disableButton(micBtn, 'Requires Chrome or Edge');
            return;
        }

        initVoiceNotes(micBtn);
    });

    function disableButton(btn, reason) {
        btn.disabled = true;
        btn.setAttribute('title', reason);
        btn.style.opacity = '0.4';
        btn.style.cursor = 'not-allowed';
    }

    function initVoiceNotes(micBtn) {
        const contentEl = document.getElementById('content');
        const banner    = document.getElementById('voiceListeningBanner');
        const preview   = document.getElementById('voiceInterimPreview');
        if (!contentEl) return;

        const originalBtnHTML = micBtn.innerHTML;
        let recognition = null;
        let isRecording = false;

        micBtn.addEventListener('click', () => {
            if (isRecording) stop();
            else             start();
        });

        function start() {
            recognition = new SpeechRec();
            recognition.lang           = 'en-IN';
            recognition.continuous     = true;
            recognition.interimResults = true;

            recognition.onresult = (event) => {
                let interim = '';
                let finalText = '';
                for (let i = event.resultIndex; i < event.results.length; i++) {
                    const transcript = event.results[i][0].transcript;
                    if (event.results[i].isFinal) finalText += transcript;
                    else                          interim   += transcript;
                }

                if (finalText) {
                    appendToTextarea(contentEl, finalText);
                }
                if (preview) {
                    preview.textContent = interim;
                    preview.classList.toggle('d-none', !interim.trim());
                }
            };

            recognition.onerror = (event) => {
                console.error('Speech recognition error:', event.error);
                if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
                    alert('Microphone access blocked. Enable it in your browser settings and reload.');
                }
                stop(/* skipCleanup */ true);
            };

            recognition.onend = () => {
                // Fires both on manual stop and on browser-side auto-stop.
                // If user is still in "recording" state, this was a browser hiccup — restart.
                if (isRecording) {
                    try { recognition.start(); } catch (e) { /* already started */ }
                }
            };

            try {
                recognition.start();
            } catch (e) {
                console.error('Failed to start recognition:', e);
                return;
            }

            isRecording = true;
            setRecordingUI(true);
        }

        function stop(skipCleanup) {
            isRecording = false; // Set before recognition.stop() so onend doesn't auto-restart
            if (recognition) {
                try { recognition.stop(); } catch (e) { /* already stopped */ }
            }
            setRecordingUI(false);

            if (!skipCleanup) {
                triggerAiCleanup(contentEl);
            }
        }

        function setRecordingUI(recording) {
            if (recording) {
                micBtn.classList.remove('btn-outline-secondary');
                micBtn.classList.add('btn-danger', 'voice-pulse');
                micBtn.innerHTML = '<i class="bi bi-stop-fill me-1"></i>Stop Recording';
                if (banner) banner.classList.remove('d-none');
            } else {
                micBtn.classList.remove('btn-danger', 'voice-pulse');
                micBtn.classList.add('btn-outline-secondary');
                micBtn.innerHTML = originalBtnHTML;
                if (banner) banner.classList.add('d-none');
                if (preview) {
                    preview.classList.add('d-none');
                    preview.textContent = '';
                }
            }
        }
    }

    function appendToTextarea(contentEl, text) {
        const current = contentEl.value;
        const needsSpace = current && !/[\s\n]$/.test(current);
        contentEl.value = current + (needsSpace ? ' ' : '') + text.trim();
        contentEl.dispatchEvent(new Event('input')); // refresh char counter
    }

    // ── AI cleanup of voice transcript ──────────────────────────────────────

    async function triggerAiCleanup(contentEl) {
        const original = contentEl.value.trim();
        if (!original) return;

        try {
            const noteId = document.getElementById('noteId')?.value || '0';
            const body = new URLSearchParams();
            body.append('content', original);

            const res = await fetch('/ai/fix-grammar/' + noteId, {
                method:  'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body
            });
            if (!res.ok) return;

            const improved = (await res.text()).trim();
            if (!improved || improved === original) return;

            showCleanupToast(contentEl, improved);
        } catch (err) {
            console.error('AI cleanup error:', err);
        }
    }

    function showCleanupToast(contentEl, improvedText) {
        const toastEl    = document.getElementById('voiceCleanupToast');
        const acceptBtn  = document.getElementById('voiceCleanupAcceptBtn');
        const dismissBtn = document.getElementById('voiceCleanupDismissBtn');
        if (!toastEl || !acceptBtn || !dismissBtn || !window.bootstrap) return;

        const bsToast = bootstrap.Toast.getOrCreateInstance(toastEl, { autohide: false });

        // Replace nodes to wipe any prior click listeners
        const freshAccept  = acceptBtn.cloneNode(true);
        const freshDismiss = dismissBtn.cloneNode(true);
        acceptBtn.replaceWith(freshAccept);
        dismissBtn.replaceWith(freshDismiss);

        freshAccept.addEventListener('click', () => {
            contentEl.value = improvedText;
            contentEl.dispatchEvent(new Event('input'));
            bsToast.hide();
        });
        freshDismiss.addEventListener('click', () => bsToast.hide());

        bsToast.show();
    }
})();

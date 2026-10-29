(function() {
    'use strict';

    if (window.__dizilla_adblock_injected) return;
    window.__dizilla_adblock_injected = true;

    // 1. Override window.open to block pop-ups
    try {
        window.open = function() {
            console.log('[AdBlock] window.open popup intercepted and blocked');
            return null;
        };
    } catch(e) {}

    // 2. Targeted CSS rules without greedy substring matches
    const adStyles = `
        iframe[src*="doubleclick"], iframe[src*="adsterra"],
        iframe[src*="popads"], iframe[src*="propeller"], iframe[src*="exoclick"],
        iframe[src*="/ads/"], iframe[src*="adservice"],
        [id^="ad-"], [id^="ad_"], [id$="-ad"], [id$="_ad"],
        [class^="ad-"], [class^="ad_"], [class$="-ad"], [class$="_ad"],
        [class~="ad"], [class*="adsbox"], [class*="reklam"], [id*="reklam"],
        [id*="banner-ad"], [class*="banner-ad"],
        [class*="pop-overlay"], .preroll-ad, .ad-countdown,
        a[href*="bet365"], a[href*="1xbet"], a[href*="bets10"], a[href*="casinomaxi"],
        a[href*="melbet"], a[href*="mostbet"],
        .ad-slot, .ad-wrapper, .ad-placeholder, #adblock-notice,
        div[class*="sponsor"] {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            width: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }

        @media (orientation: landscape) {
            html, body {
                width: 100% !important;
                max-width: 100% !important;
            }
        }
    `;

    function injectCSS() {
        if (!document.head) return;
        if (document.querySelector('style[data-adblock="true"]')) return;
        const style = document.createElement('style');
        style.type = 'text/css';
        style.setAttribute('data-adblock', 'true');
        style.appendChild(document.createTextNode(adStyles));
        document.head.appendChild(style);
    }

    if (document.head) injectCSS();
    else document.addEventListener('DOMContentLoaded', injectCSS);

    // 3. Lightweight Click-hijack & Transparent Overlay Remover
    function removeClickHijackers() {
        try {
            // Only inspect top-level body elements and overlay candidates, never all elements
            const candidates = document.querySelectorAll('body > div, body > a, .pop-overlay, [class*="overlay"], [id*="overlay"]');
            const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
            const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);

            for (let i = 0; i < candidates.length; i++) {
                const el = candidates[i];
                if (el.tagName === 'VIDEO' || el.querySelector('video') || el.querySelector('iframe')) continue;
                if (el.classList && (el.classList.contains('jw-wrapper') || el.classList.contains('vjs-tech') || el.classList.contains('player'))) continue;

                const style = window.getComputedStyle(el);
                if (style.position === 'fixed' || style.position === 'absolute') {
                    const zIndex = parseInt(style.zIndex, 10);
                    if (!isNaN(zIndex) && zIndex > 90) {
                        const rect = el.getBoundingClientRect();
                        if (rect.width >= vw * 0.7 && rect.height >= vh * 0.7) {
                            const bg = style.backgroundColor;
                            const isTransparent = bg === 'transparent' || bg.includes('rgba(0, 0, 0, 0)') || parseFloat(style.opacity) < 0.1;
                            const hasNoVisibleText = el.innerText.trim().length === 0;

                            if (isTransparent || hasNoVisibleText) {
                                console.log('[AdBlock] Removed click-hijack overlay:', el);
                                el.remove();
                            }
                        }
                    }
                }
            }
        } catch(e) {}
    }

    // 4. Video Pre-roll Ad Cleanup (without removing player skip buttons)
    function cleanVideoAdOverlays() {
        try {
            const videos = document.querySelectorAll('video');
            videos.forEach(v => {
                const parent = v.parentElement;
                if (parent) {
                    // Safe selectors that never delete "skip intro" or player skip buttons
                    const overlays = parent.querySelectorAll('[id^="ad-"], [class^="ad-"], [id^="ad_"], [class^="ad_"], [class*="preroll"], .video-ad');
                    overlays.forEach(overlay => {
                        if (overlay !== v && !overlay.contains(v)) {
                            console.log('[AdBlock] Removed video pre-roll ad overlay:', overlay);
                            overlay.remove();
                        }
                    });
                }
            });
        } catch(e) {}
    }

    // 5. Video Detection including iframes (same-origin check)
    function detectVideoElements() {
        try {
            const videos = document.querySelectorAll('video');
            videos.forEach(v => {
                if (v.src && v.src.startsWith('http')) {
                    notifyVideoFound(v.src);
                }
                const source = v.querySelector('source');
                if (source && source.src && source.src.startsWith('http')) {
                    notifyVideoFound(source.src);
                }
            });

            const iframes = document.querySelectorAll('iframe');
            iframes.forEach(iframe => {
                try {
                    const doc = iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document);
                    if (doc) {
                        const iframeVideos = doc.querySelectorAll('video');
                        iframeVideos.forEach(iv => {
                            if (iv.src && iv.src.startsWith('http')) {
                                notifyVideoFound(iv.src);
                            }
                        });
                    }
                } catch(crossOriginErr) {
                    // Cross-origin handled natively in WebView
                }
            });
        } catch(e) {}
    }

    function notifyVideoFound(url) {
        if (!url || typeof url !== 'string') return;
        // Skip obvious tracking and ad videos
        const lower = url.toLowerCase();
        if (lower.includes('doubleclick') || lower.includes('googleads') || lower.includes('popads') ||
            lower.includes('vignette') || lower.includes('adsystem') || lower.includes('adnxs')) {
            return;
        }
        if (window.DizillaNative && window.DizillaNative.onVideoDetected) {
            window.DizillaNative.onVideoDetected(url);
        }
    }

    // 6. Discovery Mode: Gather all iframes and video/source src values
    let discoveryTimer = null;
    function schedulePlayerInfoDiscovery() {
        if (discoveryTimer) clearTimeout(discoveryTimer);
        discoveryTimer = setTimeout(() => {
            try {
                const iframes = Array.from(document.querySelectorAll('iframe')).map(f => f.src || f.dataset.src || '(no-src)').filter(Boolean);
                const videos = Array.from(document.querySelectorAll('video')).map(v => v.src || v.currentSrc || '').filter(Boolean);
                const sources = Array.from(document.querySelectorAll('video > source')).map(s => s.src || '').filter(Boolean);

                const data = {
                    currentUrl: window.location.href,
                    timestamp: Date.now(),
                    iframes: iframes,
                    videos: Array.from(new Set([...videos, ...sources]))
                };

                if (window.DizillaNative && window.DizillaNative.onPlayerInfoDiscovered) {
                    window.DizillaNative.onPlayerInfoDiscovered(JSON.stringify(data));
                }
            } catch(e) {
                console.error('[PlayerInfo] Error collecting player info', e);
            }
        }, 4000);
    }

    // Debounced runner to prevent DOM stutter
    let cleanupDebounceTimer = null;
    function runFullCleanupDebounced() {
        if (cleanupDebounceTimer) clearTimeout(cleanupDebounceTimer);
        cleanupDebounceTimer = setTimeout(() => {
            injectCSS();
            removeClickHijackers();
            cleanVideoAdOverlays();
            detectVideoElements();
        }, 350);
    }

    // 7. SPA Navigation Interceptor (Next.js history.pushState & replaceState & popstate)
    function onSPANavigation(route) {
        console.log('[AdBlock] SPA route changed to:', route || window.location.pathname);
        runFullCleanupDebounced();
        schedulePlayerInfoDiscovery();
    }

    try {
        const originalPushState = history.pushState;
        history.pushState = function(state, unused, url) {
            const ret = originalPushState.apply(this, arguments);
            onSPANavigation(url);
            return ret;
        };

        const originalReplaceState = history.replaceState;
        history.replaceState = function(state, unused, url) {
            const ret = originalReplaceState.apply(this, arguments);
            onSPANavigation(url);
            return ret;
        };

        window.addEventListener('popstate', function() {
            onSPANavigation(window.location.pathname);
        });
    } catch(e) {
        console.warn('[AdBlock] Failed to wrap history state', e);
    }

    // 8. MutationObserver with debouncing
    const observer = new MutationObserver(() => {
        runFullCleanupDebounced();
    });

    function startObserver() {
        const target = document.body || document.documentElement;
        if (target) {
            observer.observe(target, { childList: true, subtree: true });
            runFullCleanupDebounced();
            schedulePlayerInfoDiscovery();
        }
    }

    if (document.body) {
        startObserver();
    } else {
        document.addEventListener('DOMContentLoaded', startObserver);
    }

    document.addEventListener('play', detectVideoElements, true);

})();

(function() {
    'use strict';

    if (window.__dizilla_adblock_injected) return;
    window.__dizilla_adblock_injected = true;

    // 1. Override window.open to prevent popup windows
    try {
        window.open = function() {
            console.log('[AdBlock] window.open intercepted and blocked');
            return null;
        };
    } catch(e) {}

    // 2. Inject CSS rules to hide ad elements
    const adStyles = `
        iframe[src*="ads"], iframe[src*="doubleclick"], iframe[src*="adsterra"],
        iframe[src*="popads"], iframe[src*="propeller"], iframe[src*="exoclick"],
        [id*="banner"], [id*="reklam"], [class*="adsbox"], [class*="reklam"],
        [class*="ad-banner"], [id*="ad-container"], [class*="banner-ads"],
        [class*="ad_box"], [id*="overlay-ad"], [class*="pop-overlay"],
        [class*="video-ads"], [id*="video-ad"], .preroll-ad, .ad-countdown,
        a[href*="bet365"], a[href*="1xbet"], a[href*="bets10"], a[href*="casinomaxi"],
        a[href*="melbet"], a[href*="mostbet"],
        .ad-slot, .ad-wrapper, .ad-placeholder, #adblock-notice,
        div[id^="ad_"], div[class^="ad_"], div[id*="_ad_"], div[class*="_ad_"],
        div[class*="sponsor"] {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            width: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
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

    // 3. Click-hijack & Transparent Overlay Remover
    function removeClickHijackers() {
        try {
            const allDivs = document.querySelectorAll('div, a, span');
            const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
            const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);

            for (let i = 0; i < allDivs.length; i++) {
                const el = allDivs[i];
                if (el.tagName === 'VIDEO' || el.querySelector('video') || el.querySelector('iframe')) continue;
                if (el.classList.contains('jw-wrapper') || el.classList.contains('vjs-tech')) continue;

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

    // 4. Video Pre-roll Ad Cleanup
    function cleanVideoAdOverlays() {
        try {
            const videos = document.querySelectorAll('video');
            videos.forEach(v => {
                const parent = v.parentElement;
                if (parent) {
                    const overlays = parent.querySelectorAll('[id*="ad"], [class*="ad"], [class*="skip"], [id*="skip"], [class*="countdown"]');
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

    function runFullCleanup() {
        injectCSS();
        removeClickHijackers();
        cleanVideoAdOverlays();
        detectVideoElements();
    }

    // 5. Video Detection including iframes (same-origin check)
    function detectVideoElements() {
        try {
            // Check top level videos
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

            // Check iframes
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
                    // Cross-origin iframe handled via WebView shouldInterceptRequest
                }
            });
        } catch(e) {}
    }

    function notifyVideoFound(url) {
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

                console.log('[PlayerInfo] Discovered player elements:', data);
                if (window.DizillaNative && window.DizillaNative.onPlayerInfoDiscovered) {
                    window.DizillaNative.onPlayerInfoDiscovered(JSON.stringify(data));
                }
            } catch(e) {
                console.error('[PlayerInfo] Error collecting player info', e);
            }
        }, 5000);
    }

    // 7. SPA Navigation Interceptor (Next.js history.pushState & replaceState & popstate)
    function onSPANavigation(route) {
        console.log('[AdBlock] SPA route changed to:', route || window.location.pathname);
        runFullCleanup();
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

    // 8. Permanent MutationObserver for dynamically added videos/iframes/ads
    const observer = new MutationObserver(() => {
        runFullCleanup();
    });

    function startObserver() {
        const target = document.body || document.documentElement;
        if (target) {
            observer.observe(target, { childList: true, subtree: true });
            runFullCleanup();
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

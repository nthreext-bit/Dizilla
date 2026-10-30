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
    // Not: "ads" gibi kısa alt dizgiler "uploads", "heads" gibi sözcükleri de eşleştirdiği için
    // iframe seçicileri host/yol sınırlarına bağlandı (player iframe'i yanlışlıkla gizlenmesin).
    const adStyles = `
        img[src*="/allads/"], iframe[src*="/allads/"], a[href*="/allads/"],
        iframe[src*="//ads."], iframe[src*=".ads."], iframe[src*="/ads/"], iframe[src*="/adserver"],
        iframe[src*="doubleclick"], iframe[src*="adsterra"],
        iframe[src*="popads"], iframe[src*="propeller"], iframe[src*="exoclick"],
        [id*="ad-banner"], [id^="banner-ad"], [id*="reklam"], [class*="adsbox"], [class*="reklam"],
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

    // Reklam öğesi mi? "ad" yalnızca ayrı bir sözcük (ad, ads, advert, reklam...) olarak eşleşir;
    // "loading", "header", "ready" gibi sözcükler ve oynatıcının skip/ileri-geri düğmeleri etkilenmez.
    const AD_TOKEN = /(^|[\s_-])(ad|ads|advert|advertisement|reklam|preroll|adskip|skip-ad|ad-skip|ad-countdown|ad-timer)($|[\s_-])/i;
    function isAdLike(el) {
        const cls = (el.getAttribute && el.getAttribute('class')) || '';
        const id = el.id || '';
        return AD_TOKEN.test(cls) || AD_TOKEN.test(id);
    }

    // 3. Click-hijack & Transparent Overlay Remover
    // Performans: tüm div/a/span yerine yalnızca body'nin 2 seviye altındaki öğeler taranır.
    function removeClickHijackers() {
        try {
            if (!document.body) return;
            const vw = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
            const vh = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);
            const candidates = [];
            const top = document.body.children;
            for (let i = 0; i < top.length; i++) {
                candidates.push(top[i]);
                const kids = top[i].children;
                for (let j = 0; j < kids.length && j < 30; j++) candidates.push(kids[j]);
            }

            for (let i = 0; i < candidates.length; i++) {
                const el = candidates[i];
                const tag = el.tagName;
                if (tag !== 'DIV' && tag !== 'A' && tag !== 'SPAN') continue;
                if (el.querySelector('video') || el.querySelector('iframe')) continue;
                if (el.classList.contains('jw-wrapper') || el.classList.contains('vjs-tech')) continue;

                const style = window.getComputedStyle(el);
                if (style.position === 'fixed' || style.position === 'absolute') {
                    const zIndex = parseInt(style.zIndex, 10);
                    if (!isNaN(zIndex) && zIndex > 90) {
                        const rect = el.getBoundingClientRect();
                        if (rect.width >= vw * 0.7 && rect.height >= vh * 0.7) {
                            const bg = style.backgroundColor;
                            const isTransparent = bg === 'transparent' || bg.includes('rgba(0, 0, 0, 0)') || parseFloat(style.opacity) < 0.1;
                            const hasNoVisibleText = (el.innerText || '').trim().length === 0;

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

    // 4. Video Pre-roll Ad Cleanup (yalnızca gerçekten reklam gibi görünen kardeş öğeler)
    function cleanVideoAdOverlays() {
        try {
            const videos = document.querySelectorAll('video');
            videos.forEach(v => {
                const parent = v.parentElement;
                if (!parent) return;
                const kids = parent.children;
                for (let i = kids.length - 1; i >= 0; i--) {
                    const overlay = kids[i];
                    if (overlay === v || overlay.contains(v)) continue;
                    if (isAdLike(overlay)) {
                        console.log('[AdBlock] Removed video pre-roll ad overlay:', overlay);
                        overlay.remove();
                    }
                }
            });
        } catch(e) {}
    }

    // 5. Video Detection including iframes (same-origin check)
    const notified = new Set();
    function notifyVideoFound(url) {
        if (!url || notified.has(url)) return;
        notified.add(url);
        if (window.DizillaNative && window.DizillaNative.onVideoDetected) {
            window.DizillaNative.onVideoDetected(url);
        }
    }

    function detectVideoElements() {
        try {
            const videos = document.querySelectorAll('video');
            videos.forEach(v => {
                if (v.src && v.src.startsWith('http')) notifyVideoFound(v.src);
                const source = v.querySelector('source');
                if (source && source.src && source.src.startsWith('http')) notifyVideoFound(source.src);
            });

            const iframes = document.querySelectorAll('iframe');
            iframes.forEach(iframe => {
                try {
                    const doc = iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document);
                    if (doc) {
                        doc.querySelectorAll('video').forEach(iv => {
                            if (iv.src && iv.src.startsWith('http')) notifyVideoFound(iv.src);
                        });
                    }
                } catch(crossOriginErr) {
                    // Cross-origin iframe handled via WebView shouldInterceptRequest
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

    // Debounce: DOM değişim fırtınasında (özellikle zayıf TV/Pi cihazlarda) en fazla ~5 kez/sn çalışır
    let cleanupTimer = null;
    function scheduleCleanup() {
        if (cleanupTimer) return;
        cleanupTimer = setTimeout(function() {
            cleanupTimer = null;
            runFullCleanup();
        }, 200);
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
        scheduleCleanup();
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

    // 8. Permanent MutationObserver for dynamically added videos/iframes/ads (debounced)
    const observer = new MutationObserver(scheduleCleanup);

    function startObserver() {
        const target = document.body || document.documentElement;
        if (target) {
            observer.observe(target, { childList: true, subtree: true });
            scheduleCleanup();
            schedulePlayerInfoDiscovery();
        }
    }

    if (document.body) {
        startObserver();
    } else {
        document.addEventListener('DOMContentLoaded', startObserver);
    }

    document.addEventListener('play', detectVideoElements, true);

    // 9. Kumanda / medya tuşları için <video> kontrolü.
    // Native taraf window.__dzMedia('play'|'pause'|'toggle'|'seek+10'|'seek-10'|'fs') çağırır.
    // Video bu çerçevede yoksa komut alt iframe'lere postMessage ile iletilir (oynatıcılar
    // genellikle iframe içindedir).
    function applyMedia(cmd) {
        try {
            const v = document.querySelector('video');
            if (v) {
                switch (cmd) {
                    case 'play': v.play(); break;
                    case 'pause': v.pause(); break;
                    case 'toggle': if (v.paused) v.play(); else v.pause(); break;
                    case 'seek+10': v.currentTime = Math.min(isFinite(v.duration) ? v.duration : Infinity, v.currentTime + 10); break;
                    case 'seek-10': v.currentTime = Math.max(0, v.currentTime - 10); break;
                    case 'fs':
                        if (document.fullscreenElement || document.webkitFullscreenElement) {
                            (document.exitFullscreen || document.webkitExitFullscreen).call(document);
                        } else {
                            const t = v.parentElement || v;
                            const req = t.requestFullscreen || t.webkitRequestFullscreen || v.webkitEnterFullscreen;
                            if (req) req.call(t);
                        }
                        break;
                }
                return;
            }
            document.querySelectorAll('iframe').forEach(f => {
                try { f.contentWindow.postMessage({ __dz: cmd }, '*'); } catch (e) {}
            });
        } catch (e) {}
    }
    window.__dzMedia = applyMedia;
    window.addEventListener('message', function(e) {
        const d = e.data;
        if (d && typeof d === 'object' && typeof d.__dz === 'string') applyMedia(d.__dz);
    });

})();

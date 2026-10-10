(() => {
  // Reloading or updating the extension leaves the previous script running in open tabs; retire it first.
  window.dispatchEvent(new Event('snowscreen:retire'));
  for (const stale of document.querySelectorAll('snowscreen-overlay')) stale.remove();
  const host = document.createElement('snowscreen-overlay');
  host.setAttribute('aria-hidden', 'true');
  host.style.cssText = 'all:initial!important;position:fixed!important;inset:0!important;width:100%!important;height:100%!important;pointer-events:none!important;z-index:2147483647!important;overflow:hidden!important;';
  const shadow = host.attachShadow({ mode: 'closed' });
  const canvas = document.createElement('canvas');
  canvas.style.cssText = 'display:block;width:100%;height:100%;pointer-events:none;';
  shadow.append(canvas);
  const engine = new SnowEngine(canvas, SnowSettings.defaults);
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  let settings = SnowSettings.defaults;
  let retired = false;
  const resize = () => engine.resize(innerWidth, innerHeight, devicePixelRatio);
  // An orphaned script no longer receives storage events, so it must not keep snowing on stale settings.
  const alive = () => { try { return Boolean(chrome.runtime?.id); } catch { return false; } };
  // Older releases cannot be retired and re-attach their overlay when the tab is shown again.
  const intruders = new MutationObserver(() => {
    for (const node of document.querySelectorAll('snowscreen-overlay')) if (node !== host) node.remove();
  });
  intruders.observe(document.documentElement, { childList: true });
  function retire() {
    retired = true; engine.stop(); host.remove(); intruders.disconnect(); clearInterval(watchdog);
  }
  // Disabling or removing the extension sends no event to the page, so poll for it.
  const watchdog = setInterval(() => { if (!alive()) retire(); }, 2000);
  function apply() {
    if (retired) return;
    if (!alive()) { retire(); return; }
    engine.update(settings);
    const active = settings.enabled && !document.hidden && !reducedMotion.matches;
    if (active) {
      if (!host.isConnected) { document.documentElement.append(host); resize(); }
      engine.start();
    } else { engine.stop(); host.remove(); }
  }
  // Register before reading storage so a live update cannot be missed.
  let revision = 0;
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes.snowSettings) {
      revision++; settings = SnowSettings.normalize(changes.snowSettings.newValue); apply();
    }
  });
  // Storage is the single source of truth for every tab; a read never overrides a newer event.
  function sync(fallback) {
    if (retired) return;
    if (!alive()) { retire(); return; }
    const seen = revision;
    chrome.storage.local.get('snowSettings').then(data => {
      if (revision === seen) { settings = SnowSettings.normalize(data.snowSettings); apply(); }
    }).catch(() => { if (fallback) settings = fallback; apply(); });
  }
  sync(SnowSettings.normalize());
  // Frozen or cached tabs can miss storage events, so re-read whenever the tab comes back.
  const show = () => { apply(); if (!document.hidden) sync(); };
  window.addEventListener('snowscreen:retire', retire);
  window.addEventListener('resize', resize, { passive: true });
  window.addEventListener('pointermove', event => { engine.pointer = event.clientX / Math.max(1, innerWidth) * 2 - 1; }, { passive: true });
  document.addEventListener('pointerleave', () => { engine.pointer = 0; });
  document.addEventListener('visibilitychange', show);
  reducedMotion.addEventListener('change', apply);
  window.addEventListener('pagehide', () => engine.stop());
  window.addEventListener('pageshow', show);
})();

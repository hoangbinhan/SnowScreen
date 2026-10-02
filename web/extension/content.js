(() => {
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
  const resize = () => engine.resize(innerWidth, innerHeight, devicePixelRatio);
  function apply() {
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
  const initialRevision = revision;
  chrome.storage.local.get('snowSettings').then(data => {
    if (revision === initialRevision) { settings = SnowSettings.normalize(data.snowSettings); apply(); }
  }).catch(() => { settings = SnowSettings.normalize(); apply(); });
  window.addEventListener('resize', resize, { passive: true });
  window.addEventListener('pointermove', event => { engine.pointer = event.clientX / Math.max(1, innerWidth) * 2 - 1; }, { passive: true });
  document.addEventListener('pointerleave', () => { engine.pointer = 0; });
  document.addEventListener('visibilitychange', apply);
  reducedMotion.addEventListener('change', apply);
  window.addEventListener('pagehide', () => engine.stop());
  window.addEventListener('pageshow', apply);
})();

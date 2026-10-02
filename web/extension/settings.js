(() => {
  const presets = {
    gentle: { count: 70, speed: 0.7, size: 0.4, wind: -1 },
    blizzard: { count: 250, speed: 2.2, size: 1.3, wind: 1.5 },
    magical: { count: 180, speed: 1.2, size: 1.5, wind: 0 }
  };
  const defaults = { enabled: true, preset: 'gentle', ...presets.gentle,
    gusts: true, pointerWind: false, opacity: 0.85, shapes: [0, 1, 2, 3, 4, 5, 6, 7] };
  function normalize(raw = {}) {
    const result = { ...defaults, ...raw };
    for (const [key, min, max] of [['count', 10, 350], ['speed', 0.2, 3],
      ['size', 0.3, 2], ['wind', -3, 3], ['opacity', 0.1, 1]]) {
      result[key] = Number.isFinite(result[key]) ? Math.max(min, Math.min(max, result[key])) : defaults[key];
    }
    result.count = Math.round(result.count);
    for (const key of ['enabled', 'gusts', 'pointerWind']) result[key] = typeof result[key] === 'boolean' ? result[key] : defaults[key];
    result.shapes = Array.isArray(result.shapes) ? [...new Set(result.shapes.filter(n => Number.isInteger(n) && n >= 0 && n <= 7))] : defaults.shapes;
    if (!result.shapes.length) result.shapes = [0];
    if (!['gentle', 'blizzard', 'magical', 'custom'].includes(result.preset)) result.preset = 'custom';
    return result;
  }
  globalThis.SnowSettings = { defaults, presets, normalize };
})();

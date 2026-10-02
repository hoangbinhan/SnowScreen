(() => {
  let settings;
  let queue = Promise.resolve();
  const ids = ['preset', 'count', 'speed', 'size', 'wind', 'opacity', 'gusts', 'pointerWind'];
  const power = document.getElementById('enabled');
  const fields = Object.fromEntries(ids.map(id => [id, document.getElementById(id)]));
  const shapes = [...document.querySelectorAll('[name=shape]')];
  const status = document.getElementById('status');
  function render() {
    power.setAttribute('aria-pressed', String(settings.enabled));
    document.getElementById('power-label').textContent = settings.enabled ? 'Stop snow' : 'Start snow';
    document.getElementById('power-state').textContent = settings.enabled ? 'ON' : 'OFF';
    for (const id of ids) {
      const field = fields[id];
      if (field.type === 'checkbox') field.checked = settings[id];
      else field.value = settings[id];
      const output = document.getElementById(`${id}-value`);
      if (output) output.textContent = id === 'opacity' ? `${Math.round(settings[id] * 100)}%` : id === 'count' || id === 'wind' ? settings[id] : `${settings[id].toFixed(1)}×`;
    }
    for (const shape of shapes) shape.checked = settings.shapes.includes(Number(shape.value));
  }
  function save() {
    const snapshot = SnowSettings.normalize(settings);
    status.textContent = 'Saving…';
    // Serialize writes so rapid slider changes finish with the latest value.
    queue = queue.then(() => chrome.storage.local.set({ snowSettings: snapshot }))
      .then(() => { status.textContent = 'Saved'; })
      .catch(() => { status.textContent = 'Could not save. Please try again.'; });
  }
  async function init() {
    try {
      const data = await chrome.storage.local.get('snowSettings');
      settings = SnowSettings.normalize(data.snowSettings);
    } catch { settings = SnowSettings.normalize(); status.textContent = 'Could not read saved settings.'; }
    render();
    power.disabled = false;
    power.addEventListener('click', () => { settings.enabled = !settings.enabled; render(); save(); });
    for (const id of ids) fields[id].addEventListener('input', () => {
      const field = fields[id];
      settings[id] = field.type === 'checkbox' ? field.checked : field.type === 'range' ? Number(field.value) : field.value;
      if (id === 'preset' && SnowSettings.presets[settings.preset]) Object.assign(settings, SnowSettings.presets[settings.preset]);
      else if (['count', 'speed', 'size', 'wind'].includes(id)) settings.preset = 'custom';
      render(); save();
    });
    for (const shape of shapes) shape.addEventListener('change', () => {
      const selected = shapes.filter(field => field.checked).map(field => Number(field.value));
      if (!selected.length) { shape.checked = true; status.textContent = 'Keep at least one snowflake shape selected.'; return; }
      settings.shapes = selected; save();
    });
    document.getElementById('reset').addEventListener('click', () => { settings = SnowSettings.normalize(); render(); save(); });
  }
  init();
})();

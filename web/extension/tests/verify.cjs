const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const manifest = JSON.parse(read('manifest.json'));
assert.equal(manifest.manifest_version, 3);
assert.deepEqual(manifest.permissions, ['storage', 'scripting']);
assert.deepEqual(manifest.host_permissions, manifest.content_scripts[0].matches);
for (const name of [...manifest.content_scripts[0].js, manifest.action.default_popup, manifest.background.service_worker, 'popup.css', 'popup.js']) assert(fs.existsSync(path.join(root, name)), name);
for (const name of ['settings.js', 'snow.js', 'content.js', 'popup.js', 'background.js']) new vm.Script(read(name), { filename: name });
let frames = new Map(), nextFrame = 0;
const context = vm.createContext({ console, Math, requestAnimationFrame: callback => {
  frames.set(++nextFrame, callback); return nextFrame;
}, cancelAnimationFrame: id => frames.delete(id) });
vm.runInContext(read('settings.js'), context);
vm.runInContext(read('snow.js'), context);
const settings = context.SnowSettings;
assert.equal(settings.normalize({ count: 999, shapes: [] }).count, 350);
assert.equal(settings.normalize({ speed: NaN }).speed, 0.7);
assert.equal(settings.normalize({ enabled: 'false' }).enabled, true);
assert.equal(settings.normalize({ shapes: [-1, 7, 7, 10] }).shapes.join(), '7');
const ctx = new Proxy({}, { get: (target, key) => target[key] || (() => {}), set: (target, key, value) => { target[key] = value; return true; } });
const canvas = { getContext: () => ctx };
const engine = new context.SnowEngine(canvas, settings.defaults);
engine.resize(1200, 800, 3);
assert.equal(canvas.width, 2400);
assert.equal(engine.flakes.length, 70);
engine.start(); engine.start(); assert.equal(frames.size, 1);
function step(time) {
  const [id, callback] = frames.entries().next().value;
  frames.delete(id); callback(time);
}
step(0);
for (let i = 1; i <= 600; i++) step(i * 16.67);
for (const flake of engine.flakes) assert(Number.isFinite(flake.x) && Number.isFinite(flake.y));
const before = engine.flakes[0].y;
step(1000000);
assert(Math.abs(engine.flakes[0].y - before) < 100, 'Long frame must be clamped');
for (let type = 0; type < 8; type++) {
  engine.update({ ...settings.defaults, count: 10, shapes: [type] });
  assert(engine.flakes.every(flake => flake.type === type));
  step(1000016 + type * 16);
}
engine.stop(); assert.equal(frames.size, 0);
engine.start(); assert.equal(frames.size, 1);
engine.stop();
// Exercise actual content script with browser APIs mocked, including storage races.
const storageListeners = [], reads = [], hosts = [], observers = new Set();
const events = {}, windowEvents = {}, children = new Set();
const emit = value => { for (const fn of storageListeners) fn({ snowSettings: { newValue: value } }, 'local'); };
const settle = () => new Promise(resolve => setImmediate(resolve));
const node = () => ({ style: {}, isConnected: false, setAttribute() {}, attachShadow: () => ({ append() {} }),
  remove() { children.delete(this); this.isConnected = false; } });
const motion = { matches: false, addEventListener: (_, fn) => { events.motion = fn; } };
context.document = { hidden: false, querySelectorAll: () => [...children],
  createElement: name => { if (name === 'canvas') return { ...canvas, style: {} }; hosts.push(node()); return hosts.at(-1); },
  documentElement: { append(child) { children.add(child); child.isConnected = true; for (const fn of [...observers]) fn(); } },
  addEventListener: (name, fn) => { events[name] = fn; } };
context.window = { addEventListener: (name, fn) => { windowEvents[name] = fn; }, dispatchEvent: event => windowEvents[event.type]?.(event) };
context.Event = class { constructor(type) { this.type = type; } };
context.MutationObserver = class {
  constructor(fn) { this.fn = fn; }
  observe() { observers.add(this.fn); }
  disconnect() { observers.delete(this.fn); }
};
const timers = new Set();
context.setInterval = fn => { timers.add(fn); return fn; };
context.clearInterval = fn => timers.delete(fn);
context.innerWidth = 1200; context.innerHeight = 800; context.devicePixelRatio = 1;
context.matchMedia = () => motion;
context.chrome = { runtime: { id: 'test' }, storage: { onChanged: { addListener: fn => { storageListeners.push(fn); } },
  local: { get: () => new Promise(resolve => { reads.push(resolve); }) } } };
(async () => {
  vm.runInContext(read('content.js'), context);
  const first = hosts[0];
  emit({ enabled: false });
  reads[0]({ snowSettings: { enabled: true } }); await settle();
  assert.equal(first.isConnected, false, 'Initial read must not override newer storage event');
  emit({ enabled: true });
  assert.equal(first.isConnected, true); assert.equal(frames.size, 1);
  context.document.hidden = true; events.visibilitychange(); assert.equal(frames.size, 0);
  context.document.hidden = false; events.visibilitychange(); assert.equal(frames.size, 1);
  motion.matches = true; events.motion(); assert.equal(frames.size, 0);
  motion.matches = false; events.motion(); assert.equal(frames.size, 1);
  windowEvents.pagehide(); assert.equal(frames.size, 0);
  windowEvents.pageshow(); assert.equal(frames.size, 1);
  emit({ enabled: false });
  assert.equal(frames.size, 0); assert.equal(first.isConnected, false);
  // Reads started before the last event are stale.
  reads[1]({ snowSettings: { enabled: true } }); reads[2]({ snowSettings: { enabled: true } }); await settle();
  assert.equal(first.isConnected, false, 'Stale read must not override newer storage event');
  // A tab that missed the storage event catches up when it is shown.
  events.visibilitychange(); assert.equal(first.isConnected, false);
  reads[3]({ snowSettings: { enabled: true } }); await settle();
  assert.equal(first.isConnected, true, 'Showing a tab must resync from storage'); assert.equal(frames.size, 1);
  const intruder = node(); context.document.documentElement.append(intruder);
  assert.equal(intruder.isConnected, false, 'Foreign overlay must be removed'); assert.equal(first.isConnected, true);
  // A second injection (extension reload or update) retires the first instance.
  vm.runInContext(read('content.js'), context);
  const second = hosts[1];
  assert.equal(first.isConnected, false); assert.equal(frames.size, 0);
  reads[4]({ snowSettings: { enabled: true } }); await settle();
  assert.equal(second.isConnected, true); assert.equal(frames.size, 1);
  emit({ enabled: false }); assert.equal(frames.size, 0); assert.equal(children.size, 0);
  emit({ enabled: true }); assert.equal(frames.size, 1);
  assert.equal(first.isConnected, false, 'Retired instance must ignore storage events'); assert.equal(second.isConnected, true);
  // An orphaned instance shuts itself down instead of snowing on stale settings.
  assert.equal(timers.size, 1, 'Retired instance must clear its watchdog');
  context.chrome.runtime.id = undefined; for (const fn of [...timers]) fn();
  assert.equal(frames.size, 0); assert.equal(second.isConnected, false); assert.equal(timers.size, 0);
  emit({ enabled: true }); assert.equal(frames.size, 0); assert.equal(children.size, 0);
  console.log('PASS: manifest, script syntax, settings, 8 shapes, particle simulation, frame lifecycle, content storage race, tab resync and instance retirement.');
})().catch(error => { console.error(error); process.exitCode = 1; });

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
const manifest = JSON.parse(read('manifest.json'));
assert.equal(manifest.manifest_version, 3);
assert.deepEqual(manifest.permissions, ['storage']);
for (const name of [...manifest.content_scripts[0].js, manifest.action.default_popup, 'popup.css', 'popup.js']) assert(fs.existsSync(path.join(root, name)), name);
for (const name of ['settings.js', 'snow.js', 'content.js', 'popup.js']) new vm.Script(read(name), { filename: name });
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
let storageListener, resolveStorage;
const events = {}, windowEvents = {};
const host = { style: {}, isConnected: false, setAttribute() {}, attachShadow: () => ({ append() {} }), remove() { this.isConnected = false; } };
const motion = { matches: false, addEventListener: (_, fn) => { events.motion = fn; } };
context.document = { hidden: false, createElement: name => name === 'canvas' ? { ...canvas, style: {} } : host,
  documentElement: { append(node) { node.isConnected = true; } }, addEventListener: (name, fn) => { events[name] = fn; } };
context.window = { addEventListener: (name, fn) => { windowEvents[name] = fn; } };
context.innerWidth = 1200; context.innerHeight = 800; context.devicePixelRatio = 1;
context.matchMedia = () => motion;
context.chrome = { storage: { onChanged: { addListener: fn => { storageListener = fn; } },
  local: { get: () => new Promise(resolve => { resolveStorage = resolve; }) } } };
vm.runInContext(read('content.js'), context);
storageListener({ snowSettings: { newValue: { enabled: false } } }, 'local');
resolveStorage({ snowSettings: { enabled: true } });
Promise.resolve().then(() => {
  assert.equal(host.isConnected, false, 'Initial read must not override newer storage event');
  storageListener({ snowSettings: { newValue: { enabled: true } } }, 'local');
  assert.equal(host.isConnected, true); assert.equal(frames.size, 1);
  context.document.hidden = true; events.visibilitychange(); assert.equal(frames.size, 0);
  context.document.hidden = false; events.visibilitychange(); assert.equal(frames.size, 1);
  motion.matches = true; events.motion(); assert.equal(frames.size, 0);
  motion.matches = false; events.motion(); assert.equal(frames.size, 1);
  windowEvents.pagehide(); assert.equal(frames.size, 0);
  windowEvents.pageshow(); assert.equal(frames.size, 1);
  storageListener({ snowSettings: { newValue: { enabled: false } } }, 'local');
  assert.equal(frames.size, 0); assert.equal(host.isConnected, false);
  console.log('PASS: manifest, script syntax, settings, 8 shapes, particle simulation, frame lifecycle and content storage race.');
}).catch(error => { console.error(error); process.exitCode = 1; });

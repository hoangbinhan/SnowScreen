(() => {
  // Canvas counterpart of Android SnowView: three depth layers and eight crystals.
  class SnowEngine {
    constructor(canvas, settings) {
      this.canvas = canvas;
      this.ctx = canvas.getContext('2d');
      this.settings = SnowSettings.normalize(settings);
      this.flakes = [];
      this.width = 0; this.height = 0; this.pointer = 0;
      this.gust = 0; this.gustTime = 0; this.frame = null;
      this.tick = this.tick.bind(this);
    }
    resize(width, height, pixelRatio = 1) {
      this.width = Math.max(1, width); this.height = Math.max(1, height);
      const dpr = Math.min(2, pixelRatio);
      this.canvas.width = Math.round(this.width * dpr);
      this.canvas.height = Math.round(this.height * dpr);
      this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      this.flakes = Array.from({ length: this.settings.count }, () => this.create(true));
    }
    create(initial = false) {
      const roll = Math.random(), layer = roll < 0.3 ? 0 : roll < 0.8 ? 1 : 2;
      const radius = [1.5, 2.5, 4.5][layer] + Math.random() * [2.5, 4.5, 7.5][layer];
      const start = initial ? Math.random() * this.height : -radius * 4;
      return { x: Math.random() * this.width, y: start, radius, layer,
        speed: [0.8, 1.2, 2.2][layer] + Math.random() * [1, 2, 3.5][layer],
        alpha: [0.4, 0.6, 0.82][layer] + Math.random() * 0.16,
        angle: Math.random() * Math.PI * 2, rotation: (Math.random() - 0.5) * 1.1,
        type: this.settings.shapes[Math.floor(Math.random() * this.settings.shapes.length)],
        melting: Math.random() < 0.2, start,
        end: start + this.height * (0.2 + Math.random() * 0.4) };
    }
    update(settings) {
      const oldShapes = this.settings.shapes.join();
      this.settings = SnowSettings.normalize(settings);
      this.flakes.length = Math.min(this.flakes.length, this.settings.count);
      while (this.flakes.length < this.settings.count) this.flakes.push(this.create(true));
      if (oldShapes !== this.settings.shapes.join()) {
        for (const flake of this.flakes) flake.type = this.settings.shapes[Math.floor(Math.random() * this.settings.shapes.length)];
      }
    }
    start() {
      if (this.frame !== null || !this.ctx) return;
      this.lastTime = null;
      this.frame = requestAnimationFrame(this.tick);
    }
    stop() {
      if (this.frame !== null) cancelAnimationFrame(this.frame);
      this.frame = null;
      this.ctx?.clearRect(0, 0, this.width, this.height);
    }
    tick(time) {
      const dt = this.lastTime === null ? 0 : Math.min((time - this.lastTime) / 1000, 0.05);
      this.lastTime = time;
      const s = this.settings;
      if (s.gusts) {
        this.gustTime += dt;
        if (this.gustTime > 6) { this.gust = (Math.random() - 0.5) * 6; this.gustTime = 0; }
        else if (this.gustTime > 3) this.gust *= Math.exp(-3 * dt);
      } else { this.gust = 0; this.gustTime = 0; }
      const damp = this.width > this.height ? 0.7 : 1;
      const wind = (s.wind + this.gust + (s.pointerWind ? this.pointer * 2 : 0)) * damp;
      this.ctx.clearRect(0, 0, this.width, this.height);
      for (const f of this.flakes) {
        f.angle += f.rotation * dt;
        f.y += Math.max(0.7, f.speed * s.speed * damp) * 45 * dt;
        f.x += (Math.sin(f.angle) * 1.5 + wind * [0.6, 1, 1.4][f.layer]) * 45 * dt;
        const margin = f.radius * s.size * 6;
        if (f.y > this.height + margin || (f.melting && f.y >= f.end)) Object.assign(f, this.create());
        if (f.x > this.width + margin) f.x = -margin;
        else if (f.x < -margin) f.x = this.width + margin;
      }
      for (let layer = 0; layer < 3; layer++) {
        for (const f of this.flakes) if (f.layer === layer) this.draw(f);
      }
      this.frame = requestAnimationFrame(this.tick);
    }
    draw(f) {
      const c = this.ctx, r = f.radius * this.settings.size;
      const fade = f.melting ? Math.max(0, 1 - (f.y - f.start) / (f.end - f.start)) : 1;
      c.save(); c.translate(f.x, f.y); c.rotate(f.angle);
      c.globalAlpha = f.alpha * fade * this.settings.opacity;
      c.fillStyle = '#ffffff'; c.strokeStyle = '#e9f3ff';
      c.lineWidth = Math.max(0.7, r * 0.13); c.lineCap = 'round';
      const dot = (x, y, radius) => { c.beginPath(); c.arc(x, y, radius, 0, Math.PI * 2); c.fill(); };
      const line = (x, y, x2, y2) => { c.moveTo(x, y); c.lineTo(x2, y2); };
      if (f.type === 7) dot(0, 0, r * 1.2);
      else if (f.type === 3) {
        dot(0, 0, r * 0.7);
        for (let i = 0; i < 6; i++) dot(Math.cos(i * Math.PI / 3) * r * 1.4, Math.sin(i * Math.PI / 3) * r * 1.4, r * 0.3);
      } else {
        dot(0, 0, r * 0.3); c.beginPath();
        const rays = f.type === 5 ? 12 : f.type === 2 || f.type === 6 ? 8 : 6;
        for (let i = 0; i < rays; i++) {
          const a = i * Math.PI * 2 / rays, x = Math.cos(a), y = Math.sin(a);
          const length = r * (f.type === 5 ? (i % 2 ? 1.2 : 2) : f.type === 6 ? (i % 2 ? 1.1 : 2.2) : 1.8);
          line(0, 0, x * length, y * length);
          if (f.type === 0) {
            for (const side of [-1, 1]) line(x * r * 1.1, y * r * 1.1,
              x * r * 1.1 + Math.cos(a + side * Math.PI / 4) * r * 0.6,
              y * r * 1.1 + Math.sin(a + side * Math.PI / 4) * r * 0.6);
          }
          if (f.type === 1) line(x * length, y * length, Math.cos(a + Math.PI / 3) * length, Math.sin(a + Math.PI / 3) * length);
          if (f.type === 4) {
            c.moveTo(x * length, y * length);
            c.lineTo(x * length - y * r * 0.3, y * length + x * r * 0.3);
            c.lineTo(x * (length + r * 0.4), y * (length + r * 0.4));
            c.lineTo(x * length + y * r * 0.3, y * length - x * r * 0.3);
            c.closePath();
          }
        }
        c.stroke();
      }
      c.restore();
    }
  }
  globalThis.SnowEngine = SnowEngine;
})();

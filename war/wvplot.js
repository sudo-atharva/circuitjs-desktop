// Shared by waveform.html (live viewer) and report.html (datasheet): number formatting,
// measurements and the time plot itself. Traces are {name, unit, color, t[], v[]}.
const PRE = [[1e9, 'G'], [1e6, 'M'], [1e3, 'k'], [1, ''], [1e-3, 'm'], [1e-6, 'µ'], [1e-9, 'n'], [1e-12, 'p']];

function fmt(v, u) {
  if (!isFinite(v)) return '—';
  if (v === 0) return '0' + u;
  const a = Math.abs(v);
  for (const [m, p] of PRE) if (a >= m * 0.9995) return +(v / m).toPrecision(4) + p + u;
  return v.toExponential(3) + u;
}
function parseSI(s) {
  const m = String(s).trim().match(/^([-+]?[\d.]+(?:e[-+]?\d+)?)\s*([GMkmuµnp]?)/i);
  if (!m) return NaN;
  const k = { G: 1e9, M: 1e6, k: 1e3, m: 1e-3, u: 1e-6, 'µ': 1e-6, n: 1e-9, p: 1e-12 }[m[2]] || 1;
  return parseFloat(m[1]) * k;
}
function niceStep(range, n) {
  const raw = range / Math.max(1, n), p = Math.pow(10, Math.floor(Math.log10(raw))), f = raw / p;
  return (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * p;
}
function lowerBound(a, x) {
  let lo = 0, hi = a.length;
  while (lo < hi) { const m = (lo + hi) >> 1; if (a[m] < x) lo = m + 1; else hi = m; }
  return lo;
}
function valueAt(tr, x) {
  const n = tr.t.length;
  if (!n || x < tr.t[0] || x > tr.t[n - 1]) return NaN;
  const i = lowerBound(tr.t, x);
  if (tr.t[i] === x || i === 0) return tr.v[i];
  const t0 = tr.t[i - 1], t1 = tr.t[i];
  return tr.v[i - 1] + (tr.v[i] - tr.v[i - 1]) * (x - t0) / (t1 - t0);
}
function fullRange(trs) {
  let x0 = Infinity, x1 = -Infinity;
  for (const tr of trs) if (tr.t.length) { x0 = Math.min(x0, tr.t[0]); x1 = Math.max(x1, tr.t[tr.t.length - 1]); }
  if (!isFinite(x0)) return { x0: 0, x1: 1e-3 };
  return { x0: x0, x1: x1 > x0 ? x1 : x0 + 1e-3 };
}
// sample index range [_i0, _i1) covering x0..x1 (one extra sample each side so lines reach the edges)
function clipTrace(tr, x0, x1) {
  tr._i0 = Math.max(0, lowerBound(tr.t, x0) - 1);
  tr._i1 = Math.min(tr.t.length, lowerBound(tr.t, x1) + 1);
}

// stats over [_i0, _i1) (time-weighted, so variable time steps don't skew them)
function measure(tr) {
  const t = tr.t, v = tr.v, i0 = tr._i0, i1 = tr._i1;
  if (!(i1 - i0 >= 2)) return null;
  let lo = Infinity, hi = -Infinity, area = 0, area2 = 0, T = 0;
  for (let i = i0; i < i1; i++) {
    if (v[i] < lo) lo = v[i];
    if (v[i] > hi) hi = v[i];
    if (i > i0) {
      const dt = t[i] - t[i - 1];
      area += (v[i] + v[i - 1]) / 2 * dt;
      area2 += (v[i] * v[i] + v[i - 1] * v[i - 1]) / 2 * dt;
      T += dt;
    }
  }
  if (!(T > 0)) return null;
  // frequency + duty: rising crossings through a Schmitt trigger at mid +- 10% of p-p
  const mid = (lo + hi) / 2, h = (hi - lo) * 0.1;
  let high = v[i0] > mid, above = 0, first = null, last = null, aboveFirst = 0, aboveLast = 0, n = 0;
  for (let i = i0 + 1; h > 0 && i < i1; i++) {
    const dt = t[i] - t[i - 1];
    if (v[i] > mid) above += dt;
    if (!high && v[i] > mid + h) {
      high = true;
      const tc = t[i] - dt * (v[i] - mid - h) / (v[i] - v[i - 1]);
      if (first === null) { first = tc; aboveFirst = above; }
      last = tc; aboveLast = above; n++;
    } else if (high && v[i] < mid - h) high = false;
  }
  const periods = n - 1, span = last - first;
  return {
    lo, hi, avg: area / T, rms: Math.sqrt(area2 / T),
    freq: periods >= 1 && span > 0 ? periods / span : null,
    duty: periods >= 1 && span > 0 ? (aboveLast - aboveFirst) / span : null
  };
}

// Grid, axes (first unit left, second right) and traces for x0..x1 in a W x H (CSS px) area.
// th: {bg, grid, text, frame, font, lineWidth, color(tr)}. Returns the geometry for overlays.
function plotTraces(ctx, W, H, trs, x0, x1, th) {
  ctx.fillStyle = th.bg;
  ctx.fillRect(0, 0, W, H);
  const units = [...new Set(trs.map(t => t.unit))];
  const left = units.includes('V') ? 'V' : (units[0] || 'V');
  const right = units.find(u => u !== left);
  const L = 70, R = right ? 70 : 16, T = 10, B = 26, pw = Math.max(1, W - L - R), ph = Math.max(1, H - T - B);
  const X = x => L + (x - x0) / (x1 - x0) * pw;

  // autoscale each unit over the visible time range
  const yr = {};
  for (const tr of trs) {
    clipTrace(tr, x0, x1);
    const r = yr[tr.unit] || (yr[tr.unit] = { lo: Infinity, hi: -Infinity });
    for (let i = tr._i0; i < tr._i1; i++) { const v = tr.v[i]; if (v < r.lo) r.lo = v; if (v > r.hi) r.hi = v; }
  }
  for (const u of [left, right]) {
    if (!u) continue;
    const r = yr[u] || (yr[u] = { lo: -1, hi: 1 });
    if (!isFinite(r.lo)) { r.lo = -1; r.hi = 1; }
    if (r.hi - r.lo < 1e-12) { const d = Math.max(Math.abs(r.hi) * 0.1, 1e-3); r.lo -= d; r.hi += d; }
    const pad = (r.hi - r.lo) * 0.06; r.lo -= pad; r.hi += pad;
  }
  const Y = (u, v) => T + (1 - (v - yr[u].lo) / (yr[u].hi - yr[u].lo)) * ph;

  // grid + axes
  ctx.font = th.font;
  ctx.lineWidth = 1;
  const xs = niceStep(x1 - x0, pw / 100);
  ctx.textAlign = 'center'; ctx.textBaseline = 'top';
  for (let x = Math.ceil(x0 / xs) * xs; x <= x1; x += xs) {
    const px = Math.round(X(x)) + 0.5;
    ctx.strokeStyle = th.grid; ctx.beginPath(); ctx.moveTo(px, T); ctx.lineTo(px, T + ph); ctx.stroke();
    ctx.fillStyle = th.text; ctx.fillText(fmt(Math.abs(x) < xs * 1e-6 ? 0 : x, 's'), px, T + ph + 6);
  }
  ctx.textBaseline = 'middle';
  for (const [u, side] of [[left, 'L'], [right, 'R']]) {
    if (!u) continue;
    const r = yr[u], ys = niceStep(r.hi - r.lo, ph / 50);
    for (let v = Math.ceil(r.lo / ys) * ys; v <= r.hi; v += ys) {
      const py = Math.round(Y(u, v)) + 0.5;
      if (side === 'L') { ctx.strokeStyle = th.grid; ctx.beginPath(); ctx.moveTo(L, py); ctx.lineTo(L + pw, py); ctx.stroke(); }
      ctx.fillStyle = th.text;
      ctx.textAlign = side === 'L' ? 'right' : 'left';
      ctx.fillText(fmt(Math.abs(v) < ys * 1e-6 ? 0 : v, u), side === 'L' ? L - 6 : L + pw + 6, py);
    }
  }
  ctx.strokeStyle = th.frame;
  ctx.strokeRect(L + 0.5, T + 0.5, pw, ph);

  // traces: per-pixel min/max when dense, polyline when sparse
  ctx.save();
  ctx.beginPath(); ctx.rect(L, T, pw, ph); ctx.clip();
  ctx.lineWidth = th.lineWidth;
  for (const tr of trs) {
    const u = tr.unit, t = tr.t, v = tr.v;
    ctx.strokeStyle = th.color(tr);
    ctx.beginPath();
    if (tr._i1 - tr._i0 > pw * 2) {
      let col = null, mn = 0, mx = 0, first = true;
      const flush = () => {
        if (first) { ctx.moveTo(col, Y(u, mn)); first = false; } else ctx.lineTo(col, Y(u, mn));
        ctx.lineTo(col, Y(u, mx));
      };
      for (let i = tr._i0; i < tr._i1; i++) {
        const c = Math.floor(X(t[i]));
        if (c !== col) { if (col !== null) flush(); col = c; mn = mx = v[i]; }
        else { if (v[i] < mn) mn = v[i]; if (v[i] > mx) mx = v[i]; }
      }
      if (col !== null) flush();
    } else {
      for (let i = tr._i0; i < tr._i1; i++) {
        if (i === tr._i0) ctx.moveTo(X(t[i]), Y(u, v[i])); else ctx.lineTo(X(t[i]), Y(u, v[i]));
      }
    }
    ctx.stroke();
  }
  ctx.restore();
  return { L, T, pw, ph, X, Y };
}

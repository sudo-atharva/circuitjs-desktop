// MCP tool handlers. Rust (mcp.rs) calls window.__mcp(id, tool, args);
// the answer goes back through the mcp_reply command.
(function () {
  var api = function () {
    if (!window.circuitjsApi) throw 'simulator is still loading, try again';
    return window.circuitjsApi;
  };
  var num = function (x) { return typeof x === 'number' && isFinite(x) ? Number(x.toPrecision(4)) : null; };
  var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };

  // compact text, one line per element: "3 ResistorElm: r 176 128 176 208 0 1000" (+ " | readings" if asked).
  // Plain lines instead of JSON objects keep MCP answers small (a model reads them, every token counts).
  // chips (3+ pins) also list their pin coordinates in pin order ("pins 64,96 64,128 ..."): wires end there.
  function elementLine(A, i, readings) {
    var s = i + ' ' + A.type(i) + ': ' + A.dumpLine(i), posts = A.posts ? A.posts(i) : '';
    if (posts.split(' ').length > 2) s += ' | pins ' + posts;
    if (readings) s += ' | ' + A.info(i).split('\n').slice(1).join('; ');
    return s;
  }

  function elements(a) {
    var A = api(), out = [], n = A.count();
    var only = a && a.indexes && a.indexes.length ? a.indexes : null;
    for (var i = 0; i < n; i++)
      if (!only || only.indexOf(i) >= 0) out.push(elementLine(A, i, !a || a.readings !== false));
    return n + ' elements\n' + out.join('\n');
  }

  function summary() {
    var A = api(), out = [];
    for (var i = 0; i < A.count(); i++) out.push(elementLine(A, i, false));
    return A.count() + ' elements\n' + out.join('\n');
  }

  // change single lines instead of sending the whole circuit back and forth.
  // remove: [index], replace: [{index, line}], add: [line]. Scope ("o") and slider ("38") lines refer to
  // element indexes: they are renumbered, and dropped when their element is removed.
  function editCircuit(a) {
    var A = api(), n = A.count(), lines = A.dump().split('\n'), pos = [], j = 0, i;
    if (lines[lines.length - 1] === '') lines.pop();
    for (i = 0; i < n; i++) {
      var line = A.dumpLine(i);
      while (j < lines.length && lines[j] !== line) j++;
      if (j >= lines.length) throw 'could not find element ' + i + ' in the circuit text; use get_circuit + load_circuit';
      pos.push(j++);
    }
    var check = function (k) {
      if (!(k >= 0 && k < n && (k | 0) === k)) throw 'no element with index ' + k + ' (0-' + (n - 1) + ')';
      return k;
    };
    var removed = {}, changed = [];
    (a.replace || []).forEach(function (r) { lines[pos[check(r.index)]] = String(r.line); changed.push(r.index); });
    (a.remove || []).forEach(function (k) { removed[check(k)] = true; });
    var newIndex = function (k) {
      if (removed[k]) return -1;
      var m = k;
      for (var x in removed) if (+x < k) m--;
      return m;
    };
    var out = [], lastElement = n ? pos[n - 1] : 0, add = a.add || [];
    for (i = 0; i < lines.length; i++) {
      var ln = lines[i], e = pos.indexOf(i);
      if (e >= 0 && removed[e]) continue;
      var t = ln.split(' ');
      if ((t[0] === 'o' || t[0] === '38') && t.length > 1 && /^\d+$/.test(t[1])) {
        var m = newIndex(+t[1]);
        if (m < 0) continue;
        t[1] = m;
        ln = t.join(' ');
      }
      out.push(ln);
      if (i === lastElement) add.forEach(function (l) { out.push(String(l)); });
    }
    A.load(out.join('\n') + '\n', A.title() || 'MCP circuit');
    var count = A.count(), report = [];
    changed.forEach(function (k) { if (newIndex(k) >= 0) report.push(elementLine(A, newIndex(k), false)); });
    for (i = Math.max(0, count - add.length); i < count; i++) report.push(elementLine(A, i, false));
    return count + ' elements' + (report.length ? '\nchanged/added:\n' + report.join('\n') : '');
  }

  function examples() {
    return fetch('circuitjs1/setuplist.txt').then(function (r) { return r.text(); }).then(function (text) {
      var path = [], out = [];
      text.split('\n').forEach(function (line) {
        line = line.trim();
        if (!line || line[0] === '#') return;
        if (line[0] === '+') { path.push(line.substring(1)); return; }
        if (line === '-') { path.pop(); return; }
        if (line[0] === '>') line = line.substring(1);
        var sp = line.indexOf(' ');
        if (sp > 0) out.push({ file: line.substring(0, sp), title: line.substring(sp + 1), menu: path.join(' > ') });
      });
      return out;
    });
  }

  function valueAt(tr, x) {
    var t = tr.t, lo = 0, hi = t.length;
    while (lo < hi) { var m = (lo + hi) >> 1; if (t[m] < x) lo = m + 1; else hi = m; }
    if (lo === 0) return tr.v[0];
    if (lo >= t.length) return tr.v[t.length - 1];
    return tr.v[lo - 1] + (tr.v[lo] - tr.v[lo - 1]) * (x - t[lo - 1]) / (t[lo] - t[lo - 1]);
  }

  // frequency from rising crossings of the mid level, with 10% hysteresis, timed by interpolation
  function frequency(tr, lo, hi) {
    var mid = (lo + hi) / 2, h = (hi - lo) * 0.1, high = false, last = -1, periods = [];
    if (!(hi - lo > 1e-12)) return null;
    for (var i = 1; i < tr.t.length; i++) {
      var v = tr.v[i];
      if (!high && v > mid + h) {
        high = true;
        var v0 = tr.v[i - 1], tc = tr.t[i];
        if (v !== v0) tc = tr.t[i] - (tr.t[i] - tr.t[i - 1]) * (v - mid - h) / (v - v0);
        if (last >= 0) periods.push(tc - last);
        last = tc;
      } else if (high && v < mid - h) high = false;
    }
    periods = periods.slice(-20);
    if (periods.length < 2) return null;
    var avg = periods.reduce(function (a, b) { return a + b; }, 0) / periods.length;
    return avg > 0 ? 1 / avg : null;
  }

  function summarize(tr, points) {
    var n = tr.t.length, lo = Infinity, hi = -Infinity, sum = 0, sum2 = 0;
    for (var i = 0; i < n; i++) {
      var v = tr.v[i];
      if (v < lo) lo = v; if (v > hi) hi = v;
      sum += v; sum2 += v * v;
    }
    var r = {
      name: tr.name, unit: tr.unit, samples: n,
      min: num(lo), max: num(hi), avg: num(sum / n), rms: num(Math.sqrt(sum2 / n)),
      final: num(tr.v[n - 1]), frequency_hz: num(frequency(tr, lo, hi))
    };
    if (points > 0 && n > 1) {
      // evenly spaced: start + step instead of a time for every point (half the size)
      var t0 = tr.t[0], t1 = tr.t[n - 1];
      r.t0 = num(t0); r.dt = num((t1 - t0) / (points - 1)); r.v = [];
      for (var k = 0; k < points; k++) r.v.push(num(valueAt(tr, t0 + (t1 - t0) * k / (points - 1))));
    }
    return r;
  }

  async function simulate(a) {
    var A = api(), stop = Number(a.stop_time), points = a.points == null ? 20 : Math.max(0, Math.min(2000, a.points | 0));
    if (!(stop > 0)) throw 'stop_time must be > 0 seconds';
    var probes = a.probes || [];
    if (!probes.length) throw 'give at least one probe {element, quantity}';
    A.clearProbes();
    var ids = probes.map(function (p) {
      var id = A.addProbe(p.element | 0, p.quantity === 'current' ? 1 : 0);
      if (id < 0) throw 'no element with index ' + p.element + ' (see list_elements)';
      return id;
    });
    A.setTurbo(true);
    try {
      A.run(stop);
      var started = Date.now();
      while (A.running()) {
        if (Date.now() - started > 590000) { A.stop(); throw 'simulation took longer than 10 minutes; use a shorter stop_time'; }
        await sleep(30);
      }
    } finally {
      A.setTurbo(false);
    }
    var err = A.error();
    if (err) throw 'simulation stopped: ' + err;
    return { time_reached: num(A.time()), probes: ids.map(function (id) { return summarize(window.wv.traces[id], points); }) };
  }

  var tools = {
    list_examples: examples,
    load_example: function (a) {
      if (!/^[\w.-]+$/.test(a.file || '')) throw 'bad file name';
      return fetch('circuitjs1/circuits/' + a.file).then(function (r) {
        if (!r.ok) throw 'no example named ' + a.file;
        return r.text();
      }).then(function (t) { api().load(t, a.file); return summary(); });
    },
    get_circuit: function () { return api().dump(); },
    load_circuit: function (a) {
      if (!a.text) throw 'text is required';
      api().load(a.text, a.name || 'MCP circuit');
      return summary();
    },
    list_elements: elements,
    edit_circuit: editCircuit,
    simulate: simulate,
    set_running: function (a) { api().setRunning(!!a.running); return 'ok'; }
  };

  window.__mcp = function (id, tool, args) {
    var reply = function (ok, result) {
      if (typeof result !== 'string') result = JSON.stringify(result);
      window.__TAURI__.core.invoke('mcp_reply', { id: id, ok: ok, result: result });
    };
    Promise.resolve().then(function () {
      if (!tools[tool]) throw 'unknown tool: ' + tool;
      return tools[tool](args || {});
    }).then(function (r) { reply(true, r); }, function (e) { reply(false, String(e && e.message || e)); });
  };
})();

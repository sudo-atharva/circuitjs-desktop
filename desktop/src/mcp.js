// MCP tool handlers. Rust (mcp.rs) calls window.__mcp(id, tool, args);
// the answer goes back through the mcp_reply command.
(function () {
  var api = function () {
    if (!window.circuitjsApi) throw 'simulator is still loading, try again';
    return window.circuitjsApi;
  };
  var num = function (x) { return typeof x === 'number' && isFinite(x) ? Number(x.toPrecision(6)) : null; };
  var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };

  function elements() {
    var A = api(), out = [];
    for (var i = 0; i < A.count(); i++)
      out.push({ index: i, type: A.type(i), dump: A.dumpLine(i), info: A.info(i).split('\n') });
    return out;
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
      var t0 = tr.t[0], t1 = tr.t[n - 1];
      r.t = []; r.v = [];
      for (var k = 0; k < points; k++) {
        var x = t0 + (t1 - t0) * k / (points - 1);
        r.t.push(num(x)); r.v.push(num(valueAt(tr, x)));
      }
    }
    return r;
  }

  async function simulate(a) {
    var A = api(), stop = Number(a.stop_time), points = a.points == null ? 100 : Math.max(0, Math.min(2000, a.points | 0));
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
      }).then(function (t) { api().load(t, a.file); return elements(); });
    },
    get_circuit: function () { return api().dump(); },
    load_circuit: function (a) {
      if (!a.text) throw 'text is required';
      api().load(a.text, a.name || 'MCP circuit');
      return elements();
    },
    list_elements: elements,
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

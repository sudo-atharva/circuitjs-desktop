// Probe data recorded by WaveformViewer.java and plotted by waveform.html.
// Each trace keeps its own time array so probes added mid-run need no padding.
window.wv = {
  traces: {}, order: [], version: 0,
  MAX: 2000000, // ponytail: halves resolution past 2M samples per trace; spill to disk if longer runs matter
  add: function (id, name, unit) {
    this.traces[id] = { id: id, name: name, unit: unit, t: [], v: [] };
    this.order.push(id);
    this.version++;
  },
  remove: function (id) {
    delete this.traces[id];
    this.order = this.order.filter(function (x) { return x != id; });
    this.version++;
  },
  push: function (id, t, v) {
    var tr = this.traces[id];
    if (!tr) return;
    var n = tr.t.length;
    if (n && t < tr.t[n - 1]) { tr.t = []; tr.v = []; } // simulation was reset
    tr.t.push(t); tr.v.push(v);
    if (tr.t.length > this.MAX) {
      tr.t = tr.t.filter(function (_, i) { return !(i & 1); });
      tr.v = tr.v.filter(function (_, i) { return !(i & 1); });
    }
    this.version++;
  }
};

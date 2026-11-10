// Provides the hooks CirSim.isElectron() looks for, backed by Rust commands.
(function () {
  var invoke = function (cmd, args) { return window.__TAURI__.core.invoke(cmd, args); };
  var lastPath = null;
  var fail = function (e) { alert(e); };
  var baseName = function (p) { return p.substring(Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\')) + 1); };
  window.showSaveDialog = function () {
    return invoke('save_dialog', { ext: 'csim' }).then(function (p) {
      return p ? { canceled: false, filePath: p } : { canceled: true };
    });
  };
  window.saveFile = function (file, text) {
    var path = file ? file.filePath : lastPath;
    if (file) lastPath = path;
    if (!path) return;
    invoke('write_file', { path: path, text: text }).catch(fail);
  };
  // export (e.g. CSV from waveform viewer) without changing the circuit's save path
  window.saveTextAs = function (text, ext) {
    invoke('save_dialog', { ext: ext }).then(function (p) {
      if (p) return invoke('write_file', { path: p, text: text });
    }).catch(fail);
  };
  window.openFile = function (callback) {
    invoke('open_file').then(function (r) {
      if (!r) return;
      lastPath = r[0];
      callback(r[1], baseName(r[0]));
    }).catch(fail);
  };
  // called by CirSim once loaded: open the .csim the app was launched with
  window.circuitjsReady = function () {
    invoke('startup_file').then(function (r) {
      if (!r) return;
      lastPath = r[0];
      window.circuitjsLoad(r[1], baseName(r[0]));
    }).catch(fail);
  };
  window.toggleDevTools = function () { invoke('toggle_devtools'); };
  // Ctrl+R is "rotate" in the app; F5/Ctrl+R must never reload the page (loses the circuit)
  window.addEventListener('keydown', function (e) {
    if (e.key === 'F5' || ((e.ctrlKey || e.metaKey) && (e.key === 'r' || e.key === 'R')))
      e.preventDefault();
  }, true);
})();

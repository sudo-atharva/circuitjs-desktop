// Provides the hooks CirSim.isElectron() looks for, backed by Rust commands.
(function () {
  var invoke = function (cmd, args) { return window.__TAURI__.core.invoke(cmd, args); };
  var lastPath = null;
  window.showSaveDialog = function () {
    return invoke('save_dialog').then(function (p) {
      return p ? { canceled: false, filePath: p } : { canceled: true };
    });
  };
  window.saveFile = function (file, text) {
    var path = file ? file.filePath : lastPath;
    if (file) lastPath = path;
    if (!path) return;
    invoke('write_file', { path: path, text: text }).catch(function (e) { alert(e); });
  };
  window.openFile = function (callback) {
    invoke('open_file').then(function (r) {
      if (!r) return;
      lastPath = r[0];
      callback(r[1], r[0].split(/[\/]/).pop());
    }).catch(function (e) { alert(e); });
  };
  window.toggleDevTools = function () { invoke('toggle_devtools'); };
})();

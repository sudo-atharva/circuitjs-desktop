# Changelog

## 1.0.0

First release of CircuitJS Desktop, a Windows edition of the CircuitJS1 circuit simulator.

### New
- **Native Windows app** (Rust + Tauri, about 7 MB), with an installer or a portable `.exe`.
- **`.csim` circuit files**: native Open/Save dialogs; double-clicking a `.csim` opens it. Old `.txt` circuits still open.
- **Oscilloscope probes** (key `h`): place them on any wire or terminal and drag them around while the circuit
  runs. Each probe is a colored channel (CH1 yellow, CH2 cyan, CH3 magenta, CH4 blue...).
- **Waveform Viewer**: a large plot window for probes and scopes.
  - Live measurements per channel: peak-to-peak, min, max, average, RMS, frequency, duty cycle.
  - Cursors A/B with Δt, 1/Δt and per-trace differences.
  - Pause/Resume, scope-style "Show last" timebase, zoom and pan, CSV export.
  - "Run for": transient run from t = 0 that stops at a set time.
  - Double-click any small scope to open its traces here.
- **Rotate** parts and selections with Ctrl+R.
- **Keyboard cheat sheet**: press `?` or Help → Keyboard Shortcuts.
- **Everyday Circuits** menu with 8 practical examples (LED, transistor switch, rectifier, regulators,
  555 blinker, PWM filter, op-amp), each checked against hand calculations.
- **MCP server** for AI assistants (off by default; Options → Enable MCP Server).

### Fixed
- Scope frequency readout now works for slow and compressed signals (it used to measure screen pixels and
  gave up when a period was shorter than 12 pixels or fewer than 2 periods were visible).
- F5 / Ctrl+R no longer reload the page and wipe the circuit.

### Known limitations
- Probes measure voltage to ground only.
- Chips and text move when rotated but keep their orientation.
- Moving a part does not drag its wires along.
- Not code-signed: Windows SmartScreen may warn on first run.

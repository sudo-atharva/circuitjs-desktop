# Changelog

## 1.3.0

### New
- **Microcontroller (MCU) block** (Digital Chips): 8 pins and a small Arduino-like program in its edit dialog
  (`P0 = 1`, `delay 500`, `if` / `while`, `pullup P1`, `A2` reads a voltage). Help: Program link in the dialog.
- **Timed power supply** (Inputs and Sources): any voltage; delay on, cycle on/off, follow a trigger with on/off
  delays (like a PLC timer relay), or a pulse per trigger edge.
- **74HC595** (latch, tri-state outputs, chainable) and **74HC165** chips with their real pins.
- **Everyday circuits**: PNP high-side switch, optocoupler 24 V input, short-circuit protection (current limit +
  fuse), 74HC165 + 74HC595 with an MCU, MCU blinker + 24 V timed supply.
- **Waveform Viewer**: rise / fall time (10-90 %) and slew rate per trace; slope at cursor A and between A and B.
- **Website** with the circuit gallery (every circuit runs in the browser), a guide with tips, and downloads:
  https://sudo-atharva.github.io/circuitjs-desktop/
- **MCP**: `edit_circuit` changes single lines (an edit costs a few hundred tokens instead of the whole circuit
  twice), compact one-line answers, pin coordinates for chips, smaller waveforms. Guide: docs/MCP.md.

### Fixed
- Menu items with short internal names (Ground, Switch, Diode, LED, Static RAM...) did nothing when clicked.
- Scope frequency readout: square waves at most frequencies between 21 and 60 kHz showed no frequency, and
  lower ones were up to 1 % off. Now every frequency 1-60 kHz reads within 0.3 % (5 µs time step).
- The Waveform Viewer window opened behind the main window or stayed minimized.

## 1.2.0

### New
- **Datasheet / PDF export** (File → Export Datasheet, or *Datasheet / PDF* in the Waveform Viewer): editable A4
  datasheet with title block, schematic, electrical characteristics, waveform figures, bill of materials,
  simulation conditions and the circuit file; save it as PDF from the print dialog.

## 1.1.0

### New
- `circuitjs.exe --mcp-stdio`: stdio MCP bridge, so Claude Desktop can use CircuitJS without Node.js.

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

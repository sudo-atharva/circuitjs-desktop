# MCP guide: letting an AI build and simulate circuits

CircuitJS has a built-in MCP server, so AI assistants (Claude Code, Claude Desktop, any MCP client) can load,
edit and simulate circuits in the running app. You see every change on screen as it happens.

## 1. Turn it on

1. Start CircuitJS.
2. **Options → Enable MCP Server (AI control)**. The setting is remembered.

The server listens on `http://127.0.0.1:7432/mcp`, on this PC only. Set the `CIRCUITJS_MCP_PORT` environment
variable to use another port.

## 2. Connect your AI

**Claude Code** (all projects):

```
claude mcp add --transport http --scope user circuitjs http://127.0.0.1:7432/mcp
```

Check with `/mcp` inside Claude Code: `circuitjs` should show as connected. If it says *failed to connect*,
CircuitJS isn't running or the MCP option is off. Start it, then run `/mcp` again.

**Claude Desktop**: Settings → Developer → Edit Config, then add:

```json
{
  "mcpServers": {
    "circuitjs": {
      "command": "C:\\Users\\YOU\\AppData\\Local\\CircuitJS\\circuitjs.exe",
      "args": ["--mcp-stdio"]
    }
  }
}
```

Use the path of your `circuitjs.exe`, then quit Claude from the tray and reopen it.

## 3. Tools

| Tool | What it does | Size of the answer |
|---|---|---|
| `list_examples` | built-in example circuits | medium (once) |
| `load_example` | load an example | one line per element |
| `get_circuit` | the whole circuit text (`.csim` content) | whole circuit |
| `load_circuit` | replace the circuit with new text | one line per element |
| `edit_circuit` | change, remove or add single lines | **only the changed lines** |
| `list_elements` | elements with live readings; `indexes` limits it to some, `readings: false` drops the readings | one line per element |
| `simulate` | reset, run to `stop_time`, return per-probe min/max/avg/rms/final/frequency and `points` samples (default 20, `0` = numbers only) | small |
| `set_running` | pause / resume | tiny |

Element lines look like `3 ResistorElm: r 176 128 176 208 0 1000`: index, type, then the line from the circuit
file. Chips (3 or more pins) also list their pin coordinates, `| pins 64,96 64,128 ...`, in pin order. Wires
connect where their end points meet those coordinates.

## 4. Keeping it cheap (tokens)

Every MCP answer goes into the AI's context, so big answers cost tokens and time.

- **Build once, then edit.** Use `load_circuit` (or `load_example`) for the first version. After that, change
  single lines with `edit_circuit`:
  ```json
  {"replace": [{"index": 4, "line": "r 352 32 160 32 0 4700"}], "remove": [7], "add": ["w 100 100 200 100 0"]}
  ```
  The answer holds only the new element count and the changed lines. The old way (`get_circuit` + `load_circuit`)
  sends the whole circuit twice and gets every element back.
- **Ask only for the numbers you need.** `simulate` with `"points": 0` returns just min/max/avg/rms/frequency.
  20 points (the default) are enough to see a shape.
- **List less.** `list_elements` with `"indexes": [3, 4]` or `"readings": false`.

A tip for Claude Code: tell it once, e.g. *"use edit_circuit for changes and simulate with points 0 unless you
need the waveform"*.

## 5. Circuit text cheat sheet

The first line holds the options (`$ 1 0.000005 10.2 50 5 50`; the 2nd number is the time step in seconds). Then
one element per line: `<type> x1 y1 x2 y2 flags parameters...`. Coordinates are pixels on a 16 px grid.

| Part | Line |
|---|---|
| wire | `w x1 y1 x2 y2 0` |
| ground | `g x y x y+16 0` |
| resistor 1 kΩ | `r x1 y1 x2 y2 0 1000` |
| capacitor 10 µF | `c x1 y1 x2 y2 0 0.00001 0` |
| DC source 5 V (2-terminal) | `v x1 y1 x2 y2 0 0 40 5 0 0 0.5` |
| LED | `162 x1 y1 x2 y2 0 1 0 0` |
| push switch | `s x1 y1 x2 y2 0 1 true` |
| text | `x x y x2 y2 0 16 Your text` |
| MCU (8 pins, program) | `436 x1 y1 x2 y2 0 <program>`, see [MCU](../war/mcu.html) |
| timed power supply | `437 x1 y1 x2 y2 0 <mode> <volts> <delay s> <on s> <off s>` |
| 74HC595 | `438 x1 y1 x2 y2 0` (pins: SER SCK RCK /CLR /OE, then Q0-Q7, Q7') |
| 74HC165 | `439 x1 y1 x2 y2 0` (pins: D0-D7 DS, then /PL CLK /CE Q7 /Q7) |
| NPN / PNP transistor | `t x1 y1 x2 y2 0 1 0 0 100` (NPN) / `... 0 -1 0 0 100` (PNP); pins: base, collector, emitter |
| optocoupler | `407 x1 y1 x2 y2 0` (pins: anode, cathode, collector, emitter) |
| fuse | `404 x1 y1 x2 y2 0 <ohms> <I²t> 0 false` |
| logic input (clickable) | `L x1 y1 x2 y2 0 <0/1> false <high V> 0` |

For anything else, draw the part once in the app and read its line with `list_elements`. Program text and other
strings in a line are escaped: space `\s`, new line `\n`, `+` `\p`, `=` `\q`, `#` `\h`, `&` `\a`, backslash `\\`.

### Timed power supply modes

| Mode | Number | Behaviour |
|---|---|---|
| Delay on | 0 | off until *delay*, then on (for *on* seconds; 0 = stays on) |
| Cycle on/off | 1 | after *delay*: on for *on*, off for *off*, repeat |
| Trigger (follow) | 2 | on *delay* after TRIG goes above 2.5 V, off *off* seconds after it drops |
| Trigger (pulse) | 3 | each rising TRIG edge: after *delay*, on for *on* seconds |

## 6. Good to know

- `simulate` resets the circuit and runs as fast as the CPU allows, then pauses so the plots stay. Ask the AI to
  resume (`set_running`) or press Run.
- Keep the window open (not minimized). Windows slows down hidden pages.
- Anything running on this PC can use the server while the option is on. Turn it off when you don't need it.

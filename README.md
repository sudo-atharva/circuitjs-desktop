# CircuitJS1

## Introduction

CircuitJS1 is an electronic circuit simulator that runs in the browser. It was originally written by Paul Falstad as a Java Applet. It was adapted by Iain Sharp to run in the browser using GWT.

For a hosted version of the application see:

* Paul's Page: [http://www.falstad.com/circuit/](http://www.falstad.com/circuit/)
* Iain's Page: [http://lushprojects.com/circuitjs/](http://lushprojects.com/circuitjs/)

Thanks to: Edward Calver for 15 new components and other improvements; Rodrigo Hausen for file import/export and many other UI improvements; J. Mike Rollins for the Zener diode code; Julius Schmidt for the spark gap code and some examples; Dustin Soodak for help with the user interface improvements; Jacob Calvert for the T Flip Flop; Ben Hayden for scope spectrum; Thomas Reitinger, Krystian Sławiński, Usevalad Khatkevich, Lucio Sciamanna, Mauro Hemerly Gazzani, J. Miguel Silva, and Franck Viard for translations; Andre Adrian for improved emitter coupled oscillator; Felthry for many examples; Colin Howell for code improvements. LZString (c) 2013 pieroxy.

## Building the web application

The tools you will need to build the project are:

* Eclipse, Oxygen version.
* GWT plugin for Eclipse.

Install "Eclipse for Java developers" from [here](https://www.eclipse.org/downloads/packages/). To add the GWT plugin for Eclipse follow the instructions [here](https://gwt-plugins.github.io/documentation/gwt-eclipse-plugin/Download.html).

This repository is a project folder for your Eclipse project space. Once you have a local copy you can then build and run in development mode or build for deployment. Running in super development mode is done by clicking on the "run" icon on the toolbar and choosing http://127.0.0.1:8888/circuitjs.html from the "Development Mode" tab which appears. Building for deployment is done by selecting the project root node and using the GWT button on the Eclipse taskbar and choosing "GWT Compile Project...".

GWT will build its output in to the "war" directory. In the "war" directory the file "iframe.html" is loaded as an iFrame in to the spare space at the bottom of the right hand pannel. It can be used for branding etc.

## Deployment of the web application

* "GWT Compile Project..." as explained above. This will put the outputs in to the "war" directory in the Eclipse project folder. You then need to copy everything in the "war" directory, except the "WEB-INF" directory, on to your web server.
* Customize the header of the file "circuitjs1.html" to include your tracking, favicon etc.
* Customize the "iframe.html" file to include any branding you want in the right hand panel of the application
* The optional file "shortrelay.php" is a server-side script to act as a relay to a URL shortening service to avoid cross-origin problems with a purely client solution. You may want to customize this for your site. If you don't want to use this feature edit the circuitjs1.java file before compiling.
* If you wish to enable dropbox loading and saving a dropbox API app-key is needed. This should be edited in to the circuitjs.html file where needed. If this is not included the relevant features will be disabled.


The link for the full-page version of the application is now:
`http://<your host>/<your path>/circuitjs1.html`
(you can rename the "circuitjs1.html" file if you want too though you should also update "shortrelay.php" if you do).

Just for reference the files should look like this

```
-+ Directory containing the front page (eg "circuitjs")
  +- circuitjs.html - full page version of application
  +- iframe.html - see notes above
  +- shortrelay.php - see notes above
  ++ circuitjs1 (directory)
   +- various files built by GWT
   +- circuits (directory, containing example circuits)
   +- setuplist.txt (index in to example circuit directory)
```
   
## Embedding

You can link to the full page version of the application using the link shown above.

If you want to embed the application in another page then use an iframe with the src being the full-page version.

You can add query parameters to link to change the applications startup behaviour. The following are supported:
```
.../circuitjs.html?cct=<string> // Load the circuit from the URL (like the # in the Java version)
.../circuitjs.html?ctz=<string> // Load the circuit from compressed data in the URL
.../circuitjs.html?startCircuit=<filename> // Loads the circuit named "filename" from the "Circuits" directory
.../circuitjs.html?startCircuitLink=<URL> // Loads the circuit from the specified URL. CURRENTLY THE URL MUST BE A DROPBOX SHARED FILE OR ANOTHER URL THAT SUPPORTS CORS ACCESS FROM THE CLIENT
.../circuitjs.html?euroResistors=true // Set to true to force "Euro" style resistors. If not specified the resistor style will be based on the user's browser's language preferences
.../circuitjs.html?usResistors=true // Set to true to force "US" style resistors. If not specified the resistor style will be based on the user's browser's language preferences
.../circuitjs.html?whiteBackground=<true|false>
.../circuitjs.html?conventionalCurrent=<true|false>
.../circuitjs.html?running=<true|false> // Start the app without the simulation running, default true
.../circuitjs.html?hideSidebar=<true|false> // Hide the sidebar, default false
.../circuitjs.html?hideMenu=<true|false> // Hide the menu, default false
.../circuitjs.html?editable=<true|false> // Allow circuit editing, default true
.../circuitjs.html?positiveColor=%2300ff00 // change positive voltage color (rrggbb)
.../circuitjs.html?negativeColor=%23ff0000 // change negative voltage color
.../circuitjs.html?hideInfoBox=<true|false>
```
## Building an Electron application

The [Electron](https://electronjs.org/) project allows web applications to be distributed as local executables for a variety of platforms. This repository contains the additional files needed to build circuitJS1 as an Electron application.

The general approach to building an Electron application for a particular platform is documented [here](https://electronjs.org/docs/tutorial/application-distribution). The following instructions apply this approach to circuit JS.

To build the Electron application:
* Compile the application using GWT, as above.
* Download and unpack a [pre-built Electron binary directory](https://github.com/electron/electron/releases) version 9.3.2 for the target platform.
* Copy the "app" directory from this repository to the location specified [here](https://electronjs.org/docs/tutorial/application-distribution) in the Electron binary directory structure.
* Copy the "war" directory, containing the compiled CircuitJS1 application, in to the "app" directory the Electron binary directory structure.
* Run the "Electron" executable file. It should automatically load CircuitJS1.

Known limitations of the Electron application:
* "Create short URL" on "Export as URL" doesn't work as it relies on server support.
* For diodes, "Create Simple Model" doesn't work as it relies on a javascript feature that is not supported.

Thanks to @Immortalin for the initial work in applying Electron to CircuitJS1.

## License

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

## Windows desktop app (Rust / Tauri)

The `desktop/` folder is a small Rust app ([Tauri](https://tauri.app)) that embeds the compiled
simulator into a single `circuitjs.exe` (~7 MB) using Windows' built-in WebView2. File Open/Save
use native dialogs. Circuits save as `.csim` (same text format as before; `.txt` still opens).

**Waveform viewer (LTspice-style):** right-click any wire or part → *Plot Voltage/Current in Waveform Viewer*
(or *Scopes → Waveform Viewer...*). It records the whole run, not a rolling window. Drag to zoom, wheel to zoom,
right-drag to pan, double-click to fit, click/right-click to place cursors A/B (shows Δt, 1/Δt, ΔV/ΔI).
**Double-click any small scope** (or its menu → Open in Waveform Viewer) to send all its traces here.
*Show last* sets a scope-style timebase (e.g. `20m`); *Pause/Resume* freezes the simulation so you can measure.
*Run for* does a transient run from t=0 that stops at the given time. *Export CSV* saves the traces.

Build locally (needs JDK 11+ and Rust):

```
# 1. compile Java -> JS (download GWT 2.12.2 SDK and unzip it next to the repo first)
java -cp "src;gwt-2.12.2/gwt-user.jar;gwt-2.12.2/gwt-dev.jar" com.google.gwt.dev.Compiler -war war -sourceLevel 1.8 com.lushprojects.circuitjs1.circuitjs1
# 2. build the exe
cd desktop && npx @tauri-apps/cli@2 build
# -> desktop/target/release/circuitjs.exe (portable)
# -> desktop/target/release/bundle/nsis/CircuitJS_*_x64-setup.exe (installer, registers .csim files)
```

GitHub Actions (`.github/workflows/build.yml`) builds the exe on every push; pushing a tag like
`v0.1.0` attaches it to a GitHub release.

### Editing help

- **Rotate:** Ctrl+R (or right-click → Rotate 90°) rotates the selection, or the part under the mouse.
- **Cheat sheet:** press `?` or Help → Keyboard Shortcuts.
- **Oscilloscope probe:** press `h` (or Draw → Outputs → Add Oscilloscope Probe), click a wire and drag out the
  handle. The Waveform Viewer opens and each probe is a colored channel there (CH1 yellow, CH2 cyan, CH3 magenta,
  CH4 blue...). Drag a probe anywhere, even while the simulation runs, and its trace follows. A hollow tip with `?` means it touches nothing.

### MCP server (AI control)

While the desktop app is running it serves MCP at `http://127.0.0.1:7432/mcp` (local only;
change the port with the `CIRCUITJS_MCP_PORT` environment variable). Add it to Claude Code with:

```
claude mcp add --transport http circuitjs http://127.0.0.1:7432/mcp
```

Tools: `list_examples`, `load_example`, `get_circuit`, `load_circuit`, `list_elements`,
`simulate` (transient run with probes; returns min/max/avg/rms/frequency and a resampled waveform),
`set_running`. Simulations requested over MCP run at full CPU speed, then return to normal speed.
Keep the app window open (not minimized): Windows slows down hidden web pages.

### Everyday circuits

See [examples/](examples/) or **Circuits → Everyday Circuits** in the app.

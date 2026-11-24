package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.TextArea;

// Microcontroller: 8 pins (P0-P3 left, P4-P7 right) run by a small program (see McuProgram for the language).
// Each pin is a voltage source behind a resistor on an internal node:
//   output: 0 / 5 V through OUTPUT_OHMS     pull-up input: 5 V through PULLUP_OHMS     input: 0 V through 100 MOhm
public class McuElm extends ChipElm {
    // ponytail: fixed 5 V part with ~25 Ohm drive and 20 kOhm pull-ups (AVR-like); make them settings if 3.3 V parts matter
    static final double VCC = 5, OUTPUT_OHMS = 25, PULLUP_OHMS = 20000, INPUT_OHMS = 1e8;
    static final String DEFAULT_PROGRAM =
	"# LED on P0 blinks while the button on P1 is not pressed.\n" +
	"# A knob (0-5 V) on P2 sets the speed: 100-1000 ms.\n" +
	"pullup P1\n" +
	"loop\n" +
	"if P1 == 1\n" +
	"  P0 = 1\n" +
	"  delay 100 + A2 * 180\n" +
	"  P0 = 0\n" +
	"  delay 100 + A2 * 180\n" +
	"else\n" +
	"  P0 = 0\n" +
	"end\n";
    static String lastProgram = DEFAULT_PROGRAM;

    String source;
    McuProgram program;

    public McuElm(int xx, int yy) {
	super(xx, yy);
	setProgram(lastProgram);
    }

    public McuElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
	super(xa, ya, xb, yb, f, st);
	setProgram(st.hasMoreTokens() ? CustomLogicModel.unescape(st.nextToken()) : DEFAULT_PROGRAM);
    }

    void setProgram(String s) {
	source = s;
	program = new McuProgram(s);
    }

    String dump() {
	return super.dump() + " " + CustomLogicModel.escape(source);
    }

    int getDumpType() { return 436; }

    String getChipName() { return "MCU"; }

    void setupPins() {
	sizeX = 3; // room for the label between the pin names
	sizeY = 4;
	pins = new Pin[McuProgram.PINS];
	for (int i = 0; i != McuProgram.PINS; i++) {
	    pins[i] = new Pin(i % 4, i < 4 ? SIDE_W : SIDE_E, "P" + i);
	    pins[i].output = true; // every pin has its driver (voltage source); inputs drive it through 100 MOhm
	}
	allocNodes();
    }

    int getPostCount() { return McuProgram.PINS; }

    int getVoltageSourceCount() { return McuProgram.PINS; }

    int getInternalNodeCount() { return McuProgram.PINS; }

    boolean nonLinear() { return true; } // pin resistance changes with the pin mode

    boolean hasGroundConnection(int n) { return true; }

    void reset() {
	super.reset();
	program.reset();
    }

    void stamp() {
	for (int i = 0; i != McuProgram.PINS; i++) {
	    sim.stampVoltageSource(0, nodes[McuProgram.PINS + i], pins[i].voltSource);
	    sim.stampNonLinear(nodes[McuProgram.PINS + i]);
	    sim.stampNonLinear(nodes[i]);
	}
    }

    // once per time step: the program sees the pin voltages of the last step
    // ponytail: if the simulator repeats a time step, statements between two delays can run twice
    void startIteration() {
	program.run(sim.t, volts);
    }

    void doStep() {
	for (int i = 0; i != McuProgram.PINS; i++) {
	    int mode = program.mode[i];
	    double v = mode == McuProgram.OUTPUT ? (program.level[i] ? VCC : 0) : mode == McuProgram.PULLUP ? VCC : 0;
	    double r = mode == McuProgram.OUTPUT ? OUTPUT_OHMS : mode == McuProgram.PULLUP ? PULLUP_OHMS : INPUT_OHMS;
	    sim.updateVoltageSource(0, nodes[McuProgram.PINS + i], pins[i].voltSource, v);
	    sim.stampResistor(nodes[McuProgram.PINS + i], nodes[i], r);
	    pins[i].value = mode == McuProgram.OUTPUT ? program.level[i] : volts[i] > 2.5;
	}
    }

    void draw(Graphics g) {
	drawChip(g);
	// chip name in the middle of the body
	g.setColor(needsHighlight() ? selectColor : lightGrayColor);
	int cx = (rectPointsX[0] + rectPointsX[2]) / 2, cy = (rectPointsY[0] + rectPointsY[2]) / 2;
	String label = program.error != null ? "MCU !" : "MCU";
	g.setFont(new Font("SansSerif", Font.BOLD, 7 * csize));
	int w = (int) g.context.measureText(label).getWidth();
	g.drawString(label, cx - w / 2, cy + 3 * csize);
    }

    void getInfo(String arr[]) {
	arr[0] = "MCU";
	if (program.error != null)
	    arr[1] = "error: " + program.error;
	else
	    arr[1] = (program.delaying(sim.t) ? "delay, next: line " : "line ") + program.currentLine();
	int a = 2;
	for (int i = 0; i != McuProgram.PINS; i += 2) {
	    arr[a++] = pinText(i) + "; " + pinText(i + 1);
	}
	String vars = program.varsText();
	if (vars.length() > 0)
	    arr[a] = vars;
    }

    String pinText(int i) {
	int mode = program.mode[i];
	String m = mode == McuProgram.OUTPUT ? (program.level[i] ? "out 1" : "out 0") : mode == McuProgram.PULLUP ? "pull-up" : "in";
	return "P" + i + " " + m + " " + getVoltageText(volts[i]);
    }

    public EditInfo getEditInfo(int n) {
	if (n == 2) {
	    EditInfo ei = new EditInfo(EditInfo.makeLink("mcu.html", "Program"), 0, -1, -1);
	    ei.textArea = new TextArea();
	    ei.textArea.setVisibleLines(14);
	    ei.textArea.setCharacterWidth(40);
	    ei.textArea.setText(source);
	    return ei;
	}
	return super.getEditInfo(n);
    }

    public void setEditValue(int n, EditInfo ei) {
	if (n == 2) {
	    lastProgram = ei.textArea.getText();
	    setProgram(lastProgram);
	    return;
	}
	super.setEditValue(n, ei);
    }
}

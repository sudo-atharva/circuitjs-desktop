package com.lushprojects.circuitjs1.client;

// Timed power supply: a DC source (between + and -) that switches on and off by time or by a trigger input.
//   Delay on:          off until "delay", then on (for "on time"; 0 = stays on)
//   Cycle:             after "delay": on for "on time", off for "off time", repeat
//   Trigger (follow):  on "delay" after TRIG goes high, off "off time" after it goes low (PLC on/off-delay timer)
//   Trigger (pulse):   each rising edge on TRIG: after "delay", on for "on time"
// TRIG is high above 2.5 V (measured to ground) and draws no current. Off = 0 V between + and -.
public class TimedSupplyElm extends ChipElm {
    static final int MODE_DELAY = 0, MODE_CYCLE = 1, MODE_FOLLOW = 2, MODE_PULSE = 3;
    static final String[] MODE_NAMES = { "Delay on", "Cycle on/off", "Trigger (follow, with delays)", "Trigger (pulse)" };
    static final int TRIG = 0, PLUS = 1, MINUS = 2;

    int mode = MODE_DELAY;
    double voltage = 24, delay = 1, onTime = 0, offTime = 1;

    // run state
    boolean on, trigWas;
    double riseAt = -1, fallAt = -1, pulseAt = -1;

    public TimedSupplyElm(int xx, int yy) {
	super(xx, yy);
    }

    public TimedSupplyElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
	super(xa, ya, xb, yb, f, st);
	mode = Integer.parseInt(st.nextToken());
	voltage = Double.parseDouble(st.nextToken());
	delay = Double.parseDouble(st.nextToken());
	onTime = Double.parseDouble(st.nextToken());
	offTime = Double.parseDouble(st.nextToken());
    }

    String dump() {
	return super.dump() + " " + mode + " " + voltage + " " + delay + " " + onTime + " " + offTime;
    }

    int getDumpType() { return 437; }

    String getChipName() { return "timed supply"; }

    void setupPins() {
	sizeX = 2;
	sizeY = 2;
	pins = new Pin[3];
	pins[TRIG] = new Pin(0, SIDE_W, "TRIG");
	pins[PLUS] = new Pin(0, SIDE_E, "+");
	pins[MINUS] = new Pin(1, SIDE_E, "-");
	pins[PLUS].output = true; // the source's current shows on +
    }

    int getPostCount() { return 3; }

    int getVoltageSourceCount() { return 1; }

    boolean hasGroundConnection(int n) { return false; } // floating source, like a bench supply

    boolean getConnection(int n1, int n2) {
	return (n1 == PLUS && n2 == MINUS) || (n1 == MINUS && n2 == PLUS);
    }

    void reset() {
	super.reset();
	on = trigWas = false;
	riseAt = fallAt = pulseAt = -1;
    }

    void stamp() {
	sim.stampVoltageSource(nodes[MINUS], nodes[PLUS], pins[PLUS].voltSource);
    }

    // once per time step: decide on/off from the time and the trigger of the last step
    void startIteration() {
	double t = sim.t;
	boolean trig = volts[TRIG] > 2.5;
	if (trig && !trigWas) {
	    riseAt = t;
	    if (mode == MODE_PULSE && pulseAt < 0)
		pulseAt = t; // a new edge while a pulse is still pending or running is ignored
	}
	if (!trig && trigWas)
	    fallAt = t;
	trigWas = trig;
	switch (mode) {
	case MODE_DELAY:
	    on = t >= delay && (onTime <= 0 || t < delay + onTime);
	    break;
	case MODE_CYCLE:
	    double period = onTime + offTime;
	    on = t >= delay && period > 0 && (t - delay) % period < onTime;
	    break;
	case MODE_FOLLOW:
	    if (trig && riseAt >= 0 && t - riseAt >= delay)
		on = true;
	    if (!trig && (fallAt < 0 || t - fallAt >= offTime))
		on = false;
	    break;
	case MODE_PULSE:
	    on = pulseAt >= 0 && t >= pulseAt + delay && t < pulseAt + delay + onTime;
	    if (pulseAt >= 0 && t >= pulseAt + delay + onTime)
		pulseAt = -1;
	    break;
	}
    }

    void doStep() {
	sim.updateVoltageSource(nodes[MINUS], nodes[PLUS], pins[PLUS].voltSource, on ? voltage : 0);
	pins[PLUS].value = on;
    }

    void draw(Graphics g) {
	drawChip(g);
	g.setColor(on ? Color.green : needsHighlight() ? selectColor : lightGrayColor);
	int cx = (rectPointsX[0] + rectPointsX[2]) / 2, cy = (rectPointsY[0] + rectPointsY[2]) / 2;
	String label = getShortUnitText(voltage, "V") + (on ? " ON" : " off");
	g.setFont(new Font("SansSerif", Font.BOLD, 6 * csize));
	int w = (int) g.context.measureText(label).getWidth();
	g.drawString(label, cx - w / 2, cy + 3 * csize);
    }

    void getInfo(String arr[]) {
	arr[0] = "timed supply (" + MODE_NAMES[mode] + ")";
	arr[1] = (on ? "ON " : "off ") + getVoltageText(volts[PLUS] - volts[MINUS]) + ", " + getCurrentText(pins[PLUS].current);
	arr[2] = "delay " + getUnitText(delay, "s") + (mode == MODE_DELAY || mode == MODE_FOLLOW ? "" : ", on " + getUnitText(onTime, "s"));
	if (mode == MODE_DELAY && onTime > 0)
	    arr[2] += ", on " + getUnitText(onTime, "s");
	if (mode == MODE_CYCLE || mode == MODE_FOLLOW)
	    arr[2] += ", off " + getUnitText(offTime, "s");
	arr[3] = "TRIG = " + getVoltageText(volts[TRIG]);
    }

    public EditInfo getEditInfo(int n) {
	if (n == 2)
	    return new EditInfo("Voltage", voltage, 0, 0);
	if (n == 3) {
	    EditInfo ei = new EditInfo("Mode", mode, -1, -1);
	    ei.choice = new Choice();
	    for (int i = 0; i != MODE_NAMES.length; i++)
		ei.choice.add(MODE_NAMES[i]);
	    ei.choice.select(mode);
	    return ei;
	}
	if (n == 4)
	    return new EditInfo(mode == MODE_FOLLOW ? "On delay after TRIG goes high (s)" : mode == MODE_PULSE ? "Delay after TRIG edge (s)" : "Delay before on (s)", delay, 0, 0);
	// field 5: on time (follow mode: off delay); field 6: off time (cycle mode only)
	if (n == 5 && mode == MODE_FOLLOW)
	    return new EditInfo("Off delay after TRIG goes low (s)", offTime, 0, 0);
	if (n == 5)
	    return new EditInfo(mode == MODE_DELAY ? "On time (s, 0 = stay on)" : "On time (s)", onTime, 0, 0);
	if (n == 6 && mode == MODE_CYCLE)
	    return new EditInfo("Off time (s)", offTime, 0, 0);
	if (n < 2)
	    return super.getEditInfo(n);
	return null;
    }

    public void setEditValue(int n, EditInfo ei) {
	if (n == 2)
	    voltage = ei.value;
	if (n == 3) {
	    mode = ei.choice.getSelectedIndex();
	    ei.newDialog = true; // the fields differ per mode
	}
	if (n == 4)
	    delay = Math.max(0, ei.value);
	if (n == 5 && mode == MODE_FOLLOW)
	    offTime = Math.max(0, ei.value);
	else if (n == 5)
	    onTime = Math.max(0, ei.value);
	if (n == 6)
	    offTime = Math.max(0, ei.value);
	if (n < 2)
	    super.setEditValue(n, ei);
    }
}

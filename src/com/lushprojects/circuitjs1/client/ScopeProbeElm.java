package com.lushprojects.circuitjs1.client;

// Oscilloscope probe: drop its tip on any wire or terminal, even while the simulation runs.
// It is not part of the circuit (no posts); it just reads the voltage to ground of
// whatever its tip touches, and shows up as a channel in the waveform viewer.
class ScopeProbeElm extends CircuitElm {
    int channel;
    CircuitElm target;
    int targetPost;
    boolean targetValid;

    public ScopeProbeElm(int xx, int yy) {
	super(xx, yy);
	channel = 1;
    }

    public ScopeProbeElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
	super(xa, ya, xb, yb, f);
	channel = 1;
	try {
	    channel = Integer.parseInt(st.nextToken());
	} catch (Exception e) {}
    }

    String dump() { return super.dump() + " " + channel; }
    int getDumpType() { return 435; }
    int getShortcut() { return 'h'; }
    int getPostCount() { return 0; }

    // a new probe becomes a channel of the waveform viewer (the big oscilloscope)
    void draggingDone() {
	// next free channel number (not in the constructor: the menu builds probes before the circuit exists)
	channel = 1;
	for (int i = 0; i != sim.elmList.size(); i++) {
	    CircuitElm ce = sim.getElm(i);
	    if (ce != this && ce instanceof ScopeProbeElm)
		channel = Math.max(channel, ((ScopeProbeElm) ce).channel + 1);
	}
	WaveformViewer.addPlot(this, Scope.VAL_VOLTAGE, Scope.UNITS_V, false);
	WaveformViewer.openIfClosed();
	// back to select mode so the next drag moves the probe instead of adding another
	sim.setMouseMode(CirSim.MODE_SELECT);
	sim.mouseModeStr = "Select";
	sim.tempMouseMode = CirSim.MODE_SELECT;
    }

    // tip moved or circuit changed: look up what the tip touches next time
    void setPoints() {
	super.setPoints();
	targetValid = false;
    }
    void invalidateTarget() { targetValid = false; }

    void findTarget() {
	targetValid = true;
	target = null;
	// a terminal of any part (wire ends included)
	for (int i = 0; i != sim.elmList.size(); i++) {
	    CircuitElm ce = sim.getElm(i);
	    for (int p = 0; p != ce.getPostCount(); p++) {
		Point pt = ce.getPost(p);
		if (pt.x == x && pt.y == y) {
		    target = ce;
		    targetPost = p;
		    return;
		}
	    }
	}
	// anywhere along a wire, or on a two-terminal part's lead/body (reads the nearer terminal)
	for (int i = 0; i != sim.elmList.size(); i++) {
	    CircuitElm ce = sim.getElm(i);
	    if (ce.getPostCount() != 2)
		continue;
	    Point a = ce.getPost(0), b = ce.getPost(1);
	    long cross = (long) (b.x-a.x)*(y-a.y) - (long) (b.y-a.y)*(x-a.x);
	    if (cross == 0 && x >= min(a.x, b.x) && x <= max(a.x, b.x) &&
		    y >= min(a.y, b.y) && y <= max(a.y, b.y)) {
		target = ce;
		targetPost = Graphics.distanceSq(x, y, a.x, a.y) <= Graphics.distanceSq(x, y, b.x, b.y) ? 0 : 1;
		if (ce instanceof WireElm)
		    return;  // exact; keep looking only if we landed on a part
	    }
	}
    }

    boolean connected() {
	if (!targetValid)
	    findTarget();
	return target != null;
    }

    double probeVoltage() { return connected() ? target.getPostVoltage(targetPost) : 0; }

    double getScopeValue(int x) { return probeVoltage(); }
    double getVoltageDiff() { return probeVoltage(); }
    String getScopeText(int v) { return "CH" + channel; }

    // fixed per channel, like a bench scope (CH1 yellow, CH2 cyan, ...); same color in the viewer
    static final String CH_COLORS[] = { "#FFD84A", "#22D3EE", "#F472B6", "#60A5FA",
	    "#4ADE80", "#FB923C", "#A78BFA", "#F87171" };
    String traceColor() { return CH_COLORS[(Math.max(channel, 1)-1) % CH_COLORS.length]; }

    void draw(Graphics g) {
	boolean on = connected();
	// always the channel color (selection color would make CH1 look like CH2); white ring when selected
	Color c = new Color(traceColor());
	setBbox(point1, point2, 6);
	if (needsHighlight()) {
	    g.setColor(Color.white);
	    g.fillOval(x-8, y-8, 16, 16);
	}
	g.setColor(c);
	drawThickLine(g, point1, point2);
	// tip: filled when touching a node, ring when floating
	g.fillOval(x-5, y-5, 10, 10);
	if (!on) {
	    g.setColor(Color.black);
	    g.fillOval(x-3, y-3, 6, 6);
	}
	g.setColor(c);
	Font f = new Font("SansSerif", Font.BOLD, 14);
	g.setFont(f);
	String s = "CH" + channel + (on ? "" : " ?");
	int dx = (x2 >= x) ? 14 : -14;
	drawCenteredText(g, s, x2 + dx + (dx > 0 ? 10 : -10), y2, true);
    }

    void getInfo(String arr[]) {
	arr[0] = "scope probe CH" + channel;
	arr[1] = connected() ? "V = " + getVoltageText(probeVoltage()) : "tip not on a wire or terminal";
    }

    public EditInfo getEditInfo(int n) {
	if (n == 0)
	    return new EditInfo("Channel", channel, 1, 8).setDimensionless();
	return null;
    }
    public void setEditValue(int n, EditInfo ei) {
	if (n == 0 && ei.value >= 1)
	    channel = (int) ei.value;
    }
}

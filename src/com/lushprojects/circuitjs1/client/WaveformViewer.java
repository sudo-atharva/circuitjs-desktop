package com.lushprojects.circuitjs1.client;

import java.util.Vector;

// LTspice-style waveform viewer. Records probed values every timestep into
// window.wv (war/wvstore.js); war/waveform.html plots them in its own window.
class WaveformViewer {
    static final int KIND_VOLTAGE = 0, KIND_CURRENT = 1;

    static Vector<CircuitElm> elms = new Vector<CircuitElm>();
    static Vector<Integer> kinds = new Vector<Integer>();
    static Vector<Integer> ids = new Vector<Integer>();
    static int nextId = 1;
    static double stopTime;

    static void addProbe(CircuitElm e, int kind) {
	addProbe(e, kind, true);
    }

    // returns the probe id (trace id in window.wv)
    static int addProbe(CircuitElm e, int kind, boolean show) {
	for (int i = 0; i != elms.size(); i++)
	    if (elms.get(i) == e && kinds.get(i) == kind) {
		if (show)
		    open();
		return ids.get(i);
	    }
	int id = nextId++;
	elms.add(e);
	kinds.add(kind);
	ids.add(id);
	String name = (kind == KIND_CURRENT ? "I" : "V") + id + ": " + e.getScopeText(Scope.VAL_VOLTAGE);
	jsAddTrace(id, name, kind == KIND_CURRENT ? "A" : "V");
	if (show)
	    open();
	return id;
    }

    static void clear() {
	while (!elms.isEmpty())
	    removeAt(elms.size()-1);
    }

    static double value(CircuitElm e, int kind) {
	if (kind == KIND_CURRENT)
	    return e.getCurrent();
	// a wire has no voltage across it; LTspice-style node voltage to ground instead
	if (e instanceof WireElm)
	    return e.volts[0];
	return e.getScopeValue(Scope.VAL_VOLTAGE);
    }

    // called after every simulation timestep
    static void step(CirSim sim) {
	for (int i = 0; i != elms.size(); i++)
	    jsPush(ids.get(i), sim.t, value(elms.get(i), kinds.get(i)));
	if (stopTime > 0 && sim.t >= stopTime) {
	    stopTime = 0;
	    sim.setSimRunning(false);
	}
    }

    // drop probes on elements that were deleted
    static void prune(CirSim sim) {
	for (int i = elms.size()-1; i >= 0; i--)
	    if (!sim.elmList.contains(elms.get(i)))
		removeAt(i);
    }

    static void removeAt(int i) {
	jsRemoveTrace(ids.get(i));
	elms.remove(i);
	kinds.remove(i);
	ids.remove(i);
    }

    // called from waveform.html
    static void removeProbe(int id) {
	int i = ids.indexOf(id);
	if (i >= 0)
	    removeAt(i);
    }

    // transient run: reset, run until stop seconds, then pause
    static void run(double stop) {
	stopTime = stop;
	CirSim.theSim.resetAction();
	CirSim.theSim.setSimRunning(true);
    }

    static void stop() {
	stopTime = 0;
	CirSim.theSim.setSimRunning(false);
    }

    static native void jsAddTrace(int id, String name, String unit) /*-{
	if ($wnd.wv) $wnd.wv.add(id, name, unit);
    }-*/;

    static native void jsPush(int id, double t, double v) /*-{
	if ($wnd.wv) $wnd.wv.push(id, t, v);
    }-*/;

    static native void jsRemoveTrace(int id) /*-{
	if ($wnd.wv) $wnd.wv.remove(id);
    }-*/;

    static native void open() /*-{
	var w = $wnd.wvWin;
	if (w && !w.closed) { w.focus(); return; }
	$wnd.wvWin = $wnd.open("waveform.html", "waveform", "width=1000,height=600");
    }-*/;

    static native void exportJS() /*-{
	$wnd.wvJava = {
	    remove: $entry(function (id) { @com.lushprojects.circuitjs1.client.WaveformViewer::removeProbe(I)(id); }),
	    run: $entry(function (s) { @com.lushprojects.circuitjs1.client.WaveformViewer::run(D)(s); }),
	    stop: $entry(function () { @com.lushprojects.circuitjs1.client.WaveformViewer::stop()(); })
	};
    }-*/;
}

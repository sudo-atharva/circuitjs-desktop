package com.lushprojects.circuitjs1.client;

// window.circuitjsApi: lets outside code (the desktop app's MCP server) inspect,
// load and simulate circuits. Element index = position in the circuit dump.
class JsApi {
    // set while an outside caller waits on a transient run
    static boolean turbo;

    static CircuitElm elm(int i) { return CirSim.theSim.getElm(i); }

    static int count() { return CirSim.theSim.elmList.size(); }

    static String type(int i) {
	String n = elm(i).getClass().getName();
	return n.substring(n.lastIndexOf('.')+1);
    }

    static String info(int i) {
	String a[] = new String[15];
	elm(i).getInfo(a);
	String s = "";
	for (int j = 0; j != a.length && a[j] != null; j++)
	    s += (j > 0 ? "\n" : "") + a[j];
	return s;
    }

    static String dumpLine(int i) { return elm(i).dump(); }

    static String dump() { return CirSim.theSim.dumpCircuit(); }

    static void load(String text, String name) { LoadFile.doLoadCallback(text, name); }

    static int addProbe(int i, int kind) {
	if (i < 0 || i >= count())
	    return -1;
	return WaveformViewer.addProbe(elm(i), kind, false);
    }

    static boolean running() { return CirSim.theSim.simIsRunning(); }

    static void setRunning(boolean b) { CirSim.theSim.setSimRunning(b); }

    static double time() { return CirSim.theSim.t; }

    static String error() { return CirSim.theSim.stopMessage; }

    static native void export() /*-{
	$wnd.circuitjsApi = {
	    count: $entry(function () { return @com.lushprojects.circuitjs1.client.JsApi::count()(); }),
	    type: $entry(function (i) { return @com.lushprojects.circuitjs1.client.JsApi::type(I)(i); }),
	    info: $entry(function (i) { return @com.lushprojects.circuitjs1.client.JsApi::info(I)(i); }),
	    dumpLine: $entry(function (i) { return @com.lushprojects.circuitjs1.client.JsApi::dumpLine(I)(i); }),
	    dump: $entry(function () { return @com.lushprojects.circuitjs1.client.JsApi::dump()(); }),
	    load: $entry(function (t, n) { @com.lushprojects.circuitjs1.client.JsApi::load(Ljava/lang/String;Ljava/lang/String;)(t, n); }),
	    addProbe: $entry(function (i, k) { return @com.lushprojects.circuitjs1.client.JsApi::addProbe(II)(i, k); }),
	    clearProbes: $entry(function () { @com.lushprojects.circuitjs1.client.WaveformViewer::clear()(); }),
	    run: $entry(function (s) { @com.lushprojects.circuitjs1.client.WaveformViewer::run(D)(s); }),
	    stop: $entry(function () { @com.lushprojects.circuitjs1.client.WaveformViewer::stop()(); }),
	    running: $entry(function () { return @com.lushprojects.circuitjs1.client.JsApi::running()(); }),
	    setRunning: $entry(function (b) { @com.lushprojects.circuitjs1.client.JsApi::setRunning(Z)(b); }),
	    time: $entry(function () { return @com.lushprojects.circuitjs1.client.JsApi::time()(); }),
	    setTurbo: $entry(function (b) { @com.lushprojects.circuitjs1.client.JsApi::turbo = b; }),
	    error: $entry(function () { return @com.lushprojects.circuitjs1.client.JsApi::error()(); })
	};
    }-*/;
}

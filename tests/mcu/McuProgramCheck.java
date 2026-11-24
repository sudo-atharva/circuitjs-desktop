// Self-check for the MCU element's program interpreter (plain Java, no GWT):
//   javac -d build/mcu-check src/com/lushprojects/circuitjs1/client/McuProgram.java tests/mcu/McuProgramCheck.java
//   java -ea -cp build/mcu-check McuProgramCheck
import com.lushprojects.circuitjs1.client.McuProgram;

public class McuProgramCheck {
    static void check(boolean ok, String what) {
	if (!ok)
	    throw new AssertionError(what);
    }

    // run like the simulator: one call per time step (1 ms here) from `from` to `to` seconds
    static void runTo(McuProgram p, double from, double to, double[] v) {
	for (double t = from; t <= to + 1e-9; t += 0.001)
	    p.run(t, v);
    }

    public static void main(String[] args) {
	double[] v = new double[8];

	// blink P0 every 100 ms
	McuProgram blink = new McuProgram("P0 = 1\ndelay 100\nP0 = 0\ndelay 100");
	check(blink.error == null, "blink compiles: " + blink.error);
	runTo(blink, 0, 0, v);
	check(blink.mode[0] == McuProgram.OUTPUT && blink.level[0], "P0 high at 0 ms");
	runTo(blink, 0.001, 0.05, v);
	check(blink.level[0], "still high at 50 ms (in delay)");
	runTo(blink, 0.051, 0.102, v);
	check(!blink.level[0], "low after 100 ms");
	runTo(blink, 0.103, 0.204, v);
	check(blink.level[0], "high again after 200 ms (loops)");

	// a pulse without delay still shows for one step
	McuProgram pulse = new McuProgram("P1 = 1\nP1 = 0\nloop\ndelay 1000");
	pulse.run(0, v);
	check(pulse.level[1], "pulse high for one step");
	pulse.run(0.001, v);
	check(!pulse.level[1], "then low");

	// setup + loop, pull-up button, delay from a voltage
	String src = "# button on P1, knob on A2\npullup P1\nn = 0\nloop\nif P1 == 0\n  P0 = 1\nelse\n  P0 = 0\nend\nn = n + 1\ndelay 100 + A2 * 100";
	McuProgram btn = new McuProgram(src);
	check(btn.error == null, "button compiles: " + btn.error);
	v[1] = 5; // not pressed (pulled up)
	v[2] = 1; // 1 V: delay 200 ms
	runTo(btn, 0, 0.002, v);
	check(btn.mode[1] == McuProgram.PULLUP, "P1 pull-up");
	check(!btn.level[0], "LED off while not pressed");
	v[1] = 0; // pressed
	runTo(btn, 0.003, 0.1, v);
	check(!btn.level[0], "still in the 200 ms delay");
	runTo(btn, 0.101, 0.21, v);
	check(btn.level[0], "LED on when pressed");
	check(btn.varsText().equals("n = 2"), "setup ran once, loop twice: " + btn.varsText());

	// while, toggling, and/or/not, operators
	McuProgram w = new McuProgram("i = 0\nwhile i < 5 and not (i == 3)\n  i = i + 1\nend\nP2 = not P2\nr = (7 % 4) * -2 + 10 / 4\nloop\ndelay 1000");
	check(w.error == null, "while compiles: " + w.error);
	runTo(w, 0, 0.005, v);
	check(w.varsText().indexOf("i = 3") >= 0, "while stops at 3: " + w.varsText());
	check(w.varsText().indexOf("r = -3.5") >= 0, "arithmetic: " + w.varsText());
	check(w.level[2], "P2 toggled to high");

	// busy loop without delay doesn't hang: it polls each call
	McuProgram busy = new McuProgram("while P3 == 0\nend\nP4 = 1");
	v[3] = 0;
	busy.run(0, v);
	check(!busy.level[4], "waiting for P3");
	v[3] = 5;
	runTo(busy, 0.001, 0.003, v);
	check(busy.level[4], "P3 high: continues");

	// errors name the line
	check(new McuProgram("P0 = 1\nif P1\nP0 = 0").error.equals("line 2: missing end"), "missing end");
	check(new McuProgram("P9 = 1").error.startsWith("line 1: can't assign to P9"), "bad pin");
	check(new McuProgram("x = 1 +").error.startsWith("line 1: missing value"), "bad expression");
	McuProgram run = new McuProgram("x = y");
	run.run(0, v);
	check(run.error != null && run.error.equals("line 1: run: y has no value yet"), "run error: " + run.error);
	run.reset();
	check(run.error == null, "reset clears run errors");

	System.out.println("mcu ok");
    }
}

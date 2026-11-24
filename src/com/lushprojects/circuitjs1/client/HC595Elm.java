package com.lushprojects.circuitjs1.client;

// 74HC595: 8-bit serial-in shift register with output latch and tri-state outputs.
//   SER     serial data in                    SRCLK   rising edge: shift (Q0 <- SER, ..., Q7' <- bit 7)
//   RCLK    rising edge: copy the shift register to the outputs Q0-Q7
//   /SRCLR  low: clear the shift register     /OE     high: Q0-Q7 off (high impedance)
//   (short labels on the chip: SCK = SRCLK/SHCP, RCK = RCLK/STCP, /CLR = /SRCLR//MR)
//   Q7'     last bit of the shift register, for chaining into the next 595's SER
// Outputs drive 0 / 5 V through about 30 Ohm (HC at 5 V), so LEDs need their resistors.
public class HC595Elm extends ChipElm {
    static final int SER = 0, SRCLK = 1, RCLK = 2, SRCLR = 3, OE = 4, Q0 = 5, Q7S = 13, N = 14;
    static final double OUT_OHMS = 30, OFF_OHMS = 1e9;

    int shift, latch; // bit i = Q(i)
    boolean lastSrclk, lastRclk;

    public HC595Elm(int xx, int yy) { super(xx, yy); }

    public HC595Elm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
	super(xa, ya, xb, yb, f, st);
    }

    String getChipName() { return "74HC595"; }

    int getDumpType() { return 438; }

    void setupPins() {
	sizeX = 3;
	sizeY = 9;
	pins = new Pin[N];
	pins[SER] = new Pin(0, SIDE_W, "SER");
	pins[SRCLK] = new Pin(2, SIDE_W, "SCK");
	pins[RCLK] = new Pin(4, SIDE_W, "RCK");
	pins[SRCLR] = new Pin(6, SIDE_W, "/CLR");
	pins[OE] = new Pin(8, SIDE_W, "/OE");
	for (int i = 0; i != 8; i++)
	    pins[Q0 + i] = new Pin(i, SIDE_E, "Q" + i);
	pins[Q7S] = new Pin(8, SIDE_E, "Q7'");
	for (int i = 0; i != N; i++) {
	    pins[i].fixName();
	    pins[i].output = i >= Q0;
	}
	allocNodes();
    }

    int getPostCount() { return N; }

    int getVoltageSourceCount() { return 9; }

    int getInternalNodeCount() { return 9; } // outputs drive through a resistor (tri-state)

    boolean nonLinear() { return true; }

    void reset() {
	super.reset();
	shift = latch = 0;
	lastSrclk = lastRclk = false;
    }

    void stamp() {
	for (int i = Q0; i != N; i++) {
	    int in = nodes[N + i - Q0];
	    sim.stampVoltageSource(0, in, pins[i].voltSource);
	    sim.stampNonLinear(in);
	    sim.stampNonLinear(nodes[i]);
	}
    }

    void doStep() {
	boolean srclk = volts[SRCLK] > 2.5, rclk = volts[RCLK] > 2.5;
	// latch first: with SRCLK and RCLK tied together the outputs are one clock behind, as on the real chip
	if (rclk && !lastRclk)
	    latch = shift;
	if (volts[SRCLR] < 2.5)
	    shift = 0;
	else if (srclk && !lastSrclk)
	    shift = ((shift << 1) | (volts[SER] > 2.5 ? 1 : 0)) & 0xFF;
	lastSrclk = srclk;
	lastRclk = rclk;
	boolean enabled = volts[OE] < 2.5;
	for (int i = Q0; i != N; i++) {
	    boolean v = i == Q7S ? (shift & 0x80) != 0 : (latch >> (i - Q0) & 1) != 0;
	    pins[i].value = v;
	    int in = nodes[N + i - Q0];
	    sim.updateVoltageSource(0, in, pins[i].voltSource, v ? 5 : 0);
	    sim.stampResistor(in, nodes[i], i == Q7S || enabled ? OUT_OHMS : OFF_OHMS);
	}
    }

    void getInfo(String arr[]) {
	arr[0] = "74HC595 (SCK = SRCLK, RCK = RCLK latch, /CLR = /SRCLR)";
	arr[1] = "shift register " + bits(shift) + " (Q7..Q0)";
	arr[2] = "outputs " + bits(latch) + (volts[OE] < 2.5 ? "" : " (off: /OE high)");
	arr[3] = "Q7' = " + ((shift & 0x80) != 0 ? 1 : 0);
    }

    static String bits(int b) {
	String s = "";
	for (int i = 7; i >= 0; i--)
	    s += (b >> i) & 1;
	return s;
    }
}

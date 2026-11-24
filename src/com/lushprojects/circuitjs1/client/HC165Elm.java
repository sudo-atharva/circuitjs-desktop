package com.lushprojects.circuitjs1.client;

// 74HC165: 8-bit parallel-in / serial-out shift register (read 8 buttons with 3 wires).
//   D0-D7   parallel inputs                   /PL   low: load D0-D7 into the register (SH/LD on TI datasheets)
//   CLK     rising edge: shift towards Q7 (D7 first out, DS enters at bit 0), unless CE is high
//   /CE     clock enable, active low (CLK INH on TI datasheets)
//   DS      serial in (chain from the next 165's Q7)
//   Q7      bit 7 out (QH), /Q7 inverted
public class HC165Elm extends ChipElm {
    static final int D0 = 0, DS = 8, PL = 9, CLK = 10, CE = 11, Q7 = 12, NQ7 = 13, N = 14;

    int reg; // bit i = stage i (D(i) after a load)
    boolean lastClk;

    public HC165Elm(int xx, int yy) { super(xx, yy); }

    public HC165Elm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
	super(xa, ya, xb, yb, f, st);
    }

    String getChipName() { return "74HC165"; }

    int getDumpType() { return 439; }

    void setupPins() {
	sizeX = 3;
	sizeY = 9;
	pins = new Pin[N];
	for (int i = 0; i != 8; i++)
	    pins[D0 + i] = new Pin(i, SIDE_W, "D" + i);
	pins[DS] = new Pin(8, SIDE_W, "DS");
	pins[PL] = new Pin(0, SIDE_E, "/PL");
	pins[CLK] = new Pin(2, SIDE_E, "CLK");
	pins[CE] = new Pin(4, SIDE_E, "/CE");
	pins[Q7] = new Pin(6, SIDE_E, "Q7");
	pins[NQ7] = new Pin(8, SIDE_E, "/Q7");
	for (int i = 0; i != N; i++)
	    pins[i].fixName();
	pins[Q7].output = pins[NQ7].output = true;
    }

    int getPostCount() { return N; }

    int getVoltageSourceCount() { return 2; }

    void reset() {
	super.reset();
	reg = 0;
	lastClk = false;
    }

    void execute() {
	boolean clk = pins[CLK].value;
	if (!pins[PL].value) {
	    reg = 0;
	    for (int i = 0; i != 8; i++)
		if (pins[D0 + i].value)
		    reg |= 1 << i;
	} else if (clk && !lastClk && !pins[CE].value) {
	    reg = ((reg << 1) | (pins[DS].value ? 1 : 0)) & 0xFF;
	}
	lastClk = clk;
	pins[Q7].value = (reg & 0x80) != 0;
	pins[NQ7].value = !pins[Q7].value;
    }

    void getInfo(String arr[]) {
	arr[0] = "74HC165 parallel-in shift register";
	arr[1] = "register " + HC595Elm.bits(reg) + " (bit 7 first out)";
	arr[2] = "Q7 = " + (pins[Q7].value ? 1 : 0) + (pins[PL].value ? "" : " (loading: /PL low)");
    }
}

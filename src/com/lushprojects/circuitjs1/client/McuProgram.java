package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;

// Program of the MCU element: a small Arduino-like language, compiled to a flat list of steps with jumps.
// Plain Java (no GWT classes) so it can be tested on its own: tests/mcu/McuProgramCheck.java.
//
//   # comment                 (also //)
//   pullup P1                 P1 is an input with the internal pull-up (for a button to ground)
//   loop                      lines above run once at start (setup); lines below repeat forever.
//                             Without "loop" the whole program repeats.
//   P0 = 1                    write a pin (non-zero = high); this makes it an output
//   x = A2 * 100              variable; P2 reads 1/0 (above/below 2.5 V), A2 reads the pin's voltage
//   delay 500                 wait (milliseconds, any expression)
//   if P1 == 0 ... else ... end
//   while x < 10 ... end
//   expressions: numbers, variables, P0-P7, A0-A7, millis, + - * / %, == != < > <= >=, and or not, ( )
public class McuProgram {
    public static final int PINS = 8;
    public static final int INPUT = 0, PULLUP = 1, OUTPUT = 2;
    static final int MAX_STEPS_PER_TICK = 1000; // a loop without delay polls once per time step, like a real MCU

    // ---- compiled program ----
    static final int SET_PIN = 0, SET_VAR = 1, DELAY = 2, JUMP_IF_FALSE = 3, JUMP = 4, PULLUP_PIN = 5;

    static class Step {
	int op, pin, target, line;
	String name;
	Node expr;
    }

    ArrayList<Step> steps = new ArrayList<Step>();
    int loopStart; // where execution goes back to after the last line
    public String error; // compile or run-time error (the program stops), null = fine

    // ---- run state ----
    public int[] mode = new int[PINS];
    public boolean[] level = new boolean[PINS];
    HashMap<String, Double> vars = new HashMap<String, Double>();
    int pc;
    double resumeAt; // seconds; inside a delay until then
    double[] volts; // pin voltages for this tick
    double now;

    public McuProgram(String source) {
	try {
	    compile(source);
	} catch (RuntimeException e) {
	    error = e.getMessage();
	    steps.clear();
	}
	reset();
    }

    public void reset() {
	for (int i = 0; i != PINS; i++) {
	    mode[i] = INPUT;
	    level[i] = false;
	}
	vars.clear();
	pc = 0;
	resumeAt = 0;
	if (error != null && error.startsWith("line ") && error.indexOf(": run:") > 0)
	    error = null; // run-time errors are cleared by a reset, compile errors stay
    }

    // run from time t (seconds) until a delay or the step budget is used up
    public void run(double t, double[] pinVolts) {
	if (steps.isEmpty() || error != null || t < resumeAt)
	    return;
	volts = pinVolts;
	now = t;
	try {
	    for (int n = 0; n != MAX_STEPS_PER_TICK; n++) {
		if (pc >= steps.size())
		    pc = loopStart;
		if (pc >= steps.size())
		    return; // only setup lines, all done
		Step s = steps.get(pc++);
		switch (s.op) {
		case SET_PIN: {
		    boolean v = s.expr.eval(this) != 0, changed = mode[s.pin] != OUTPUT || level[s.pin] != v;
		    mode[s.pin] = OUTPUT;
		    level[s.pin] = v;
		    // a pin change takes one time step (like instruction time on a real MCU), so a pulse
		    // "P1 = 1 ... P1 = 0" is always seen by the circuit, even without a delay in between
		    if (changed)
			return;
		    break;
		}
		case PULLUP_PIN:
		    mode[s.pin] = PULLUP;
		    break;
		case SET_VAR:
		    vars.put(s.name, s.expr.eval(this));
		    break;
		case DELAY:
		    resumeAt = t + Math.max(0, s.expr.eval(this)) / 1000;
		    return;
		case JUMP_IF_FALSE:
		    if (s.expr.eval(this) == 0)
			pc = s.target;
		    break;
		case JUMP:
		    pc = s.target;
		    break;
		}
	    }
	} catch (RuntimeException e) {
	    error = "line " + steps.get(pc - 1).line + ": run: " + e.getMessage();
	}
    }

    // source line of the next step (0 when none)
    public int currentLine() {
	int i = pc < steps.size() ? pc : loopStart;
	return i < steps.size() ? steps.get(i).line : 0;
    }

    public boolean delaying(double t) {
	return t < resumeAt;
    }

    public String varsText() {
	String s = "";
	for (String k : vars.keySet())
	    s += (s.length() > 0 ? ", " : "") + k + " = " + fmt(vars.get(k));
	return s;
    }

    static String fmt(double v) {
	return v == Math.floor(v) && Math.abs(v) < 1e9 ? "" + (long) v : "" + Math.round(v * 1000) / 1000.0;
    }

    // ---- compiler ----

    void compile(String source) {
	String[] lines = source.split("\n");
	ArrayList<int[]> blocks = new ArrayList<int[]>(); // {kind 0=if 1=while, step index of jump, start, line}
	loopStart = 0;
	for (int ln = 0; ln != lines.length; ln++) {
	    String line = lines[ln];
	    int c = line.indexOf('#');
	    if (c >= 0) line = line.substring(0, c);
	    c = line.indexOf("//");
	    if (c >= 0) line = line.substring(0, c);
	    line = line.trim();
	    if (line.length() == 0)
		continue;
	    int lineNo = ln + 1;
	    try {
		ArrayList<String> tok = tokenize(line);
		String first = tok.get(0).toLowerCase();
		if (first.equals("loop") && tok.size() == 1) {
		    loopStart = steps.size();
		} else if (first.equals("pullup")) {
		    for (int i = 1; i < tok.size(); i++)
			add(PULLUP_PIN, pinIndex(tok.get(i), 'p'), null, null, lineNo);
		} else if (first.equals("delay")) {
		    add(DELAY, 0, null, expr(tok, 1), lineNo);
		} else if (first.equals("if") || first.equals("while")) {
		    int start = steps.size();
		    add(JUMP_IF_FALSE, 0, null, expr(tok, 1), lineNo);
		    blocks.add(new int[] { first.equals("if") ? 0 : 1, start, start, lineNo });
		} else if (first.equals("else") && tok.size() == 1) {
		    int[] b = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1);
		    if (b == null || b[0] != 0)
			throw new RuntimeException("else without if");
		    add(JUMP, 0, null, null, lineNo); // end of the "then" part jumps over the else part
		    steps.get(b[1]).target = steps.size();
		    b[0] = 2; // if with else
		    b[1] = steps.size() - 1;
		} else if (first.equals("end") && tok.size() == 1) {
		    if (blocks.isEmpty())
			throw new RuntimeException("end without if or while");
		    int[] b = blocks.remove(blocks.size() - 1);
		    if (b[0] == 1)
			add(JUMP, 0, null, null, lineNo).target = b[2];
		    steps.get(b[1]).target = steps.size();
		} else if (tok.size() >= 3 && tok.get(1).equals("=")) {
		    String name = tok.get(0);
		    if (isPin(name, 'p'))
			add(SET_PIN, pinIndex(name, 'p'), null, expr(tok, 2), lineNo);
		    else if (isName(name) && !looksLikePin(name) && !isKeyword(name))
			add(SET_VAR, 0, name, expr(tok, 2), lineNo);
		    else
			throw new RuntimeException("can't assign to " + name);
		} else {
		    throw new RuntimeException("don't understand \"" + line + "\"");
		}
	    } catch (RuntimeException e) {
		throw new RuntimeException("line " + lineNo + ": " + e.getMessage());
	    }
	}
	if (!blocks.isEmpty())
	    throw new RuntimeException("line " + blocks.get(blocks.size() - 1)[3] + ": missing end");
    }

    Step add(int op, int pin, String name, Node e, int line) {
	Step s = new Step();
	s.op = op;
	s.pin = pin;
	s.name = name;
	s.expr = e;
	s.line = line;
	steps.add(s);
	return s;
    }

    static ArrayList<String> tokenize(String line) {
	ArrayList<String> t = new ArrayList<String>();
	int i = 0;
	while (i < line.length()) {
	    char c = line.charAt(i);
	    if (c == ' ' || c == '\t' || c == '\r') {
		i++;
	    } else if (Character.isDigit(c) || c == '.') {
		int j = i;
		while (j < line.length() && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '.'))
		    j++;
		t.add(line.substring(i, j));
		i = j;
	    } else if (Character.isLetter(c) || c == '_') {
		int j = i;
		while (j < line.length() && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_'))
		    j++;
		t.add(line.substring(i, j));
		i = j;
	    } else if (i + 1 < line.length() && isTwoCharOp(line.substring(i, i + 2))) {
		t.add(line.substring(i, i + 2));
		i += 2;
	    } else if ("+-*/%()<>=!".indexOf(c) >= 0) {
		t.add("" + c);
		i++;
	    } else {
		throw new RuntimeException("unexpected '" + c + "'");
	    }
	}
	return t;
    }

    static boolean isTwoCharOp(String s) {
	return s.equals("==") || s.equals("!=") || s.equals("<=") || s.equals(">=") || s.equals("&&") || s.equals("||");
    }

    static boolean isName(String s) {
	return s.length() > 0 && (Character.isLetter(s.charAt(0)) || s.charAt(0) == '_');
    }

    static boolean isKeyword(String s) {
	String k = s.toLowerCase();
	return k.equals("and") || k.equals("or") || k.equals("not") || k.equals("millis") || k.equals("if")
		|| k.equals("while") || k.equals("else") || k.equals("end") || k.equals("delay") || k.equals("loop")
		|| k.equals("pullup");
    }

    // P0-P7 (kind 'p') or A0-A7 (kind 'a'), any case
    static boolean isPin(String s, char kind) {
	return s.length() == 2 && Character.toLowerCase(s.charAt(0)) == kind && s.charAt(1) >= '0'
		&& s.charAt(1) < '0' + PINS;
    }

    // P12, a9...: not a variable name (a typo for a pin, or a pin this MCU doesn't have)
    static boolean looksLikePin(String s) {
	char c = Character.toLowerCase(s.charAt(0));
	if (s.length() < 2 || (c != 'p' && c != 'a'))
	    return false;
	for (int i = 1; i < s.length(); i++)
	    if (!Character.isDigit(s.charAt(i)))
		return false;
	return true;
    }

    static int pinIndex(String s, char kind) {
	if (!isPin(s, kind))
	    throw new RuntimeException("expected a pin " + Character.toUpperCase(kind) + "0-" + Character.toUpperCase(kind) + (PINS - 1) + ", got " + s);
	return s.charAt(1) - '0';
    }

    // ---- expressions (precedence: or < and < not < compare < + - < * / % < unary minus) ----

    abstract static class Node {
	abstract double eval(McuProgram m);
    }

    int at; // parser position
    ArrayList<String> toks;

    Node expr(ArrayList<String> tok, int from) {
	if (from >= tok.size())
	    throw new RuntimeException("missing value");
	toks = tok;
	at = from;
	Node n = parseOr();
	if (at != tok.size())
	    throw new RuntimeException("unexpected \"" + tok.get(at) + "\"");
	return n;
    }

    String peek() {
	return at < toks.size() ? toks.get(at) : "";
    }

    boolean accept(String s) {
	if (peek().equalsIgnoreCase(s)) {
	    at++;
	    return true;
	}
	return false;
    }

    Node parseOr() {
	Node l = parseAnd();
	while (accept("or") || accept("||")) {
	    final Node a = l, b = parseAnd();
	    l = new Node() { double eval(McuProgram m) { return a.eval(m) != 0 || b.eval(m) != 0 ? 1 : 0; } };
	}
	return l;
    }

    Node parseAnd() {
	Node l = parseNot();
	while (accept("and") || accept("&&")) {
	    final Node a = l, b = parseNot();
	    l = new Node() { double eval(McuProgram m) { return a.eval(m) != 0 && b.eval(m) != 0 ? 1 : 0; } };
	}
	return l;
    }

    Node parseNot() {
	if (accept("not") || accept("!")) {
	    final Node a = parseNot();
	    return new Node() { double eval(McuProgram m) { return a.eval(m) == 0 ? 1 : 0; } };
	}
	return parseCompare();
    }

    Node parseCompare() {
	final Node a = parseSum();
	final String op = peek();
	if (!(op.equals("==") || op.equals("!=") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")))
	    return a;
	at++;
	final Node b = parseSum();
	return new Node() {
	    double eval(McuProgram m) {
		double x = a.eval(m), y = b.eval(m);
		boolean r = op.equals("==") ? x == y : op.equals("!=") ? x != y : op.equals("<") ? x < y
			: op.equals(">") ? x > y : op.equals("<=") ? x <= y : x >= y;
		return r ? 1 : 0;
	    }
	};
    }

    Node parseSum() {
	Node l = parseProduct();
	while (peek().equals("+") || peek().equals("-")) {
	    final boolean plus = toks.get(at++).equals("+");
	    final Node a = l, b = parseProduct();
	    l = new Node() { double eval(McuProgram m) { return plus ? a.eval(m) + b.eval(m) : a.eval(m) - b.eval(m); } };
	}
	return l;
    }

    Node parseProduct() {
	Node l = parseUnary();
	while (peek().equals("*") || peek().equals("/") || peek().equals("%")) {
	    final char op = toks.get(at++).charAt(0);
	    final Node a = l, b = parseUnary();
	    l = new Node() {
		double eval(McuProgram m) {
		    double x = a.eval(m), y = b.eval(m);
		    if (op == '*')
			return x * y;
		    if (y == 0)
			throw new RuntimeException("division by zero");
		    return op == '/' ? x / y : x % y;
		}
	    };
	}
	return l;
    }

    Node parseUnary() {
	if (accept("-")) {
	    final Node a = parseUnary();
	    return new Node() { double eval(McuProgram m) { return -a.eval(m); } };
	}
	return parseAtom();
    }

    Node parseAtom() {
	String t = peek();
	if (t.length() == 0)
	    throw new RuntimeException("missing value");
	at++;
	if (t.equals("(")) {
	    Node n = parseOr();
	    if (!accept(")"))
		throw new RuntimeException("missing )");
	    return n;
	}
	if (Character.isDigit(t.charAt(0)) || t.charAt(0) == '.') {
	    final double v;
	    try {
		v = Double.parseDouble(t);
	    } catch (NumberFormatException e) {
		throw new RuntimeException("bad number " + t);
	    }
	    return new Node() { double eval(McuProgram m) { return v; } };
	}
	if (isPin(t, 'p')) {
	    final int p = t.charAt(1) - '0';
	    // an output reads back what it was set to (so "P0 = not P0" toggles)
	    return new Node() { double eval(McuProgram m) { return (m.mode[p] == OUTPUT ? m.level[p] : m.volts[p] > 2.5) ? 1 : 0; } };
	}
	if (isPin(t, 'a')) {
	    final int p = t.charAt(1) - '0';
	    return new Node() { double eval(McuProgram m) { return m.volts[p]; } };
	}
	if (t.equalsIgnoreCase("millis"))
	    return new Node() { double eval(McuProgram m) { return Math.floor(m.now * 1000); } };
	if (looksLikePin(t))
	    throw new RuntimeException("no pin " + t + " (P0-P7, A0-A7)");
	if (isName(t) && !isKeyword(t)) {
	    final String name = t;
	    return new Node() {
		double eval(McuProgram m) {
		    Double v = m.vars.get(name);
		    if (v == null)
			throw new RuntimeException(name + " has no value yet");
		    return v;
		}
	    };
	}
	throw new RuntimeException("unexpected \"" + t + "\"");
    }
}

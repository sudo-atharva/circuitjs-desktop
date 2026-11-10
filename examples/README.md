# Everyday circuits

Open with File → Open (or double-click once the installer has registered `.csim`).
The same circuits are in the app under **Circuits → Everyday Circuits**
(copies live in `src/com/lushprojects/circuitjs1/public/circuits/everyday-*.txt`; keep both in sync).

| File | What it shows | Simulated result |
|---|---|---|
| led-resistor.csim | 9 V battery, 680 Ω, red LED | 10.5 mA |
| transistor-switch.csim | 1 Hz logic signal switches a 12 V LED load through an NPN | 0 / 21.5 mA at 1 Hz |
| bridge-rectifier.csim | 12 Vrms 50 Hz, bridge, 1000 µF, 100 Ω load | 15.5 V peak DC |
| zener-regulator.csim | 12 V, 330 Ω, 5.1 V zener, 1 kΩ load | 5.13 V |
| linear-regulator-5v.csim | 7805-style: zener reference + NPN pass transistor, 100 Ω load | 4.91 V |
| 555-led-blinker.csim | 555 astable driving an LED | 0.99 Hz |
| pwm-rc-filter.csim | 1 kHz 0–5 V PWM through 10 kΩ / 1 µF | settles at ~2.5 V |
| opamp-noninverting.csim | 0.5 V 1 kHz in, gain 1 + 10k/1k | ±5.5 V at 1 kHz |

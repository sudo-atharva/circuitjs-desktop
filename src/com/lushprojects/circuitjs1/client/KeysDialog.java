package com.lushprojects.circuitjs1.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

// Read-only cheat sheet: editing keys, mouse actions, and the (user-editable) part shortcuts.
public class KeysDialog extends DialogBox {
    static final String FIXED[][] = {
	{ "Ctrl+R", "Rotate selection (or part under mouse) 90°" },
	{ "Ctrl+Z / Ctrl+Y", "Undo / redo" },
	{ "Ctrl+X / C / V", "Cut / copy / paste" },
	{ "Ctrl+D", "Duplicate" },
	{ "Ctrl+A", "Select all" },
	{ "Delete / Backspace", "Delete selection" },
	{ "Space or Esc", "Back to select mode" },
	{ "+ / - / 0", "Zoom in / out / 100%" },
	{ "?", "This help" },
	{ "Drag on empty space", "Select area" },
	{ "Drag a part", "Move it (drag an end dot to move just that end)" },
	{ "Double-click a part", "Edit its values" },
	{ "Right-click a part", "Menu: edit, rotate, scope, waveform viewer..." },
	{ "Ctrl+click a wire", "Split the wire" },
	{ "Mouse wheel", "Zoom (over a part: change its value)" },
	{ "Scope probe (key h)", "Click a wire, drag out the handle; it plots in the Waveform Viewer. Drag it anywhere, even while running" },
    };

    static String esc(String s) {
	return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public KeysDialog(CirSim sim) {
	super();
	setText(CirSim.LS("Keyboard Shortcuts"));
	StringBuilder h = new StringBuilder("<table style='border-spacing:12px 3px'>");
	for (String r[] : FIXED)
	    h.append("<tr><td><b>").append(esc(r[0])).append("</b></td><td>").append(esc(r[1])).append("</td></tr>");
	h.append("<tr><td colspan=2><br><b>Add a part (press the key, then drag on the circuit)</b></td></tr>");
	for (int i = 0; i != sim.mainMenuItems.size(); i++) {
	    CheckboxMenuItem item = sim.mainMenuItems.get(i);
	    String k = item.getShortcut();
	    if (k.length() != 1)
		continue;
	    h.append("<tr><td><b>").append(esc(k)).append("</b></td><td>").append(esc(item.getName())).append("</td></tr>");
	}
	h.append("<tr><td colspan=2><br>Change part keys in Options → Shortcuts...</td></tr></table>");

	VerticalPanel vp = new VerticalPanel();
	ScrollPanel sp = new ScrollPanel(new HTML(h.toString()));
	sp.setHeight("420px");
	vp.add(sp);
	Button ok = new Button(CirSim.LS("Close"));
	ok.addClickHandler(new ClickHandler() {
	    public void onClick(ClickEvent e) { hide(); }
	});
	vp.add(ok);
	setWidget(vp);
	center();
    }
}

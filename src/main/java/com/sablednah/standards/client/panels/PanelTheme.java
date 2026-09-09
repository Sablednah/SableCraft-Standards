package com.sablednah.standards.client.panels;

/**
 * The two colours Standards paints a pane's frame with.
 *
 * <h2>Why theming rather than an opt-out</h2>
 *
 * <p>LegendQuest asked for an opt-out instead — let a panel paint its own frame — and argued that
 * two themeable colours would leave it "a gold-framed panel whose interior is still gold", while
 * enough colours to fix that would mean Standards maintaining LegendQuest's palette.</p>
 *
 * <p>The second half is right and this avoids it: <b>the palette lives in the panel</b>. Standards
 * holds no mod's colours; it is handed two and paints with them. The first half dissolves on
 * inspection — a host only ever paints the <em>frame</em>. Every colour inside the pane is drawn by
 * the panel itself and always was, so "the interior is still gold" describes LegendQuest painting
 * its own interior gold, which is what it wants. There is nothing for an opt-out to opt out of
 * beyond these two values.</p>
 *
 * <p>The result is the same look with less API: no boolean, no two ways of drawing a frame, and no
 * panel that can forget to draw one. A mod that says nothing gets {@link #STANDARD} and looks like
 * everything else, which is the point of having a host at all.</p>
 *
 * @param background fill behind the pane. Alpha matters — these sit over the inventory
 * @param border     the one-pixel edge
 */
public record PanelTheme(int background, int border) {

    /** What a panel gets for saying nothing: the furniture every pane shares. */
    public static final PanelTheme STANDARD = new PanelTheme(0xF0100010, 0xFF3A2A5A);
}

package com.sablednah.standards.client.panels;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A pane another mod draws on the inventory screen, in a space Standards hands it.
 *
 * <h2>Why Standards owns the space</h2>
 *
 * <p>Because everybody wants the same strip of it, and none of them can see each other. Vanilla's
 * recipe book slides out to the left. LegendQuest's character and skills panes slide out to the
 * left. Factions' panel did too, briefly, then moved right — where it landed exactly on vanilla's
 * potion effects, which render at {@code leftPos + imageWidth + 2}, and on JEI, whose tooltips went
 * on firing underneath it because nothing had told JEI it was there.</p>
 *
 * <p>Every one of those was a mod behaving reasonably on its own. The fix is not a better guess
 * about where to draw; it is somebody deciding. So: a mod says <em>what</em> it wants to draw and
 * Standards says <em>where</em>, and <b>only one pane is ever open</b>.</p>
 *
 * <h2>What you get, and what you must not assume</h2>
 *
 * <p>You are handed a rectangle and the mouse. You do not choose it, you cannot move it, and it
 * changes between frames — the window resizes, the recipe book shifts the inventory, the player
 * changes GUI scale. <b>Lay out from the {@code x}, {@code y}, {@code width} and {@code height} you
 * are given, every frame.</b> A panel that caches its own coordinates is the bug this seam exists
 * to stop.</p>
 *
 * <p>Clicks and scrolls arrive only while you are the open pane and only inside your rectangle, so
 * there is no need to bounds-check the pane itself — only the things inside it.</p>
 *
 * <h2>The rule that outranks all of this</h2>
 *
 * <p>Same as the action bar's: <b>a pane may only present what the server would have told a vanilla
 * player anyway.</b> It is a nicer surface for the same answer, never a second capability. If your
 * pane can show something no command will say, the vanilla client has lost something, and decision 2
 * of {@code CLAUDE.md} is the reason the whole client half is allowed to exist.</p>
 */
public interface InventoryPanel {

    /** How wide you would like to be. Standards may give you less; draw to what you are handed. */
    int preferredWidth();

    /**
     * How tall you would like to be, or {@code 0} to match the inventory.
     *
     * <p>⚠ <b>Asked every frame, immediately before the pane is positioned, and never cached.</b>
     * That is a promise rather than an implementation detail: a panel's height is allowed to depend
     * on what it is showing right now. LegendQuest's grows with the open tab, the skill count, the
     * party list and whether the race and class pickers are open — so a host that asked once and
     * remembered would draw the wrong height the moment a player opened a picker, and would compute
     * the slide-up below against a stale number. Keep it a cheap function of state you already
     * hold; it is called once per frame and it is meant to be.</p>
     *
     * <p>A pane taller than the room available is anchored at the inventory's top and <b>slid up
     * only as far as it needs</b>, never above two pixels, leaving two at the bottom:
     * {@code max(2, min(guiTop, screenHeight - height - 2))}. That is LegendQuest's own rule,
     * adopted verbatim, because a pane growing downward off the bottom of the screen is the failure
     * it was written to prevent.</p>
     */
    default int preferredHeight() {
        return 0;
    }

    /**
     * The colours Standards paints your frame with.
     *
     * <p>Say nothing and you get {@link PanelTheme#STANDARD}, so several mods' panes look like one
     * set of furniture. Return your own when the frame is part of your identity rather than
     * decoration — LegendQuest's gold on near-black is the case this exists for.</p>
     *
     * <p>Everything <em>inside</em> the pane is yours and always was; the host paints only the
     * frame. If you want a single palette, keep these two constants where the rest of your colours
     * live and hand them over here.</p>
     */
    default PanelTheme theme() {
        return PanelTheme.STANDARD;
    }

    /**
     * Draw yourself into the rectangle you have been given.
     *
     * <p>The background and border are already drawn — Standards paints the frame so that every
     * pane on a server looks like the same furniture rather than like four mods each having a go.
     * Draw your content and nothing else.</p>
     */
    void render(GuiGraphicsExtractor graphics, Font font,
            int x, int y, int width, int height, int mouseX, int mouseY);

    /**
     * A left click landed inside you.
     *
     * @return true if you used it, which stops it reaching the screen underneath
     */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * The mouse moved with a button held, having been pressed inside you.
     *
     * <p><b>Delivered even when the cursor has left your rectangle</b>, which is the whole reason
     * this is a separate callback rather than a click you could infer. A scrollbar thumb dragged
     * quickly ends up well outside the pane, and one that stops tracking the moment the cursor
     * strays is worse than one you cannot drag at all — it looks like it broke rather than like it
     * has an edge. Dragging ends at {@link #mouseReleased}.</p>
     *
     * @return true if you used it, which stops it reaching the screen underneath
     */
    default boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        return false;
    }

    /** A button came up. Where a drag ends, wherever the cursor happens to be. */
    default void mouseReleased(double mouseX, double mouseY, int button) {}

    /**
     * The wheel turned over you.
     *
     * @return true if you used it. Say false when you have nothing to scroll — the wheel then does
     *         whatever it did before, and stealing it silently is a bug in a mod that looks
     *         unrelated
     */
    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /**
     * Draw anything that must sit above everything else — a tooltip, a dragged item under the cursor.
     *
     * <p>Called after the pane, after its frame, and after anything else the host draws, so nothing
     * can paint over it. <b>Unclipped, and in screen coordinates</b>: a tooltip for a control at
     * your right edge is supposed to hang over the inventory, and one near the bottom is supposed
     * to flip upward past your own bounds.</p>
     *
     * <p>⚠ <b>The host does not scissor the pane rectangle at any point</b> — not here and not in
     * {@link #render}. Panes may overflow, and that is a guarantee rather than an oversight: do not
     * "fix" it later by adding a scissor, because tooltips and drag ghosts depend on it. What keeps
     * a pane inside its bounds is the pane, laying out against the {@code width} and {@code height}
     * it was handed.</p>
     */
    default void renderOverlay(GuiGraphics graphics, Font font, int mouseX, int mouseY) {}

    /**
     * You are about to be shown. The moment to ask the server for fresh data.
     *
     * <p><b>Called more than once</b>, and you must treat every call as "start again": when the
     * pane is opened, and again every time the inventory screen is opened while it is still
     * showing. A pane stays open across an inventory close, so a pane that asked only on the first
     * call would show one snapshot for the rest of the session — accurate when it was drawn and
     * quietly wrong from then on, which is worse than being empty.</p>
     */
    default void onOpen() {}

    /**
     * You are no longer the open pane.
     *
     * <p><b>Unconditional.</b> It fires for every reason a pane stops being open: the player closed
     * it, another pane opened, the recipe book opened, another mod took the space, or
     * {@link #available()} answered false. There is no path that quietly stops showing a pane
     * without telling it — that was worth removing a nicety for, because a conditional teardown
     * callback is one every consumer has to second-guess.</p>
     *
     * <p>⚠ <b>The one thing this is NOT is the inventory screen closing.</b> A pane stays open
     * across that — it is a toggle, and the button is the only thing that changes it — so closing
     * your inventory and opening it again finds the pane where you left it and fires
     * {@link #onOpen()}, not this. Reset transient interaction state (a drag in progress, a scroll
     * offset) in {@code onOpen}, which is documented as "every call means start again" precisely
     * so it can carry that.</p>
     */
    default void onClose() {}

    /**
     * Whether you should be offered at all right now.
     *
     * <p>Asked every frame. Answering false while open closes you, which is what should happen when
     * the player leaves the faction whose panel this is: the button that opens it has already been
     * withheld, and a pane still insisting otherwise would be the last thing on screen that
     * disagreed with the server.</p>
     */
    default boolean available() {
        return true;
    }
}

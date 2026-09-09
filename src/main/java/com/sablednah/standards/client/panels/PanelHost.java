package com.sablednah.standards.client.panels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import com.sablednah.standards.Standards;

/**
 * Draws whichever pane is open, and routes the mouse to it.
 *
 * <p>The panes themselves know nothing about events, screens or layout: they are handed a rectangle
 * and told where the mouse is. Everything version-fragile lives here, in one file, which is the
 * point of having a seam at all — when Minecraft moves the inventory screen around again, this is
 * the file that changes rather than every mod that draws on it.</p>
 *
 * <h2>The frame is drawn here on purpose</h2>
 *
 * <p>Background, border and clipping are Standards' job, so that four mods' panes on one server look
 * like the same furniture instead of like four mods each having a go. A pane draws its contents and
 * nothing else.</p>
 */
public final class PanelHost {

    /** Between the pane and the inventory. */
    private static final int GAP = 4;
    /** Kept off the window edge, so a pane never looks like it has fallen off. */
    private static final int MARGIN = 4;

    private static final int BG = 0xF0100010;
    private static final int EDGE = 0xFF3A2A5A;

    /** Where the open pane was drawn last frame, for routing the mouse. */
    private static int x;
    private static int y;
    private static int width;
    private static int height;
    private static boolean drawn;

    /**
     * Whether the button currently down was pressed inside the pane.
     *
     * <p>The gate on drags: without it, a drag that merely passes over the pane — a vanilla item
     * being dragged across hotbar slots, say — would be handed to a panel that had nothing to do
     * with it.</p>
     */
    private static boolean pressedInside;

    /**
     * The {@code leftPos} we last wrote, or {@link Integer#MIN_VALUE} if we have not moved it.
     *
     * <p>This is what makes "is somebody else in the space" answerable now that <em>we</em> also
     * move the inventory. Off-centre used to mean occupied; it now means occupied by somebody, and
     * this is how we tell whether that somebody is us.</p>
     */
    private static int shiftedTo = Integer.MIN_VALUE;

    /**
     * Vanilla's recipe-book button, and how far it sits from {@code leftPos}.
     *
     * <p>⚠ Moving the inventory is not enough on its own. Vanilla sets {@code leftPos} and
     * repositions this button in the same breath — see {@code AbstractRecipeBookScreen.initButton},
     * which recomputes {@code getRecipeBookButtonPosition()} after every shift. A pane that moved
     * the inventory and left the button behind would park vanilla's own button inside itself.</p>
     *
     * <p>The offset is measured rather than assumed: {@code InventoryScreen} happens to use
     * {@code leftPos + 104}, and hardcoding that would be a second copy of a number only vanilla
     * should own.</p>
     */
    private static net.minecraft.client.gui.components.AbstractWidget recipeButton;
    private static int recipeButtonOffset;

    /**
     * Whether somebody <em>else</em> has taken the left margin.
     *
     * <p>{@code AbstractContainerScreen.init} centres the inventory at
     * {@code (width - imageWidth) / 2}, and the recipe book — and LegendQuest's panes, which follow
     * the same convention — move it off centre to make room. So an off-centre inventory means the
     * space is spoken for, and that is true of mods which have never heard of this seam. It is the
     * cheapest useful signal in the whole feature: one comparison, no reflection, and it cooperates
     * with code that never agreed to cooperate.</p>
     *
     * <p>The second half of the test exists because <b>we</b> move it too now. Off centre at the
     * value we last wrote is our own shift, not somebody else's.</p>
     */
    public static boolean occluded(AbstractContainerScreen<?> screen) {
        int at = screen.getGuiLeft();
        return at != (screen.width - screen.getXSize()) / 2 && at != shiftedTo;
    }

    /** Put the inventory back where vanilla had it, and vanilla's button with it. */
    private static void unshift(AbstractContainerScreen<?> screen) {
        if (shiftedTo == Integer.MIN_VALUE) {
            return;
        }
        // Only if it is still where we left it. If somebody else has written leftPos since, it is
        // theirs now and restoring would be us reaching into their layout.
        if (screen.getGuiLeft() == shiftedTo) {
            moveTo(screen, (screen.width - screen.getXSize()) / 2);
        }
        shiftedTo = Integer.MIN_VALUE;
    }

    /** Write {@code leftPos}, keeping vanilla's recipe-book button with it. */
    private static void moveTo(AbstractContainerScreen<?> screen, int leftPos) {
        screen.leftPos = leftPos;
        if (recipeButton != null) {
            recipeButton.setX(leftPos + recipeButtonOffset);
        }
    }

    /**
     * Tell the open pane the screen has come back, so it can ask again.
     *
     * <p>⚠ <b>Without this a pane goes stale and looks broken.</b> A pane stays open across an
     * inventory close — that is what makes it a toggle rather than a thing you reopen — so
     * {@code onOpen} would fire once and never again while everything behind it went on changing.
     * Factions' panel showed a faction with no standard and no allies for as long as it took to
     * plant one and seed nine neighbours, because nothing had asked the server since.</p>
     *
     * <p>It is the one thing the seam took away when that panel adopted it: the panel used to hold
     * its own {@code Init.Post} handler and refresh there. Moving the layout into the seam moved
     * this with it, and nothing noticed, because a stale pane draws perfectly.</p>
     */
    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        // A fresh screen is centred and its widgets are freshly placed, so any shift we were
        // holding belongs to the screen that has just gone.
        shiftedTo = Integer.MIN_VALUE;
        findRecipeButton(screen, event.getListenersList());

        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            return;
        }
        try {
            showing.panel().onOpen();
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed on reopen ({})",
                    showing.id(), e.toString());
        }
    }

    /**
     * Lay out before the screen draws; {@link #onRender} only paints.
     *
     * <p>⚠ <b>The shift has to happen in Pre, and it is not a style preference.</b> Post fires
     * after the inventory has already been drawn, so moving {@code leftPos} there would place the
     * inventory using last frame's value and the pane using this frame's — one frame of the two
     * overlapping, every single time the pane opens or the window changes. Layout, then draw, in
     * that order, is the only arrangement where they cannot disagree.</p>
     */
    @SubscribeEvent
    static void onLayout(ScreenEvent.Render.Pre event) {
        drawn = false;
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        AbstractContainerScreen<?> container = screen;
        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            unshift(container);
            return;
        }
        // Asked every frame rather than remembered: a pane whose reason to exist has gone — the
        // faction you left — must not be the last thing on screen still insisting otherwise.
        if (!safeAvailable(showing.panel())) {
            unshift(container);
            Panels.close();
            return;
        }
        if (occluded(container)) {
            // Standing down, not closing — it comes back when the recipe book does. And NOT
            // unshifting: whoever moved the inventory owns that number now, and putting it back
            // would be us editing their layout.
            shiftedTo = Integer.MIN_VALUE;
            return;
        }

        // The pane and the inventory are laid out as one block and that block is centred, which is
        // what the recipe book does and therefore what the shift looks like to a player who has
        // seen one before.
        int want = Math.max(60, safeWidth(showing.panel()));
        int centred = (screen.width - container.getXSize()) / 2;
        int block = want + GAP + container.getXSize();
        int paneLeft = (screen.width - block) / 2;
        if (paneLeft < MARGIN) {
            // Too narrow to give the pane its own room. Fall back to the margin the inventory
            // already leaves, unshifted — the recipe book does the same thing under 379px, and a
            // pane squeezed to nothing is not a smaller pane, it is a stripe nobody can read.
            unshift(container);
            int room = centred - GAP - MARGIN;
            if (room < 60) {
                return;
            }
            width = Math.min(want, room);
            x = centred - GAP - width;
        } else {
            width = want;
            x = paneLeft;
            moveTo(container, paneLeft + want + GAP);
            shiftedTo = container.getGuiLeft();
        }
        height = Math.min(screen.height - MARGIN * 2, container.getYSize());
        y = container.getGuiTop();
        drawn = true;
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (!drawn || !(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            drawn = false;
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, EDGE);
        graphics.fill(x, y + height - 1, x + width, y + height, EDGE);
        graphics.fill(x, y, x + 1, y + height, EDGE);
        graphics.fill(x + width - 1, y, x + width, y + height, EDGE);

        // Guarded, because the pane belongs to another mod: one that throws should cost that mod
        // its pane rather than taking the inventory screen down with it.
        try {
            showing.panel().render(graphics, Minecraft.getInstance().font,
                    x + 1, y + 1, width - 2, height - 2, event.getMouseX(), event.getMouseY());
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed while drawing ({}); closing it",
                    showing.id(), e.toString());
            Panels.close();
        }
    }

    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!drawn || !within(event.getMouseX(), event.getMouseY())) {
            return;
        }
        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            return;
        }
        try {
            showing.panel().mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed on a click ({})",
                    showing.id(), e.toString());
        }
        // Cancelled even when the pane did not use it, so long as it landed inside: a click that
        // fell through a pane onto whatever was behind it is how the JEI-hover complaint started,
        // and the fix is the pane being solid rather than each thing behind it being taught to
        // duck. Anything inside the rectangle belongs to the pane.
        pressedInside = true;
        event.setCanceled(true);
    }

    /**
     * A drag, routed <b>without</b> the bounds check the click has.
     *
     * <p>Deliberately: a drag belongs to whoever the press landed on, and a scrollbar thumb pulled
     * quickly is outside the pane within a frame or two. Bounds-checking here is what makes a
     * scrollbar stop dead halfway down, which reads as broken rather than as having an edge. The
     * pane only hears about it if a press landed inside it first — see {@link #pressedInside}.</p>
     */
    @SubscribeEvent
    static void onDrag(ScreenEvent.MouseDragged.Pre event) {
        if (!drawn || !pressedInside) {
            return;
        }
        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            return;
        }
        try {
            if (showing.panel().mouseDragged(event.getMouseX(), event.getMouseY(),
                    event.getMouseButton(), event.getDragX(), event.getDragY())) {
                event.setCanceled(true);
            }
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed on a drag ({})",
                    showing.id(), e.toString());
        }
    }

    /**
     * The end of a drag, wherever the cursor is.
     *
     * <p>Never cancelled. A release the pane swallowed is a button somewhere else that stays stuck
     * down, and the pane has no way to know what else was waiting for it.</p>
     */
    @SubscribeEvent
    static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!pressedInside) {
            return;
        }
        pressedInside = false;
        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            return;
        }
        try {
            showing.panel().mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed on a release ({})",
                    showing.id(), e.toString());
        }
    }

    @SubscribeEvent
    static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!drawn || !within(event.getMouseX(), event.getMouseY())) {
            return;
        }
        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            return;
        }
        try {
            // Only cancelled if the pane says it used the scroll. A pane with nothing to scroll
            // must not silently swallow the wheel — that is a bug in a mod that looks unrelated.
            if (showing.panel().mouseScrolled(event.getMouseX(), event.getMouseY(),
                    event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed on a scroll ({})",
                    showing.id(), e.toString());
        }
    }

    /**
     * Find vanilla's recipe-book button among the freshly built widgets.
     *
     * <p>Identified by type and size rather than by index: it is the only {@code ImageButton} on
     * the inventory screen, and vanilla builds it 20×18. Other mods add buttons here — LegendQuest
     * adds two beside it — but those are plain {@code Button}s.</p>
     *
     * <p>⚠ <b>If it is not found, the pane does not shift at all.</b> Failing back to margin
     * drawing is the right way to be wrong: shifting the inventory while leaving vanilla's own
     * button behind would park it inside the pane, which is a visible break in vanilla's UI caused
     * by ours. Better to lose the shift than to move something and abandon half of it.</p>
     */
    private static void findRecipeButton(InventoryScreen screen,
            java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> widgets) {
        recipeButton = null;
        for (var listener : widgets) {
            if (listener instanceof net.minecraft.client.gui.components.ImageButton button
                    && button.getWidth() == 20 && button.getHeight() == 18) {
                recipeButton = button;
                recipeButtonOffset = button.getX() - screen.getGuiLeft();
                return;
            }
        }
    }

    private static boolean within(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static boolean safeAvailable(InventoryPanel panel) {
        try {
            return panel.available();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static int safeWidth(InventoryPanel panel) {
        try {
            return panel.preferredWidth();
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    private PanelHost() {}
}

package com.sablednah.standards.client.panels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    /** Top and bottom margin for a tall pane. Two, because that is LegendQuest's rule. */
    private static final int V_MARGIN = 2;

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
     * Vanilla's recipe book itself, so the question "is it open" can be <em>asked</em>.
     *
     * <p>{@code AbstractRecipeBookScreen} keeps it private, but it is added to the screen with
     * {@code addWidget}, so it arrives in the listener list like any other — no reflection and no
     * second access transformer. {@code RecipeBookComponent} is public, and so are
     * {@code isVisible()} and {@code updateScreenPosition(...)}.</p>
     */
    private static net.minecraft.client.gui.screens.recipebook.RecipeBookComponent<?> recipeBook;

    /**
     * Whether somebody <em>else</em> is holding the inventory somewhere of their own choosing.
     *
     * <p>Three values are legitimate: where vanilla wants it, where <em>we</em> would put a pane,
     * and where we last actually put one. Anything else was written by a mod that is not a
     * registrant — LegendQuest, most likely, which shifts {@code leftPos} exactly as the recipe
     * book does and knows nothing about this seam. The open pane stands down until they let go.</p>
     *
     * <p>That is the cheapest useful signal in the whole feature and it needs no reflection: a mod
     * playing by vanilla's own convention is handled correctly without having agreed to cooperate,
     * which is the only kind of cooperation you can rely on from code you do not control.</p>
     *
     * <p>⚠ <b>With one honest gap.</b> Now that we use vanilla's shift formula — which is also
     * LegendQuest's — an inventory sitting at that value is indistinguishable from ours. So we
     * stand down correctly when LQ opens <em>first</em>, and if ours is already open when LQ's
     * opens, the two panes overlap. Matching everybody else's position cost this, and it was worth
     * it: a pane that lands where no other pane on the screen lands is wrong every time, where this
     * is wrong only when two panes are open at once. It closes the day LegendQuest registers here,
     * which is what {@code PANELS-API.md} §4 is about.</p>
     */
    public static boolean occluded(AbstractContainerScreen<?> screen) {
        int at = screen.getGuiLeft();
        return at != naturalLeft(screen) && at != shiftedLeft(screen) && at != shiftedTo;
    }

    /** Put the inventory back where vanilla had it, and vanilla's button with it. */
    private static void unshift(AbstractContainerScreen<?> screen) {
        if (shiftedTo == Integer.MIN_VALUE) {
            return;
        }
        // Only if it is still where we left it. If somebody else has written leftPos since, it is
        // theirs now and restoring would be us reaching into their layout.
        //
        // Restored to where vanilla wants it *at this moment* rather than to a hardcoded centre:
        // if the recipe book has opened meanwhile, centred is the wrong answer and asking the book
        // is the right one.
        if (screen.getGuiLeft() == shiftedTo) {
            moveTo(screen, naturalLeft(screen));
        }
        shiftedTo = Integer.MIN_VALUE;
    }

    /** Write {@code leftPos}, keeping vanilla's recipe-book button with it. */
    private static void moveTo(AbstractContainerScreen<?> screen, int leftPos) {
        screen.leftPos = leftPos;
        positionRecipeButton(screen);
    }

    /**
     * A pane is about to open: put the recipe book away if it is up.
     *
     * <p>The other half of §3b's modality, and the half that has to be an <em>action</em> rather
     * than a rule. "The recipe book wins" is right when the book is what you just opened, and
     * exactly wrong when the pane is — clicking a panel button while the book is up must open the
     * panel, not refuse. So the two cases are told apart by who moved last, and the cleanest way to
     * know that is to do this here, at the moment of opening: by the time {@link #onLayout} runs,
     * a visible book can only be one that was opened <b>after</b> the pane, and closing the pane is
     * then unambiguously the right answer.</p>
     *
     * <p>Only {@code toggleVisibility()}, deliberately. Vanilla's own button also recomputes
     * {@code leftPos} and moves itself, and both of those already happen here every frame. It also
     * sets {@code buttonClicked}, which exists to swallow the following mouse-release for the
     * button that was pressed — and the button that was pressed was <em>ours</em>, so swallowing a
     * release we did not cause would eat the next click on the pane.</p>
     */
    static void makeRoom() {
        if (recipeBook != null && recipeBook.isVisible()) {
            recipeBook.toggleVisibility();
        }
    }

    /** Keep vanilla's recipe button with the inventory, wherever the inventory has got to. */
    private static void positionRecipeButton(AbstractContainerScreen<?> screen) {
        if (recipeButton != null) {
            recipeButton.setX(screen.getGuiLeft() + recipeButtonOffset);
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
        // ⚠ Every frame, not only when we move it. Vanilla repositions its recipe button inside its
        // own click handler, so the button goes stale the moment ANYBODY else writes leftPos — and
        // ours would go stale whenever vanilla does. LegendQuest learned this and says so in a
        // comment; chasing it every frame is the only arrangement where nobody has to be told.
        positionRecipeButton(container);

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
        // ⚠ MODAL, and vanilla is the one that wins. The recipe book opening CLOSES the pane rather
        // than hiding it behind: the first version stood the pane down and left it open, and from
        // the player's side that is a pane that "stays active but behind" — its button still lit,
        // its space still spoken for, and nothing visible to close. LegendQuest resolves it the
        // same way and in the same place: if the book is up, put the panel away.
        if (recipeBook != null && recipeBook.isVisible()) {
            shiftedTo = Integer.MIN_VALUE;
            Panels.close();
            return;
        }

        if (occluded(container)) {
            // ⚠ CLOSED, not stood down. The first version kept the pane open and stopped drawing
            // it, so it would come back when the other mod let go. That nicety cost the teardown
            // callback its only useful property: onClose fired for some ways of ceasing to show and
            // not others, which is a contract every consumer has to second-guess. LegendQuest asked
            // whether it was unconditional precisely so it could delete its own recipe-book
            // detection, and "almost" was not an answer worth keeping the nicety for.
            //
            // It also matters less than it did. The recipe book — the common case — now closes the
            // pane outright, and this path is only for mods outside the seam, which is transitional
            // by definition.
            shiftedTo = Integer.MIN_VALUE;
            Panels.close();
            return;
        }

        int natural = naturalLeft(container);
        int shifted = shiftedLeft(container);
        int want = Math.max(60, safeWidth(showing.panel()));
        if (shifted == natural) {
            // Too narrow for vanilla to shift, so we do not either. The pane overlays the margin
            // that is there, clamped — the same fallback the recipe book takes below 379px.
            unshift(container);
            int room = container.getGuiLeft() - GAP - MARGIN;
            if (room < 60) {
                return;
            }
            width = Math.min(want, room);
        } else {
            width = want;
            moveTo(container, shifted);
            shiftedTo = shifted;
        }
        // Clamped rather than allowed off the left edge, which is what LegendQuest does too — on a
        // window barely over 379px the shift does not buy a full panel's width.
        x = Math.max(MARGIN, container.getGuiLeft() - width - GAP);
        // ⚠ Asked EVERY FRAME and never cached — see InventoryPanel#preferredHeight. A panel's
        // height may depend on what it is showing this instant, so a host that remembered would
        // draw the wrong size the moment a tab or a picker changed, and slide it up against a
        // stale number.
        int wantHeight = safeHeight(showing.panel());
        height = Math.min(wantHeight > 0 ? wantHeight : container.getYSize(),
                screen.height - V_MARGIN * 2);
        // LegendQuest's rule verbatim: anchor at the inventory's top, slide up only as far as
        // needed, never above the top margin, always leaving one at the bottom.
        y = Math.max(V_MARGIN,
                Math.min(container.getGuiTop(), screen.height - height - V_MARGIN));
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
        // 26.x: GuiGraphics is GuiGraphicsExtractor here.
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        // The palette is the panel's; Standards holds no mod's colours and paints with what it is
        // handed. A panel that says nothing gets the shared furniture.
        PanelTheme theme = safeTheme(showing.panel());
        graphics.fill(x, y, x + width, y + height, theme.background());
        graphics.fill(x, y, x + width, y + 1, theme.border());
        graphics.fill(x, y + height - 1, x + width, y + height, theme.border());
        graphics.fill(x, y, x + 1, y + height, theme.border());
        graphics.fill(x + width - 1, y, x + width, y + height, theme.border());

        // Guarded, because the pane belongs to another mod: one that throws should cost that mod
        // its pane rather than taking the inventory screen down with it.
        try {
            showing.panel().render(graphics, Minecraft.getInstance().font,
                    x + 1, y + 1, width - 2, height - 2, event.getMouseX(), event.getMouseY());
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed while drawing ({}); closing it",
                    showing.id(), e.toString());
            Panels.close();
            return;
        }

        // Last, and outside everything: a tooltip drawn where it was asked for would be painted
        // over by whatever the pane drew next, and one clipped to the pane would be truncated —
        // they are positioned in screen coordinates and are meant to hang over the inventory.
        // Nothing here scissors, and InventoryPanel#renderOverlay says so as a guarantee.
        try {
            showing.panel().renderOverlay(graphics, Minecraft.getInstance().font,
                    event.getMouseX(), event.getMouseY());
        } catch (RuntimeException | LinkageError e) {
            Standards.LOGGER.warn("Standards: panel '{}' failed drawing its overlay ({})",
                    showing.id(), e.toString());
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
        recipeBook = null;
        for (var listener : widgets) {
            if (recipeButton == null
                    && listener instanceof net.minecraft.client.gui.components.ImageButton button
                    && button.getWidth() == 20 && button.getHeight() == 18) {
                recipeButton = button;
                recipeButtonOffset = button.getX() - screen.getGuiLeft();
            } else if (listener
                    instanceof net.minecraft.client.gui.screens.recipebook.RecipeBookComponent<?> b) {
                recipeBook = b;
            }
        }
    }

    /**
     * Where vanilla would put the inventory right now — centred, or shifted for its own book.
     *
     * <p>Asked of the recipe book rather than computed, because only it knows whether it is open.
     * That is also what makes putting the inventory back safe: we restore to whatever vanilla wants
     * at that moment, not to a "centred" that may no longer be true.</p>
     */
    private static int naturalLeft(AbstractContainerScreen<?> screen) {
        return recipeBook != null
                ? recipeBook.updateScreenPosition(screen.width, screen.getXSize())
                : (screen.width - screen.getXSize()) / 2;
    }

    /**
     * Where the inventory goes with a pane open — <b>vanilla's own formula, deliberately copied</b>.
     *
     * <pre>177 + (width - imageWidth - 200) / 2</pre>
     *
     * <p>This is what {@code RecipeBookComponent.updateScreenPosition} computes for itself, and
     * what LegendQuest's panes use. Standards' first attempt centred the pane and the inventory as
     * a block instead, which is defensible arithmetic and lands the inventory somewhere no other
     * panel on the screen puts it — so opening two mods' panes in turn made the inventory hop. The
     * number matters less than everyone using the same one.</p>
     *
     * <p>Below 379px vanilla does not shift at all ({@code widthTooNarrow}), and neither do we.</p>
     */
    private static int shiftedLeft(AbstractContainerScreen<?> screen) {
        return screen.width >= 379
                ? 177 + (screen.width - screen.getXSize() - 200) / 2
                : naturalLeft(screen);
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

    private static int safeHeight(InventoryPanel panel) {
        try {
            return Math.max(0, panel.preferredHeight());
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    /** A foreign theme that throws costs its own mod its colours, and nothing else. */
    private static PanelTheme safeTheme(InventoryPanel panel) {
        try {
            PanelTheme theme = panel.theme();
            return theme == null ? PanelTheme.STANDARD : theme;
        } catch (RuntimeException | LinkageError e) {
            return PanelTheme.STANDARD;
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

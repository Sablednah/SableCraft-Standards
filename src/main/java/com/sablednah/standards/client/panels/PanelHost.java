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
     * Whether somebody else has taken the left margin.
     *
     * <p>One comparison, no reflection: {@code AbstractContainerScreen.init} centres the inventory
     * at {@code (width - imageWidth) / 2}, and the recipe book — and LegendQuest's panes, which
     * follow the same convention — move it off centre to make room. So an off-centre inventory
     * means the space is spoken for, and it is true for mods that have never heard of this seam.</p>
     */
    public static boolean occluded(AbstractContainerScreen<?> screen) {
        return screen.getGuiLeft() != (screen.width - screen.getXSize()) / 2;
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        drawn = false;
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        var showing = Panels.showing().orElse(null);
        if (showing == null) {
            return;
        }
        // Asked every frame rather than remembered: a pane whose reason to exist has gone — the
        // faction you left — must not be the last thing on screen still insisting otherwise.
        if (!safeAvailable(showing.panel())) {
            Panels.close();
            return;
        }
        if (occluded(screen)) {
            // Standing down, not closing. It comes back when the recipe book does.
            return;
        }

        AbstractContainerScreen<?> container = screen;
        int want = Math.max(60, safeWidth(showing.panel()));
        int room = container.getGuiLeft() - GAP - MARGIN;
        if (room < 60) {
            // Not enough margin to be worth drawing in. Silent rather than squeezed: a pane four
            // pixels wide is not a smaller pane, it is a stripe nobody can read.
            return;
        }
        width = Math.min(want, room);
        height = Math.min(screen.height - MARGIN * 2, container.getYSize());
        x = container.getGuiLeft() - GAP - width;
        y = container.getGuiTop();

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, EDGE);
        graphics.fill(x, y + height - 1, x + width, y + height, EDGE);
        graphics.fill(x, y, x + 1, y + height, EDGE);
        graphics.fill(x + width - 1, y, x + width, y + height, EDGE);
        drawn = true;

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
        event.setCanceled(true);
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

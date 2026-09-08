package com.sablednah.standards.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import com.sablednah.standards.api.actions.Action;
import com.sablednah.standards.api.actions.Actions;

/**
 * A row of buttons under the inventory, for whatever the server said this player may do.
 *
 * <h2>Under, not beside — and that is the second answer</h2>
 *
 * <p>The first version put a column down the left, which is where FTB's mods put theirs. Watched in
 * a real modpack, that was wrong: the left edge is crowded. The recipe book opens there, LegendQuest
 * puts its character sheet and skill panel there, Baubles and its like open there. Following the
 * inventory to stay flush against it meant landing <em>on top of</em> whatever else had opened.</p>
 *
 * <p>Under the inventory is nobody's territory. It moves with the screen for free, it cannot be
 * covered by a side panel, and it has room to grow sideways where a column has to grow into the
 * screen edge.</p>
 *
 * <h2>A row per mod</h2>
 *
 * <p>Actions are grouped by the namespace of their id — Standards' own are bare, everybody else's
 * are {@code "storyteller:possess"} — and each mod gets its own row. Mixing them would put a
 * storyteller's possession button between {@code /home} and {@code /back} on the strength of a
 * priority number, which reads as arbitrary because it is. Grouped, the bar says <em>these five are
 * one tool</em> without anybody being told.</p>
 *
 * <p>A row that will not fit wraps rather than running off the screen, so a mod contributing twenty
 * actions costs vertical space rather than correctness.</p>
 */
public final class ActionBar {

    private static final int SIZE = 20;
    private static final int GAP = 2;
    /** Left of the inventory, with room for the icon. Nudged out so it does not touch the frame. */
    private static final int OFFSET = 4;
    private static final int CHILD_HEIGHT = 14;

    private static final List<Entry> DRAWN = new ArrayList<>();
    /**
     * The open expansion's entries, drawn and hit-tested by us.
     *
     * <p>Not vanilla {@link Button} widgets: a screen's listener list is not ours to add to, and
     * the click handling is already here for the right-click anyway. Three fields and a bounds
     * test beats borrowing a widget we would have to fight.</p>
     */
    private static final List<Kid> CHILDREN = new ArrayList<>();

    private record Kid(String label, String command, int x, int y, int width) {}

    /**
     * Which action's children are showing, if any.
     *
     * <p><b>One at a time.</b> Several open rows would be a tree drawn sideways, and the bar's
     * whole value is being readable at a glance.</p>
     */
    private static String expanded;

    private record Entry(Action action, Button button) {}

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        DRAWN.clear();
        CHILDREN.clear();
        // Closed on every screen open. An expansion is a transient answer to "which home", not a
        // setting, and finding one still open next time would be a small mystery.
        expanded = null;
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        // Nothing to draw is the ordinary case: a server without Standards, or a player granted
        // nothing. Costing nothing there is the point of asking first.
        if (!ClientCapabilities.any()) {
            return;
        }
        // Grouped by mod so the rows mean something, Standards first because every server has it.
        List<Action> ordered = new ArrayList<>(Actions.all().stream()
                .filter(a -> ClientCapabilities.has(a.id())).toList());
        ordered.sort(java.util.Comparator
                .comparing((Action a) -> modOf(a.id()).equals("standards") ? 0 : 1)
                .thenComparing(a -> modOf(a.id()))
                .thenComparing(java.util.Comparator.comparingInt(Action::priority).reversed()));
        for (Action action : ordered) {
            Button button = Button.builder(Component.empty(),
                            // A category has no command, so a left click opens it rather than
                            // doing nothing — a button that ignores a click is worse than none.
                            b -> {
                                if (action.isCategory()) {
                                    toggle(action.id());
                                } else {
                                    ClientActions.run(action.id());
                                }
                            })
                    .bounds(0, 0, SIZE, SIZE)
                    .tooltip(Tooltip.create(tooltip(action)))
                    .build();
            event.addListener(button);
            DRAWN.add(new Entry(action, button));
        }
        position(screen);
    }

    /**
     * The tooltip says the state, not just the name.
     *
     * <p>"Fly — on" reads as an answer; "Fly" reads as a label. For a gamemaster tool the state is
     * the more valuable half, and it is the reason actions carry an {@code active} flag at all.</p>
     */
    private static Component tooltip(Action action) {
        String name = ClientLang.get(action.tooltipKey());
        boolean hasChildren = !ClientCapabilities.children(action.id()).isEmpty();
        String hint = ClientCapabilities.hint(action.id());
        StringBuilder text = new StringBuilder(name);
        if (!hint.isEmpty()) {
            text.append(" (").append(hint).append(')');
        }
        if (ClientCapabilities.isActive(action.id())) {
            text.append(" — on");
        }
        // Said in the tooltip, because a corner mark tells you there IS something and not how to
        // reach it — and nobody guesses at right-click on a button that already does something.
        if (hasChildren) {
            text.append(action.isCategory() ? "\nClick to list" : "\nRight-click to list");
        }
        return Component.literal(text.toString());
    }

    /**
     * Lay them out under the inventory: one row per mod, wrapping when a row will not fit.
     *
     * <p>Ordered so Standards' own row comes first — it is the one every server has — and other
     * mods follow in the order their highest-priority action would have come. Within a row the
     * priority order the seam already guarantees is kept.</p>
     */
    private static void position(InventoryScreen screen) {
        AbstractContainerScreen<?> container = screen;
        int left = container.getGuiLeft();
        int top = container.getGuiTop() + container.getYSize() + GAP * 2;
        int perRow = Math.max(1, container.getXSize() / (SIZE + GAP));

        // How many rows this will take, worked out before placing anything, so the whole block
        // can be lifted if it would run off the bottom. A second mod's row appearing half off the
        // screen is exactly the failure a column had at the left edge, rotated ninety degrees.
        int rowsNeeded = rowsFor(perRow);
        int blockHeight = rowsNeeded * (SIZE + GAP);
        if (top + blockHeight > screen.height) {
            top = Math.max(0, screen.height - blockHeight);
        }

        String currentMod = null;
        int column = 0;
        int row = 0;
        for (Entry entry : DRAWN) {
            String mod = modOf(entry.action().id());
            // A new mod starts a new row, so a glance groups them without a label.
            if (currentMod != null && !mod.equals(currentMod)) {
                row++;
                column = 0;
            }
            currentMod = mod;
            if (column >= perRow) {
                row++;
                column = 0;
            }
            entry.button().setPosition(left + column * (SIZE + GAP), top + row * (SIZE + GAP));
            column++;
        }
        layoutChildren(screen, left, top + (row + 1) * (SIZE + GAP), container.getXSize());
    }

    /**
     * Right-click a button to open what is underneath it.
     *
     * <p>Right rather than left, because left must go on running the command: a {@code /home}
     * button that stopped going home the day it gained a list of homes would be a regression
     * dressed as a feature. So left is the thing you meant, right is the thing you might have
     * meant instead.</p>
     *
     * <p>Handled here rather than by subclassing {@link Button}, because vanilla's widget refuses
     * anything but the left mouse button and overriding that is a version-fragile surface for no
     * gain — the bounds test is three lines and cannot break on a Minecraft update.</p>
     */
    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (DRAWN.isEmpty()) {
            return;
        }
        // A left click on an open child runs it. Checked before the right-click work below, so a
        // child cannot be shadowed by whatever button happens to sit behind it.
        if (event.getButton() == 0) {
            for (Kid kid : CHILDREN) {
                if (event.getMouseX() >= kid.x() && event.getMouseX() < kid.x() + kid.width()
                        && event.getMouseY() >= kid.y()
                        && event.getMouseY() < kid.y() + CHILD_HEIGHT) {
                    ClientActions.runCommand(kid.command());
                    toggle(expanded);
                    event.setCanceled(true);
                    return;
                }
            }
            return;
        }
        if (event.getButton() != 1) {
            return;
        }
        for (Entry entry : DRAWN) {
            if (within(entry.button(), event.getMouseX(), event.getMouseY())
                    && !ClientCapabilities.children(entry.action().id()).isEmpty()) {
                toggle(entry.action().id());
                event.setCanceled(true);
                return;
            }
        }
    }

    private static boolean within(Button button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    /** Open this action's children, or close them if they are already open. */
    private static void toggle(String id) {
        expanded = id.equals(expanded) ? null : id;
        CHILDREN.clear();
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen instanceof InventoryScreen inventory) {
            position(inventory);
        }
    }

    /**
     * Lay the open expansion out under the bar.
     *
     * <p>Sized to their labels rather than to a grid: a home called {@code base} and one called
     * {@code the-far-mine} want different widths, and equal boxes would either truncate the second
     * or waste space on the first.</p>
     */
    private static void layoutChildren(InventoryScreen screen, int left, int belowY, int maxWidth) {
        CHILDREN.clear();
        if (expanded == null) {
            return;
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int x = left;
        int y = belowY;
        for (var child : ClientCapabilities.children(expanded)) {
            int width = Math.max(20, font.width(child.label()) + 8);
            if (x > left && x + width > left + maxWidth) {
                x = left;
                y += CHILD_HEIGHT + 1;
            }
            CHILDREN.add(new Kid(child.label(), child.command(), x, y, width));
            x += width + 2;
        }
    }

    /** How many rows the current set needs, at this width. */
    private static int rowsFor(int perRow) {
        String currentMod = null;
        int column = 0;
        int rows = 1;
        for (Entry entry : DRAWN) {
            String mod = modOf(entry.action().id());
            if (currentMod != null && !mod.equals(currentMod)) {
                rows++;
                column = 0;
            }
            currentMod = mod;
            if (column >= perRow) {
                rows++;
                column = 0;
            }
            column++;
        }
        return rows;
    }

    /** The mod an action belongs to. Standards' own ids are bare, which is the owner's one liberty. */
    private static String modOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "standards" : id.substring(0, colon);
    }

    /**
     * Re-position every frame, and draw the icons over the buttons.
     *
     * <p>Every frame rather than once, because the recipe book can open without the screen being
     * re-initialised on every version, and a bar that has slid under the inventory is worse than
     * no bar. It is arithmetic on a handful of buttons; the cost is not measurable.</p>
     */
    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (DRAWN.isEmpty() || !(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        position(screen);
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        renderChildren(graphics);
        for (Entry entry : DRAWN) {
            ItemStack icon = iconFor(entry.action());
            if (icon.isEmpty()) {
                continue;
            }
            int x = entry.button().getX();
            int y = entry.button().getY();

            // A category, or anything with children, says so: a small corner mark. Without it a
            // right-click menu is a secret, and a category's left click looks like a dead button.
            boolean hasChildren = !ClientCapabilities.children(entry.action().id()).isEmpty();

            // The "on" state is drawn UNDER the icon as a filled panel and a border, not as a wash
            // over it. The first version tinted the icon at 25% alpha and it was invisible against
            // a coloured item — watched in game, reported as "either not working or so subtle it is
            // invisible", which is the same thing from the player's side.
            if (ClientCapabilities.isActive(entry.action().id())) {
                graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF1E5E1E);
                // A border too: a filled panel alone is ambiguous against a dark inventory
                // background, and an outline reads as "this one is different" at any scale.
                graphics.fill(x, y, x + SIZE, y + 1, 0xFF55FF55);
                graphics.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, 0xFF55FF55);
                graphics.fill(x, y, x + 1, y + SIZE, 0xFF55FF55);
                graphics.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, 0xFF55FF55);
            }
            graphics.item(icon, x + 2, y + 2);

            // renderItemDecorations, NOT drawString. This is the stack-count overlay, and it is
            // the only text that reliably lands above an item: an item renders at a raised Z, so
            // flat text drawn afterwards is still behind it. That is why the first version showed
            // no number even with four homes to report — and the same Z problem made the "on"
            // tint invisible, which is why the panel above is drawn BEFORE the item rather than
            // over it. One root cause, two symptoms, and neither looked like a Z-order bug.
            if (hasChildren) {
                // Bottom-left corner, away from the stack-count position a hint uses.
                int markY = y + SIZE - 4;
                graphics.fill(x + 2, markY, x + 7, markY + 2,
                        expanded != null && expanded.equals(entry.action().id())
                                ? 0xFF55FF55 : 0xFFAAAAAA);
            }

            String hint = ClientCapabilities.hint(entry.action().id());
            if (!hint.isEmpty()) {
                graphics.itemDecorations(
                        net.minecraft.client.Minecraft.getInstance().font, icon, x + 2, y + 2,
                        hint);
            }
        }
    }

    /** Draw the open expansion: a dark box per entry with its label. */
    private static void renderChildren(GuiGraphicsExtractor graphics) {
        if (CHILDREN.isEmpty()) {
            return;
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        for (Kid kid : CHILDREN) {
            graphics.fill(kid.x(), kid.y(), kid.x() + kid.width(), kid.y() + CHILD_HEIGHT,
                    0xE0101010);
            graphics.fill(kid.x(), kid.y(), kid.x() + kid.width(), kid.y() + 1, 0xFF6A6A6A);
            graphics.fill(kid.x(), kid.y() + CHILD_HEIGHT - 1, kid.x() + kid.width(),
                    kid.y() + CHILD_HEIGHT, 0xFF6A6A6A);
            graphics.text(font, kid.label(),
                    kid.x() + (kid.width() - font.width(kid.label())) / 2, kid.y() + 3,
                    0xFFFFFF, false);
        }
    }

    private static ItemStack iconFor(Action action) {
        var item = BuiltInRegistries.ITEM.getValue(action.icon());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private ActionBar() {}
}

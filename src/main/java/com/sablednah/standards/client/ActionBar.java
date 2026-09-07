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
 * A column of buttons beside the inventory, for whatever the server said this player may do.
 *
 * <h2>Left, not right</h2>
 *
 * <p>The right-hand column belongs to JEI or REI in nearly every modpack, and fighting them is a
 * fight we would lose weekly. Left is also where FTB's mods put theirs, so it is where a player
 * already looks.</p>
 *
 * <h2>The recipe book moves the inventory</h2>
 *
 * <p>Opening it shifts the whole panel right by half its width, so the bar is positioned relative
 * to the screen's <em>current</em> left edge every time the screen initialises rather than once.
 * Vanilla re-inits the screen when the book opens, so that is enough — and it is why the position
 * is computed in the render pass rather than baked into the button at construction.</p>
 */
public final class ActionBar {

    private static final int SIZE = 20;
    private static final int GAP = 2;
    /** Left of the inventory, with room for the icon. Nudged out so it does not touch the frame. */
    private static final int OFFSET = 4;

    private static final List<Entry> DRAWN = new ArrayList<>();

    private record Entry(Action action, Button button) {}

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        DRAWN.clear();
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        // Nothing to draw is the ordinary case: a server without Standards, or a player granted
        // nothing. Costing nothing there is the point of asking first.
        if (!ClientCapabilities.any()) {
            return;
        }
        for (Action action : Actions.all()) {
            if (!ClientCapabilities.has(action.id())) {
                continue;
            }
            Button button = Button.builder(Component.empty(),
                            b -> ClientActions.run(action.id()))
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
        String hint = ClientCapabilities.hint(action.id());
        StringBuilder text = new StringBuilder(name);
        if (!hint.isEmpty()) {
            text.append(" (").append(hint).append(')');
        }
        if (ClientCapabilities.isActive(action.id())) {
            text.append(" — on");
        }
        return Component.literal(text.toString());
    }

    /** Stack them down the left edge, from the screen's current position. */
    private static void position(InventoryScreen screen) {
        int left = ((AbstractContainerScreen<?>) screen).getGuiLeft() - SIZE - OFFSET;
        int top = ((AbstractContainerScreen<?>) screen).getGuiTop();
        for (int i = 0; i < DRAWN.size(); i++) {
            DRAWN.get(i).button().setPosition(left, top + i * (SIZE + GAP));
        }
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
        // 26.x reworked GUI rendering: GuiGraphics became GuiGraphicsExtractor, renderItem
        // became item, and drawString became text. Recorded in CROSS-VERSION.md — the first
        // divergence in this pair that is genuinely about drawing rather than an accessor rename.
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        for (Entry entry : DRAWN) {
            ItemStack icon = iconFor(entry.action());
            if (icon.isEmpty()) {
                continue;
            }
            graphics.item(icon, entry.button().getX() + 2, entry.button().getY() + 2);
            // Active is drawn as a tint over the icon rather than a different icon, so a mod
            // supplying one icon gets the on/off distinction for free.
            if (ClientCapabilities.isActive(entry.action().id())) {
                graphics.fill(entry.button().getX() + 1, entry.button().getY() + 1,
                        entry.button().getX() + SIZE - 1, entry.button().getY() + SIZE - 1,
                        0x4000FF00);
            }
            String hint = ClientCapabilities.hint(entry.action().id());
            if (!hint.isEmpty()) {
                graphics.text(net.minecraft.client.Minecraft.getInstance().font, hint,
                        entry.button().getX() + SIZE - 8, entry.button().getY() + SIZE - 9,
                        0xFFFFFF, true);
            }
        }
    }

    private static ItemStack iconFor(Action action) {
        var item = BuiltInRegistries.ITEM.getValue(action.icon());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private ActionBar() {}
}

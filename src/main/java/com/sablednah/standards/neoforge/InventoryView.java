package com.sablednah.standards.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A live six-row chest view onto another player's inventory, for {@code /invsee}.
 *
 * <p>Live, not a copy: an item a moderator takes must actually leave the player, because a copy
 * would silently duplicate it — the worst possible bug in a moderation tool.</p>
 *
 * <p>A chest of six rows is 54 slots and a player inventory is 43, so they do not line up. The
 * layout is deliberate rather than a straight run, so the sections are readable at a glance:</p>
 *
 * <pre>
 *   chest  0-26  -> inventory  9-35   main storage, as the player sees it
 *   chest 27-35  -> inventory  0-8    hotbar, on its own row
 *   chest 36-44  -> DEAD             a labelled divider row
 *   chest 45-53  -> the equipment row, position by position:
 *                     H C L B X O X S A
 *                     helmet(39) chestplate(38) leggings(37) boots(36) spacer
 *                     off hand(40) spacer saddle(42) animal armour(41)
 * </pre>
 *
 * <h2>Equipment slots accept anything, on purpose</h2>
 *
 * <p>Vanilla's armour slots restrict by item type; these do not, and that is a settled decision
 * rather than an omission — <b>do not add a config to "fix" it.</b> The guard that matters is
 * against <em>accidents</em>, and that is handled by shift-click never reaching the equipment row
 * (see {@link #STORAGE_SLOTS}). Putting something odd on a player therefore takes a deliberate,
 * targeted click, which is exactly the bar a staff tool should set.</p>
 *
 * <p>Enforcing item types would cost more than it saves: modded armour that equips through its own
 * mechanism may not satisfy vanilla's {@code getEquipmentSlotForItem} check, and a moderation tool
 * that refuses to hand a player back their own chestplate is worse than one that permits a silly
 * hat. Vanilla agrees, incidentally — any block is legitimately wearable in the head slot.</p>
 *
 * <h2>Dead slots are filled, not empty — and that is a bug fix</h2>
 *
 * <p>The first version left them empty and relied on {@link #setItem} ignoring writes there. That
 * <b>destroyed items</b>: the client has already taken the stack off the cursor by the time the
 * server sees it, so discarding the write means it is gone. Reported by two people testing, with
 * screenshots.</p>
 *
 * <p>The container-level guard could not work either, because vanilla's {@code Slot.mayPlace}
 * returns {@code true} unconditionally and never consults {@link Container#canPlaceItem} — so a
 * plain {@code ChestMenu} physically cannot refuse a placement. The fix therefore needs
 * {@link InventoryViewMenu}, which uses slots that really do refuse, and these filler panes, so
 * the dead space is visible instead of being a hole items fall into.</p>
 */
public final class InventoryView implements Container {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;
    /**
     * The storage half: main inventory and hotbar, chest slots 0-35.
     *
     * <p>Shift-click may only ever land here. Everything below is equipment, and an item shoved
     * into someone's saddle slot because their backpack happened to be full is not a thing anyone
     * asked for — vanilla avoids it by chests simply not having equipment slots.</p>
     */
    public static final int STORAGE_SLOTS = 36;
    /** Not backed by anything in the player. */
    private static final int NONE = -1;

    private final Inventory target;
    private final int[] mapping = new int[SIZE];
    private final ItemStack[] filler = new ItemStack[SIZE];

    public InventoryView(ServerPlayer target) {
        this.target = target.getInventory();
        System.arraycopy(buildMapping(this.target.getContainerSize()), 0, mapping, 0, SIZE);

        for (int slot = 0; slot < SIZE; slot++) {
            if (mapping[slot] != NONE) continue;
            filler[slot] = pane("Unused");
        }
        // The divider row sits directly above the equipment row, so each pane names what is
        // beneath it — the layout explains itself on hover instead of in documentation nobody
        // reads.
        for (int i = 0; i < BOTTOM_LABELS.length; i++) {
            filler[36 + i] = pane(BOTTOM_LABELS[i] == null
                    ? "Hotbar above"
                    : BOTTOM_LABELS[i] + " below");
        }
    }

    /**
     * The equipment row, written out position by position so it can be read against the layout
     * rather than derived from arithmetic:
     *
     * <pre>
     *     H C L B X O X S A
     *     helmet, chestplate, leggings, boots, spacer,
     *     off hand, spacer, saddle, animal armour
     * </pre>
     *
     * <p>The two spacers are punctuation, not padding. Four slots together read as a set, one
     * alone reads as its own thing, and a moderator infers "armour" and "off hand" without a
     * label — grouping explains a layout better than naming ever does.</p>
     *
     * <p>Saddle and animal armour get permanent places rather than appearing only when occupied.
     * They are slots a player can hold something in but never sees, so a tool that hid them would
     * be exactly the wrong tool.</p>
     */
    private static final int[] BOTTOM_ROW = {39, 38, 37, 36, NONE, 40, NONE, 42, 41};

    private static final String[] BOTTOM_LABELS = {
        "Helmet", "Chestplate", "Leggings", "Boots", null,
        "Off hand", null, "Saddle", "Animal armour"
    };

    /**
     * Chest slot to inventory slot, as a pure function of the inventory's size.
     *
     * <p>Static and player-free precisely so it is testable. The first version of this check
     * needed a live player and was therefore skipped on an empty dev server — a test that quietly
     * does not run is worse than no test, because it reports as a pass.</p>
     */
    public static int[] buildMapping(int inventorySize) {
        int[] map = new int[SIZE];
        java.util.Arrays.fill(map, NONE);
        for (int i = 0; i < 27; i++) map[i] = i + 9;      // main storage
        for (int i = 0; i < 9; i++) map[27 + i] = i;       // hotbar
        for (int i = 0; i < BOTTOM_ROW.length; i++) {
            if (BOTTOM_ROW[i] != NONE && BOTTOM_ROW[i] < inventorySize) {
                map[45 + i] = BOTTOM_ROW[i];
            }
        }
        return map;
    }

    private static ItemStack pane(String label) {
        ItemStack stack = new ItemStack(Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.GRAY));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(label).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return stack;
    }

    /** Is this chest slot backed by a real inventory slot? Drives the menu's slot behaviour. */
    public boolean isLive(int slot) {
        return slot >= 0 && slot < SIZE && mapping[slot] != NONE;
    }

    private int real(int slot) {
        return slot >= 0 && slot < SIZE ? mapping[slot] : NONE;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (isLive(i) && !getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        int real = real(slot);
        return real == NONE ? filler[slot] : target.getItem(real);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        int real = real(slot);
        return real == NONE ? ItemStack.EMPTY : target.removeItem(real, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        int real = real(slot);
        return real == NONE ? ItemStack.EMPTY : target.removeItemNoUpdate(real);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        int real = real(slot);
        // Unreachable now that the menu's slots refuse dead positions, and left refusing rather
        // than silently swallowing: this exact path is what destroyed items before.
        if (real != NONE) target.setItem(real, stack);
    }

    @Override
    public void setChanged() {
        target.setChanged();
    }

    @Override
    public boolean stillValid(Player viewer) {
        return true;
    }

    @Override
    public void clearContent() {
        // Deliberately not wired: emptying someone's inventory is /clear's job, not a side effect
        // of a viewing tool.
    }
}

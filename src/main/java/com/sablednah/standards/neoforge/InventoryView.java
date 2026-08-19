package com.sablednah.standards.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A live six-row chest view onto another player's inventory, for {@code /invsee}.
 *
 * <p>Live, not a copy: a moderator taking a stolen item out of this must actually take it out of
 * the player, and a copy would silently duplicate items instead — the worst possible bug in a
 * moderation tool.</p>
 *
 * <p>A chest of six rows is 54 slots and a player inventory is 42, so the sizes do not line up and
 * {@code ChestMenu.sixRows} would refuse the container outright. This maps them deliberately:</p>
 *
 * <pre>
 *   chest  0-26  -> inventory  9-35   main inventory, laid out as the player sees it
 *   chest 27-35  -> inventory  0-8    hotbar, on its own row underneath
 *   chest 36-44  -> (blank)           a visual gap
 *   chest 45-50  -> inventory 36-41   armour and offhand
 *   chest 51-53  -> (blank)
 * </pre>
 *
 * <p>The gap is worth the two wasted rows: hotbar and armour landing in a readable arrangement is
 * the difference between a tool a moderator can use at a glance and a wall of 42 squares.</p>
 */
public final class InventoryView implements Container {

    public static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    /** Blank, non-interactive filler. */
    private static final int NONE = -1;

    private final Inventory target;
    private final int[] mapping = new int[SIZE];

    public InventoryView(ServerPlayer target) {
        this.target = target.getInventory();
        java.util.Arrays.fill(mapping, NONE);
        for (int i = 0; i < 27; i++) mapping[i] = i + 9;          // main
        for (int i = 0; i < 9; i++) mapping[27 + i] = i;           // hotbar
        int equipment = this.target.getContainerSize() - 36;       // however many vanilla exposes
        for (int i = 0; i < equipment && 45 + i < SIZE; i++) {
            mapping[45 + i] = 36 + i;                              // armour, offhand
        }
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
            if (!getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        int real = real(slot);
        return real == NONE ? ItemStack.EMPTY : target.getItem(real);
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
        // Silently dropping a write into a blank slot is correct here: the alternative is an item
        // vanishing from the moderator's cursor into a slot that does not exist.
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
        // Deliberately not wired: clearing someone's inventory is /clear's job, not a side effect
        // of a viewing tool.
    }
}

package com.sablednah.standards.neoforge;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The menu behind {@code /invsee}.
 *
 * <p>Exists because {@code ChestMenu} cannot do the one thing this needs. Vanilla's
 * {@code Slot.mayPlace} returns {@code true} unconditionally and never asks the container, so a
 * chest menu will accept an item into <em>any</em> of its 54 slots — including the dozen that a
 * 43-slot player inventory does not reach. Those placements then had nowhere to go and the items
 * were destroyed.</p>
 *
 * <p>So the slots here refuse: dead positions accept nothing and give up nothing. Combined with
 * the filler panes in {@link InventoryView}, the unusable space is both visible and inert.</p>
 *
 * <p>{@code MenuType.GENERIC_9x6} means the client draws an ordinary double chest and needs no mod
 * of its own — {@code /invsee} works for a vanilla client, like everything else here.</p>
 */
public class InventoryViewMenu extends AbstractContainerMenu {

    private final InventoryView view;

    public InventoryViewMenu(int containerId, Inventory viewer, InventoryView view) {
        super(MenuType.GENERIC_9x6, containerId);
        this.view = view;

        for (int row = 0; row < InventoryView.ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                addSlot(new Slot(view, index, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return view.isLive(index);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return view.isLive(index);
                    }
                });
            }
        }

        // The viewer's own inventory underneath, at vanilla's offsets for a six-row chest.
        int offset = (InventoryView.ROWS - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(viewer, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + offset));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(viewer, col, 8 + col * 18, 161 + offset));
        }
    }

    /**
     * Shift-click. {@code moveItemStackTo} consults {@code mayPlace}, so this inherits the refusal
     * of dead slots for free — but it has to be implemented at all, because the default throws.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        // hasItem() is not enough: the filler panes ARE items, so shift-clicking one would move a
        // pane into the viewer's inventory and then call set(EMPTY) on a dead slot — a no-op —
        // conjuring panes out of nothing. Shift-click is where inventory dupes live; ask whether
        // the slot will actually give the item up, not merely whether it has one.
        if (!slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < InventoryView.SIZE) {
            if (!moveItemStackTo(stack, InventoryView.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            // Storage only, never the equipment row. Without this bound, shift-clicking with a
            // full inventory spills into the armour, off-hand and saddle slots — reported after
            // exactly that happened. Dressing someone stays possible, but only deliberately.
        } else if (!moveItemStackTo(stack, 0, InventoryView.STORAGE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Always valid: the target may walk away, change dimension or log out, and none of that should
     * slam the screen shut mid-click. The view reads through to a live inventory, so a logged-out
     * target simply stops changing.
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}

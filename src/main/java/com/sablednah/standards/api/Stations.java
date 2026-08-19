package com.sablednah.standards.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.SimpleContainer;

/**
 * Portable workstations, openable from code.
 *
 * <p>These exist as commands too ({@code /craft}, {@code /anvil}, …) but the commands are
 * <b>permission-denied by default</b>, deliberately: a portable workbench is not a utility every
 * player should have, it is an <em>ability</em>. A builder class gets a workbench anywhere; a
 * blacksmith gets an anvil anywhere. That is a LegendQuest skill, not a config default.</p>
 *
 * <p>Which is exactly why this API exists. A skill that ran the command as the player would be
 * refused by the very permission check that makes the design work, so skills call in here instead
 * — the caller is the authority, and it has already decided the player has earned this.</p>
 *
 * <pre>{@code
 * // "Field Forge" skill: an anvil, wherever the smith is standing
 * Stations.openAnvil(player);
 * }</pre>
 *
 * <p>Every one of these is a vanilla menu, so they work on an unmodified client. Server thread
 * only.</p>
 */
public final class Stations {

    public static void openCrafting(ServerPlayer player) {
        // ContainerLevelAccess.NULL: the menu is not attached to a real block, which is what makes
        // it portable. Vanilla handles that case — it is how the crafting grid in your own
        // inventory works.
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new CraftingMenu(id, inventory),
                Component.translatable("container.crafting")));
    }

    public static void openAnvil(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new AnvilMenu(id, inventory),
                Component.translatable("container.repair")));
    }

    public static void openGrindstone(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new GrindstoneMenu(id, inventory),
                Component.translatable("container.grindstone_title")));
    }

    public static void openEnderChest(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.threeRows(id, inventory, player.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
    }

    /**
     * A disposal bin: a chest whose contents are discarded when it closes.
     *
     * <p>Backed by a throwaway container rather than anything of the player's, so there is nothing
     * to clean up and nothing that can be recovered by force-closing the screen.</p>
     */
    public static void openTrash(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> ChestMenu.threeRows(id, inventory, new SimpleContainer(27)),
                Component.literal("Disposal")));
    }

    private Stations() {}
}

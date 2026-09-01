package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * The small tools that act on what you are holding: {@code /repair}, {@code /more},
 * {@code /condense}, {@code /itemname}, {@code /itemlore}.
 *
 * <p>Grouped because they share one shape — take the held stack, change it, say what happened —
 * and because each is far too small to be its own file.</p>
 *
 * <h2>Two of these are cheats and three are not, and the nodes say which</h2>
 *
 * <p>{@code /repair} and {@code /more} create value out of nothing: a repaired tool is an anvil and
 * some levels you did not spend, and a filled stack is items that did not exist. Both are op-gated,
 * and a survival server that wants neither turns the family off.</p>
 *
 * <p>{@code /condense} is open to everyone, because it creates nothing. It merges partial stacks
 * you already have, which is tidying rather than duplication, and it is the one on this list a
 * normal player actually wants.</p>
 */
public final class ItemToolCommands {

    // --- /repair ---

    public static LiteralArgumentBuilder<CommandSourceStack> repair(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.REPAIR))
                .executes(ctx -> repairHeld(ctx))
                .then(Commands.literal("all")
                        .requires(StandardsPermissions.require(StandardsPermissions.REPAIR_ALL))
                        .executes(ItemToolCommands::repairAll));
    }

    private static int repairHeld(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.item.empty_hand"));
            return 0;
        }
        if (!held.isDamageableItem()) {
            // Said plainly rather than silently succeeding: "repaired!" on a stack of cobblestone
            // reads as the command not working, which is worse than being told it does not apply.
            Feedback.chat(player, Lang.fmt("msg.item.not_repairable",
                    "item", held.getHoverName().getString()));
            return 0;
        }
        if (!held.isDamaged()) {
            Feedback.chat(player, Lang.fmt("msg.item.already_whole",
                    "item", held.getHoverName().getString()));
            return 0;
        }
        held.setDamageValue(0);
        Feedback.chat(player, Lang.fmt("msg.item.repaired",
                "item", held.getHoverName().getString()));
        return 1;
    }

    private static int repairAll(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var inventory = player.getInventory();
        int fixed = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isDamageableItem() && stack.isDamaged()) {
                stack.setDamageValue(0);
                fixed++;
            }
        }
        Feedback.chat(player, fixed == 0
                ? Lang.get("msg.item.nothing_to_repair")
                : Lang.fmt("msg.item.repaired_all", "count", String.valueOf(fixed)));
        return fixed;
    }

    // --- /more ---

    public static LiteralArgumentBuilder<CommandSourceStack> more() {
        return Commands.literal("more")
                .requires(StandardsPermissions.require(StandardsPermissions.MORE))
                .executes(ItemToolCommands::more);
    }

    private static int more(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.item.empty_hand"));
            return 0;
        }
        int max = held.getMaxStackSize();
        if (held.getCount() >= max) {
            Feedback.chat(player, Lang.fmt("msg.item.already_full",
                    "item", held.getHoverName().getString(), "count", String.valueOf(max)));
            return 0;
        }
        int added = max - held.getCount();
        held.setCount(max);
        Feedback.chat(player, Lang.fmt("msg.item.more",
                "count", String.valueOf(added), "item", held.getHoverName().getString()));
        return added;
    }

    // --- /condense ---

    public static LiteralArgumentBuilder<CommandSourceStack> condense() {
        return Commands.literal("condense")
                .requires(StandardsPermissions.require(StandardsPermissions.CONDENSE))
                .executes(ItemToolCommands::condense);
    }

    /**
     * Merge partial stacks of the same item.
     *
     * <p><b>Deliberately not the nine-ingots-into-a-block conversion</b> that EssentialsX does
     * under this name. That is a <em>crafting</em> operation — it changes one item into another,
     * needs a recipe table to know which, and quietly does work a crafting grid exists to do.
     * Standards already ships a portable one: {@code /craft}. Merging partial stacks creates
     * nothing and converts nothing, which is why this is the one item command everybody gets.</p>
     */
    private static int condense(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var inventory = player.getInventory();
        int moved = 0;
        int freed = 0;
        for (int a = 0; a < inventory.getContainerSize(); a++) {
            ItemStack into = inventory.getItem(a);
            if (into.isEmpty() || into.getCount() >= into.getMaxStackSize()) {
                continue;
            }
            for (int b = a + 1; b < inventory.getContainerSize(); b++) {
                ItemStack from = inventory.getItem(b);
                if (from.isEmpty() || !ItemStack.isSameItemSameComponents(into, from)) {
                    continue;
                }
                int room = into.getMaxStackSize() - into.getCount();
                int take = Math.min(room, from.getCount());
                if (take <= 0) {
                    continue;
                }
                into.grow(take);
                from.shrink(take);
                moved += take;
                if (from.isEmpty()) {
                    // Clear the slot rather than leaving a zero-count stack sitting in it.
                    // shrink() to nothing leaves the slot holding an empty stack, which the
                    // client can render as a ghost item until something else disturbs it.
                    inventory.setItem(b, ItemStack.EMPTY);
                    freed++;
                }
                if (into.getCount() >= into.getMaxStackSize()) {
                    break;
                }
            }
        }
        Feedback.chat(player, report(moved, freed));
        // Slots freed, because that is what the command is FOR. The item count is the honest
        // answer to a question nobody asked.
        return freed;
    }

    /**
     * Say what they actually gained.
     *
     * <p>The first version reported items <em>moved</em>, which is accurate and unhelpful: two
     * stacks of 32 merging reports "32", and eight stacks of 8 reports "56", because that is how
     * many crossed from one slot to another. A player reads those numbers as a count of what they
     * have, and neither is that.</p>
     *
     * <p>What they wanted to know is how much room they got back. Three outcomes, because
     * "0 slots freed" on a tidy-up that genuinely merged something reads as a command that did
     * nothing — two half stacks becoming one full one and one part one frees no slot at all.</p>
     */
    private static String report(int moved, int freed) {
        if (moved == 0) {
            return Lang.get("msg.item.nothing_to_condense");
        }
        if (freed == 0) {
            return Lang.fmt("msg.item.condensed_no_slots", "count", String.valueOf(moved));
        }
        return Lang.fmt("msg.item.condensed",
                "slots", String.valueOf(freed), "count", String.valueOf(moved));
    }

    // --- /itemname and /itemlore ---

    public static LiteralArgumentBuilder<CommandSourceStack> itemName() {
        return Commands.literal("itemname")
                .requires(StandardsPermissions.require(StandardsPermissions.ITEMNAME))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ItemToolCommands::itemName));
    }

    private static int itemName(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.item.empty_hand"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "name");
        if ("-".equals(raw.trim())) {
            held.remove(DataComponents.CUSTOM_NAME);
            Feedback.chat(player, Lang.get("msg.item.name_cleared"));
            return 1;
        }
        held.set(DataComponents.CUSTOM_NAME, Feedback.colored(raw));
        Feedback.chat(player, Lang.fmt("msg.item.named", "name", raw));
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> itemLore() {
        return Commands.literal("itemlore")
                .requires(StandardsPermissions.require(StandardsPermissions.ITEMLORE))
                .then(Commands.literal("clear").executes(ctx -> lore(ctx, null)))
                .then(Commands.literal("add")
                        .then(Commands.argument("line", StringArgumentType.greedyString())
                                .executes(ctx -> lore(ctx,
                                        StringArgumentType.getString(ctx, "line")))));
    }

    /**
     * Add a line of lore, or clear the lot.
     *
     * <p>Add-a-line rather than set-everything, because lore is a list and a command that took the
     * whole thing at once would need a separator — and whichever character were chosen would be
     * the one somebody wanted in their text.</p>
     */
    private static int lore(CommandContext<CommandSourceStack> ctx, String line)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.item.empty_hand"));
            return 0;
        }
        if (line == null) {
            held.remove(DataComponents.LORE);
            Feedback.chat(player, Lang.get("msg.item.lore_cleared"));
            return 1;
        }
        List<Component> lines = new ArrayList<>(
                held.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        if (lines.size() >= ItemLore.MAX_LINES) {
            Feedback.chat(player, Lang.fmt("msg.item.lore_full",
                    "max", String.valueOf(ItemLore.MAX_LINES)));
            return 0;
        }
        lines.add(Feedback.colored(line));
        held.set(DataComponents.LORE, new ItemLore(lines));
        Feedback.chat(player, Lang.fmt("msg.item.lore_added",
                "count", String.valueOf(lines.size())));
        return lines.size();
    }

    private ItemToolCommands() {}
}

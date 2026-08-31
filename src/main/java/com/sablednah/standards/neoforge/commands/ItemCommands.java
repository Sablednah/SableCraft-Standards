package com.sablednah.standards.neoforge.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /i} — give yourself an item.
 *
 * <pre>
 *   /i &lt;item&gt;            a full stack
 *   /i &lt;item&gt; &lt;count&gt;    exactly that many
 * </pre>
 *
 * <h2>Why this exists when vanilla has {@code /give}</h2>
 *
 * <p>The catalogue said <b>NO</b> to this, reasoning that vanilla covers it and "the short alias is
 * the only draw". That was the wrong conclusion from the right observation, and it was overturned
 * by the owner catching himself still typing {@code /i} on his own server.</p>
 *
 * <p><b>The short alias is the draw, and in this mod that is sufficient.</b> Decision 12 exists
 * because muscle memory is the product: {@code /give @s minecraft:stone 64} is four times the
 * typing and needs a selector for the commonest case there is — giving something to yourself. A
 * server-utility mod that makes people type the long form has misunderstood what it is for.</p>
 *
 * <h2>A bare {@code /i} gives a full stack</h2>
 *
 * <p>Not one item. Somebody typing {@code /i stone} while building wants a stack far more often
 * than a single block, and {@code /i stone 1} is right there when they do not. The count is capped
 * at what the item actually stacks to, so a tool or a shulker box gives one.</p>
 *
 * <p>Vanilla's {@code /give} is untouched — no merge, no override. This is a second, shorter door
 * into the same room, and unlike {@code /msg} there is nothing to take back from anybody.</p>
 */
public final class ItemCommands {

    /**
     * Needs the build context, which is why {@link com.sablednah.standards.neoforge.StandardsCommands}
     * now takes the whole event rather than just the dispatcher.
     *
     * <p>{@code ItemArgument} resolves ids against the registries as they are for <em>this</em>
     * server, so a modpack's items tab-complete beside vanilla's with nothing added here. A
     * hand-rolled string argument would have been simpler and would have known about vanilla
     * only.</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> give(String alias,
            CommandBuildContext build) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.ITEM))
                .then(Commands.argument("item", ItemArgument.item(build))
                        .executes(ctx -> give(ctx, -1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 6400))
                                .executes(ctx -> give(ctx,
                                        IntegerArgumentType.getInteger(ctx, "count")))));
    }

    /** @param count how many, or {@code -1} for a full stack of whatever it turns out to be */
    private static int give(CommandContext<CommandSourceStack> ctx, int count)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemInput input = ItemArgument.getItem(ctx, "item");

        // One stack to read the real maximum from, which is per-item and per-component: a shulker
        // box stacks to 1, an ordinary block to 64, and a datapack can change either. Asking the
        // stack beats a hardcoded 64 that is wrong for a good part of the inventory.
        // The boolean is 'allow oversized stacks', and false is right: we chunk by the real
        // maximum below, so nothing here should ever exceed it. 26.x dropped this parameter —
        // see CROSS-VERSION.md, it is the divergence this command carries.
        ItemStack probe = input.createItemStack(1, false);
        int stackSize = Math.max(1, probe.getMaxStackSize());
        int wanted = count < 0 ? stackSize : count;

        int given = 0;
        while (given < wanted) {
            int size = Math.min(stackSize, wanted - given);
            ItemStack stack = input.createItemStack(size, false);
            // Drop what will not fit rather than binning it. A command that reports giving 640
            // cobblestone and quietly discards half is worse than one that refuses outright.
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            given += size;
        }

        Feedback.reply(ctx.getSource(), Lang.fmt("msg.item.given",
                "count", String.valueOf(given),
                "item", probe.getHoverName().getString()), false);
        return given;
    }

    private ItemCommands() {}
}

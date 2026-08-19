package com.sablednah.standards.neoforge.commands;

import java.util.Collection;
import java.util.function.Consumer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

/**
 * Putting a player right again: {@code /heal}, {@code /feed}, {@code /rest}.
 *
 * <p>All three share one shape — do it to yourself, or to a selector of other people with a
 * second permission — so they are built from one helper rather than three near-identical classes.
 * That is the same instinct as {@link SwitchCommand}: the moment two commands differ only in which
 * method they call, the difference belongs in an argument.</p>
 */
public final class SelfCareCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build(
            String name, String messageKey, PermissionNode<Boolean> self,
            PermissionNode<Boolean> others, Consumer<ServerPlayer> action) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(self))
                .executes(ctx -> applySelf(ctx, messageKey, action))
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(StandardsPermissions.require(others))
                        .executes(ctx -> applyOthers(ctx, messageKey, action)));
    }

    private static int applySelf(CommandContext<CommandSourceStack> ctx, String messageKey,
            Consumer<ServerPlayer> action) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        action.accept(player);
        Feedback.chat(player, Lang.get(messageKey + ".self"));
        return 1;
    }

    private static int applyOthers(CommandContext<CommandSourceStack> ctx, String messageKey,
            Consumer<ServerPlayer> action) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        String by = ctx.getSource().getTextName();
        String lastName = "";
        for (ServerPlayer target : targets) {
            action.accept(target);
            lastName = target.getName().getString();
            if (ctx.getSource().getEntity() != target) {
                Feedback.chat(target, Lang.fmt(messageKey + ".notified", "by", by));
            }
        }
        if (targets.size() == 1) {
            final String name = lastName;
            Feedback.reply(ctx.getSource(), Lang.fmt(messageKey + ".other", "player", name), true);
        } else if (targets.size() > 1) {
            Feedback.reply(ctx.getSource(),
                    Lang.fmt(messageKey + ".many", "count", targets.size()), true);
        }
        return targets.size();
    }

    // --- the actions ---

    /**
     * Full health, and put the fire out.
     *
     * <p>Extinguishing is not scope creep: being healed while still burning means being healed and
     * then immediately hurt again, which reads as the command not having worked.</p>
     */
    public static void heal(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
    }

    /** Full hunger and full saturation — without saturation it drains again within the minute. */
    public static void feed(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
    }

    /** Reset the phantom clock, which is what "rested" actually means to the game. */
    public static void rest(ServerPlayer player) {
        player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
    }

    private SelfCareCommands() {}
}

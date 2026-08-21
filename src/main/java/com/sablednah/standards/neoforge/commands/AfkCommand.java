package com.sablednah.standards.neoforge.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Afk;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /afk [reason]} and its alias {@code /lurk}.
 *
 * <p>Not built on {@link SwitchCommand} despite looking like a switch, and the reason is worth
 * stating: an explicit {@code /afk off} is pointless, because <em>doing anything at all</em>
 * already brings you back — including typing that command. So the only sensible manual action is
 * "I am going away now", optionally with a note.</p>
 */
public final class AfkCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.AFK))
                .executes(ctx -> toggle(ctx, ""))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> toggle(ctx, Feedback.stripCodes(StringArgumentType.getString(ctx, "reason")))));
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, String reason)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        // Running the command is itself activity, so an already-away player typing /afk is coming
        // back — which is what they meant.
        Afk.setAway(player, !Afk.isAway(player), reason, false);
        return 1;
    }

    private AfkCommand() {}
}

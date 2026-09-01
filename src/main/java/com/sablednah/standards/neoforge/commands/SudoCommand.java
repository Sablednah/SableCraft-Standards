package com.sablednah.standards.neoforge.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.Standards;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /sudo <player> <command>} — run a command as somebody, <b>with their permissions</b>.
 *
 * <h2>Vanilla's {@code /execute as} does not do this, and the difference is the whole point</h2>
 *
 * <p>The catalogue said NO to {@code /sudo} on the grounds that {@code /execute as} covers most of
 * it. It does not cover the half that matters. Brigadier evaluates {@code requires()} at
 * <b>parse</b> time against whoever typed the line, and {@code execute} only swaps the source at
 * <em>execution</em> time — so {@code /execute as Steve run fly on} runs with <em>your</em>
 * permissions and succeeds whatever Steve's are.</p>
 *
 * <p>That is documented in {@code CLAUDE.md} as a useful mechanism, and it is: it is how another
 * mod invokes a Standards command on a player's behalf without granting them the node. But it
 * means <b>permission boundaries can only be tested by the player actually typing the command</b> —
 * which cost a two-client session to close the loop on the permission handler.</p>
 *
 * <p>This parses the command against the <em>target's</em> source, so their nodes decide. An admin
 * can check what a rank can really do without asking somebody to log in.</p>
 *
 * <h2>Consequences worth stating</h2>
 *
 * <ul>
 * <li><b>It can fail, and that is correct.</b> {@code /sudo Steve fly on} is refused when Steve
 *     lacks {@code standards.fly} — that refusal <em>is</em> the answer you asked for.</li>
 * <li><b>Every use is logged</b>, with who ran what as whom. An op making somebody else appear to
 *     act is exactly the thing that needs accounting for afterwards, and the person asking will
 *     not be the person who did it.</li>
 * <li><b>It will not sudo a sudo.</b> Cheap, and it stops the obvious loop.</li>
 * </ul>
 */
public final class SudoCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sudo")
                .requires(StandardsPermissions.require(StandardsPermissions.SUDO))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(SudoCommand::sudo)));
    }

    private static int sudo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String command = StringArgumentType.getString(ctx, "command").trim();
        // Tolerate a leading slash: half of everybody types one, and rejecting it teaches nothing.
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            Feedback.fail(ctx.getSource(), Lang.get("msg.sudo.empty"));
            return 0;
        }
        String head = command.split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
        if (head.equals("sudo")) {
            Feedback.fail(ctx.getSource(), Lang.get("msg.sudo.recursive"));
            return 0;
        }

        Standards.LOGGER.info("[sudo] {} ran '{}' as {}",
                ctx.getSource().getTextName(), command, target.getName().getString());
        String who = target.getName().getString();
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.sudo.running",
                "player", who, "command", command), true);

        var dispatcher = ctx.getSource().getServer().getCommands().getDispatcher();
        // THE LINE THE COMMAND EXISTS FOR. Parsing against the target's own source is what makes
        // their permissions apply — hand it ours and this would be /execute as with extra steps.
        var parsed = dispatcher.parse(command, target.createCommandSourceStack());

        if (!reachable(parsed)) {
            // It did not parse for them. Two very different reasons, and telling them apart is
            // the most useful thing this command does: re-parse as the CALLER, who we know can
            // see more of the tree. Parses for us and not for them means requires() said no —
            // which is the answer somebody testing a rank actually wanted, and is otherwise
            // reported as the thoroughly misleading "Unknown or incomplete command".
            boolean callerCanSeeIt = reachable(dispatcher.parse(command, ctx.getSource()));
            Feedback.fail(ctx.getSource(), callerCanSeeIt
                    ? Lang.fmt("msg.sudo.refused", "player", who, "command", command)
                    : Lang.fmt("msg.sudo.unknown", "command", command));
            return 0;
        }

        try {
            int result = dispatcher.execute(parsed);
            Feedback.reply(ctx.getSource(), Lang.fmt("msg.sudo.ok",
                    "player", who, "command", command), false);
            return result;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failed) {
            // The target has already been told by their own source — they saw the error as though
            // they had typed it, which is correct. This is the caller's copy, so they are not left
            // guessing whether anything happened at all.
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.sudo.failed",
                    "player", who, "command", command, "reason", failed.getRawMessage().getString()));
            return 0;
        }
    }

    /**
     * Whether a parse actually reached something runnable.
     *
     * <p>{@code getExceptions()} alone is not enough and neither is a non-null context — the same
     * trap {@code SelfTest} documents. A command hidden by {@code requires()} produces a parse that
     * simply stops early, so the reader still has text left to consume.</p>
     */
    private static boolean reachable(com.mojang.brigadier.ParseResults<CommandSourceStack> parsed) {
        return parsed.getExceptions().isEmpty()
                && !parsed.getReader().canRead()
                && parsed.getContext().getLastChild().getCommand() != null;
    }

    private SudoCommand() {}
}

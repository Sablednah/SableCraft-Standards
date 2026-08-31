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
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.sudo.running",
                "player", target.getName().getString(), "command", command), true);

        // THE LINE THE COMMAND EXISTS FOR. Parsing against the target's own source is what makes
        // their permissions apply — hand it ours and this would be /execute as with extra steps.
        ctx.getSource().getServer().getCommands()
                .performPrefixedCommand(target.createCommandSourceStack(), command);
        return 1;
    }

    private SudoCommand() {}
}

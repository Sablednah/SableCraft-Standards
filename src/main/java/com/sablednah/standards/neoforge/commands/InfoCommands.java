package com.sablednah.standards.neoforge.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /motd}, {@code /rules} and {@code /info} — whatever the owner wants to say.
 *
 * <h2>The text lives in {@code messages.yml}, and that is the whole design</h2>
 *
 * <p>Every other essentials package invents a second file format for this — {@code motd.txt},
 * {@code rules.txt} — with its own colour-code handling, its own reload command and its own
 * encoding bugs. Standards already has a file where an owner edits player-facing text, with
 * {@code &} colours, {@code {term.*}} vocabulary and a merge that survives upgrades. A second one
 * would be strictly worse and would need explaining.</p>
 *
 * <p>So each of these is a numbered run of message keys — {@code msg.motd.1}, {@code msg.motd.2},
 * … — printed until one is missing. Lines are added by adding keys, and a server that wants a
 * one-line MOTD deletes the rest.</p>
 *
 * <h2>Shown on join, once</h2>
 *
 * <p>The MOTD prints itself when somebody logs in, which is the only time most players will ever
 * see it. {@code /motd} is for reading it again.</p>
 */
public final class InfoCommands {

    /** How far the numbered run is followed before giving up. Generous; nobody writes 40 lines. */
    private static final int MAX_LINES = 40;

    public static LiteralArgumentBuilder<CommandSourceStack> build(String alias, String prefix) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.MOTD))
                .executes(ctx -> show(ctx, prefix));
    }

    private static int show(CommandContext<CommandSourceStack> ctx, String prefix) {
        String text = render(prefix);
        if (text.isEmpty()) {
            Feedback.reply(ctx.getSource(), Lang.get("msg.info.empty"), false);
            return 0;
        }
        Feedback.reply(ctx.getSource(), text, false);
        return 1;
    }

    /**
     * The numbered run as one block, stopping at the first gap.
     *
     * <p>Stopping at a gap rather than skipping it is deliberate: deleting {@code msg.rules.3}
     * from a list of five is how an owner shortens their rules, and silently printing 4 and 5
     * afterwards would renumber the whole thing under them.</p>
     *
     * <p>Public and pure so {@code SelfTest} can prove the run-and-stop behaviour without a
     * player, and so the join handler can share it.</p>
     */
    public static String render(String prefix) {
        StringBuilder sb = new StringBuilder();
        for (int line = 1; line <= MAX_LINES; line++) {
            String key = prefix + "." + line;
            if (!Lang.has(key)) {
                break;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(Lang.get(key));
        }
        return sb.toString();
    }

    private InfoCommands() {}
}

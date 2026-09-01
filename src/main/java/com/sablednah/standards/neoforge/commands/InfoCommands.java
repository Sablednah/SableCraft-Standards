package com.sablednah.standards.neoforge.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        String text = ctx.getSource().getEntity() instanceof ServerPlayer player
                ? render(prefix, player)
                : render(prefix, ctx.getSource().getServer());
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
        return render(prefix, null, null);
    }

    /** For a player: every placeholder is answerable. */
    public static String render(String prefix, ServerPlayer player) {
        return render(prefix, player, player.level().getServer());
    }

    /** For the console: the server-wide placeholders only. */
    public static String render(String prefix, MinecraftServer server) {
        return render(prefix, null, server);
    }

    /**
     * The numbered run, with placeholders filled.
     *
     * <p>Every supported placeholder is passed on every line whether the text uses it or not,
     * which is what lets {@link Lang#fmt} warn about one that is <em>not</em> supported. An owner
     * who writes <code>{rankk}</code> gets a line in the log naming the key, instead of a welcome
     * message that greets everybody with a pair of braces.</p>
     */
    private static String render(String prefix, ServerPlayer player, MinecraftServer server) {
        Object[] values = placeholders(player, server);
        StringBuilder sb = new StringBuilder();
        for (int line = 1; line <= MAX_LINES; line++) {
            String key = prefix + "." + line;
            if (!Lang.has(key)) {
                break;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(Lang.fmt(key, values));
        }
        return sb.toString();
    }

    /**
     * What an owner may write in {@code messages.yml}.
     *
     * <p>Deliberately a short list. Every one is either about the reader or about the server as a
     * whole, both of which a welcome message legitimately wants — and nothing here can fail, since
     * a missing answer becomes a dash rather than an exception on somebody's login.</p>
     */
    private static Object[] placeholders(ServerPlayer player, MinecraftServer server) {
        String none = Lang.get("msg.info.unknown");
        String name = player == null ? none : player.getName().getString();
        // The nickname where they have one, because that is what the rest of the server calls
        // them — a welcome that uses their real name when nobody else does reads oddly.
        String shown = player == null ? none
                : com.sablednah.standards.neoforge.ChatFormatter.displayName(player);
        String rank = none;
        String playtime = none;
        String world = none;
        if (player != null && server != null) {
            world = player.level().dimension().identifier().getPath();
            var data = com.sablednah.standards.neoforge.StandardsData.get(server);
            long minutes = data.playedMinutes(player.getUUID());
            playtime = minutes <= 0 ? Lang.get("msg.playtime.none")
                    : com.sablednah.standards.core.Duration.describe(minutes * 60L);
            if (com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler.isActive()) {
                var ranks = com.sablednah.standards.neoforge.permissions.PermissionStore.get(server)
                        .groupsOf(player.getUUID());
                if (!ranks.isEmpty()) {
                    rank = String.join(", ", ranks);
                }
            }
        }
        int online = server == null ? 0 : server.getPlayerList().getPlayerCount();
        int max = server == null ? 0 : server.getPlayerList().getMaxPlayers();
        return new Object[] {
            "player", shown,
            "name", name,
            "rank", rank,
            "playtime", playtime,
            "world", world,
            "online", String.valueOf(online),
            "max", String.valueOf(max),
        };
    }

    private InfoCommands() {}
}

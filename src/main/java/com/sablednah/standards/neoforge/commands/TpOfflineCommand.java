package com.sablednah.standards.neoforge.commands;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /tpoffline} — to where a player was standing when they logged out.
 *
 * <p>Op-only, and that is the whole of the design conversation. It is a genuinely useful staff
 * tool — following up a grief report starts with going to where the reported player was — and it
 * would be unpleasant in ordinary players' hands, since it turns logging off into a location
 * broadcast.</p>
 *
 * <p>The position is recorded on logout only, not continuously: writing save data every time
 * somebody walks would be an absurd price for a command used a few times a week.</p>
 */
public final class TpOfflineCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.TP_OFFLINE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(TpOfflineCommand::suggestSeenPlayers)
                        .executes(TpOfflineCommand::go));
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = me.level().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        StandardsData data = StandardsData.get(server);

        Optional<UUID> target = data.byName(server, name);
        if (target.isEmpty()) {
            Feedback.chat(me, Lang.fmt("msg.common.player_not_found", "name", name));
            return 0;
        }
        // If they are online, this is just /tp — and going to a stale logout position while the
        // player stands somewhere else is a confusing way to fail.
        ServerPlayer online = server.getPlayerList().getPlayer(target.get());
        Optional<Waypoint> destination = online != null
                ? Optional.of(Waypoint.of(online))
                : data.lastPosition(target.get());
        if (destination.isEmpty()) {
            Feedback.chat(me, Lang.fmt("msg.tp.never_seen", "player", name));
            return 0;
        }

        Teleports.Attempt attempt = Teleports.request(me, destination.get(), true,
                Lang.fmt("msg.tp.to_offline",
                        "player", name, "place", destination.get().describe()));
        if (!MoveCommands.report(me, attempt)) {
            return 0;
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestSeenPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                StandardsData.get(ctx.getSource().getServer()).knownNames(), builder);
    }

    private TpOfflineCommand() {}
}

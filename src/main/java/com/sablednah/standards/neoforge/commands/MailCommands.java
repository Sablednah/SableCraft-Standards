package com.sablednah.standards.neoforge.commands;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.Mailbox;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /mail send|read|clear} — messages for people who are not here.
 *
 * <p>The counterpart to {@code /msg}: same instinct, different problem. {@code /msg} fails when
 * the other person is offline, and telling someone "they are not online" is not a solution, it is
 * a restatement of the problem.</p>
 *
 * <p>New post is announced on login and <b>not</b> marked read by that announcement — being told
 * you have mail is not the same as having read it, and conflating them loses letters.</p>
 */
public final class MailCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mail")
                .requires(StandardsPermissions.require(StandardsPermissions.MAIL))
                .executes(MailCommands::read)
                .then(Commands.literal("read").executes(MailCommands::read))
                .then(Commands.literal("clear").executes(MailCommands::clear))
                .then(Commands.literal("send")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(MailCommands::suggestKnownPlayers)
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(MailCommands::send))));
    }

    private static int send(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer from = ctx.getSource().getPlayerOrException();
        MinecraftServer server = from.level().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        Optional<UUID> to = StandardsData.get(server).byName(server, name);
        if (to.isEmpty()) {
            Feedback.chat(from, Lang.fmt("msg.common.player_not_found", "name", name));
            return 0;
        }
        String text = Feedback.stripCodes(StringArgumentType.getString(ctx, "message"));
        if (!Mailbox.get(server).send(to.get(), from.getUUID(), from.getName().getString(), text)) {
            Feedback.chat(from, Lang.fmt("msg.mail.full", "player", name));
            return 0;
        }
        Feedback.chat(from, Lang.fmt("msg.mail.sent", "player", name));

        // If they happen to be online, tell them now rather than at their next login.
        ServerPlayer online = server.getPlayerList().getPlayer(to.get());
        if (online != null) {
            Feedback.chat(online, Lang.fmt("msg.mail.arrived",
                    "player", from.getName().getString()));
        }
        return 1;
    }

    private static int read(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        MinecraftServer server = me.level().getServer();
        List<Mailbox.Letter> letters = Mailbox.get(server).read(me.getUUID());
        if (letters.isEmpty()) {
            Feedback.chat(me, Lang.get("msg.mail.empty"));
            return 0;
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(Lang.fmt("msg.mail.header", "count", letters.size()));
        for (Mailbox.Letter letter : letters) {
            sb.append("\n").append(Lang.fmt(letter.read() ? "msg.mail.line" : "msg.mail.line_new",
                    "player", letter.fromName(),
                    "ago", Duration.describe(Math.max(1, (now - letter.sentAt()) / 1000)),
                    "message", letter.text()));
        }
        Feedback.chat(me, sb.toString());
        Mailbox.get(server).markAllRead(me.getUUID());
        return letters.size();
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer me = ctx.getSource().getPlayerOrException();
        int thrown = Mailbox.get(me.level().getServer()).clear(me.getUUID());
        Feedback.chat(me, thrown == 0
                ? Lang.get("msg.mail.empty")
                : Lang.fmt("msg.mail.cleared", "count", thrown));
        return thrown;
    }

    /** Tell them on login, without marking anything read. */
    public static void announceOnLogin(ServerPlayer player) {
        long unread = Mailbox.get(player.level().getServer()).unread(player.getUUID());
        if (unread > 0) {
            Feedback.chat(player, Lang.fmt("msg.mail.waiting", "count", unread));
        }
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        java.util.List<String> names =
                new java.util.ArrayList<>(java.util.List.of(server.getPlayerNames()));
        StandardsData.get(server).knownNames().forEach(n -> {
            if (!names.contains(n)) names.add(n);
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private MailCommands() {}
}

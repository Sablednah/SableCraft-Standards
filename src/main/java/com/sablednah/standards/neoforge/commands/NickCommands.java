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
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /nick} and {@code /realname}.
 *
 * <pre>
 *   /nick &lt;name&gt;              call yourself something
 *   /nick -                   back to your real name
 *   /nick &lt;player&gt; &lt;name&gt;     a moderator's undo, and their override
 *   /realname &lt;nick&gt;          who is that, really
 * </pre>
 *
 * <h2>Chat only, and that is the design</h2>
 *
 * <p>A nickname replaces the name <b>in chat</b>. The tab list and the nameplate over the player's
 * head keep the real one. That is not a limitation to be lifted later — it is what keeps a
 * nickname a flourish rather than a disguise, and it means the answer to "who is that" is always
 * one glance at tab away even for a player who has never heard of {@code /realname}.</p>
 *
 * <p>{@code /msg} is likewise real-names-only, because it merges onto vanilla's node and takes
 * vanilla's {@code EntityArgument} with it. That falls out of decision 10 rather than being
 * chosen, but it is the right answer anyway and is worth keeping on purpose.</p>
 *
 * <h2>The impersonation rule</h2>
 *
 * <p>A nickname may not be another player's real name, nor another player's nickname — see
 * {@link StandardsData#impersonates}. Checked against the <b>name cache</b> rather than the online
 * list, because impersonating somebody who is asleep is the version that works: they are not there
 * to object, and their friends are.</p>
 *
 * <p>Both halves are enforced in the store rather than here, so an admin setting a nickname for
 * somebody else, or a mod doing it later, cannot route around the check.</p>
 *
 * <h2>Why the name argument is greedy</h2>
 *
 * <p><b>Brigadier's {@code word()} cannot read an ampersand</b>, so {@code /nick &cBob} was
 * unparseable — while {@code standards.nick.color} sat there gating colour codes nobody could
 * type. Exactly the trap the permission wildcards hit with {@code *}: {@code word()} accepts
 * letters, digits and {@code _.+-} and stops dead at anything else, and the error names nothing
 * ("Expected whitespace to end one argument") so it reads as the typist's mistake.</p>
 *
 * <p>Greedy for that reason alone — a nickname is still one word, and a spaced one is refused
 * here with a message that says so.</p>
 */
public final class NickCommands {

    /** Clears a nickname. The same convention as {@code /rank group <name> tag -}. */
    private static final String CLEAR = "-";

    public static LiteralArgumentBuilder<CommandSourceStack> nick() {
        return Commands.literal("nick")
                .requires(StandardsPermissions.require(StandardsPermissions.NICK))
                // A bare /nick SHOWS, it does not clear. Clearing on an argumentless command is
                // destructive by omission: half-typing something should never throw away what you
                // set. The button does the clearing in one click, which is what somebody typing a
                // bare /nick actually wanted anyway.
                .executes(NickCommands::show)
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> setOwn(ctx, StringArgumentType.getString(ctx, "name"))))
                // The two-argument form is a separate literal branch rather than an optional
                // second argument, so a player called 'off' cannot be caught by it. Same trade
                // SwitchCommand documents for '/fly on'.
                .then(Commands.literal("player")
                        .requires(StandardsPermissions.require(StandardsPermissions.NICK_OTHERS))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(NickCommands::suggestKnownPlayers)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(NickCommands::setOther))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> realName(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.REALNAME))
                .then(Commands.argument("nick", StringArgumentType.word())
                        .suggests(NickCommands::suggestNicks)
                        .executes(NickCommands::realNameOf));
    }

    /**
     * {@code /nick} on its own — what you currently are, and a button to stop being it.
     *
     * <p>Added after testing: it was falling through to brigadier's "Unknown or incomplete
     * command", which is the least useful thing a command can say to somebody who typed its name
     * correctly.</p>
     */
    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<String> nick = StandardsData.get(ctx.getSource().getServer())
                .nick(player.getUUID());
        if (nick.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.nick.none",
                    "name", player.getName().getString()));
            return 0;
        }
        Feedback.chatWithButtons(player,
                Lang.fmt("msg.nick.current", "name", nick.get()),
                Feedback.button(Lang.get("msg.nick.clear_button"), "/nick " + CLEAR,
                        Lang.get("msg.nick.clear_tooltip")));
        return 1;
    }

    private static int setOwn(CommandContext<CommandSourceStack> ctx, String raw)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return apply(ctx, player.getUUID(), player.getName().getString(), raw, true);
    }

    private static int setOther(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String target = StringArgumentType.getString(ctx, "player");
        Optional<UUID> who = StandardsData.get(server).byName(server, target);
        if (who.isEmpty()) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.common.player_not_found", "name", target));
            return 0;
        }
        return apply(ctx, who.get(), target, StringArgumentType.getString(ctx, "name"), false);
    }

    /**
     * The single road in, so every rule is checked exactly once however the nickname arrived.
     *
     * @param own whether the setter is naming themselves, which decides only whose colour-code
     *            permission is consulted and which message comes back
     */
    private static int apply(CommandContext<CommandSourceStack> ctx, UUID target,
            String targetName, String raw, boolean own) {
        MinecraftServer server = ctx.getSource().getServer();
        StandardsData data = StandardsData.get(server);

        if (CLEAR.equals(raw)) {
            data.setNick(target, null);
            Feedback.reply(ctx.getSource(), own
                    ? Lang.fmt("msg.nick.cleared", "name", targetName)
                    : Lang.fmt("msg.nick.cleared_other", "player", targetName), !own);
            return 1;
        }

        // Colour codes come out unless the setter may use them. Stripped rather than refused: a
        // player who pasted a code they did not know about wanted the word, not an error.
        boolean mayColour = ctx.getSource().getEntity() instanceof ServerPlayer setter
                ? StandardsPermissions.has(setter, StandardsPermissions.NICK_COLOR)
                : true; // console, a command block, a datapack — already trusted
        String nick = mayColour ? raw : Feedback.stripCodes(raw);

        String plain = Feedback.stripCodes(nick).trim();

        // YOUR OWN NAME IS NOT A NICKNAME, it is the absence of one — so asking for it clears.
        //
        // Reported in testing, where it was refused as impersonating somebody. The cause is the
        // offline-UUID trap: online-mode=false derives the id from "OfflinePlayer:<name>"
        // verbatim, so 'Sablednah' and 'sablednah' are genuinely different players and the name
        // cache holds both. The guard excludes the chooser by UUID, which is correct, and the
        // OTHER casing is a different UUID — so your own name looked like somebody else's.
        //
        // Handling it here rather than teaching the guard about casing is deliberate. Treating the
        // two spellings as one player would be wrong: on an offline server they really can be two
        // people. But nobody has ever meant "impersonate myself", and clearing is what they asked
        // for in the only words that occurred to them.
        if (plain.equalsIgnoreCase(targetName)) {
            data.setNick(target, null);
            Feedback.reply(ctx.getSource(), own
                    ? Lang.fmt("msg.nick.cleared", "name", targetName)
                    : Lang.fmt("msg.nick.cleared_other", "player", targetName), !own);
            return 1;
        }

        // One word. The argument is greedy only so an ampersand can be typed at all — see the
        // note on the tree — not so that nicknames may contain spaces. A name with a space in it
        // cannot be looked up by /realname, cannot be tab-completed, and reads as two people.
        if (plain.contains(" ")) {
            Feedback.fail(ctx.getSource(), Lang.get("msg.nick.one_word"));
            return 0;
        }
        if (plain.isEmpty()) {
            // Reachable with a colour-code-only nickname such as '&k', which measures four
            // characters and displays none.
            Feedback.fail(ctx.getSource(), Lang.get("msg.nick.empty"));
            return 0;
        }
        int max = StandardsConfig.NICK_MAX_LENGTH.get();
        if (plain.length() > max) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.nick.too_long",
                    "max", String.valueOf(max), "length", String.valueOf(plain.length())));
            return 0;
        }

        Optional<UUID> clash = data.impersonates(target, plain);
        if (clash.isPresent()) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.nick.taken", "name", plain));
            return 0;
        }

        data.setNick(target, nick);
        Feedback.reply(ctx.getSource(), own
                ? Lang.fmt("msg.nick.set", "name", nick)
                : Lang.fmt("msg.nick.set_other", "player", targetName, "name", nick), !own);

        // Tell them, when somebody else did it. A name changing under you with no explanation is
        // alarming, and the moderator case is exactly when it happens.
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (!own && online != null) {
            Feedback.chat(online, Lang.fmt("msg.nick.set_by",
                    "name", nick, "by", ctx.getSource().getTextName()));
        }
        return 1;
    }

    /**
     * {@code /realname} — the command that makes the whole feature defensible.
     *
     * <p>Answers for offline players, because that is when it is asked. Also answers for a real
     * name typed straight in, so somebody who guesses wrong about whether a name is a nickname
     * still gets a useful reply rather than "no such thing".</p>
     */
    private static int realNameOf(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        StandardsData data = StandardsData.get(server);
        String wanted = StringArgumentType.getString(ctx, "nick");

        Optional<UUID> who = data.byNick(wanted);
        if (who.isEmpty()) {
            // Not a nickname. It may simply be somebody's actual name, which is a perfectly
            // sensible thing to have typed.
            Optional<UUID> real = data.byName(server, wanted);
            if (real.isPresent()) {
                Feedback.reply(ctx.getSource(), Lang.fmt("msg.nick.is_real", "name", wanted), false);
                return 1;
            }
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.nick.unknown", "name", wanted));
            return 0;
        }
        String realName = data.nameOf(who.get()).orElse(who.get().toString());
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.nick.realname",
                "nick", Feedback.stripCodes(data.nick(who.get()).orElse(wanted)).trim(),
                "name", realName), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestNicks(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                StandardsData.get(ctx.getSource().getServer()).knownNicks(), builder);
    }

    private static CompletableFuture<Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        List<String> names = new java.util.ArrayList<>(List.of(server.getPlayerNames()));
        StandardsData.get(server).knownNames().forEach(n -> {
            if (!names.contains(n)) {
                names.add(n);
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private NickCommands() {}
}

package com.sablednah.standards.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.economy.Economy;
import com.sablednah.standards.neoforge.commands.EconomyCommands;
import com.sablednah.standards.neoforge.commands.GameMasterCommands;
import com.sablednah.standards.neoforge.commands.AfkCommand;
import com.sablednah.standards.neoforge.commands.KitCommands;
import com.sablednah.standards.neoforge.commands.ServerHealthCommand;
import com.sablednah.standards.neoforge.commands.MailCommands;
import com.sablednah.standards.neoforge.commands.TpOfflineCommand;
import com.sablednah.standards.neoforge.commands.MessageCommands;
import com.sablednah.standards.neoforge.commands.ModerationCommands;
import com.sablednah.standards.neoforge.commands.SelfCareCommands;
import com.sablednah.standards.neoforge.commands.SpawnCommands;
import com.sablednah.standards.neoforge.commands.SpeedCommand;
import com.sablednah.standards.neoforge.commands.StationCommands;
import com.sablednah.standards.neoforge.commands.GroupCommands;
import com.sablednah.standards.neoforge.commands.HomeCommands;
import com.sablednah.standards.neoforge.commands.MoveCommands;
import com.sablednah.standards.neoforge.commands.PermissionCommands;
import com.sablednah.standards.neoforge.commands.SwitchCommand;
import com.sablednah.standards.neoforge.commands.TpaCommands;
import com.sablednah.standards.neoforge.commands.WarpCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where every command is registered.
 *
 * <p>Two rules hold across the whole surface:</p>
 *
 * <ul>
 * <li><b>A command that is switched off is not registered.</b> Not registered-and-refusing —
 *     absent. A greyed-out entry in a client's tab-complete for something the server will never
 *     run is a lie the player has to discover by trying it, and a modpack that already ships a
 *     homes mod wants ours out of the way, not arguing with it.</li>
 * <li><b>Every command exists at its plain name.</b> {@code /home}, not {@code /standards home}.
 *     Muscle memory is the entire product here; a utility mod that makes people type a prefix has
 *     misunderstood what it is for. The {@code /standards} root is for administering the mod
 *     itself, and nothing else lives under it.</li>
 * </ul>
 */
public final class StandardsCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        int before = dispatcher.getRoot().getChildren().size();

        // --- switches: the tri-state pattern, see SwitchCommand ---
        if (StandardsConfig.ENABLE_FLY.get()) {
            dispatcher.register(SwitchCommand.build("fly", "msg.toggle.fly",
                    StandardsPermissions.FLY, StandardsPermissions.FLY_OTHERS,
                    player -> StandardsAttachments.of(player).fly(),
                    (player, value) -> {
                        StandardsAttachments.of(player).setFly(value);
                        StandardsEvents.applySwitches(player);
                    }));
        }
        if (StandardsConfig.ENABLE_GOD.get()) {
            dispatcher.register(SwitchCommand.build("god", "msg.toggle.god",
                    StandardsPermissions.GOD, StandardsPermissions.GOD_OTHERS,
                    player -> StandardsAttachments.of(player).god(),
                    (player, value) -> {
                        StandardsAttachments.of(player).setGod(value);
                        StandardsEvents.applySwitches(player);
                    }));
        }

        if (StandardsConfig.ENABLE_VANISH.get()) {
            // A switch like the others, so it inherits on/off/toggle and selector targeting —
            // '/vanish @a off' at the end of an event is worth having.
            dispatcher.register(SwitchCommand.build("vanish", "msg.toggle.vanish",
                    StandardsPermissions.VANISH, StandardsPermissions.VANISH_OTHERS,
                    Vanish::isVanished, Vanish::set));
            dispatcher.register(SwitchCommand.build("v", "msg.toggle.vanish",
                    StandardsPermissions.VANISH, StandardsPermissions.VANISH_OTHERS,
                    Vanish::isVanished, Vanish::set));
        }
        if (StandardsConfig.ENABLE_SMITE.get()) {
            dispatcher.register(GameMasterCommands.smite());
        }

        // --- getting about ---
        if (StandardsConfig.ENABLE_TOP.get()) {
            dispatcher.register(MoveCommands.top("top"));
            // Real tree, not a redirect — see the note on /j below.
            dispatcher.register(MoveCommands.top("up"));
        }
        if (StandardsConfig.ENABLE_JUMP.get()) {
            dispatcher.register(MoveCommands.jump("jump"));
            // Aliases are built as their own trees, NOT with dispatcher.redirect(). A redirect
            // node carries no command of its own, so brigadier has nothing to run when the input
            // ends there — a bare '/j' would fail with "Unknown command" while '/j something'
            // worked, which is a maddening bug to be told about second-hand. Registering the
            // builder twice costs a few nodes and always behaves.
            dispatcher.register(MoveCommands.jump("j"));
        }
        if (StandardsConfig.ENABLE_BACK.get()) {
            dispatcher.register(MoveCommands.back());
        }

        if (StandardsConfig.ENABLE_BOTTOM.get()) {
            dispatcher.register(MoveCommands.bottom("bottom"));
            dispatcher.register(MoveCommands.bottom("down"));
        }
        if (StandardsConfig.ENABLE_SPAWN.get()) {
            dispatcher.register(SpawnCommands.spawn());
            dispatcher.register(SpawnCommands.setSpawn());
            dispatcher.register(SpawnCommands.playerSpawn());
        }
        if (StandardsConfig.ENABLE_SELFCARE.get()) {
            dispatcher.register(SelfCareCommands.build("heal", "msg.care.heal",
                    StandardsPermissions.HEAL, StandardsPermissions.HEAL_OTHERS,
                    SelfCareCommands::heal));
            dispatcher.register(SelfCareCommands.build("feed", "msg.care.feed",
                    StandardsPermissions.FEED, StandardsPermissions.FEED_OTHERS,
                    SelfCareCommands::feed));
            dispatcher.register(SelfCareCommands.build("eat", "msg.care.feed",
                    StandardsPermissions.FEED, StandardsPermissions.FEED_OTHERS,
                    SelfCareCommands::feed));
            dispatcher.register(SelfCareCommands.build("rest", "msg.care.rest",
                    StandardsPermissions.REST, StandardsPermissions.REST_OTHERS,
                    SelfCareCommands::rest));
        }
        if (StandardsConfig.ENABLE_SPEED.get()) {
            dispatcher.register(SpeedCommand.build());
        }

        // --- homes ---
        if (StandardsConfig.ENABLE_HOMES.get()) {
            dispatcher.register(HomeCommands.home());
            dispatcher.register(HomeCommands.homes());
            dispatcher.register(HomeCommands.setHome());
            dispatcher.register(HomeCommands.delHome());
        }

        // --- teleport requests ---
        if (StandardsConfig.ENABLE_TPA.get()) {
            dispatcher.register(TpaCommands.tpa("tpa"));
            dispatcher.register(TpaCommands.tpa("call"));          // the EssentialsX alias
            dispatcher.register(TpaCommands.tpaHere());
            dispatcher.register(TpaCommands.accept("tpaccept"));
            dispatcher.register(TpaCommands.accept("tpyes"));
            dispatcher.register(TpaCommands.deny("tpdeny"));
            dispatcher.register(TpaCommands.deny("tpno"));
            dispatcher.register(TpaCommands.cancel());
            dispatcher.register(TpaCommands.list());

            // /tptoggle is a switch like any other, so it gets on/off/toggle for free — and the
            // switch is named for what it controls ("Accepting teleport requests"), not for the
            // command. That matters: '/tptoggle on' is genuinely ambiguous when the thing being
            // toggled is a refusal, and reading it as "requests: on" is the only sense a player
            // guesses right first time.
            //
            // A GERUND, not a plural noun. The switch message is "{what} is now {state}", so
            // "Incoming teleport requests" produced "Incoming teleport requests IS now off".
            // Every other switch happens to be a singular noun — Flight, God mode, Vanish — so
            // nothing caught it until a screenshot did.
            dispatcher.register(SwitchCommand.build("tptoggle", "msg.tpa.toggle_name",
                    StandardsPermissions.TPTOGGLE, StandardsPermissions.ADMIN,
                    player -> !StandardsAttachments.of(player).refusingTeleports(),
                    (player, accepting) ->
                            StandardsAttachments.of(player).setRefusingTeleports(!accepting)));
        }

        // --- warps ---
        if (StandardsConfig.ENABLE_WARPS.get()) {
            dispatcher.register(WarpCommands.warp());
            dispatcher.register(WarpCommands.warps());
            dispatcher.register(WarpCommands.setWarp());
            dispatcher.register(WarpCommands.delWarp());
        }

        if (StandardsConfig.ENABLE_AFK.get()) {
            dispatcher.register(AfkCommand.build("afk"));
            dispatcher.register(AfkCommand.build("lurk"));
        }

        if (StandardsConfig.ENABLE_SERVER_HEALTH.get()) {
            for (String alias : new String[] {"gc", "tps", "lag", "mem"}) {
                dispatcher.register(ServerHealthCommand.build(alias));
            }
        }
        if (StandardsConfig.ENABLE_KITS.get()) {
            dispatcher.register(KitCommands.kit());
            dispatcher.register(KitCommands.kits());
            dispatcher.register(KitCommands.setKit());
            dispatcher.register(KitCommands.delKit());
            dispatcher.register(KitCommands.showKit());
        }
        if (StandardsConfig.ENABLE_MAIL.get()) {
            dispatcher.register(MailCommands.build());
        }
        if (StandardsConfig.ENABLE_TP_OFFLINE.get()) {
            dispatcher.register(TpOfflineCommand.build("tpoffline"));
            dispatcher.register(TpOfflineCommand.build("otp"));
        }

        // --- groups ---
        if (StandardsConfig.ENABLE_GROUPS.get()) {
            dispatcher.register(GroupCommands.group());
            // These two DO sit at their plain names — see the note on GroupCommands.groupHome.
            dispatcher.register(GroupCommands.groupHome("ghome"));
            dispatcher.register(GroupCommands.groupHomes("ghomes"));
        }

        // --- talking ---
        if (StandardsConfig.ENABLE_MESSAGING.get()) {
            // 'msg' merges onto vanilla's node and replaces its command; 'tell' and 'w' are
            // vanilla redirects to that same node, so they follow automatically and must NOT be
            // registered here — doing so merges children into a redirect node, which ignores them.
            // The rest are names vanilla does not use, so they are ours outright.
            for (String alias : new String[] {"msg", "whisper", "pm", "m"}) {
                dispatcher.register(MessageCommands.msg(alias));
            }
            // Same merge rule: replaces vanilla's /me command so mutes, ignores and vanish apply.
            dispatcher.register(MessageCommands.emote());
            dispatcher.register(MessageCommands.reply("r"));
            dispatcher.register(MessageCommands.reply("reply"));
            dispatcher.register(MessageCommands.ignore());
            dispatcher.register(SwitchCommand.build("msgtoggle", "msg.pm.toggle_name",
                    StandardsPermissions.MSG, StandardsPermissions.ADMIN,
                    player -> !StandardsAttachments.of(player).refusingMessages(),
                    (player, accepting) ->
                            StandardsAttachments.of(player).setRefusingMessages(!accepting)));
            dispatcher.register(SwitchCommand.build("socialspy", "msg.pm.spy_toggle_name",
                    StandardsPermissions.SOCIALSPY, StandardsPermissions.ADMIN,
                    player -> StandardsAttachments.of(player).socialSpy(),
                    (player, on) -> StandardsAttachments.of(player).setSocialSpy(on)));
        }

        // --- moderation: exactly three, on purpose ---
        if (StandardsConfig.ENABLE_MODERATION.get()) {
            dispatcher.register(ModerationCommands.tempban());
            dispatcher.register(ModerationCommands.mute());
            dispatcher.register(ModerationCommands.unmute());
            dispatcher.register(ModerationCommands.invsee());
        }

        // --- portable workstations, denied to everyone until granted ---
        if (StandardsConfig.ENABLE_STATIONS.get()) {
            StationCommands.registerAll(dispatcher);
        }

        // --- money ---
        if (StandardsConfig.ENABLE_ECONOMY.get()) {
            dispatcher.register(EconomyCommands.balance("balance"));
            dispatcher.register(EconomyCommands.balanceTop());
            dispatcher.register(EconomyCommands.pay());
            dispatcher.register(EconomyCommands.eco());
            // Aliases everyone already has in their fingers. Own trees, same reasoning as /j.
            dispatcher.register(EconomyCommands.balance("bal"));
            dispatcher.register(EconomyCommands.balance("money"));
        }

        // --- the built-in permission handler ---
        // Always registered, never always visible: the tree's own requires() hides it unless our
        // handler is the active one, and that cannot be decided here. Commands are built while
        // the level loads; the handler is chosen later, at handleServerStarting. A check at
        // registration time would therefore see 'not us' on every server and hide it forever.
        dispatcher.register(PermissionCommands.build("perm"));
        // And at /rank, because LuckPerms claims /perm as an alias of /luckperms. Ours still
        // wins every subcommand — brigadier merges the literals — but a bare /perm runs whichever
        // mod registered its executes() last, and that is not a coin toss worth shipping. Own
        // tree rather than a redirect, same reasoning as /j and /bal.
        dispatcher.register(PermissionCommands.build("rank"));

        dispatcher.register(root());
        Standards.LOGGER.info("Registered {} Standards commands",
                dispatcher.getRoot().getChildren().size() - before);
    }

    /** {@code /standards} — administering the mod, not using it. */
    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("standards")
                .requires(StandardsPermissions.require(StandardsPermissions.ADMIN))
                .then(Commands.literal("reload").executes(StandardsCommands::reload))
                .then(Commands.literal("economy").executes(StandardsCommands::economyInfo))
                .then(Commands.literal("permissions")
                        .executes(StandardsCommands::permissionInfo))
                .then(Commands.literal("testchat")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(StandardsCommands::testChat)));
    }

    /**
     * Put a real chat message through the real pipeline, as yourself.
     *
     * <p><b>Why this exists.</b> {@code SelfTest} runs on {@code ServerStartedEvent} with nobody
     * connected, so the one thing it structurally cannot do is push player input through a path —
     * and "a path nothing has ever called" is the shape of most of the bugs this mod has produced.
     * Everything from the mute gate to the router seam only truly runs when somebody types, and
     * RCON cannot make somebody type.</p>
     *
     * <p>So this posts a genuine {@link net.neoforged.neoforge.event.ServerChatEvent} on the real
     * bus. AFK clearing, the mute gate, router offers, decoration and delivery all run exactly as
     * they would for a typed line; only vanilla's packet decode is skipped. Borrowed from the
     * LegendQuest session, which built the same harness to test the router from RCON.</p>
     *
     * <p><b>Yourself only, deliberately.</b> The obvious generalisation — a player argument — is a
     * tool for making anybody appear to say anything, and impersonation is the one thing the colour
     * code stripping was just hardened against. Ops can already do a great deal; putting words in
     * a named player's mouth should not be one of them.</p>
     *
     * @return 1 if the message went through, 0 if something stopped it — so
     *         {@code execute store result score} can assert on it from RCON
     */
    private static int testChat(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String message = StringArgumentType.getString(ctx, "message");

        var event = new net.neoforged.neoforge.event.ServerChatEvent(
                player, message, Feedback.colored(message));
        boolean cancelled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled();

        Feedback.reply(ctx.getSource(), Lang.fmt("msg.admin.testchat",
                "result", Lang.get(cancelled ? "msg.admin.testchat_stopped"
                        : "msg.admin.testchat_through")), false);
        return cancelled ? 0 : 1;
    }

    /**
     * Re-read {@code messages.yml}.
     *
     * <p>Text only — and deliberately so. The config's {@code enable*} switches decide what is
     * registered with the command dispatcher at server start, and a reload cannot un-register a
     * command from clients that have already been sent the command tree. Saying that plainly
     * beats a reload that appears to work and silently does not.</p>
     */
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        Lang.load();
        Feedback.reply(ctx.getSource(), Lang.get("msg.admin.reloaded"), true);
        return 1;
    }

    /**
     * Which handler is answering permission questions — the first thing to check when a gated
     * command has quietly disappeared for everybody.
     *
     * <p>That symptom has one obvious cause and one that is not obvious at all. A failed
     * {@code requires()} removes a command from the tree entirely, so a permissions manager whose
     * storage did not come up answers false to everything and the whole mod looks broken, with
     * nothing on screen to say why. Asking here beats reading the boot log for an error somebody
     * else's mod printed twenty minutes ago.</p>
     */
    private static int permissionInfo(CommandContext<CommandSourceStack> ctx) {
        var active = net.neoforged.neoforge.server.permission.PermissionAPI
                .getActivePermissionHandler();
        if (com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler.isActive()) {
            int groups = com.sablednah.standards.neoforge.permissions.PermissionStore
                    .get(ctx.getSource().getServer()).allGroups().size();
            Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.handler_ours",
                    "groups", String.valueOf(groups)), false);
            return 1;
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.perm.handler_other",
                "name", String.valueOf(active)), false);
        return 0;
    }

    /** Which economy is actually holding the money — the first question when one misbehaves. */
    private static int economyInfo(CommandContext<CommandSourceStack> ctx) {
        var active = Economy.provider();
        if (active.isEmpty()) {
            Feedback.reply(ctx.getSource(), Lang.get("msg.eco.provider_none"), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder(Lang.fmt("msg.eco.provider",
                "name", active.get().name(), "priority", active.get().priority()));
        Economy.all().stream().skip(1).forEach(other ->
                sb.append("\n &8- ").append(other.name())
                        .append(" (").append(other.priority()).append(", standing by)"));
        Feedback.reply(ctx.getSource(), sb.toString(), false);
        return Economy.all().size();
    }

    private StandardsCommands() {}
}

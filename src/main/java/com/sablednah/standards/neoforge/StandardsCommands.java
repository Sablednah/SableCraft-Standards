package com.sablednah.standards.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import com.sablednah.standards.neoforge.commands.HomeCommands;
import com.sablednah.standards.neoforge.commands.MoveCommands;
import com.sablednah.standards.neoforge.commands.SwitchCommand;
import com.sablednah.standards.neoforge.commands.TpaCommands;
import com.sablednah.standards.neoforge.commands.WarpCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

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
            dispatcher.register(MoveCommands.top());
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
            dispatcher.register(MoveCommands.bottom());
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
            // switch is named for what it controls ("Incoming teleport requests"), not for the
            // command. That matters: '/tptoggle on' is genuinely ambiguous when the thing being
            // toggled is a refusal, and reading it as "requests: on" is the only sense a player
            // guesses right first time.
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

        dispatcher.register(root());
        Standards.LOGGER.info("Registered {} Standards commands",
                dispatcher.getRoot().getChildren().size() - before);
    }

    /** {@code /standards} — administering the mod, not using it. */
    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("standards")
                .requires(StandardsPermissions.require(StandardsPermissions.ADMIN))
                .then(Commands.literal("reload").executes(StandardsCommands::reload))
                .then(Commands.literal("economy").executes(StandardsCommands::economyInfo));
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

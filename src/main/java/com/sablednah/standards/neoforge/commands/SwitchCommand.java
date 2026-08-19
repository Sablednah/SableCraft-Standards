package com.sablednah.standards.neoforge.commands;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.core.Toggle;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

/**
 * The shape every switch in Standards takes — and the reason the mod exists.
 *
 * <pre>
 *   /fly                      toggle your own
 *   /fly on | off | toggle    set your own, explicitly
 *   /fly &lt;players&gt;            toggle someone else's
 *   /fly &lt;players&gt; on|off     set someone else's, explicitly
 * </pre>
 *
 * <p>The explicit state is the whole point. A human typing {@code /fly} wants a toggle and gets
 * one; a command block, a datapack, a shop or a LegendQuest skill wants {@code /fly Steve on} now
 * and {@code /fly Steve off} in twenty seconds, and a toggle in that position is a coin flip that
 * grounds the player mid-air half the time. See {@link Toggle}.</p>
 *
 * <p>The target is {@link EntityArgument#players()} rather than a single player, so
 * {@code /god @a off} works — the selector syntax is free and an event host will use it within
 * the hour.</p>
 *
 * <p>One ambiguity worth knowing about: brigadier tries literals before arguments, so a player
 * genuinely named "on" cannot be targeted as {@code /fly on}. They can still be targeted as
 * {@code /fly @p[name=on]}. This is the right trade — the literals are typed thousands of times
 * a day and that player does not exist.</p>
 */
public final class SwitchCommand {

    /** How a switch is read and written. Supplied per switch from {@code StandardsCommands}. */
    public interface Switch {
        boolean get(ServerPlayer player);

        void set(ServerPlayer player, boolean value);
    }

    /**
     * Build the command tree for one switch.
     *
     * @param name    the command literal, e.g. {@code fly}
     * @param whatKey the Lang key naming it in messages, e.g. {@code msg.toggle.fly}
     * @param self    the node letting a player switch their own
     * @param others  the node letting them switch someone else's
     */
    public static LiteralArgumentBuilder<CommandSourceStack> build(
            String name, String whatKey,
            PermissionNode<Boolean> self, PermissionNode<Boolean> others,
            Predicate<ServerPlayer> getter, BiConsumer<ServerPlayer, Boolean> setter) {

        Switch sw = new Switch() {
            @Override
            public boolean get(ServerPlayer player) {
                return getter.test(player);
            }

            @Override
            public void set(ServerPlayer player, boolean value) {
                setter.accept(player, value);
            }
        };

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(StandardsPermissions.require(self))
                .executes(ctx -> applyToSelf(ctx, sw, whatKey, Toggle.TOGGLE));

        for (Toggle state : Toggle.values()) {
            root.then(Commands.literal(state.key())
                    .executes(ctx -> applyToSelf(ctx, sw, whatKey, state)));
        }

        var target = Commands.argument("players", EntityArgument.players())
                .requires(StandardsPermissions.require(others))
                .executes(ctx -> applyToOthers(ctx, sw, whatKey, Toggle.TOGGLE));
        for (Toggle state : Toggle.values()) {
            target.then(Commands.literal(state.key())
                    .executes(ctx -> applyToOthers(ctx, sw, whatKey, state)));
        }
        root.then(target);

        return root;
    }

    private static int applyToSelf(CommandContext<CommandSourceStack> ctx, Switch sw,
            String whatKey, Toggle state) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean before = sw.get(player);
        boolean after = state.resolve(before);
        String what = Lang.get(whatKey);

        if (before == after) {
            // Saying "already on" rather than silently succeeding matters most for the explicit
            // form: a script that runs /fly on twice should be able to tell the difference.
            Feedback.chat(player, Lang.fmt("msg.toggle.already", "what", what, "state", stateWord(after)));
            return 0;
        }
        sw.set(player, after);
        Feedback.chat(player, Lang.fmt("msg.toggle.self", "what", what, "state", stateWord(after)));
        return 1;
    }

    /**
     * @return the number of players actually changed — vanilla's convention, and what a command
     *         block's comparator output reads, so {@code /fly @a on} is measurable
     */
    private static int applyToOthers(CommandContext<CommandSourceStack> ctx, Switch sw,
            String whatKey, Toggle state) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        String what = Lang.get(whatKey);
        String by = ctx.getSource().getTextName();
        int changed = 0;
        String lastName = "";
        boolean lastState = false;

        for (ServerPlayer target : targets) {
            boolean before = sw.get(target);
            boolean after = state.resolve(before);
            if (before == after) continue;
            sw.set(target, after);
            changed++;
            lastName = target.getName().getString();
            lastState = after;
            // Tell the target, unless the target is the person who ran it — they get the summary.
            if (ctx.getSource().getEntity() != target) {
                Feedback.chat(target, Lang.fmt("msg.toggle.notified",
                        "what", what, "state", stateWord(after), "by", by));
            }
        }

        if (changed == 1) {
            final String name = lastName;
            final boolean value = lastState;
            Feedback.reply(ctx.getSource(), Lang.fmt("msg.toggle.other",
                    "what", what, "player", name, "state", stateWord(value)), true);
        } else if (changed > 1) {
            // Not stateWord(lastState): under TOGGLE the players can land on different states, so
            // naming one of them would be a confident lie about the rest.
            final int count = changed;
            Feedback.reply(ctx.getSource(), Lang.fmt("msg.toggle.many",
                    "what", what, "count", count,
                    "state", state == Toggle.TOGGLE
                            ? Lang.get("msg.toggle.flipped") : stateWord(lastState)), true);
        }
        return changed;
    }

    private static String stateWord(boolean on) {
        return Lang.get(on ? "msg.toggle.on" : "msg.toggle.off");
    }

    private SwitchCommand() {}
}

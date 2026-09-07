package com.sablednah.standards.neoforge.commands;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.sablednah.standards.api.actions.Action;
import com.sablednah.standards.api.actions.Actions;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

/**
 * {@code /actions} — the button bar, for a client that has no button bar.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>Chat click events are plain vanilla components, so a row of {@code [Fly] [God] [Home]} in chat
 * is a working set of buttons on an <b>unmodified client</b>. That makes the action seam serve
 * everybody rather than only the people who installed something — which is decision 2 applied to
 * the newest feature rather than exempted from it.</p>
 *
 * <p>Asked for by LegendQuest's StoryTeller, whose rule is that only the server and the storyteller
 * need the mod and everyone else needs nothing. The honest answer to "can your buttons work for a
 * vanilla client" turned out to be yes, and it took this rather than an apology.</p>
 *
 * <p>It is worse than the drawn bar — it does not sit beside your inventory, and it does not update
 * itself. It is also the only version some people will ever have, and a row you can click beats a
 * list of commands you have to remember.</p>
 */
public final class ActionCommands {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> actions() {
        return Commands.literal("actions")
                .requires(StandardsPermissions.require(StandardsPermissions.ACTIONS))
                .executes(ActionCommands::show);
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<Component> buttons = new ArrayList<>();
        int shown = 0;
        for (Action action : Actions.all()) {
            if (!available(action, player)) {
                continue;
            }
            boolean on = action.active() != null && test(action.active(), player);
            String hint = action.hint() == null ? "" : String.valueOf(action.hint().apply(player));
            // The state is carried by the LABEL rather than by a separate column, because a chat
            // row has no second column. Lit and dim, the same distinction the drawn bar makes.
            String label = Lang.fmt(on ? "msg.actions.button_on" : "msg.actions.button_off",
                    "name", Lang.get(action.tooltipKey()),
                    "hint", hint == null || hint.isEmpty() ? "" : " " + hint);
            buttons.add(Feedback.button(label, "/" + action.command(),
                    Lang.get(action.tooltipKey())));
            shown++;
        }
        if (shown == 0) {
            Feedback.chat(player, Lang.get("msg.actions.none"));
            return 0;
        }
        Feedback.chatWithButtons(player, Lang.get("msg.actions.header"),
                buttons.toArray(new Component[0]));
        return shown;
    }

    /** A foreign predicate that throws costs its own mod a button, and nothing else. */
    private static boolean available(Action action, ServerPlayer player) {
        return test(action.available(), player);
    }

    private static boolean test(java.util.function.Predicate<ServerPlayer> predicate,
            ServerPlayer player) {
        try {
            return predicate.test(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ActionCommands() {}
}

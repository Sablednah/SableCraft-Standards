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
                .executes(ActionCommands::show)
                .then(Commands.literal("all").executes(ActionCommands::all));
    }

    /**
     * {@code /actions all} — the whole registry, including what you are <b>not</b> being offered.
     *
     * <h2>Why a second listing</h2>
     *
     * <p>Because a withheld button and a broken button look exactly the same, and the seam is built
     * so that they must: {@code available} is evaluated server-side and only the ids that pass are
     * ever sent, which is deliberate — a button offered for something the player cannot do is the
     * "granted command renders red" bug in reverse. The cost is that a correctly hidden button is
     * silent, and silence is what a bug sounds like.</p>
     *
     * <p>It was paid in full once already. Four of Factions' five buttons and all five of
     * StoryTeller's were absent from the bar, correctly — the player was in no faction and was not
     * a storyteller — and the reports were "the faction panel button just draws the chat map" and
     * "still no storytellers". Both were the seam working. Establishing that took disassembling two
     * shipped jars to read their predicates and reading the world's save file to prove the faction
     * store was empty. This command is that afternoon, in one line.</p>
     *
     * <p>It reports <em>whether</em>, not <em>why</em>: the predicate belongs to another mod and
     * only that mod knows its own reason. Naming the mod is enough to ask the right person.</p>
     */
    private static int all(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<Action> registry = Actions.all();
        if (registry.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.actions.all_none"));
            return 0;
        }
        Feedback.chat(player, Lang.get("msg.actions.all_header"));
        int offered = 0;
        for (Action action : registry) {
            boolean can = available(action, player);
            if (can) {
                offered++;
            }
            Feedback.chat(player, Lang.fmt("msg.actions.all_row",
                    "id", action.id(),
                    "priority", action.priority(),
                    "state", Lang.get(can ? "msg.actions.all_offered" : "msg.actions.all_withheld"),
                    // Only meaningful for an action you are actually being offered: the state, the
                    // hint and the children are all evaluated for you, and a withheld action's are
                    // answers to a question nobody asked.
                    "extra", can ? extras(action, player) : ""));
        }
        return offered;
    }

    /** The parts of an offered action that only exist for this player, if any of them do. */
    private static String extras(Action action, ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        if (action.isCategory()) {
            out.append(" &8category");
        }
        if (action.active() != null && test(action.active(), player)) {
            out.append(" &aon");
        }
        String hint = action.hint() == null ? null : safeHint(action, player);
        if (hint != null && !hint.isEmpty()) {
            out.append(" &7\"").append(hint).append('"');
        }
        int kids = action.children() == null ? 0 : safeChildren(action, player);
        if (kids > 0) {
            out.append(" &7+").append(kids);
        }
        return out.toString();
    }

    /** A foreign hint that throws costs its own mod a hint, and nothing else. */
    private static String safeHint(Action action, ServerPlayer player) {
        try {
            return String.valueOf(action.hint().apply(player));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Likewise for children — this listing must never be the thing that breaks. */
    private static int safeChildren(Action action, ServerPlayer player) {
        try {
            var kids = action.children().apply(player);
            return kids == null ? 0 : kids.size();
        } catch (RuntimeException e) {
            return 0;
        }
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

package com.sablednah.standards.neoforge;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.sablednah.standards.api.actions.Action;
import com.sablednah.standards.api.actions.Actions;
import com.sablednah.standards.network.CapabilitiesPayload;

/**
 * What to tell a modded client it may do.
 *
 * <p>Reads the {@link Actions} registry rather than a list of its own, so a mod that registers an
 * action gets the capability half for free. A second list here would drift from the registry, and
 * the symptom would be a button that draws for something the server does not offer — the exact bug
 * the payload exists to prevent, arriving by another door.</p>
 *
 * <p>Every entry's own {@code available} predicate decides. Standards' own actions ask the same
 * config switch and the same permission node the command does, in that order: decision 7 says a
 * command that is off in config is <em>not registered</em>, so no permission can make it exist and
 * asking permission first would offer a button for something nobody can run.</p>
 */
public final class Capabilities {

    /** Work out what this player may do, and hand it over — if they are listening at all. */
    public static void send(ServerPlayer player) {
        Set<String> actions = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        Map<String, String> hints = new LinkedHashMap<>();
        Map<String, java.util.List<CapabilitiesPayload.Child>> children = new LinkedHashMap<>();
        for (Action action : Actions.all()) {
            if (!safelyAvailable(action, player)) {
                continue;
            }
            actions.add(action.id());
            if (action.active() != null && safely(action, player, action.active(), "state")) {
                active.add(action.id());
            }
            if (action.children() != null) {
                var kids = safeChildren(action, player);
                if (!kids.isEmpty()) {
                    children.put(action.id(), kids);
                }
            }
            if (action.hint() != null) {
                String hint = safeHint(action, player);
                if (hint != null && !hint.isEmpty()) {
                    hints.put(action.id(), hint);
                }
            }
        }
        // sendIfAble, always. optional() makes the handshake tolerant; it does not make this send
        // droppable, and a bare sendToPlayer here would kick every vanilla player who joined.
        Net.sendIfAble(player, new CapabilitiesPayload(
                Set.copyOf(actions), Set.copyOf(active), Map.copyOf(hints),
                Map.copyOf(children)));
    }

    /**
     * A foreign predicate that throws must not take the whole bar down with it.
     *
     * <p>Same reasoning as the economy facade wrapping a foreign provider: this runs on join, and
     * an exception here would be thrown from the login handler — which is exactly the place this
     * mod has already learned not to throw from. One mod's broken action costs it its own button
     * and nothing else.</p>
     */
    private static boolean safelyAvailable(Action action, ServerPlayer player) {
        return safely(action, player, action.available(), "availability");
    }

    private static boolean safely(Action action, ServerPlayer player,
            java.util.function.Predicate<ServerPlayer> test, String what) {
        try {
            return test.test(player);
        } catch (RuntimeException e) {
            com.sablednah.standards.Standards.LOGGER.error(
                    "Standards: action '{}' threw deciding {}; treating it as false",
                    action.id(), what, e);
            return false;
        }
    }

    private static java.util.List<CapabilitiesPayload.Child> safeChildren(
            Action action, ServerPlayer player) {
        try {
            var kids = action.children().apply(player);
            return kids == null ? java.util.List.of() : kids.stream()
                    .map(c -> new CapabilitiesPayload.Child(c.label(), c.command()))
                    .toList();
        } catch (RuntimeException e) {
            com.sablednah.standards.Standards.LOGGER.error(
                    "Standards: action '{}' threw listing its children; sending none",
                    action.id(), e);
            return java.util.List.of();
        }
    }

    private static String safeHint(Action action, ServerPlayer player) {
        try {
            return action.hint().apply(player);
        } catch (RuntimeException e) {
            com.sablednah.standards.Standards.LOGGER.error(
                    "Standards: action '{}' threw building its hint; sending none", action.id(), e);
            return null;
        }
    }

    /**
     * Resend to everybody. Called where the command tree is resent, and for the same reason.
     *
     * <p>{@code PermissionCommands.refresh} already resends the command tree on every permission
     * edit, because the client's copy goes stale and a granted command renders red. The capability
     * set goes stale on the same events, so it rides the same trigger rather than inventing a
     * second one that could be forgotten.</p>
     */
    public static void sendToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private Capabilities() {}
}

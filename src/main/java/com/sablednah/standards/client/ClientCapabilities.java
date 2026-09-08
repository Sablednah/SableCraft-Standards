package com.sablednah.standards.client;

import java.util.Map;
import java.util.Set;

import com.sablednah.standards.network.CapabilitiesPayload;

/**
 * What the server last told this client it may do.
 *
 * <p>Empty until a {@link CapabilitiesPayload} arrives, and empty is the <b>correct</b> resting
 * state rather than a failure: a client with this mod may well be talking to a server without it,
 * or to one where the player has been granted nothing. Both draw no bar, which is right.</p>
 *
 * <p>Cleared on disconnect. A set left over from the last server would draw buttons for commands
 * this one has never heard of — and the player would click one and be told it does not exist,
 * which is the "granted command renders red" confusion arriving from the other direction.</p>
 */
public final class ClientCapabilities {

    private static volatile Set<String> actions = Set.of();
    private static volatile Set<String> active = Set.of();
    private static volatile Map<String, String> hints = Map.of();
    private static volatile Map<String, java.util.List<CapabilitiesPayload.Child>> children =
            Map.of();

    public static void accept(CapabilitiesPayload payload) {
        actions = payload.actions();
        active = payload.active();
        hints = payload.hints();
        children = payload.children();
    }

    /** On disconnect. See the class note: a stale set is worse than no set. */
    public static void clear() {
        actions = Set.of();
        active = Set.of();
        hints = Map.of();
        children = Map.of();
    }

    public static boolean has(String id) {
        return actions.contains(id);
    }

    /** Whether this action is on right now — flying, hidden, possessing. */
    public static boolean isActive(String id) {
        return active.contains(id);
    }

    public static String hint(String id) {
        return hints.getOrDefault(id, "");
    }

    /** What right-clicking this action offers. Empty means nothing underneath. */
    public static java.util.List<CapabilitiesPayload.Child> children(String id) {
        return children.getOrDefault(id, java.util.List.of());
    }

    public static Set<String> all() {
        return actions;
    }

    /** Is there anything at all to draw? Lets the bar cost nothing on a server without Standards. */
    public static boolean any() {
        return !actions.isEmpty();
    }

    private ClientCapabilities() {}
}

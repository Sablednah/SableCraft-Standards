package com.sablednah.standards.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Forget what the last server said.
 *
 * <p>A capability set left over from a previous connection would draw buttons for commands this
 * server has never heard of, and the player would click one and be told it does not exist. That is
 * the "granted command renders red" confusion arriving from the other direction, and the fix is the
 * same shape: never let the client hold an answer it was not just given.</p>
 */
public final class ClientLifecycle {

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCapabilities.clear();
    }

    /**
     * And on the way in, before the server has said anything.
     *
     * <p>Belt and braces with the logout clear: a disconnect that never fires {@code LoggingOut} —
     * a crash, a kicked connection — would otherwise carry the old set into the next server.</p>
     */
    @SubscribeEvent
    static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientCapabilities.clear();
    }

    private ClientLifecycle() {}
}

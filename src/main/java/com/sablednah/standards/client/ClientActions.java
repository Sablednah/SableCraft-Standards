package com.sablednah.standards.client;

import net.minecraft.client.Minecraft;

import com.sablednah.standards.Standards;
import com.sablednah.standards.api.actions.Action;
import com.sablednah.standards.api.actions.Actions;

/**
 * Fire an action from the client — the one path both a button and a keybind take.
 *
 * <p>Two triggers, one route, deliberately. If a keybind sent its command directly it would skip
 * whatever check the button does, and the two would drift the first time that check changed — a
 * keybind that works for something the button greys out is a bug nobody thinks to look for.</p>
 *
 * <p>Public because other mods' key handlers call it. StoryTeller registers its own
 * {@code KeyMapping}s the ordinary way — a {@code KeyMapping} must exist at client startup, before
 * anything knows which server it is talking to, so the seam cannot conjure one — and then calls
 * this, getting the availability check and the refusal for free.</p>
 */
public final class ClientActions {

    /**
     * Run the action with this id, if the server said we may.
     *
     * @return false if there is no such action, or the server has not offered it to this player
     */
    public static boolean run(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return false;
        }
        Action action = Actions.find(id).orElse(null);
        if (action == null || !ClientCapabilities.has(id)) {
            // Silent. A keybind pressed on a server that does not offer the action should do
            // nothing rather than complain — the player may not even know the key is bound, and a
            // message every time they brushed it would be worse than the missing feature.
            return false;
        }
        // A registered handler wins, but only as a nicer surface for the same answer: the command
        // below is what a vanilla client sends and it must give the same information. Checked
        // before the screen registry because it is the general case — a panel the button toggles
        // cannot be expressed as "make a screen and show it".
        var handler = Actions.handler(action.id());
        if (handler.isPresent()) {
            // Guarded because the handler belongs to another mod: one that throws should cost its
            // own mod a button, not take the click path down with it. Falls through to the command
            // rather than swallowing the click, so the player still gets the chat answer.
            try {
                handler.get().run();
                return true;
            } catch (RuntimeException | LinkageError e) {
                Standards.LOGGER.warn("Standards: client handler for '{}' failed ({}); "
                        + "falling back to its command", action.id(), e.toString());
            }
        }
        // A registered screen next, same contract. If the mod that owns it is absent, or older
        // than its own screen, this falls through and everybody still gets the chat version.
        var screen = Actions.screen(action.id());
        if (screen.isPresent()) {
            Object made = screen.get().get();
            if (made instanceof net.minecraft.client.gui.screens.Screen s) {
                mc.setScreen(s);
                return true;
            }
        }
        // Sent as if typed. The server path is then identical to typing it, which is what makes
        // permissions, cooldowns, warmups and config switches all apply without a second code path.
        mc.getConnection().sendCommand(action.command());
        return true;
    }

    /**
     * Send a command the server offered us — a child of an action, not an action itself.
     *
     * <p>No capability check, and that is not a hole: the command came from the server in the first
     * place, in the capability payload, in answer to "what may this player do". Re-checking a list
     * we were handed would be checking our own arithmetic.</p>
     */
    public static void runCommand(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && command != null && !command.isBlank()) {
            mc.getConnection().sendCommand(command);
        }
    }

    /** Whether an action would fire — for greying out a key's own hint, or a button. */
    public static boolean available(String id) {
        return ClientCapabilities.has(id) && Actions.find(id).isPresent();
    }

    private ClientActions() {}
}

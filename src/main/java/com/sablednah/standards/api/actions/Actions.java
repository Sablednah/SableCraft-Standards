package com.sablednah.standards.api.actions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * The meeting point for client actions. Standards owns it and knows nothing about what is on it.
 *
 * <p>The same shape as {@code api/chat} and {@code api/groups}: Factions is a separate mod and a
 * separate release, StoryTeller is in another repository again, and none of them should need an
 * edit to Standards to put a button on the bar.</p>
 *
 * <p><b>Additive</b>, like chat decoration and unlike the economy — several mods may contribute
 * actions without contradicting each other. Contrast the economy, where exactly one provider wins
 * outright because a balance is a single fact.</p>
 *
 * <h2>Register on both sides</h2>
 *
 * <p>Call this in common setup, not client setup. The server needs the {@code available} predicate
 * to work out what to offer; the client needs the icon and the command to draw and fire it. One
 * registration, read differently at each end — which also means the two cannot disagree about what
 * an action <em>is</em>.</p>
 *
 * <p>Registering costs nothing on a dedicated server beyond the record itself: the icon is an
 * {@link net.minecraft.resources.Identifier} and is never resolved there.</p>
 */
public final class Actions {

    private static final Logger LOG = LogUtils.getLogger();

    private static final List<Action> REGISTERED = new ArrayList<>();

    /**
     * Offer an action.
     *
     * <p>Sorted on insert by <b>priority, highest first</b> — closeness to the anchor, the rule the
     * chat decorators already use, because a second ordering rule is a second thing to get
     * backwards. Ties keep registration order, which at least makes the outcome depend on mod load
     * order rather than on nothing.</p>
     */
    public static synchronized void register(Action action) {
        if (find(action.id()).isPresent()) {
            // Refused rather than replaced. Two mods claiming one id is a mistake somewhere, and
            // silently letting the later one win makes it a mistake nobody can see.
            LOG.warn("Standards: action '{}' is already registered; ignoring the second one",
                    action.id());
            return;
        }
        REGISTERED.add(action);
        REGISTERED.sort(Comparator.comparingInt(Action::priority).reversed());
        LOG.info("Standards: client action '{}' registered at priority {} ({} total)",
                action.id(), action.priority(), REGISTERED.size());
    }

    /** Every action, nearest the anchor first. */
    public static synchronized List<Action> all() {
        return List.copyOf(REGISTERED);
    }

    public static synchronized Optional<Action> find(String id) {
        return REGISTERED.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    /**
     * Client screens an action can open, by id. Registered from client setup only.
     *
     * <p>A {@link java.util.function.Supplier} of {@code Object} rather than of {@code Screen},
     * because this class is loaded on a dedicated server and must not name a rendering type. The
     * client casts; nothing else ever looks.</p>
     */
    private static final java.util.Map<String, java.util.function.Supplier<Object>> SCREENS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Offer a screen an action may open <b>on a modded client</b>.
     *
     * <p>The action still carries a command, and that command must still work — the screen is the
     * nicer surface for the same answer, never a second capability. A client without the mod, or
     * without this screen registered, sends the command and gets the chat version.</p>
     */
    public static void registerScreen(String id, java.util.function.Supplier<Object> factory) {
        SCREENS.put(id, factory);
    }

    /** The screen for this action, if one was registered. */
    public static java.util.Optional<java.util.function.Supplier<Object>> screen(String id) {
        return java.util.Optional.ofNullable(SCREENS.get(id));
    }

    /**
     * Client handlers an action can run instead of sending its command, by id.
     *
     * <p>{@link #registerScreen} covers exactly one shape — <em>make a screen and show it</em> —
     * and the first real consumer wanted a different one. Factions' panel is an inline pane on the
     * inventory screen that the button <b>toggles</b>, the way LegendQuest's character sheet
     * behaves, so there is no screen to hand back and pressing the button a second time has to put
     * it away. A supplier cannot express that.</p>
     */
    private static final java.util.Map<String, Runnable> HANDLERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Offer something for an action to do <b>on a modded client</b>, instead of sending its command.
     *
     * <p>Same contract as {@link #registerScreen}, and it matters more here because a handler can
     * do anything: the action still carries a command, that command must still work, and what the
     * handler shows may only be what the command would have said. A nicer surface for the same
     * answer, never a second capability — otherwise a vanilla client has lost something, and
     * decision 2 is the whole reason this seam runs commands rather than sending payloads.</p>
     *
     * <p>Checked <b>before</b> the screen registry, so a mod that registers both gets the handler.
     * That ordering is deliberate: the handler is the general case and the screen is sugar, so the
     * more specific registration is the one that loses.</p>
     */
    public static void registerHandler(String id, Runnable handler) {
        HANDLERS.put(id, handler);
    }

    /** The client handler for this action, if one was registered. */
    public static java.util.Optional<Runnable> handler(String id) {
        return java.util.Optional.ofNullable(HANDLERS.get(id));
    }

    private Actions() {}
}

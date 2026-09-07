package com.sablednah.standards.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import com.sablednah.standards.Standards;

/**
 * Keys for Standards' own switches, and the worked example other mods copy.
 *
 * <h2>Why each mod registers its own</h2>
 *
 * <p>A {@link KeyMapping} must exist at client startup, in {@link RegisterKeyMappingsEvent}, before
 * anything knows which server it is talking to or what that player may do. So an action registered
 * at runtime cannot conjure a key for itself, and the tempting design — <em>register an action, get
 * a bindable key free</em> — is simply not available.</p>
 *
 * <p>The alternative, a pool of {@code "Standards action 1…8"} keys assigned in a screen, fills the
 * controls list with placeholders that mean nothing until bound and leaves a modpack unable to name
 * what it bound. So: each mod registers its own the ordinary way, and calls
 * {@link ClientActions#run} — which is where the availability check lives, so a key and a button
 * cannot disagree about whether an action is on offer.</p>
 *
 * <h2>Unbound by default</h2>
 *
 * <p>All of them. A mod claiming keys on install is how conflicts start, and none of these is worth
 * a key to most players — the ones that are worth a key belong to mods whose users installed them
 * precisely for that.</p>
 */
public final class StandardsKeys {

    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(Standards.MODID, "main"));

    private static final List<Bound> BOUND = new ArrayList<>();

    private record Bound(KeyMapping key, String actionId) {}

    static void register(RegisterKeyMappingsEvent event) {
        add(event, "key.standards.fly", "fly");
        add(event, "key.standards.god", "god");
        add(event, "key.standards.vanish", "vanish");
        add(event, "key.standards.home", "home");
        add(event, "key.standards.back", "back");
    }

    private static void add(RegisterKeyMappingsEvent event, String name, String actionId) {
        // UNKNOWN = unbound. See the class note: a mod claiming keys on install starts conflicts.
        KeyMapping key = new KeyMapping(name, InputConstants.UNKNOWN.getValue(), CATEGORY);
        event.register(key);
        BOUND.add(new Bound(key, actionId));
    }

    /**
     * Called once a tick.
     *
     * <p>{@code consumeClick} rather than {@code isDown}: a held key would otherwise send the
     * command sixty times a second, and the first thing anybody would notice is the server rate
     * limiting them off it.</p>
     */
    public static void onClientTick() {
        for (Bound bound : BOUND) {
            while (bound.key().consumeClick()) {
                ClientActions.run(bound.actionId());
            }
        }
    }

    private StandardsKeys() {}
}

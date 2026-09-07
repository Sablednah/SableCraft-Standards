package com.sablednah.standards.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The optional client channel.
 *
 * <p>No longer empty, and the comment that used to say so is worth replacing rather than deleting:
 * it said nothing here may ever become load-bearing, and that is still true. What arrived is one
 * clientbound payload saying which buttons to draw — a convenience, and a vanilla client that never
 * receives it loses nothing but the drawing.</p>
 *
 * <p>Formerly empty on purpose. Standards is server-authoritative — every command works for an
 * unmodified vanilla client — so nothing here may ever become load-bearing. The channel exists so
 * the conveniences a modded client could have (a balance readout, a clickable {@code /tpa}
 * prompt, a warmup bar) have somewhere to live when they are added, and so the
 * {@code optional()} registration is in place from the start rather than being retrofitted
 * later.</p>
 *
 * <p>When payloads do arrive: every clientbound send goes through
 * {@link com.sablednah.standards.neoforge.Net#sendIfAble}. {@code optional()} makes the handshake
 * tolerant, not the sends — see that class for what happens otherwise.</p>
 */
public final class StandardsNetwork {

    /** Bump when a payload's wire format changes incompatibly. */
    public static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(), which is the whole reason a vanilla client can still join. It makes the
        // HANDSHAKE tolerant of a client that has never heard of us — it does NOT make sends
        // droppable, which is what Net.sendIfAble is for. Both halves or neither.
        //
        // ⚠ CHAINED, and it has to be. optional() returns a CLONE of the registrar rather than
        // mutating it, so the obvious three-line version registers a REQUIRED channel:
        //
        //     var r = event.registrar(VERSION);
        //     r.optional();              // clone made, and thrown away
        //     r.playToClient(...);       // registered on the ORIGINAL — not optional
        //
        // That compiles, reads correctly, and kicks every vanilla player at login with "Invalid
        // player data". Same failure as forgetting sendIfAble, arriving through a different door.
        // Never split this expression.
        event.registrar(VERSION).optional()
                // Clientbound only, and there is no serverbound payload by design: a button runs
                // the command a player would have typed, so every action already has a path to the
                // server that permissions, cooldowns and config switches all understand. Adding a
                // serverbound payload would mean a second path that has to be taught the same
                // rules and would drift from them.
                .playToClient(CapabilitiesPayload.TYPE, CapabilitiesPayload.CODEC,
                        (payload, context) -> {
                            // Handled on the client. Registered here with a no-op so a dedicated
                            // server never touches a client class: referencing one from this
                            // method would load it during registration, on a jar that has none.
                        });
    }

    private StandardsNetwork() {}
}

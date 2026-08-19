package com.sablednah.standards.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The optional client channel.
 *
 * <p>Empty on purpose, for now. Standards is server-authoritative — every command works for an
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
        // No payloads yet — the registrar is claimed so the channel and its version are in place
        // before the first one is added.
        event.registrar(VERSION).optional();
    }

    private StandardsNetwork() {}
}

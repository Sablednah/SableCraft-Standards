package com.sablednah.standards.network;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.sablednah.standards.Standards;

/**
 * What this player may do — the only thing the client needs the server to tell it.
 *
 * <h2>What it is for, and what it is not</h2>
 *
 * <p>It says <b>what to draw</b>, never what is allowed. The server re-checks on the command, as it
 * would for anything typed, so a stale or forged set gets you a button that fails — which is
 * precisely what typing the command would have got you. Nothing here is a security boundary and
 * nothing should ever be added to it that is.</p>
 *
 * <p>It exists because the alternative is worse than having no buttons at all. A <em>Vanish</em>
 * button offered to somebody without {@code standards.vanish} is the "granted command renders red"
 * bug in reverse: they click, it fails, and they report the mod broken. Better to draw nothing.</p>
 *
 * <h2>Hints</h2>
 *
 * <p>Short strings a button can put under its icon — {@code "home" -> "3 of 5"} — because a button
 * that can show its own state is worth more than one that cannot, and the client has no way to
 * compute these. Deliberately opaque text rather than structured data: the moment the client parses
 * one, the client is making decisions again.</p>
 *
 * @param actions ids the player may use, matching what the seam registered
 * @param hints   optional display text per action; an action need not appear here
 */
public record CapabilitiesPayload(Set<String> actions, Map<String, String> hints)
        implements CustomPacketPayload {

    public static final Type<CapabilitiesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Standards.MODID, "capabilities"));

    /**
     * Hand-rolled rather than composed, so the wire format is visible in one place.
     *
     * <p>A cap on both collections: this is written by our own code today, but a payload with no
     * bound is a payload that becomes one the first time somebody registers a thousand actions in a
     * loop. Cheap insurance against a bug that would show up as a client hang.</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CapabilitiesPayload> CODEC =
            StreamCodec.of(CapabilitiesPayload::encode, CapabilitiesPayload::decode);

    private static final int MAX_ENTRIES = 256;

    private static void encode(RegistryFriendlyByteBuf buf, CapabilitiesPayload payload) {
        buf.writeVarInt(Math.min(payload.actions().size(), MAX_ENTRIES));
        int written = 0;
        for (String action : payload.actions()) {
            if (written++ >= MAX_ENTRIES) {
                break;
            }
            buf.writeUtf(action, 64);
        }
        buf.writeVarInt(Math.min(payload.hints().size(), MAX_ENTRIES));
        written = 0;
        for (Map.Entry<String, String> hint : payload.hints().entrySet()) {
            if (written++ >= MAX_ENTRIES) {
                break;
            }
            buf.writeUtf(hint.getKey(), 64);
            buf.writeUtf(hint.getValue(), 64);
        }
    }

    private static CapabilitiesPayload decode(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        Set<String> actions = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            actions.add(buf.readUtf(64));
        }
        int hintCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
        Map<String, String> hints = new LinkedHashMap<>();
        for (int i = 0; i < hintCount; i++) {
            hints.put(buf.readUtf(64), buf.readUtf(64));
        }
        return new CapabilitiesPayload(Set.copyOf(actions), Map.copyOf(hints));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

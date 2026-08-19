package com.sablednah.standards.api.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Where chat name decorations are collected.
 *
 * <p>Standards owns the seam; other mods contribute through {@link NameDecorator}. This is the
 * third time that shape appears in the mod — after the economy and the player switches — and it is
 * what the whole thing is converging on: Standards earns its place over a pile of separate
 * utilities precisely by being where they can meet.</p>
 *
 * <p><b>Additive, unlike the economy.</b> Worth stating, because the two look similar and behave
 * oppositely: exactly one economy provider may hold the money, since a balance is a single fact. A
 * name can carry a faction tag <em>and</em> a party tag <em>and</em> a rank without contradiction,
 * so here every decorator gets a turn.</p>
 */
public final class Chat {

    private static final Logger LOG = LogUtils.getLogger();

    private static final List<NameDecorator> DECORATORS = new ArrayList<>();

    /** Add a decorator. Call during setup, guarded by a {@code standards} loaded check. */
    public static synchronized void register(NameDecorator decorator) {
        DECORATORS.add(decorator);
        DECORATORS.sort(Comparator.comparingInt(NameDecorator::priority));
        LOG.info("Standards: chat decorator '{}' registered at priority {} ({} total)",
                decorator.id(), decorator.priority(), DECORATORS.size());
    }

    public static synchronized List<NameDecorator> all() {
        return List.copyOf(DECORATORS);
    }

    /**
     * Everything that goes before the name, outermost first.
     *
     * <p>Ascending by priority, so the lowest priority ends up furthest from the name — see
     * {@link NameDecorator} for why that is the rule.</p>
     */
    public static List<String> prefixes(ServerPlayer player) {
        return collect(player, true);
    }

    /** Everything that goes after the name, innermost first — the mirror of {@link #prefixes}. */
    public static List<String> suffixes(ServerPlayer player) {
        List<String> out = collect(player, false);
        // Reversed against the prefix order: the highest priority must sit nearest the name on
        // this side too, and on the right-hand side that means first.
        java.util.Collections.reverse(out);
        return out;
    }

    private static List<String> collect(ServerPlayer player, boolean prefix) {
        List<String> out = new ArrayList<>();
        for (NameDecorator decorator : all()) {
            try {
                (prefix ? decorator.prefix(player) : decorator.suffix(player))
                        .filter(s -> !s.isBlank())
                        .ifPresent(out::add);
            } catch (RuntimeException e) {
                // One misbehaving decorator must not cost everybody their chat.
                LOG.error("Standards: chat decorator '{}' threw; skipping it", decorator.id(), e);
            }
        }
        return out;
    }

    private Chat() {}
}

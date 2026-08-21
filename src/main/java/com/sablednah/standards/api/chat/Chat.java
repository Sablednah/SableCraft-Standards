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
    private static final List<ChatRouter> ROUTERS = new ArrayList<>();

    /**
     * Answers "may this player speak, and if not, why not" — installed by Standards at setup.
     *
     * <p>A function rather than a direct call into {@code Mutes} because that lives in the
     * {@code neoforge} package: the API must not depend on the implementation it is the seam for.
     * Same arrangement as {@code VanishGate}, and for the same reason.</p>
     */
    private static java.util.function.Function<ServerPlayer,
            java.util.Optional<net.minecraft.network.chat.Component>> speechGate = p -> java.util.Optional.empty();

    private static java.util.function.Consumer<ServerPlayer> activityNote = p -> {};

    /** Installed by Standards during setup. Not for other mods to call. */
    public static synchronized void installGates(
            java.util.function.Function<ServerPlayer,
                    java.util.Optional<net.minecraft.network.chat.Component>> blocked,
            java.util.function.Consumer<ServerPlayer> activity) {
        speechGate = blocked;
        activityNote = activity;
    }

    /**
     * Whether this player is currently silenced, and the reason if so — already worded for them.
     *
     * <p>For anything that carries a player's words and is not ordinary chat. A mute is meant to
     * silence every channel, so a book, a sign, a shop label or a channel that does its own
     * delivery should ask before publishing. Empty means they may speak.</p>
     */
    public static java.util.Optional<net.minecraft.network.chat.Component> speechBlocked(
            ServerPlayer player) {
        return speechGate.apply(player);
    }

    /** Mark this player as active, clearing any AFK state. Chat through a channel still counts. */
    public static void noteActivity(ServerPlayer player) {
        activityNote.accept(player);
    }

    /**
     * Add a channel that may claim messages before they reach the server at large.
     *
     * <p>Call during setup, guarded by a {@code standards} loaded check. See {@link ChatRouter} —
     * unlike decorators these are <b>not</b> additive: the first to claim a message ends it.</p>
     */
    public static synchronized void registerRouter(ChatRouter router) {
        ROUTERS.add(router);
        // Descending: the highest priority is offered the message first.
        ROUTERS.sort(Comparator.comparingInt(ChatRouter::priority).reversed());
        LOG.info("Standards: chat router '{}' registered at priority {} ({} total)",
                router.id(), router.priority(), ROUTERS.size());
    }

    /** Remove a router. Mainly for the self-test, which must not leave its fixtures behind. */
    public static synchronized void unregisterRouter(ChatRouter router) {
        ROUTERS.remove(router);
    }

    public static synchronized List<ChatRouter> routers() {
        return List.copyOf(ROUTERS);
    }

    /**
     * Offer a message to each router in turn.
     *
     * @return the id of the router that took it, or empty if nobody did and it should be broadcast
     */
    public static java.util.Optional<String> route(ServerPlayer sender, String message) {
        for (ChatRouter router : routers()) {
            try {
                if (router.route(sender, message)) {
                    return java.util.Optional.of(router.id());
                }
            } catch (RuntimeException e) {
                // A thrown router has not delivered anything, so the message must carry on rather
                // than vanish — the same rule the decorators follow.
                LOG.error("Standards: chat router '{}' threw; treating the message as unclaimed",
                        router.id(), e);
            }
        }
        return java.util.Optional.empty();
    }

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

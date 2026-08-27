package com.sablednah.standards.api.combat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * May this player harm that one?
 *
 * <h2>Why this is not each mod's own business</h2>
 *
 * <p>Factions can cancel a damage event and does. But a hostile <b>skill</b> is not a damage event
 * — a curse, a snare, a summon aimed at somebody — and nothing about cancelling damage stops one.
 * So a faction that has declared itself peaceful is peaceful against swords and not against
 * spells, which is not what anybody was promised.</p>
 *
 * <p>The mod that knows the relationship is not the mod doing the harm, and neither should have to
 * know the other exists. So Standards owns the question, whoever knows the answer registers, and
 * anything about to be hostile asks first.</p>
 *
 * <pre>{@code
 * // Before applying a hostile effect that is not damage:
 * Optional<Component> refused = Harm.forbidden(caster, target);
 * if (refused.isPresent()) {
 *     caster.displayClientMessage(refused.get(), true);
 *     return;
 * }
 * }</pre>
 *
 * <p>Player-on-player <b>damage</b> is gated by Standards automatically, so a mod that only deals
 * damage needs no code at all — which is the point, because the mods that need this most are the
 * ones that will not think to ask.</p>
 */
public final class Harm {

    private static final Logger LOG = LogUtils.getLogger();
    private static final List<HarmProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    public static void register(HarmProvider provider) {
        if (provider == null) {
            return;
        }
        PROVIDERS.add(provider);
        LOG.info("Standards: harm provider '{}' registered ({} total)", provider.id(),
                PROVIDERS.size());
    }

    public static void unregister(HarmProvider provider) {
        PROVIDERS.remove(provider);
    }

    public static List<HarmProvider> all() {
        return List.copyOf(PROVIDERS);
    }

    /**
     * Whether anything forbids this.
     *
     * <p>Every provider is asked and the first refusal is returned; there is no ordering to argue
     * about, because a refusal is a promise rather than a bid.</p>
     *
     * <p><b>Fails open on a broken provider.</b> A provider that throws is logged and skipped
     * rather than silently forbidding everything — a mod with a bug should not be able to switch
     * off combat for the whole server, which is the more damaging way to be wrong.</p>
     *
     * @return the reason to show the attacker, or empty if nothing objects
     */
    public static Optional<Component> forbidden(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == null || victim == null || attacker.getUUID().equals(victim.getUUID())) {
            return Optional.empty();
        }
        for (HarmProvider provider : PROVIDERS) {
            try {
                Optional<Component> refusal = provider.forbids(attacker, victim);
                if (refusal.isPresent()) {
                    return refusal;
                }
            } catch (RuntimeException e) {
                LOG.error("Standards: harm provider '{}' threw; permitting", provider.id(), e);
            }
        }
        return Optional.empty();
    }

    /** Shorthand for the common case. */
    public static boolean allowed(ServerPlayer attacker, ServerPlayer victim) {
        return forbidden(attacker, victim).isEmpty();
    }

    private Harm() {}
}

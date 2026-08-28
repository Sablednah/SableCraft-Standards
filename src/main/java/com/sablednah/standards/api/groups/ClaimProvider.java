package com.sablednah.standards.api.groups;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Whoever owns the land model: a faction mod, or a bridge to FTB Chunks.
 *
 * <p><b>One provider, unlike group kinds.</b> Two mods disagreeing about who owns a chunk is the
 * same problem as two ledgers disagreeing about a balance, so this behaves like the economy:
 * highest priority wins outright.</p>
 */
public interface ClaimProvider {

    /** Stable id for logs — {@code factions:claims}, {@code standards:ftbchunks}. */
    String id();

    /**
     * Higher wins. A bridge to somebody else's claims should sit low, so a dedicated land mod
     * displaces it without either side knowing the other exists.
     */
    default int priority() {
        return 0;
    }

    /** Who owns this chunk, or empty for wilderness. */
    Optional<Group> owner(ServerLevel level, ChunkPos chunk);

    /**
     * Whether this player may change this block.
     *
     * <p><b>This is the one consumers should call</b>, and the reason it is separate from
     * {@link #owner}. The real answer folds in membership, trust lists, faction relations and
     * admin bypass — and if only {@code owner} were exposed, every consumer would re-derive that
     * rule slightly differently. One would forget allies, another would forget op bypass, and the
     * bugs would be invisible until somebody exploited them.</p>
     *
     * <p>Called on block break, block place and block interact, so it must be a cheap synchronous
     * lookup. No I/O, no allocation you can avoid.</p>
     */
    boolean mayModify(ServerPlayer player, ServerLevel level, BlockPos pos);

    /**
     * Whether players may fight each other at this position.
     *
     * <p>A different question from {@link #mayModify}, and a different axis from
     * {@link com.sablednah.standards.api.combat.Harm}: that one asks about the <em>pair</em> — two
     * allies, a peaceful faction — while this asks about the <em>place</em>. A spawn area is safe
     * for everybody regardless of who they are; an arena is hostile to everybody the same way.</p>
     *
     * <p>Defaults to permitting, so a provider that has no notion of safe zones inherits sensible
     * behaviour and never has to think about it. <b>Fails open</b> for the same reason the rest of
     * this interface does: a claims mod with a bug should not be able to switch combat off for a
     * whole server, which is the more damaging way to be wrong.</p>
     *
     * @param pos where the fight would happen
     */
    default boolean pvpAllowed(net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos) {
        return true;
    }

    /**
     * Whether a <b>non-player</b> may modify blocks here.
     *
     * <p>A creeper cratering a wall, a zombie chewing a door, a mod's mob laying moss. Distinct
     * from {@link #mayModify} because that one is player-shaped: membership, trust, relations and
     * admin bypass all mean something for a person and nothing whatever for a zombie, and a
     * consumer with no player to pass can only fake it.</p>
     *
     * <p>Same shape as {@link #pvpAllowed}: a question about the <em>place</em>, with nobody in
     * it. Which is what lets a land mod answer it properly — mobs may grief the wilderness and a
     * war zone but not a claim or a safe zone — rather than every consumer deriving its own
     * version from {@code owner()} and quietly disagreeing.</p>
     *
     * <p>Defaults to "only where nobody has claimed it", which is what a consumer would have
     * derived anyway, so adopting this changes no behaviour until a provider says otherwise.</p>
     *
     * <p><b>Hot path.</b> Asked for every block a mob breaks, so it must stay synchronous and
     * allocation-light.</p>
     */
    default boolean griefAllowed(net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos) {
        return owner(level, net.minecraft.world.level.ChunkPos.containing(pos)).isEmpty();
    }
}

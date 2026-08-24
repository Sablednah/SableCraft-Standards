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
}

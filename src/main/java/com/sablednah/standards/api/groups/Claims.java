package com.sablednah.standards.api.groups;

import java.util.Optional;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

/**
 * Who owns this chunk, and may this player build here.
 *
 * <p>Claim <em>data</em> belongs to whoever owns the land model. The claim <em>question</em>
 * belongs here, because mods that will never own land still need to ask it — a mob mod deciding
 * whether something may grief, a world generator deciding whether it may overwrite.</p>
 *
 * <p>Without this seam every one of them hardcodes a single answer, and the day a pack swaps its
 * claims mod they all break — quietly, in the direction of <em>letting</em> something be griefed.</p>
 *
 * <h2>Two queries, and they are not interchangeable</h2>
 *
 * <ul>
 *   <li>{@link #owner} is for <b>display</b>: the map, the border particles, "you are entering
 *       Ravenhold". Empty means wilderness.</li>
 *   <li>{@link #mayModify} is for <b>grief checks</b>, and is the one to call. It folds in
 *       membership, trust, relations and bypass — all the things a caller re-deriving from
 *       {@code owner} would get subtly wrong.</li>
 * </ul>
 *
 * <p>With no provider registered, nothing is claimed and everything is permitted — a server
 * without a land mod behaves exactly as vanilla, and a consumer needs no special case for it.</p>
 */
public final class Claims {

    private static final Logger LOG = LogUtils.getLogger();

    private static volatile ClaimProvider provider;

    /**
     * Offer a claims provider. Highest priority wins, like the economy.
     *
     * @return true if this provider is now the one answering
     */
    public static synchronized boolean register(ClaimProvider candidate) {
        if (provider != null && provider.priority() >= candidate.priority()) {
            LOG.info("Standards: claims provider '{}' ignored; '{}' has priority {} >= {}",
                    candidate.id(), provider.id(), provider.priority(), candidate.priority());
            return false;
        }
        if (provider != null) {
            LOG.info("Standards: claims provider '{}' displaces '{}'", candidate.id(), provider.id());
        } else {
            LOG.info("Standards: claims provider '{}' registered at priority {}",
                    candidate.id(), candidate.priority());
        }
        provider = candidate;
        return true;
    }

    /** For the self-test, which must not leave its fixtures behind. */
    public static synchronized void clear() {
        provider = null;
    }

    /** Whether anything is answering claim questions at all. */
    public static boolean isAvailable() {
        return provider != null;
    }

    /** Who owns this chunk. Empty for wilderness, and empty when nothing provides claims. */
    public static Optional<Group> owner(ServerLevel level, ChunkPos chunk) {
        ClaimProvider p = provider;
        if (p == null) {
            return Optional.empty();
        }
        try {
            Optional<Group> found = p.owner(level, chunk);
            return found == null ? Optional.empty() : found;
        } catch (RuntimeException e) {
            LOG.error("Standards: claims provider '{}' threw on owner(); treating as wilderness",
                    p.id(), e);
            return Optional.empty();
        }
    }

    /**
     * Whether this player may change this block.
     *
     * <p><b>Permits when nothing provides claims, and permits when a provider throws.</b> Failing
     * open is the deliberate choice: a claims mod that errors should not brick every player's
     * ability to place a block, and a server with no land mod at all must behave as vanilla. The
     * cost is that a broken provider stops protecting rather than stops the server, which is the
     * right way round for something on the block-break path.</p>
     */
    public static boolean mayModify(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ClaimProvider p = provider;
        if (p == null) {
            return true;
        }
        try {
            return p.mayModify(player, level, pos);
        } catch (RuntimeException e) {
            LOG.error("Standards: claims provider '{}' threw on mayModify(); permitting", p.id(), e);
            return true;
        }
    }

    private Claims() {}
}

package com.sablednah.standards.neoforge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.standards.core.Duration;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Who is muted, and until when.
 *
 * <p>Saved, unlike teleport requests and cooldowns: a mute that a restart clears is a mute the
 * muted player learns to wait out. Kept in its own save data rather than in {@link StandardsData}
 * because moderation state is the thing most likely to be inspected, exported or hand-edited by an
 * owner, and it is easier to reason about on its own.</p>
 *
 * <p>Bans deliberately do <b>not</b> live here — vanilla's own ban list already stores an expiry
 * date, so {@code /tempban} writes there instead. That keeps {@code /pardon}, the ban screen and
 * {@code banned-players.json} all working exactly as an operator expects.</p>
 */
public final class Mutes extends SavedData {

    /** @param until epoch millis, or {@link Duration#PERMANENT} for no expiry */
    public record Mute(UUID player, long until, String reason, String by) {
        static final Codec<Mute> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Mute::player),
                Codec.LONG.fieldOf("until").forGetter(Mute::until),
                Codec.STRING.optionalFieldOf("reason", "").forGetter(Mute::reason),
                Codec.STRING.optionalFieldOf("by", "").forGetter(Mute::by))
                .apply(i, Mute::new));

        public boolean permanent() {
            return until == Duration.PERMANENT;
        }

        public boolean expired(long now) {
            return !permanent() && now >= until;
        }

        /** Seconds left, or {@link Duration#PERMANENT}. */
        public long remaining(long now) {
            return permanent() ? Duration.PERMANENT : Math.max(0, (until - now) / 1000);
        }
    }

    private static final Codec<Mutes> CODEC = Mute.CODEC.listOf()
            .xmap(Mutes::new, m -> List.copyOf(m.muted.values()))
            .fieldOf("mutes").codec();

    public static final SavedDataType<Mutes> TYPE =
            new SavedDataType<>("standards_mutes", Mutes::new, CODEC, null);

    private final Map<UUID, Mute> muted = new LinkedHashMap<>();

    private Mutes() {}

    private Mutes(List<Mute> entries) {
        entries.forEach(m -> muted.put(m.player(), m));
    }

    public static Mutes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The active mute for this player, if any.
     *
     * <p>Expiry is checked and cleaned up on read rather than on a timer. A mute nobody has looked
     * at has not affected anybody, so there is nothing for a sweep to do — and this way there is
     * no per-tick cost for a feature that is usually empty.</p>
     */
    public Optional<Mute> active(UUID player) {
        Mute mute = muted.get(player);
        if (mute == null) return Optional.empty();
        if (mute.expired(System.currentTimeMillis())) {
            muted.remove(player);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(mute);
    }

    /** @param seconds duration, or {@link Duration#PERMANENT} */
    public Mute mute(UUID player, long seconds, String reason, String by) {
        long until = seconds == Duration.PERMANENT
                ? Duration.PERMANENT
                : System.currentTimeMillis() + seconds * 1000L;
        Mute mute = new Mute(player, until, reason, by);
        muted.put(player, mute);
        setDirty();
        return mute;
    }

    /** @return false if they were not muted */
    public boolean unmute(UUID player) {
        boolean removed = muted.remove(player) != null;
        if (removed) setDirty();
        return removed;
    }

    public List<Mute> all() {
        long now = System.currentTimeMillis();
        return muted.values().stream().filter(m -> !m.expired(now)).toList();
    }
}

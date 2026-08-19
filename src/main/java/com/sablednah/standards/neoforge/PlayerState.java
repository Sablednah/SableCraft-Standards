package com.sablednah.standards.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.core.Waypoint;

import net.minecraft.core.UUIDUtil;

/**
 * The per-player state Standards keeps: which switches are on, and where they have been.
 *
 * <p>Attached to the player entity and saved with them, so it survives logout <em>and</em> death —
 * {@code copyOnDeath} is not a nicety here, it is the entire point of {@code /back}. The one
 * thing a player wants after dying is the way back to their body, and losing the trail at exactly
 * that moment would be a comedy.</p>
 *
 * <p>Anything that should <em>not</em> outlive a session — pending {@code /tpa} requests, teleport
 * warmups, cooldown clocks — deliberately lives in static maps in the service that owns it,
 * rather than here. A teleport request that survives a restart is not a feature.</p>
 */
public class PlayerState {

    public static final MapCodec<PlayerState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("fly", false).forGetter(PlayerState::fly),
            Codec.BOOL.optionalFieldOf("god", false).forGetter(PlayerState::god),
            Waypoint.CODEC.listOf().optionalFieldOf("back", List.of()).forGetter(s -> List.copyOf(s.back)),
            Codec.BOOL.optionalFieldOf("backWasDeath", false).forGetter(PlayerState::backWasDeath),
            Codec.BOOL.optionalFieldOf("refusingTeleports", false).forGetter(PlayerState::refusingTeleports),
            Codec.BOOL.optionalFieldOf("vanished", false).forGetter(PlayerState::vanished),
            Codec.FLOAT.optionalFieldOf("walkSpeed", 1.0F).forGetter(PlayerState::walkSpeed),
            Codec.FLOAT.optionalFieldOf("flySpeed", 1.0F).forGetter(PlayerState::flySpeed),
            Codec.BOOL.optionalFieldOf("refusingMessages", false).forGetter(PlayerState::refusingMessages),
            Codec.BOOL.optionalFieldOf("socialSpy", false).forGetter(PlayerState::socialSpy),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("ignored", List.of())
                    .forGetter(s -> List.copyOf(s.ignored)))
            .apply(i, PlayerState::new));

    private boolean fly;
    private boolean god;
    /** Most recent first. Bounded by the configured history depth. */
    private final List<Waypoint> back = new ArrayList<>();
    /** Whether the newest entry in {@link #back} is a death site, purely so the message can say so. */
    private boolean backWasDeath;
    /** {@code /tptoggle} — refuse all incoming teleport requests. Persisted, because a player who
     *  set it wants it to still be set tomorrow. */
    private boolean refusingTeleports;
    /** {@code /vanish}. Persisted, so staff stay hidden across a relog rather than
     *  popping into existence in front of whoever they were watching. */
    private boolean vanished;
    /** Multipliers of vanilla's own speeds, so 1.0 is normal. Persisted, because a
     *  speed that silently resets on death is worse than one that never worked. */
    private float walkSpeed = 1.0F;
    private float flySpeed = 1.0F;
    /** {@code /msgtoggle} — refuse all private messages. */
    private boolean refusingMessages;
    /** {@code /socialspy} — see other people's private messages. */
    private boolean socialSpy;
    /** {@code /ignore} — people whose messages never arrive. */
    private final java.util.Set<java.util.UUID> ignored = new java.util.LinkedHashSet<>();

    /** The default for a player who has never had state. */
    public PlayerState() {}

    private PlayerState(boolean fly, boolean god, List<Waypoint> back, boolean backWasDeath,
            boolean refusingTeleports, boolean vanished, float walkSpeed, float flySpeed,
            boolean refusingMessages, boolean socialSpy, List<java.util.UUID> ignored) {
        this.fly = fly;
        this.god = god;
        this.back.addAll(back);
        this.backWasDeath = backWasDeath;
        this.refusingTeleports = refusingTeleports;
        this.vanished = vanished;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.refusingMessages = refusingMessages;
        this.socialSpy = socialSpy;
        this.ignored.addAll(ignored);
    }

    public boolean fly() {
        return fly;
    }

    public void setFly(boolean value) {
        this.fly = value;
    }

    public boolean god() {
        return god;
    }

    public void setGod(boolean value) {
        this.god = value;
    }

    public boolean backWasDeath() {
        return backWasDeath;
    }

    public boolean refusingTeleports() {
        return refusingTeleports;
    }

    public void setRefusingTeleports(boolean value) {
        this.refusingTeleports = value;
    }

    public boolean vanished() {
        return vanished;
    }

    public void setVanished(boolean value) {
        this.vanished = value;
    }

    public float walkSpeed() {
        return walkSpeed;
    }

    public void setWalkSpeed(float value) {
        this.walkSpeed = value;
    }

    public float flySpeed() {
        return flySpeed;
    }

    public void setFlySpeed(float value) {
        this.flySpeed = value;
    }

    public boolean refusingMessages() {
        return refusingMessages;
    }

    public void setRefusingMessages(boolean value) {
        this.refusingMessages = value;
    }

    public boolean socialSpy() {
        return socialSpy;
    }

    public void setSocialSpy(boolean value) {
        this.socialSpy = value;
    }

    public boolean ignores(java.util.UUID other) {
        return ignored.contains(other);
    }

    /** @return true if they are now ignored, false if the ignore was lifted */
    public boolean toggleIgnore(java.util.UUID other) {
        if (ignored.remove(other)) return false;
        ignored.add(other);
        return true;
    }

    public java.util.Set<java.util.UUID> ignoredPlayers() {
        return java.util.Set.copyOf(ignored);
    }

    /**
     * Remember somewhere as a place to come back to. Pushed onto the front and trimmed to the
     * configured depth, so {@code /back 1} is always the most recent departure.
     */
    public void pushBack(Waypoint where, boolean death) {
        back.addFirst(where);
        backWasDeath = death;
        int limit = StandardsConfig.BACK_HISTORY.get();
        while (back.size() > limit) {
            back.removeLast();
        }
    }

    /** The n-th previous location, 1-based, without consuming it. */
    public Optional<Waypoint> peekBack(int depth) {
        return depth >= 1 && depth <= back.size() ? Optional.of(back.get(depth - 1)) : Optional.empty();
    }

    /** Take the n-th previous location out of the trail — {@code /back} consumes what it uses. */
    public Optional<Waypoint> popBack(int depth) {
        Optional<Waypoint> found = peekBack(depth);
        found.ifPresent(w -> {
            back.remove(depth - 1);
            if (depth == 1) backWasDeath = false;
        });
        return found;
    }

    public int backDepth() {
        return back.size();
    }
}

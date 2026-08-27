package com.sablednah.standards.api.combat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired before a combat tag lands, so somebody closer to the truth can change it.
 *
 * <h2>The lines will move, so they are movable</h2>
 *
 * <p>Pets, magic bolts, enchanted swords, flamethrowers. How damage should be classified will be
 * re-argued the moment real players start hitting each other with things nobody anticipated, and
 * <b>it should not need a code change each time.</b></p>
 *
 * <p>LegendQuest knows a magic bolt is a spell rather than a punch. A flamethrower mod knows its
 * flames are player-sourced. Standards cannot know either and should not guess — so it publishes
 * its best answer and lets anyone with a better one say so.</p>
 *
 * <pre>{@code
 * @SubscribeEvent
 * static void onTag(CombatTagEvent event) {
 *     if (event.getSource().startsWith("legendquest:")) {
 *         event.setKind(CombatKind.SKILL);
 *         event.setSeconds(30);          // a channelled ritual, not a quick blast
 *     }
 * }
 * }</pre>
 *
 * <p>Cancelling it means <b>no tag at all</b> — for an arena plugin, a duel that should not lock
 * anybody out of anything, or a damage type a server has decided is not combat.</p>
 */
public class CombatTagEvent extends Event implements ICancellableEvent {

    private final ServerPlayer player;
    private final String source;
    private CombatKind kind;
    private int seconds;

    public CombatTagEvent(ServerPlayer player, CombatKind kind, String source, int seconds) {
        this.player = player;
        this.kind = kind;
        this.source = source;
        this.seconds = seconds;
    }

    /** Who is about to be tagged. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * A short id for what caused it — {@code "standards:player"}, {@code "standards:mob"},
     * {@code "legendquest:curse"}. Read-only: it describes what happened, and what happened does
     * not change because somebody disagrees about its classification.
     */
    public String getSource() {
        return source;
    }

    public CombatKind getKind() {
        return kind;
    }

    public void setKind(CombatKind kind) {
        if (kind != null) {
            this.kind = kind;
        }
    }

    public int getSeconds() {
        return seconds;
    }

    /** Zero or less means the tag does not land, the same as cancelling. */
    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }
}

package com.sablednah.standards.neoforge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * What command a player is in the middle of running.
 *
 * <h2>Why this rather than a parameter</h2>
 *
 * <p>{@code /back list} wants to say <em>how</em> each stop got onto the trail, and only the
 * command knows that. Threading a label through {@link Teleports#request} would work, and would
 * mean every present and future caller has to remember to supply one — including other mods,
 * which is precisely the kind of thing nobody remembers. Factions would have needed a change to
 * get labels at all, and the first mod to forget would produce a trail row reading "unknown" with
 * no clue why.</p>
 *
 * <p>So the label is taken where every command already passes: the dispatcher. One listener, and
 * anything that teleports a player during a command is labelled with it, whether it has heard of
 * this class or not.</p>
 *
 * <h2>The whole typed line, trimmed</h2>
 *
 * <p>Stored as typed rather than reduced to a command name, because {@code /home base} and
 * {@code /home mine} are different answers to "where did this come from" and the argument is the
 * half that identifies it. Trimmed only to stop a pathological line bloating the save.</p>
 *
 * <p>Not persisted, and cleared as the command finishes: it is a fact about right now, and a stale
 * one would label the next teleport with the last thing anybody typed.</p>
 */
public final class CommandTrace {

    /** Longer than any real command worth remembering, short enough not to matter in a save. */
    private static final int MAX = 48;

    private static final Map<UUID, String> RUNNING = new ConcurrentHashMap<>();

    public static void begin(UUID player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String trimmed = command.strip();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        if (trimmed.length() > MAX) {
            trimmed = trimmed.substring(0, MAX - 1) + "…";
        }
        RUNNING.put(player, trimmed);
    }

    public static void end(UUID player) {
        RUNNING.remove(player);
    }

    /** The command this player is running, or empty if they are not running one. */
    public static String current(ServerPlayer player) {
        return RUNNING.getOrDefault(player.getUUID(), "");
    }

    private CommandTrace() {}
}

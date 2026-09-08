package com.sablednah.standards.api.actions;

import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * One thing a player can do, offered to a modded client as a button and/or a keybind.
 *
 * <h2>An action, not a button</h2>
 *
 * <p>A button and a keybind are two <em>triggers</em> for one action, so the action is registered
 * once and both find it. StoryTeller wants possession and drift on keys as well as on the bar,
 * because a storyteller hops between them constantly through a scene and a mouse trip per hop is
 * the friction that makes a tool go unused.</p>
 *
 * <h2>It runs a command</h2>
 *
 * <p>{@link #command} is what the player would have typed, without the slash. That is the whole
 * design: the server path is then identical to typing it, so permissions, cooldowns, warmups,
 * config switches and logging all already work, with no second code path to keep in step. There is
 * deliberately <b>no serverbound payload</b> anywhere in this seam.</p>
 *
 * <p>It follows that an action must be something a vanilla player could also do by typing. A button
 * that reached a capability no command exposes would break the promise that an unmodified client
 * loses nothing but convenience.</p>
 *
 * @param id         namespaced, and yours: {@code "storyteller:possess"}. Standards' own are bare
 *                   ({@code "fly"}), which is the one liberty the owner of the seam takes
 * @param priority   closeness to the anchor, the same rule the chat decorators use. Higher sits
 *                   nearer the inventory; Standards' own switches are 0
 * @param icon       an item to draw as the icon, resolved on the client. An {@link Identifier}
 *                   rather than an {@code ItemStack} so registration cannot depend on registry
 *                   timing, and so a dedicated server never touches item rendering
 * @param tooltipKey a {@code Lang} key. Contributed by your own mod's catalogue, so an owner can
 *                   rewrite it like every other string
 * @param command    what to run, without the leading slash
 * @param available  whether to offer it to this player at all. Evaluated <b>server-side</b> and
 *                   sent as a list of ids, because the client cannot be trusted to know and must
 *                   not be asked to guess — a button offered for something the player cannot do is
 *                   the "granted command renders red" bug in reverse
 * @param active     whether this is <b>on right now</b>, for anything that is a state rather than
 *                   an act: flying, vanished, possessing. Drawn lit rather than dim, and it is
 *                   worth more than another button — half of a gamemaster tool's bugs are the game
 *                   and the operator disagreeing about what is happening, and a bar that says
 *                   "you are wearing a cow, you are hidden" settles that at a glance. Null for an
 *                   action that is an act rather than a state ({@code /home}, {@code /back})
 * @param hint       optional short text under the icon — {@code "3"} homes, {@code "a cow"}.
 *                   Opaque on purpose: the moment the client parses a hint it is deciding things
 * @param children   what right-clicking this one offers: your homes, your warps, the commands you
 *                   grouped under a category. Evaluated server-side like everything else, so the
 *                   client draws what it is told rather than deriving commands itself. Null for an
 *                   action with nothing underneath
 */
public record Action(
        String id,
        int priority,
        Identifier icon,
        String tooltipKey,
        String command,
        Predicate<ServerPlayer> available,
        Predicate<ServerPlayer> active,
        Function<ServerPlayer, String> hint,
        Function<ServerPlayer, java.util.List<Child>> children) {

    /**
     * One entry in a right-click row.
     *
     * <p>A label and a command, and nothing else. Deliberately not an {@link Action}: a child is
     * data about <em>this player right now</em> — the homes they happen to have — where an action
     * is a registration. Making children actions would mean registering one per home per player.</p>
     */
    public record Child(String label, String command) {}

    /** An act rather than a state: no lit/dim, no hint, nothing underneath. */
    public Action(String id, int priority, Identifier icon, String tooltipKey, String command,
            Predicate<ServerPlayer> available) {
        this(id, priority, icon, tooltipKey, command, available, null, null, null);
    }

    /** A state: drawn lit while {@code active} says so. */
    public Action(String id, int priority, Identifier icon, String tooltipKey, String command,
            Predicate<ServerPlayer> available, Predicate<ServerPlayer> active) {
        this(id, priority, icon, tooltipKey, command, available, active, null, null);
    }

    /**
     * A state with a hint, and nothing underneath.
     *
     * <p>⚠ <b>This overload exists because removing it broke a shipped consumer.</b> {@code children}
     * was added to the canonical constructor, which silently made the eight-argument form — the
     * canonical one until that moment — stop existing. StoryTeller was compiled against it, the
     * version number did not change, and the already-shipped jar went on calling a constructor that
     * had gone. It degraded quietly only because that mod wrapped the call in a {@code LinkageError}
     * guard.
     *
     * <p>So: <b>a record's canonical constructor is public API, and adding a component is a breaking
     * change.</b> Every previously-canonical shape keeps an explicit overload here, permanently, and
     * anything new goes on the end with one of these beside it.</p>
     */
    public Action(String id, int priority, Identifier icon, String tooltipKey, String command,
            Predicate<ServerPlayer> available, Predicate<ServerPlayer> active,
            Function<ServerPlayer, String> hint) {
        this(id, priority, icon, tooltipKey, command, available, active, hint, null);
    }

    /**
     * A <b>category</b>: does nothing itself, and exists to hold others.
     *
     * <p>Left-clicking it runs nothing, so the bar draws it differently — a button that silently
     * ignores a click is worse than no button. Right-clicking opens what is underneath.</p>
     */
    public static Action category(String id, int priority, Identifier icon, String tooltipKey,
            Predicate<ServerPlayer> available,
            Function<ServerPlayer, java.util.List<Child>> children) {
        return new Action(id, priority, icon, tooltipKey, "", available, null, null, children);
    }

    /** Whether this is a category — no command of its own, only things underneath. */
    public boolean isCategory() {
        return command() == null || command().isBlank();
    }
}

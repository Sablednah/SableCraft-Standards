# The vanish API

Whether a player is hidden, for mods that draw things attached to players.

**Status: built 2026-08-29, and consumed since 2026-08-30.** `api/vanish/` — `Vanish`,

**Settable since 2026-09-07**, on request from LegendQuest's StoryTeller — see *Holds* below.
`VanishEvent` — under the self-test, and LegendQuest's `VanishSupport` now takes its nameplate down:
`PlayerVisibility.setCheck(Vanish::isVanished, Vanish::anyVanished)` plus a `VanishEvent` listener.

That it used **both halves** is the part worth recording. This document argued that a query alone
leaves the plate hanging for anyone who vanishes mid-session, and the first real consumer wired the
query *and* the event without being asked twice — which is the only evidence that an API's contract
was actually legible to somebody who did not write it.

## The bug it was written for

A `/vanish`ed player was hidden, and their LegendQuest nameplate stayed exactly where it was —
a floating name hanging over nobody. Found by hand on the 26.2 dev server, and it gives a vanish
away as completely as being seen would.

## Why Standards cannot fix this itself

Standards hides a player by answering `false` from `ServerPlayer.broadcastToPlayer`, which is the
question vanilla's own entity tracker asks every pass. That is the right lever and it covers the
player completely — pairing, chunk loads, dimension changes, view distance, all of it. See decision
9 in `CLAUDE.md`.

What it cannot cover is **a nameplate, health bar, hologram or particle trail another mod attached
to them.** Those are separate entities that Standards has never heard of.

The tempting fix is to hide entities near a vanished player. It is wrong in both directions:

- it catches **other people's** holograms, dropped items and pets that happen to be standing there;
- it misses a decoration that tracks its owner **from somewhere else** — above their head, at their
  feet, or a few blocks off.

Which entities *belong to* a player is a question only the mod that spawned them can answer. So
Standards answers the question it owns and the decoration's owner acts on it — the same division as
the chat, claims and combat seams. **Standards owns the meeting point; the other mod consults it.**

## Two mechanisms, because one is not enough

This is the part worth reading twice, because getting it half right looks like it works.

```java
// 1. ASK, when you create the decoration.
//    A player can log in already vanished - the state exists before anything of yours does.
if (!Vanish.isVanished(player)) spawnNameplate(player);

// 2. LISTEN, for the change.
//    A player who vanishes MID-SESSION is the case that actually bites: check-on-spawn alone
//    leaves the decoration hanging in the air. That was the reported bug.
@SubscribeEvent
static void onVanish(VanishEvent event) {
    if (event.isVanished()) removeNameplate(event.getPlayer());
    else                    spawnNameplate(event.getPlayer());
}
```

`VanishEvent` fires on the server thread **after** the change has taken effect, so `isVanished`
already agrees with it and there is no window where the two disagree. It is not cancellable: by
then the player has already been unpaired from every viewer's tracker, and a listener that
"refused" would leave the two halves contradicting each other.

It fires only on a deliberate change **while the player is online**. A returning player's saved
state is restored during login, before mods have attached anything to them, so there is nothing to
notify — that case is what mechanism 1 is for.

## Per-viewer, if you need it

```java
Vanish.hiddenFrom(subject, viewer)   // honours standards.vanish.see
```

Staff holding the see-through permission still see the vanished player, so they can sensibly still
see the name. That needs you to gate your own entity's visibility per viewer, which is real work —
**removing the decoration outright is simpler and fixes the giveaway**, and is what to build first.
This is here for when simpler is not good enough.

`Vanish.anyVanished()` is the hot-path escape hatch: one field read, and on the overwhelming
majority of servers it lets a per-tick or per-entity check bail out immediately.

## Safe to call unconditionally

Every method answers "not vanished" when the feature is off, when nobody has used it, or during
startup before the mod has finished loading. There is no initialisation order to respect and no
null to guard.

## ⚠ Nothing calls this yet

The self-test covers the gate's logic in both directions — hidden from a bystander, *not* hidden
from yourself, see-through beating the vanish, and the set emptying on unvanish. What it cannot
cover is whether anything ever asks.

That is this codebase's recurring failure: **the chat decorator path returned empty for weeks
because no decorator existed**, and looked exactly this healthy. Until LegendQuest removes a
nameplate through this seam, treat it as unproven — and when it does, watch the mid-session vanish
specifically, because that is the case the query alone silently gets wrong.

## Holds — setting it, not just reading it

`api/vanish` was read-only until StoryTeller needed to *put* somebody into the state: possession
drops a storyteller into a creature's scene, and spectator turned out to be the wrong tool for it —
a spectator flies, noclips, and has its own input semantics that fight the feature. A vanished
player has a real grounded body, which is the whole point.

```java
Vanish.hold(player, "storyteller:possess", true);   // hide
Vanish.hold(player, "storyteller:possess", false);  // release YOUR hold
Vanish.holders(player);                             // who is hiding them
```

**Holds rather than a boolean, and this is the load-bearing part.** More than one thing can want
somebody hidden at once, and the loser of a plain boolean is whoever releases second:

> A storyteller types `/vanish`, then possesses a creature for a scene. The scene ends. A boolean
> setter reveals them — undoing a choice they made before the scene started.

Reading the state first and restoring conditionally is the obvious fix and it races the moment a
second mod does the same thing. So each caller holds under its own key and releases only its own,
and the player is hidden while **any** hold stands. Additive, like the chat decorators, and for the
same reason — several contributors can want this without contradicting each other.

`/vanish` is itself a holder, under `standards:command`. So `/vanish off` during somebody else's
scene releases only the command's hold and **says so**, rather than reporting "off" while the player
is still invisible — which is the worst of both, since they then walk out in front of somebody
believing they can be seen.

Only the command hold survives a logout. A hold another mod placed belongs to whatever that mod was
doing at the time; a scene that ended while somebody was offline should not still be hiding them.

**No permission check.** The caller is the authority — a mod gating possession behind its own node
should not also need the *target* to be allowed to `/vanish` themselves, which is a different
question about a different person. Same reasoning as `api.PlayerSwitches`.

`VanishEvent` fires when the state actually changes, and not when a second holder joins or leaves
without moving anything on the wire.

## What vanish actually guarantees

Asked by StoryTeller before it claimed any of it, which is the right question and found a gap:

| | |
|---|---|
| hidden from other players | **yes** — `ServerPlayerVanishMixin` on `broadcastToPlayer` |
| not pushable | **yes** — `LivingEntityVanishMixin` on `isPushable` |
| does not hoover up items | **yes**, unless `vanishPickup` |
| **not targeted by mobs** | **yes, since 2026-09-07** — it was *not* covered before the question |
| still solid against blocks | **yes** — vanish never touches collision with the world |
| still subject to gravity | **yes** — and this is why possession uses vanish rather than spectator |

The targeting fix is a `LivingChangeTargetEvent` listener rather than a third mixin: `CLAUDE.md`
treats every mixin as a version-fragile surface needing justification, and NeoForge fires this event
precisely so nobody has to inject into targeting goals. `vanishTargeted` turns it off.

It clears the target only. It does not stop a mob mid-swing and cannot un-anger something that was
already hunting them — vanishing is walking away from a fight, not undoing it.

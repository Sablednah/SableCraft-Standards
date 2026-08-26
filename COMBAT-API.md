# The combat API

Whether a player is in combat, so that nothing lets them walk out of one.

**Status: specified, and scheduled for 1.1.** Agreed 2026-08-20, deliberately held out of 1.0.0.

Nothing in `api/combat/` exists yet — but see below, because Standards already ships a narrow,
holed version of this, and the hole is the reason the API is worth building rather than a reason
to rush it.

Held back because `movement.cancelOnDamage` already covers the warmup window, which is most of the
value; because its known consumers — LegendQuest, and now Factions — have not asked for it yet; and
because it is purely additive, so nothing in 1.0.0 forecloses any of it. Holding a release for a
feature nobody is waiting on is how releases slip.

## Why there is one at all

A teleport is an escape hatch. `/home` mid-fight is not a clever play, it is the fight not
happening — and the person who was winning it has no recourse.

The classic fix is a combat tag: being hit, or hitting someone, starts a countdown, and while it
runs the escape hatches are closed.

The reason it needs to be an **API** rather than a private field is LegendQuest. A skill or a feat
can be an act of combat without any damage event firing — a curse, a summon, a channelled ritual.
Only LegendQuest knows that happened. Standards owns the question "is this player in combat";
anything closer to the answer must be able to say so.

---

## Standards already does this, narrowly, and the narrow version has a hole

```
movement.cancelOnDamage = true
  "Taking damage during the warmup cancels the teleport — the anti-combat-log
   half of a warmup, and the reason a warmup is worth having at all."
```

That comment is right about why it matters and wrong about how far it goes. **It only protects the
warmup window.** Once the warmup completes, or if warmup is `0`, or if the player holds
`standards.teleport.instant` — damage does nothing at all.

So today a player cannot be *interrupted*, but can absolutely leave the moment their countdown
ends. The combat tag closes that, and `cancelOnDamage` folds into it rather than sitting alongside
it.

---

## Kinds, because one number cannot serve both servers

On a peaceful server, a skeleton plinking you must not block `/home`. On ZARP, a player hitting you
absolutely must. That is not a difference of duration, it is a difference of consequence, so kinds
carry both:

```
combat.pve.seconds = 8       blocksTeleport = true    onLogout = nothing
combat.pvp.seconds = 12      blocksTeleport = true    onLogout = npc
combat.skill.seconds = 10    blocksTeleport = true    onLogout = nothing
```

Indicative defaults: **PvE 5–10s, PvP 10–15s.**

A peaceful server then sets `pvp.seconds = 0` and the entire PvP branch goes quiet with no separate
code path — the same trick as emptying `movement.topBarriers` to opt out of barrier checks.

Modern practice has settled on **short tags**. Ten to fifteen seconds, against the thirty to sixty
of the Factions era. Long tags punish ordinary play — shot by a skeleton, cannot `/home` for a
minute — and that erodes trust in the mechanic faster than the occasional escape does.

### Tags extend, they never overwrite

PvP tags for 12s; a zombie clips the same player at second 3 for 8s. **The answer is 12.**

With a single global duration this bug is invisible. With per-kind durations, a shorter tag
actively *rescues* the player who is fleeing — the exact person the feature exists to stop. Worth a
self-test check on the day it is written.

### A caller may specify its own duration

Standards knows what a punch is worth. Only LegendQuest knows whether a skill was a quick blast or
a ten-second channelled ritual.

```java
Combat.tag(player, CombatKind.SKILL, "legendquest:curse");        // config default
Combat.tag(player, CombatKind.SKILL, "legendquest:ritual", 30);   // caller knows better
```

Without the second form, LegendQuest over-tags every quick skill or under-tags every slow one.

---

## Who counts as the attacker

### Resolve the owner, not the projectile

Arrows, splash potions, TNT. If the tag resolves the *damage source entity* rather than the entity
**behind** it, every one of those is a free hit that does not tag — and it is what people find in
the first week.

`LivingIncomingDamageEvent` hands over the `DamageSource`; the wanted answer is
`Projectile.getOwner()`. Standards already resolves projectile ownership for the vanish
pass-through, so there is a pattern to follow rather than invent.

### Nothing environmental tags at all

Fall damage, drowning, cactus, fire, **freezing**. None of it has an attacker, so none of it is
combat, so none of it may close an escape hatch.

That sounds obvious until you notice the failure it prevents, which was found by accident: a player
trapped in powder snow inside somebody else’s claim cannot break out — claim protection is working
exactly as intended — and their only way out is a teleport. Tag them for the freezing damage and
the teleport closes too. They are now stuck in a hole, taking damage, with every exit shut by two
features that are each behaving correctly.

So the rule is not "damage starts a tag", it is **"an attacker starts a tag"**. Resolve the source
to a player or a mob first; if there is nobody behind it, there is no combat and no tag. Worth a
self-test check, because the naive implementation is a single `LivingIncomingDamageEvent` handler
that never asks the question.

### Pets: the tag follows intent to harm a player

Decided deliberately, and it is **directional**:

| | kind |
|---|---|
| You hit somebody's wolf | **PvE** — you attacked an animal |
| Their wolf hits you | **PvP, for both** — they are fighting you through a proxy |

The first half stops a griefer shoving a wolf in front of you and forcing you into combat lock by
making you kill it.

The second half exists because "literal — a wolf is not a player" applied to *both* directions puts
pet-fighting entirely outside the combat system: the owner never tagged, the victim only briefly
tagged and free to leave. Fighting through pets would become the way to *avoid* combat lock. On a
server running LegendQuest that is not a corner case — **"fights through animals" is a character
build**, and a beastmaster structurally immune to combat lock is a balance problem that arrives
with the class rather than with the exploiters.

One rule covers arrows and pets both, which is a fair sign it is one rule and not two wearing a
coat.

**Still fuzzy, deliberately:** a wolf retaliating automatically when its owner is hit. Arguably
correct to tag the owner — they are in a fight — but it does mean owning a wolf makes you easier to
lock. Probably fine, since they were in a PvP fight already.

---

## The lines will move, so make them movable

Pets, magic bolts, enchanted swords, flamethrowers. The classification will be re-argued the moment
real players start hitting each other with things nobody anticipated, and **it should not need a
code change each time.**

- **A hook on the classification.** A `CombatTagEvent` whose kind can be reassigned before it
  lands. LegendQuest knows a magic bolt is a spell rather than a punch; a flamethrower mod knows
  its flames are player-sourced. Standards cannot know either and should not guess.
- **Config overrides by damage type**, so an owner can reclassify a modded damage source without
  touching Java.

Same seam shape as everything else: Standards owns the question, whoever is closer owns the answer.

### Log the classification from day one

`pvp via arrow -> owner Sablednah` costs nothing and is the difference between tuning and guessing.
It is obvious in hindsight and infuriating to add after an evening spent wondering why a
flamethrower did not tag.

---

## Reading the tag

```java
Combat.isInCombat(player)            // any kind
Combat.isInCombat(player, kind)      // a specific one
Combat.remaining(player)             // millis, for countdowns
Combat.clear(player)                 // death, admin
```

**Bypass is a permission, not an op check.** `standards.combat.bypass` lets LuckPerms grant it to
staff without opping them — the same reasoning that put every other gate on a node.

**It must say why.** *"You cannot teleport for 6 more seconds — in combat"*, on the action bar
already used for warmup countdowns. A refusal with no reason reads as a broken command, which is
the exact failure `Teleports.Watcher` exists to prevent for `/tpa`.

---

## Combat logging

**Deliberately deferred. Config it now, build it later.**

The modern consensus is a **combat-log NPC**: on disconnect while tagged, spawn a stand-in holding
the player's inventory. Kill it and the player is dead with their loot dropped; let the tag expire
and it vanishes with nothing lost.

It won because **you cannot distinguish a rage-quit from a router dying** — nobody can. The old
answer, instant death on logout while tagged, assumed malice and so punished every genuine
disconnect, which on a real server is most of them. The NPC sidesteps the question: the punishment
becomes **conditional on being pursued**. A disconnect nobody was chasing costs nothing; a flight
from a losing fight still ends in death by the person who earned it, rather than handing them a
free kill.

### Why it is not in v1

**"Teleport escape" and "disconnect escape" feel like one feature and are not:**

| | value | cost |
|---|---|---|
| Block teleports while tagged | most of it | small — the tag is being built anyway |
| Combat-log NPC | the rest | genuinely large |

In practice most combat logging is `/home` and `/tpa`, not alt-F4, because typing a command is
easier and does not cost you your session.

The NPC is where the edge cases live: a server restart with an NPC alive, a player rejoining before
it dies, inventory reconciliation — and, specific to this server, **an NPC stand-in cannot use
LegendQuest skills**, so a logged Mage becomes a punching bag rather than the fight the attacker
was actually having.

So ship `combat.pvp.onLogout = nothing | npc | death` with `nothing` as the default, and build the
NPC once ZARP-style PvP is live and it can be tuned against real behaviour. Nothing here forecloses
it.

*(The NPC consensus is the pattern the plugin ecosystem converged on rather than something surveyed
recently — worth a sanity check against current CombatLogX behaviour before committing to details.)*

---

## Stability

Nothing here is stable until it is built. When it is, `api/combat/` follows the same promise as
`api/economy/`.

Related: [`GROUPS-API.md`](GROUPS-API.md), [`ECONOMY-API.md`](ECONOMY-API.md),
[`CHAT-API.md`](CHAT-API.md), and
[Factions ReForged's `POWER.md`](https://github.com/Sablednah/Factions-ReForged/blob/main/POWER.md),
which found the 2012 plugin blocking `/home`, `/spawn` and `/tpa` outright inside enemy territory —
a cheaper answer to the same question than a damage-driven tag, and one that wants building here
rather than there.

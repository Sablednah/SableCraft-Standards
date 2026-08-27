# The combat API

Whether a player is in combat, so that nothing lets them walk out of one.

**Status: built, and in use.** `api/combat/` shipped on 2026-08-27 — `CombatKind`, `CombatTag`,
`Combat`, `CombatTagEvent`, `Harm`, `HarmProvider` — with tagging, teleport blocking, a bypass
permission and a harm gate, under 352 self-test checks.

**LegendQuest builds against it** and its skills respect PvP rules and zones: targeted spells ask
about the pair, area effects ask about the place and then about each player they catch. **Factions
registers `factions:pvp`**, so peaceful, same-faction, ally and pvp-off all answer through one
question. Everything below the design sections describes what is there now; the combat-log NPC at
the end is the one part still deliberately deferred.

The rest of this document is the reasoning, kept because the decisions still stand and each of
them is the sort that looks arbitrary until somebody re-derives it.

---

## Using it from another mod

Add Standards as a `compileOnly` dependency — the API is a soft dependency and every call is safe
to make, but guard the *class* if your mod must run without Standards installed at all.

### Say that something was an act of combat

```java
import com.sablednah.standards.api.combat.Combat;
import com.sablednah.standards.api.combat.CombatKind;

// The configured duration for a skill tag (default 10s).
Combat.tag(player, CombatKind.SKILL, "legendquest:curse");

// Or one you have decided, because only you know a channelled ritual from a quick blast.
Combat.tag(player, CombatKind.SKILL, "legendquest:ritual", 30);
```

`source` is a short id used in logs and nothing else. With `combat.log = true` a server owner sees
`Combat: Sablednah -> skill via legendquest:ritual (30s)`, which is the difference between tuning
and guessing when somebody asks why a spell does or does not lock them in.

**Tagging both sides is your job**, because only you know who the aggressor was. A curse is an act
of combat for the caster as well as the target on most servers — call it twice.

### Ask whether somebody is fighting

```java
Combat.isInCombat(player);                      // any kind
Combat.isInCombat(player, CombatKind.PVP);      // one kind
Combat.remaining(player);                       // millis, for your own countdown
Combat.current(player, CombatKind.PVP);         // the live tag, with its source
Combat.clear(player);                           // your own escape effect, an arena, an admin tool
```

`Combat.blockingTeleport(player)` answers the narrower question Standards itself asks — is there a
tag whose kind is configured to close escape hatches — so a mod adding its own teleport can refuse
for the same reasons and with the same config.

### Resolve who was really behind a hit

```java
Combat.playerBehind(damageSource);   // Optional<ServerPlayer>, through arrows and pets
Combat.hasAttacker(damageSource);    // false for fall, drowning, fire, freezing
```

**Public on purpose.** Anything deciding "was this a player's doing" needs the same answer, and two
implementations of it eventually disagree — which is a bug nobody can reproduce because it depends
on whether an arrow or a wolf was involved. Factions' power system uses exactly these.

### Ask whether you may harm somebody at all

A combat tag says a fight *is* happening. This is the question before it: **may this even
happen?**

```java
import com.sablednah.standards.api.combat.Harm;

Optional<Component> refused = Harm.forbidden(caster, target);
if (refused.isPresent()) {
    caster.displayClientMessage(refused.get(), true);   // it already says why
    return;
}
```

One call covers peaceful factions, two people in the same faction, **allies**, a server with PvP
off, and anything else that ever registers — without your mod knowing any of them exist.

**Player-on-player damage is gated by Standards automatically.** A mod that only deals damage needs
none of this. The call is for the hostile things that are *not* damage — a curse, a snare, a hex, a
summon aimed at somebody — which cancelling a damage event never stopped. That gap is why the seam
exists: Factions used to cancel `LivingIncomingDamageEvent` and nothing else, so a faction that had
declared itself peaceful was peaceful against arrows and defenceless against spells.

**By target, and by area.** A targeted effect asks about the pair. An area effect asks about the
place first and then about each player it actually catches:

```java
// Lightning at a point: is fighting allowed HERE at all?
if (!Claims.pvpAllowed(level, pos)) return;

// ...then per victim it lands on, because the place allowing it does not mean this pair may fight.
for (ServerPlayer caught : inRadius) {
    if (Harm.allowed(caster, caught)) hurt(caught);
}
```

`Claims.pvpAllowed(level, pos)` is the **place** half and lives on the claims seam, because the mod
that owns the chunk is the one that knows. Standards checks **both ends of a shot** for ordinary
damage — a safe zone has to stop arrows fired into it as well as out of it, or the safety is one
bowshot from useless.

**Any veto denies, and that is unlike every other seam here.** One economy provider holds the
money; the highest-priority claims provider wins; the first chat router to claim a message ends the
matter. Here every provider is asked and a single refusal is final — because a refusal is a
promise, and a priority contest would mean `/f peaceful` held only until somebody registered a
provider with a bigger number. A mod that genuinely needs to override should cancel the damage
event at its own priority, where it is visibly taking responsibility rather than quietly outbidding
somebody.

**A provider that throws is skipped, not obeyed.** Failing open, because a mod with a bug switching
combat off for a whole server is the more damaging way to be wrong.

### Disagree with a classification

```java
@SubscribeEvent
static void onTag(CombatTagEvent event) {
    if (event.getSource().startsWith("legendquest:")) {
        event.setKind(CombatKind.SKILL);
        event.setSeconds(30);
    }
    if (inMyArena(event.getPlayer())) {
        event.setCanceled(true);     // a duel should lock nobody out of anything
    }
}
```

Fired before **every** tag, Standards' own included, so a mod that reclassifies is never working
against a special case we forgot to route through it. Cancelling, or setting seconds to zero,
means no tag at all.

`getSource()` is read-only: it describes what happened, and what happened does not change because
somebody disagrees about how to classify it.

### What a server owner controls

`config/standards-common.toml`, `[combat]`: per-kind durations (`pvpSeconds` 12, `pveSeconds` 8,
`skillSeconds` 10), per-kind `…BlocksTeleport` (PvE **off** by default), `clearOnDeath`, and `log`.

**A duration of 0 disables that kind entirely** — a co-operative server sets `pvpSeconds = 0` and
the whole PvP branch goes quiet with no separate code path.

`standards.combat.bypass` lets a player leave a fight anyway. Op by default, and a permission
rather than an op check so staff can hold it without also holding `/stop`.

---

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

`api/combat/` follows the same promise as `api/economy/`: additive changes only, and anything that
would break a caller gets a deprecation cycle first.

Still unbuilt from the above, and knowingly so: **combat logging**. The `onLogout` behaviour — the
combat-log NPC — is where the edge cases live, and it is worth tuning against real behaviour rather
than guessing. In practice most combat logging is `/home` and `/tpa` rather than alt-F4, because
typing a command is easier and does not cost you your session, and that half is closed.

Related: [`GROUPS-API.md`](GROUPS-API.md), [`ECONOMY-API.md`](ECONOMY-API.md),
[`CHAT-API.md`](CHAT-API.md), and
[Factions ReForged's `POWER.md`](https://github.com/Sablednah/Factions-ReForged/blob/main/POWER.md),
which found the 2012 plugin blocking `/home`, `/spawn` and `/tpa` outright inside enemy territory —
a cheaper answer to the same question than a damage-driven tag, and one that wants building here
rather than there.

# Reputation — what people think of you

**Status: built 2026-09-06, shipping in Standards 1.5.0, and consumed the same day.** Chronicler
wired it within the hour — `chronicler/neoforge/compat/StandardsReputation.java` — and booted
against the jar. Verified by grepping that repo for the import rather than by remembering, which is
the rule `VANISH-API.md` earned.

**What that proves and what it does not.** Chronicler calls `isAvailable`, `get`, `adjust` and
`band`, so the *reward* path is real. `set`, `of`, `standings` and `top` have still never been
called by anybody but `/rep` and the self-test — the leaderboard half is exactly as unproven as it
was, and the first mod to use it should expect to find something.

The contract was legible enough to wire without a second round of questions, which is the only real
evidence a seam's design survived contact with somebody who did not write it.

The third seam after the economy and chat decoration, and it exists for the same reason the economy
does: **two mods wanted to grant the same fact.** Chronicler wants a reputation reward and an
availability condition on quests; StoryTeller wants `/st reward <player> rep <standing> <n>` beside
xp, karma and money. Neither should own a concept the other writes to.

## What a standing is

A **standing** is one named group's opinion of one player — `survivors`, `raiders`,
`the_hospital`. It is deliberately none of the three things it gets confused with:

- **not membership.** `api/groups` answers "are they one of us". You can be loathed by a faction
  you belong to.
- **not a moral axis.** LegendQuest's karma is one number for your whole soul. The entire point of
  reputation is that the hospital and the raiders can hold *opposite* opinions of you at once, and
  a single axis cannot express that.
- **not permission.** A standing may gate content, but it is a fact about opinion rather than a
  grant. Routing it through the permission handler would make every quest author an administrator.

## The shape

```java
Reputation.get(uuid, "the_hospital");                       // int, 0 = no opinion
Reputation.adjust(uuid, "the_hospital", +10, "saved Mara"); // returns where it LANDED
Reputation.set(uuid, "the_hospital", 50, "chapter 2");      // returns where it landed
Reputation.of(uuid);                                        // Map<String,Integer>
Reputation.standings();                                     // List<String>
Reputation.top("the_hospital", 10);                         // List<Entry(uuid, value)>
Reputation.isAvailable();
```

**It works with nobody registered.** `get` returns zero, `adjust` changes nothing and returns zero,
`standings()` is empty. A server with no provider is not broken — it is a server where nobody has an
opinion — and a quest mod should be able to call this without asking permission first.

**`adjust` returns the value rather than void**, and that is load-bearing. It is the only way a
caller can tell *"they went up by ten"* from *"they were already at the ceiling"*, which is the
difference between a message worth printing and one that reads as a lie.

## Exactly one provider holds it

Highest `priority()` wins **outright** — the economy's rule, for the economy's reason: a standing is
a *single fact*, and two stores disagreeing about whether the survivors trust you is worse than
either alone. Standards registers at `BUILTIN_PRIORITY` (−1000), so a dedicated reputation mod
displaces it without either side knowing the other exists. `reputation.preferOwn` flips that.

Note this is the opposite of the chat decorators, where **every** contributor gets a turn. The two
seams look alike and behave oppositely on purpose: a name can carry a faction tag *and* a party tag
without contradiction; a balance and a standing cannot.

**A provider owns every standing, not one of them.** Per-standing providers were the obvious
alternative and were rejected: a mod owning `the_hospital` while Standards owned the rest means
`standings()` has to merge sources that may disagree about what exists, and `/rep` has to explain
which store answered. Recorded here so it is a decision rather than an oversight.

## Standings are created on first use

There is no registry. A quest file naming `the_hospital` for the first time makes it exist;
`/rep list` shows what does. The cost is that a standing nobody holds a non-zero value for vanishes
— which is the right behaviour, because an opinion nobody holds is not a group, it is a typo.

**Names are normalised in the facade**, once: lower-cased and trimmed. `The_Hospital` and
`the_hospital` are the same group. This is in `Reputation` rather than in each provider precisely
because a provider that forgot would produce a bug visible only to whoever typed the capital.

## Storage, and why it is `SavedData`

Standards' own provider keys `player → standing → value` in `StandardsData`, not on a player
attachment — the same call as balances and for the same reason: **the useful questions are about
somebody who is not online.** `/rep top survivors` and an admin fixing a botched quest reward both
need an answer with nobody to attach anything to.

A value of zero is stored as *absence*, so a player who drifts back to neutral stops occupying space
and stops appearing with nothing to say.

## The clamp lives in the provider

Default −100…100, configurable. It is in the provider rather than the facade because a range is a
property of a *store* — a different provider may want −1000…1000, or no ceiling — and a facade that
clamped would silently overrule it.

## The event

`ReputationEvent` fires **after** a change lands, and is not cancellable. A cancellable
before-event would make a quest reward a negotiation between mods, which is the shape that has two
mods' rewards silently cancelling each other.

It carries **both** values, because almost every useful reaction is about a *threshold being
crossed* rather than about the new number:

```java
@SubscribeEvent
static void onReputation(ReputationEvent event) {
    if (event.getStanding().equals("the_hospital") && event.crossed(50)) {
        // the armoury opens, or closes
    }
}
```

A listener with only the new value has to keep its own copy of the old one to know whether anything
it cares about happened — and every listener keeping that copy is one bug per listener.

It fires even when nothing moved. `getDelta() == 0` is a correct answer to "did they cross the
threshold"; suppressing the event would instead mean every listener needs its own idea of whether a
no-op counts.

## Bands are for prose, never for logic

`reputation.bands` maps thresholds to words — `hostile`, `wary`, `neutral`, `friendly`, `trusted` —
so a message can say a word rather than a number.

```java
Reputation.band("the_hospital", 60);   // Optional[friendly]
Reputation.band("the_hospital", -999); // Optional.empty()
```

Empty is an ordinary answer, not a failure: a server may configure no bands at all, so a caller
needs something to say without one, and the number always works.

**Never branch on it.** A consumer keying off `"friendly"` breaks the day an owner renames it; a
quest that needs a threshold should say the threshold, and `ReputationEvent.crossed(int)` exists for
exactly that. The number is the fact; the band is a label for humans. It was left out of the API
entirely at first for that reason, and added on request for the case it is actually good at — *"the
survivors now consider you friendly"* on an action bar when `crossed(n)` fires.

It is a `default` method on the provider, like `Economy.format`, because how a store prefers to
describe its own numbers is the store's business — and defaulted to empty so a provider with no
opinion about words does not have to say so.

`reputation.standingBands` overrides the ladder for one standing, as `standing/threshold:name`. A
standing named there takes its bands **entirely** from those lines rather than merging with the
defaults: merging would mean an owner could add a band but never remove one, and a half-overridden
ladder reads as a bug rather than as a setting.

## Commands

Every one at its plain name, per decision 12.

| Command | Node | Default |
|---|---|---|
| `/rep` | `standards.rep` | everyone |
| `/rep <player>` | `standards.rep.others` | ops — standings can be a story spoiler |
| `/rep list` | `standards.rep` | everyone |
| `/rep top <standing>` | `standards.rep.top` | everyone |
| `/rep set <player> <standing> <n>` | `standards.rep.admin` | ops |
| `/rep add <player> <standing> <n>` | `standards.rep.admin` | ops |

⚠ **Standing names are not `word()`.** They are typed into quest files by hand and will contain
punctuation — `st.marys`, `hospital-north`, or a space. Brigadier's `word()` accepts letters, digits
and `_.+-` and nothing else, and this repository has now paid for that **four** separate times: a
permission wildcard, `/nick &cBob`, a faction called "Lantern Vale", and an argument in
`/f money pay`. So the standing is a `greedyString` where it is last, and `string()` (bare word, or
quoted) where a number follows it. The self-test parses the real dispatcher, including a quoted name
with a space, because every one of those four bugs tested the *logic* while nothing had ever managed
to enter the input.

`/rep set` and `/rep add` report where the value **landed**, not what was asked for — a clamp that
silently ate half a reward is exactly what an admin needs told at the moment it happens.

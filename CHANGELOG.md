# Changelog

## 1.1.1

### Added

- **`Claims.griefAllowed(level, pos)`** — may a *non-player* modify blocks here? A creeper
  cratering a wall, a zombie chewing a door, another mod's griefing mob.

  Asked for by ZombieMod, which had no player to pass to `mayModify` — membership, trust lists and
  admin bypass all mean something for a person and nothing whatever for a zombie — and was
  therefore deriving the rule from `owner()`. That is exactly the duplication `GROUPS-API.md`
  warns against: a land mod should get to say that mobs may grief a war zone but not a home claim,
  and a mob mod should not have to decide that on its behalf.

  Defaults to precisely what a consumer would have derived, so adopting it changes no behaviour
  until a provider says otherwise.

  **It fails closed, alone among these seams.** Everywhere else a broken provider permits, on the
  grounds that wrongly permitting a build can be undone. Here wrongly permitting means a mob eats
  somebody's base while nobody is watching, and that cannot — so a provider that throws stops the
  griefing rather than licensing it.

  *Diagnostic worth knowing: mobs mysteriously refusing to break anything means a claims provider
  is throwing, not that the mob mod is broken.*

## 1.1.0

**The combat API**, plus the seam it turned out to need. Both were built because two other mods
asked for them the same day, which is the only reason to build an API at all.

### Added

- **`api.combat` — combat tagging.** Being hit by a player puts you in a fight, and while it lasts
  the escape hatches are closed. A teleport out of a fight is not a clever play; it is the fight
  not happening, and whoever was winning has no recourse.

  Three rules, each invisible until it is wrong. **An attacker starts a tag, not damage** — fall,
  drowning, cactus, fire and freezing never tag anybody, which is what stops a player trapped in a
  protected claim being sealed in by the very feature meant to protect them. **Tags extend, never
  overwrite** — twelve seconds of PvP then a zombie for eight is still twelve, or a shorter tag
  would rescue exactly the person fleeing. **Pets are directional** — their wolf biting you is
  them fighting you through a proxy, but you hitting their wolf is not, so nobody can lock you in
  combat by shoving a pet in front of you.

  Per-kind durations and per-kind teleport blocking. `pveBlocksTeleport` is **off** by default: on
  a peaceful server a skeleton must not block `/home`, while a player hitting you must.
  `standards.combat.bypass` for staff, a permission rather than an op check.

- **`api.combat.Harm` — may this player harm that one?** Asked once, centrally, for player-on-player
  damage, so a mod that only deals damage needs no code at all. The explicit call is for hostile
  things that are *not* damage — a curse, a snare, a summon aimed at somebody — which cancelling a
  damage event never stopped. Before this, a faction that had declared itself peaceful was peaceful
  against arrows and defenceless against spells.

  **Any veto denies**, unlike every other seam here, because a refusal is a promise rather than a
  bid. A broken provider fails open: a mod with a bug must not be able to switch combat off for a
  whole server.

- **`Claims.pvpAllowed(level, pos)`** — the *place* half of the same question, for safe zones and
  war zones. Standards checks both ends of a shot, or a safe zone is one bowshot from useless.

- **`Combat.playerBehind(damageSource)`**, public on purpose: resolving who was really behind a hit
  through arrows and pets is something several mods need, and two implementations of it eventually
  disagree in a way nobody can reproduce.

### Notes

LegendQuest builds against this, and Factions registers `factions:pvp` on it.

## 1.0.1

Everything here came out of walking through every command by hand with two and then three real
clients. None of it was reachable by the self-test, which is excellent at "does this compute the
right answer" and blind to "has anything ever called it".

### Added

- **`/back list`.** `/back` is a stack, not a bookmark: every teleport pushes an entry and `/back`
  pops the newest, so two idle `/jump`s after a `/home` put two rungs between you and the place you
  meant. `/back <n>` was the answer and was unusable, because using it meant counting in your head.
  Each row now carries the dimension, the coordinates, **how far away it is** — "the one 40 blocks
  away" is a thought somebody has, "the one at -122, 71, 908" is not — and **the command that made
  it**.
- **A reason on `/pay`.** `/pay Steve 500 half of what we dug`. Money that turns up with no
  explanation is money the recipient treats as a bug, and it is carried into the mailbox when they
  are offline, so a payment made on Tuesday still explains itself on Friday.
- **A logo**, at last.

### Changed

- **Teleport trail entries are labelled by the command that made them**, taken at the dispatcher
  rather than passed as a parameter. Any mod that teleports a player during a command gets a
  labelled entry without knowing this exists — Factions' `/f home` shows up as `/f home` and
  Factions has never heard of the mechanism.
- **`/group disband` is now its own command, and `/group leave` refuses to do it.**

- **`/group disband` is now its own command, and `/group leave` refuses to do it.** An owner
  An owner walking out took every member's shared homes with them, on a word one keystroke away
  from the one that means "I am done with this". Factions already refused the same move; groups
  now agree with it. An owner *alone* in a group still just leaves — friction there protects
  nobody, and would only mean learning a second command to undo a mistake.

### Fixed

- **An unedited message now follows the mod on upgrade.** `messages.known` recorded only *which*
  keys had been offered, so a wording fix in a new version reached servers that had never run the
  old one and nobody else — the file kept the old text forever with nothing to suggest why. It now
  records the value shipped alongside each key, which is what lets an upgrade tell an owner's edit
  from a line nobody has touched: untouched lines are rewritten, edited ones are left alone.
  *Upgrading installs have no record of what they were shipped, so the first start after this
  change only takes the record; refreshing begins from the next one.*
- **A group name or tag could carry colour codes into everyone's chat.** They are printed on other
  people's screens by the chat decorator, so they are untrusted input in the same way a chat line
  is — arriving through a door nobody was watching. Codes are now stripped where the group is
  stored, so every route in is covered. `&k` was the sharp end: an obfuscated tag is unreadable
  noise on every line its members speak, and `&k&l` measured four of the five allowed characters
  while displaying none.

## 1.0.0 — first release

Server essentials for NeoForge 1.21.11, and an economy other mods can drive. **Everything works on
an unmodded client** — no client mod, no resource pack, nothing to install for players.

### Why it exists

Two complaints, taken seriously:

- **FTB Essentials has no `/top`.**
- **Both it and EssentialsX make `/fly` and `/god` pure toggles**, with no explicit `on` / `off` —
  which makes them unusable from a command block, a datapack, or another mod's skill. A toggle in
  that position is a coin flip that grounds the player mid-air half the time.

So every switch in this mod takes `on` / `off` / `toggle`, with or without a target:

```
/fly            /fly on            /fly @a on
/god Steve      /god Steve off
```

That one decision is why the rest of the mod looks the way it does.

### What you get

**Movement** — `/top`, `/bottom`, `/jump`, `/back` with a multi-step trail, `/spawn`, `/setspawn`,
`/playerspawn`. Warmups with a ticking countdown, cancel-on-move, cancel-on-damage, and a
safe-landing search that refuses rather than dropping you in lava.

**Homes and warps** — per-player limits granted by permission node, an overwrite escape hatch when
you hit the ceiling, and nothing deleted when an admin lowers a limit.

**Teleport requests** — `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpalist`, with clickable
Accept and Deny buttons and, crucially, **narration to both parties**: told when it is accepted,
told when they land, and told *why* when it is cancelled.

**Economy** — `/balance`, `/pay`, `/baltop`, `/eco`, with a provider API. Standards registers at a
negative priority so a dedicated economy mod displaces it without either side knowing.

**Groups** — a lightweight built-in group system with shared homes, chat tags and teleport relief
between members, published through an API other mods can provide instead.

**The rest** — kits defined in-game by equipping yourself, mail, `/msg` with ignores and social
spy, mutes and tempbans, `/vanish`, `/invsee`, portable workstations, AFK with an idle timer,
`/smite`.

### Things worth knowing

**Every string is yours.** `config/standards/messages.yml` holds every player-facing message, and
`{term.*}` keys let you re-skin vocabulary wholesale — set `term.balance` to "credits" and every
money message follows. New keys merge in on upgrade without touching your edits.

**Commands you turn off are unregistered, not refused.** A greyed-out tab-complete entry for
something the server will never run is a lie the player discovers by trying it.

**`/vanish` is genuinely hidden** — off the tab list, unhittable by arrows, unpushable, invisible
to `/msg` and absent from tab-completion. The world's reactions stay visible on purpose: a chest
you open still animates.

**Decorated chat lines are not signed.** If a chat decorator is registered, those lines are
composed server-side and sent as system messages, so client-side reporting and blocking do not
apply to them. Undecorated chat is untouched. See `CHAT-API.md`.

### With no permissions mod

Everything works, because Standards asks NeoForge's `PermissionAPI` rather than any particular
mod. But NeoForge's default handler cannot *grant* anything, so the five portable workstations —
which default to "nobody" on purpose — never appear at all. `stationAccess` and
`backOnDeathAccess` let such a server say who they are for. A permissions mod overrides both.

### For other mods

Five seams, all soft dependencies: `Economy`, `Chat` decorators and routers, `PlayerSwitches`,
`Groups` and `Claims`. Plus `Lang.contribute()`, so a mod's strings land in the same
`messages.yml` — one catalogue for the whole server.

### How it was tested

326 self-test checks run on every server start, a 150-item hand walkthrough by two and sometimes
three players, and an RCON battery of 57 live assertions.

Fifteen real bugs came out of the walkthrough that the self-tests could not see — among them
`/top` landing on the Nether roof, `/back` being impossible for a flying player, `/me` bypassing
mutes, colour-code injection through player text, and the self-test leaking its own fixtures into
the live server.

# Changelog

## Unreleased

### Added

- **`/nick`, `/realname` and `/whois`.** A nickname replaces your name **in chat only** — the tab
  list and the nameplate keep the real one, and that is the design rather than a limitation. It is
  what keeps a nickname a flourish instead of a disguise, and it means "who is that" is answerable
  by anyone who glances at tab without knowing a command exists. Nicknames wear a `~` by default.

  **A nickname may not be another player's real name, nor another player's nickname**, checked
  against every name the server has seen rather than who is online — impersonating somebody who is
  asleep is the version that works, since they are not there to object. The rule lives in the store,
  not the command, so an admin setting one for somebody else goes through the same door.

  `/realname` is open to **everyone** on purpose: if only staff can tell who somebody is, the
  feature is a disguise. Colour codes need `standards.nick.color`; without it they are stripped
  rather than refused, because somebody who pasted a code they did not know about wanted the word.

- **`/i` and `/item`** — give yourself a stack. *This reverses a NO in `COMMANDS.md`*, which had
  reasoned that vanilla's `/give` covers it and "the short alias is the only draw". The short alias
  **is** the draw, and in a mod whose stated thesis is that muscle memory is the product, that is
  sufficient — the owner was still reaching for `/i` months after the verdict. A bare `/i stone`
  gives a full stack, capped at what the item really stacks to. Op-gated; vanilla's `/give` is
  untouched.

### Fixed

- **Colour codes in a nickname could not be typed at all.** Brigadier's `word()` accepts letters,
  digits and `_.+-` and stops dead at `&`, so `standards.nick.color` gated a feature that was
  unreachable — the second time this exact trap has cost a feature, after the permission wildcards
  and `*`. Both are now `greedyString` split in code. `CLAUDE.md` records it as a rule: any
  argument that can contain punctuation must not be `word()`.

## 1.3.0

### Added

- **A built-in permission handler**, for servers with no permissions mod. One line in
  `neoforge-server.toml` — `permissionHandler = "standards:permissions"` — and `/rank` appears:
  groups with inheritance, per-player grants, `standards.home.*` wildcards, an explicit deny that
  beats everything, and a default group everybody is in without being put there.

  It is **one more handler, not a Vault.** NeoForge's `PermissionAPI` is already the facade and the
  owner picks the active handler by name, so this is dormant unless chosen and a server running
  LuckPerms is untouched. Nothing in Standards' own config turns it on; a second switch beside
  NeoForge's would be the arbitration layer this deliberately avoids.

  The gap it fills is that NeoForge's default handler answers every question with the node's own
  default, so on a server with no permissions mod **you cannot grant anybody anything** —
  `standards.fly` is op-or-nothing, and a builder cannot have `/craft` without also getting
  `/stop`.

  `/rank user <player> info` and `/rank check <player> <node>` say **which rule answered**, not
  just yes or no: `standards.home.others = yes (from donor, via standards.home.*)`. Every hour lost
  to a permissions system is spent asking why a player has something.

- **Permission groups are groups.** They are published through the groups API as `standards:role`,
  so putting somebody in `moderator` gets them their nodes, a chat tag (add `standards:role` to
  `chat.groupTagKinds`) and visibility to any other mod, in one edit with no second list to keep in
  sync. LuckPerms cannot do that half — its groups are a permissions concept and nothing else on
  the server can ask about them — and it is the reason this exists rather than being a smaller copy
  of LuckPerms.

- `/standards permissions` — which handler is actually answering. The first thing to check when a
  gated command has quietly vanished for everybody, because a permissions manager whose storage
  failed to start answers false to everything and the whole mod looks broken with nothing on screen
  to say why.

### Fixed

- **A newly granted command rendered red and would not tab-complete** until the player
  reconnected. The grant itself always worked — the server re-checks permissions on every command
  it parses — but the client holds a command tree sent once on join, so a player told "you have
  `/craft` now" typed it, saw *"unknown command"* in red, and reported it broken. Almost nobody
  presses enter through a red line. `/rank` now resends the tree on every edit.

  One residue is a client behaviour and cannot be reached from a server: a line already sitting in
  the chat box keeps its old colouring until you touch it. Type a character and it repaints.

### Notes

- `/rank` and `/perm` are the same tree. **`/perm` is not reliably ours**: LuckPerms claims it as
  an alias of `/luckperms`, so on a server carrying both, a bare `/perm` runs LuckPerms' help while
  our subcommands still work. `/rank` is claimed by nothing.

- The self-test is at **403 checks**, including the resolution order in both directions, the
  wildcard command forms, and a round trip through `PermissionAPI` itself. Beyond that it was
  driven with two clients against the dev server: a genuine non-op was refused an op-gated node,
  kept an everyone-node, was granted `standards.craft` — which defaults to *nobody*, so not even an
  operator has it ungranted — and got a crafting table.

## 1.2.0

### Added

- **Minecraft 26.1.2 and 26.2**, on their own branches — `mc26.1` and `mc26.2` beside `main` for
  1.21.11. All three carry the same 362 self-test checks and the same behaviour; see
  `CROSS-VERSION.md` for what each version drop actually moved.

- **`api/vanish/`** — `Vanish` and `VanishEvent`, so a mod that draws something *on* a player can
  take it down when they vanish.

  A `/vanish`ed player kept their LegendQuest nameplate: a floating name hanging over nobody, which
  gives a vanish away as completely as being seen would. Standards cannot fix that itself — it
  hides a player by answering `false` from `broadcastToPlayer`, which covers the player and nothing
  attached to them, and "hide entities near a vanished player" would catch other people's holograms
  and miss a plate that tracks from a distance. Which entities *belong to* a player is only
  answerable by whoever spawned them.

  Both halves matter: ask `Vanish.isVanished` when you create the decoration, for a player who logs
  in already hidden, **and** listen for `VanishEvent`, for one who vanishes mid-session. A
  spawn-time check alone is exactly what leaves the nameplate hanging. `Vanish.hiddenFrom` is the
  per-viewer form for staff holding `standards.vanish.see`, and `Vanish.anyVanished()` is a
  one-field-read bail-out for per-tick callers. See `VANISH-API.md`.

- **A saved-data migration for 26.1 and later**, and a **line in the log saying what came off
  disk** — `Standards: loaded 14 home(s) across 2 player(s), …`.

  26.1 changed two things at once. `SavedDataType`'s id became an `Identifier`, so
  `standards_kits.dat` became `standards/kits.dat` — *and* per-dimension data left the world root
  for `world/dimensions/minecraft/overworld/data/`. Fixing only the filename writes a perfect copy
  into the world-global folder, beside the scoreboard and the weather, where nothing reads it.
  Nothing errors either way, because a missing saved-data file is not an error, it is a new world.
  Every home, warp, kit, mailbox, mute, balance and group would have vanished silently.

  The boot line exists because an empty store and a store that failed to load are indistinguishable.
  A write is not a success until something reads it back.

### Fixed

- **The required NeoForge version is no longer whichever build it was compiled on.** It was derived
  from `neo_version`, so building against a newer NeoForge to match a test client quietly declared
  `[26.2.0.72,)` and locked out everyone below it. `neo_version_min` now carries the floor and drops
  the trailing build counter, which is the only part of a NeoForge version that says nothing about
  compatibility — `[1.21.11]` gets `[21.11,)`, `[26.1,26.2)` gets `[26.1,)`, `[26.2,26.3)` gets
  `[26.2,)`.

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

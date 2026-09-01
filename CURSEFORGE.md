![SableCraft Standards](https://media.forgecdn.net/attachments/description/null/description_36551562-8a32-420e-bae3-f409214706d5.png)

# SableCraft Standards — the essentials, done properly

_Do you have Standards?_

**Every server command you end up needing, and an economy other mods can drive.** `/fly`, `/god`,
`/home`, `/warp`, `/tpa`, `/back`, `/top`, `/kit`, `/vanish`, `/invsee`, mail, mutes, bans — the set
every server installs something for, built once and built to be used by other mods rather than
merely alongside them.

**Your players do not need to install anything.** Every command works for an unmodified client off
the Mojang launcher: no client mod, no resource pack, nothing to download. The networking channel
exists and is deliberately empty.

---

## Yet another essentials mod?

Yes. That is the joke in the name — see [xkcd 927](https://xkcd.com/927/) — but it exists for two
specific, fixable complaints:

- **FTB Essentials has no `/top`.**
- **Both it and EssentialsX make `/fly` and `/god` pure toggles**, with no explicit `on` or `off`.

That second one is not a nitpick. A toggle cannot be driven from a command block, a datapack or
another mod's skill: a spell that wants flight *on* for twenty seconds and *off* afterwards has to
guess, and gets it wrong half the time — grounding the player mid-air. So **every switch here takes
`on` / `off` / `toggle`, with or without a target**:

```
/fly            /fly on            /fly @a on
/god Steve      /god Steve off
```

A human typing `/fly` still gets a toggle. Everything else gets an answer it can rely on. **The
whole mod follows from taking those two complaints seriously.**

## A few that are not what you expect

- **`/top` scans, it does not read the heightmap** — so it works in the Nether and from inside a
  cave, and it stops at a bedrock box rather than blaming you for the shape of the world.
- **`/tpa` narrates itself at both ends.** With a warmup set, the requester gets a ticking
  action-bar countdown the moment they are accepted; the acceptor is told when they arrive, or *why*
  they did not. Prompts carry clickable **[Accept]** / **[Deny]** buttons that work on a vanilla
  client. The classic bug this fixes: five seconds of nothing observable, so both people re-run the
  command.
- **`/back` is a stack, not a bookmark.** Every teleport pushes an entry; `/back <n>` reaches
  further, and **`/back list`** shows what is actually on it — with the distance and *the command
  that made each stop*. Other mods' teleports are labelled too, without those mods doing anything.
- **`/vanish` genuinely hides you** — unpaired from other players' entity trackers, off the tab list,
  ignored by mob AI. Not a packet trick. Item pickup is suppressed by default, because an arrow
  vanishing with nobody standing there gives you away as surely as being seen.
- **`/pay` takes a reason** — `/pay Steve 500 half of what we dug` — carried into the mailbox if
  they are offline, so a payment made on Tuesday still explains itself on Friday.
- **Portable workbenches are denied to everyone by default, operators included.** That is the
  design: a crafting table you can open anywhere is an ability to be granted, not a utility to
  assume.

## The systems underneath

| | |
|---|---|
| **Economy** | A built-in ledger, and a **provider API** so a dedicated economy mod displaces it without either side knowing the other exists. Balances live in save data, so `/eco give` works on a sleeping player and `/baltop` answers about everyone. |
| **Homes, warps and spawn** | Per-player home limits by permission node, named warps, a server spawn, safe-landing searches that will not drop you in lava or drown you. |
| **Teleports** | Warmups with a live countdown, cooldowns, cancel-on-damage, cancel-on-move — all of it available to other mods, so anything that teleports a player inherits the lot. |
| **Groups** | A lightweight built-in group system — shared homes, chat tags, group teleports — *and* a seam other mods register into, so a faction and a party can coexist without either arbitrating. |
| **Chat** | Name decorators contributed by several mods at once and ordered by closeness to the name, plus a channel router that cannot be used to sidestep a mute. |
| **Kits** | `/setkit <name> armour\|hotbar\|inventory\|all` captures what you are wearing and carrying — kits are made by equipping yourself, not by writing item ids into a file. |
| **Moderation** | Mutes, temp-mutes, bans, temp-bans with the reason shown on reconnect, `/invsee`, `/socialspy`, `/ignore` that actually hides public chat. |
| **Away** | `/afk` by command and by timer, cleared by moving or speaking — including in another mod's chat channel. |

## Every string is yours

Everything a player can see lives in **`config/standards/messages.yml`**, written on first run.
Not vanilla translation keys — a stock client does not carry our lang file and would see raw keys —
so this is resolved server-side and works everywhere.

`{term.*}` keys re-skin vocabulary wholesale: set `term.balance` to *credits* and every message
that mentions money follows, without editing a hundred lines. `&` colour codes work throughout,
including `&#RRGGBB` hex.

**The welcome message knows who it is greeting.** `/motd`, `/rules` and `/info` take `{player}`
(their nickname if they have one), `{name}`, `{rank}`, `{playtime}`, `{world}`, `{online}` and
`{max}`:

```yaml
msg.motd.1: "&7Welcome back, &f{player}&7. You are &f{rank}&7."
msg.motd.2: "&7{online} of {max} online. You have played for {playtime}."
```

A placeholder that does not exist is reported in the server log rather than shown to a player, so a
typo is findable instead of embarrassing.

**Upgrades keep your edits.** New keys are appended under a heading so "what is new in this
version" is answerable by scrolling to the bottom, and a message you have *not* edited is quietly
updated to the current wording while anything you have touched is left exactly alone.

## Permissions

Every gate is a permission node, never a hardcoded op check — so a donor rank can have ten homes and
a builder can have `/craft` without anybody being handed `/stop`.

Standards asks NeoForge's own `PermissionAPI`, so whatever you already run answers those questions
and nothing here gets in its way. **LuckPerms works out of the box**, with no configuration on
either side.

### Ranks without installing a permissions mod

With nothing installed, NeoForge answers every question with the node's own default — which means
you cannot grant anybody anything. A trusted regular cannot have flight, a donor cannot have ten
homes, and a builder cannot have `/craft` without being made an operator.

So there is a handler for exactly that server. One line in `neoforge-server.toml`:

```toml
permissionHandler = "standards:permissions"
```

and `/rank` appears:

```
/rank group donor create
/rank group donor set standards.home.limit.10 true
/rank group moderator parent add donor      inheritance
/rank user Steve group add donor            works offline
/rank check Steve standards.home.limit.10
```

Groups with inheritance, per-player grants, `standards.*` wildcards, an explicit deny that beats
everything, and a default group everybody is in without being put there.

**Dormant unless you pick it.** NeoForge decides which handler is active — leave that line alone and
this one never runs, and `/rank` is not even registered. Switching either way is that one line, with
nothing to import and nothing to migrate off.

### It tells you *why*

Every hour lost to a permissions system is spent asking why a player has something, and yes-or-no
cannot answer that:

```
> /rank check Steve standards.home.others
  standards.home.others = yes (from donor, via standards.home.*)
> /rank check Steve standards.god
  standards.god = no (nothing set — the node's own default)
```

That second line matters as much as the first. A bare "no" reads as a rule somebody wrote, and sends
you looking for one that does not exist.

### A rank is a group, not just a bundle of nodes

Put somebody in `moderator` and they get their permission nodes, a **chat tag**, and **visibility to
every other mod on the server** — one edit, no second list to keep in sync. A faction mod, a party
mod or a quest mod can ask who your moderators are, because ranks are published through the same
groups API everything else here uses.

That is the reason this exists. Permissions on their own are a solved problem; ranks that the rest
of the server can actually see are not.

### Players can climb it on their own

```toml
startingGroup = "guest"
promotions = ["guest -> regular after 24h and 2h played"]
```

New players land in `guest` and move up by themselves. Two clocks, because they answer different
questions: **real time** asks for patience — a few minutes is enough to lose the fly-by griefer who
is on another server by now — and **played time** asks them to have actually done something.

Played time counts only while somebody is **online and not away**. Minecraft's own statistic happily
counts a player idling in a corner all night, which is exactly the promotion you did not want to
give. Give both and both must pass.

`/rank` on its own is open to everyone and shows what you are and what is left: *"next: regular —
needs 18m more and 45m more played"*. A ladder a player cannot see is one that happens to them.

### What it is not

No per-world contexts, no temporary nodes, no promotion tracks, no weights, no SQL backend, no web
editor. This is **enough to run a server**, and honest about the line: if you need any of that, a
dedicated permissions mod is a better tool and switching to one costs a single config line.

## For other mods

Six seams, all soft dependencies — add a `compileOnly` and Standards can be absent at runtime:

| API | For |
|---|---|
| `api.economy` | spending money, or *being* the economy |
| `api.chat` | name prefixes and suffixes from several mods at once; chat channels that respect mutes |
| `api.groups` | group membership by kind, "who owns this chunk", whether PvP is allowed there, and whether **mobs** may break blocks there. Standards' own permission ranks publish here too, as `standards:role` |
| `api.PlayerSwitches` / `api.Stations` | driving `/fly`, `/god`, `/vanish` and the workstations from code |
| `api.combat` | combat tagging, resolving who was really behind a hit, and whether one player may harm another at all |
| `api.vanish` | whether a player is hidden, so a mod drawing a nameplate or a health bar on them can take it down — a floating name over nobody gives a vanish away as completely as being seen |

The switches API exists because a skill granting flight should not have to build a command string
and hope: the skill is already the authority, so it calls in directly and skips the permission check
that gates the typed command.

**Combat tagging** deserves a line of its own, because it is what stops `/home` being an escape
hatch. Being hit by a player closes the escape routes while the tag lasts — and three rules make it
behave:

- **An attacker starts a tag, not damage.** Fall, drowning, fire and freezing never tag anybody,
  which is what stops a player trapped by their own bad luck being sealed in by the very feature
  meant to protect them.
- **Tags extend, they never overwrite.** Twelve seconds of PvP then a zombie for eight is still
  twelve, or a shorter tag would rescue exactly the person fleeing.
- **Pets are directional.** Somebody's wolf biting you is them fighting you through a proxy; you
  hitting their wolf is not — so nobody can lock you in combat by shoving a pet in front of you.

And `Harm.forbidden(a, b)` answers whether one player may harm another *at all*, so a hostile
**skill** — a curse, a snare, a summon — is refused for the same reasons a sword is. Player-on-player
damage is gated centrally, so a mod that only deals damage needs no code whatever.

**[Factions ReForged](https://www.curseforge.com/minecraft/mc-mods/factions-reforged)** is built
entirely on these — claims, groups, chat and economy — and is the proof they work from the outside.

## Requirements

Standards is built for three Minecraft lines. **Take the jar that names your version** — every
file carries it, so `standards-1.4.0+mc26.1.2.jar` is the 26.1 one.

| Minecraft | NeoForge | Java | Jar |
|---|---|---|---|
| 1.21.11 | 21.11+ | **21** | `…+mc1.21.11.jar` |
| 26.1.x | 26.1+ | **25** | `…+mc26.1.2.jar` |
| 26.2.x | 26.2+ | **25** | `…+mc26.2.jar` |

⚠ **26.x needs Java 25, not 21.** That is Minecraft's requirement rather than ours, and it is the
one thing here that will stop a server booting — with an error that does not obviously say so. If
you are moving up from 1.21.11, change the Java your server launches with at the same time.

Built and self-tested against NeoForge 21.11.42, 26.1.2.95 and 26.2.0.72. The floors above are what
the jars actually declare, so a later build of the same line is fine.

**Install on the server. That is all.** No dependencies. Nothing for your players to install, and
nothing that stops them joining from a stock client.

## Credits and licence

MIT. All original work.

Inspired by **Essentials** / **EssentialsX** and **FTB Essentials** — read for intent, then built
better. No code is taken from either.

By **Sablednah**. Source, issue tracker and full design notes on GitHub.

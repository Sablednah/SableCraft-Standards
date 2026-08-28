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

**Upgrades keep your edits.** New keys are appended under a heading so "what is new in this
version" is answerable by scrolling to the bottom, and a message you have *not* edited is quietly
updated to the current wording while anything you have touched is left exactly alone.

## Permissions, and one thing to know first

Standards uses NeoForge's own `PermissionAPI`, which means **LuckPerms works out of the box** — and
so does having no permissions mod at all. Verified both ways: a fresh server with nothing installed
gives ordinary players the everyone-commands and operators the op-gated ones, and a LuckPerms grant
applies live without a restart.

Every gate is a node, never a hardcoded op check, so a donor rank can have ten homes and a builder
rank can have `standards.craft` without anybody being handed `/stop`.

## For other mods

Five seams, all soft dependencies — add a `compileOnly` and Standards can be absent at runtime:

| API | For |
|---|---|
| `api.economy` | spending money, or *being* the economy |
| `api.chat` | name prefixes and suffixes from several mods at once; chat channels that respect mutes |
| `api.groups` | group membership by kind, "who owns this chunk", whether PvP is allowed there, and whether **mobs** may break blocks there |
| `api.PlayerSwitches` / `api.Stations` | driving `/fly`, `/god`, `/vanish` and the workstations from code |
| `api.combat` | combat tagging, resolving who was really behind a hit, and whether one player may harm another at all |

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

| Minecraft | NeoForge | Java |
|---|---|---|
| 1.21.11 | 21.11.42+ | 21 |

**Install on the server. That is all.** No dependencies. Nothing for your players to install, and
nothing that stops them joining from a stock client.

## Credits and licence

MIT. All original work.

Inspired by **Essentials** / **EssentialsX** and **FTB Essentials** — read for intent, then built
better. No code is taken from either.

By **Sablednah**. Source, issue tracker and full design notes on GitHub.

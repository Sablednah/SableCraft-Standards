# Standards — commands reference

Source of truth: `src/main/java/com/sablednah/standards/neoforge/StandardsCommands.java` and
`StandardsPermissions.java`.

Two things worth knowing before the list.

**Every switch takes `on` / `off` / `toggle`, with or without a target.** `/fly`, `/god`, `/vanish`,
`/tptoggle`, `/msgtoggle`, `/socialspy` and anything added later are all built the same way, so
`/fly`, `/fly on`, `/fly @a on` and `/god Steve off` all work. That is the whole reason this mod
exists — a toggle cannot be driven from a command block, a datapack or another mod's skill.

**A command turned off in config is not registered at all.** It does not appear in tab-complete and
answers "unknown command", rather than appearing and then refusing. A greyed-out entry for something
the server will never run is a lie the player discovers by trying it.

**Every command lives at its plain name** — `/home`, never `/standards home`. `/standards` is for
administering the mod itself and nothing else lives under it.

---

## Permission defaults

Nodes are all `standards.<node>`. Standards uses NeoForge's `PermissionAPI`, so **LuckPerms works
out of the box and so does having no permissions mod at all** — with nothing installed, everyone
gets the everyone-nodes and operators get the op-gated ones.

| Default | Nodes |
|---|---|
| **Everyone** | `top` `back` `home` `sethome` `delhome` `spawn` `gc` `kit` `mail` `afk` `msg` `tpa` `tpahere` `tptoggle` `warp` `balance` `baltop` `pay` `group` |
| **Operators** | `combat.bypass` `fly` `god` `vanish` `smite` `jump` `bottom` `heal` `feed` `rest` `speed` `setspawn` `setkit` `setwarp` `tpoffline` `socialspy` `tempban` `mute` `invsee` `eco` `admin`, every `.others` variant, `msg.override` `tpa.override` `afk.exempt` `home.limit.unlimited` `teleport.instant` `teleport.nocooldown` |
| **Nobody, including operators** | `craft` `anvil` `grindstone` `enderchest` `trashcan` `back.ondeath` |

That last row is deliberate. **A workbench you can open anywhere is an ability to be granted, not a
utility to assume** — a builder rank gets `standards.craft`, a blacksmith class gets
`standards.anvil` from a skill, and nobody gets them by being an operator. `back.ondeath` is the
same shape: returning to your corpse is a choice a server makes, not a default.

**Home limits are numbered nodes.** `standards.home.limit.5` gives five, `standards.home.limit.10`
gives ten, highest wins, and `home.limit.unlimited` beats them all. Servers without a permissions
mod fall back to `homes.defaultLimit`.

---

## Moving

### `/top` · `/up`

Straight up to the first place it is safe to stand. **Scanned, not read off the heightmap** — a
heightmap answers "the highest non-air block in this column", which in the Nether is the bedrock
roof and in a cave means the surface far above you. It stops at bedrock, because some servers box
builds in it, and says so rather than blaming you for the shape of the world.

### `/bottom` · `/down`

The mirror. The lowest safe standing spot beneath you.

### `/jump` · `/j`

To wherever you are looking, if it is somewhere you can stand.

### `/back [n]` · `/back list`

Return to where you were before your last teleport.

**`/back` is a stack, not a bookmark.** Every teleport pushes an entry and `/back` pops the newest,
so two idle `/jump`s after a `/home` put two rungs between you and the place you meant — `/back 3`
is the answer, and **`/back list`** is how you know that without counting in your head.

The list shows the dimension, coordinates, **how far away it is**, and **the command that made each
stop**. Other mods' teleports are labelled too, without those mods doing anything: the label is
taken at the dispatcher.

Depth is `backHistory` (default 5). `back.ondeath` is off by default; without it, dying does not
push your corpse onto the trail.

### `/spawn` · `/setspawn` · `/playerspawn`

`/spawn` goes to the server spawn, `/setspawn` sets it, `/playerspawn` goes to your bed or
respawn anchor.

## Homes and warps

### `/home [name]` · `/homes` · `/sethome <name>` · `/delhome <name>`

With exactly one home, `/home` needs no name. With several it asks which rather than guessing.
`/sethome` over an existing name refuses and tells you how to overwrite, because losing a base to a
typo is not recoverable.

Limits come from numbered permission nodes; see above.

### `/warp <name>` · `/warps` · `/setwarp <name>` · `/delwarp <name>`

Server-wide named destinations. `/setwarp` is op by default.

## Teleport requests

### `/tpa <player>` · `/tpahere <player>` · `/tpaccept` · `/tpdeny` · `/tpacancel` · `/tpalist`

Aliases: `/call` for `/tpa`, `/tpyes` and `/tpno` for accept and deny.

**`/tpahere` accepted moves the acceptor**, not the requester. That direction is invisible until two
real people try it, at which point one of them is somewhere they never asked to be.

**A warmed teleport narrates itself to everyone waiting on it.** The traveller gets a ticking
action-bar countdown — the action bar rather than chat, because five identical messages would bury a
conversation. The other party is told the moment it is accepted, when it lands, and if it is
cancelled **with the reason**, because "they did not make it" invites an identical second attempt.

Prompts carry clickable **[Accept]** and **[Deny]** buttons that work on a vanilla client.

### `/tptoggle [on|off|toggle]`

Refuse incoming requests.

### `/tpoffline <player>`

To where a player logged out. Op.

## Switches

### `/fly [player] [on|off|toggle]` · `/god [player] [on|off|toggle]`

**Flight is granted as an attribute modifier**, not by writing the ability flag — that flag is one
boolean many mods want to own, and whoever writes `false` last takes flight from everyone else's
feature. The ability flag is kept as a derived cache purely so a vanilla client is told the truth.

Re-applied on respawn, dimension change and game-mode change, all three of which rebuild ability
flags underneath you.

### `/vanish` · `/v [player] [on|off|toggle]`

Genuinely hidden — unpaired from other players' entity trackers, off the tab list, ignored by mob
AI. Not a packet trick.

The world's *reactions* to you are not suppressed: a chest you open still animates, deliberately,
because suppressing that means suppressing the sound and the particles and every second-order effect
after it. **Item pickup is on the other side of that line** and is suppressed by default — an arrow
vanishing with nobody standing there gives you away as surely as being seen, and it stops hidden
staff quietly collecting the loot from a fight they were only watching.

### `/speed [walk|fly] <n> [player]`

Capped, and it refuses over the cap rather than silently clamping.

## Self-care

`/heal` · `/feed` · `/eat` · `/rest` — health, hunger and the phantom timer. All op by default, all
with `.others` variants that tell the target who did it.

`/smite [player]` — a lightning bolt, for when somebody needs one.

## Groups

### `/group create|invite|accept|deny|leave|disband|kick|rename|tag|sethome|delhome|list|info`

A lightweight group system: shared homes, a chat tag, and teleport relief between members.

**`/group disband` is separate from `/group leave`.** An owner walking out would take everybody's
shared homes with them, which is too much for a word one keystroke from "I am done with this". An
owner *alone* still just leaves, because friction there protects nobody.

### `/ghome [name]` · `/ghomes`

At their plain names, not under `/group`, because travelling to the base is something a member does
several times an hour.

## Talking

### `/msg <player> <message>` · `/r` · `/reply` · `/me <action>`

`/msg`, `/tell` and `/w` are **vanilla's** commands, taken over rather than added beside — otherwise
mutes and ignores would leak straight through them.

### `/ignore <player>` · `/ignore list`

Hides their public chat and their messages, both ways.

### `/msgtoggle` · `/socialspy`

Refuse private messages; watch everyone else's. Spy is op.

### `/mail send|read|clear`

Messages for players who are not on. Also where an offline `/pay` reason is delivered.

## Money

### `/balance` · `/bal` · `/money [player]` · `/baltop`

Works on offline players — balances live in save data, not on the player.

### `/pay <player> <amount> [reason]`

`/pay Steve 500 half of what we dug`. The reason reaches them, and is **carried into the mailbox if
they are offline**, so a payment made on Tuesday still explains itself on Friday. Money that turns up
with no explanation is money the recipient treats as a bug.

### `/eco give|take|set <player> <amount>`

Op. Works on offline players.

## Kits

### `/kit [name]` · `/kits` · `/setkit <name> armour|hotbar|inventory|all` · `/delkit` · `/showkit`

**Kits are made by equipping yourself**, not by writing item ids into a file. Cooldowns survive a
restart.

## Moderation

`/mute` · `/unmute` · `/tempban` · `/invsee` — with durations, reasons shown on reconnect, and an
`/invsee` that cannot be used to duplicate or destroy items.

**A mute silences every channel**, including private messages and any channel another mod adds
through the chat router. A mute that only stops public chat is not a mute.

## Workstations

`/craft` · `/anvil` · `/grindstone` · `/enderchest` · `/trash`

**Denied to everyone by default, operators included** — see the permissions note above. They do not
appear in tab-complete for anyone who lacks the node.

## Combat

No commands of its own — it is a rule rather than a thing you type — but it decides whether several
of the above work.

Being hit by a player puts you in combat, and while it lasts `/home`, `/spawn`, `/tpa` and the rest
refuse with a countdown, in chat and on the action bar. `standards.combat.bypass` lets staff leave
anyway, and is a permission rather than an op check so it can be granted without handing anybody
`/stop`.

**Being hit by a mob does not block teleports by default** (`combat.pveBlocksTeleport = false`): on
a peaceful server a skeleton must not stop you going home, while a player hitting you absolutely
must. Turn it on for a survival server where running from the world should cost something.

**Nothing environmental ever tags you** — fall, drowning, fire, freezing. Set `combat.log = true`
the moment you wonder why something does or does not tag; it prints who, what kind, what caused it
and for how long.

## Away

`/afk [reason]` · `/lurk` — by command or by timer, cleared by moving or speaking. Speaking in
another mod's chat channel clears it too.

## Nicknames

`/nick <name>` — what chat calls you. `/nick -` puts it back. Node `standards.nick`, everyone.

**Chat only, deliberately.** The tab list and the nameplate above your head keep your real name.
That is the whole reason nicknames are safe to switch on: a player who wonders who somebody is can
glance at tab without knowing any command exists. Nicknames render with a `~` marker by default
(`nick.prefix`) so a chosen name reads differently from a real one.

**A nickname may not be another player's real name, nor another player's nickname.** Checked
against every name the server has ever seen, not just who is online — impersonating somebody who
is asleep is the version that works, because they are not there to object.

`/realname <nick>` and `/whois <nick>` — who that actually is. Node `standards.realname`, and
**everyone gets it**: if only staff can tell who somebody is, a nickname is a disguise rather than
a flourish.

Colour codes need `standards.nick.color` (ops). Without it they are stripped rather than refused —
somebody who pasted a code they did not know about wanted the word. `standards.nick.others` lets a
moderator set or clear somebody else's, which is the undo for a nickname that had to go.

## Items

`/i <item> [count]` — give yourself a stack. `/item` is the same command. Node `standards.item`,
ops.

A bare `/i stone` gives a **full stack**, capped at what the item actually stacks to, so a tool or
a shulker box gives one. `/i stone 1` when you want exactly one.

Vanilla's `/give` is untouched — this is a shorter door into the same room, for the commonest case
there is: giving something to yourself. `/give @s minecraft:stone 64` is four times the typing and
needs a selector to do it.

## Server

`/gc` — memory, uptime and tick timing.

`/standards reload` — **messages only**, and it says so. Commands are registered at startup based on
config, so a config change needs a restart; reloading messages is the part that can be done live.

`/standards permissions` — which handler is actually answering permission questions. The first
thing to check when a gated command has quietly disappeared for everybody: a permissions manager
whose storage failed to start answers false to everything, so every gated command vanishes from the
tree and players see only "Unknown or incomplete command".

## Permissions — `/rank`

Only present when Standards' own permission handler is the active one
(`permissionHandler = "standards:permissions"` in `neoforge-server.toml`). Node:
`standards.permissions`, ops by default — deliberately separate from `standards.admin`, so a
moderator can reload messages without also being able to grant themselves everything.

`/rank groups` · `/rank group <name> create|delete|info` · `/rank group <name> set <node> [true|false]`
· `/rank group <name> unset <node>` · `/rank group <name> parent add|remove <other>` ·
`/rank group <name> tag <tag|->`

`/rank user <player> group add|remove <group>` · `/rank user <player> set <node> [true|false]` ·
`/rank user <player> unset <node>` · `/rank user <player> info`

`/rank check <player> <node>` — one node, and **which rule answered it**, not just yes or no.

Players are named rather than selected, so an offline player can be granted a rank — which is most
of what an admin does with a permissions system.

Also at `/perm`, but **`/rank` is the reliable name**: LuckPerms claims `/perm` as an alias of
`/luckperms`, so on a server carrying both, a bare `/perm` runs LuckPerms while the subcommands
still reach ours.

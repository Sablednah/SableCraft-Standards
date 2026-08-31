# Permission nodes

**Generated from the source — do not edit by hand.** `python3 scripts/nodes.py` rebuilds it from `StandardsPermissions.java`, which is the only place a node is really declared.

85 declared nodes, plus the runtime ones described at the bottom.

Standards asks NeoForge's `PermissionAPI` for every one of these, so they work with LuckPerms, with Standards' own handler (`/rank`, see [`PERMISSIONS.md`](PERMISSIONS.md)), or with nothing installed at all — in which case the **Default** column is the whole answer.

| Default | Means |
|---|---|
| `everyone` | Every player has it unless a permissions mod takes it away. |
| `ops` | Operators have it; anybody else needs it granted. |
| `nobody` | **Nobody has it until it is granted**, operators included. |

## Switches

| Node | Default | What it allows |
|---|---|---|
| `standards.fly` | `ops` | Use `/fly`. |
| `standards.fly.others` | `ops` | Use `/fly` on another player. |
| `standards.god` | `ops` | Use `/god`. |
| `standards.god.others` | `ops` | Use `/god` on another player. |
| `standards.smite` | `ops` | Call down lightning. A gamemaster's tool, not a toy. |
| `standards.vanish` | `ops` | Hide from other players. |
| `standards.vanish.others` | `ops` | Use `/vanish` on another player. |
| `standards.vanish.see` | `ops` | See through someone else's vanish. Ops by default so staff are not invisible to each other — two moderators unable to find one another is its own problem. |

## Movement

| Node | Default | What it allows |
|---|---|---|
| `standards.back` | `everyone` | Use `/back`. |
| `standards.back.ondeath` | `nobody` | Returning to your corpse is a gameplay decision, not a convenience — a server that wants death to cost something must not have that quietly handed back. Off by default even though `/back` itself is open. |
| `standards.jump` | `ops` | Use `/jump`. |
| `standards.top` | `everyone` | Use `/top`. |

## Homes

| Node | Default | What it allows |
|---|---|---|
| `standards.delhome` | `everyone` | Use `/delhome`. |
| `standards.home` | `everyone` | Use `/home`. |
| `standards.home.limit.unlimited` | `ops` | Any number of homes, beating every numbered `home.limit.<n>`. |
| `standards.home.others` | `ops` | Use `/home` on another player. |
| `standards.sethome` | `everyone` | Use `/sethome`. |

## Putting a player right, and moving them about

| Node | Default | What it allows |
|---|---|---|
| `standards.bottom` | `ops` | Op-only: near bedrock this is a crude ore finder as much as a travel command. |
| `standards.feed` | `ops` | Use `/feed`. |
| `standards.feed.others` | `ops` | Use `/feed` on another player. |
| `standards.gc` | `everyone` | Reading server health is not sensitive; knowing the TPS helps players report problems. |
| `standards.heal` | `ops` | Use `/heal`. |
| `standards.heal.others` | `ops` | Use `/heal` on another player. |
| `standards.kit` | `everyone` | Use `/kit`. |
| `standards.mail` | `everyone` | Use `/mail`. |
| `standards.rest` | `ops` | Use `/rest`. |
| `standards.rest.others` | `ops` | Use `/rest` on another player. |
| `standards.setkit` | `ops` | Use `/setkit`. |
| `standards.setspawn` | `ops` | Use `/setspawn`. |
| `standards.spawn` | `everyone` | Use `/spawn`. |
| `standards.speed` | `ops` | Use `/speed`. |
| `standards.speed.others` | `ops` | Use `/speed` on another player. |
| `standards.tpoffline` | `ops` | Op-only: logging off should not broadcast where you were. |

## Away

| Node | Default | What it allows |
|---|---|---|
| `standards.afk` | `everyone` | Use `/afk`. |
| `standards.afk.exempt` | `ops` | Never auto-kicked for idling. Ops and, typically, whoever runs the map render. |

## Talking

| Node | Default | What it allows |
|---|---|---|
| `standards.anvil` | `nobody` | Use `/anvil`. |
| `standards.craft` | `nobody` | Portable workstations — nobody by default, including operators. That is the design, not an oversight. |
| `standards.enderchest` | `nobody` | Use `/enderchest`. |
| `standards.grindstone` | `nobody` | Use `/grindstone`. |
| `standards.invsee` | `ops` | Use `/invsee`. |
| `standards.msg` | `everyone` | Use `/msg`. |
| `standards.msg.override` | `ops` | Message someone who has /msgtoggle on. Staff need it; it is not for everyone. |
| `standards.mute` | `ops` | Use `/mute`. |
| `standards.socialspy` | `ops` | Use `/socialspy`. |
| `standards.tempban` | `ops` | Use `/tempban`. |
| `standards.trashcan` | `nobody` | Use `/trashcan`. |

## Teleport requests

| Node | Default | What it allows |
|---|---|---|
| `standards.tpa` | `everyone` | Use `/tpa`. |
| `standards.tpa.override` | `ops` | Ask someone who has /tptoggle on anyway. Staff need it, or /tptoggle becomes a place to hide from moderation rather than from strangers. |
| `standards.tpahere` | `everyone` | Use `/tpahere`. |
| `standards.tptoggle` | `everyone` | Use `/tptoggle`. |

## Warps

| Node | Default | What it allows |
|---|---|---|
| `standards.setwarp` | `ops` | Use `/setwarp`. |
| `standards.warp` | `everyone` | Use `/warp`. |

## Economy

| Node | Default | What it allows |
|---|---|---|
| `standards.balance` | `everyone` | Use `/balance`. |
| `standards.balance.others` | `ops` | Use `/balance` on another player. |
| `standards.baltop` | `everyone` | Use `/baltop`. |
| `standards.eco` | `ops` | Use `/eco`. |
| `standards.pay` | `everyone` | Use `/pay`. |

## Item tools

| Node | Default | What it allows |
|---|---|---|
| `standards.condense` | `everyone` | Merge your own partial stacks. Everyone , and the only one of these a normal player wants: it creates nothing and converts nothing, it just tidies up what you are already carrying. |
| `standards.itemlore` | `ops` | Use `/itemlore`. |
| `standards.itemname` | `ops` | Use `/itemname`. |
| `standards.more` | `ops` | Fill the held stack. Item duplication, plainly, so ops only. |
| `standards.powertool` | `ops` | Bind a command to an item. Runs as the holder, so it is a shortcut and not an escalation. |
| `standards.repair` | `ops` | Repair the held item. A cheat — an anvil and the levels you did not spend. |
| `standards.repair.all` | `ops` | Repair everything you are carrying at once. |

## Where you are

| Node | Default | What it allows |
|---|---|---|
| `standards.compass` | `everyone` | Use `/compass`. |
| `standards.depth` | `everyone` | Your depth and your bearing. Everyone, because on an ordinary server F3 already shows both and there is nothing to protect. |
| `standards.playtime` | `everyone` | How long people have actually played. Everyone — it is a scoreboard, not a secret. |
| `standards.playtime.others` | `everyone` | Use `/playtime` on another player. |
| `standards.sudo` | `ops` | `/sudo` — run a command as somebody, with their permissions. Ops only and obviously so, but note what it is not: it cannot give anybody an ability they lack, because the command is parsed against their source. |
| `standards.world` | `ops` | `/world` and `/worlds` — moving between dimensions keeping your coordinates. |

## Admin teleports

| Node | Default | What it allows |
|---|---|---|
| `standards.butcher` | `ops` | `/butcher` — clearing entities in a radius. A lag tool, and a griefing tool. |
| `standards.item` | `ops` | `/i` — spawning items out of nothing. Ops, obviously: on a survival server this is the single most consequential thing in the mod. |
| `standards.motd` | `everyone` | `/motd`, `/rules` and `/info` — owner-written text. Everyone reads them. |
| `standards.tp` | `ops` | `/tpx` and `/tppos` — moving yourself about at will. A node rather than an op check, which is the entire reason these exist beside vanilla's `/tp`: a builder can be given this without also being given `/stop`. |
| `standards.tp.others` | `ops` | Moving other people — `/tphere`, and the two-player form of `/tpx`. |

## Nicknames

| Node | Default | What it allows |
|---|---|---|
| `standards.group` | `everyone` | Founding and running a lightweight group. Everyone, like homes — it is a social feature. |
| `standards.nick` | `everyone` | Set your own. Everyone, like homes — it is a social feature, not a privilege. |
| `standards.nick.color` | `ops` | Colour codes in a nickname. Ops only, and not fussiness: `&k` is obfuscated text, which renders as animated gibberish on every line its owner speaks and cannot be read, reported or typed back. |
| `standards.nick.others` | `ops` | Set or clear somebody else's — the moderator's undo for a nickname that had to go. |
| `standards.realname` | `everyone` | Look a nickname up. Everyone , deliberately. |

## Teleport bypasses

| Node | Default | What it allows |
|---|---|---|
| `standards.combat.bypass` | `ops` | Leave a fight anyway. A permission and not an op check, so a server can grant it to staff without also handing them `/stop` — the same reasoning that put every other gate on a node. |
| `standards.teleport.instant` | `ops` | Skip the teleport warmup — arrive immediately. |
| `standards.teleport.nocooldown` | `ops` | Skip the wait between teleports. |

## Admin

| Node | Default | What it allows |
|---|---|---|
| `standards.admin` | `ops` | `/standards` — administering the mod itself, not using it. |
| `standards.permissions` | `ops` | Editing the built-in permission handler's groups and grants. Separate from `ADMIN` because the two are different jobs: reloading messages is a caretaker's task, and handing out permissions is how somebody becomes an operator by proxy. |

## Built at runtime

These are not declared in source — the server builds them from what it actually holds, so they are not in the table above and a permissions mod will only see them after a restart.

| Node | What it allows |
|---|---|
| `standards.home.limit.<n>` | That many homes. The highest granted number wins, and `home.limit.unlimited` beats them all. Numbered nodes rather than one integer node, because every server admin alive already knows the idiom. |
| `standards.kit.<name>` | One particular kit. Its default follows the kit's own access — see `/kitaccess`. A kit created since the last restart has no node, and is answered from that access directly. |

Ask a running server what it really has with `/standards nodes`, which lists every node actually registered, runtime ones included.

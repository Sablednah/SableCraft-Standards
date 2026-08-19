# The command catalogue — pick what Standards ships

Every command in **EssentialsX** (the living descendant of the Bukkit *Essentials* you used to run)
and in **FTB Essentials** (the modern NeoForge one), in one list, so you can say yes/no once
instead of discovering gaps a year in.

**How to use this:** the `Verdict` column is my recommendation, not a decision. Change any of them.
The only column that matters in the morning is what you write in it.

| Verdict | Means |
|---|---|
| **CORE** | Standards is not credible without it. Ship in 1.0. |
| **YES** | Clearly worth having, not urgent. |
| **MAYBE** | Real trade-offs — flagged below with what they are. |
| **NO** | A modern mod does it better, or it does not belong in a utility mod. |
| **DONE** | Already built in the shell as of tonight. |

`E` = EssentialsX has it · `F` = FTB Essentials has it · `—` = neither, my suggestion

---

## 1. Teleporting and movement

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/top` | ✓ | ✗ | Up to the surface | **DONE** | **The command that started this.** Ours scans upward for the first safe floor instead of reading the heightmap — so it works in the Nether and in caves, where the classic one either kills you or overshoots to the surface. |
| `/jump` `/j` | ✓ | ✓ | Teleport where you're looking | **DONE** | Lands *on top* of the block, scanning up if that's occupied. |
| `/back` | ✓ | ✓ | Return to where you were | **DONE** | Ours keeps a **trail**, not one slot: `/back 2` walks further up it. Death-return is behind its own permission, off by default. |
| `/bottom` | ✓ | ✗ | Lowest block in your column | **DONE** | *Answered: ship it, op-only — "why not complete the set".* Op-only because it is a trivial x-ray for ore near bedrock. |
| `/tp` `/tphere` `/tppos` | ✓ | ✓ | Admin teleports | **YES** | Vanilla `/tp` exists; ours is worth it only for the tri-state/permission consistency and `/tppos` with a dimension. Low priority. |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` `/tpacancel` `/tpalist` `/tptoggle` | ✓ | ✓ | Request-based teleport | **DONE** | Clickable `[Accept]`/`[Deny]` buttons (vanilla chat click events, so they work unmodded). **Both ends are narrated** — accepted, arriving in N, arrived, cancelled and why, lapsed — plus a ticking action-bar countdown, because a warmed teleport that goes silent for 5s reads as broken. |
| `/tpaall` `/tpall` | ✓ | ✗ | Mass teleport | YES | Event hosting. Cheap once `/tpa` exists. |
| `/tpauto` | ✓ | ✗ | Auto-accept requests | YES | One line once `/tpa` exists. |
| `/tptoggle` | ✓ | ✗ | Refuse all teleports to you | YES | Anti-grief; pairs with `/tpa`. |
| `/tpo` `/tpohere` | ✓ | ✗ | Override `/tptoggle` | YES | Staff need it or `/tptoggle` becomes a hiding place. |
| `/tpoffline` `/otp` | ✓ | ✓ | To a player's last logout spot | **DONE** | *Answered: ship it, op-only.* FTB calls it `teleport_last`. |
| `/rtp` `/tpr` `/wild` | ✓ | ✓ | Random teleport | **2.0** | *Answered: deferred — whole mods exist that do only this.* Needs its own cooldown, a biome blacklist and a claim-mod check (that instance runs FTB Chunks). |
| `/settpr` | ✓ | ✗ | Configure the RTP region | **2.0** | Ships with `/rtp`. |
| `/tpx` | ✗ | ✓ | Teleport across dimensions | NO | Our `/tp` and every waypoint already carry a dimension. Redundant. |
| `/spawn` `/setspawn` | ✓(spawn) | ✓ | World spawn | **DONE** | Data layer is already built (`StandardsData.spawn`); commands not wired. |
| `/playerspawn` | ✗ | ✓ | To your bed/anchor | **DONE** | Nice pairing with `/spawn`. |
| `/warp` `/warps` `/setwarp` `/delwarp` | ✓ | ✓ | Server-wide named places | **DONE** | |
| `/warpinfo` | ✓ | ✗ | Where a warp points | YES | Trivial. |
| `/home` `/sethome` `/delhome` `/homes` | ✓ | ✓ | Personal named places | **DONE** | Limits are numbered permission nodes (`standards.home.limit.5`), EssentialsX-style, so LuckPerms sets them per rank. |
| `/renamehome` | ✓ | ✗ | Rename a home | YES | Small and people ask for it. |
| `/home <player>` | ✓ | ✗ | Go to someone else's home | YES | Node exists (`standards.home.others`), command not wired. |

**Decisions — all three answered (2026-08-19):**
1. ~~`/bottom` — ship it, op-only, or not at all?~~ → **op-only, ship it.**
2. ~~`/rtp` — 1.0 or later?~~ → **2.0.** Whole mods do only this; half-doing it is worse than not.
3. ~~`/tpoffline` — staff tool or no?~~ → **op-only, ship it.**

---

## 2. Player state — the toggles

**Every one of these takes `on` / `off` / `toggle` in Standards.** That is the whole point of the
mod: `/fly Steve on` from a LegendQuest skill must not be a coin flip.

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/fly` | ✓ | ✓ | Creative flight | **DONE** | Granted via NeoForge's `CREATIVE_FLIGHT` attribute, so it composes with other mods instead of fighting them over one boolean — and mirrored to the ability flag so **vanilla clients** are actually told. |
| `/god` | ✓ | ✓ | Invulnerability | **DONE** | Ability flag *and* a damage-event veto, because the flag alone misses starvation and the void. |
| `/heal` | ✓ | ✓ | Refill health | **DONE** | |
| `/feed` `/eat` | ✓ | ✓ | Refill hunger | **DONE** | |
| `/rest` | ✓ | ✗ | Clear the phantom timer | **DONE** | Comes free with `/feed`. |
| `/speed` | ✓ | ✓ | Walk/fly speed | **DONE** | Also an attribute, same reasoning as `/fly`. |
| `/ext` `/extinguish` | ✓ | ✓ | Put a player out | YES | |
| `/ice` `/freeze` | ✓ | ✗ | Cool a player down | **NO** | *Answered: dropped.* |
| `/burn` | ✓ | ✗ | Set a player on fire | **NO** | *Answered: dropped.* Make it a LegendQuest skill. |
| `/kill` `/suicide` | ✓ | ✗ | Kill | NO | Vanilla `/kill` is fine. |
| `/vanish` `/v` | ✓ | ✗ | Hide from other players | **DONE** | *Built.* A switch (so `on`/`off`/`toggle` and `/vanish @a off` come free) plus `PlayerSwitches.setVanished(...)` for the storyteller mod. Hides via the mod's **one mixin** on vanilla's own visibility check, so the entity tracker unpairs and re-pairs correctly; also removes from the tab list and stops mobs targeting them. |
| `/afk` `/lurk` | ✓ | ✗ | Mark yourself away | **DONE** | *Answered: build it, with a **configurable auto-AFK** timer, and alias `/lurk`.* Auto-detection is what makes it worth having — a manual-only marker is one nobody sets. |
| `/smite` | ✓ | ✗ | Lightning on a target | **DONE** | *Answered: the one joke command that survived.* Op-gated; bare `/smite` strikes wherever you are looking, so it works as theatre and not only as punishment. |
| `/gamemode` `/gm` `/gmc` `/gms` | ✓ | ✗ | Change game mode | NO | Vanilla `/gamemode` plus its aliases already exists. Aliases only if you want the muscle memory. |
| `/ptime` `/pweather` | ✓ | ✗ | Personal time/weather | MAYBE | Charming, needs client packets, nobody misses it. |

**Decisions:**
4. ~~`/vanish` — worth the cost?~~ → **yes, build it now**, and expose it to other mods: a future
   LegendQuest storyteller/GM mod is a named consumer.
5. ~~`/afk` — auto-timer, or manual only?~~ → **configurable auto-AFK**, plus a `/lurk` alias.
6. ~~The joke commands — in or out?~~ → **dropped**, with one exception. LegendQuest skills can
   run commands, so server-specific silliness belongs there, not here. **`/smite` survives** — it
   is `/execute at <player> run summon lightning_bolt` in one word, op-gated, and a gamemaster's
   tool rather than a joke.

---

## 3. Homes for money — the economy

Standards ships its own ledger because **there is no clear leader on NeoForge 1.21+ and no Vault
equivalent**. Verified tonight: the candidates are SG-Economy API, Real Economy, EconomyCraft and
EconomyMod — four fragmented mods, none dominant, none with an adopted cross-mod API. So ours is
built behind `EconomyProvider`, registers itself at a *negative* priority, and steps aside the
moment a dedicated economy mod registers. LegendQuest and ZombieMod call `Economy.deposit(...)` and
never learn which ledger answered.

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/balance` `/bal` `/money` | ✓ | ✗ | Your balance | **DONE** | Works for offline players too. |
| `/baltop` | ✓ | ✗ | Richest players | **DONE** | A foreign ledger that cannot enumerate accounts says so rather than showing a half-truth. |
| `/pay` | ✓ | ✗ | Pay another player | **DONE** | |
| `/eco give\|take\|set` | ✓ | ✗ | Admin money | **DONE** | Goes through the active provider, never behind its back. |
| `/paytoggle` `/payconfirmtoggle` | ✓ | ✗ | Refuse / confirm payments | YES | The confirm prompt prevents a real class of typo. |
| `/worth` `/setworth` | ✓ | ✗ | Item sell values | MAYBE | Only earns its keep with `/sell`. |
| `/sell` | ✓ | ✗ | Sell your held stack | MAYBE | Server-shop territory. It is a whole feature, not a command. |
| — | | | **Sign shops / chest shops** | **OWN MOD** | *Answered: a follow-up mod, and deliberately so — it becomes the worked example of how to consume the economy API.* |
| — | | | **ATMs** | **OWN MOD** | *Answered: wanted.* A sign beside a dispenser: click to buy an emerald for ₡100, right-click with an emerald to sell it for ₡100 (configurable both ways). Possibly a purpose-built block later. **This is the important one** — it bridges vanilla's villager/emerald economy to the bank balance, which is what stops a virtual currency feeling like a spreadsheet. |
| — | | | **`/eco log`** | YES | Every transaction carries an audit `reason` already. Surfacing it costs little and answers "where did the money go", which is the first question every time. |

**Decisions — all three answered:**
7. ~~Shops in scope?~~ → **separate follow-up mod**, doubling as the economy API's reference
   implementation. With **ATMs** bridging emeralds ↔ balance.
8. ~~Currency defaults?~~ → **whole numbers**, named *credits*, symbol **₡** before the number.
   Name, plural, symbol, symbol side and decimals are all config.
9. ~~Starting balance of 100?~~ → **keep it.** At the ATM rate that is one emerald to start with,
   which is a defensible amount rather than an arbitrary one.

---

## 4. Chat and social

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/msg` `/w` `/tell` `/r` | ✓ | ✗ | Private messages | **DONE** | Vanilla `/msg` exists but has no `/r`, which is the half people use. |
| `/msgtoggle` `/ignore` | ✓ | ✗ | Block messages | YES | |
| `/socialspy` | ✓ | ✗ | Staff see private messages | YES | Ships with `/msg`. |
| `/nick` | ✓ | ✓ | Nickname | **YES** | FTB has it. Needs a colour-code permission and a "real name still findable" rule. |
| `/realname` | ✓ | ✗ | Who is behind a nickname | YES | Required if `/nick` exists. |
| `/me` | ✓ | ✗ | Emote | NO | Vanilla `/me`. |
| `/mail` | ✓ | ✗ | Offline messages | **DONE** | *Answered: wanted.* Envisioned with **post box blocks** holding written books — see the block note below. |
| `/helpop` `/ac` | ✓ | ✗ | Message staff | YES | |
| `/broadcast` `/bc` | ✓ | ✗ | Server-wide announcement | YES | Vanilla `/say` is uglier. |
| `/mute` `/unmute` | ✓ | ✓ | Silence a player | **DONE** | With durations (`30m`, `2h30m`, `perm`) and a reason. Persisted, and the muted player is told how long is left every time they try to speak. |
| `/list` `/who` | ✓ | ✗ | Who is online | NO | Vanilla `/list`. |
| `/near` | ✓ | ✓ | Who is nearby | YES | |
| `/seen` | ✓ | ✗ | Last login/logout | YES | The name cache that powers offline `/balance` already has half of this. |
| `/playtime` | ✓ | ✗ | Time played | YES | Vanilla statistics have the number. |
| `/motd` `/rules` `/info` | ✓ | ✗ | Owner-written text | **YES** | Cheap, and `messages.yml` is already the right home for it. |
| `/recording` `/streaming` | ✗ | ✓ | Tell the server you're recording | NO | Very FTB-specific. |

**Decision:**
10. ~~`/mail` — in or out?~~ → **in**, and it grows a physical side: post boxes holding written
    books, the same way the economy grows ATMs.

---

## 5. Moderation

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/tempban` | ✓ | ✗ | Ban with an expiry | **DONE** | *Answered: this plus vanilla's own `/ban` and `/kick` complete the set.* Writes into **vanilla's ban list**, which has always stored an expiry — so `/pardon`, the ban screen and `banned-players.json` all keep working. |
| `/ban` `/unban` `/banip` `/kick` | ✓ | ✗ | Bans and kicks | **NO** | *Answered: vanilla has them.* |

| `/jail` `/setjail` `/deljail` `/jails` | ✓ | ✗ | Jail a player | **NO** | *Answered: out of scope* — past the moderation boundary. |
| `/invsee` | ✓ | ✓ | See a player's inventory | **DONE** | A **live** six-row view — items taken really leave the player, since a copy would duplicate them. Laid out as main / hotbar / armour+offhand rather than 42 undifferentiated squares. |
| `/enderchest` `/ec` | ✓ | ✓ | Open your ender chest | **DONE** | |
| `/sudo` | ✓ | ✗ | Run a command as someone | MAYBE | Vanilla `/execute as` covers most of it. |
| `/whois` | ✓ | ✗ | Player info dump | YES | |

**Decision — answered:**
11. ~~Does Standards go into moderation at all?~~ → **`/tempban`, `/invsee`, `/mute` and stop.**
    Vanilla's `/kick` and `/ban` complete the set; LuckPerms owns everything about who may do
    what. **This is a boundary, not a to-do list** — the value is in what Standards agrees not to
    build.

---

## 6. Inventory and items

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/hat` | ✓ | ✓ | Wear your held item | YES | Cheap and beloved. |
| `/trashcan` `/disposal` | ✓ | ✓ | Throwaway inventory | **DONE** | Backed by a throwaway container, so nothing can be recovered by force-closing the screen. |
| `/craft` `/workbench` | ✓ | ✓ | Portable crafting table | **DONE** | Permission **denied by default** — see below. |
| `/anvil` `/grindstone` | ✓ | partial | Portable stations | **DONE** | The two that earn their place. Loom, stonecutter, smithing and cartography deliberately left out. |
| `/repair` `/fix` | ✓ | ✗ | Repair held item | MAYBE | Straightforwardly a cheat on a survival server. Op-only, or config-off by default. |
| `/more` `/condense` | ✓ | ✗ | Fill stack / compact items | MAYBE | `/condense` is genuinely handy and not a cheat. |
| `/give` `/item` `/i` | ✓ | ✗ | Spawn items | NO | Vanilla `/give`. The short alias is the only draw. |
| `/clearinventory` `/ci` | ✓ | ✗ | Clear inventory | NO | Vanilla `/clear`. |
| `/itemname` `/itemlore` | ✓ | ✗ | Rename/relabel an item | MAYBE | Nice for server builds and lore items. |
| `/skull` `/head` | ✓ | ✗ | Player-head shortcut | YES | Cheap. |
| `/powertool` `/pt` | ✓ | ✗ | Bind a command to an item | MAYBE | **Overlaps LegendQuest's `/bind`.** Coordinate the two or drop it. |
| `/unlimited` | ✓ | ✗ | Infinite placing | NO | |
| `/enchant` | ✓ | ✗ | Enchant held item | NO | Vanilla `/enchant`. |
| `/itemdb` `/dura` | ✓ | ✗ | Item id / durability | MAYBE | Modern clients show this. |

**Decision — answered, and the reasoning is the good part:**
12. ~~Portable stations — all, a few, or none?~~ → **`/craft`, `/enderchest`, `/trashcan`,
    `/grindstone`, `/anvil`** — with **permissions off by default, including for operators.**

    That single choice turns them from cheats into **class abilities**: a builder rank gets a
    workbench anywhere, a blacksmith gets an anvil anywhere. LuckPerms grants them per group, and
    a LegendQuest skill drives them through the `Stations` API — which bypasses the permission
    check, because a skill the player has already earned is its own authority. (A skill *running
    the command* would be refused by the very check that makes the design work.)

---

## 7. Kits

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/kit` `/kits` | ✓ | ✓ | Claim a kit | **DONE** | Per-kit cooldowns and permissions. |
| `/setkit <name> armour\|hotbar\|all` `/delkit` `/showkit` | ✓ | ✓ | Manage kits in-game | **1.0 — queued** | *Answered:* capture **what you are wearing / holding / carrying**, scoped by argument. This is the bit that makes kits actually get used; a YAML-only kit system never does. |
| `/kitreset` | ✓ | ✗ | Clear someone's cooldown | YES | |

**Decision — answered:**
13. ~~Kits in 1.0, or later?~~ → **1.0**, built around
    **`/setkit <name> armour|hotbar|all`** — you equip yourself the way the kit should look and
    save it, rather than writing out item ids.

---

## 8. World and server

| Command | E | F | What it does | Verdict | Notes |
|---|:-:|:-:|---|---|---|
| `/time` `/day` `/night` | ✓ | ✗ | Change time | NO | Vanilla `/time`. |
| `/weather` `/sun` `/storm` | ✓ | ✗ | Change weather | NO | Vanilla `/weather`. |
| `/gc` `/tps` `/lag` `/mem` | ✓ | ✗ | Server health | **DONE** | Genuinely useful on a modpack server, and the mod-loader ones are all worse than this. |
| `/world` | ✓ | ✗ | Switch worlds | MAYBE | Overlaps `/tpx` and dimension-aware warps. |
| `/near` | ✓ | ✓ | Nearby players | YES | (Also listed in Chat.) |
| `/getpos` `/coords` `/whereami` | ✓ | ✗ | Your coordinates | YES | F3 exists; this works on servers that hide it. |
| `/depth` `/compass` | ✓ | ✗ | Depth / bearing | MAYBE | |
| `/remove` `/butcher` `/killall` | ✓ | ✗ | Clear entities in a radius | **YES** | Real lag-fighting tool on a modpack server. |
| `/spawnmob` `/spawner` | ✓ | ✗ | Spawn mobs / change spawners | NO | Vanilla `/summon`; ZombieMod owns the interesting half. |
| `/tree` `/bigtree` | ✓ | ✗ | Grow a tree | NO | |
| `/leaderboard` | ✗ | ✓ | Statistic leaderboards | MAYBE | FTB-only. Nice with `/baltop` and `/playtime`. |
| `/kickme` | ✗ | ✓ | Kick yourself | NO | FTB curiosity. |
| `/editsign` `/sign` | ✓ | ✗ | Edit a placed sign | YES | Useful for builds; CityWorld's sign work proves the API. |
| `/backup` | ✓ | ✗ | Run the configured backup | NO | Server-host territory. |

---

## 9. Fun / gimmick — my recommendation is drop the lot

`/antioch` `/beezooka` `/kittycannon` `/nuke` `/fireball` `/lightning` `/firework` `/potion`
`/ping` `/echo`

EssentialsX carries these for history. They are the reason "essentials" sounds unserious. **NO** on
all of them — with the possible exception of `/ping`, which people do use.

**Decision:**
14. Confirm: drop the joke commands?

---

## 9b. A note on blocks — ATMs, post boxes, and the vanilla-client rule

Two answers now want a physical object in the world: **ATMs** (decision 7) and **post boxes**
(decision 10). That is a pattern, so it is worth settling once rather than twice.

**A new block cannot be rendered by a client that has never heard of it.** This is the same wall
ZombieMod hit with entities, and it reached the right answer: *register nothing new; express the
feature through things vanilla already knows how to draw.* So the default implementation of both
should be **vanilla blocks with a marker** — a sign beside a dispenser is an ATM; a lectern or
barrel with a sign is a post box. Every player gets the feature, modded or not, which is the whole
promise of the mod.

An **optional client mod adding prettier dedicated blocks** then sits on top as a pure upgrade, in
exactly the relationship `network/StandardsNetwork` was reserved for: nice if you have it, never
load-bearing. Getting that order right matters — building the bespoke blocks first would quietly
turn a server-side utility into a mod every player must install.

---

## 10. Things neither of them has, that I think we should

| Idea | Why |
|---|---|
| **`/standards economy`** | Which ledger is actually holding the money. **Built** — it is the first question when an economy misbehaves and nothing else answers it. |
| **Tri-state on every switch** | **Built.** The reason the mod exists. |
| **`/back` as a trail, not a slot** | **Built.** `/back 3` after a chain of warps. |
| **An audit reason on every transaction** | **Built** into the API. Makes `/eco log` possible later. |
| **`messages.yml` as one editable catalogue** | **Built.** Translation *and* re-skinning: a server whose currency is credits sets one key. |
| **A name with a joke in it** | **Settled.** Full name *SableCraft Standards*, friendly name *Standards*, mod id `standards`. Yes, it is a generic namespace — that is the [xkcd 927](https://xkcd.com/927/) joke, and "do you have Standards?" is worth the ambiguity. The mod id stays short because it is what admins type all day: `standards.home.limit.5`, not `sablecraft_standards.home.limit.5`. |
| **A dormant self-test** | **Built.** `./gradlew runServer -Pselftest`. |
| `/tempfly` / timed switches | `/fly Steve on 30s` — an obvious extension of the tri-state and exactly what a skill wants. |
| Per-command cooldown and cost | Any command could cost money or have a cooldown, declared once rather than per-command. |
| A claim-mod check on teleports | `/rtp` and `/tpa` into someone's claim is the classic griefing hole. Soft-dependency, like ZombieMod's CityWorld hook. |

---

## Where 1.0 actually stands

**Everything on the original 1.0 list is built.** 77 commands, 92 permission nodes, 198 self-test
checks passing on a real dedicated server.

**Deferred by decision, not by omission:**

| | |
|---|---|
| `/rtp` + `/settpr` | 2.0 — decision 2 |
| Sign/chest shops, ATMs | their own mod — decision 7 |
| Post box blocks | with `/mail`, once the block question is settled — §9b |
| Bans, kicks, jails | vanilla's and LuckPerms' job — decision 11 |
| Joke commands | dropped — decision 6, `/smite` excepted |

**Still open, small:** `/warpinfo`, `/renamehome`, `/home <player>`, `/seen`, `/playtime`,
`/near`, `/getpos`, `/broadcast`, `/helpop`, `/motd` + `/rules`, `/nick` + `/realname`,
`/hat`, `/skull`, `/condense`, `/remove` (entity clearing), `/editsign`, `/paytoggle`, `/tpaall`,
`/tpauto`, `/tpo`.

None of those need a decision — they are all "yes, when there is an evening for it".

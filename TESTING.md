# Walking every command

A manual pass over all 77 commands. `SelfTest` proves they parse, execute and get their logic
right; this is for the half it cannot reach — what a person actually sees.

**Setup.** `./gradlew runServer` from WSL, then `.\TestClient.cmd` (TestBuddy) and either your own
Standards instance or `.\TestClient.cmd main`, both Direct Connect to `127.0.0.1:25569`.
Two players marked **[2P]**. You are op; TestBuddy deliberately is not.

Useful config while testing, in `run/config/standards-common.toml`:

| Setting | Why |
|---|---|
| `teleport.warmupSeconds = 5` | otherwise the `/tpa` countdown has nothing to show |
| `teleport.tpaTimeoutSeconds = 300` | 120s lapses while you are alt-tabbing |
| `afk.awayAfterSeconds` | lower it to see auto-AFK without waiting five minutes |

---

## Already proven by machine — skip unless something looks off

`scripts/battery.py` drives these over RCON and asserts on the commands' **return values**, not
merely that they did not error. 72 assertions, all passing as of 19 Aug:

| Area | Covered |
|---|---|
| Economy | `/balance` `/baltop` `/eco set\|give\|take` arithmetic, refusals for unknown players |
| Homes | set, list, delete, missing-name refusal, the **limit** and raising it live via LuckPerms |
| Warps | set, list, delete, missing-name refusal |
| Kits | capture, list, show, claim, delete, and the **cooldown** refusing a second claim |
| Mail | send, read, clear, empty-mailbox refusal |
| Movement | `/top` `/back` `/spawn` `/setspawn` |
| Self care | `/heal` `/feed` `/rest` `/speed`, and the speed cap refusing |
| Switches | `/fly` `/god` `/vanish` `/tptoggle`, including "already on" reporting no change |
| Moderation | `/mute` covering **private messages** too, `/unmute`, bad-duration refusal |
| Server | `/gc` returning real TPS, `/standards economy` naming the provider, `/smite` |
| Persistence | balance, warps, homes, kits, mail and **kit cooldowns** all surviving a restart |

Two of those confirm design decisions rather than code: **`/eco give` works on an offline player**
(the reason balances are world save data), and **granting `standards.home.limit.10` takes effect
without a restart**.

**What a machine could not check** is everything below: whether a message reads clearly, whether a
grid is legible, whether the timing feels right, and anything needing two people at once.

---

## Switches — the whole point of the mod

Every one takes `on` / `off` / `toggle`, bare, or with a player/selector.

- [x] `/fly` and `/god` — bare toggles, and explicit `on` / `off` both work
- [x] `/fly on` twice — says "already on" rather than silently succeeding
- [x] `/fly off` while airborne — you drop, rather than hanging there
- [x] `/god off` — damage lands again immediately
- [x] `/fly TestBuddy on` **[2P]** — they are told who did it
- [x] `/fly @a off` — reports a count, not one name
- [x] `/god` / `/god on` — take damage, then don't. Try lava, fall, starvation
- [x] `/vanish` `/v` — see the vanish section below
- [x] `/tptoggle off` **[2P]** — TestBuddy's `/tpa` to you says you are not accepting
- [x] `/msgtoggle off` **[2P]** — same for `/msg`
- [x] `/socialspy on` **[3P]** — TestBuddy and TestThird `/msg` each other; you see it, and
      **neither of them can tell.** Two players cannot test this at all: every message between
      you and TestBuddy is one you would receive anyway, so spying is indistinguishable from
      being talked to. `TestClient.cmd third` starts the bystander.

## Getting about

- [x] `/up` and `/down` aliases — bare, with no argument
- [x] `/top` from inside a cave — first safe floor **above you**, not the surface
- [x] `/back` **while flying** — returns you to a mid-air point instead of refusing. Every back
      point a flying player makes is mid-air, so this is the normal case for anyone with `/fly` on
- [x] `/sethome` while flying, then `/home` — same story for homes and warps
- [x] `/setspawn` somewhere with no floor — saves, but warns you at the time
- [x] Bed spawn boxed in, with water nearby — lands you on **dry ground** if any is in range;
      underwater only when there is genuinely nothing better
- [x] `/top` in the Nether — lands **under** the bedrock, never on the roof
- [x] `/top` under a bedrock or barrier roof — refuses, and names the block in the way
- [x] `/bottom` over a sealed bedrock vault — refuses, rather than dropping you inside it
- [x] `/bottom` **from** the Nether roof — still gets you down; the rescue outranks the barrier
- [x] `/bottom` on open ground — still lands on the world bedrock floor (landing *on* it is fine,
      only scanning *through* it is refused)
- [x] `/bottom` — op only
- [x] `/jump` `/j` — lands on top of what you are looking at, not inside it
- [x] `/jump` at the sky — "nothing in range"
- [x] `/back` after any teleport, and `/back 2`, `/back 3` up the trail
- [x] `/back` after dying — with `standards.back.ondeath` granted, returns you to the corpse
      with a death-specific message
- [x] `/back` after dying **without** the node — explains that the spot was not saved, rather
      than silently sending you to the previous entry on the trail
- [x] with a warmup configured, arrival is still announced — the message fires on landing,
      not on acceptance
- [x] `/spawn` with no `/setspawn` ever run — falls back to world spawn, does not error
- [x] `/setspawn` then `/spawn`
- [x] `/playerspawn` with no bed — says so; with a bed — goes there
- [x] warmup: start any teleport and **walk** — cancelled, and you are told why
- [x] warmup: start one and take damage — cancelled

## Teleport requests **[2P]**

- [x] `/tpa TestBuddy` — they get a prompt with clickable `[Accept]` / `[Deny]`
- [x] click Accept — **you** are told immediately, and get a ticking action-bar countdown
- [x] they are told you are arriving, and told again when you land
- [x] `/tpahere TestBuddy` — accepted, **they** travel, and both sides get the right message
- [x] accept, then walk during the countdown — cancelled, **and they are told why**
- [x] `/tpa`, they accept, then **they** walk off while **you stand still** — you land next to
      wherever they ended up, not where they accepted from. (You moving cancels it; that is the
      separate check above. `tpaFollowTarget = false` lands you where they accepted instead.)
- [x] `/tpacancel` with nothing to cancel but a request waiting — offers an Accept button
- [x] `/tpaccept` when you are the one waiting — tells you so
- [x] let one lapse — ⌛ message at both ends
- [x] `/tpalist` — who asked, which direction, seconds left
- [x] `/call` `/tpyes` `/tpno` aliases
- [x] `/tpa` yourself — refused, you are already there
- [x] `/tpoffline TestBuddy` after they log out — op only

## Vanish **[2P]** — TestBuddy must not be op

- [x] `/vanish on` — invisible, not translucent
- [x] Tab — you are off their list
- [x] a mob near you stops pathing to you
- [x] their `/msg` to you fails with an ordinary "no player" error; your `/msg` to them works
- [x] …and you are absent from **tab-completion** too — hiding someone from chat and the
      player list leaks them straight back the moment anyone types `/msg s`
- [x] spectral arrow — no glow outline
- [x] they shoot you — arrows pass through, no bounce
- [x] they walk into you — no shove, they phase through
- [x] open a chest — **it should still animate for them.** Deliberate; the world's reactions stay
      visible, only the player is hidden
- [x] shoot past them — arrows do not bounce, and the arrow that lands is **not picked up**
      (`vanish.vanishPickup = false`; an item vanishing off the floor with nobody there gives
      you away as surely as being seen)
- [x] relog while vanished — still hidden
- [x] `/vanish off` — reappear cleanly, no ghost

## Before a release

- [x] SnakeYAML is bundled jar-in-jar and declared in the metadata — the classic works-in-dev,
      fails-on-a-real-server trap
- [x] the server starts with **no permissions mod at all**, on NeoForge's `default_handler`.
      That is the commonest configuration in the world and the dev server has had LuckPerms
      since session three, so the path had never run
- [ ] a **player** on that server, with no permissions mod: everyone-nodes work, op-gated ones
      work for an op and refuse for a non-op, and the home limit falls back to `defaultLimit`
- [ ] the built jar on a server that is **not** the dev environment — `messages.yml` written,
      commands registered, no missing-class errors from the bundled YAML
- [ ] a logo. `logoFile` is commented out and there is no icon, so the mods list and any
      CurseForge page show a blank tile
- [x] decide the version — **1.0.0**

## Groups — the built-in lightweight ones

- [x] `/group create <name>` — you own it
- [x] `/group invite`, and they `/group accept` — membership sticks
- [x] `/group tag SBL` — the tag renders in chat as `[SBL] name: message`
- [x] `/group sethome base` as the owner, then a **member** runs `/ghome` — they reach a home
      they never set, which is the point of the whole feature
- [x] `/ghome` with exactly one home needs no name
- [x] `/ghome` with several — asks which, rather than guessing
- [x] `/ghomes` lists them
- [x] `/group rename` — the chat tag survives, because config keys on the kind and not the name
- [x] `/group kick`, and `/group leave` as a member
- [x] `/group leave` as the **owner** of a group with members — **refused**, and points at
      `/group disband`
- [x] `/group disband` — ends it, tells the members, and the shared homes go with it
- [ ] `/group leave` as an owner who is **alone** — just leaves, no second command to learn
- [x] a non-owner tries `/group sethome` / `tag` / `rename` — refused
- [x] two groups try the same tag — the second is refused
- [x] `/tpa` to a group-mate — **no cooldown**, but the warmup still applies
      (`groupTeleportSkipsWarmup = false`, deliberately: the warmup is the anti-combat-log half)
- [x] `/group create` a name that already exists — refused, and says which reason
- [x] restart the server — groups, members, tags and shared homes all survive

## Factions — the sibling mod

Nothing here has been run by a player. Both mods load, the group kind and claims provider both
register, and the commands parse — that is all that is known.

- [x] `/f create <name>`, `/f claim` — the chunk you stand in becomes yours
- [ ] walk out of it — the action bar says **Wilderness**, and says the name walking back in
- [x] `/f borders`, and separately **hold a compass** — the outline appears either way
- [ ] the outline follows the *shape* of the land, not a grid on every chunk
- [ ] `/f map` — the chat grid, with you as `+` in the middle
- [ ] **`/f map item`** — a real map item, one pixel per chunk, edges bright and interiors dim.
      It must **not** repaint itself with terrain as you walk (it is locked)
- [ ] a second player tries to break a block in your claim — refused, and told whose it is
- [ ] `/f ally <them>` — says the alliance is pending until they offer back; then they do
- [ ] `/f enemy <them>` — effective immediately, one-sided
- [ ] `/f peaceful` — cannot declare enemies, and cannot be declared upon
- [ ] `/f sethome` outside your land — refused; inside — works, and `/f home` uses the warmup
- [ ] `/f claim` past the limit — refused, and names the per-member arithmetic
- [ ] `/f claim` a chunk not touching your land — refused (`mustBeConnected`)
- [ ] faction tag in chat, after adding `factions:faction` to `groupTagKinds`
- [ ] restart — factions, claims, relations and homes all survive

## Homes and warps

- [x] `/sethome` then `/home` — the unnamed default
- [x] `/sethome base`, `/home base`, `/homes`
- [x] `/sethome` a fourth time — refused at the limit, with the overwrite hint
- [x] overwriting an existing home while over the limit — allowed, so a ceiling never
      means "you may never /sethome again"
- [x] lowering a limit below what a player already has — nothing is deleted
- [x] `lp user <you> permission set standards.home.limit.10 true` — limit rises without a restart
- [x] holding two numbered limits at once (5 and 10) — the **highest** wins, so grants
      from a rank and a donor perk stack instead of fighting
- [x] `/delhome base`
- [x] `/delhome wrongname` — lists your homes, same as `/home wrongname`
- [x] `/home nonsense` — lists what you do have
- [x] `/setwarp shop`, `/warp shop`, `/warps`, `/delwarp shop`
- [x] `/warp` bare — lists them
- [x] `/warp junk` — names the warps that exist, or says the server has none at all
      (that empty-list case used to read as a broken command)
- [x] `/setwarp` as TestBuddy **[2P]** — refused, it is op only

## Money

- [x] `/balance` — starts at ₡100
- [x] `/baltop`
- [x] `/pay TestBuddy 25` **[2P]** — both sides told, balances move
- [x] `/pay TestBuddy 999999` — refused, tells you what you actually have
- [x] `/pay` an **offline** player — resolved by name, and the notice arrives as mail from you
      when they next log in, so the money never appears unexplained
- [x] `/pay` yourself — the dry refusal
- [x] `/eco give TestBuddy 500`, `take`, `set`
- [x] `/eco give` an **offline** player
- [x] `/eco give @p 100` from a **command block** — the arena case; selectors resolve
- [x] `/eco give @a[tag=winner] 500` — a real tag, several winners at once — should work; that is why balances are save data
- [x] `/standards economy` — says Standards holds the money, priority -1000
- [x] `/bal` `/money` aliases

## Before you start: you are op, and op hides things

Several features default to `Default.OPS`, so **the person doing the testing is the one person who
never sees them.** That is not a bug — an admin should not wait five seconds to teleport — but it
means a check can read as "working" when you simply bypassed it.

Bitten three times already: the teleport countdown, the teleport cooldown, and the home limit.
Deny them on your own account for the duration, then unset when finished:

```
/lp user <you> permission set standards.teleport.instant false
/lp user <you> permission set standards.teleport.nocooldown false
/lp user <you> permission set standards.home.limit.unlimited false
/lp user <you> permission set standards.tpa.override false
/lp user <you> permission set standards.msg.override false
```

The last two are the ones that make `/tptoggle` and `/msgtoggle` look broken: staff can always
reach a player, deliberately, because otherwise anyone could make themselves uncontactable by
exactly the people who need to contact them.

```
/lp user <you> permission unset standards.teleport.instant
/lp user <you> permission unset standards.teleport.nocooldown
/lp user <you> permission unset standards.home.limit.unlimited
```

⚠ **LuckPerms does not grant its own commands to ops on this platform**, so `/lp` may answer with
nothing but its version banner — which looks identical to a malformed command. Console and RCON
always work, and `lp user <you> permission set luckperms.* true` from there fixes it permanently.

## Talking

- [x] `/msg TestBuddy hi` **[2P]** and `/r` back — vanilla owns `/msg`, we override it
- [x] `/w` `/whisper` `/tell` `/pm` `/m` — all ours, all behave the same
      (`/tell` and `/w` are vanilla **redirects** to the node we merged onto, so they only
      work if the merge replaced vanilla's command rather than losing to it)
- [x] `/r` with nobody to reply to
- [x] `/ignore TestBuddy` **[2P]** — their messages stop arriving
- [x] the ignore list survives a disconnect and a server restart
- [x] `/ignore` also hides their **public chat**, not only their `/msg`
- [x] …**and they cannot tell** — the sender still sees an ordinary "sent" confirmation,
      because an ignore that announces itself is a weapon rather than a shield
- [x] `/ignore` bare — lists who
- [x] `/mail send TestBuddy hello`, they `/mail read`
- [x] mail to someone **offline**, then they log in — announced, not marked read
- [x] `/mail clear`

## Away

- [x] `/afk` — announced to everyone
- [x] move — automatically back, no second command needed
- [x] `/afk gone for tea` — reason shown
- [x] `/lurk` alias
- [x] stand still for `afk.awayAfterSeconds` — marked away automatically

## Kits

- [x] equip yourself, `/setkit knight armour` — saves only what you are wearing
- [x] `/setkit starter hotbar`, `/setkit everything all`
- [x] stack size and durability are preserved — kits store whole `ItemStack`s,
      so components (enchantments, custom names) ride along too
- [x] `/kit`, `/kits`, `/showkit knight`
- [x] `/kit knight` with a full inventory — overflow lands at your feet, and says so
- [x] `/setkit daily all 1d` then `/kit daily` twice — cooldown refuses the second
- [x] `/delkit daily`
- [x] restart the server — kits and cooldowns survive

## Yourself

- [x] `/heal` while burning — healed **and** extinguished
- [x] `/feed` `/eat`, `/rest`
- [x] `/heal TestBuddy` **[2P]** — they are told who did it
- [x] `/speed 2`, `/speed 5` while flying (sets fly speed), `/speed walk 2`, `/speed reset`
- [x] `/speed 50` — refused, names the ceiling
- [x] die and respawn with speed set — it should still be set
- [x] die with `/god` or `/fly` on — no flicker of vulnerability or grounding before they
      re-apply; the state is written on Clone, before the client is told

## Stations — all denied by default, on purpose

- [x] `/craft` — refused for everyone including you
- [x] `lp user <you> permission set standards.craft true` — now works, no restart
- [x] `/anvil` `/grindstone` `/enderchest` `/ec` `/trashcan` `/disposal` `/workbench`
- [x] put something in `/trashcan` and close it — gone, unrecoverable

## Moderation

- [x] `/invsee TestBuddy` **[2P]** — rows 1-3 storage, row 4 hotbar, bottom row
      `H C L B _ O _ S A`
- [x] take an item — it really leaves them
- [x] shift-click everywhere — nothing duplicates, nothing lands in equipment
- [x] click a grey pane — inert, and shift-clicking one does nothing
- [x] every drag-and-drop route a person could think of, tried deliberately
- [x] `/mute TestBuddy 30m spam` **[2P]** — they are told, and told again each time they try
- [x] a muted player's `/msg` is blocked too, not just chat
- [x] and `/r`, `/w` and `/me` — every channel, not a list of the ones we remembered
- [x] `/unmute TestBuddy`
- [x] `/tempban TestBuddy 2h testing` — kicked with the reason; `/pardon` lifts it
- [x] `/tempban TestBuddy bananas` — refused, does not silently ban for 0 seconds

## Server and admin

- [x] `/gc` `/tps` `/lag` `/mem` — TPS coloured by health, memory, uptime, entities per dimension
- [x] bare `/smite` at a block
- [x] `/smite TestBuddy` **[2P]** — the target is told **nothing**, deliberately: unlike a
      switch, a smite is self-evident, and naming the caster turns an act of God into an
      admin with a command
- [x] `/standards reload` after editing `messages.yml` — text changes without a restart
- [x] edit `term.balance` to "credits" — every money message follows

## Permissions, with LuckPerms

- [x] TestBuddy (non-op, no grants) can use `/home` `/back` `/balance` `/pay` `/msg` `/afk` `/kit`
- [x] TestBuddy cannot use `/fly` `/god` `/invsee` `/eco` `/setwarp` `/bottom`
- [x] `lp user TestBuddy permission set standards.fly true` — works immediately
- [x] `lp group default permission set standards.craft true` — group-wide
- [x] deop yourself — `/fly on` refuses
- [x] deopped **while airborne** — you keep flying rather than dropping, and `/fly off` is
      still available so you can land. Losing a permission must never strand you

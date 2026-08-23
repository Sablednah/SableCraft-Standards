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
- [ ] `/fly TestBuddy on` **[2P]** — they are told who did it
- [x] `/fly @a off` — reports a count, not one name
- [x] `/god` / `/god on` — take damage, then don't. Try lava, fall, starvation
- [x] `/vanish` `/v` — see the vanish section below
- [ ] `/tptoggle off` **[2P]** — TestBuddy's `/tpa` to you says you are not accepting
- [ ] `/msgtoggle off` **[2P]** — same for `/msg`
- [ ] `/socialspy on` **[2P]** — you see their private messages

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

- [ ] `/tpa TestBuddy` — they get a prompt with clickable `[Accept]` / `[Deny]`
- [ ] click Accept — **you** are told immediately, and get a ticking action-bar countdown
- [ ] they are told you are arriving, and told again when you land
- [ ] `/tpahere TestBuddy` — accepted, **they** travel, and both sides get the right message
- [ ] accept, then walk during the countdown — cancelled, **and they are told why**
- [ ] `/tpa`, accept, then have the host run away — you land where they *ended up*
      (`tpaFollowTarget = false` to land where they were instead)
- [x] `/tpacancel` with nothing to cancel but a request waiting — offers an Accept button
- [x] `/tpaccept` when you are the one waiting — tells you so
- [ ] let one lapse — ⌛ message at both ends
- [x] `/tpalist` — who asked, which direction, seconds left
- [x] `/call` `/tpyes` `/tpno` aliases
- [x] `/tpa` yourself — refused, you are already there
- [ ] `/tpoffline TestBuddy` after they log out — op only

## Vanish **[2P]** — TestBuddy must not be op

- [x] `/vanish on` — invisible, not translucent
- [x] Tab — you are off their list
- [x] a mob near you stops pathing to you
- [ ] their `/msg` to you fails; your `/msg` to them works
- [x] spectral arrow — no glow outline
- [ ] they shoot you — arrows pass through, no bounce
- [x] they walk into you — no shove, they phase through
- [x] open a chest — **it should still animate for them.** Deliberate; the world's reactions stay
      visible, only the player is hidden
- [x] relog while vanished — still hidden
- [x] `/vanish off` — reappear cleanly, no ghost

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
- [ ] `/setwarp` as TestBuddy **[2P]** — refused, it is op only

## Money

- [x] `/balance` — starts at ₡100
- [x] `/baltop`
- [x] `/pay TestBuddy 25` **[2P]** — both sides told, balances move
- [x] `/pay TestBuddy 999999` — refused, tells you what you actually have
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
```

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
- [ ] `/ignore TestBuddy` **[2P]** — their messages stop arriving, **and they cannot tell**
- [x] `/ignore` bare — lists who
- [x] `/mail send TestBuddy hello`, they `/mail read`
- [ ] mail to someone **offline**, then they log in — announced, not marked read
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
- [ ] `/heal TestBuddy` **[2P]** — they are told who did it
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

- [ ] `/invsee TestBuddy` **[2P]** — rows 1-3 storage, row 4 hotbar, bottom row
      `H C L B _ O _ S A`
- [ ] take an item — it really leaves them
- [ ] shift-click everywhere — nothing duplicates, nothing lands in equipment
- [ ] click a grey pane — inert, and shift-clicking one does nothing
- [ ] `/mute TestBuddy 30m spam` **[2P]** — they are told, and told again each time they try
- [ ] a muted player's `/msg` is blocked too, not just chat
- [ ] `/unmute TestBuddy`
- [ ] `/tempban TestBuddy 2h testing` — kicked with the reason; `/pardon` lifts it
- [ ] `/tempban TestBuddy bananas` — refused, does not silently ban for 0 seconds

## Server and admin

- [x] `/gc` `/tps` `/lag` `/mem` — TPS coloured by health, memory, uptime, entities per dimension
- [x] bare `/smite` at a block
- [ ] `/smite TestBuddy` **[2P]**
- [x] `/standards reload` after editing `messages.yml` — text changes without a restart
- [x] edit `term.balance` to "credits" — every money message follows

## Permissions, with LuckPerms

- [ ] TestBuddy (non-op, no grants) can use `/home` `/back` `/balance` `/pay` `/msg` `/afk` `/kit`
- [ ] TestBuddy cannot use `/fly` `/god` `/invsee` `/eco` `/setwarp` `/bottom`
- [ ] `lp user TestBuddy permission set standards.fly true` — works immediately
- [ ] `lp group default permission set standards.craft true` — group-wide
- [x] deop yourself — `/fly on` refuses
- [x] deopped **while airborne** — you keep flying rather than dropping, and `/fly off` is
      still available so you can land. Losing a permission must never strand you

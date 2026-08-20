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

- [ ] `/fly` — toggles. `/fly on` twice says "already on" rather than silently succeeding
- [ ] `/fly off` while airborne — you drop, rather than hanging there
- [ ] `/fly TestBuddy on` **[2P]** — they are told who did it
- [ ] `/fly @a off` — reports a count, not one name
- [ ] `/god` / `/god on` — take damage, then don't. Try lava, fall, starvation
- [ ] `/vanish` `/v` — see the vanish section below
- [ ] `/tptoggle off` **[2P]** — TestBuddy's `/tpa` to you says you are not accepting
- [ ] `/msgtoggle off` **[2P]** — same for `/msg`
- [ ] `/socialspy on` **[2P]** — you see their private messages

## Getting about

- [x] `/top` from inside a cave — first safe floor **above you**, not the surface
- [ ] `/back` **while flying** — returns you to a mid-air point instead of refusing. Every back
      point a flying player makes is mid-air, so this is the normal case for anyone with `/fly` on
- [ ] `/sethome` while flying, then `/home` — same story for homes and warps
- [ ] `/setspawn` somewhere with no floor — saves, but warns you at the time
- [ ] Bed spawn boxed in, with water nearby — lands you on **dry ground** if any is in range;
      underwater only when there is genuinely nothing better
- [ ] `/top` in the Nether — lands **under** the bedrock, never on the roof
- [ ] `/top` under a bedrock or barrier roof — refuses, and names the block in the way
- [ ] `/bottom` over a sealed bedrock vault — refuses, rather than dropping you inside it
- [ ] `/bottom` **from** the Nether roof — still gets you down; the rescue outranks the barrier
- [ ] `/bottom` on open ground — still lands on the world bedrock floor (landing *on* it is fine,
      only scanning *through* it is refused)
- [ ] `/bottom` — op only
- [x] `/jump` `/j` — lands on top of what you are looking at, not inside it
- [ ] `/jump` at the sky — "nothing in range"
- [ ] `/back` after any teleport, and `/back 2`, `/back 3` up the trail
- [ ] `/back` after dying — refused unless you grant `standards.back.ondeath`
- [ ] `/spawn` with no `/setspawn` ever run — falls back to world spawn, does not error
- [ ] `/setspawn` then `/spawn`
- [ ] `/playerspawn` with no bed — says so; with a bed — goes there
- [ ] warmup: start any teleport and **walk** — cancelled, and you are told why
- [ ] warmup: start one and take damage — cancelled

## Teleport requests **[2P]**

- [ ] `/tpa TestBuddy` — they get a prompt with clickable `[Accept]` / `[Deny]`
- [ ] click Accept — **you** are told immediately, and get a ticking action-bar countdown
- [ ] they are told you are arriving, and told again when you land
- [ ] `/tpahere TestBuddy` — accepted, **they** travel, and both sides get the right message
- [ ] accept, then walk during the countdown — cancelled, **and they are told why**
- [ ] `/tpa`, accept, then have the host run away — you land where they *ended up*
      (`tpaFollowTarget = false` to land where they were instead)
- [ ] `/tpacancel` with nothing to cancel but a request waiting — offers an Accept button
- [ ] `/tpaccept` when you are the one waiting — tells you so
- [ ] let one lapse — ⌛ message at both ends
- [ ] `/tpalist` — who asked, which direction, seconds left
- [ ] `/call` `/tpyes` `/tpno` aliases
- [ ] `/tpoffline TestBuddy` after they log out — op only

## Vanish **[2P]** — TestBuddy must not be op

- [ ] `/vanish on` — invisible, not translucent
- [ ] Tab — you are off their list
- [ ] a mob near you stops pathing to you
- [ ] their `/msg` to you fails; your `/msg` to them works
- [ ] spectral arrow — no glow outline
- [ ] they shoot you — arrows pass through, no bounce
- [ ] they walk into you — no shove, they phase through
- [ ] open a chest — **it should still animate for them.** Deliberate; the world's reactions stay
      visible, only the player is hidden
- [ ] relog while vanished — still hidden
- [ ] `/vanish off` — reappear cleanly, no ghost

## Homes and warps

- [ ] `/sethome` then `/home` — the unnamed default
- [ ] `/sethome base`, `/home base`, `/homes`
- [ ] `/sethome` a fourth time — refused at the limit, with the overwrite hint
- [ ] `lp user <you> permission set standards.home.limit.10 true` — limit rises without a restart
- [ ] `/delhome base`
- [ ] `/home nonsense` — lists what you do have
- [ ] `/setwarp shop`, `/warp shop`, `/warps`, `/delwarp shop`
- [ ] `/warp` bare — lists them
- [ ] `/setwarp` as TestBuddy **[2P]** — refused, it is op only

## Money

- [ ] `/balance` — starts at ₡100
- [ ] `/baltop`
- [ ] `/pay TestBuddy 25` **[2P]** — both sides told, balances move
- [ ] `/pay TestBuddy 999999` — refused, tells you what you actually have
- [ ] `/pay` yourself — the dry refusal
- [ ] `/eco give TestBuddy 500`, `take`, `set`
- [ ] `/eco give` an **offline** player — should work; that is why balances are save data
- [ ] `/standards economy` — says Standards holds the money, priority -1000
- [ ] `/bal` `/money` aliases

## Talking

- [ ] `/msg TestBuddy hi` **[2P]** and `/r` back — vanilla owns `/msg`, we override it
- [ ] `/w` `/whisper` `/tell` `/pm` `/m` — all ours, all behave the same
- [ ] `/r` with nobody to reply to
- [ ] `/ignore TestBuddy` **[2P]** — their messages stop arriving, **and they cannot tell**
- [ ] `/ignore` bare — lists who
- [ ] `/mail send TestBuddy hello`, they `/mail read`
- [ ] mail to someone **offline**, then they log in — announced, not marked read
- [ ] `/mail clear`

## Away

- [ ] `/afk` — announced to everyone
- [ ] move — automatically back, no second command needed
- [ ] `/afk gone for tea` — reason shown
- [ ] `/lurk` alias
- [ ] stand still for `afk.awayAfterSeconds` — marked away automatically

## Kits

- [ ] equip yourself, `/setkit knight armour` — saves only what you are wearing
- [ ] `/setkit starter hotbar`, `/setkit everything all`
- [ ] `/kit`, `/kits`, `/showkit knight`
- [ ] `/kit knight` with a full inventory — overflow lands at your feet, and says so
- [ ] `/setkit daily all 1d` then `/kit daily` twice — cooldown refuses the second
- [ ] `/delkit daily`
- [ ] restart the server — kits and cooldowns survive

## Yourself

- [x] `/heal` while burning — healed **and** extinguished
- [x] `/feed` `/eat`, `/rest`
- [ ] `/heal TestBuddy` **[2P]** — they are told who did it
- [ ] `/speed 2`, `/speed 5` while flying (sets fly speed), `/speed walk 2`, `/speed reset`
- [ ] `/speed 50` — refused, names the ceiling
- [ ] die and respawn with speed set — it should still be set

## Stations — all denied by default, on purpose

- [ ] `/craft` — refused for everyone including you
- [ ] `lp user <you> permission set standards.craft true` — now works, no restart
- [ ] `/anvil` `/grindstone` `/enderchest` `/ec` `/trashcan` `/disposal` `/workbench`
- [ ] put something in `/trashcan` and close it — gone, unrecoverable

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
- [ ] `/smite TestBuddy` and bare `/smite` at a block
- [ ] `/standards reload` after editing `messages.yml` — text changes without a restart
- [ ] edit `term.balance` to "credits" — every money message follows

## Permissions, with LuckPerms

- [ ] TestBuddy (non-op, no grants) can use `/home` `/back` `/balance` `/pay` `/msg` `/afk` `/kit`
- [ ] TestBuddy cannot use `/fly` `/god` `/invsee` `/eco` `/setwarp` `/bottom`
- [ ] `lp user TestBuddy permission set standards.fly true` — works immediately
- [ ] `lp group default permission set standards.craft true` — group-wide
- [ ] deop yourself — `/fly` starts refusing

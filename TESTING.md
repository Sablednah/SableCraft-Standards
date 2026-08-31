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

## Permissions — the built-in handler (1.3) **[2P]** — TestThird must not be op

Set `permissionHandler = "standards:permissions"` in `neoforge-server.toml` and restart; config
hot-reload does not work on `/mnt/d`. **TestBuddy and Sablednah are both op level 4 in `ops.json`,
so neither can test this** — an op passes the op-gated default anyway. TestThird is the only
non-op of the three.

Walked 2026-08-31, in this order, and the order matters:

- [x] **baseline, before granting anything**: `/fly on` gives "Unknown or incomplete command",
      and `/home` gives our own "no homes yet" text. **Both, always.** A failed `requires()`
      removes a command from the tree entirely, so "correctly refused" and "the handler denies
      everything" look identical from the player's side — the everyone-node is the only thing
      that tells them apart
- [x] `/rank user TestThird set standards.fly true` → `/fly on` works **without reconnecting**.
      The server re-evaluates `requires()` on every command it parses
- [x] a **group** grant: `/rank group builder create`, `set standards.craft true`,
      `user TestThird group add builder` → `/craft` opens. This is the one that proves the
      feature, because `standards.craft` defaults to **nobody** — ungranted, not even an
      operator has it, so nothing but the grant can explain a crafting table
- [x] the command goes **white and tab-completes** the moment the grant lands. Before the fix it
      stayed red, reading "unknown command", until the player reconnected — while working if you
      pressed enter. Almost nobody presses enter through a red line
- [x] …but a line **already typed** in the chat box keeps its red until you touch it. Client
      behaviour, unreachable from a server: backspace one character and it repaints. Do not
      report this as the fix failing
- [x] `[BLD]` renders in chat once `standards:role` is in `chat.groupTagKinds` — the permission
      group showing up through the decorator seam with no chat code written for it
- [x] a non-op can turn a switch **off** without holding its node, and cannot turn it back on.
      `/god` was granted by console, TestThird turned it off themselves. Deliberate: switches
      persist across a logout, so gating the off-ramp would strand somebody in god mode forever
      the moment their permission changed
- [ ] the **home limit** falls back to `defaultLimit` for a non-op, and rises with
      `standards.home.limit.10` granted through `/rank` rather than LuckPerms
- [ ] an explicit **deny** on an everyone-node — `/rank user TestThird set standards.home false`
      — makes `/home` disappear for them
- [ ] a **restart** with grants in place: the player still has them, and `/standards permissions`
      reports the store's contents rather than an empty one

## Before a release

- [x] SnakeYAML is bundled jar-in-jar and declared in the metadata — the classic works-in-dev,
      fails-on-a-real-server trap
- [x] the server starts with **no permissions mod at all**, on NeoForge's `default_handler`.
      That is the commonest configuration in the world and the dev server has had LuckPerms
      since session three, so the path had never run
## Power (Factions)

Dev server is on `power.mode = "both"`, `maxPerPlayer = 10`, `perDeath = 2`,
`perMinuteOnline = 0.2` (five minutes a point), `freezeSecondsAfterDeath = 30`.

- [x] `/f power` — yours out of the max, and what the faction holds against its entitlement
- [x] die to a **mob** — lose 2, and be told; `/f power` agrees
- [x] die to a **player** — same
- [ ] die to **fall damage or lava** — lose **nothing**. Falling in your own lava is not a raid
- [x] kill mobs and watch power come back faster than the clock alone (`perExperience`)
- [ ] power does **not** regenerate for 30s after a death, then does
- [ ] power stops at the maximum and does not overshoot
- [ ] with everyone at full power, entitlement equals `chunksPerMember × members` exactly —
      nothing changed by switching power on
- [x] die enough that the faction goes **over** its entitlement — `/f status` says how many chunks
      are exposed, and every online member is told
- [x] an **enemy** takes one of those chunks — refused until they `/f enemy` you, refused unless it
      is on your **border**, and refused once your overreach reaches zero
- [x] the victim is told the moment land changes hands, not when they walk home
- [ ] a **peaceful** faction can neither take nor be taken from
- [ ] power survives a restart, and is **not** cleared by disbanding — leaving to wipe your losses
      is the first thing anybody would try
- [ ] `/f power <player>` for somebody else, including offline

## The standard (Factions)

Dev server: `regenWithStandard = 1.0`, `regenWithoutStandard = 0.5`,
`regenWithCapturedStandard = 0.25`.

- [x] place a banner on your own land **under open sky**, look at it, `/f standard` — it is
      designated, named in-world, and the message says its colour
- [ ] aiming at the **cloth** rather than the base now works — a standing banner's hit shape is a
      thin pole, so the ray used to pass over it
- [ ] a banner **indoors** or under any block is refused, and says why
- [ ] a banner on **somebody else's land**, or wilderness, is refused
- [x] `/f standard` with nothing in front of you reports where yours stands
- [ ] your faction's **name wears its banner colour** in `/f who`, `/f list` and `/f status` —
      while the map, borders and territory messages stay relation-coloured
- [x] power comes back **twice as fast** with a standard up as without
- [x] an **enemy can break it**, where they can break nothing else of yours
- [x] an ordinary banner — including a dropped standard replanted — is **not** breakable by a
      rival: the exception keys on the designated position, not on "is a banner"
- [ ] a **non-enemy cannot** — neutral, allied and peaceful all refused
- [x] taking it is announced **to the whole server**, and your faction is told the regen dropped
- [x] the dropped banner is **named for its owner** and keeps its pattern
- [x] the thief plants it on **their** land under sky, `/f standard` — flown as a trophy, they get
      the extra regen, and **you are told where it is**
- [x] **roof it over after planting** — the sky rule is re-checked, so the bonus stops and you
      are told once; take the roof off and it resumes
- [ ] destroy the banner some other way (piston, explosion) — the standard is cleared rather than
      merely paused, because there is nothing left to uncover
- [x] holding it in a **chest pays nothing** — the bonus is for flying it, not owning it
- [x] while an enemy **flies** your standard you cannot raise another — it names who has it
- [ ] if they **roof their trophy over**, it pays them nothing and you are free to raise a new one
- [ ] while somebody **holds it in hand**, you cannot raise another and the refusal names them
      **and their coordinates**
- [ ] the same banner in their **inventory rather than their hand** denies nothing
- [x] a carrier **glows red through walls**, and stops within a second of stashing it
- [ ] a player already on another mod's scoreboard team is left on it and glows white
- [ ] destroying or stashing a captured flag denies you nothing — you simply raise another
- [ ] the dropped standard is **fireproof and does not despawn**
- [x] take your own flag back and re-plant it — it is yours again, not a trophy
- [x] breaking and replacing your **own** banner re-roots it as the standard, so a flag can be
      moved without a command
- [ ] you may always break your **own** captured flag, whatever the relation and even if either
      side has gone peaceful
- [ ] disbanding a faction takes its flag with it, including one somebody else was flying
- [ ] all of it survives a restart

## Combat (1.1)

⚠ **An op has `standards.combat.bypass` by default, so you can never be blocked.** Test from
TestBuddy's or TestThird's side, or deny yourself the node.

- [ ] hit by a **mob**, then `/home` — still works (`pveBlocksTeleport = false` by default)
- [x] hit by a **player**, then `/home` — refused, with a countdown, in chat *and* on the action bar
- [ ] wait it out — `/home` works again without doing anything
- [x] shot with an **arrow** from range — still tags, because the owner is resolved, not the arrow
- [x] **fall damage** does not tag, so `/home` still works — the one that matters, and what stops
      a player trapped in a claim being stuck
- [ ] the same for drowning, freezing and fire
- [x] a tag is **extended, not overwritten** — covered by the self-test rather than by hand, since
      arranging the collision needs two clients, a zombie and four seconds of luck. To see it live,
      set `pvpSeconds = 120` and the countdown after a mob bite still reads ~110, not 8
- [x] **dying** clears it — respawn and `/home` immediately
- [x] **logging out and back in** clears it
- [ ] with `pveBlocksTeleport = true`, a mob hit *does* block, and the message says so
- [ ] an **op** is never blocked — the bypass node working as intended
- [ ] `combat.log = true` shows the classification: who, kind, cause, seconds

- [~] a **player** on that server, with no permissions mod: everyone-nodes work, op-gated ones
      work for an op and refuse for a non-op, and the home limit falls back to `defaultLimit`.
      *First two done 2026-08-31 with TestThird (see Permissions above); the home limit is still
      untested*
- [ ] the built jar on a server that is **not** the dev environment — `messages.yml` written,
      commands registered, no missing-class errors from the bundled YAML
- [x] a logo — `src/main/resources/standards.png` at 256x256, `logoFile` set, and it lands in
      the jar
- [ ] check the mods list actually renders it in game
- [x] `/back list` — numbered, with dimension, coordinates, distance and the command that made
      each entry; `/back <n>` then picks one
- [x] a teleport from **another mod** is labelled without that mod knowing — `/f home` shows as
      `/f home` in the trail
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
- [x] `/group leave` as an owner who is **alone** — just leaves, no second command to learn
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
- [x] walk out of it — the action bar says **Wilderness**, and says the name walking back in
- [x] `/f borders`, and separately **hold a compass** — the outline appears either way
- [x] the outline follows the *shape* of the land, not a grid on every chunk
- [x] `/f map` — the chat grid, with you as `+` in the middle
- [x] **`/f map item`** — a real map item, one pixel per chunk, edges bright and interiors dim
- [x] `/f map item 2` / `4` / `8` — zoomed, covering 64 / 32 / 16 chunks, outline still one pixel
- [x] an atlas must **not** repaint itself with terrain as you walk (it is locked) — carry one
      across a few hundred blocks and check it is unchanged
- [x] `/f autoclaim` — walking takes each chunk
- [x] `/f autoclaim` away from your own land with `mustBeConnected` — says why **once**, then
      stays quiet while it stays true
- [x] `/f autoclaim` through somebody else's territory — **silent**, no chat at all
- [x] `/f autoclaim` up to the claim limit — switches itself **off** and says so
- [x] borders stand on the ground rather than at your feet — walk a claim across a hill
- [x] `/f request <name>` from a factionless player — officers online are told, and only them
- [x] `/f requests`, then `/f accept` — they are in, everyone is told, and the request is gone
- [x] `/f decline` — they are told, and they can ask again
- [x] with `officersMayAccept = false`, an officer cannot `/f accept`, `/f decline` or even
      `/f requests`
- [x] a request is shown only to whoever can answer it — with officers locked out, the leader
      alone is told
- [x] ask a faction, then found your own — the request is gone, and `/f requests` no longer
      shows you (the two-player version of "join one, the other clears")
- [x] ask two factions, join one — the other request is gone (three accounts: two factions and a
      factionless asker)
- [x] with `officersMayAccept = true` again, an **officer** is told a request arrived, can read
      `/f requests` and can answer it — the gate governs who hears, not only who acts
- [x] `/f status` with nothing going on — says so, rather than an empty header
- [x] `/f status` after one side offers an alliance — **offered to us** on their side, **waiting
      on** on yours, and the two lines do not swap
- [x] `/f status` after a one-sided `/f enemy` — **you declared on** for them, **declared on you**
      for the target, and both lines once it is mutual
- [x] a second player tries to break a block in your claim — refused, and told whose it is
- [x] `/f ally <them>` — says the alliance is pending until they offer back; then they do
- [x] `/f enemy <them>` — effective immediately, one-sided
- [x] `/f peaceful` — cannot declare enemies, and cannot be declared upon
- [x] `/f sethome` outside your land — refused; inside — works, and `/f home` uses the warmup
- [x] `/f claim` past the limit — refused, and names the per-member arithmetic
- [x] `/f claim` a chunk not touching your land — refused (`mustBeConnected`)
- [x] faction tag in chat, after adding `factions:faction` to `groupTagKinds` — beside the group
      tag, both rendering, neither swallowing the other
- [x] a stranger right-clicks a **chest** in your claim — refused, and told whose it is
- [x] the same for a door, a button, a lever and a furnace
- [x] a **pressure plate** still works for them — the deliberate hole
- [x] item frames and armour stands: cannot take from, cannot break
- [x] with `anyoneMayRotateFrames = true`: a stranger can **turn** an occupied frame, still
      cannot take from it, and still cannot fill an empty one
- [x] an **ally** can open the door and the chest, and **cannot** build or break
      (`alliesMayBuild = false`)
- [x] a **creeper** blows up inside your claim — no blocks broken, but you still take the damage
- [x] a creeper on the **wilderness side** of your wall craters the wilderness and leaves the wall
- [x] TNT lit outside and thrown in does nothing to claimed blocks (`blockTnt = true`)
- [x] TNT cannot be **placed** inside a claim at all — the place guard catches it first
- [x] a refused **placement** leaves the item in your hand — no phantom consumption that only a
      relog undoes
- [x] with `blockTnt = false`, TNT breaks claimed blocks again — the siege tool working
- [x] and with TNT allowed, a **creeper** is still blocked — separate config, separate branch
- [x] animals are still feedable in somebody else's claim — adults to breed, babies to grow
- [x] a denied click puffs **red particles at the block** and thuds, seen only by them
- [x] a denied lever or door does not stay looking flipped — the client's guess is corrected
      *(a door still swings and slams and a button still bounces: that IS the correction landing,
      and client-side prediction cannot be prevented from the server)*
- [x] `/f chat` cycles public → faction → ally → public, and each says where you are talking
- [x] a **muted** player cannot talk in faction chat, by `/f chat` or by `/f c` — the whole reason
      it goes through the router
- [x] `/f c <message>` reaches faction members and nobody else; `/f ca` reaches allies too
- [x] faction chat clears your AFK marker, same as public chat
- [x] the channel resets to public on reconnect — never come back still talking to your faction
- [x] `/f chatspy` shows an outsider's faction chat, marked as overheard
- [x] a spy does **not** count as an audience — a lone member still hears "nobody else is
      listening", or the message would quietly announce that somebody is watching
- [x] `/f money deposit` / `withdraw` / `pay`, and the balance on `/f status`
- [x] a bank cannot be pushed negative — an over-withdrawal and an over-payment both refuse
      rather than clamping
- [x] a **non-officer** cannot withdraw or pay, but can still deposit
- [x] `/f money pay <faction> <amount> <reason>` — the reason reaches them, and a colour code in
      it arrives as text
- [x] `/f status` with **no faction** lists invitations you can act on and requests you are
      waiting on, and says so plainly when there are neither
- [x] `/pay <player> <amount> <reason>` in Standards — shown to both, and carried in the mail when
      they are offline
- [x] set `claimCost` above 0, restart: `/f claim` spends from the bank, refuses when short, and
      `/f unclaim` refunds at the position price
- [x] restart — factions, claims, relations, tags, homes **and bank balances** all survive
      (10 factions, 67 claims, 4 allies, 2 enemies, 2 peaceful, 1 home, 903 in the bank)

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

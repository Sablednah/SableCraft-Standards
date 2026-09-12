# The optional client

**Status: shipped in Standards 1.8.0, 2026-09-12.** The seam, the bar and the panel area are built
and driven by hand; adopted by LegendQuest's StoryTeller on 2026-09-07 (five actions) and by
LegendQuest itself on 2026-09-11, when its character pane moved onto `api/panels`.
What is built: the optional channel and its capability payload, `api/actions`, `/actions` and
`/actions all`, the drawn bar on the inventory screen with right-click children and categories,
`ClientActions.run`, keybinds, `Actions.registerScreen`, `Actions.registerHandler` and
`Actions.registerClientState`, the **panel seam** (`client/panels`, see `PANELS-API.md`) with its
one-pane-at-a-time arbitration, and Factions' faction panel drawn through it. What is not:
`Area.BUTTON_STRIP`, deliberately — `PANELS-API.md` §6 — and the live map, which is `MAP-API.md` and
a separate plan.

A client half for Standards and Factions: quick toggles beside the inventory, and real GUIs for
faction management. Optional in the strict sense — a vanilla client loses nothing but convenience.

---

## 1. The promise this must not break

Decision 2 of `CLAUDE.md` is that **every command works for an unmodified client**, and
`network/StandardsNetwork` is deliberately empty. That is the mod's central promise and it is worth
being precise about what it does and does not say.

It is a promise about **capability**, not about the wire being unused. Adding payloads does not
break it. Adding a *feature you can only reach with the client mod* does.

So the rule, and everything below follows from it:

> **A GUI may only present information and actions the server would have given you anyway.**
> Same answers, nicer surface.

Concretely: `/f map item terrain` still hands a vanilla player a real filled map — the client mod
may render the same data as a live overlay. `/f raids top` still prints to chat — the client mod may
draw it as a sortable panel. A button marked *Fly* runs `/fly on`. Nothing new becomes possible.

**The test to apply to any proposed feature:** describe it to somebody on a vanilla client. If the
answer is "you can't do that", it does not go in.

---

## 2. Built in, not a separate mod

Two jars was the alternative and it loses on every axis that matters here.

- **Versions.** Three Minecraft lines already means three artifacts per mod per release. A separate
  client mod doubles that to six, each pair of which must match. The cross-version work in
  `CROSS-VERSION.md` is already the most tedious part of a release.
- **Support.** "Which of these do I need?" and "I have the client one and it does nothing" are
  questions a single jar never generates. The CurseForge page says *works on any client, better on
  yours* and that is the end of it.
- **Precedent.** `../LegendQuest-ReForged` and `../ZombieMod` both ship one jar with a client half
  and it has caused no trouble. This is the fourth and fifth mod in the series doing what the
  second and third already do.

So: one jar each, with the client code behind `Dist.CLIENT` registration, exactly as LegendQuest
does it. A dedicated server never loads the class.

**The cost, stated honestly:** GUI code is the most version-fragile part of Minecraft, and this is
the first thing either mod has written that is. `CROSS-VERSION.md` currently reports small, boring
divergence sets — a `ChunkPos` accessor here, a colour collection there. Screens, layouts and
rendering will not be that. Expect the client half to be the majority of every future port's work,
and keep it small for that reason alone.

---

## 3. The trap, before anything else is written

**Every clientbound send goes through `neoforge/Net.sendIfAble`.** Not most. Every one.

`PayloadRegistrar.optional()` makes the *handshake* tolerant; it does **not** make sends droppable.
`PacketDistributor.sendToPlayer` throws synchronously on the server thread for a payload the
receiver never negotiated, and from a login handler that takes vanilla's login flow with it and
kicks the player with *"Invalid player data"* — a message naming nothing. Channels are agreed during
the configuration phase, so there is no later event that helps.

LegendQuest found this the hard way. ZombieMod re-found it. This will be the third time if the guard
is not written first, and the symptom will be *vanilla players cannot join*, which is the worst
possible failure for a mod whose whole pitch is that vanilla players are first-class.

⚠ **And `optional()` returns a clone, not a mutation.** The obvious three-line version registers a
**required** channel:

```java
var r = event.registrar(VERSION);
r.optional();          // clone made, and thrown away
r.playToClient(...);   // registered on the ORIGINAL — not optional
```

That compiles, reads correctly, and kicks every vanilla player at login. It is the same failure as
forgetting `sendIfAble`, arriving through a different door, and it is why the registration is one
chained expression that must never be split.

`network/StandardsNetwork` stops being empty here. The comment saying it is deliberately empty
should be replaced with one saying what replaced it and why, rather than deleted.

---

## 4. Buttons send commands

**A button runs the command a player would have typed.** `player.connection.sendCommand("fly on")`,
and nothing else.

This is the decision that keeps the whole feature small, and it is worth defending because the
instinct is to define a tidy `ToggleFlyPayload` instead:

- **No new serverbound payloads at all.** The only wire traffic invented here is clientbound.
- **The server path is identical to typing it.** Permissions, cooldowns, warmups, the switch
  off-ramp rule, config-unregistered commands, `/socialspy`, logging — every one of them already
  works, and works the same, with no second code path to keep in step.
- **A command that is off in config is simply not there.** Decision 7 unregisters commands rather
  than refusing them; a button that sent a payload would have to reimplement that check and would
  drift from it.
- **It degrades correctly.** Worst case the player sees the same red *unknown command* they would
  have seen typing it.

The button is a macro. That is not a compromise, it is the correct architecture for a feature whose
stated rule is *same answers, nicer surface*.

---

## 5. The one payload that matters: what may I do?

The client cannot guess which buttons to draw. Showing a *Vanish* button to somebody without
`standards.vanish` is the "granted command renders red" bug in reverse — the player clicks, it
fails, and they report the mod broken.

So there is **one clientbound payload**, sent on join and on every permission change:

```
StandardsCapabilities {
    Set<String> actions;      // "fly", "god", "vanish", "home", "back", ...
    Map<String,String> hints; // optional: "home" -> "3 of 5", "back" -> "2 places"
}
```

Two things about this:

- **It is a list of what to draw, not a permission cache.** The client never decides anything with
  it; it decides what to *show*. The server re-checks on the command, as it always would. A stale
  or forged capability set gets you a button that fails, which is exactly what typing the command
  would do.
- **Resending is already solved.** `PermissionCommands.refresh` resends the command tree on every
  permission edit — to one player for a user edit, to everyone for a group edit — precisely because
  the client's copy of the tree goes stale. The capability payload rides the same trigger. Getting
  this wrong produces the identical symptom that fix exists for: a player told they have something,
  clicking it, and being refused.

Everything else the panels need — faction roster, balances, raid records — is **request/response**,
sent only when a screen is open, and never pushed. A panel nobody is looking at costs nothing.

---

## 6. The seam registers *actions*, not buttons

Standards owns the bar. It does not know what is on it.

**An action, not a button**, because StoryTeller wants the same things on **keybinds** as well —
possession in and out, drift in and out, and a *next player* step. A Storyteller hops between those
constantly during a scene, and reaching for a mouse each time is the sort of friction that makes a
tool go unused. A button and a keybind are two triggers for one action, so the seam registers the
action once and both triggers find it.

This is the same shape as `api/chat` and `api/groups`, and for the same reason: Factions is a
separate mod and a separate release, LegendQuest will want a party button, and none of them should
require an edit to Standards to appear.

```java
Buttons.register(new ButtonSpec(
        Identifier.of("factions", "claim"),
        /* priority */ 20,
        /* icon     */ ...,
        /* tooltip  */ "msg.factions.button_claim",
        /* shown if */ caps -> caps.has("f.claim"),
        /* runs     */ "f claim"));
```

Ordering follows the rule already established for chat decorators, because a second rule would be a
second thing to remember: **priority is closeness to the anchor.** Standards' own switches sit
nearest the inventory, other mods' buttons stack outward from there.

Additive, like chat decoration and unlike the economy — several mods may contribute actions without
contradiction. `SelfTest` should assert the ordering from both ends, as it does for chat affixes,
because getting it backwards looks fine until a second mod registers.

### Keybinds: each mod owns its own, and that is forced

**A `KeyMapping` must be registered at client startup**, in `RegisterKeyMappingsEvent`, before
anything knows which server it is talking to or what that player may do. So an action registered at
runtime *cannot* conjure a keybind for itself, and the tempting design — "register an action, get a
bindable key free" — is not available.

The alternative, pre-registering a pool of *"Standards action 1…8"* keys for users to assign in a
screen, is worse than it sounds: the controls list fills with placeholders that mean nothing until
bound, and a modpack shipping defaults cannot name what it bound.

So: **each mod registers its own `KeyMapping`s the ordinary way**, and Standards' contribution is
that pressing one goes through the same action:

```java
// StoryTeller's client, in its own key handler
if (POSSESS_KEY.consumeClick()) {
    ClientActions.run(Identifier.of("storyteller", "possess"));
}
```

`ClientActions.run` does what the button does — checks the capability set, sends the command, and
says something useful if the player may not. Without it every mod reimplements that check and they
drift; with it a keybind and a button cannot disagree about whether an action is available.

`consumeClick()` rather than `isDown()`, so holding the key fires once. A keybind that repeats is a
command sent sixty times a second, and the first thing anybody would notice is the server rate
limiting them off it.

**Keybinds are unbound by default.** A mod claiming keys on install is how conflicts start, and
Standards' own switches are not worth a key to most players — the ones that are (possession, drift)
belong to a mod whose users have explicitly installed it for that.

---

## 7. Where it goes on screen

**Left of the inventory, top-aligned**, in the FTB style. The right-hand column belongs to JEI or
REI in nearly every modpack, and fighting them is a fight we would lose weekly.

Four things that will each cost an evening if not planned for:

- **The recipe book moves the inventory.** Opening it shifts the whole panel right by half its
  width. The bar must be positioned relative to the screen's actual left edge each time it changes,
  not once at init.
- **Small windows.** At GUI scale 4 on a 1280-wide window there may be no room to the left at all.
  The bar needs a fallback — collapse to a single expander button, and a config to move it.
- **We are not the only guest.** Other mods add buttons to the same corner. Leave a configurable
  offset rather than assuming the corner is ours.
- **Vertical space.** A column of eight buttons is fine; twenty is a wall. If the seam is open to
  other mods, the bar needs to scroll or fold past a threshold, and that should exist before the
  first mod adds a ninth.

---

## 8. What to actually build

### StoryTeller's shape, which is the one driving this

Worth stating because it is the demanding case and the seam should fit it:

| Action | Trigger | Why a keybind |
|---|---|---|
| possess / release | toggle | entered and left constantly through a scene |
| drift in / out | toggle | the survey view, paired with possession |
| next player | repeat | stepping through a cast; a button would mean a mouse trip per player |

Two toggles and a stepper. Toggles want the button to show **state** — lit when possessing — which
is the same thing `Toggle`'s tri-state buys the switch commands, and the reason a button beats a
chat command for anything you are in or out of.

The stepper is the one that argues hardest for keybinds: *next, next, next* through a cast is a
rhythm, and a rhythm through a GUI is not one.

### Standards — the quick toggles

The tri-state switches earn a button more than anything else in the mod, because `Toggle` means a
button can show *state* rather than just fire an action: lit when on, dim when off.

| Button | Runs | Shown when |
|---|---|---|
| Fly | `/fly on` / `/fly off` | `standards.fly` |
| God | `/god on` / `/god off` | `standards.god` |
| Vanish | `/vanish on` / `/vanish off` | `standards.vanish` |
| Home | `/home` — a menu if they have several | `standards.home` |
| Spawn | `/spawn` | `standards.spawn` |
| Back | `/back` | `standards.back` |
| AFK | `/afk` | always |

**And one that is only sometimes there:** a *TPA pending* button that appears with the requester's
name and accepts or denies. That is the single best argument for this whole feature — decision 8
exists because a teleport nobody can see the state of gets re-run by both parties, and a button that
appears exactly when there is something to answer is the same fix taken one step further.

### Factions — management

The roster is the piece that genuinely wants a GUI. Everything else is chat working fine.

- **Roster** — members, ranks, online state, last seen. Promote, demote, kick, invite. This is the
  `/f who` output that people actually re-read, and the one place where a list with buttons beats a
  wall of text outright.
- **Relations** — allies, enemies, and *pending offers in each direction*. The two halves of an
  unanswered alliance are the thing players most often misread in chat, and a panel showing both
  columns fixes it by construction.
- **Claims and power** — held against entitlement, overreach shown as the thing it is: how much
  land is currently takeable. A number nobody notices in chat is a red bar nobody misses.
- **Bank** — balance, deposit, withdraw, and the claim cost of the next chunk.
- **Standard and raids** — where the flag is, whether it flies, trophies held, the raid record. Most
  of this shipped in 1.3.0 as chat output and reads well as a panel.
- **The map** — the terrain survey rendered live rather than as an item. Same data, and the
  strongest visual argument for installing the client half.

### Deliberately not

- **Anything a vanilla player cannot reach.** Stated above; restated because it is the rule that
  will be under pressure the first time a GUI-only idea is genuinely good.
- **A minimap that renders unloaded chunks.** The terrain map's whole design is that it reads only
  what is in memory. A client-side minimap that asks the server for terrain reintroduces exactly the
  cost that design avoided.
- **Client-side state the server does not have.** Waypoints, notes, drawings. The moment the client
  is the only place something lives, it is lost on a reinstall and invisible to everybody else.
- **Replacing chat.** The chat output is the product for most servers and must stay first-class.

---

## 9. Testing, and the category this will produce

`SelfTest` has no client. It cannot see any of this, and the two bug families in `CLAUDE.md` are
both waiting here — *first real input* and *the server is right and the client was never told*.

What the self-test **can** do, and should:

- the button seam's ordering, from both ends, as the chat decorators are tested;
- that the capability set derives from the same `PermissionRules` the commands use, against
  fixtures — not a duplicate of the logic;
- that every registered button's command string actually parses. That check would have caught the
  `word()` trap four times over, and a button whose command does not parse is a button that renders
  a red error for everybody who clicks it.

What needs real clients, and belongs in `TESTING.md` before any of it is written:

- **a vanilla client joins and plays with the client mod present on other players.** This is the
  one that must never break, and the failure mode is a kick during login;
- a client with the mod on a server **without** it — the bar must simply not appear;
- a permission granted mid-session — the button appears without a reconnect, the same way
  `PermissionCommands.refresh` makes the command usable;
- a permission revoked mid-session — the button goes;
- the recipe book, GUI scale 1 through 4, and a window narrow enough that there is no room.

---

## 10. Order of work

1. `Net.sendIfAble` and the payload registration, with the vanilla-client join test **first**. It is
   the only part that can break existing players, so nothing else should exist until it is proven.
2. The capability payload, riding `PermissionCommands.refresh`.
3. The action seam and its self-test, with **one** action — Fly — proving the whole path: a button
   that draws, a `ClientActions.run` that fires, and a capability set that hides it when it should.
4. The rest of the Standards toggles, then the TPA-pending button.
5. **A keybind, on that same one action**, before there are many — the cheapest moment to find out
   whether `ClientActions.run` is the right shape is while exactly one thing calls it.
6. Factions' buttons, registered through the seam from the other repo. That is the real test of
   whether the seam is legible to somebody who did not write it — the same evidence
   `VANISH-API.md` counts, where LegendQuest wired both halves without being asked twice.
7. The faction panels, roster first.
8. The live map last. It is the most impressive and the least necessary, and it is the piece most
   likely to be rewritten by a port.

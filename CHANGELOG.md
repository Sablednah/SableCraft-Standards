# Changelog

## 1.0.0 — first release

Server essentials for NeoForge 1.21.11, and an economy other mods can drive. **Everything works on
an unmodded client** — no client mod, no resource pack, nothing to install for players.

### Why it exists

Two complaints, taken seriously:

- **FTB Essentials has no `/top`.**
- **Both it and EssentialsX make `/fly` and `/god` pure toggles**, with no explicit `on` / `off` —
  which makes them unusable from a command block, a datapack, or another mod's skill. A toggle in
  that position is a coin flip that grounds the player mid-air half the time.

So every switch in this mod takes `on` / `off` / `toggle`, with or without a target:

```
/fly            /fly on            /fly @a on
/god Steve      /god Steve off
```

That one decision is why the rest of the mod looks the way it does.

### What you get

**Movement** — `/top`, `/bottom`, `/jump`, `/back` with a multi-step trail, `/spawn`, `/setspawn`,
`/playerspawn`. Warmups with a ticking countdown, cancel-on-move, cancel-on-damage, and a
safe-landing search that refuses rather than dropping you in lava.

**Homes and warps** — per-player limits granted by permission node, an overwrite escape hatch when
you hit the ceiling, and nothing deleted when an admin lowers a limit.

**Teleport requests** — `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpalist`, with clickable
Accept and Deny buttons and, crucially, **narration to both parties**: told when it is accepted,
told when they land, and told *why* when it is cancelled.

**Economy** — `/balance`, `/pay`, `/baltop`, `/eco`, with a provider API. Standards registers at a
negative priority so a dedicated economy mod displaces it without either side knowing.

**Groups** — a lightweight built-in group system with shared homes, chat tags and teleport relief
between members, published through an API other mods can provide instead.

**The rest** — kits defined in-game by equipping yourself, mail, `/msg` with ignores and social
spy, mutes and tempbans, `/vanish`, `/invsee`, portable workstations, AFK with an idle timer,
`/smite`.

### Things worth knowing

**Every string is yours.** `config/standards/messages.yml` holds every player-facing message, and
`{term.*}` keys let you re-skin vocabulary wholesale — set `term.balance` to "credits" and every
money message follows. New keys merge in on upgrade without touching your edits.

**Commands you turn off are unregistered, not refused.** A greyed-out tab-complete entry for
something the server will never run is a lie the player discovers by trying it.

**`/vanish` is genuinely hidden** — off the tab list, unhittable by arrows, unpushable, invisible
to `/msg` and absent from tab-completion. The world's reactions stay visible on purpose: a chest
you open still animates.

**Decorated chat lines are not signed.** If a chat decorator is registered, those lines are
composed server-side and sent as system messages, so client-side reporting and blocking do not
apply to them. Undecorated chat is untouched. See `CHAT-API.md`.

### With no permissions mod

Everything works, because Standards asks NeoForge's `PermissionAPI` rather than any particular
mod. But NeoForge's default handler cannot *grant* anything, so the five portable workstations —
which default to "nobody" on purpose — never appear at all. `stationAccess` and
`backOnDeathAccess` let such a server say who they are for. A permissions mod overrides both.

### For other mods

Five seams, all soft dependencies: `Economy`, `Chat` decorators and routers, `PlayerSwitches`,
`Groups` and `Claims`. Plus `Lang.contribute()`, so a mod's strings land in the same
`messages.yml` — one catalogue for the whole server.

### How it was tested

326 self-test checks run on every server start, a 150-item hand walkthrough by two and sometimes
three players, and an RCON battery of 57 live assertions.

Fifteen real bugs came out of the walkthrough that the self-tests could not see — among them
`/top` landing on the Nether roof, `/back` being impossible for a flying player, `/me` bypassing
mutes, colour-code injection through player text, and the self-test leaking its own fixtures into
the live server.

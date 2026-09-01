# Built-in permissions

Groups, grants and ranks, for a server that has not installed a permissions mod.

**Status: built 2026-08-31, and driven end to end against the dev server.**
`core/PermissionRules` (the resolver, pure), `neoforge/permissions/` (`PermissionStore`,
`StandardsPermissionHandler`, `PermissionRoles`) and `neoforge/commands/PermissionCommands`.
Under the self-test at **521 checks**, exercised over RCON with the handler actually selected, and
then **proven with two clients**: a real non-op was refused an op-gated node, kept an
everyone-node, and was granted `standards.craft` — which defaults to *nobody*, so not even an
operator holds it ungranted — and got a crafting table. Specified 2026-08-25, shipped in **1.3.0**
on all three Minecraft lines.

⚠ **521 is the count with this handler active. A server where it is not reports 491, and that is
correct rather than a regression** — the `/rank` parse checks, the `PermissionStore` round trip and
the promotion move all stand down when something else is answering, because there is no store for
them to act on. Both numbers are the tree behaving. **Compare PASSED/FAILED, never totals.**

## The arbitrator already exists, and it is NeoForge

Worth settling first, because the obvious framing is wrong. This is **not** a Vault: that job is
taken, and taken correctly.

```java
// PermissionAPI, on server start
Identifier selected = Identifier.parse(NeoForgeServerConfig.INSTANCE.permissionHandler.get());
IPermissionHandlerFactory factory = availableHandlers.get(selected);
PermissionAPI.activeHandler = factory.create(nodesEvent.getNodes());
```

`PermissionAPI` **is** the facade. Handlers register themselves at `PermissionGatherEvent.Handler`,
and the **server owner chooses which one is active** in `neoforge-server.toml` — an explicit
setting, not a priority contest. LuckPerms is one registrant among however many are installed.

Standards already calls that facade for every check, which is why it behaves identically under
LuckPerms and without it. Building a second arbitration layer would duplicate NeoForge and fight
LuckPerms for a slot that is not contested in the first place.

**So this document specifies one more handler, not a system.**

## The gap it fills

NeoForge's `DefaultPermissionHandler` is four lines of substance:

```java
public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, ...) {
    return node.getDefaultResolver().resolve(player, player.getUUID(), context);
}
```

Every answer is the node's own default. Which means: **on a server with no permissions mod, you
cannot grant anybody anything.** `standards.fly` is op-or-nothing. A trusted regular cannot have
flight, a donor cannot have ten homes, and a builder cannot have `/craft` without being made an
operator — which hands them `/stop` as well.

That is the same gap the built-in economy fills, and it deserves the same answer: ship something
that works, and step aside the moment a real one arrives.

## Scope, and the trap

LuckPerms has contexts, temporary nodes, tracks, inheritance weights, SQL and MySQL backends, a web
editor, an import/export format and a decade of edge cases. **Chasing any of that loses.** A
half-built LuckPerms is worse than none, because it looks like it will keep up and then does not.

The pitch is *enough to run a small server*, and the honest advice in the README should be: if you
outgrow this, install LuckPerms and change one config line.

| in — all built | out — and staying out |
|---|---|
| groups, with inheritance | contexts (per-world, per-dimension, per-server) |
| players in groups | temporary nodes and expiry |
| per-player grants | tracks and promotion ladders |
| explicit deny, beating grant | weights, priorities, meta values |
| `*` wildcards — `standards.*`, `standards.home.*` | SQL backends, web editor |
| a default group everyone gets | import from LuckPerms |
| a chat tag per group, through the existing seam | |

## Shape

```java
public final class StandardsPermissionHandler implements IPermissionHandler {
    public Identifier getIdentifier();                    // standards:permissions
    public Set<PermissionNode<?>> getRegisteredNodes();
    public <T> T getPermission(ServerPlayer, PermissionNode<T>, PermissionDynamicContext<?>...);
    public <T> T getOfflinePermission(UUID, PermissionNode<T>, PermissionDynamicContext<?>...);
}
```

Registered at `PermissionGatherEvent.Handler`. **Dormant unless chosen** — an owner sets
`permissionHandler = "standards:permissions"` in `neoforge-server.toml`, and anybody running
LuckPerms is untouched.

### Resolution order

Built as **tiers**, nearest the player first, which collapses the original five rules into two.
`core/PermissionRules` does the deciding and imports nothing from Minecraft.

1. The player's own grants and denials.
2. The groups they are directly in.
3. Those groups' parents, then *their* parents, outward — breadth-first.
4. The default group, which everybody is in without being put there.
5. The node's **own default resolver** — so op still means op, and everyone-nodes still work, for
   anyone nothing has been said about.

**The first tier that says anything wins outright.** That is what makes "an explicit deny on the
player beats everything" and "a deny in a group beats a grant in its parent" the same rule instead
of two, and it is why they cannot drift apart.

**Within one tier, the most specific pattern wins**: exact `standards.fly` beats
`standards.home.*` beats `standards.*` beats `*`.

**A tie inside one tier resolves to no.** Somebody in both `donor` (granting) and `guest`
(denying), at the same distance, with no weights to separate them — quietly taking the permissive
half of a contradiction is the wrong direction to guess in. The self-test asserts it with the
scopes in both orders, because a rule that only holds when the denying group happens to be listed
first is not a rule.

**A trailing wildcard covers the bare node too**: `standards.home.*` matches `standards.home`. The
alternative is defensible and everyone who has met it has been baffled by it — granting
`standards.home.*` and finding `/home` still refused reads as the system being broken.

Falling through to the default resolver at step 5 is what makes this safe to switch on: a server
that enables it and grants nothing behaves exactly as it did before. That property is asserted
first in the self-test, because it is the difference between an owner trying this and an owner who
tried it once.

**Booleans only.** An integer, string or component node falls through to its own resolver
untouched. Every node Standards ships is a boolean, and inventing an answer for another mod's
typed node would be worse than declining to have one.

### Offline answers matter

`getOfflinePermission` exists and Standards uses the offline path for `/eco give`, `/baltop` and
home-limit checks on absent players. Group membership therefore has to live in `SavedData` keyed by
UUID rather than in a player attachment — the same reasoning as homes and balances, and the same
mistake to avoid.

## The part worth building it for

**Permission groups and the Groups API should be the same groups.**

We already decided staff roles are a group kind — non-exclusive, since a moderator can also be a
builder. If a permission group *is* a `standards:role` group, then granting somebody the moderator
group gets them, at once and with no second list to keep in sync:

- their permission nodes;
- a chat tag, through the decorator seam;
- membership visible to any mod through `Groups.all(player, ROLE_KIND)`;
- staff chat and "who is on" for free, since a group is already a channel.

LuckPerms cannot do the back half of that. Its groups are a permissions concept: they do not render
chat tags without its own prefix system, and nothing else on the server can query them. **That is
the reason to build this rather than a reason to compete** — it is not "LuckPerms but ours", it is
"the groups you already have, that also carry permissions".

## Commands

```
/rank groups                              every group on the server
/rank group <name> create | delete | info
/rank group <name> set <node> [true|false]
/rank group <name> unset <node>
/rank group <name> parent add|remove <other>
/rank group <name> tag <tag|->            the chat label, if roles are rendered
/rank user <player> group add|remove <group>
/rank user <player> set <node> [true|false]
/rank user <player> unset <node>
/rank user <player> info                  their groups, their grants, and what it adds up to
/rank check <player> <node>               one node, and WHICH RULE said so
```

`/rank user info` and `/rank check` explaining **which rule answered** are what earn the feature.
Every hour lost to a permissions system is spent asking "why does this player have that", and one
that can only say yes or no makes you bisect it by hand. Real output:

```
> rank check Sablednah standards.home.others
 standards.home.others = yes (from donor, via standards.home.*)
> rank group donor set standards.home.others false
> rank check Sablednah standards.home.others
 standards.home.others = no (from donor, via standards.home.others)
> rank check Sablednah standards.god
 standards.god = no (nothing set — the node's own default)
```

That last line matters as much as the other two: a bare `no` reads as a denial somebody
configured, and sends an admin looking for a rule that does not exist.

### It is `/rank`, and also `/perm`

Both are registered, as real trees rather than brigadier redirects — the reason the rest of the mod
builds aliases that way is that a redirect node carries no command of its own, so the bare form
fails while every subcommand works.

**`/perm` is not reliably ours.** LuckPerms claims it as an alias of `/luckperms`, along with
`perms`, `permission`, `permissions` and `lp`. On the server this was built for — no permissions
mod at all — that never arises. But LuckPerms can be *installed* while ours is the *selected*
handler, and then the two literals merge: our subcommands still work, and a bare `/perm` runs
LuckPerms' help instead of our overview. Silently, because
[LuckPerms' output never reaches RCON](CLAUDE.md) either, so it looks like the command did nothing.

Found on the dev server, which carries LuckPerms. `/rank` is claimed by nothing and always works.

### Both are absent unless our handler is the active one

`requires()` on the tree checks `StandardsPermissionHandler.isActive()`. A server running
LuckPerms would otherwise get a command that accepts every edit, reports success and changes
nothing — because the store it writes is not the store being read. That is the worst shape a
command can have, and it is decision 7 applied to a switch that lives in someone else's config
file.

The predicate is evaluated per source rather than at registration, and that is load-bearing:
commands are built while the level loads, and the handler is not chosen until
`handleServerStarting`, which is later. A check at registration time would see "not us" on every
server and hide the tree forever.

## Turning it on, and what LuckPerms does about it

```toml
# <world>/serverconfig or config/neoforge-server.toml
permissionHandler = "standards:permissions"
```

Then `/rank` appears and every node Standards registers becomes grantable. Nothing in Standards'
own config turns this on — NeoForge owns that switch, and putting a second one beside it would be
the arbitration layer this document opens by refusing to build.

**LuckPerms claims the slot when nobody has chosen.** Verified both ways on the dev server, which
has LuckPerms installed:

| `permissionHandler` | who ends up answering |
|---|---|
| `neoforge:default_handler` (the shipped value) | **LuckPerms** — it takes the slot rather than let the do-nothing handler have it |
| `standards:permissions` | **Standards**, with LuckPerms sitting there registered and unused |

So an owner who wants ours has to say so explicitly, and one who installs LuckPerms and touches
nothing gets LuckPerms. Both are the right outcome. Worth knowing because the first reading —
"LuckPerms forces itself" — is wrong, and it is the reading the decompiled bytecode suggests.

## What the first real use found

Two bugs, both of the family this codebase keeps producing, and neither reachable by the
resolver tests:

- **`*` could not be typed.** The tree used brigadier's `word()`, which accepts letters, digits
  and `_.+-` and stops dead at an asterisk. So `standards.home.*` was *unparseable* — the
  wildcards were proved correct by the self-test and could not be entered by a human. The error
  was "Expected whitespace to end one argument", which names nothing and reads like the admin's
  own typo. The node argument now takes the rest of the line and splits it in code.
- **A bare `/perm` ran LuckPerms.** See above.

Both were found by driving the real commands over RCON with the handler actually selected. The
self-test now covers each: the wildcard forms are parsed as commands, and `/rank`'s visibility is
asserted in *both* directions, because a gate that hides the tree always looks identical to one
that works.

Then a third, which needed **two clients** and could not have been found any other way — the
self-test has no client, and RCON cannot make somebody type:

- **A granted command rendered red.** The server re-evaluates `requires()` on every command it
  parses, so a grant works the instant it is made: a real non-op was granted `standards.craft` and
  got a crafting table with no reconnect. But the *client* holds a copy of the command tree, sent
  once on join, and uses it for tab-completion and for colouring the line as you type. So the
  player is told "you have `/craft` now", types it, sees red **"unknown command"**, and reports
  that it does not work. Almost nobody presses enter through a red line. The permission was fine;
  the only broken thing was what the player had been told.

  Fixed by resending the tree from `/rank` on every edit — to the one player for a user edit, to
  everyone for a group edit, since inheritance and the default group make the affected set more
  ways to be wrong than a packet sent only when an admin runs a command.

  **One residue survives, and it looks exactly like the fix not working.** The client re-colours a
  line when its *text* changes, not when a tree arrives, so a line already sitting in the chat box
  keeps its red until you touch it. Watched directly: a granted `/anvil` stayed red, then went
  white on a single backspace-and-retype. Nothing server-side can prompt a repaint.

## Promotions — a guest ladder that runs itself

```toml
[permissions]
  startingGroup = "guest"
  promotions = ["guest -> regular after 24h and 2h played"]
```

New players land in `guest`; the rest happens on its own.

### Two clocks, because they answer different questions

- **Real time** since we first saw them — *"come back tomorrow"*. A few minutes of wall clock is
  enough to lose the fly-by griefer who is on somebody else's server by now, and it costs an honest
  new player nothing but patience.
- **Played time**, counted only while they are online and **not away** — *"show me you meant it"*.

Minecraft's own `PLAY_TIME` statistic counts a player idling in a corner all night, which is
exactly the promotion an admin did not want to give. Standards already knows who is AFK, so this
number means what it says. As far as I can tell no other essentials mod makes that distinction.

**Give both and both must pass.** A rule that fired on whichever came first would make the stricter
half decorative.

Durations use the same parser as `/tempban` — `90m`, `36h`, `2w` — so they mean the same thing they
do everywhere else in the mod.

### `startingGroup` is not `defaultGroup`

Easy to conflate and they do opposite jobs. `defaultGroup` is consulted **last for everybody** and
nobody is a member of it — it is how you grant something to the whole server. `startingGroup` is
**real membership** a player can be promoted *out of*, which is the thing a ladder needs. A player
who already has any group is left alone, so an admin's ranking is never undone by a later login.

### One rung per minute

Somebody returning after a year climbs the ladder visibly rather than arriving to four promotion
messages at once and a rank nobody watched them earn.

### Conditions are a seam

`Rule.satisfiedBy` is the only place that decides whether somebody has qualified, and it reads
plain numbers off `StandardsData`. Another trigger — a vote, a purchase confirmed by a website, a
moderator's nod — is another condition in the same shape rather than a second system. Nothing here
assumes time is the only thing that can promote a player.

### It says so when it cannot work

Promotions move players between **Standards'** groups, so they need this handler active. Configure
a rule under LuckPerms and the server says so at startup rather than silently doing nothing:

```
[WARN] Promotions are configured (1 rule(s)) but NOT AVAILABLE: they move players between
Standards' own permission groups, and permissions here are being answered by
luckperms:permission_handler. Set permissionHandler = "standards:permissions" in
neoforge-server.toml to use them. LuckPerms has 'tracks' for the same job.
```

Naming LuckPerms' own equivalent matters — somebody who has written a rule wants the job done, not
a sales pitch. And the warning only appears when rules exist: a server using none does not need
telling about a feature it is not using.

## What would make this a bad idea

Worth writing down so the decision can be re-made honestly:

- **If it grows contexts.** The moment per-world permissions are wanted, the design above is the
  wrong one and LuckPerms is right.
- **If servers run it *and* LuckPerms.** They cannot — NeoForge picks one — but a pack shipping
  both and defaulting to ours would make Standards look like it broke somebody's setup.
- **If nobody uses it.** If every real server installs LuckPerms anyway, this is 500 lines of
  liability. Worth asking before building, and worth deleting rather than maintaining if the
  answer turns out to be no. (Built anyway, on the strength of the groups tie-in above: even a
  server running LuckPerms cannot get chat tags and cross-mod group visibility out of it.)

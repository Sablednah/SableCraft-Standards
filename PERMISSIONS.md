# Built-in permissions

Groups, grants and ranks, for a server that has not installed a permissions mod.

**Status: specified, and scheduled for 1.2** — after the combat API. Nothing in
`neoforge/permissions/` exists yet. Agreed 2026-08-25.

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

| in | out |
|---|---|
| groups, with inheritance | contexts (per-world, per-dimension, per-server) |
| players in groups | temporary nodes and expiry |
| per-player grants | tracks and promotion ladders |
| explicit deny, beating grant | weights, priorities, meta values |
| `*` wildcards — `standards.*` | SQL backends, web editor |
| a default group everyone gets | import from LuckPerms |

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

1. An **explicit deny** on the player — always wins, including over op.
2. An explicit **grant** on the player.
3. Their groups, most specific first, and a deny in a group beats a grant in a parent.
4. Wildcards, longest prefix first: `standards.home.limit.10` before `standards.home.*` before
   `standards.*`.
5. The node's **own default resolver** — so op still means op, and everyone-nodes still work, for
   anyone nothing has been said about.

Falling through to the default resolver at step 5 is what makes this safe to switch on: a server
that enables it and grants nothing behaves exactly as it did before.

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
/perm group <name> create | delete
/perm group <name> set <node> [true|false]
/perm group <name> unset <node>
/perm group <name> parent add|remove <other>
/perm group <name> info
/perm user <player> group add|remove <group>
/perm user <player> set <node> [true|false]
/perm user <player> info          — every node, and WHY: which group or grant answered
/perm check <player> <node>       — the same, for one node
```

`/perm user info` explaining **which rule answered** is the one that earns its keep. Every hour
lost to a permissions system is spent asking "why does this player have that", and a system that
can only say yes or no makes you bisect it by hand.

## What would make this a bad idea

Worth writing down so the decision can be re-made honestly:

- **If it grows contexts.** The moment per-world permissions are wanted, the design above is the
  wrong one and LuckPerms is right.
- **If servers run it *and* LuckPerms.** They cannot — NeoForge picks one — but a pack shipping
  both and defaulting to ours would make Standards look like it broke somebody's setup.
- **If nobody uses it.** If every real server installs LuckPerms anyway, this is 500 lines of
  liability. Worth asking before building, and worth deleting rather than maintaining if the
  answer turns out to be no.

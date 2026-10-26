# The places API

The server's public places — warps and `/spawn` — for mods that show them somewhere else.

**Status: built 2026-10-26 in 1.12.0. One consumer: SableCraft Diorama**, which draws them on its
web map.

```java
for (Places.Place w : Places.warps(server)) { ... }    // sorted by name
Places.spawn(server).ifPresent(s -> ...);              // empty = /setspawn never used
```

Read-only, server thread, and each call returns a copy.

## What is not here, on purpose

**Homes.** Warps are public by design — `standards.warp` is everyone's and `/warps` lists them —
so a map that shows them gives nothing away. A home is where somebody sleeps. Putting homes on a
web map anybody can open would make it the best tool on the server for finding a player who does
not want to be found. If a future consumer wants a player's *own* homes shown to *that* player,
that is a per-viewer question this API does not answer and should not grow into by accident.

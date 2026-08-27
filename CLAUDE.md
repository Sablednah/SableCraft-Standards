# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

**Standards** — a NeoForge server-utility mod: the commands every server ends up needing
(`/fly`, `/god`, `/top`, `/back`, `/home`, `/warp`, `/tpa`, …) plus a built-in economy with a
provider API other mods drive.

Yes, another essentials package. That is the joke in the name — see
[xkcd 927](https://xkcd.com/927/), and the tagline: *do you have Standards?*

It exists because the incumbents have specific, fixable flaws: FTB Essentials has no `/top`, and
both it and EssentialsX make `/fly` and `/god` pure toggles with no explicit `on`/`off`, which
makes them unusable from a command block, a datapack, or a LegendQuest skill. **Everything in this
mod follows from taking those two complaints seriously.**

**The generic mod id is a settled decision, not an oversight.** It was raised with the owner and
kept: the id is what admins type all day (`standards.home.limit.5` beats
`sablecraft_standards.home.limit.5`), and the ambiguity is the joke. `mod_name` carries the full
*SableCraft Standards* because the mods list is the one place a bare "Standards" genuinely
confuses. Don't re-litigate it.

Inspired by [Essentials](https://dev.bukkit.org/projects/essentials) / EssentialsX and FTB
Essentials, but **no code is copied from either** — read them for intent, then build it better.
That is a standing instruction from the owner, not a licensing constraint.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loader | NeoForge 21.11.42 |
| Java | 21 |
| Build | Gradle 9.2.1 + ModDevGradle (`net.neoforged.moddev` 2.0.141) |
| Licence | MIT (all original work) |
| Mod id | `standards`, package `com.sablednah.standards` |
| Display name | **SableCraft Standards** (`mod_name`); everyone says *Standards* |

**Standards drives a sibling build.** `../Factions-ReForged` is a separate mod, a separate repo and
a separate release, but `settings.gradle` includes it as `:factions` and the dev server loads both —
testing a faction mod without the mod it hard-depends on proves nothing. It is the first real
consumer of the groups, claims, chat-router and economy seams, and it found bugs in all of them.
`./deploy.sh` ships both, because a new Standards beside an old Factions starts and *then*
misbehaves somewhere unrelated.

This is the **fifth** mod in the series. `../MobHealth-Forge` is the canonical template,
`../LegendQuest-ReForged` is the closest architectural relative (commands, permissions, messages,
optional client), `../CityWorld-ReForged/PORTING.md` is the richest source of verified 1.21.11 API
notes, and `../ZombieMod/ZombieMod/CLAUDE.md` has the hardest-won networking lessons. **Read those
before inventing anything.**

## Build & run

No system Java. Borrow the portable JDK from the first mod in the series:

```bash
export JAVA_HOME=/mnt/d/Repos/sable/MobHealth-Forge/tools/jdk21
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew compileJava             # fast inner loop
./gradlew build                   # -> build/libs/standards-<version>+mc<mcver>.jar
./gradlew runServer               # headless dedicated server on port 25569
./gradlew runServer -Pselftest    # the same, running SelfTest on ServerStartedEvent
./gradlew runClient               # dev client (needs a display)
./gradlew runClientBuddy          # a second client, 'TestBuddy', auto-connecting to the dev server
./deploy.sh                       # build + copy into the CurseForge test instance
```

- **The dev server runs on port 25569**, set in `gradle.properties`, so it cannot collide with a
  CityWorld `runServer` (25565) or a ZombieMod one (25567). **Kill the previous `runServer` before
  starting another** — a lingering one holds the port and the clash surfaces as
  `bind(..) failed: Address already in use` → `Failed to initialize server` → a crash report, which
  reads like a code fault and is not one.
  - ⚠ `pkill -f "gradlew runServer"` **kills the shell you type it in**, because the pattern
    matches your own command line. Match on something narrower, or use the harness's background
    task controls.
- `-D` on the `gradlew` command line sets the property on **Gradle's** JVM, not the forked server.
  That is why `-Pselftest` exists — it is translated into a `systemProperty` on the run in
  `build.gradle`. A `-Dstandards.selftest=true` looks like the test silently not running.
- Versions and metadata live in `gradle.properties` and expand into
  `src/main/templates/META-INF/neoforge.mods.toml` at build time. **Never edit a generated
  mods.toml.**
- The jar carries its Minecraft version (`standards-1.0.0+mc1.21.11.jar`); the version *inside*
  `neoforge.mods.toml` stays a plain `1.0.0`. See `CROSS-VERSION.md`.

## Verifying changes: `SelfTest`

`./gradlew runServer -Pselftest` runs `neoforge/SelfTest` on `ServerStartedEvent` and logs a
pass/fail block. Gradle cannot pipe stdin to a dev server console, so this is the *only* headless
route to "does the command actually work", and it earned its keep on the very first run by catching
a static-initialisation-order crash.

Rules it keeps, all learned next door:

- **Parse *and* execute.** `dispatcher.parse(...)` alone proves nothing — check
  `getExceptions()`, `getReader().canRead()` *and* that the parse reached an executable node.
- **Test both directions.** A tree that matches anything passes every positive assertion. There is
  a deliberate `fly sideways backwards` check that must *fail* to parse.
- **Force-load the chunk before reading blocks.** An unloaded chunk answers with defaults, and a
  probe that skips this proves nothing about the world it thinks it is reading.
- **Call the real code.** A probe that re-derives the logic it is testing is testing the duplicate.

Add checks here rather than writing throwaway probes.

## The decisions everything else follows from

### 1. `Toggle` — the tri-state

`core/Toggle` is three enum constants and it is the reason the mod exists. Every switch command
(`/fly`, `/god`, and everything added later) is built by `commands/SwitchCommand.build(...)` and
therefore accepts `on` / `off` / `toggle`, with and without a target:

```
/fly            /fly on            /fly @a on
/god Steve      /god Steve off
```

**Never add a switch command that is toggle-only.** A human typing `/fly` wants a toggle; a skill
that wants flight *on* for twenty seconds and *off* afterwards needs the explicit form, and a
toggle in that position is a coin flip that grounds the player mid-air half the time.

The target is `EntityArgument.players()`, not `player()`, so selectors work. Brigadier tries
literals before arguments, so a player literally named `on` cannot be targeted as `/fly on` — a
trade worth making, documented in `SwitchCommand`.

### 2. Server-authoritative — vanilla clients get everything

Every command works for an unmodified client. `network/StandardsNetwork` exists and is
deliberately **empty**; nothing in it may ever become load-bearing.

When payloads *are* added: **every clientbound send goes through `neoforge/Net.sendIfAble`.**
`PayloadRegistrar.optional()` makes the *handshake* tolerant; it does **not** make sends
droppable. `PacketDistributor.sendToPlayer` throws synchronously on the server thread for a payload
the receiver never negotiated, and from a login handler that takes vanilla's login flow with it and
kicks the player with "Invalid player data". Channels are agreed during the configuration phase, so
there is no later event that helps — guard permanently. (LegendQuest found this the hard way;
ZombieMod re-found it.)

### 3. Flight is an attribute, mirrored to the ability flag

`StandardsEvents.applySwitches` grants flight with a modifier on NeoForge's
`NeoForgeMod.CREATIVE_FLIGHT` attribute, **not** by writing `abilities.mayfly` — which NeoForge has
deprecated precisely because it is one boolean many mods want to own, so whoever writes `false`
last takes flight from everyone else's feature.

**But the attribute alone is not enough for us**, and this trap is specific to server-side mods:
`ClientboundPlayerAbilitiesPacket` is constructed from `abilities.mayfly` directly, so a vanilla
client is never told about the attribute. The server accepts it flying while its own client refuses
to leave the ground. So the attribute is the source of truth and `mayfly` is written as a derived
cache of it, purely so the packet carries the right answer.

Related: **Minecraft rebuilds ability flags on respawn, dimension change and game-mode change.**
`applySwitches` is called from all three. `PlayerChangeGameModeEvent` fires *before* the change, so
the re-apply is deferred with `server.execute(...)`.

### 4. Exactly one ledger holds the money

`api/economy/` is the stable surface other mods compile against. `Economy` is the facade; providers
register with a priority and **the highest one wins outright**.

Do not make this additive. A bounty *payer* can sensibly be additive (ZombieMod pays an economy
*and* tallies a scoreboard — two different rewards), but a provider answers "what is my balance",
and two ledgers disagreeing about that is worse than either alone.

Standards registers itself at `BUILTIN_PRIORITY` (**negative**), so a dedicated economy mod
displaces it without either side knowing the other exists. `economy.preferOwnLedger` flips that.

Registration happens on `FMLCommonSetupEvent`, not in the mod constructor — the priority is a
config value and config is not loaded while mods are still being constructed.

### 5. Where state lives

| Where | What | Why |
|---|---|---|
| `StandardsData` (SavedData, overworld) | homes, warps, spawn, balances, name cache | **Offline access.** `/eco give` a sleeping player, `/baltop`, admin home cleanup — all questions about someone not online, and player attachments are not loaded then. |
| `PlayerState` (attachment, `copyOnDeath`) | fly/god flags, the `/back` trail | Belongs to the player and must survive death — `copyOnDeath` is the entire point of `/back`. |
| static maps in the owning service | teleport warmups, cooldowns, (later) tpa requests | A pending teleport or a cooldown that survives a restart is worse than losing it. |

### 6. Text is resolved server-side

`neoforge/Lang` holds every player-facing string, written to `config/standards/messages.yml` on
first run and merged thereafter. **Not vanilla translatable components** — a vanilla client does
not carry our lang file and would see raw keys.

**The merge is the load-bearing half, and it did not exist until it was measured.** `load()` used
to write the file only when it was *absent*, so every key added after a server's first run was
missing from that server's file forever — 146 of the catalogue's 222 on this dev world. `get()`
falls back to `DEFAULTS`, so nothing looked broken; the strings just could not be customised, with
nothing to say they existed. Found by asking whether `/me` could be italic.

Merging safely needs `messages.known` beside it, because the file's own header invites you to trim
it to just your changes — so "absent from the file" cannot mean "new", or every restart would
undo the trimming. New means *never offered to this installation*. There is no guard for an empty
seen-set: a server upgrading from before the bookkeeping has no record, and re-offering keys once
beats never offering them, because the same run marks everything seen.

`{term.*}` keys let an owner re-skin vocabulary wholesale (a server whose currency is credits sets
one key and every message follows). `&` colour codes are converted to `§` in exactly one place,
`Feedback.colored`. Add a key to the catalogue for anything a player can see; a hardcoded string
is a bug.

### 7. Config switches unregister, they do not refuse

A command that is off in config is **not registered with the dispatcher**. A greyed-out
tab-complete entry for something the server will never run is a lie the player discovers by trying
it, and a modpack that already ships a homes mod wants ours absent, not arguing. Consequence:
`/standards reload` reloads **messages only**, and says so.

### 8. A teleport narrates itself to everyone waiting on it

Watched-on-streams failure, and the reason `Teleports.Watcher` exists: with a warmup configured,
the classic `/tpa` is accepted and then **nothing observable happens for five seconds**. The
requester does not know they were accepted. The acceptor does not know anyone is coming. Both
re-run the command.

So every warmed teleport now:

- shows the traveller a **ticking action-bar countdown** (the action bar, not chat — it is already
  the transient-status line and it does not bury chat under five identical messages);
- tells any **watcher** the moment it is accepted, when it lands, and when it is cancelled *with
  the reason* — a bare "they did not make it" invites an identical second attempt.

`Watcher` is a callback rather than a UUID field so `Teleports` stays ignorant of `/tpa`. Any
future command with a second interested party (`/tpahere`, a shop delivery, a GM summon) gets the
same narration for free.

The other classic bug in this feature is direction: **`/tpahere` accepted moves the acceptor**, not
the requester. `Request.traveller()`/`host()` own that, and `SelfTest` asserts both directions —
it is invisible until two real people try it, at which point one of them is somewhere they never
asked to be.

### 9. Exactly one mixin, and it is `/vanish`

`ServerPlayerVanishMixin` injects into `ServerPlayer.broadcastToPlayer`, which is the question
vanilla's own entity tracker asks every pass:

```java
boolean flag = inRange && this.entity.broadcastToPlayer(viewer) && chunkTracked;
if (flag) { addPairing(viewer); } else { removePlayer(viewer); }
```

Answering `false` there gets unpair-on-vanish, re-pair-on-unvanish, chunk loads, dimension changes
and view-distance handling for free. It is the same lever spectator mode pulls two lines below.

The rejected alternative was packets — `ClientboundRemoveEntitiesPacket` to everyone, re-fired on
`PlayerEvent.StartTracking`. That leaves the tracker believing the pairing exists (so it keeps
streaming movement packets), flickers on re-track, and requires reimplementing every correctness
case by hand. `StartTracking` is not cancellable, so it can only undo, never prevent.

**This is the mod's only version-fragile surface — treat it as such.** `defaultRequire: 1` makes a
non-applying mixin fail loudly; without it, vanish would silently stop hiding anyone and look like
a permissions bug. Don't add a second mixin without the same level of justification.

**The mixin touches `core/VanishGate` and nothing else, and that is load-bearing.** A mixin runs
during class transformation, so everything it references loads right then — along with everything
*that* references. The first version called into `Vanish`, which pulls in `StandardsPermissions`,
NeoForge's `PermissionAPI` and the mod config, all while `ServerPlayer` was mid-transform. It
worked, and it was luck; the failure mode is a `MixinTransformerError` that kills the server before
any mod initialises, with a stack trace pointing at whatever vanilla class happened to trigger the
transform. `VanishGate` imports nothing but `java.util`, holds the vanished set, and takes a
predicate that `Vanish.install()` registers at setup. **Keep it dependency-free.**

`VanishGate.hidden` is called from inside the tracker for every player pair every pass, so it opens
with an `isEmpty()` check — the feature costs nothing on a server where nobody uses it.

**Where the line sits, decided by testing rather than by rule.** A vanished player is hidden; the
*world's* reactions to them are not. A chest they open still animates, and that is deliberate —
suppressing it would mean suppressing the sound, the particles and every other second-order effect,
and there is no end to that list.

But **item pickup is on the other side of the line**, because it is not an act they chose: you walk
past an arrow and it is gone from everyone's screen with nobody standing there. That gives you away
as surely as being seen, and it also means hidden staff quietly collect the loot from a fight they
were only watching. `vanish.vanishPickup` defaults to `false`.

Note the config is named for the behaviour rather than its negation — `vanishPickup = false` rather
than `vanishNoPickup = true` — so "off" means the same thing whichever end you approach it from. A
negated boolean is a coin flip every time somebody reads it.

### 10. Taking a command back off vanilla

`/msg` is vanilla's, and `/tell` and `/w` are **redirects** to its node. That defeats the obvious
approach twice: a redirect node ignores children merged into it, and an extra argument beside
vanilla's loses the race because brigadier tries children in insertion order. The first version of
`MessageCommands` did both — and `/msg` still "passed" the self-test, because *vanilla's* node was
executing. Mutes and ignores would have leaked straight through.

The way in is brigadier's merge rule: `CommandNode.addChild` **replaces the command** when merging a
node of the same name. So re-register vanilla's exact tree — same literal, same argument names,
same argument types (`targets` as `players()`, `message` as `MessageArgument`) — with our
`executes`. Ours wins, and `/tell` and `/w` inherit it because they point at that same node.

Two consequences worth remembering:

- **Do not register `/tell` or `/w` yourself.** Merging children into a redirect node silently does
  nothing.
- **When testing a redirected command, `getContext().getCommand()` is always null.** The command
  lives in the child context; use `getContext().getLastChild().getCommand()`. `SelfTest.boundCommand`
  exists for exactly this, and the test asserts the bound command is *ours*, not merely that
  something is executable.

### 11. Chat decoration is additive; the economy is not

`api/chat/` is the third seam, after the economy and the player switches, and the mod is clearly
converging on that shape: Standards owns a meeting point, other mods contribute.

But note the difference in kind, because the two look alike and behave oppositely. **Exactly one**
economy provider holds the money — a balance is a single fact, and two ledgers disagreeing is worse
than either. **Every** chat decorator gets a turn — a name can carry a faction tag and a party tag
and a rank without contradiction.

The ordering rule is one sentence: **priority is closeness to the name.** Prefixes render
lowest-priority-leftmost; suffixes mirror it, highest priority nearest the name. That is what lets
`[FACTION][PARTY] Lord Sablednah the noble` come out right with nobody coordinating. `SelfTest`
asserts both sides, because getting it backwards on one side only looks fine until a second mod
registers.

### 12. Every command lives at its plain name

`/home`, never `/standards home`. Muscle memory is the product. `/standards` is for administering
the mod itself and nothing else lives under it.

## 1.21.11 API notes (verified against the decompiled sources, not guessed)

Extract them once with:

```bash
mkdir -p .apisrc && cd .apisrc
unzip -oq ../build/moddev/artifacts/neoforge-21.11.42-sources.jar 'net/neoforged/**'
```

- `ResourceLocation` → **`net.minecraft.resources.Identifier`**;
  `ResourceKey.location()` → `identifier()`.
- **`ServerPlayer.server` is private** and there is no `serverLevel()`. Use
  `player.level()` (which returns `ServerLevel` on `ServerPlayer`) and `player.level().getServer()`.
- **Permissions were reworked.** `Commands.LEVEL_GAMEMASTERS` is a `PermissionCheck`, not an int.
  `player.hasPermissions(n)` is gone; use `Commands.LEVEL_GAMEMASTERS.check(player.permissions())`
  for a player, or `Commands.hasPermission(LEVEL).test(source)` for a source.
- **World spawn moved.** No `getSharedSpawnPos()`; it is
  `level.getRespawnData().globalPos().pos()` (a `LevelData.RespawnData` record).
- `Abilities.mayfly` is **deprecated** — see decision 3 above.
- `ServerPlayer.teleportTo(ServerLevel, x, y, z, Set<Relative>, yaw, pitch, boolean setCamera)`.
- `SavedDataType<>(String id, Supplier<T>, Codec<T>, @Nullable DataFixTypes)`; fetch with
  `server.overworld().getDataStorage().computeIfAbsent(TYPE)`.
- `AttributeModifier` is a **record** `(Identifier id, double amount, Operation)`;
  `AttributeInstance.removeModifier(Identifier)` and `addTransientModifier(...)`.
- `CompoundTag` getters return `Optional`.

## Two-player testing on Windows + WSL

`SelfTest` cannot prove the things that need two humans — `/tpa`'s narration, whether `/vanish`
actually hides, the `/invsee` slot mapping. Those need two clients against the dev server, and
getting there costs an hour of environment problems the first time. All five are solved in the
repo now; this is why.

**Launch clients from Windows, not WSL.** `./gradlew runClientBuddy` from WSL goes through WSLg and
the window frequently never appears. `TestClient.cmd` runs them natively on Windows using
CurseForge's bundled JDK 21:

```
.\TestClient.cmd            -> TestBuddy   (runClientBuddy, runBuddy/)
.\TestClient.cmd main       -> Sablednah   (runClientMain,  runMain/)
.\TestClient.cmd third      -> TestThird   (runClientThird, runThird/)
```

**Three clients, and the third is not optional for some things.** Any rule with *two* sides and a
bystander needs three people to observe: `/socialspy`, a request only some ranks are told about, and
"ask two factions, join one". With two accounts you can usually only prove the adjacent case and
guess at the real one.

⚠ **`if cond set A=1 & set B=2` in a `.cmd` file does not do what it reads like.** `cmd.exe` splits
on `&` before evaluating the `if`, so everything after it runs unconditionally. This silently broke
`TestClient.cmd` for months: `WHO` always ended up `TestThird` whatever was typed, so the default
launch ran TestBuddy's task in the third client's directory. Parenthesise, one command per line.

**Mute the clients.** `TestClient.cmd` zeroes every `soundCategory_*` in the client's run
directory before launching. Two clients and a server on one machine play the same sound two or
three times slightly out of step, for the whole session. It writes the file rather than trusting
one to exist, because a fresh client generates its own `options.txt` on first start — so a brand
new run directory is exactly the one that would come up at full volume.

**Each Windows client needs its own build directory**, via `-PwinClient=<name>` →
`build-win-<name>/`, **and every subproject needs it too.** `layout.buildDirectory` is
per-project, so the root redirection says nothing about `:factions` — which then tries to write
`Factions-ReForged/build/libs/` while the WSL server holds that jar open, and fails with
`Unable to delete file`. The `-P` property reaches every project in the build, so each one just
has to test for it. Without it the client's `:createMinecraftArtifacts` tries to replace
`build/moddev/artifacts/neoforge-*.jar` while the running dev server holds it open, and fails with
`Unable to delete file` / `AccessDeniedException`. A separate `--project-cache-dir` is *not*
enough — that separates lock files, not build outputs. Both are set in `TestClient.cmd`.

**⚠ Config hot-reload does not work on this dev server, and it is the environment, not the mod.**
NeoForge watches `config/*.toml` and reloads them live — the log even says
`Watching TOML config file ... for changes`. But inotify events do not propagate from the Windows
filesystem to Linux on `/mnt/d`, so the watcher never fires: verified by editing a value, waiting,
then `touch`-ing the file, with no reload logged either time. **Restart the dev server after any
config change.** On a real server the same edit applies live, so do not "fix" this by adding a
reload command. (`/standards reload` is messages-only for a different and deliberate reason — see
decision 7.)

**⚠ On `/mnt/d`, Linux does NOT get Linux file semantics.** The usual rule is that Linux can unlink
a file another process has open and Windows cannot — but drvfs goes through Windows file APIs, so
Windows locking applies **in both directions**. A file held open by a Windows process cannot be
replaced from WSL either. This is the single most confusing part of the whole setup.

**Windows gradle daemons outlive the build and keep holding the jar.** A failed Windows-side run
leaves daemons up for hours. `cmd.exe /c "cd /d <repo> && gradlew.bat --stop"` clears them, and is
safe — it only stops build daemons.

**Test the operation, not a proxy.** Probing with `open(path, 'r+b')` reports success while a
rename over the same file is still denied, because the holder's sharing mode permits writes but not
delete. Probe with an actual `mv there && mv back`.

**⚠ Compiling with the dev server running takes minutes; stopped, it takes seconds.** Measured on
2026-08-27: `:factions:compileJava` went from **6m22s** to **7s** purely by stopping `runServer`
first. The server holds jars open and drvfs makes every write contend with it. If a build seems to
have hung, stop the server before reaching for `wsl --shutdown`.

**Your real modpack instance cannot join the dev server**, unless you make the mod lists match. It carries LegendQuest, ZombieMod,
CityWorld and the FTB mods; NeoForge refuses when required-mod lists disagree ("bad network
protocol"). Either use a dev client, or copy the instance's extra jars into `run/mods/` so the two agree —
which is what was done on 2026-08-27, and the dev server now carries LuckPerms, CityWorld,
LegendQuest and ZombieMod alongside Standards and Factions from source. That makes it a real
integration environment, and worth knowing: **LuckPerms being present changes permission
resolution**, so if an op-gated feature stops biting, check it before suspecting the feature.

**Offline UUIDs are case-sensitive.** `online-mode=false` derives the UUID from
`OfflinePlayer:<name>` verbatim, so `Sablednah` and `sablednah` are different players and an
`ops.json` entry for one does not op the other. Put both spellings in. (Standards itself is immune
— it keys everything by UUID and uses names only for lookup and display.)

**`spawn-protection` makes a non-op look like broken permissions.** They cannot break blocks near
spawn, with no message explaining why. Set it to `0` on the dev server; on a live server, tell
players it exists.

**✔ LuckPerms honours our default resolvers, op-gated ones included — verified twice.** The worry was that
installing it would answer "undefined → false" and silently strip ordinary players of every
everyone-by-default node. It does not: a non-op with no grants ran `/homes` and got our own
"no homes yet" message. That is proof rather than inference — a failed `requires()` hides the
command entirely and yields "Unknown or incomplete command", so seeing *our* text means the
permission resolved true. Re-check this on a LuckPerms major version bump.

The second half took longer to prove, because only the everyone-by-default nodes had been tested.
**An op with no LuckPerms grants at all does get the op-gated commands** — `/fly`, `/god` and the
rest — so LuckPerms resolves our op-level defaults from op status rather than demanding an explicit
grant. Verified 2026-08-21 against an empty permission set (the `default` group holding no nodes
and the player holding none), which is what a fresh server looks like on day one.

⚠ **But an unhealthy LuckPerms denies everything, silently.** If its storage fails to initialise,
every node resolves false, every gated command vanishes from the tree, and the only symptom a
player sees is *"Unknown or incomplete command"* — indistinguishable from a broken mod. It cost an
hour before the LP error at the top of the boot log was spotted:

```
[ERROR] [luckperms]: Failed to init storage implementation
  Database may be already in use: .../luckperms-h2-v2.mv.db   The file is locked
```

The cause was **an orphaned dev-server JVM from a previous run still holding the H2 file** — a
`runServer` that outlived its `stop`. So when permissions look broken, check for a stale JVM before
suspecting the permission code: `ps -eo pid,etime,args | grep java | grep SableCraft-Standards`.
Match on the repo path, never on `java` alone — the sibling mods share the same portable JDK and a
broad kill takes out someone else's dev server.

**RCON is worth enabling on the dev server.** Gradle cannot pipe stdin to the server console, so
without it every `op`/`deop`/config change costs a two-minute restart. `enable-rcon=true`,
`rcon.port=25575` in `run/server.properties`, plus the small client in the session scratchpad.
Caveat: **LuckPerms' command output never reaches RCON** — it replies to the sender and swallows it
— so `lp` commands work but you cannot read their answers that way.

**⚠ `/execute as <player> run <cmd>` does NOT test that player's permissions.** `requires()` is
evaluated at *parse* time against whoever typed it, and `execute` only swaps the source at
execution. So running a Standards command as a non-op from the console succeeds regardless of
their nodes — verified: `execute as TestBuddy run fly on` set `mayfly: 1b` on a non-op.

That is a genuinely useful mechanism rather than a hole (vanilla `/execute` is op-gated anyway):
**it is how another mod can invoke a Standards command on a player's behalf without granting them
the node** — one live answer to the LegendQuest integration question. But it means permission
boundaries can only be tested by the player actually typing the command.

## The category of bug this mod keeps producing

**Code that has never met real user input.** Three separate bugs in one day, all the same shape,
none of them catchable by the self-test as written:

- The chat decorator path had **never executed**. `format()` returns empty with no decorator
  registered, so the `setMessage` line below it was dead code for weeks. LegendQuest registered the
  first real decorator and the name doubled within a minute.
- `Feedback.colored` had only ever seen **text we wrote ourselves**, where every `&` was
  deliberately a colour code. The first player to type an ampersand got "Tom § Jerry", and the
  first to type `&r` could dress their words as a server message.
- The `/top` barrier check had never met **real bedrock** — only the synthetic ids in its own test —
  so it blamed a player for the shape of the Nether.

The self-test is excellent at "does this function compute the right answer" and blind to "has
anything ever called it". So when adding a seam or a gate, ask the second question explicitly:
*what is the first real input this will see, and where does it come from?* If the answer is "another
mod, later" or "whatever a player types", that path deserves a test that supplies exactly that —
and, better, a real consumer on the other side of the seam before it is called done.

Both mods independently arrived at treating **"first time real user input reaches this code"** as
its own risk category. It has earned that status.

## The other category: the server is right and the client was never told

Three bugs in one afternoon, all the same shape, none of them reachable by `SelfTest` — because
the self-test has no client. Minecraft **predicts** an interaction locally before the server rules
on it, so a cancelled action has already happened on screen:

- a denied **lever** still threw its redstone spark, and a denied **door** could sit there looking
  open;
- a denied **placement** consumed the item from the hotbar, and only a relog brought it back. This
  is the alarming one: *"the mod ate my items"* is the worst thing a protection feature can appear
  to do, and nobody who believes it stops to check;
- a refusal aimed at `entity.blockPosition()` puffed its particles at **ankle height**, because for
  an entity that is the block under its feet.

So when cancelling anything a player did: **resend what they were told**. Block state (and its
neighbours — a door is two blocks), inventory, and put the feedback where the cursor was. Doors and
buttons will still visibly twitch; that twitch *is* the correction landing and cannot be prevented
from the server.

## Gotchas already paid for

- **Static initialisation order.** A `static final` collection declared *after* the fields that
  fill it is null when their initialisers run. It crashed the whole mod during command
  registration with an NPE nowhere near the mistake. `StandardsPermissions` declares `FIXED` first,
  and the comment there says why.
- **Close Minecraft before `./deploy.sh`** — a running instance holds the jar open, Windows refuses
  the replace, and you test a stale jar. `deploy.sh` checks and fails loudly.
- If Gradle hangs on `:compileJava` with no CPU and no class files, that is the known WSL2 `/mnt/d`
  degradation: `wsl --shutdown` from Windows PowerShell, reopen, rebuild.
- The first build after changing `accesstransformer.cfg` re-runs the neoform runtime and takes
  10+ minutes. It is working, not hung. (Standards has no AT yet — keep it that way if you can.)

## Status lines rot, and they rot quietly

`README.md` claimed the groups and claims seams were "designed, not built" two days after they
shipped and were being driven by two mods. `GROUPS-API.md` was worse — *"no provider is registered,
and `Factions-ReForged` does not exist"*, all three untrue.

Nobody notices, because a doc that undersells is never contradicted by a failure. **Sweep the
status lines whenever something ships**: `grep -n "^\*\*Status:" *.md`, plus "not built", "does not
exist", "nothing consumes", "yet". Worth doing before any release.

## Where to look next

- `COMMANDS.md` — the full EssentialsX/FTB catalogue with keep/skip recommendations. **This is the
  open decision list**; the owner is working through it.
- `ECONOMY-API.md` — how LegendQuest and ZombieMod hook the economy.
- `CHAT-API.md` — the decorator seam, and the stated cost that a decorated line is not signed.
- `GROUPS-API.md` — **built and in use**, by Standards' own `/group` and by Factions. Group
  membership by kind and chunk claim queries; also the decisions on FTB (Teams out, compatibility
  kept) and why there will be no minimap. Note the two halves behave oppositely on purpose: a group
  kind takes exactly one provider, claims take the highest priority and **fail open**.
- `../Factions-ReForged/POWER.md` — **designed, not built.** Power, the faction bank's other half
  and the standard, with the real 2012 numbers rather than the remembered ones. Also two findings
  that belong *here* rather than there: the original blocked `/home`, `/spawn` and `/tpa` outright
  inside enemy territory (combat logging solved in 2012, by gating other plugins' commands), and
  `/f stuck` is the answer to being trapped in a claim without holing the protection.
- `COMBAT-API.md` — **built 2026-08-27.** Combat tagging, and it now opens with a "using it from
  another mod" section written for LegendQuest. `Combat.playerBehind` is public because Factions'
  power modes need the same attacker resolution and two implementations would disagree.
- `PERMISSIONS.md` — **specified, scheduled for 1.2.** A built-in permission handler for servers
  with no permissions mod. Note the framing correction it opens with: NeoForge's `PermissionAPI`
  is already the Vault-equivalent and the owner picks the active handler in `neoforge-server.toml`,
  so this is one more handler rather than an arbitration layer.
- `CROSS-VERSION.md` — the plan for living on several Minecraft lines at once.

# Living on several Minecraft lines

Standards ships on **1.21.11, 26.1.x and 26.2.x** — a branch per line, released together. Minecraft
now drops roughly quarterly (26.1 in June 2026, 26.2 in August, 26.3 due around September), and a
server-utility mod that only exists on last quarter's version is a mod nobody can use.

This was the plan for that, written before the first port rather than discovered during it, and it
has now survived two. It is almost entirely borrowed
from what **CityWorld ReForged** measured the hard way across three versions in August 2026 —
see `../CityWorld-ReForged/PORTING.md`, sections "The measured 26.1 delta", "The measured 26.2
delta", and "Stage 3: what the two deltas say".

## Measured, 2026-08-29: both ports done, all three lines green

Predictions below; results here. **352 Standards checks and 42 Factions checks pass identically on
1.21.11, 26.1.2 and 26.2.**

> ⚠ **The counts stopped being identical in 1.3.0, and that is correct.** The permissions
> self-test asks what the *active handler* is: the `/rank` parse checks, the `PermissionStore`
> round trip and the promotion move all stand down unless Standards' own handler is selected,
> because there is no store for them to act on. As of 1.4.0 that is **521 with it, 491 without** —
> and the gap grows every time a permissions check is added. A future port comparing raw numbers
> across lines would read a deliberate skip as a regression. **Compare PASSED/FAILED, not totals.**

**And proven on real upgraded worlds, which is the half that matters.** The 1.21.11 dev world was
copied onto a 26.1.2 server and played: the full raid and standard cycle ran, and everything behaved
as it had on 1.21.11 — homes, warps, balances, groups, claims, power, captured standards. That is
the check the self-test cannot make, and it is the one that caught the save migration writing to the
wrong folder (below). A green self-test on an empty world says nothing about an upgrade.

**26.2 was then done the same way, and deliberately as a direct jump** — a second copy of the
*1.21.11* world, not the 26.1 one, because a server skipping 26.1 entirely must still get its data
and that path had never been run. Same result: standard cycle, raiding, LegendQuest skills tagging
combat, and ZombieMod's mobs unable to break blocks in claimed land. **Both 26.x lines are now
validated with real consumers on the other side of every seam**, which is the part `SelfTest`
structurally cannot reach.

| | 26.1.2 | 26.2 |
|---|---|---|
| Standards source changes | 5 API changes, ~20 sites | 3 API changes, 3 sites |
| Factions source changes | 4 API changes, 9 files | 2 API changes, 2 sites |
| Toolchain | Java 21 → 25, MDG 2.0.141 → 2.0.144 | none |
| Mixins | **both still apply, signatures unchanged** | **both still apply** |

### 26.1.2 — what actually moved

- **`displayClientMessage(Component, boolean)` → `sendSystemMessage(c, overlay)`.** Four call sites
  had grown up around the codebase. They now all go through `Feedback`, which is the single place
  either mod hands a message to a player — so the next time this moves it is one line.
- **`ChunkPos` became a record**: `pos.x` → `pos.x()`, `new ChunkPos(BlockPos)` → `containing()`.
  Exactly what CityWorld measured. Nine files in Factions, one in Standards.
- **`SavedDataType`'s id is an `Identifier`**, not a `String` — and see the warning below.
- **`BlockEvent.BreakEvent` → `event.level.block.BreakBlockEvent`.**
- **`DamageResistant(TagKey)` → `DamageResistant(HolderSet)`**, so it needs a level to resolve the
  tag against.

### ⚠ Gamerules are snake_case now

`reducedDebugInfo` is **`reduced_debug_info`**, and the same goes for the rest — `sendCommandFeedback`
is `send_command_feedback`, `mobGriefing` is `mob_griefing`. Every gamerule was renamed.

Not a compile error, because no code here reads one: it cost nothing but a documentation bug in
**eight places**, where `/depth` and `/compass` told an owner to set a gamerule that does not exist
under that name. Found by trying to set it on the dev server, which is the only way it could have
been — the wrong name was in prose the compiler never sees.

Worth remembering as a shape rather than a fact: **the things a doc tells an owner to type are not
checked by anything.** A renamed gamerule, config key or command in prose survives every build and
every self-test.

### Divergences carried by individual commands

- **`/f raid`'s side glow hits both of the known 26.x moves at once**, which made it a good check
  that they were written down properly: `ChunkPos.x` → `x()` on 26.1, and `PlayerTeam.setColor`
  taking an `Optional<TeamColor>` on 26.2. `FactionRaidEvents` and `FactionStandards` now carry the
  same divergence for the same reason, and the comment in each points at the other.

- **`ItemInput.createItemStack`** takes `(int count, boolean allowOversized)` on 1.21.11 and
  `(int count)` on 26.x. `/i` is the only caller. ⚠ Note the 1.21.11 Minecraft sources live inside
  **`neoforge-21.11.42-sources.jar`**, while 26.x splits them into `minecraft-patched-*-sources.jar`
  — reading the wrong one is how this was got wrong the first time, and it is the same trap as the
  stale `.apisrc` below wearing different clothes.

### 26.2 — what actually moved

- **`ChatFormatting` was stripped to a bare enum** of code characters: no `isFormat()`, no name, no
  colour. `Style.applyFormat` and `withColor` both survive, so the only loss is the *question* —
  and owning the five formatting constants ourselves works on all three lines and cannot rot.
- **`EntityType`'s constants moved to `EntityTypes`.**
- **Dyed items — and dyed *blocks* — are collections**: `Items.GRAY_STAINED_GLASS_PANE` →
  `Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)`, and equally
  `Blocks.RED_BANNER` → `Blocks.BANNER.pick(DyeColor.RED)`. CityWorld had 145 of these; we had
  **one** at port time, because almost nothing here touches blocks or items. That prediction held
  exactly.

  ⚠ **It is the divergence most likely to reappear**, and it did: `/f fixture standards`, written on
  1.21.11 four days later, named nine banner blocks by colour and hit all nine on the first 26.2
  compile. Worth knowing before writing the code rather than after — anything that reaches for a
  *coloured* block or item by name is a `pick(DyeColor)` on 26.2, and the port branch stores the
  `DyeColor` rather than the block so only the lookup differs.
- **`PlayerTeam.setColor(ChatFormatting)` → `setColor(Optional<TeamColor>)`** — precisely what
  ZombieMod's `Colours` seam warned about, arriving as described.
- **`PlayerInteractEvent.EntityInteractSpecific`** folded into `EntityInteract`.

### ⚠ The one that was not a compile error

**26.1 moved every saved-data file into a namespaced folder.** `SavedDataType`'s id resolves as
`root.resolve(namespace, path)`, so `data/standards_kits.dat` is now `data/standards/kits.dat`.

A world upgraded from 1.21.11 finds no file, creates an empty one, and carries on — **every home,
warp, kit, mailbox, mute, balance, group and faction gone, with no exception and no warning.** The
first anybody would know is a player asking where their base went.

`SaveMigration` (and `FactionSaveMigration`) run on `ServerAboutToStartEvent`, before anything reads
saved data, and **copy rather than move**: a server that upgrades, hits something unrelated and
rolls back must not find its data gone, and the old file is the only evidence if a copy turns out
wrong.

### Traps this port paid for

- **Two repos, two branch sets.** Factions is a subproject of the Standards build, so both must be
  on matching branches at once. Standards on `mc26.1` with Factions on `mc26.2` compiles the wrong
  thing and the error mentions neither branches nor versions.
- **`options.release` pinned to 21** in Factions made the subproject ask a JVM-25 root project for
  a JVM-21 artefact. It fails at *dependency resolution* with no mention of Java versions at all.
  It now tracks the toolchain.
- **The dev server needs its own world AND port per line.** A 26.1 server pointed at a 1.21.11
  world upgrades it in place — a one-way trip — and sharing a port produces
  `Address already in use` → `Failed to initialize server` → a crash report that reads like a code
  fault. `run-mc<version>/` and a port per branch.
- **`build/moddev/artifacts/` keeps the *old* NeoForge sources** after a retarget while replacing
  the Minecraft ones, so API lookups silently answer from the previous version. Read NeoForge
  sources out of `~/.gradle/caches/modules-2/` by version instead. Same shape as the `.apisrc`
  trap below, which is why that is now deleted on every retarget.

## What CityWorld's two data points actually showed

|  | 26.1 | 26.2 |
|---|---|---|
| Hand-written source changes | 12 lines, 6 files | 3 files |
| Generated source changes | none (byte-identical) | 145 constants |
| Toolchain | Java 21 → 25, ModDevGradle bump | none |
| Nature of the change | one record conversion | block-declaration model rewrite |

**The lesson is not "12 lines a quarter".** 26.1 was one record conversion (`ChunkPos.x` became
`x()`); 26.2 rewrote how whole families of blocks are declared. A quarterly drop is not reliably
cheap, and planning as if it were is how you end up three versions behind.

What carried the weight was not a clever build setup. It was two design decisions already in
place before the first port: a narrow seam onto Minecraft's API, and generating the widest surface
rather than hand-writing it.

## What that means for Standards

Standards is in a far better position than CityWorld ever was, and it is worth being explicit about
why: **almost nothing here touches Minecraft's block, item or worldgen APIs**, which is where every
one of those breaking changes landed. What we touch is commands, permissions, attachments, save
data, teleports and abilities — a much smaller and much more stable surface.

Measured tonight, the whole mod's contact with `net.minecraft` is:

| Area | Files | Risk |
|---|---|---|
| Brigadier command building | `neoforge/commands/*` | Low — Brigadier is stable |
| Permission nodes | `StandardsPermissions` | **Already moved once** — 1.21.11 replaced `hasPermissions(int)` with `PermissionCheck`. Expect more. |
| Player abilities / attributes | `StandardsEvents` | Medium — `Abilities.mayfly` is already deprecated |
| Teleporting | `Teleports` | Medium — `teleportTo`'s signature has changed before |
| Save data + codecs | `StandardsData`, `Waypoint` | Low, but a codec change is a data-loss change, not a compile error |
| Block state reads | `SafeLoc` | Low — three method calls |
| **Mixins** | `ServerPlayerVanishMixin`, `LivingEntityVanishMixin` | **Highest in the mod.** Two `@Inject`s, both for `/vanish`: `ServerPlayer.broadcastToPlayer` (who can see you) and `LivingEntity.isPushable` (who can shove you). Both methods are public, short and long-stable, but a mixin that stops applying is the worst failure mode here — see below. |

So the honest expectation is **a handful of call sites per drop**, not a port.

### The mixins deserve their own paragraph

Standards has two, both belonging to `/vanish`, because hiding a player from *some* observers is
inherently invasive and vanilla exposes no events for it:

| Mixin | Injects | Stops |
|---|---|---|
| `ServerPlayerVanishMixin` | `ServerPlayer.broadcastToPlayer` | being seen — vanilla's tracker unpairs, so glow and outlines cannot leak either |
| `LivingEntityVanishMixin` | `LivingEntity.isPushable` | being shoved, which otherwise locates hidden staff by touch |

Both were driven by real testing: the first from the feature's design, the second after someone
walked into a vanished player and felt them push back. Damage is handled without a mixin, through
`LivingIncomingDamageEvent`.

The alternatives were considered and are worse. Packet-based hiding leaves the entity tracked, so
it keeps streaming movement and leaks through glowing. A scoreboard team with
`collisionRule=never` would clobber FTB Teams and anything else managing team membership, since a
player can only be in one team.

⚠ `LivingEntityVanishMixin` targets `LivingEntity`, so it runs for **every living entity**, and
`Entity.push` consults it for every nearby pair every tick. Its first statement is
`VanishGate.anyVanished()` — one field read, false on virtually every server. **Protect that fast
path** if the mixin is ever touched.

**Check it first on every Minecraft update.** `injectors.defaultRequire: 1` in
`standards.mixins.json` makes a non-applying mixin fail the *build/launch* loudly rather than
silently, which is the entire reason that setting is there: a quietly non-applying vanish mixin
would look like a permissions problem, and someone would spend an evening in LuckPerms.

## The mechanism: branch per version, for now

Follow CityWorld's actual recommendation rather than its speculation. It considered a single tree
with per-version compat source sets and deliberately did **not** commit to it on two data points,
because the divergences have no syntax valid on both versions (`pos.x` vs `pos.x()`) and guessing
wrong about the mechanism is more expensive than merging branches later.

For Standards:

1. **`master` targets the newest supported line.** A branch per older line
   (`mc1.21.11`, `mc26.1`, …).
2. **Bump `gradle.properties` and nothing else** to retarget. `minecraft_version`,
   `minecraft_version_range`, `neo_version`, and the JDK if the line moved.
3. **The jar carries its Minecraft version** — `standards-1.0.0+mc1.21.11.jar`. Already set up in
   `build.gradle`. Two files both called `standards-1.0.0.jar` are indistinguishable in a mods
   folder or on a releases page, and this cost nothing to do from the start. The version *inside*
   `neoforge.mods.toml` stays a plain `1.0.0`.
4. **Cherry-pick features forward and back.** With a small hand-written divergence set, this stays
   cheap. Revisit the single-tree question once there are three lines and two drops of evidence
   about whether the divergence set is growing or shrinking.

## The one thing to build before it is needed: a version matrix for `SelfTest`

`SelfTest` already exists and already runs on `ServerStartedEvent`. It is the natural spine of a
version check, and it is worth wiring into CI **before** the first port rather than after, because
the failure it catches is the one that looks like nothing at all.

CityWorld's `selftest.sh` earned its keep on its first green CI run by catching a *silent* fallback
to vanilla worldgen — the world generated, looked entirely normal, and was not CityWorld. Our
equivalent silent failures:

- a command that stops registering because a `requires()` predicate now returns false everywhere;
- a codec that stops round-tripping, so every home on the server becomes a hole in the ground —
  and only after a restart;
- an economy provider that no longer wins, so money silently goes into a second ledger;
- a permission node that resolves to false for everyone, making the mod look "broken" with no
  error anywhere.

Every one of those is already asserted by `SelfTest`. What is missing is running it on more than
one version and comparing.

**Concretely, when the second line appears:**

- `scripts/selftest.sh` — pick the right JDK from `minecraft_version`, run
  `./gradlew runServer -Pselftest`, grep for the PASSED/FAILED block, exit non-zero on failure.
- A GitHub Actions matrix over the version branches. Warm, CityWorld measured 4–5 minutes per
  version in parallel; cold it has to let NeoForm decompile Minecraft, which is 10–15 minutes — so
  **key the cache on the NeoForge version**.
- Assert **presence, not exact counts**. CityWorld found its own counts wobble by one or two
  between identical runs and warns explicitly against tightening those into equality assertions:
  it produces a test that fails at random and teaches everyone to ignore it.

## Traps already known about, so they cost nothing twice

- **Java version tracks Minecraft.** 26.1 shipped `java-runtime-epsilon` and needed JDK 25.
  `deploy.sh` already honours a preset `JAVA_HOME` and falls back through the portable JDKs, so it
  is ready for a second one.
- **A shared cache across versions serves the wrong version's sources.** CityWorld's material
  generator silently regenerated against whichever cached jar sorted first once two versions
  existed. Our `.apisrc/` reference extraction has the same shape — key it per version or delete it
  between retargets.
- ⚠ **The CurseForge Java tag used to be hardcoded `Java 21`**, which was right for 1.21.11 and
  quietly wrong for every 26.x jar — those compile against Java 25, and the file page told people
  21 would do. Nothing failed, because CurseForge accepts whatever tag it is handed; 1.2.0 and
  1.3.0 both shipped with it. `scripts/curseforge-upload.sh` now reads the class-file major version
  out of the jar's own `com/sablednah/` classes (major = 44 + release) and takes the highest, so
  **the artefact states its own requirement** and cannot disagree with what was built. A lookup
  table keyed on the Minecraft version would have fixed today and rotted the next time a line moved
  its toolchain.

- **Publishing to CurseForge right after a Minecraft release will fail**, because CurseForge has to
  add the version before anything can be uploaded against it. That is expected, not a bug.
- **HTTP 200 from CurseForge means accepted, not published.** It dedupes by file content, so
  re-uploading an existing release gets every file rejected as a duplicate — and rejected files are
  hidden from the author file list by default, so it looks like nothing arrived at all. The
  authoritative view is always `authors.curseforge.com/#/projects/<id>/files`.

- ⚠ **And a file can sit in manual review while its batch-mates go live.** On the 1.3.0 release the
  1.21.11 and 26.1.2 jars appeared on the public files page within the hour and the 26.2 one did
  not, despite the workflow logging all three as uploaded with file ids. It was queued for a
  moderator, and that is routine.

  Worth knowing because **a partly-visible release looks exactly like a partial rejection**, and the
  rejection case above is the one that needs acting on. Do not re-upload to "fix" it: that is the
  duplicate path, which really does fail. Check the authors page, see `Under review`, and wait.

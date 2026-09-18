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
- ⚠ **GUI rendering was reworked, and this is the first divergence that is genuinely about
  drawing** rather than an accessor rename. `GuiGraphics` → **`GuiGraphicsExtractor`**,
  `renderItem(stack, x, y)` → `item(...)`, `drawString(font, s, x, y, col, shadow)` →
  `text(...)`, and a screen's `render(...)` → `extractRenderState(...)`. `fill(...)` survives.

  Met on the very first client-side feature, exactly as `CLIENT.md` predicted when it said GUI code
  would be the majority of every future port's work. Worth reading that prediction as confirmed:
  the divergence set for *server* code across two Minecraft lines has been a handful of accessor
  renames, and one screen cost more than all of them together.

  The practical consequence for anything drawn: **expect the render method to diverge per branch and
  keep it in one small class**, so the port is one file rather than a hunt. `ActionBar` is deliberately
  the only class that touches a rendering type.

  **The counter-example, 2026-09-13: world-space drawing ported for free.** Factions' border grid
  draws with vanilla's `Gizmos` from a `DebugRenderer.SimpleDebugRenderer`, and both are
  byte-identical on 1.21.11, 26.1 and 26.2 — diffed from the decompiled sources rather than assumed.
  The whole feature cherry-picked forward with one fix, `ChunkPos` being a record, already on this
  list. So the split is not "drawing diverges": **GUI screens diverged; vanilla's debug primitives
  did not.** Borrow vanilla's primitives where they exist and the port is close to free.

### 26.3 — what actually moved

**Minecraft 26.3 "Wilderness Bound" is a full release (2026-09-15); NeoForge is the beta**, at
`26.3.0.3-beta`, with no 26.2→26.3 primer published. Java stays 25; MDG 2.0.144 → 2.0.147.
CurseForge already listed 26.3, so the "publishing right after a release fails" caveat did not
apply. **3 API changes, 19 sites.**

- **Four screen accessors were RENAMED, not removed** — `getGuiLeft/getGuiTop/getXSize/getYSize` →
  `getLeftPos/getTopPos/getImageWidth/getImageHeight`. 16 sites in `ActionBar` and `PanelHost`.
  Because it is a rename, **the access transformer is untouched** — which matters, since an AT edit
  forces a 10-minute NeoForm re-run.
- **`drop(stack, bool)` → `drop(stack, bool, Prediction)`**, and `Player`'s two-argument form is
  gone. ⚠ **`SERVER_ONLY` is the right constant** where the inventory was full and the item goes on
  the floor — taken from vanilla's own server-initiated sites (`AbstractContainerMenu`,
  `BeaconMenu`, `AdvancementRewards`). `PREDICTED` is for a drop the client already drew. The names
  invite the wrong pick and it compiles either way.
- **JourneyMap's API is per line**: `journeymap_api_version` → `26.3-2.0.0`. ⚠ An *optional*
  dependency whose range is unmet still refuses to load, so a wrong floor stops the mod rather than
  dropping the layer.

**Everything version-fragile came back clean, measured from the jars rather than read off a
changelog:** all mixins apply, the AT target `leftPos` survives, and **`SavedDataStorage` is
byte-identical** — no repeat of 26.1 silently moving saved data.

### ⚠ 26.3 replaced GLFW with SDL, and SDL renumbers the mouse

**The first behavioural 26.3-only divergence, and it broke every modded click while compiling
perfectly.** SDL says left=**1**, middle=2, right=**3** where GLFW said 0/1/2.
`ScreenEvent.getButton()` and `getMouseButton()` hand that raw value straight through — identical
source on every line — so any comparison against a literal `0` or `1` breaks on 26.3 alone.

Symptoms, none of them reachable by `SelfTest`, which has no client:

- the Factions panel **drew perfectly and nothing in it was clickable** (`mouseClicked` opens with
  `if (button != 0) return false`);
- an action-bar category **toggled on a left click and ignored a right one**, because the `!= 1`
  right-click branch caught the left click (1) and cancelled the event, so vanilla's own
  `Button.onPress` never ran;
- claiming from JourneyMap's map was **exactly reversed**.

**Vanilla hit the same wall**: 26.3 adds `AbstractContainerScreen.getContainerClickButton`
(`case 1 -> 0; case 3 -> 1;`), absent on 26.1 and 26.2. Read it as the authoritative translation.

✅ **The fix needs no per-branch divergence, because the constants track their own backend:**
`InputConstants.MOUSE_BUTTON_LEFT` is **0** on 1.21.11/26.1/26.2 and **1** on 26.3 — verified from
all four jars. So the named constant is one expression correct everywhere, and the identity where
nothing moved. Standards normalises **once** in `PanelHost` to the domain `InventoryPanel` now
documents (0 left, 1 right, 2 middle), which also repaired **LegendQuest's already-shipped pane**
without touching its source.

⚠ `static final int` is **inlined by javac**, so each line's jar hardcodes its own backend's number.
Compile-time correct per line, not adaptive — which the source does not read like.

**The general rule, and it is worth more than the five fixes: a platform-supplied integer compared
against a literal is a latent version break.** And the lesson that cost the most: I checked that
`InputConstants`, `UNKNOWN` and `KEY_APOSTROPHE` still *existed* on 26.3 and declared keybindings
safe. **Existence is not semantics.**

⚠ **NeoForge ADDS the new name early and REMOVES the old one later — so "renamed in version X" is
the wrong shape entirely.** Read from the patched sources of all four lines:

| | `getGuiLeft` / `getXSize` | `getLeftPos` / `getImageWidth` |
|---|---|---|
| 1.21.11 | ✔ | — |
| 26.1 | ✔ | **✔** |
| 26.2 | ✔ | **✔** |
| 26.3 | **—** | ✔ |

Both pairs coexist on 26.1 and 26.2; only 26.3 drops the old one. That is why the button fix
cherry-picked cleanly onto 26.1 and 26.2 and conflicted on 1.21.11 — correct in both cases, though
it looked like a silent breakage until the branches were compiled.

It also explains `GuiGraphicsExtractor` appearing on **mc26.1** rather than 26.2: the same
add-then-remove, not a documentation error. An earlier revision of this file claimed one; that claim
was itself wrong, which is the point worth keeping — **a two-point reading of "when did this
change?" will invent a rename that never happened.** Read every supported line before writing the
substitution down.

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
| **Mixins** | `ServerPlayerVanishMixin`, `LivingEntityVanishMixin`, `ServerLevelFireMixin`, `FireBlockMixin` | **Highest in the mod.** Five `@Inject`s across four classes: two for `/vanish` (`ServerPlayer.broadcastToPlayer`, `LivingEntity.isPushable`) and three for fire in claims (`ServerLevel.canSpreadFireAround`, and `FireBlock`'s private `checkBurnOut` and `getIgniteOdds`). A mixin that stops applying is the worst failure mode here — see below. ⚠ `checkBurnOut`'s trailing `Direction face` is a **NeoForge patch**: vanilla's own jar has five parameters, the patched one six. Read the patched sources, never the vanilla jar. |

So the honest expectation is **a handful of call sites per drop**, not a port.

### The mixins deserve their own paragraph

Standards has four. Two belong to `/vanish`, because hiding a player from *some* observers is
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

## Port by construction, not by reading — LegendQuest's technique, 2026-09-10

The best thing to come out of the panel-seam migration, and it is not about panels.

The usual way to carry a rewritten file to a version branch is to cherry-pick and then resolve the
conflicts by reading. That fails in one specific, nasty way: **a wrong resolution often draws
correctly.** Taking "ours" on a hunk where the incoming commit *removed* something leaves a
duplicate that works — a tooltip drawn twice, a fill painted twice — on one branch and not the
others. Nothing errors. Nothing looks wrong. It is found months later, if at all.

LegendQuest avoided the merge entirely:

1. Verify the 26.x file differs from `main`'s **pre-change** file by *nothing except the known
   rename substitutions*.
2. Take `main`'s **post-change** file wholesale and re-apply exactly those substitutions.
3. Verify afterwards that the ported file differs from `main`'s by *only* those substitutions —
   with a filtered diff that must come back **empty**.

The port is then correct by construction, and step 3 is the part that matters: a mistake shows up as
an **unexpected line in a diff** rather than as something you have to notice by reading. That is the
difference between a check and a hope.

⚠ It only works while the branches differ by *mechanical* substitutions. The moment a branch has a
genuine behavioural divergence, step 1 fails — which is itself the signal to stop and merge
properly, rather than a reason to skip the check. The method is safe **because the diff comes back
empty**; a non-empty diff has told you something true and the answer is to listen to it, not to
widen the filter until it passes.

⚠ **And be precise about what it does not catch.** It catches a wrong *resolution*, because that
shows up as an unexpected line. It does **not** catch a wrong *substitution list* — get the list
wrong and the filtered diff comes back clean, and is clean about the wrong thing.

The case in point is in this document. `Minecraft.setScreen` has two plausible 26.2 successors and
the obvious-looking one is wrong: `setScreenAndShow` reads like the rename, and is actually
`gui.setScreen` plus a synchronous forced frame. A port built on that belief would verify perfectly
and quietly cost a frame-time spike inside every click handler that opened a screen — the kind of
thing nobody ever traces back.

So: **the substitution list must come from the branch's own existing code**, not from what a name
looks like it should mean. Find a call site that already works on that branch and copy what it does.
That is where LegendQuest's list came from, which is why its port was right and the note it sent
about it was not.

## 26.2 moved the screen accessors, and 26.1 did **not**

⚠ **This substitution is 26.2-only. Applying it to 26.1 breaks the build**, which is the worst
shape of porting error because the two branches otherwise take the same edits.

| 1.21.11 / 26.1 | 26.2 |
|---|---|
| `mc.screen` | `mc.gui.screen()` |
| `mc.setScreen(s)` | `mc.setScreenAndShow(s)` **or** `mc.gui.setScreen(s)` |

**The two replacements are not equivalent**, and the sources say why:

```java
public void setScreenAndShow(Screen screen) {
    try (Zone ignored = Profiler.get().zone("forcedTick")) {
        this.gui.setScreen(screen);
        this.renderFrame(false);     // <- forces a frame, synchronously
    }
}
```

So `gui.setScreen` is the faithful equivalent of the old `setScreen`, and `setScreenAndShow` is that
plus an immediate forced render — which is what vanilla uses where a frame *must* appear before
something blocking. Prefer `gui.setScreen` for an ordinary "open this screen"; reach for
`setScreenAndShow` only when you need the frame now.

⚠ **Standards and Factions currently use `setScreenAndShow` on 26.2 and neither has ever been
rendered** — the 26.x branches are compile-verified only. Check this the first time a 26.2 client
actually opens the chat box from the faction pane's create button.

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

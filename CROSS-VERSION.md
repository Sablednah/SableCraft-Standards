# Living on several Minecraft lines

Standards targets **1.21.11** today. It will not stay there — Minecraft now drops roughly
quarterly (26.1 in June 2026, 26.2 in August, 26.3 due around September), and a server-utility mod
that only exists on last quarter's version is a mod nobody can use.

This is the plan for that, written now rather than discovered later. It is almost entirely borrowed
from what **CityWorld ReForged** measured the hard way across three versions in August 2026 —
see `../CityWorld-ReForged/PORTING.md`, sections "The measured 26.1 delta", "The measured 26.2
delta", and "Stage 3: what the two deltas say".

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
3. **The jar carries its Minecraft version** — `standards-0.1.0+mc1.21.11.jar`. Already set up in
   `build.gradle`. Two files both called `standards-0.1.0.jar` are indistinguishable in a mods
   folder or on a releases page, and this cost nothing to do from the start. The version *inside*
   `neoforge.mods.toml` stays a plain `0.1.0`.
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
- **Publishing to CurseForge right after a Minecraft release will fail**, because CurseForge has to
  add the version before anything can be uploaded against it. That is expected, not a bug.
- **HTTP 200 from CurseForge means accepted, not published.** It dedupes by file content, so
  re-uploading an existing release gets every file rejected as a duplicate — and rejected files are
  hidden from the author file list by default, so it looks like nothing arrived at all. The
  authoritative view is always `authors.curseforge.com/#/projects/<id>/files`.

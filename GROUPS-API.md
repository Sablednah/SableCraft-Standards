# The groups & claims API

Who is in a group with whom, and who owns this chunk — asked of Standards, answered by whoever
actually knows.

**Status: designed, not built.** This is the specification agreed on 2026-08-20, written down so
the next session starts from a decision rather than an argument. Nothing in `api/groups/` exists
yet.

## Why there is one at all

Three mods want the same two facts and none of them owns either.

LegendQuest has parties and wants guilds. ZombieMod and CityWorld want to know whether a chunk is
protected before they let something break it. A faction mod would own membership *and* land. FTB
Teams and FTB Chunks already answer both questions today, in packs Sable already runs.

Without a seam, every consumer hardcodes one answer. That is the state we found: sibling mods
checking FTB Chunks directly, which means the day a pack swaps claims mods, they all break — and
they break quietly, in the direction of *letting* something be griefed.

So Standards owns the question. It does not own the answer, and it must not.

---

## The four seams, and why this one is shaped differently

Standards now has four extension points, and they are deliberately not the same shape. Getting the
shape wrong is the expensive mistake, so it is worth stating why each is what it is.

| seam | shape | why |
|---|---|---|
| Economy | **one provider wins** | a balance is a single fact; two ledgers disagreeing is worse than either |
| Chat decorators | **everyone contributes** | a name carries a faction tag and a party tag and a rank without contradiction |
| Player switches | **an API others drive** | flight is a switch, not an opinion |
| **Groups** | **several named kinds, queried by kind** | a player is in a party *and* a faction *and* a guild, and those do not compete |

A player being in an LQ party has nothing to say about which faction they belong to. Modelling
that as one-provider-wins would force the two mods to arbitrate over a question neither is asking.
Modelling it as a flat additive list would make every consumer re-derive which group was the
faction. Kinds are the shape that fits.

---

## Query membership, own rendering

**The single most important rule in this document.** Standards must never own membership.

LegendQuest's parties already drive shared kill XP, friendly-fire suppression and party teleport.
Those are per-kill and per-tick paths. A seam that made them read another mod's truth would be a
bad seam even if it were correct, and it would be correct only until it was not.

So the split is:

- **The provider owns membership.** Who is in what, invites, lifecycle, persistence.
- **Standards owns rendering and consumption.** The scoreboard team slot, name colouring, group
  tags in chat, and the group-aware behaviour of Standards' own commands.

`groups` reads like it could mean either, which is exactly why it has to be said out loud.

### The same split applies one level down

**A provider owns what its kind is called.** LegendQuest's vocabulary system renames "party" to
*Crew* under the Wasteland and Cold Frontier packs. If Standards held that display name in its own
config, a re-themed server would render `[Party]` next to a UI saying Crew everywhere else.

Standards has a whole `{term.*}` system to prevent precisely this failure inside itself, and the
first draft of this API still hardcoded LegendQuest's vocabulary into Standards' config. **Internal
consistency does not automatically cross a seam.** Flagged by the LegendQuest session before it was
built, which is the cheap way to learn it.

So: the provider supplies the display name for its kind; Standards' config controls only *whether
and where* it renders.

---

## Exclusivity varies by kind

A player has at most one party. At most one faction. But **several staff roles at once** — a
moderator can also be a builder.

So a kind declares whether it is exclusive, and consumers pick the matching accessor:

```java
Groups.primary(player, kind)   // Optional<Group> — exclusive kinds only
Groups.all(player, kind)       // Collection<Group> — any kind
```

Returning a flat list for everything and letting callers work it out pushes the same de-duplication
into every call site, and they will not all get it right. Both cases exist on day one — parties and
factions are exclusive, roles are not — so the rule gets tested rather than assumed.

### Membership may be computed, not stored

LegendQuest intends to declare **guilds** as a kind, with membership derived from character class:
rogues and thieves in the thieves' guild, magic users in an arcane guild. No invites, no member
list, no `SavedData`.

**Build the provider interface against that case first.** An interface that quietly assumes stored
membership works fine for parties and factions and breaks on the first guild — and it breaks late,
after the shape is set.

### Groups are renameable

`/party rename` exists and players use it. Any config referencing a group must key on **kind**,
never on name, or a rename silently drops a group's styling.

---

## Claims are a query, not a possession

Claim *data* belongs to whoever owns the land model — a faction mod, or FTB Chunks. But the claim
*question* belongs in the seam, because mods that will never own land still need to ask it.

Two queries, deliberately separate:

```java
Claims.owner(level, chunkPos)          // Optional<Group> — empty means wilderness
Claims.mayModify(player, level, pos)   // boolean
```

**`owner()` is for display.** The map, the border particles, "you are entering Ravenhold".

**`mayModify()` is for grief checks, and it is the one consumers should call.** The real answer
folds in membership, trust lists, faction relations and admin bypass. Expose only `owner()` and
every consumer re-derives that rule slightly differently — one forgets allies, another forgets op
bypass — and the bugs are invisible until somebody exploits them.

### It has to be cheap

`mayModify` sits on block break, block place and block interact. It is a per-event call on a hot
path. That constrains the provider interface to a synchronous, allocation-light lookup, and it is
much easier to design in than to retrofit.

### The payoff arrives before the faction mod does

An FTB Chunks bridge answers both queries. The sibling mods stop hardcoding FTB immediately, and
if the pack later swaps to `Factions-ReForged`, none of them changes a line.

That is also what proves the API: **a seam with only in-house consumers is not a seam.** The chat
decorator API looked fine for weeks with no external consumer, and its one real code path had never
executed — LegendQuest registered the first decorator and found a name-doubling bug in about a
minute. Claims give this API a third-party consumer on day one.

---

## What ships where

| | where | why |
|---|---|---|
| Groups & claims API | **Standards** | it is the seam |
| Basic group commands — create, invite, leave, list | **Standards** | a small server should get working groups out of the box |
| Group-aware behaviour of Standards' own commands — shared homes, free `/tpa` in-group, name rendering | **Standards** | shared homes belong with homes |
| Factions — claims, relations, ranks, war, the territory map | **`Factions-ReForged`**, beside it | opinionated gameplay, not a utility |
| FTB Teams / FTB Chunks bridges | **separate or optional** | compatibility, not dependency |

### FTB Teams comes out; FTB stays testable

**Decided 2026-08-20.** Standards owns membership. FTB Teams is not the source of truth.

But **LegendQuest and Standards must keep working in a pack carrying FTB Ranks and FTB Chunks**, so
people can choose either ecosystem. Work with, not against. FTB stays installed during testing
precisely because the interesting bugs live in the overlap and only exist while both are present:

- **Command collisions.** FTB Teams registers team commands; vanilla owns `/team`. Same brigadier
  merge-order trap as `/msg` — see decision 10 in `CLAUDE.md` — and it fails silently in whichever
  direction insertion order decides.
- **The scoreboard team slot.** FTB Ranks wants it; Standards would want it. Last writer wins, and
  the two are already installed together in the `MobHealth - Forge` instance. Latent only because
  Standards does not claim that slot today.
- **Two group systems disagreeing** about a player who is in an FTB team and a Standards group,
  with FTB Chunks claims keyed to the former.

### Roles are a kind too, and LuckPerms should provide them

Staff, moderator, owner. Once roles are groups, staff chat is a group channel, the staff nameplate
colour is group rendering, and "who is on right now" is a membership query — all falling out of
machinery being built anyway.

**LuckPerms is already answering "which groups is this player in", and it is pure server-side with
no client component — so by the vanilla tiebreaker it is a keep, not a rebuild.** It is already in
the dev server, and it was verified in session three to honour Standards' default permission
resolvers.

Which makes roles the mirror image of the FTB decision: *FTB Teams out because we can do it better
and vanilla-safe; LuckPerms in as a provider because it already is vanilla-safe and we cannot.*
Same rule, opposite answers — a good sign the rule is real rather than post-hoc.

With a built-in fallback, so a server without LuckPerms still gets staff groups out of the box.

---

## Factions-ReForged: scope

Not built. Recorded so the shape is not re-argued.

**It may hard-depend on Standards.** That buys `Lang` and the `{term.*}` vocabulary, `Feedback`,
`SafeLoc`, `Waypoint`, teleports with warmup and narration, the economy for faction banks, and the
permission nodes — most of a mod that does not get written twice.

**A required dependency still crosses a jar boundary**, so the API is still proven by an outside
consumer. What it does *not* do is license reaching past the API:

> **The API is the only door, even for the mod that could pick the lock.**

The moment Factions reaches into `StandardsData` or a package-private helper, the API stops being
load-bearing and the FTB bridge — which *cannot* do that — quietly becomes a second-class citizen
supporting only whatever the API still happens to cover. If Factions needs something the FTB bridge
could not also provide, that is a signal the API is wrong, not a licence to reach through.

### PvP, and neutrality as a relation

**PvP is in.** Faction-versus-faction PvP was fundamental to ZARP, the zombie apocalypse roleplay
server this lineage comes from.

But a faction can **declare itself neutral** and opt out entirely. That is a *relation*, not a
config flag — per-faction state that other factions can see — so relations are in the data model
from day one. A peaceful server is then "everyone is neutral and cannot change it", which is a
configuration of the same model rather than a separate code path.

### Do not build a minimap

A minimap is inherently client-side, and it converts *"vanilla clients can join and play"* from a
promise into a footnote. The server owns claims; the client owns cartography. Players bring their
own Xaero's or JourneyMap.

Everything claims actually need renders on an unmodded client:

- **The classic Factions text map.** `/f map` as an ASCII grid in chat. It is how everyone
  navigated claims for a decade and it is genuinely good.
- **Particle borders.** Server-spawned particles along claim edges.
- **Territory-entry notification** on the action bar, which is most of what a minimap tells you.
- **Vanilla map items — better than expected.** Verified against the 1.21.11 source:

  ```java
  int i = 1 << this.scale;                  // blocks per pixel
  public static final int MAX_SCALE = 4;
  public byte[] colors = new byte[16384];   // 128×128, public
  public void setColor(int x, int y, byte)  // public
  ```

  **A scale-4 map is 16 blocks per pixel — exactly one chunk per pixel.** A fully zoomed-out
  vanilla map is a 128×128 *chunk* grid covering 2048 blocks, with the pixel grid already aligned
  to chunk boundaries. The server owns `colors` and pushes `ClientboundMapItemDataPacket`, so a
  vanilla client renders whatever is written; `locked()` stops vanilla's terrain scan overwriting
  it. So `/f map` can hand out a locked, claim-coloured map item that simply works unmodded.

  One limit: `TRACKED_DECORATION_LIMIT = 256`. Decorations are **one marker per faction** — sixteen
  banner colours, each carrying a name — not one per chunk. Chunks are pixels; factions are
  markers. Vanilla's own banner-on-map mechanic already gives players base markers for free.

---

## Stability

Nothing here is stable until it is built. When it is, `api/groups/` follows the same promise as
`api/economy/`: additions are fine, signature changes are not, and a consumer compiled against one
version keeps working.

Related: [`ECONOMY-API.md`](ECONOMY-API.md), [`CHAT-API.md`](CHAT-API.md),
[`COMBAT-API.md`](COMBAT-API.md).

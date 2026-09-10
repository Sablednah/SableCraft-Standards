**Status: Factions' JourneyMap integration is BUILT and compile-verified, 2026-09-11 — and nothing
has been seen on a real map yet.** The server plugin (territory polygons, standard waypoints), the
client plugin (layer toggle, claim mode, click-to-claim) and the coordinate `/f claim <x> <z>`
commands all exist and self-test. What has never happened is a rendered frame: JourneyMap is not in
the dev server's mods folder, and CityWorld's notes are emphatic that **only a real client catches a
client-plugin crash**, having had two reach a player. Treat every visual claim below as unverified.
faction claims, homes, warps, quest markers and landmarks on a map, without this series growing a
cartography mod. No code exists in any repo yet, and the version compatibility caveat in §7 is
unresolved.

---

## 1. This does not reverse "do not build a minimap"

`GROUPS-API.md` decided it, and the decision stands word for word:

> A minimap is inherently client-side, and it converts *"vanilla clients can join and play"* from a
> promise into a footnote. The server owns claims; the client owns cartography. **Players bring
> their own Xaero's or JourneyMap.**

What is new is only that **JourneyMap has a published API**, so a player who brought one can have it
decorated. We still write no renderer, no chunk cache, no threading model and no config screen. The
division is unchanged: the server owns the data, the client owns the drawing, and now there is a
seam between the two that somebody else maintains.

Everything in `GROUPS-API.md`'s list still ships and still matters — `/f map`, particle borders,
territory-entry notices, and the vanilla filled map at one chunk per pixel. **Those are the
feature.** What follows is a nicer surface for people who happen to have JourneyMap, which is the
same rule the whole client half runs on.

---

## 2. Buy, don't build

A minimap sounds like a rendering job and is not. It is a chunk cache with an eviction policy, a
background thread that does not stutter the client, a projection, a zoom model, waypoints, a config
screen, and a per-dimension storage format — before it draws anything. It would immediately be the
largest thing in this series and the least related to what any of these mods are for.

JourneyMap does maps and nothing else, which is the same argument that puts essentials in Standards
rather than in each mod that needs a `/home`.

**Soft dependency, always.** `compileOnly` plus a `ModList.get().isLoaded("journeymap")` guard,
exactly as `VanishSupport` does it. Nothing here may become load-bearing: a server whose players
have no JourneyMap loses decoration and keeps every feature.

---

## 3. What the API actually offers

Read from `github.com/TeamJM/journeymap-api` (`journeymap.api.v2`) on 2026-09-08, not from memory.
Names below are exact.

### Two tiers, and the split is the useful part

| | Where the code runs | What it can do | What the player needs |
|---|---|---|---|
| **Server overlays** | our server | draw polygons | JourneyMap, nothing else |
| **Client plugin** | our client half | draw, **and be clicked** | JourneyMap **and** our mod |

`IServerOverlayAPI.show(ServerPlayer, modId, ServerPolygon...)` pushes a polygon straight to one
player's map from the server. `ServerPolygon` is `(overlayId, dimension, List<OverlayPolygon>,
OverlayShapeProps)`, and `OverlayShapeProps` carries `fillColor`, `fillOpacity`, `strokeColor`,
`strokeWidth`, `strokeOpacity`, `displayOrder`, `minZoom`, `maxZoom`, `activeUIs`, `activeMapTypes`
and a `label`.

**That is the whole of the passive half, with no client code of ours at all.** Translucent claims
with solid borders, on the minimap and the fullscreen map, for anybody running JourneyMap. It is
also the half that matters most and the half that is cheapest.

### The interactive half needs a client plugin

`IClientPlugin`, and then:

- **`PolygonOverlay`** — `setOuterArea(MapPolygon)`, `setHoles(List<MapPolygon>)`,
  `setPolygonWithHoles(...)`, `setShapeProperties(ShapeProperties)`. Holes matter more than they
  sound: a faction's territory with an enemy chunk taken out of the middle *is* a polygon with a
  hole, and drawing it as separate squares loses the shape.
- **`MarkerOverlay`** and **`ImageOverlay`** — icons. Standards' waypoints, faction standards.
- **`Overlay.setOverlayListener(IOverlayListener)`**, **`setDisplayOrder(int)`**.
- **`IOverlayListener`**, which is where the two questions get answered:

  ```java
  boolean onMouseClick(UIState, Point2D.Double mouse, BlockPos block, int button, boolean doubleClick)
  void    onOverlayMenuPopup(UIState, Point2D.Double mouse, BlockPos block, ModPopupMenu menu)
  void    onActivate / onDeactivate / onMouseMove / onMouseOut
  ```

  **It hands you a `BlockPos`.** So click-to-claim is `blockPos >> 4` and a `/f claim` — no
  projection arithmetic of our own. Returning `true` consumes the click.
- **`ModPopupMenu`** — `addMenuItem(String, Action)`, `addMenuItemScreen(String, Screen)`,
  `createSubItemList(String)`, with `Action.doAction(BlockPos)`. A right-click menu on a faction's
  territory is what this is for, and `addMenuItemScreen` can open the faction panel directly.
- **`FullscreenDisplayEvent.AddonButtonDisplayEvent`** → `getThemeButtonDisplay()` →
  **`ThemeButtonDisplay.addThemeToggleButton(label, icon, toggled, onPress)`**, returning an
  `IThemeButton` with `setToggled`/`getToggled`/`toggle`. A real toggle, so *claim mode: on* is a
  first-class state rather than something we fake.
- **`Context.UI`** is `Fullscreen`, `Minimap` **and** `Webmap` — one registration covers all three.

---

## 4. Who contributes what

**Each mod owns its own plugin.** Standards brokers nothing here, and that is a deliberate
departure from how the economy, chat and actions seams work.

The reason: those seams exist because two mods wanted to write the *same fact* and something had to
own the meeting point. Here there is no shared fact. Claims, quest markers and landmarks are
unrelated data going to a third party's API that every mod can call directly. A Standards wrapper
would be a thin pass-through that adds a version to keep in step and answers no question.

| Mod | Draws | Needs interaction? |
|---|---|---|
| **Standards** | waypoints for **homes**, **warps** and the **death point** | no — waypoints are enough |
| **Factions** | claim polygons by relation, standard icons | **yes** — click to claim, right-click for relations |
| **Chronicler** | quest markers, NPCs, shops | probably — click to track a quest |
| **CityWorld** | landmarks | no |

Standards' share is smaller than it looks and worth doing first *because* it is small: homes, warps
and the last death are already `SavedData`, they are per-player, and they need no interaction. It is
the cheapest possible proof that the dependency, the guard and the version actually work.

---

## 5. What Factions gets, concretely

The passive half needs no client code:

- every claim as a polygon, **coloured by your relation to the owner** — the same green/blue/red
  `/f map` already uses, so the two agree;
- solid borders, translucent fill, so terrain reads through;
- a standard's position as a marker, since "where is their flag" is the question a raid asks.

The interactive half, with the client plugin:

- a **toolbar toggle** for claim mode, so clicking the map is deliberate rather than something you
  do by accident while panning;
- **click a chunk to claim it** while that toggle is on — the single biggest quality-of-life win,
  because claiming a border today means walking it;
- **right-click a faction's territory** for *Ally* / *Enemy* / *Neutral* / *Pay…* / *Open panel*,
  with `addMenuItemScreen` wiring the last one straight to the panel that already exists.

Every one of those sends a **command** — `/f claim`, `/f ally`, `/f panel` — so the permission and
rank checks that already guard them apply unchanged, and there is no new server surface. That is the
same rule the action bar runs on and it is why this is a smaller job than it looks.

---

## 6. What this does *not* justify

- **Rendering claims ourselves inside JourneyMap.** Use the overlay types; a custom renderer is the
  minimap decision arriving through the back door.
- **Requiring JourneyMap for anything.** If a feature only works with it, it is a feature we have
  built wrong.
- **A Standards map seam.** Written above; recorded here so it is a decision rather than an
  omission somebody later "fixes".
- **Xaero's as well.** Different API, double the surface, and the passive half is most of the value.
  Worth revisiting only if somebody actually asks.

---

## 7. Verify before designing around any of it

⚠ **The interfaces above were read; none of it has been run.** The single thing to settle first is
whether `journeymap-api` v2 matches the JourneyMap build people are actually running on 1.21.11 —
and then on 26.1 and 26.2, where a client-side API is exactly the kind of thing that moves. The GUI
rework in 26.x cost more to port than every server-side divergence in this series put together.

So: **a hello-world polygon first**, from the server API, before a line of design is committed to.
One claim, one colour, on one player's map. If that renders on all three Minecraft lines the rest is
ordinary work; if it does not, this document is the cheapest thing to have thrown away.

## 8. Order of work

1. A **server-side** hello-world polygon on 1.21.11, then on 26.1 and 26.2. Nothing else until it
   draws.
2. **Standards' waypoints** — homes, warps, death point. Small, no interaction, proves the guard.
3. **Factions' claim polygons**, server-side, coloured by relation. Still no client plugin.
4. The **client plugin**: the claim-mode toggle, then click-to-claim.
5. The **right-click relations menu**, and `addMenuItemScreen` to the faction panel.
6. Chronicler and CityWorld, by whoever owns them, against whatever this has learned.

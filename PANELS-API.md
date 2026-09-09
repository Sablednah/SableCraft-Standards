# Panels — who draws on the inventory screen

**Status: built 2026-09-09, and driven the same day by Factions' faction panel.** The seam, the
one-pane-at-a-time arbitration and the `LEFT` area are built and in use. `BUTTON_STRIP` is specified
below and **not built** — see §6 for why that is deliberate rather than unfinished.

**Intended second consumer: LegendQuest**, whose character and skills panes are the reason the
arbitration is shaped the way it is. Nothing in LegendQuest has changed yet.

---

## 1. The problem, which is not a layout problem

Four things want the left margin of the inventory screen:

| | Where it draws | How it takes the space |
|---|---|---|
| Vanilla's recipe book | left | shifts the inventory right |
| LegendQuest's character pane | left | shifts the inventory right |
| LegendQuest's skills pane | left | shifts the inventory right |
| Factions' faction panel | left | draws in the margin |

And the right is not free either. **Vanilla's potion effects render at `leftPos + imageWidth + 2`**,
which is exactly where a right-hand pane goes; JEI's ingredient list is usually there too.

Factions' panel was on the right for one afternoon and did both of the things that space punishes:
it hid the player's active potion effects, and JEI's tooltips went on firing underneath it because
nothing had told JEI it was there.

**Every one of those mods was behaving reasonably on its own.** That is the whole finding. The fix
is not a better guess about where to draw — there is no free space to guess at — it is that
somebody has to decide, and the decision has to be visible to mods that cannot see each other.

## 2. The shape

A mod says **what** it wants to draw. Standards says **where**, and **only one pane is ever open**.

```java
// once, from client setup
Panels.register("legendquest:character", Panels.Area.LEFT, CharacterPane.INSTANCE);

// from a button, a keybind, anywhere
Panels.toggle("legendquest:character");
```

```java
public interface InventoryPanel {
    int preferredWidth();
    void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY);

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseScrolled(double mouseX, double mouseY, double delta) { return false; }
    default void onOpen() {}
    default void onClose() {}
    default boolean available() { return true; }
}
```

You are handed a rectangle and the mouse. You do not choose it, you cannot move it, and **it changes
between frames** — the window resizes, the recipe book shifts the inventory, the player changes GUI
scale. Lay out from the arguments every frame. A pane that caches its own coordinates is the bug
this seam exists to stop.

Clicks and scrolls arrive only while you are the open pane and only inside your rectangle, so there
is nothing to bounds-check but the things inside it.

Standards draws the background, the border and the frame, so that four mods' panes on one server
look like the same furniture rather than like four mods each having a go.

## 3. ⚠ Vanilla still wins, and is never asked

The recipe book is not a registrant and cannot be made one. But it announces itself:

```java
// AbstractContainerScreen.init
this.leftPos = (this.width - this.imageWidth) / 2;

// AbstractRecipeBookScreen, on open
this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
```

So the whole occlusion check is one comparison and **no reflection at all**:

```java
screen.getGuiLeft() != (screen.width - screen.getXSize()) / 2
```

If the inventory is not centred, somebody has taken the left, and the open pane stands down.

**This is worth more than it looks.** It catches LegendQuest's panes too — they shift the inventory
the same way, and know nothing about this seam. A mod that plays by vanilla's own convention is
handled correctly without ever having been asked to cooperate, which is the only kind of cooperation
you can actually rely on from code you do not control.

Since Standards now moves the inventory itself (§3a), the test has a second half: off centre *at the
value we last wrote* is our own shift, not somebody else's.

## 3a. Moving the inventory, and the access transformer it costs

The first version drew in whatever margin the centred inventory happened to leave and moved nothing.
That was the cheap correct thing, and it looked wrong beside the recipe book and beside LegendQuest,
both of which slide the inventory right. Consistency won, and it cost two things worth naming.

**Standards gained its first access transformer**, for `AbstractContainerScreen.leftPos` — protected,
no setter. The alternative was reflection, which is what LegendQuest does today:

```java
LEFT_POS = AbstractContainerScreen.class.getDeclaredField("leftPos");
...
throw new IllegalStateException("LegendQuest: inventory screen internals moved", e);
```

Same fragility, worse failure. Reflection finds out at **runtime**, in front of a player, as an
exception on the inventory screen. An access transformer finds out at **build** time, on the machine
of whoever is doing the port, with the field named in the error. The field has to be written either
way, so being told early is the whole of the difference. It is the mod's only AT and should stay
that way — the note in `accesstransformer.cfg` is written for whoever wants to add a second.

⚠ **Only Standards needs it.** Factions moves nothing and has no AT: it hands over a pane and is
handed a rectangle. That is the seam earning its keep — one mod carries the fragile surface and
every other mod is spared it, which is also the strongest argument for LegendQuest adopting, since
LQ currently carries two reflective reads of its own.

⚠ **Moving `leftPos` is not enough on its own.** Vanilla sets it *and* repositions the recipe-book
button in the same handler:

```java
this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
ScreenPosition p = this.getRecipeBookButtonPosition();   // leftPos + 104
button.setPosition(p.x(), p.y());
```

So the host finds that button on `Init.Post` — the only `ImageButton` on the inventory screen, 20×18
— measures its offset from `leftPos` rather than hardcoding 104, and moves the two together. **If it
is not found, the pane does not shift at all** and falls back to margin drawing: losing the shift is
a far better way to be wrong than moving the inventory and abandoning vanilla's own button inside
the pane.

⚠ **The shift happens in `Render.Pre`, not `Post`.** Post fires after the inventory has drawn, so
moving `leftPos` there would place the inventory from last frame's value and the pane from this
frame's — one frame of the two overlapping, every time the pane opens. Layout, then draw, is the
only order in which they cannot disagree.

⚠ **Use vanilla's number, not a better one.** The first version centred the pane and the inventory
as a block, which is defensible arithmetic and puts the inventory somewhere no other panel on the
screen puts it — so opening a Standards pane and then a LegendQuest one made the inventory hop.
Everyone here uses the same formula, which is the recipe book's own:

```java
leftPos = 177 + (width - imageWidth - 200) / 2;
```

Below 379px vanilla does not shift at all (`widthTooNarrow`), and neither do we: the pane overlays
whatever margin exists, clamped.

⚠ **Vanilla's recipe button must be repositioned every frame, not only when we move it.** Vanilla
moves it inside its own click handler, so it goes stale the moment anybody else writes `leftPos` —
and ours would go stale whenever vanilla does. LegendQuest found this and says so in a comment.
Chasing `getGuiLeft()` every frame is the only arrangement where nobody has to be told.

## 3b. The recipe book is modal, and it wins

An open pane closes when the recipe book opens. The first version stood the pane *down* — kept it
open and stopped drawing it — which is correct bookkeeping and reads, from the player's side, as a
pane that "stays active but behind": its button still lit, its space still spoken for, and nothing
visible to close. LegendQuest resolves it the same way and in the same place, and the pane being put
away is what a player expects from something that shares a space with a modal.

**Both directions, and they are not the same rule.** "The recipe book wins" is right when the book
is what you just opened and exactly wrong when the pane is — clicking a panel button while the book
is up must open the panel, not refuse. So the two are told apart by who moved last: `Panels.open`
puts the book away at the moment of opening, which means that by the time the layout pass runs, a
visible book can only be one opened *after* the pane, and closing the pane is then unambiguous.
Closing it is `toggleVisibility()` and nothing else — vanilla's own button also recomputes `leftPos`
and moves itself, both of which already happen every frame, and sets a flag that swallows the next
mouse-release for the button that was pressed. That button was ours, so honouring it would eat the
first click on the pane that just opened.

Asking is exact rather than inferred: `AbstractRecipeBookScreen` keeps its `RecipeBookComponent`
private, but adds it to the screen with `addWidget`, so it arrives in `Init.Post`'s listener list
like any other — **no reflection and no second access transformer**. `RecipeBookComponent` is public,
and so are `isVisible()` and `updateScreenPosition(...)`. The second of those is also how a pane
puts the inventory *back*: restore to whatever vanilla wants at that moment rather than to a
hardcoded centre, which would be the wrong answer if the book had opened meanwhile.

**Standing down is not closing.** An occluded pane stays open and simply is not drawn, so it comes
back when the recipe book closes. Closing it would mean reopening something you never shut — and
worse, pressing the button while the recipe book was open would open a pane that immediately hid
itself again, which reads as a broken button.

## 4. What this asks of LegendQuest

Nothing, today. LQ works as it is, and the occlusion rule already keeps Factions' pane out of its
way. What adoption would buy:

- **Opening the faction panel would close LQ's pane, and vice versa.** ⚠ This is now the one real
  gap rather than a nicety. Since Standards adopted vanilla's shift formula — which is also LQ's —
  an inventory sitting at that value is indistinguishable from one we shifted ourselves. So the
  faction pane stands down correctly when LQ's is opened **first**, and if the faction pane is
  already open when LQ's opens, the two overlap. Matching everybody else's position cost that, and
  it was the right trade: a pane that lands where no other pane lands is wrong every time, where
  this is wrong only with two panes open at once. Registering here closes it.
- **LQ stops needing its own reflection.** `CharacterPanel` reads `AbstractContainerScreen.leftPos`
  and `AbstractRecipeBookScreen.recipeBookComponent` reflectively, and throws
  `IllegalStateException("LegendQuest: inventory screen internals moved")` if either goes. That is
  two private vanilla fields on the version-fragile list, in a mod that has three other Minecraft
  lines to keep up with. Standards carries one access transformer so that no consumer has to carry
  anything — and an AT breaks the build rather than the game.
- **One frame style** across LQ, Factions and whatever comes next.

What it would cost: very little now. Standards' panes shift the inventory exactly as LQ's do, so
the two behave the same way — that objection is what §3a was written to remove.

**Standards would become a hard dependency of LegendQuest.** That is the owner's call, not this
document's. It is already a hard dependency of Factions, and LQ already depends on Standards'
economy, vanish and chat seams at runtime — so the change is one of declaration more than of
substance. **Not yet done, and not to be done from a Standards session.**

## 5. The rule that outranks everything here

Same as the action bar's, and it is decision 2 of `CLAUDE.md` rather than a preference:

> **A pane may only present what the server would have told a vanilla player anyway.**

A nicer surface for the same answer, never a second capability. Factions' panel is drawn from the
reply to `/f panel` — the identical command a vanilla client sends — and every button on it sends a
command that could have been typed. If a pane can show something no command will say, the vanilla
client has lost something.

## 6. `BUTTON_STRIP`, specified and not built

The area is named in `Panels.Area` and there is no code behind it. That is on purpose, and the
reason is worth writing down because it will look like an oversight:

**LegendQuest already draws two tab buttons beside the recipe-book button**, at a position it chose
before this seam existed. `InventoryScreen.getRecipeBookButtonPosition()` returns
`leftPos + 104, height / 2 - 22`, and the button is 20×18, so "beside it" is one specific pair of
pixels that LQ has already taken. Building a strip there now means shipping overlapping buttons
today in exchange for tidiness later.

So when it is built, the strip stacks **below** the recipe-book button rather than beside it.
Adopting the seam is then a move rather than a collision, and when LQ does adopt, Standards lays the
whole row out at once and the offset goes away.

⚠ And it is not built at all until something registers with it. This repo has a standing rule that
a seam is not done until a real consumer is on the other side of it — see the section in `CLAUDE.md`
about code that has never met real input. An unused area would be exactly that.

## 7. See also

- `CLIENT.md` — the client half as a whole: the capability payload, the action bar, keybinds, and
  the two ways a button can do something on a modded client.
- `CLAUDE.md` decision 2 — why a vanilla client loses nothing, which is what constrains all of this.
- `MAP-API.md` — the other client-side plan, and deliberately unrelated: maps go to JourneyMap.

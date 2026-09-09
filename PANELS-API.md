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

The obvious alternative was reflection on `leftPos`, to shift the inventory ourselves the way the
recipe book does. That is the wrong trade: private-field access into a vanilla screen is a
version-fragile surface, and `CLAUDE.md` spends a whole decision on why this mod keeps exactly one
of those. So a Standards pane draws in the margin and is clamped, rather than moving anything.

**Standing down is not closing.** An occluded pane stays open and simply is not drawn, so it comes
back when the recipe book closes. Closing it would mean reopening something you never shut — and
worse, pressing the button while the recipe book was open would open a pane that immediately hid
itself again, which reads as a broken button.

## 4. What this asks of LegendQuest

Nothing, today. LQ works as it is, and the occlusion rule already keeps Factions' pane out of its
way. What adoption would buy:

- **Opening the faction panel would close LQ's pane, and vice versa.** Right now the faction pane
  merely hides behind LQ's, which is correct but not the same as tidy.
- **LQ stops needing its own reflection.** `CharacterPanel` reads `AbstractContainerScreen.leftPos`
  and `AbstractRecipeBookScreen.recipeBookComponent` reflectively, and throws
  `IllegalStateException("LegendQuest: inventory screen internals moved")` if either goes. That is
  two private vanilla fields on the version-fragile list, in a mod that has three other Minecraft
  lines to keep up with. The seam has none.
- **One frame style** across LQ, Factions and whatever comes next.

What it would cost: LQ's panes currently *shift* the inventory and Standards' do not, so its two
panes would sit in the margin instead. On a narrow window that is a visible difference. It is also
the reason the seam has no `shift` option — adding one means adding the reflection back for
everybody, which is the thing being escaped.

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

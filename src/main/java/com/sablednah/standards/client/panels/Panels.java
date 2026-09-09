package com.sablednah.standards.client.panels;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who may draw on the inventory screen, and where — the registry and the arbitration.
 *
 * <h2>One pane at a time, and that is the whole feature</h2>
 *
 * <p>The seam's value is not the layout arithmetic; it is that opening one pane closes the others.
 * Two panes in one space is the failure every mod here arrived at independently, and no amount of
 * each of them being careful fixes it, because none of them can see the others.</p>
 *
 * <h2>Vanilla still wins, and is not asked</h2>
 *
 * <p>The recipe book is not a registrant and cannot be made one. But it announces itself: opening it
 * <b>moves the inventory off centre</b>, since {@code AbstractRecipeBookScreen} reassigns
 * {@code leftPos} from {@code recipeBookComponent.updateScreenPosition(...)} where
 * {@code AbstractContainerScreen.init} had put it at {@code (width - imageWidth) / 2}.</p>
 *
 * <p>So {@link #occluded} is one comparison and <b>no reflection at all</b>: if the inventory is not
 * centred, somebody has taken the left, and we stand down. That is worth more than it looks —
 * it catches LegendQuest's panes too, which shift the inventory the same way and know nothing about
 * this seam. A mod that plays by vanilla's own convention is handled without having been asked to
 * cooperate, which is the only kind of cooperation you can actually rely on.</p>
 *
 * <p>Reaching for reflection on {@code leftPos} was the obvious alternative and is the wrong trade:
 * private-field access into a vanilla screen is a version-fragile surface, and {@code CLAUDE.md}
 * spends decision 9 on why this mod keeps exactly one of those.</p>
 *
 * <h2>Standing down rather than closing</h2>
 *
 * <p>An occluded pane stays <em>open</em> and is not drawn, so it comes back when the recipe book
 * closes. Closing it instead would mean the player has to reopen something they never shut, and
 * — worse — pressing the button while the recipe book is open would open a pane that immediately
 * hides again, which reads as a broken button.</p>
 */
public final class Panels {

    /**
     * Where a pane can live. Deliberately few, and none of them are "wherever you like".
     *
     * <p>There is no {@code RIGHT}. That space is vanilla's: potion effects render at
     * {@code leftPos + imageWidth + 2}, and a pane there hides live status information with no way
     * to get it back. Factions' panel was there for exactly one afternoon and that is what it did.
     * JEI's ingredient list is usually there too. If a right-hand area is ever added it will have to
     * negotiate with both, and it will not be added speculatively.</p>
     */
    public enum Area {
        /**
         * Beside the inventory, on the left, where the recipe book goes.
         *
         * <p>Ours does <b>not</b> shift the inventory the way the recipe book does, because shifting
         * means writing {@code leftPos} and that is the reflection this seam refuses. It draws in
         * the margin instead and is clamped rather than allowed off-screen.</p>
         */
        LEFT,

        /**
         * The little buttons under the recipe-book button.
         *
         * <p><b>Under</b>, not beside, and that is not an aesthetic choice: LegendQuest already
         * draws its two tab buttons beside it, at a position it picked before this seam existed.
         * Stacking below means adopting this seam is a move rather than a collision, and until LQ
         * does adopt, nothing overlaps. When it does, Standards lays the whole row out at once and
         * the offset goes away.</p>
         */
        BUTTON_STRIP
    }

    /** Package-private, not public: what is registered is Standards' bookkeeping, not API. */
    record Registration(String id, Area area, InventoryPanel panel) {}

    private static final Map<String, Registration> PANELS = new ConcurrentHashMap<>();

    /** The one showing, if any. */
    private static volatile String openId;

    /**
     * Offer a pane.
     *
     * <p>Registration does not open it and does not reserve anything — it says the pane exists and
     * what it wants. Whether it is ever shown is the player's business.</p>
     *
     * @throws IllegalArgumentException if the id is already taken, which is a mistake somewhere
     *         rather than something to resolve silently
     */
    public static void register(String id, Area area, InventoryPanel panel) {
        // ⚠ Refused rather than accepted and drawn as a LEFT pane. BUTTON_STRIP is specified in
        // PANELS-API.md §6 and has no code behind it, and silently treating it as something else
        // would be the worst of both: a registration that appears to work and puts a row of little
        // buttons in the margin. Say so, loudly, at the moment somebody tries.
        if (area == Area.BUTTON_STRIP) {
            throw new UnsupportedOperationException(
                    "Standards: Area.BUTTON_STRIP is specified but not built — see PANELS-API.md");
        }
        Registration previous = PANELS.putIfAbsent(id, new Registration(id, area, panel));
        if (previous != null) {
            throw new IllegalArgumentException("Standards: a panel is already registered as " + id);
        }
    }

    /** Show this one, closing whatever was showing. Unknown ids are ignored. */
    public static void open(String id) {
        Registration next = PANELS.get(id);
        if (next == null || id.equals(openId)) {
            return;
        }
        close();
        openId = id;
        next.panel().onOpen();
    }

    /** Put away whatever is showing. */
    public static void close() {
        String was = openId;
        if (was == null) {
            return;
        }
        openId = null;
        Registration reg = PANELS.get(was);
        if (reg != null) {
            reg.panel().onClose();
        }
    }

    /** Show it, or put it away if it is already showing — what a toggle button calls. */
    public static void toggle(String id) {
        if (id.equals(openId)) {
            close();
        } else {
            open(id);
        }
    }

    /**
     * Whether this pane is the one showing.
     *
     * <p>True even while {@link #occluded}, because it <em>is</em> open — the recipe book is merely
     * standing in front of it. A button that went dark whenever the recipe book opened would be
     * reporting the recipe book's state, not its own.</p>
     */
    public static boolean isOpen(String id) {
        return id.equals(openId);
    }

    /** The pane showing, if any. */
    static Optional<Registration> showing() {
        String id = openId;
        return id == null ? Optional.empty() : Optional.ofNullable(PANELS.get(id));
    }

    private Panels() {}
}

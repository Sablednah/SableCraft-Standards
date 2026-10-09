package com.sablednah.standards.api.chat;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A {@link MessageFilter}'s verdict on one message: let it through, censor it, shadow it, or stop
 * it.
 *
 * <p>A censor is <b>per viewer</b>. Standards already delivers its formatted lines one recipient at
 * a time, so it costs nothing to let a filter say who still sees the original — the sender
 * themselves, for a "silent" filter that does not tell anyone they were caught, or staff with a
 * see-everything permission. Paths that cannot do per-viewer (mail, a routed channel) get the
 * censored text.</p>
 *
 * <p>When filters disagree the harsher verdict wins: block, then shadow, then censor, then pass.</p>
 */
public final class Screening {

    private static final Screening PASS = new Screening(Kind.PASS, null, null, v -> true, false);

    // Declared mildest first: chaining keeps whichever ranks higher.
    private enum Kind { PASS, CENSOR, SHADOW, BLOCK }

    private final Kind kind;
    private final String censored;
    private final Component reason;
    private final Predicate<ServerPlayer> seesOriginal;
    private final boolean vanillaHandled;

    private Screening(Kind kind, String censored, Component reason,
            Predicate<ServerPlayer> seesOriginal, boolean vanillaHandled) {
        this.kind = kind;
        this.censored = censored;
        this.reason = reason;
        this.seesOriginal = seesOriginal;
        this.vanillaHandled = vanillaHandled;
    }

    /** Nothing to do. */
    public static Screening pass() {
        return PASS;
    }

    /**
     * Show {@code censored} instead, except to viewers the predicate lets see the original.
     *
     * @param seesOriginal asked once per recipient; {@code v -> false} censors for everyone
     */
    public static Screening censor(String censored, Predicate<ServerPlayer> seesOriginal) {
        return new Screening(Kind.CENSOR, censored, null, seesOriginal, false);
    }

    /**
     * Stop the message outright.
     *
     * @param reason told to the sender; {@code null} says nothing, for a filter that would rather
     *               the sender did not know
     */
    public static Screening block(Component reason) {
        return new Screening(Kind.BLOCK, null, reason, v -> false, false);
    }

    /**
     * Let the sender believe it went out, and deliver it to nobody else.
     *
     * <p>Standards renders the sender's copy exactly as a delivered line would look — same
     * decorations, same nickname, same {@code /msg} confirmation — which only Standards can do. A
     * filter faking this with a block and a hand-built copy gives itself away the moment a server
     * decorates chat. A letter reports "sent" and is not stored. The console and social spy still
     * see it.</p>
     *
     * <p>⚠ A line a {@link ChatRouter} would have claimed is not routed: the sender sees it as an
     * ordinary chat line, because routers render their own lines and have no per-viewer hook.</p>
     */
    public static Screening shadow() {
        return new Screening(Kind.SHADOW, null, null, v -> false, false);
    }

    /**
     * Marks a censor as already arranged for vanilla's own delivery — through the client's chat
     * filter mask, say — so a line Standards would otherwise leave untouched can stay a signed
     * vanilla message. Without this, Standards rewrites the body of such a line itself, and every
     * viewer, the sender included, sees the censored form.
     */
    public Screening vanillaHandled() {
        return kind == Kind.CENSOR
                ? new Screening(kind, censored, reason, seesOriginal, true) : this;
    }

    public boolean passed() {
        return kind == Kind.PASS;
    }

    public boolean blocked() {
        return kind == Kind.BLOCK;
    }

    public boolean censored() {
        return kind == Kind.CENSOR;
    }

    /** Delivered to the sender alone. See {@link #shadow()}. */
    public boolean shadowed() {
        return kind == Kind.SHADOW;
    }

    /** Whether a vanilla-delivered line already carries this censor. Always false unless censored. */
    public boolean isVanillaHandled() {
        return vanillaHandled;
    }

    /** What to tell the sender of a blocked message, if anything. */
    public Optional<Component> reason() {
        return Optional.ofNullable(reason);
    }

    /** The censored text, or {@code original} if this did not censor. */
    public String text(String original) {
        return kind == Kind.CENSOR ? censored : original;
    }

    /** The text this particular viewer should see. */
    public String textFor(ServerPlayer viewer, String original) {
        if (kind != Kind.CENSOR) {
            return original;
        }
        return seesOriginal.test(viewer) ? original : censored;
    }

    /**
     * Chain a later filter's verdict onto this one. The harsher kind wins; two censors compose,
     * and a viewer sees the original only if both filters would have let them.
     */
    Screening then(Screening next) {
        if (next.kind.ordinal() > kind.ordinal()) return next;
        if (kind != Kind.CENSOR || next.kind != Kind.CENSOR) return this;
        Predicate<ServerPlayer> both = seesOriginal.and(next.seesOriginal);
        return new Screening(Kind.CENSOR, next.censored, null, both,
                vanillaHandled && next.vanillaHandled);
    }
}

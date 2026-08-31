package com.sablednah.standards.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which rule answers a permission question, and why.
 *
 * <p>Pure: no Minecraft, no NeoForge, no saved data. That is deliberate rather than tidy. The
 * whole value of a permissions system is being able to say <em>why</em> a player has something,
 * and a resolver tangled up in a live server is one that can only be tested by playing on one.
 * Everything here takes plain maps and returns a plain answer, so {@code SelfTest} exercises the
 * real code against fixtures instead of a duplicate.</p>
 *
 * <h2>The order</h2>
 *
 * <p>Scopes arrive as <b>tiers</b>, nearest the player first: the player's own grants, then the
 * groups they are directly in, then those groups' parents, and so on outward, with the default
 * group last. The first tier that says anything wins outright — which is what makes "an explicit
 * deny on the player beats everything" and "a deny in a group beats a grant in its parent" the
 * same rule rather than two.</p>
 *
 * <p>Within one tier, the <b>most specific pattern</b> wins: an exact {@code standards.fly} beats
 * {@code standards.home.*} beats {@code standards.*} beats {@code *}. Where two scopes in the
 * same tier match equally well — a player in both {@code moderator} and {@code guest}, one
 * granting and one denying — <b>the deny wins</b>. There are no weights to break the tie with, and
 * quietly picking the permissive half of a contradiction is the wrong direction to guess in.</p>
 *
 * <h2>What a wildcard covers</h2>
 *
 * <p>{@code standards.home.*} matches {@code standards.home.others} and also {@code standards.home}
 * itself. The alternative — a trailing wildcard that grants every child of a node but not the node
 * — is defensible and every admin who has met it has been baffled by it: granting
 * {@code standards.home.*} and finding {@code /home} still refused reads as the system being
 * broken.</p>
 */
public final class PermissionRules {

    /** One place an answer can come from: a player's own grants, or one group's. */
    public record Scope(String name, Map<String, Boolean> nodes) {}

    /**
     * The answer and its provenance.
     *
     * @param allowed what the rule said
     * @param scope   which scope said it — "you", or a group name
     * @param pattern the pattern that matched, which may be a wildcard rather than the node asked
     */
    public record Answer(boolean allowed, String scope, String pattern) {}

    /**
     * Resolve one node against the tiers, nearest first.
     *
     * @return empty when nothing anywhere has an opinion, which is the caller's cue to fall
     *         through to the node's own default resolver — the step that makes switching this
     *         handler on safe, because a server that grants nothing behaves exactly as before
     */
    public static Optional<Answer> resolve(List<List<Scope>> tiers, String node) {
        for (List<Scope> tier : tiers) {
            Answer best = null;
            int bestScore = -1;
            for (Scope scope : tier) {
                for (Map.Entry<String, Boolean> rule : scope.nodes().entrySet()) {
                    int score = specificity(rule.getKey(), node);
                    if (score < 0) {
                        continue;
                    }
                    // Strictly better, or equally specific and a denial — a contradiction inside
                    // one tier resolves to no.
                    boolean better = score > bestScore
                            || (score == bestScore && best != null && best.allowed()
                                    && !rule.getValue());
                    if (better) {
                        bestScore = score;
                        best = new Answer(rule.getValue(), scope.name(), rule.getKey());
                    }
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    /**
     * How well a stored pattern matches a node, higher being more specific.
     *
     * @return the number of characters the pattern pins down, or {@code -1} if it does not match
     *         at all. An exact match scores above every wildcard by construction, because it pins
     *         down the whole node and then some.
     */
    public static int specificity(String pattern, String node) {
        if (pattern.equals(node)) {
            return node.length() + 1;
        }
        if (pattern.equals("*")) {
            return 0;
        }
        if (!pattern.endsWith(".*")) {
            return -1;
        }
        String prefix = pattern.substring(0, pattern.length() - 2);
        // The node itself, or anything beneath it. See the class note on why the bare node counts.
        return node.equals(prefix) || node.startsWith(prefix + ".") ? prefix.length() : -1;
    }

    private PermissionRules() {}
}

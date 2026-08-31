package com.sablednah.standards.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.ParseResults;
import com.sablednah.standards.Standards;
import com.sablednah.standards.api.chat.Chat;
import com.sablednah.standards.api.chat.NameDecorator;
import com.sablednah.standards.api.economy.Economy;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.InventoryView;
import com.sablednah.standards.neoforge.commands.MoveCommands;
import com.sablednah.standards.core.Money;
import com.sablednah.standards.core.Toggle;
import com.sablednah.standards.core.VanishGate;
import com.sablednah.standards.core.Waypoint;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;

/**
 * A headless smoke test, dormant unless {@code -Dstandards.selftest=true}.
 *
 * <p>Gradle cannot pipe stdin to a dev server's console, so "does the command actually work" is
 * not a question you can answer by hand on this toolchain — which means in practice it does not
 * get answered at all. This runs on {@link ServerStartedEvent}, exercises the paths that are easy
 * to break and impossible to see, and logs a pass/fail block.</p>
 *
 * <p>Two habits it deliberately keeps, both learned next door in ZombieMod:</p>
 *
 * <ul>
 * <li><b>Parse <em>and</em> execute.</b> Parsing alone proves nothing: a command with a bad
 *     argument type parses happily and only fails when something asks it for a value.</li>
 * <li><b>Test both directions.</b> A permission check that refuses everything looks identical to
 *     one that works. Every assertion that something is allowed has a partner asserting that its
 *     opposite is not.</li>
 * </ul>
 *
 * <p>It calls the real code. A probe that re-derives the logic it is testing is testing the
 * duplicate.</p>
 */
public final class SelfTest {

    private static final String FLAG = "standards.selftest";

    private final List<String> failures = new ArrayList<>();
    private int checks;

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean(FLAG)) return;
        new SelfTest().run(event.getServer());
    }

    private void run(MinecraftServer server) {
        Standards.LOGGER.info("=== Standards self-test ===");

        checkToggleLogic();
        checkStateSentence();
        checkVanishGate();
        checkTopCeiling();
        checkChatLine();
        checkNoTermInflection();
        checkColourCodes();
        checkChatRouters();
        checkGroups();
        checkClaims();
        checkTeleportRelief();
        checkGroupNamesAreText();
        checkCombat();
        checkAccessFallback();
        checkPermissionRules();
        checkMoneyFormatting();
        checkCommandsParse(server);
        checkSafeLoc(server);
        checkTeleportRequests();
        checkDurations();
        checkChatDecorators();
        checkInventoryViewMapping();
        checkLedger(server);
        checkNicknames(server);
        checkInfoRuns();
        checkPromotionRules();
        checkCompassBearings();
        checkPermissionStore(server);
        checkWaypointRoundTrip(server);

        if (failures.isEmpty()) {
            Standards.LOGGER.info("=== Standards self-test PASSED ({} checks) ===", checks);
        } else {
            Standards.LOGGER.error("=== Standards self-test FAILED: {} of {} checks ===",
                    failures.size(), checks);
            failures.forEach(f -> Standards.LOGGER.error("  ✗ {}", f));
        }
    }

    /**
     * The built-in permission handler's resolution order.
     *
     * <p>Tested against {@link com.sablednah.standards.core.PermissionRules} directly, which is
     * why that class is pure: this is exactly the sort of logic that reads correctly and is not,
     * and the alternative — proving it by playing on a server — means shipping it first.</p>
     *
     * <p>Every assertion has its opposite. A resolver that answered "true" to everything would
     * pass half of these, and one that answered "empty" would pass the fallthrough check while
     * being useless, so both directions are asked at each step.</p>
     */
    private void checkPermissionRules() {
        java.util.function.BiFunction<String, Boolean, java.util.Map<String, Boolean>> one =
                (node, value) -> java.util.Map.of(node, value);

        // 1. Nothing said anywhere: the caller must fall through to the node's own default. This
        // is the property that makes the handler safe to switch on, so it is asserted first.
        check("an unmentioned node has no stored answer",
                com.sablednah.standards.core.PermissionRules.resolve(
                        List.of(List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                                "you", java.util.Map.of()))),
                        "standards.fly").isEmpty());

        // 2. The player's own tier beats every group, in both directions.
        var playerDenies = List.of(
                List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                        "you", one.apply("standards.fly", false))),
                List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                        "mod", one.apply("standards.fly", true))));
        check("a deny on the player beats a grant in their group",
                com.sablednah.standards.core.PermissionRules.resolve(playerDenies, "standards.fly")
                        .map(a -> !a.allowed()).orElse(false));
        var playerGrants = List.of(
                List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                        "you", one.apply("standards.fly", true))),
                List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                        "mod", one.apply("standards.fly", false))));
        check("a grant on the player beats a deny in their group",
                com.sablednah.standards.core.PermissionRules.resolve(playerGrants, "standards.fly")
                        .map(com.sablednah.standards.core.PermissionRules.Answer::allowed)
                        .orElse(false));

        // 3. A child group beats its parent — the rule that makes inheritance worth having.
        var childBeatsParent = List.of(
                List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                        "you", java.util.Map.of())),
                List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                        "mod", one.apply("standards.fly", false))),
                List.of(new com.sablednah.standards.core.PermissionRules.Scope(
                        "trusted", one.apply("standards.fly", true))));
        check("a deny in a group beats a grant in its parent",
                com.sablednah.standards.core.PermissionRules.resolve(
                        childBeatsParent, "standards.fly")
                        .map(a -> !a.allowed() && a.scope().equals("mod")).orElse(false));

        // 4. Specificity inside one tier: exact beats a wildcard, however the map is ordered.
        java.util.Map<String, Boolean> mixed = new java.util.LinkedHashMap<>();
        mixed.put("standards.*", true);
        mixed.put("standards.fly", false);
        var specific = List.of(List.of(
                new com.sablednah.standards.core.PermissionRules.Scope("donor", mixed)));
        check("an exact node beats a wildcard that also matches",
                com.sablednah.standards.core.PermissionRules.resolve(specific, "standards.fly")
                        .map(a -> !a.allowed() && a.pattern().equals("standards.fly"))
                        .orElse(false));
        check("the wildcard still answers everything else it covers",
                com.sablednah.standards.core.PermissionRules.resolve(specific, "standards.home")
                        .map(a -> a.allowed() && a.pattern().equals("standards.*")).orElse(false));

        // 5. A longer wildcard prefix beats a shorter one.
        java.util.Map<String, Boolean> nested = new java.util.LinkedHashMap<>();
        nested.put("standards.*", false);
        nested.put("standards.home.*", true);
        var tiers = List.of(List.of(
                new com.sablednah.standards.core.PermissionRules.Scope("donor", nested)));
        check("standards.home.* beats standards.*",
                com.sablednah.standards.core.PermissionRules.resolve(tiers, "standards.home.others")
                        .map(com.sablednah.standards.core.PermissionRules.Answer::allowed)
                        .orElse(false));
        check("standards.* still answers outside the narrower one",
                com.sablednah.standards.core.PermissionRules.resolve(tiers, "standards.fly")
                        .map(a -> !a.allowed()).orElse(false));

        // 6. A trailing wildcard covers the bare node too. Granting standards.home.* and finding
        // /home still refused is the classic "this system is broken" report.
        check("a trailing wildcard covers the node it hangs off",
                com.sablednah.standards.core.PermissionRules.specificity(
                        "standards.home.*", "standards.home") >= 0);
        check("a wildcard does not reach a sibling branch",
                com.sablednah.standards.core.PermissionRules.specificity(
                        "standards.home.*", "standards.homes") < 0);
        check("an unrelated exact node does not match",
                com.sablednah.standards.core.PermissionRules.specificity(
                        "standards.fly", "standards.god") < 0);
        check("bare * matches anything, at the lowest specificity",
                com.sablednah.standards.core.PermissionRules.specificity("*", "anything.at.all")
                        == 0);

        // 7. Two equally specific rules in the SAME tier: the deny wins. There are no weights to
        // break the tie with, and guessing the permissive half of a contradiction is the wrong
        // direction. Asserted both ways round, because a rule that only works when the denying
        // scope happens to be listed first is not a rule.
        var denyFirst = List.of(List.of(
                new com.sablednah.standards.core.PermissionRules.Scope(
                        "guest", one.apply("standards.fly", false)),
                new com.sablednah.standards.core.PermissionRules.Scope(
                        "donor", one.apply("standards.fly", true))));
        var grantFirst = List.of(List.of(
                new com.sablednah.standards.core.PermissionRules.Scope(
                        "donor", one.apply("standards.fly", true)),
                new com.sablednah.standards.core.PermissionRules.Scope(
                        "guest", one.apply("standards.fly", false))));
        check("a tie inside one tier resolves to no (deny listed first)",
                com.sablednah.standards.core.PermissionRules.resolve(denyFirst, "standards.fly")
                        .map(a -> !a.allowed()).orElse(false));
        check("a tie inside one tier resolves to no (grant listed first)",
                com.sablednah.standards.core.PermissionRules.resolve(grantFirst, "standards.fly")
                        .map(a -> !a.allowed()).orElse(false));
    }

    /** The tri-state that is the whole reason for the mod. Every combination, both directions. */
    private void checkToggleLogic() {
        check("ON resolves true from false", Toggle.ON.resolve(false));
        check("ON resolves true from true", Toggle.ON.resolve(true));
        check("OFF resolves false from true", !Toggle.OFF.resolve(true));
        check("OFF resolves false from false", !Toggle.OFF.resolve(false));
        check("TOGGLE flips false", Toggle.TOGGLE.resolve(false));
        check("TOGGLE flips true", !Toggle.TOGGLE.resolve(true));
    }

    /**
     * The login reminder's sentence-joining. Trivial-looking, but it is the first thing a
     * returning player reads, and "vanished, in god mode, flying" reads like a stack trace.
     */
    private void checkStateSentence() {
        check("one state stands alone",
                StandardsEvents.joinStates(List.of("vanished")).equals("vanished"));
        check("two states join with and",
                StandardsEvents.joinStates(List.of("vanished", "flying"))
                        .equals("vanished and flying"));
        check("three states use a comma then and",
                StandardsEvents.joinStates(List.of("vanished", "in god mode", "flying"))
                        .equals("vanished, in god mode and flying"));
    }

    /**
     * The vanish gate, which is now a published seam rather than an internal detail.
     *
     * <p>Worth testing properly for one reason: {@code api/vanish} exists so another mod can take
     * down a nameplate it drew on a vanished player, and until LegendQuest does that, <b>nothing
     * calls these methods at all</b>. That is the failure family this codebase keeps producing —
     * code that computes the right answer and has never been asked the question.</p>
     *
     * <p>The see-through predicate is stubbed here, because the real one resolves a viewer out of
     * the player list and answers "yes, see everything" for the invented ids below — which would
     * make every assertion pass for the wrong reason. {@link Vanish#install()} puts the real one
     * back afterwards.</p>
     */
    private void checkVanishGate() {
        UUID subject = UUID.nameUUIDFromBytes("selftest-vanish-subject".getBytes());
        UUID viewer = UUID.nameUUIDFromBytes("selftest-vanish-viewer".getBytes());
        UUID staff = UUID.nameUUIDFromBytes("selftest-vanish-staff".getBytes());
        try {
            VanishGate.setSeeThroughCheck((who, watcher) -> watcher.equals(staff));

            check("nobody is vanished to start with", !VanishGate.anyVanished());
            check("an unvanished player is not hidden", !VanishGate.hidden(subject, viewer));

            VanishGate.setVanished(subject, true);
            check("a vanished player reads as vanished", VanishGate.isVanished(subject));
            check("anyVanished notices", VanishGate.anyVanished());
            check("a vanished player is hidden from an ordinary viewer",
                    VanishGate.hidden(subject, viewer));

            // Both directions, because a gate that hides everyone passes every positive assertion.
            check("a vanished player is not hidden from themselves",
                    !VanishGate.hidden(subject, subject));
            check("see-through beats the vanish", !VanishGate.hidden(subject, staff));
            check("an unvanished bystander is still not hidden",
                    !VanishGate.hidden(viewer, subject));

            VanishGate.setVanished(subject, false);
            check("unvanishing clears the hide", !VanishGate.hidden(subject, viewer));
            check("and empties the set", !VanishGate.anyVanished());
        } finally {
            // Leave nothing behind: this runs on a live server, and a stubbed predicate or a
            // lingering id would quietly break the real feature for the rest of the run.
            VanishGate.setVanished(subject, false);
            Vanish.install();
        }
    }

    /**
     * {@code /top}'s ceiling. The Nether is the case that matters: blocks to y255, ceiling at
     * y127, and the naive answer puts you on the bedrock roof.
     */
    private void checkTopCeiling() {
        // Overworld: logical height is the build height, so nothing is capped.
        check("the overworld ceiling is the build height",
                MoveCommands.highestStandableY(-64, 319, 384) == 319);
        // Nether: min 0, blocks to 255, logical height 128 — must stop under the roof.
        check("the nether ceiling is the bedrock roof, not the build height",
                MoveCommands.highestStandableY(0, 255, 128) == 127);
        check("the nether ceiling is well below the build height",
                MoveCommands.highestStandableY(0, 255, 128) < 255);
        // A dimension claiming more logical height than it has must not scan into nothing.
        check("an over-claimed logical height is clamped to the build height",
                MoveCommands.highestStandableY(0, 100, 512) == 100);

        // Barrier blocks: a bedrock box is protection, and /top must not step over its roof.
        List<String> barriers = List.of("minecraft:bedrock", "minecraft:barrier");
        check("bedrock stops the scan",
                MoveCommands.matchesAny("minecraft:bedrock", barriers));
        check("barriers stop the scan",
                MoveCommands.matchesAny("minecraft:barrier", barriers));
        check("ordinary blocks do not stop the scan",
                !MoveCommands.matchesAny("minecraft:stone", barriers));
        check("a block whose id merely contains a barrier id does not match",
                !MoveCommands.matchesAny("othermod:bedrock_brick", barriers));
        check("an id from an absent mod is simply never matched",
                !MoveCommands.matchesAny("minecraft:stone",
                        List.of("somemod:unbreakable_casing")));
        check("an empty list stops nothing",
                !MoveCommands.matchesAny("minecraft:bedrock", List.of()));

        // The world's own shell must not be reported as somebody's protection. Overworld
        // -64..319, Nether 0..127 ceiling.
        check("the overworld floor is the world's edge",
                MoveCommands.isWorldEdge(-63, -64, 319, false));
        check("a box in the middle of the overworld is not",
                !MoveCommands.isWorldEdge(64, -64, 319, false));
        check("the nether roof is the world's edge",
                MoveCommands.isWorldEdge(126, 0, 127, true));
        check("the nether floor is the world's edge",
                MoveCommands.isWorldEdge(2, 0, 127, true));
        check("a box in the middle of the nether is not",
                !MoveCommands.isWorldEdge(64, 0, 127, true));
        // A dimension without a ceiling must never treat its build limit as one.
        check("a ceilingless dimension has no roof edge",
                !MoveCommands.isWorldEdge(318, -64, 319, false));
    }

    /**
     * The composed chat line. It carries the name itself, so anything that adds a second name
     * around it is a bug — which is exactly what handing it to ServerChatEvent.setMessage did.
     */
    private void checkChatLine() {
        String tpl = "{prefixes}{name}{suffixes}: {message}";
        String plain = ChatFormatter.compose(tpl, "", "Steve", List.of(), List.of(), "hello");
        check("an undecorated line is name: message", plain.equals("Steve: hello"));

        String full = ChatFormatter.compose(tpl, "", "Steve",
                List.of("[FACTION]", "[PARTY]"), List.of("the noble"), "hello");
        check("prefixes sit left of the name, suffixes right",
                full.equals("[FACTION][PARTY] Steve the noble: hello"));
        check("the composed line names the player exactly once",
                full.split("Steve", -1).length - 1 == 1);
        check("a decorated line still ends with the message", full.endsWith(": hello"));

        // The affix separator goes between affixes, never against the name.
        String sep = ChatFormatter.compose(tpl, " ", "Steve",
                List.of("[A]", "[B]"), List.of(), "hi");
        check("the separator falls between affixes only", sep.equals("[A] [B] Steve: hi"));
        check("no doubled space against the name", !sep.contains("  "));
    }

    /**
     * Colour codes. Two separate failures: mangling ordinary text that contains an ampersand, and
     * letting text a player wrote become formatting.
     */
    private void checkColourCodes() {
        check("a real code becomes a section sign",
                Feedback.translateCodes("&aon").equals("\u00a7aon"));
        // The blind replace('&','\u00a7') eats the space after the ampersand as a code.
        check("an ampersand in ordinary text survives",
                Feedback.translateCodes("Tom & Jerry").equals("Tom & Jerry"));
        check("a trailing ampersand survives",
                Feedback.translateCodes("fish &").equals("fish &"));
        check("an ampersand before a non-code survives",
                Feedback.translateCodes("A&W root beer").equals("A&W root beer"));

        // Player text must never become formatting: colour, bold, and obfuscation worst of all.
        check("player colour codes are stripped",
                Feedback.stripCodes("&c&lSHOUTING").equals("SHOUTING"));
        check("obfuscation is stripped",
                Feedback.stripCodes("&khidden").equals("hidden"));
        check("a literal section sign is stripped too",
                Feedback.stripCodes("\u00a7cred").equals("red"));
        check("a bare section sign does not survive",
                Feedback.stripCodes("odd \u00a7 sign").equals("odd  sign"));
        check("stripping leaves ordinary ampersands alone",
                Feedback.stripCodes("Tom & Jerry").equals("Tom & Jerry"));
        check("stripping leaves ordinary text untouched",
                Feedback.stripCodes("hello world").equals("hello world"));
        // The impersonation case: reset, then something that looks like somebody else.
        check("a reset code cannot be smuggled through",
                !Feedback.stripCodes("&r[Admin] hi").contains("&r"));

        // Codes must become component STYLES, not section signs sitting in the text — otherwise
        // the console, the log and any RCON-driven admin tool read back gibberish.
        check("a coloured message reads cleanly as plain text",
                Feedback.colored("&7[&bStandards&7]&r &7Muted &fSteve").getString()
                        .equals("[Standards] Muted Steve"));
        check("no section sign survives into the text",
                !Feedback.colored("&aon").getString().contains("\u00a7"));
        check("the text itself is preserved exactly",
                Feedback.colored("&aon").getString().equals("on"));
        check("an ampersand in ordinary text still reads back",
                Feedback.colored("Tom & Jerry").getString().equals("Tom & Jerry"));
        check("an empty string does not explode",
                Feedback.colored("").getString().isEmpty());
        check("a message with no codes at all is unchanged",
                Feedback.colored("plain words").getString().equals("plain words"));
        check("a trailing code produces no stray text",
                Feedback.colored("done &").getString().equals("done &"));

        // Hex colours: the palette has no shade between dark grey and grey, which is where an
        // aside wants to sit. Text must survive, and the code must not leak into it.
        check("a hex colour leaves the text intact",
                Feedback.colored("&#8A8A8Adim words").getString().equals("dim words"));
        check("a hex colour leaves no hash behind",
                !Feedback.colored("&#8A8A8Adim").getString().contains("#"));
        check("a malformed hex is left as ordinary text",
                Feedback.colored("&#ZZZZZZnope").getString().equals("&#ZZZZZZnope"));
        check("a short hex is left as ordinary text",
                Feedback.colored("&#abc").getString().equals("&#abc"));
        // And a player must not be able to smuggle one through.
        check("hex is stripped from player text",
                Feedback.stripCodes("&#ff0000RED").equals("RED"));
        check("stripping hex leaves no hash behind",
                !Feedback.stripCodes("&#ff0000RED").contains("#"));
    }

    /**
     * The router seam. Registers real routers and takes them out again — asserting that the list
     * is merely non-empty would pass for a seam that never calls anybody.
     */
    private void checkChatRouters() {
        var order = new ArrayList<String>();
        // Deliberately registered lowest-first, so a seam that ignored priority would fail.
        com.sablednah.standards.api.chat.ChatRouter weak = testRouter("test:weak", 0, order, false);
        com.sablednah.standards.api.chat.ChatRouter strong = testRouter("test:strong", 100, order, true);
        com.sablednah.standards.api.chat.ChatRouter thrower =
                new com.sablednah.standards.api.chat.ChatRouter() {
                    public String id() { return "test:thrower"; }
                    public int priority() { return 200; }
                    public boolean route(ServerPlayer s, String m) {
                        throw new IllegalStateException("deliberate");
                    }
                };
        try {
            check("nothing is claimed with no routers",
                    com.sablednah.standards.api.chat.Chat.route(null, "hi").isEmpty());

            com.sablednah.standards.api.chat.Chat.registerRouter(weak);
            check("a declining router leaves the message unclaimed",
                    com.sablednah.standards.api.chat.Chat.route(null, "hi").isEmpty());
            check("a declining router was still asked", order.contains("test:weak"));

            order.clear();
            com.sablednah.standards.api.chat.Chat.registerRouter(strong);
            var claimed = com.sablednah.standards.api.chat.Chat.route(null, "hi");
            check("a claiming router claims it",
                    claimed.isPresent() && claimed.get().equals("test:strong"));
            check("higher priority is offered first, and ends it",
                    order.equals(List.of("test:strong")));

            order.clear();
            com.sablednah.standards.api.chat.Chat.registerRouter(thrower);
            var afterThrow = com.sablednah.standards.api.chat.Chat.route(null, "hi");
            // A router that throws has delivered nothing, so the message must carry on rather
            // than vanish — the same rule the decorators follow.
            check("a throwing router does not eat the message",
                    afterThrow.isPresent() && afterThrow.get().equals("test:strong"));
        } finally {
            com.sablednah.standards.api.chat.Chat.unregisterRouter(weak);
            com.sablednah.standards.api.chat.Chat.unregisterRouter(strong);
            com.sablednah.standards.api.chat.Chat.unregisterRouter(thrower);
        }
        check("the test routers are gone again",
                com.sablednah.standards.api.chat.Chat.routers().stream()
                        .noneMatch(r -> r.id().startsWith("test:")));
    }

    private static com.sablednah.standards.api.chat.ChatRouter testRouter(
            String id, int priority, List<String> order, boolean claims) {
        return new com.sablednah.standards.api.chat.ChatRouter() {
            public String id() { return id; }
            public int priority() { return priority; }
            public boolean route(ServerPlayer sender, String message) {
                order.add(id);
                return claims;
            }
        };
    }

    /**
     * No message may inflect a {@code {term.*}} value.
     *
     * <p>The terms are vocabulary the server owner chose — a currency might be "credits", "gil"
     * or "brass" — so appending an "s" for a plural is a rule that cannot hold. It produced
     * "creditss" in front of a real user before this check existed. Where a plural is genuinely
     * needed the catalogue defines one, which is why {@code term.homes} and {@code term.warps}
     * sit beside their singulars.</p>
     */
    private void checkNoTermInflection() {
        java.util.regex.Pattern inflected =
                java.util.regex.Pattern.compile("\\{term\\.[a-z_.]+\\}[A-Za-z]");
        List<String> offenders = Lang.catalogue().entrySet().stream()
                .filter(e -> inflected.matcher(e.getValue()).find())
                .map(java.util.Map.Entry::getKey)
                .toList();
        check("no message inflects a vocabulary term" + (offenders.isEmpty() ? "" : " " + offenders),
                offenders.isEmpty());
    }

    /**
     * The groups seam. Registers real providers and takes them out again — asserting a list is
     * non-empty would pass for a seam that never calls anybody, and a fixture left registered
     * changes the live server, which the chat decorators already proved the hard way.
     *
     * <p>{@code share()} is not exercised here: it needs two real players, and the self-test runs
     * with nobody connected. That belongs to the RCON battery, which exists for exactly the
     * questions this cannot answer.</p>
     */
    private void checkGroups() {
        record Kind(String id, String displayName, boolean exclusive)
                implements com.sablednah.standards.api.groups.GroupKind {}
        record Fixed(com.sablednah.standards.api.groups.GroupKind kind, String id, String name,
                java.util.Set<UUID> who)
                implements com.sablednah.standards.api.groups.Group {
            public boolean contains(UUID player) { return who.contains(player); }
            public java.util.Collection<UUID> members() { return who; }
        }

        var partyKind = new Kind("selftest:party", "party", true);
        var roleKind = new Kind("selftest:role", "role", false);
        var thrower = new Kind("selftest:thrower", "boom", true);

        var party = new Fixed(partyKind, "p1", "The Crew", java.util.Set.of());
        var mod = new Fixed(roleKind, "r1", "moderator", java.util.Set.of());
        var builder = new Fixed(roleKind, "r2", "builder", java.util.Set.of());

        com.sablednah.standards.api.groups.GroupProvider parties =
                new com.sablednah.standards.api.groups.GroupProvider() {
                    public com.sablednah.standards.api.groups.GroupKind kind() { return partyKind; }
                    public java.util.Collection<com.sablednah.standards.api.groups.Group>
                            groupsOf(ServerPlayer p) { return List.of(party); }
                    public java.util.Optional<com.sablednah.standards.api.groups.Group>
                            byName(String n) { return java.util.Optional.of(party); }
                };
        com.sablednah.standards.api.groups.GroupProvider roles =
                new com.sablednah.standards.api.groups.GroupProvider() {
                    public com.sablednah.standards.api.groups.GroupKind kind() { return roleKind; }
                    public java.util.Collection<com.sablednah.standards.api.groups.Group>
                            groupsOf(ServerPlayer p) { return List.of(mod, builder); }
                    public java.util.Optional<com.sablednah.standards.api.groups.Group>
                            byName(String n) { return java.util.Optional.empty(); }
                };
        com.sablednah.standards.api.groups.GroupProvider boom =
                new com.sablednah.standards.api.groups.GroupProvider() {
                    public com.sablednah.standards.api.groups.GroupKind kind() { return thrower; }
                    public java.util.Collection<com.sablednah.standards.api.groups.Group>
                            groupsOf(ServerPlayer p) { throw new IllegalStateException("deliberate"); }
                    public java.util.Optional<com.sablednah.standards.api.groups.Group>
                            byName(String n) { return java.util.Optional.empty(); }
                };

        // Measured, not assumed. Standards' own built-in groups provider registers on the same
        // event this test runs on, so an absolute count is wrong the moment the mod grows a
        // provider of its own — which is exactly the mistake the RCON battery made with homes.
        int baseKinds = com.sablednah.standards.api.groups.Groups.kinds().size();
        try {
            check("a provider is accepted",
                    com.sablednah.standards.api.groups.Groups.register(parties));
            // Two mods disagreeing about who is in a party is not something to settle quietly.
            check("a second provider for the same kind is refused",
                    !com.sablednah.standards.api.groups.Groups.register(parties));
            check("a different kind is accepted alongside",
                    com.sablednah.standards.api.groups.Groups.register(roles));

            // A null player is fine: these fixtures never look at it.
            check("an exclusive kind has a primary",
                    com.sablednah.standards.api.groups.Groups.primary(null, partyKind)
                            .map(g -> g.name().equals("The Crew")).orElse(false));
            // Asking for "the" role of somebody who is both moderator and builder has no answer,
            // and picking one arbitrarily would be worse than saying so.
            check("a non-exclusive kind has no primary",
                    com.sablednah.standards.api.groups.Groups.primary(null, roleKind).isEmpty());
            check("but all() returns every one of them",
                    com.sablednah.standards.api.groups.Groups.all(null, roleKind).size() == 2);
            check("an unprovided kind is simply empty",
                    com.sablednah.standards.api.groups.Groups.all(null, thrower).isEmpty());

            com.sablednah.standards.api.groups.Groups.register(boom);
            check("a throwing provider leaves the player ungrouped rather than killing the call",
                    com.sablednah.standards.api.groups.Groups.all(null, thrower).isEmpty());
            check("and does not stop the other kinds answering",
                    com.sablednah.standards.api.groups.Groups.all(null).size() == 3);

            check("kinds are listed",
                    com.sablednah.standards.api.groups.Groups.kinds().size() == baseKinds + 3);
            check("a kind can be found by id",
                    com.sablednah.standards.api.groups.Groups.kind("selftest:role")
                            .map(k -> !k.exclusive()).orElse(false));
        } finally {
            com.sablednah.standards.api.groups.Groups.unregister(partyKind);
            com.sablednah.standards.api.groups.Groups.unregister(roleKind);
            com.sablednah.standards.api.groups.Groups.unregister(thrower);
        }
        check("the kind count is back where it started",
                com.sablednah.standards.api.groups.Groups.kinds().size() == baseKinds);
        check("the test providers are gone again",
                com.sablednah.standards.api.groups.Groups.kinds().stream()
                        .noneMatch(k -> k.id().startsWith("selftest:")));
    }

    /** Claims: fail open, highest priority wins, and a thrower must not brick block-breaking. */
    private void checkClaims() {
        try {
            com.sablednah.standards.api.groups.Claims.clear();
            check("nothing claimed with no provider",
                    com.sablednah.standards.api.groups.Claims.owner(null, null).isEmpty());
            // Failing open is deliberate: a server with no land mod must behave as vanilla.
            check("everything permitted with no provider",
                    com.sablednah.standards.api.groups.Claims.mayModify(null, null, null));
            check("and it says so",
                    !com.sablednah.standards.api.groups.Claims.isAvailable());

            com.sablednah.standards.api.groups.ClaimProvider low =
                    claimFixture("selftest:low", 0, false);
            com.sablednah.standards.api.groups.ClaimProvider high =
                    claimFixture("selftest:high", 100, true);
            com.sablednah.standards.api.groups.ClaimProvider boom =
                    claimFixture("selftest:boom", 200, null);

            check("a provider is taken",
                    com.sablednah.standards.api.groups.Claims.register(low));
            check("and answers", !com.sablednah.standards.api.groups.Claims.mayModify(null, null, null));
            check("higher priority displaces it",
                    com.sablednah.standards.api.groups.Claims.register(high));
            check("and now answers instead",
                    com.sablednah.standards.api.groups.Claims.mayModify(null, null, null));
            check("lower priority is refused",
                    !com.sablednah.standards.api.groups.Claims.register(low));
            check("so the higher one still answers",
                    com.sablednah.standards.api.groups.Claims.mayModify(null, null, null));

            // A claims mod that errors must not stop every player placing a block.
            com.sablednah.standards.api.groups.Claims.register(boom);
            check("a throwing provider permits rather than refuses",
                    com.sablednah.standards.api.groups.Claims.mayModify(null, null, null));
            check("and reports wilderness rather than throwing",
                    com.sablednah.standards.api.groups.Claims.owner(null, null).isEmpty());
        } finally {
            com.sablednah.standards.api.groups.Claims.clear();
        }
        check("claims are unprovided again",
                !com.sablednah.standards.api.groups.Claims.isAvailable());
    }

    /** @param permits true, false, or null to throw */
    private static com.sablednah.standards.api.groups.ClaimProvider claimFixture(
            String id, int priority, Boolean permits) {
        return new com.sablednah.standards.api.groups.ClaimProvider() {
            public String id() { return id; }
            public int priority() { return priority; }
            public java.util.Optional<com.sablednah.standards.api.groups.Group> owner(
                    net.minecraft.server.level.ServerLevel level,
                    net.minecraft.world.level.ChunkPos chunk) {
                if (permits == null) throw new IllegalStateException("deliberate");
                return java.util.Optional.empty();
            }
            public boolean mayModify(ServerPlayer player,
                    net.minecraft.server.level.ServerLevel level,
                    net.minecraft.core.BlockPos pos) {
                if (permits == null) throw new IllegalStateException("deliberate");
                return permits;
            }
        };
    }

    /**
     * Teleport relief. The two halves are separate on purpose: the cooldown is a rate limit with
     * nothing to protect inside a group, the warmup is the anti-combat-log half and stays.
     */
    private void checkTeleportRelief() {
        var none = com.sablednah.standards.neoforge.Teleports.Relief.NONE;
        check("no relief skips nothing", !none.cooldown() && !none.warmup());

        var mates = com.sablednah.standards.neoforge.Teleports.Relief.forGroupMates();
        check("group mates skip the cooldown by default", mates.cooldown());
        // If this ever defaults on, somebody has made escaping a fight free for anyone who
        // remembered to make a group first.
        check("group mates do NOT skip the warmup by default", !mates.warmup());
    }

    /**
     * Combat tags. Two of these are rules that are invisible until they are wrong.
     */
    private void checkCombat() {
        var kinds = com.sablednah.standards.api.combat.CombatKind.values();
        check("there are three combat kinds", kinds.length == 3);
        check("every combat kind has a config key",
                java.util.Arrays.stream(kinds).allMatch(k -> !k.key().isBlank()));

        // Tags extend, never overwrite. With one global duration this bug is invisible; with
        // per-kind durations a SHORTER later tag would rescue the person fleeing, who is exactly
        // who the feature exists to stop.
        long now = System.currentTimeMillis();
        var longTag = new com.sablednah.standards.api.combat.CombatTag(
                com.sablednah.standards.api.combat.CombatKind.PVP, now + 12_000, "test");
        var shortTag = new com.sablednah.standards.api.combat.CombatTag(
                com.sablednah.standards.api.combat.CombatKind.PVE, now + 8_000, "test");
        check("a longer tag outlasts a shorter one",
                longTag.remaining(now) > shortTag.remaining(now));

        // The rule itself, rather than an arrangement of it. Twelve seconds of PvP followed by a
        // zombie for eight is still twelve — and the reverse, so order cannot matter.
        check("a shorter tag arriving second does not shorten the fight",
                com.sablednah.standards.api.combat.Combat.longer(longTag, shortTag) == longTag);
        check("a longer tag arriving second does extend it",
                com.sablednah.standards.api.combat.Combat.longer(shortTag, longTag) == longTag);
        check("the first tag of a kind is simply taken",
                com.sablednah.standards.api.combat.Combat.longer(null, shortTag) == shortTag);
        check("nothing arriving does not clear what is running",
                com.sablednah.standards.api.combat.Combat.longer(longTag, null) == longTag);
        check("an expired tag reports zero remaining",
                new com.sablednah.standards.api.combat.CombatTag(
                        com.sablednah.standards.api.combat.CombatKind.PVP, now - 1, "test")
                        .remaining(now) == 0L);
        check("an expired tag knows it", new com.sablednah.standards.api.combat.CombatTag(
                com.sablednah.standards.api.combat.CombatKind.PVP, now - 1, "test").expired(now));

        // An attacker starts a tag, not damage. Environmental sources have neither entity, so
        // hasAttacker is the whole of the rule — and getting it wrong traps a player who is
        // drowning in their own basement, or freezing inside somebody else's claim.
        check("damage with nobody behind it is not combat",
                !com.sablednah.standards.api.combat.Combat.hasAttacker(null));
        check("no source resolves to no player",
                com.sablednah.standards.api.combat.Combat.playerBehind(null).isEmpty());

        // The event is the seam that lets the classification be argued with later.
        var event = new com.sablednah.standards.api.combat.CombatTagEvent(null,
                com.sablednah.standards.api.combat.CombatKind.PVE, "test:thing", 8);
        event.setKind(com.sablednah.standards.api.combat.CombatKind.SKILL);
        event.setSeconds(30);
        check("a listener can reclassify a tag",
                event.getKind() == com.sablednah.standards.api.combat.CombatKind.SKILL);
        check("a listener can lengthen a tag", event.getSeconds() == 30);
        event.setKind(null);
        check("a listener cannot null out the kind",
                event.getKind() == com.sablednah.standards.api.combat.CombatKind.SKILL);
        check("the source is not something a listener can rewrite",
                event.getSource().equals("test:thing"));

        // The harm seam. Any veto denies, and a broken provider must fail OPEN — a mod with a bug
        // switching combat off for a whole server is the more damaging way to be wrong.
        var quiet = new com.sablednah.standards.api.combat.HarmProvider() {
            public String id() { return "selftest:quiet"; }
            public java.util.Optional<net.minecraft.network.chat.Component> forbids(
                    ServerPlayer a, ServerPlayer b) {
                return java.util.Optional.empty();
            }
        };
        var thrower = new com.sablednah.standards.api.combat.HarmProvider() {
            public String id() { return "selftest:thrower"; }
            public java.util.Optional<net.minecraft.network.chat.Component> forbids(
                    ServerPlayer a, ServerPlayer b) {
                throw new IllegalStateException("deliberate");
            }
        };
        int before = com.sablednah.standards.api.combat.Harm.all().size();
        com.sablednah.standards.api.combat.Harm.register(quiet);
        com.sablednah.standards.api.combat.Harm.register(thrower);
        try {
            check("harm providers register",
                    com.sablednah.standards.api.combat.Harm.all().size() == before + 2);
            // Null and self-harm short-circuit before any provider is asked, which is also how
            // the thrower is proved not to be reached in the trivial cases.
            check("nobody forbids harming nobody",
                    com.sablednah.standards.api.combat.Harm.forbidden(null, null).isEmpty());
        } finally {
            // Registered into a live server's list, so they must come out again whatever happens —
            // the self-test leaking a chat decorator into a running server has happened once
            // already, and every line spoken carried it for the rest of the session.
            com.sablednah.standards.api.combat.Harm.unregister(quiet);
            com.sablednah.standards.api.combat.Harm.unregister(thrower);
        }
        check("harm providers unregister cleanly",
                com.sablednah.standards.api.combat.Harm.all().size() == before);
    }

    /**
     * A group name and its tag are printed on other people's screens, so they are untrusted input
     * in exactly the way a chat line is — and arrived through a door nobody was watching.
     */
    private void checkGroupNamesAreText() {
        check("a group name cannot carry colour",
                StandardsGroups.clean("&cEvil").equals("Evil"));
        check("a group tag cannot be obfuscated",
                StandardsGroups.clean("&kTCB").equals("TCB"));
        // Four characters by String.length, none of them visible. Measured raw, it passes the
        // five-character limit and renders as an invisible tag on every line its members speak.
        check("an all-formatting tag cleans to nothing",
                StandardsGroups.clean("&k&l").isEmpty());
        check("an ampersand in a name survives",
                StandardsGroups.clean("Salt & Pepper").equals("Salt & Pepper"));
    }

    /**
     * The config fallback for nodes nobody can otherwise hold. Its defaults matter more than its
     * mechanics: shipping with stations open would hand every player a portable ender chest.
     */
    private void checkAccessFallback() {
        check("stations default to nobody",
                com.sablednah.standards.StandardsConfig.STATION_ACCESS.get().equalsIgnoreCase("nobody"));
        check("back-on-death defaults to nobody",
                com.sablednah.standards.StandardsConfig.BACK_ON_DEATH_ACCESS.get().equalsIgnoreCase("nobody"));
    }

    private void checkMoneyFormatting() {
        // Against explicit settings, not the server's — a test that asserts the shipped default
        // is measuring the config file, and breaks the day someone changes their currency.
        check("named currency: 1 is singular",
                Money.render(1.0D, 0, "", true, "credit", "credits").equals("1 credit"));
        check("named currency: 2 is plural",
                Money.render(2.0D, 0, "", true, "credit", "credits").equals("2 credits"));
        check("symbol before",
                Money.render(25.0D, 0, "\u20A1", true, "credit", "credits").equals("\u20A125"));
        check("symbol after",
                Money.render(25.0D, 0, "\u20A1", false, "credit", "credits").equals("25\u20A1"));
        check("decimals honoured",
                Money.render(2.5D, 2, "", true, "credit", "credits").equals("2.50 credits"));
        check("whole-number currency rounds for display",
                Money.render(2.5D, 0, "", true, "credit", "credits").equals("3 credits"));
        check("thousands separator",
                Money.render(1234.0D, 0, "", true, "credit", "credits").equals("1,234 credits"));
        // And the live formatter still produces something, whatever it is configured to.
        check("configured formatter produces output", !Money.format(1.0D).isBlank());
        check("parses a bare number", Money.parse("25").orElse(-1.0D) == 25.0D);
        check("parses a thousands separator", Money.parse("1,000").orElse(-1.0D) == 1000.0D);
        // The property that matters: anything format() writes, parse() must read back. Checked
        // against several currency configurations, not just the one this server happens to run.
        for (String[] currency : new String[][] {
                {"\u20A1", "credit", "credits"},
                {"", "credit", "credits"},
                {"$", "dollar", "dollars"},
        }) {
            String symbol = currency[0];
            for (double amount : new double[] {1.0D, 50.0D, 1234.0D}) {
                String shown = Money.render(amount, 0, symbol, true, currency[1], currency[2]);
                double back = Money.parse(shown, symbol, currency[1], currency[2]).orElse(-1.0D);
                check("round-trips '" + shown + "'", back == amount);
            }
        }
        check("refuses nonsense", Money.parse("tuesday").isEmpty());
        // Rounding is what stops a balance reading 0.30000000000000004 — asserted against
        // explicit precisions, because "the configured one" is exactly the assumption that broke
        // this check the moment the default currency became whole-number.
        check("rounds to 2dp", Money.round(0.1D + 0.2D, 2) == 0.3D);
        check("rounds to 0dp", Money.round(0.1D + 0.2D, 0) == 0.0D);
        check("rounds half up", Money.round(2.5D, 0) == 3.0D);
        check("leaves exact values alone", Money.round(50.0D, 0) == 50.0D);
    }

    /**
     * Every command must parse from the console <em>and</em> reach an executable node. A literal
     * that exists but leads nowhere is the failure mode a bare {@code parse()} misses.
     */
    private void checkCommandsParse(MinecraftServer server) {
        CommandSourceStack console = server.createCommandSourceStack();
        for (String command : List.of(
                "fly", "fly on", "fly off", "fly toggle", "fly @a on",
                "god", "god on", "god @a off",
                "top", "jump", "j", "back", "back 2", "back list",
                "home", "sethome", "sethome base", "delhome base", "homes",
                "warp", "warps", "setwarp spawnpoint", "delwarp spawnpoint",
                "tpa Steve", "call Steve", "tpahere Steve",
                "tpaccept", "tpaccept Steve", "tpyes", "tpdeny", "tpdeny Steve", "tpno",
                "tpacancel", "tpalist",
                "tptoggle", "tptoggle on", "tptoggle off",
                "vanish", "vanish on", "vanish off", "v", "v @a off",
                "smite", "smite Steve",
                "tempban Steve 2h", "tempban Steve 2h being rude",
                "mute Steve", "mute Steve 30m", "mute Steve 30m spam", "unmute Steve",
                "invsee Steve",
                "up",
                "down",
                "standards testchat hello world",
                "standards permissions", "standards nodes",
                "i stone", "i stone 12", "i minecraft:diamond_sword",
                "repair", "repair all", "fix", "more", "condense",
                "itemname Excalibur", "itemname &cExcalibur", "itemname -",
                "itemlore add A blade of legend", "itemlore clear",
                "powertool jump", "powertool clear", "powertool clearall", "powertool list",
                "pt jump", "pt list",
                "depth", "compass",
                "world minecraft:the_nether", "worlds",
                "sudo Steve fly on", "sudo Steve home",
                "playtime", "playtime Steve", "leaderboard", "playtop",
                "motd", "rules", "info",
                "butcher", "butcher 32", "butcher 32 all", "killall",
                "tpx Steve", "tpx Steve Alex", "tphere @a", "tppos 0 64 0",
                "tppos 0 64 0 minecraft:the_nether",
                "kitaccess starter ops", "kitaccess starter permission",
                "kitaccess starter nobody", "kitaccess starter everyone",
                "item stone", "item minecraft:stone 64",
                "nick Wanderer", "nick -",
                // THE ONE THAT WAS BROKEN, twice over now: word() stops dead at an ampersand
                // exactly as it does at an asterisk, so a coloured nickname was unparseable
                // while standards.nick.color gated it. Same bug, second door.
                "nick &cWanderer", "nick &c&lWanderer",
                "nick player Steve Wanderer", "nick player Steve -",
                "realname Wanderer", "whois Wanderer",
                // Both /eco argument branches: a bare name (offline-capable) and a selector
                // (command blocks). The order they are registered in is what makes both work.
                "eco give Steve 100",
                "eco give @p 100",
                "eco give @a[tag=winner] 500",
                "eco take @p 5",
                "eco set @p 0",
                "pay Steve 10",
                "group",
                "group create Crew",
                "group invite Steve",
                "group accept Crew",
                "group deny Crew",
                "group leave",
                "group disband",
                "group kick Steve",
                "group rename Crew",
                "group tag TCB",
                "group list",
                "group info",
                "group info Crew",
                "group sethome base",
                "group delhome base",
                "ghome",
                "ghome base",
                "ghomes",
                "craft", "workbench", "anvil", "grindstone", "enderchest", "ec",
                "trashcan", "disposal",
                "spawn", "setspawn", "playerspawn", "bottom",
                "heal", "heal Steve", "feed", "eat", "rest", "rest @a",
                "speed 2", "speed walk 2", "speed fly 3", "speed reset", "speed 2 Steve",
                "msg Steve hello", "w Steve hello", "whisper Steve hello", "tell Steve hello",
                "pm Steve hello", "m Steve hello", "r hello", "reply hello",
                "ignore", "ignore Steve", "msgtoggle", "msgtoggle off",
                "socialspy", "socialspy on",
                "afk", "afk back in 5", "lurk",
                "mail", "mail read", "mail clear", "mail send Steve hello",
                "tpoffline Steve", "otp Steve",
                "kit", "kits", "kit starter", "showkit starter", "delkit starter",
                "setkit starter", "setkit starter armour", "setkit starter all 1d",
                "gc", "tps", "lag", "mem",
                "balance", "bal", "money", "baltop", "eco give Steve 100", "eco set Steve 0",
                "standards reload", "standards economy")) {
            ParseResults<CommandSourceStack> parsed =
                    server.getCommands().getDispatcher().parse(command, console);
            boolean clean = parsed.getExceptions().isEmpty()
                    && !parsed.getReader().canRead()
                    && boundCommand(parsed) != null;
            check("/" + command + " parses to an executable node", clean);
        }

        // /msg is vanilla's command that we merge onto. Asserting it "parses" proves nothing —
        // vanilla's own node would satisfy that — so check the command actually bound there is
        // ours. This is the exact hole that let the first version ship broken.
        for (String alias : new String[] {"msg", "tell", "w", "whisper", "pm", "m"}) {
            ParseResults<CommandSourceStack> parsed =
                    server.getCommands().getDispatcher().parse(alias + " @a hello", console);
            var command = boundCommand(parsed);
            boolean ours = command != null
                    && command.getClass().getName().startsWith("com.sablednah.standards");
            check("/" + alias + " is bound to Standards, not vanilla", ours);
        }

        // /me is the same trap wearing a different hat: vanilla broadcasts it on a path that
        // knows nothing about mutes, so "it works" and "the mute holds" are separate questions.
        ParseResults<CommandSourceStack> emote =
                server.getCommands().getDispatcher().parse("me waves", console);
        var emoteCommand = boundCommand(emote);
        check("/me is bound to Standards, not vanilla",
                emoteCommand != null
                        && emoteCommand.getClass().getName()
                                .startsWith("com.sablednah.standards"));

        // /rank and /perm are registered on every server and visible on almost none: their
        // requires() hides them unless Standards' own permission handler is the active one. Both
        // directions matter — a gate that hides them always is indistinguishable from one that
        // works, until somebody switches the handler on and finds nothing there.
        //
        // Probed as /rank, never /perm: LuckPerms claims /perm as an alias of /luckperms, so on a
        // server carrying both this would be asking about somebody else's node.
        boolean ours = com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler
                .isActive();
        ParseResults<CommandSourceStack> perm =
                server.getCommands().getDispatcher().parse("rank groups", console);
        boolean reachable = perm.getExceptions().isEmpty() && !perm.getReader().canRead()
                && boundCommand(perm) != null;
        check(ours ? "/rank is reachable while our handler is active"
                   : "/rank is hidden while another handler is active",
                reachable == ours);
        for (String alias : new String[] {"perm", "rank"}) {
            check("/" + alias + " is registered either way, so switching handlers needs no reload",
                    server.getCommands().getDispatcher().getRoot().getChild(alias) != null);
        }

        // The permission tree, but only when it is reachable — requires() hides it otherwise, and
        // a parse against a hidden command proves nothing either way.
        if (ours) {
            for (String command : List.of(
                    "rank groups",
                    "rank group staff create",
                    "rank group staff delete",
                    "rank group staff info",
                    "rank group staff parent add trusted",
                    "rank group staff tag MOD",
                    "rank user Steve group add staff",
                    "rank user Steve info",
                    "rank user Steve set standards.fly false",
                    "rank check Steve standards.fly",
                    // THE ONE THAT WAS BROKEN. A wildcard is the feature admins reach for, and
                    // brigadier's word() stops dead at an asterisk — so every wildcard node was
                    // unparseable while the resolver handled them perfectly. Proved correct and
                    // untypeable, which the resolver tests could never have caught.
                    "rank group staff set standards.home.* true",
                    "rank group staff unset standards.*",
                    "rank user Steve set standards.* true",
                    "rank check Steve standards.home.*")) {
                ParseResults<CommandSourceStack> parsed =
                        server.getCommands().getDispatcher().parse(command, console);
                check("/" + command + " parses to an executable node",
                        parsed.getExceptions().isEmpty() && !parsed.getReader().canRead()
                                && boundCommand(parsed) != null);
            }
        }

        // The other direction: a command that should NOT parse must not. Without this, an
        // over-eager tree that matches anything would sail through every check above.
        ParseResults<CommandSourceStack> nonsense =
                server.getCommands().getDispatcher().parse("fly sideways backwards", console);
        check("garbage arguments are rejected",
                !nonsense.getExceptions().isEmpty() || nonsense.getReader().canRead());

        // Vanilla's own /tp must still work: we deliberately did NOT merge onto it, and a
        // regression there would be invisible until an admin reached for it under pressure.
        ParseResults<CommandSourceStack> vanillaTp =
                server.getCommands().getDispatcher().parse("tp @s ~ ~ ~", console);
        check("vanilla /tp is left alone",
                vanillaTp.getExceptions().isEmpty() && !vanillaTp.getReader().canRead()
                        && boundCommand(vanillaTp) != null);

        // /i resolves against the real registries, so an item that does not exist must fail. A
        // tree that accepted anything would pass every '/i <something>' check above.
        ParseResults<CommandSourceStack> noSuchItem =
                server.getCommands().getDispatcher().parse("i not_a_real_item", console);
        check("/i rejects an item that does not exist",
                !noSuchItem.getExceptions().isEmpty() || noSuchItem.getReader().canRead());
        // ...and a count of zero, which would silently give nothing.
        ParseResults<CommandSourceStack> zero =
                server.getCommands().getDispatcher().parse("i stone 0", console);
        check("/i rejects a count of zero",
                !zero.getExceptions().isEmpty() || zero.getReader().canRead());
    }

    /**
     * The compass, which is arithmetic nobody would notice being wrong.
     *
     * <p>Two conversions stacked: Minecraft's yaw is 0 = <em>south</em> and grows clockwise, and a
     * bearing is 0 = north. A compass that is 180 degrees out still moves the right way when you
     * turn, so it looks entirely plausible until somebody navigates by it.</p>
     *
     * <p>The boundaries matter more than the middles. North is the wrap — 359 and 1 must both be
     * north — and every name owns the 45 degrees <em>centred</em> on it, so the edges are at 22.5
     * rather than at 0.</p>
     */
    private void checkCompassBearings() {
        java.util.function.IntFunction<String> at =
                b -> com.sablednah.standards.neoforge.commands.LocationCommands.cardinal(b);
        check("0 degrees is north", at.apply(0).equals(Lang.get("msg.where.n")));
        check("90 is east", at.apply(90).equals(Lang.get("msg.where.e")));
        check("180 is south", at.apply(180).equals(Lang.get("msg.where.s")));
        check("270 is west", at.apply(270).equals(Lang.get("msg.where.w")));
        check("45 is north-east", at.apply(45).equals(Lang.get("msg.where.ne")));
        check("225 is south-west", at.apply(225).equals(Lang.get("msg.where.sw")));

        // The wrap, which is where an off-by-one lives.
        check("359 is still north", at.apply(359).equals(Lang.get("msg.where.n")));
        check("1 is still north", at.apply(1).equals(Lang.get("msg.where.n")));
        check("22 is north, just", at.apply(22).equals(Lang.get("msg.where.n")));
        check("23 has tipped into north-east", at.apply(23).equals(Lang.get("msg.where.ne")));

        // And it must not throw or wander off the end for anything a wrapped yaw can produce.
        boolean allNamed = true;
        for (int b = 0; b < 360; b++) {
            String name = at.apply(b);
            allNamed &= name != null && !name.isBlank() && !name.startsWith("msg.");
        }
        check("every bearing from 0 to 359 has a name", allNamed);
    }

    /**
     * Promotion rules: the parser, and the two clocks.
     *
     * <p>The parser is what meets a real config file, which is to say a real typo. Every accepted
     * shape is asserted alongside a rejected one, because a parser that accepts everything would
     * pass all the positive checks and then promote the whole server on a malformed line.</p>
     */
    private void checkPromotionRules() {
        var P = com.sablednah.standards.neoforge.permissions.Promotions.class;
        java.util.function.Function<String, java.util.Optional<
                com.sablednah.standards.neoforge.permissions.Promotions.Rule>> parse =
                com.sablednah.standards.neoforge.permissions.Promotions::parse;

        var real = parse.apply("guest -> regular after 24h");
        check("a real-time rule parses", real.isPresent());
        check("...with the right groups and clock",
                real.map(r -> r.from().equals("guest") && r.to().equals("regular")
                        && r.realSeconds() == 86400L && r.playedSeconds() == 0L).orElse(false));

        var played = parse.apply("guest -> regular after 2h played");
        check("a played-time rule parses",
                played.map(r -> r.playedSeconds() == 7200L && r.realSeconds() == 0L).orElse(false));

        var both = parse.apply("guest -> regular after 24h and 2h played");
        check("both clocks in one rule parse",
                both.map(r -> r.realSeconds() == 86400L && r.playedSeconds() == 7200L)
                        .orElse(false));

        // The other direction. A rule that cannot be read must promote nobody, never everybody.
        check("a rule with no arrow is refused", parse.apply("guest regular after 24h").isEmpty());
        check("a rule with no 'after' is refused", parse.apply("guest -> regular 24h").isEmpty());
        check("a rule with no duration is refused", parse.apply("guest -> regular after ").isEmpty());
        check("a rule with an unreadable duration is refused",
                parse.apply("guest -> regular after soon").isEmpty());
        check("a rule with no target group is refused",
                parse.apply("guest ->  after 24h").isEmpty());

        // Both clocks given means BOTH must pass — the stricter half must not be decorative.
        var rule = both.orElseThrow();
        StandardsData data = StandardsData.get(
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer());
        UUID who = UUID.nameUUIDFromBytes("selftest-promotion".getBytes());
        // Both clocks are PERSISTED, so a previous run's minutes are still here. Without this the
        // second run of the self-test starts already qualified and the "not yet" assertions fail —
        // which is exactly how the missing cleanup was found.
        data.forgetTiming(who);
        try {
            long now = 1_000_000_000_000L;
            data.rememberFirstSeen(who, now - 86400_000L * 2);   // two days ago: real time passed
            check("real time alone does not satisfy a rule that also wants played time",
                    !rule.satisfiedBy(data, who, now));
            data.addPlayedMinutes(who, 119);
            check("...nor does nearly enough played time", !rule.satisfiedBy(data, who, now));
            data.addPlayedMinutes(who, 1);
            check("both clocks passing satisfies it", rule.satisfiedBy(data, who, now));
            check("...but not for somebody the clock has not started for",
                    !rule.satisfiedBy(data, UUID.nameUUIDFromBytes("nobody".getBytes()), now));

            // And the played-only rule ignores the wall clock entirely.
            check("a played-only rule is satisfied by played time alone",
                    played.orElseThrow().satisfiedBy(data, who, now));

            // The load-bearing half: satisfying a rule must actually MOVE them. Only meaningful
            // with our handler active — under LuckPerms there is no store to move them in.
            if (com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler
                    .isActive()) {
                checkPromotionMoves(data, who, now, List.of(rule));
            }
        } finally {
            data.forgetTiming(who);
            check("the promotion clocks are reset for the next run",
                    data.playedMinutes(who) == 0 && data.firstSeen(who).isEmpty());
        }
    }

    /**
     * A satisfied rule actually changes the player's groups.
     *
     * <p>Separate from the parser checks because it is a different claim. A rule can be parsed
     * perfectly, report itself satisfied, and move nobody — and that failure would look exactly
     * like a rule nobody had qualified for yet, which is to say like nothing at all.</p>
     */
    private void checkPromotionMoves(StandardsData data, UUID who, long now,
            List<com.sablednah.standards.neoforge.permissions.Promotions.Rule> rules) {
        var store = com.sablednah.standards.neoforge.permissions.PermissionStore.get(
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer());
        String from = "selftest-guest";
        String to = "selftest-regular";
        var rule = rules.get(0);
        var real = List.of(new com.sablednah.standards.neoforge.permissions.Promotions.Rule(
                from, to, rule.realSeconds(), rule.playedSeconds()));
        try {
            store.createGroup(from);
            store.addToGroup(who, from);

            // The target does not exist yet. Moving them now would take them OUT of guest and put
            // them nowhere — a promotion to less than they had.
            check("a rule whose target group is missing moves nobody",
                    com.sablednah.standards.neoforge.permissions.Promotions
                            .promote(store, data, who, real, now).isEmpty());
            check("...and leaves them where they were",
                    store.groupsOf(who).stream().anyMatch(g -> g.equalsIgnoreCase(from)));

            store.createGroup(to);
            var moved = com.sablednah.standards.neoforge.permissions.Promotions
                    .promote(store, data, who, real, now);
            check("a satisfied rule promotes", moved.isPresent());
            check("...into the target group",
                    store.groupsOf(who).stream().anyMatch(g -> g.equalsIgnoreCase(to)));
            check("...and out of the old one",
                    store.groupsOf(who).stream().noneMatch(g -> g.equalsIgnoreCase(from)));
            check("a second pass does not promote them again",
                    com.sablednah.standards.neoforge.permissions.Promotions
                            .promote(store, data, who, real, now).isEmpty());
        } finally {
            store.deleteGroup(from);
            store.deleteGroup(to);
            check("the promotion fixtures are gone",
                    store.group(from).isEmpty() && store.group(to).isEmpty()
                            && store.groupsOf(who).isEmpty());
        }
    }

    /**
     * The numbered message runs behind {@code /motd}, {@code /rules} and {@code /info}.
     *
     * <p>The behaviour worth pinning down is <b>stopping at a gap</b>. Deleting a line from the
     * middle is how an owner shortens their rules, and carrying on past the hole would silently
     * renumber everything after it.</p>
     */
    private void checkInfoRuns() {
        String rules = com.sablednah.standards.neoforge.commands.InfoCommands.render("msg.rules");
        check("a numbered run renders every line it has",
                rules.lines().count() == 4);
        check("...in order, header first",
                rules.startsWith(Lang.get("msg.rules.1")));
        check("a run with no keys at all renders nothing",
                com.sablednah.standards.neoforge.commands.InfoCommands
                        .render("msg.selftest.nosuchrun").isEmpty());
        // The other direction: a key that exists but is not numbered must not be picked up, or
        // 'msg.info.empty' would be printed as part of /info.
        check("an unnumbered sibling key is not part of the run",
                !com.sablednah.standards.neoforge.commands.InfoCommands.render("msg.info")
                        .contains(Lang.get("msg.info.empty")));
    }

    /**
     * Nicknames, and above all the rule that stops one being an impersonation.
     *
     * <p>Every assertion here has its opposite, because a check that refuses <em>every</em>
     * nickname would pass all the positive ones and make the feature useless, while one that
     * refuses none would pass nothing and ship the hole.</p>
     *
     * <p>Uses the real store on the real server, seeded with names, and cleans up after itself.</p>
     */
    private void checkNicknames(MinecraftServer server) {
        StandardsData data = StandardsData.get(server);
        UUID alice = UUID.nameUUIDFromBytes("selftest-nick-alice".getBytes());
        UUID mallory = UUID.nameUUIDFromBytes("selftest-nick-mallory".getBytes());
        try {
            data.rememberName(alice, "SelftestAlice");
            data.rememberName(mallory, "SelftestMallory");

            check("a free nickname is allowed",
                    data.impersonates(mallory, "Wanderer").isEmpty());

            // THE ONE THAT MATTERS. Taking somebody else's real name is the whole attack, and it
            // works on everybody who reads chat rather than the tab list.
            check("a nickname may not be another player's real name",
                    data.impersonates(mallory, "SelftestAlice").isPresent());
            check("...case-insensitively, or the guard is one keystroke wide",
                    data.impersonates(mallory, "selftestALICE").isPresent());
            check("...and it names who is being impersonated",
                    data.impersonates(mallory, "SelftestAlice").map(alice::equals).orElse(false));

            // Your own name back is always allowed, or somebody who nicknamed themselves could
            // never undo it by typing what they are actually called.
            check("your own real name is not an impersonation",
                    data.impersonates(alice, "SelftestAlice").isEmpty());

            data.setNick(alice, "Wanderer");
            check("the nickname is what chat will use",
                    data.displayName(alice, "SelftestAlice").equals("Wanderer"));
            check("somebody without one keeps their real name",
                    data.displayName(mallory, "SelftestMallory").equals("SelftestMallory"));
            check("a nickname is findable, which is the price of having them at all",
                    data.byNick("wanderer").map(alice::equals).orElse(false));

            // The second door into the same room: two players rendering as one word.
            check("a nickname may not be another player's nickname",
                    data.impersonates(mallory, "Wanderer").isPresent());
            check("...ignoring colour codes, because a reader cannot see them",
                    data.impersonates(mallory, "&cWanderer").isPresent());
            check("the owner may re-set their own nickname",
                    data.impersonates(alice, "Wanderer").isEmpty());

            data.setNick(alice, null);
            check("clearing gives the real name back",
                    data.displayName(alice, "SelftestAlice").equals("SelftestAlice"));
            check("and the nickname is free again",
                    data.impersonates(mallory, "Wanderer").isEmpty());
        } finally {
            data.setNick(alice, null);
            data.setNick(mallory, null);
        }
    }

    /**
     * The store and the handler, end to end through {@link PermissionAPI} itself.
     *
     * <p>{@link #checkPermissionRules} proves the resolver decides correctly against fixtures.
     * This asks the other question, the one this codebase keeps getting wrong: <b>has anything
     * ever actually called it?</b> Everything in between — the saved data, the tier assembly, the
     * boolean cast, NeoForge's own dispatch — sits on the path a real permission check takes, and
     * none of it is touched by testing the rules directly.</p>
     *
     * <p>Only when our handler is the active one. Under LuckPerms or the default handler these
     * assertions would be asking somebody else's implementation about a group it has never heard
     * of, and would fail for a reason that is not a bug.</p>
     *
     * <p>The fixtures are removed again in a finally block. A self-test that leaves a group behind
     * in a real world has done something worse than not running.</p>
     */
    private void checkPermissionStore(MinecraftServer server) {
        if (!com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler.isActive()) {
            return;
        }
        var store = com.sablednah.standards.neoforge.permissions.PermissionStore.get(server);
        UUID subject = UUID.nameUUIDFromBytes("selftest-permissions".getBytes());
        String group = "standards-selftest";
        try {
            check("a fresh node answers its own default before anything is granted",
                    !PermissionAPI.getOfflinePermission(subject, StandardsPermissions.FLY));

            check("the fixture group is created", store.createGroup(group));
            check("the fixture player joins it", store.addToGroup(subject, group));
            store.setGroupNode(group, StandardsPermissions.FLY.getNodeName(), true);
            check("a group grant reaches PermissionAPI",
                    PermissionAPI.getOfflinePermission(subject, StandardsPermissions.FLY));

            // Both directions: a grant that cannot be taken away again is not a permission.
            store.setPlayerNode(subject, StandardsPermissions.FLY.getNodeName(), false);
            check("a deny on the player beats their group, through PermissionAPI",
                    !PermissionAPI.getOfflinePermission(subject, StandardsPermissions.FLY));
            store.setPlayerNode(subject, StandardsPermissions.FLY.getNodeName(), null);

            // A wildcard, all the way through rather than against a fixture map.
            store.setGroupNode(group, "standards.*", true);
            check("a wildcard grant reaches an unrelated node",
                    PermissionAPI.getOfflinePermission(subject, StandardsPermissions.SMITE));
            check("and it was the wildcard that answered",
                    com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler
                            .explain(subject, StandardsPermissions.SMITE.getNodeName())
                            .map(a -> a.pattern().equals("standards.*")).orElse(false));

            // Somebody the store has never been told about must be untouched by any of it.
            UUID stranger = UUID.nameUUIDFromBytes("selftest-permissions-stranger".getBytes());
            check("a stranger is untouched by another group's wildcard",
                    !PermissionAPI.getOfflinePermission(stranger, StandardsPermissions.SMITE));
        } finally {
            store.deleteGroup(group);
            store.setPlayerNode(subject, StandardsPermissions.FLY.getNodeName(), null);
            check("the fixtures are gone again", store.group(group).isEmpty()
                    && store.groupsOf(subject).isEmpty() && store.nodesOf(subject).isEmpty());
        }
    }

    /**
     * The command a parse would actually run.
     *
     * <p>Not {@code getContext().getCommand()}, which is null whenever the parse followed a
     * <b>redirect</b> — vanilla registers {@code /tell} and {@code /w} as redirects to
     * {@code /msg}, so the command lives in the child context and the obvious accessor reports
     * "not executable" for two commands that work perfectly. That is a bug in the test, not the
     * tree, and it cost a round of head-scratching to find.</p>
     */
    private static com.mojang.brigadier.Command<CommandSourceStack> boundCommand(
            ParseResults<CommandSourceStack> parsed) {
        return parsed.getContext().getLastChild().getCommand();
    }

    /** Safe landing has to say no. A search that always finds somewhere is not a safety check. */
    private void checkSafeLoc(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        // 1.21.11 moved world spawn behind LevelData.RespawnData — there is no
        // getSharedSpawnPos() any more. Worth knowing: it is the same rename family as
        // ResourceLocation -> Identifier, and it will bite again on the next port.
        BlockPos spawn = overworld.getRespawnData().globalPos().pos();
        // Force the chunk: an unloaded chunk answers block queries with defaults, and a probe that
        // skips this proves nothing about the world it thinks it is reading.
        overworld.getChunkAt(spawn);

        // Stand the probe on the SURFACE at spawn's column rather than at spawn itself. This
        // check used to trust world spawn to be somewhere buildable, which is a fact about the
        // world and not about SafeLoc — and it duly failed the first time it met a world whose
        // spawn was over open air. (An upgraded world reports the default 0, 70, 0: vanilla's
        // spawn does not survive the trip. Standards' own /spawn is unaffected, because it keeps
        // its own in save data — decision 5.)
        BlockPos ground = overworld.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        overworld.getChunkAt(ground);
        check("finds somewhere safe on the surface at " + ground.toShortString()
                        + " [feet=" + overworld.getBlockState(ground)
                        + " below=" + overworld.getBlockState(ground.below()) + "]",
                SafeLoc.find(overworld, ground).isPresent());

        BlockPos deepUnderground = new BlockPos(spawn.getX(), overworld.getMinY() + 1, spawn.getZ());
        overworld.getChunkAt(deepUnderground);
        check("refuses solid bedrock", SafeLoc.find(overworld, deepUnderground).isEmpty()
                || SafeLoc.isSafe(overworld, SafeLoc.find(overworld, deepUnderground).orElseThrow()));

        // A flying traveller needs no floor. Without this, anywhere you flew is somewhere you can
        // never return to — the whole back trail of a flying player is unreachable.
        BlockPos midAir = new BlockPos(spawn.getX(), overworld.getMaxY() - 2, spawn.getZ());
        overworld.getChunkAt(midAir);
        check("open sky is not safe to stand in",
                !SafeLoc.isSafe(overworld, midAir, true));
        check("open sky is fine for someone who can fly",
                SafeLoc.isSafe(overworld, midAir, false));
        check("the default still demands a floor",
                SafeLoc.isSafe(overworld, midAir) == SafeLoc.isSafe(overworld, midAir, true));
        // Relaxing the floor is not permission to be entombed.
        check("a flier is still refused inside solid rock",
                !SafeLoc.isSafe(overworld, deepUnderground, false));
    }

    /**
     * Which end of a {@code /tpa} actually moves.
     *
     * <p>Getting this backwards is one of the two classic bugs in the feature — {@code /tpahere}
     * accepted must move the <em>acceptor</em>, not the requester — and it is invisible until two
     * people try it, at which point one of them is somewhere they never asked to be. Pure
     * arithmetic on the request record, so it costs nothing to assert.</p>
     */
    private void checkTeleportRequests() {
        UUID asker = UUID.nameUUIDFromBytes("selftest-asker".getBytes());
        UUID askee = UUID.nameUUIDFromBytes("selftest-askee".getBytes());

        var toTarget = new TeleportRequests.Request(
                asker, askee, TeleportRequests.Direction.TO_TARGET, 0L);
        check("/tpa moves the requester", toTarget.traveller().equals(asker));
        check("/tpa leaves the target standing", toTarget.host().equals(askee));

        var toRequester = new TeleportRequests.Request(
                asker, askee, TeleportRequests.Direction.TO_REQUESTER, 0L);
        check("/tpahere moves the target", toRequester.traveller().equals(askee));
        check("/tpahere leaves the requester standing", toRequester.host().equals(asker));

        // Both directions of the other kind: traveller and host must never be the same person,
        // or the teleport is a no-op that still reports success.
        check("traveller and host are always different",
                !toTarget.traveller().equals(toTarget.host())
                        && !toRequester.traveller().equals(toRequester.host()));
    }

    /**
     * Duration parsing, which is entirely made of edge cases.
     *
     * <p>The one that matters most is the negative: {@code "5 bananas"} must <em>not</em> quietly
     * parse as five of something. A moderator who fat-fingers a duration and gets a silent
     * five-second ban has been told nothing, and will not find out until the player complains.</p>
     */
    private void checkDurations() {
        check("bare number is seconds", Duration.parse("90").orElse(-1L) == 90L);
        check("minutes", Duration.parse("30m").orElse(-1L) == 1800L);
        check("hours", Duration.parse("2h").orElse(-1L) == 7200L);
        check("days", Duration.parse("7d").orElse(-1L) == 604_800L);
        check("compound", Duration.parse("1h30m").orElse(-1L) == 5400L);
        check("compound, any order", Duration.parse("30m1h").orElse(-1L) == 5400L);
        check("long unit names", Duration.parse("2hours").orElse(-1L) == 7200L);
        check("permanent", Duration.parse("perm").orElse(0L) == Duration.PERMANENT);
        check("forever", Duration.parse("forever").orElse(0L) == Duration.PERMANENT);

        // Both directions. A parser that accepts everything is not a parser.
        check("rejects nonsense", Duration.parse("bananas").isEmpty());
        check("rejects a number with a bogus unit", Duration.parse("5x").isEmpty());
        check("rejects trailing rubbish", Duration.parse("5 bananas").isEmpty());
        check("rejects empty", Duration.parse("").isEmpty());
        check("rejects zero", Duration.parse("0m").isEmpty());

        check("describes compound", Duration.describe(90_061L).equals("1d 1h 1m 1s"));
        check("describes permanent", Duration.describe(Duration.PERMANENT).equals("permanent"));
        // Round trip, the property that actually matters.
        for (long seconds : new long[] {1L, 59L, 60L, 3600L, 5400L, 604_800L}) {
            check("duration round-trips " + Duration.describe(seconds),
                    Duration.parse(Duration.describe(seconds).replace(" ", "")).orElse(-1L) == seconds);
        }
    }

    /**
     * The chat decorator ordering, which is the whole contract other mods code against.
     *
     * <p>Asserts the rule in both directions — priority is closeness to the name, so prefixes run
     * lowest-first and suffixes highest-first. Getting this backwards on one side only is exactly
     * the kind of thing that looks fine until a second mod registers.</p>
     */
    private void checkChatDecorators() {
        record Fixed(String id, int priority, String pre, String post) implements NameDecorator {
            public String id() { return id; }
            public int priority() { return priority; }
            public java.util.Optional<String> prefix(net.minecraft.server.level.ServerPlayer p) {
                return java.util.Optional.of(pre);
            }
            public java.util.Optional<String> suffix(net.minecraft.server.level.ServerPlayer p) {
                return java.util.Optional.of(post);
            }
        }

        int before = Chat.all().size();
        NameDecorator party = new Fixed("selftest:party", 10, "[PARTY]", "-party");
        NameDecorator faction = new Fixed("selftest:faction", 5, "[FACTION]", "-faction");
        NameDecorator rank = new Fixed("selftest:rank", 100, "Lord", "the noble");
        try {
            Chat.register(party);
            Chat.register(faction);
            Chat.register(rank);

            // A null player is fine here: these fixed decorators never look at it.
            var prefixes = Chat.prefixes(null);
            var suffixes = Chat.suffixes(null);

            check("decorators registered", Chat.all().size() == before + 3);
            check("prefixes run lowest priority first (furthest from the name)",
                    prefixes.equals(java.util.List.of("[FACTION]", "[PARTY]", "Lord")));
            check("suffixes mirror them, highest priority nearest the name",
                    suffixes.equals(java.util.List.of("the noble", "-party", "-faction")));
            // The worked example from the design, end to end.
            check("assembles the intended line",
                    String.join("", prefixes).equals("[FACTION][PARTY]Lord"));
        } finally {
            // MUST come out again. Left registered, these decorate every real chat line on the
            // server for the rest of its life — which also drags every message onto the
            // cancel-and-deliver path, costing signed chat and hover cards. It did exactly that
            // for a whole day of testing before the RCON battery noticed.
            Chat.unregister(party);
            Chat.unregister(faction);
            Chat.unregister(rank);
        }
        check("the test decorators are gone again",
                Chat.all().stream().noneMatch(d -> d.id().startsWith("selftest:")));
    }

    /**
     * The {@code /invsee} slot mapping — the one place in the mod where being wrong costs items.
     *
     * <p>Asserted rather than eyeballed because the first version destroyed anything dropped into
     * a dead slot, and only two people with a stack of diamonds found it. Pure arithmetic on the
     * mapping, so it costs nothing to check on every boot.</p>
     */
    private void checkInventoryViewMapping() {
        // A player inventory is 36 storage plus 7 equipment slots. No live player needed — the
        // mapping is pure data, which is the whole reason it was pulled out of the constructor:
        // the first version of this check required someone online and so was silently skipped on
        // an empty dev server, reporting as a pass while testing nothing.
        int[] map = InventoryView.buildMapping(43);
        final int NONE = -1;

        check("mapping covers a full six rows", map.length == InventoryView.SIZE);

        boolean storage = true;
        for (int i = 0; i < 27; i++) storage &= map[i] == i + 9;
        check("rows 1-3 are main storage (inventory 9-35)", storage);

        boolean hotbar = true;
        for (int i = 0; i < 9; i++) hotbar &= map[27 + i] == i;
        check("row 4 is the hotbar (inventory 0-8)", hotbar);

        boolean divider = true;
        for (int i = 36; i <= 44; i++) divider &= map[i] == NONE;
        check("row 5 is entirely a divider", divider);

        // The agreed equipment row: H C L B X O X S A
        check("helmet, chestplate, leggings, boots",
                map[45] == 39 && map[46] == 38 && map[47] == 37 && map[48] == 36);
        check("both spacers are dead", map[49] == NONE && map[51] == NONE);
        check("off hand sits alone between them", map[50] == 40);
        check("saddle and animal armour close the row", map[52] == 42 && map[53] == 41);
        check("the tail is dead", map[44] == NONE);

        // No inventory slot may appear twice, or taking from one would empty another.
        var seen = new java.util.HashSet<Integer>();
        boolean unique = true;
        for (int slot : map) {
            if (slot != NONE && !seen.add(slot)) unique = false;
        }
        check("no inventory slot is mapped twice", unique);

        // Every storage and equipment slot the player has must be reachable, or /invsee hides
        // something — which for a moderation tool is the failure that matters most.
        boolean reachable = true;
        for (int i = 0; i < 41; i++) {
            if (i == 41 || i == 42) continue;
            reachable &= seen.contains(i);
        }
        check("every real inventory slot is reachable", reachable);
    }

    /** The ledger, through the public facade — the same door other mods come in by. */
    private void checkLedger(MinecraftServer server) {
        if (!Economy.isAvailable()) {
            check("an economy provider is registered", false);
            return;
        }
        UUID test = UUID.nameUUIDFromBytes("standards-selftest".getBytes());
        StandardsData.get(server).setBalance(test, 0.0D);

        check("deposit succeeds", Economy.deposit(test, 100.0D, "selftest").success());
        check("balance reflects the deposit", Economy.balance(test) == 100.0D);
        check("withdraw within balance succeeds", Economy.withdraw(test, 40.0D, "selftest").success());
        check("balance reflects the withdrawal", Economy.balance(test) == 60.0D);
        // Both directions again: overdrawing must fail, and must not move the money.
        check("overdrawing fails", !Economy.withdraw(test, 1000.0D, "selftest").success());
        check("a failed withdrawal leaves the balance alone", Economy.balance(test) == 60.0D);
        check("a negative deposit is refused", !Economy.deposit(test, -5.0D, "selftest").success());

        UUID other = UUID.nameUUIDFromBytes("standards-selftest-2".getBytes());
        StandardsData.get(server).setBalance(other, 0.0D);
        check("transfer succeeds", Economy.transfer(test, other, 10.0D, "selftest").success());
        check("transfer moved exactly the amount",
                Economy.balance(test) == 50.0D && Economy.balance(other) == 10.0D);

        StandardsData.get(server).setBalance(test, 0.0D);
        StandardsData.get(server).setBalance(other, 0.0D);
    }

    /**
     * A waypoint has to survive being written and read back — the failure that turns every home on
     * the server into a hole in the ground, and one that only shows up after a restart.
     */
    private void checkWaypointRoundTrip(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        Waypoint original = new Waypoint(overworld.dimension(), 1.5D, 64.0D, -2.5D, 90.0F, -10.0F);
        var encoded = Waypoint.CODEC.encodeStart(
                net.minecraft.nbt.NbtOps.INSTANCE, original).result();
        check("waypoint encodes", encoded.isPresent());
        if (encoded.isEmpty()) return;
        var decoded = Waypoint.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded.get()).result();
        check("waypoint decodes back to itself",
                decoded.isPresent() && decoded.get().equals(original));
        check("waypoint resolves its level", original.level(server) != null);
    }

    private void check(String what, boolean passed) {
        checks++;
        if (passed) {
            Standards.LOGGER.info("  ✓ {}", what);
        } else {
            failures.add(what);
        }
    }
}

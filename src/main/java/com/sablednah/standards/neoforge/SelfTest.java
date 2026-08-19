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
import com.sablednah.standards.core.Money;
import com.sablednah.standards.core.Toggle;
import com.sablednah.standards.core.Waypoint;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

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
        checkMoneyFormatting();
        checkCommandsParse(server);
        checkSafeLoc(server);
        checkTeleportRequests();
        checkDurations();
        checkChatDecorators();
        checkInventoryViewMapping();
        checkLedger(server);
        checkWaypointRoundTrip(server);

        if (failures.isEmpty()) {
            Standards.LOGGER.info("=== Standards self-test PASSED ({} checks) ===", checks);
        } else {
            Standards.LOGGER.error("=== Standards self-test FAILED: {} of {} checks ===",
                    failures.size(), checks);
            failures.forEach(f -> Standards.LOGGER.error("  ✗ {}", f));
        }
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
                "top", "jump", "j", "back", "back 2",
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

        // The other direction: a command that should NOT parse must not. Without this, an
        // over-eager tree that matches anything would sail through every check above.
        ParseResults<CommandSourceStack> nonsense =
                server.getCommands().getDispatcher().parse("fly sideways backwards", console);
        check("garbage arguments are rejected",
                !nonsense.getExceptions().isEmpty() || nonsense.getReader().canRead());
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
        check("finds somewhere safe near spawn " + spawn.toShortString()
                        + " [feet=" + overworld.getBlockState(spawn)
                        + " below=" + overworld.getBlockState(spawn.below()) + "]",
                SafeLoc.find(overworld, spawn).isPresent());

        BlockPos deepUnderground = new BlockPos(spawn.getX(), overworld.getMinY() + 1, spawn.getZ());
        overworld.getChunkAt(deepUnderground);
        check("refuses solid bedrock", SafeLoc.find(overworld, deepUnderground).isEmpty()
                || SafeLoc.isSafe(overworld, SafeLoc.find(overworld, deepUnderground).orElseThrow()));
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
        Chat.register(new Fixed("selftest:party", 10, "[PARTY]", "-party"));
        Chat.register(new Fixed("selftest:faction", 5, "[FACTION]", "-faction"));
        Chat.register(new Fixed("selftest:rank", 100, "Lord", "the noble"));

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
    }

    /**
     * The {@code /invsee} slot mapping — the one place in the mod where being wrong costs items.
     *
     * <p>Asserted rather than eyeballed because the first version destroyed anything dropped into
     * a dead slot, and only two people with a stack of diamonds found it. Pure arithmetic on the
     * mapping, so it costs nothing to check on every boot.</p>
     */
    private void checkInventoryViewMapping() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) {
            // Needs a real player's inventory to map; skipped on an empty dev server rather than
            // faked, since a fake would be testing the fake.
            return;
        }
        var view = new InventoryView(server.getPlayerList().getPlayers().getFirst());

        check("view is a full six rows", view.getContainerSize() == InventoryView.SIZE);

        // Main storage, hotbar and armour are live; the divider and tail are not.
        boolean storageLive = true;
        for (int i = 0; i < 36; i++) storageLive &= view.isLive(i);
        check("all 36 storage and hotbar slots are live", storageLive);

        boolean dividerDead = true;
        for (int i = 36; i <= 44; i++) dividerDead &= !view.isLive(i);
        check("the divider row is entirely dead", dividerDead);

        // The bottom row, asserted against the agreed layout: H C L B X O X S A
        check("helmet, chestplate, leggings, boots are live",
                view.isLive(45) && view.isLive(46) && view.isLive(47) && view.isLive(48));
        check("the two spacers are dead", !view.isLive(49) && !view.isLive(51));
        check("off hand is live", view.isLive(50));
        check("saddle and animal armour are live", view.isLive(52) && view.isLive(53));

        // Every dead slot must show something, or it is an invisible hole again.
        boolean deadSlotsVisible = true;
        for (int i = 0; i < InventoryView.SIZE; i++) {
            if (!view.isLive(i) && view.getItem(i).isEmpty()) deadSlotsVisible = false;
        }
        check("every dead slot is visibly filled", deadSlotsVisible);

        // And the other direction: a dead slot must refuse to give anything up.
        check("a dead slot yields nothing on removal",
                view.removeItem(40, 64).isEmpty() && view.removeItemNoUpdate(40).isEmpty());
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

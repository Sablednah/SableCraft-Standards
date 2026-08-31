package com.sablednah.standards.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes, LuckPerms-compatible through NeoForge's {@code PermissionAPI}.
 *
 * <p>With no permissions manager installed each node's default resolver is the answer, and those
 * defaults are chosen so a fresh server behaves the way a fresh server should: the everyday
 * conveniences ({@code /home}, {@code /back}, {@code /balance}, {@code /pay}) are open to
 * everyone, and anything that changes the rules of the game ({@code /fly}, {@code /god}, the
 * {@code /eco} admin tree, acting on <em>other</em> players) needs op. Install LuckPerms — which
 * has a NeoForge build and hooks this API — and every one of them becomes per-group, with no
 * extra code here.</p>
 *
 * <h2>Home limits</h2>
 *
 * <p>Home allowances are boolean nodes numbered like EssentialsX's — {@code
 * standards.home.limit.5} — rather than one integer node. Two reasons: NeoForge's integer
 * permission type has patchy support across managers, and more importantly every server admin
 * alive already knows the numbered-node idiom. The highest granted number wins, and
 * {@code standards.home.limit.unlimited} beats them all.</p>
 */
public final class StandardsPermissions {

    /**
     * Every fixed node, in declaration order, so gathering cannot forget one.
     *
     * <p><b>Declared before the nodes themselves, and that is load-bearing.</b> Java runs static
     * initialisers in source order, so a collection declared after the fields that fill it is
     * still null when they run — which crashes the whole mod during command registration with an
     * NPE nowhere near the actual mistake. (Found by the self-test run, as intended.)</p>
     */
    private static final List<PermissionNode<Boolean>> FIXED = new ArrayList<>();

    /** {@code standards.kit.<name>} for each kit that existed at server start. See {@link #canUseKit}. */
    private static final Map<String, PermissionNode<Boolean>> KIT_NODES = new LinkedHashMap<>();

    /** Numbered home-limit nodes, built at gather time from the configured ceiling. */
    private static final Map<Integer, PermissionNode<Boolean>> HOME_LIMITS = new LinkedHashMap<>();

    private enum Default { EVERYONE, OPS, NOBODY }

    /**
     * Node name to the default it was declared with, so the mod can describe its own permissions.
     *
     * <p>Kept because {@code PermissionNode} does not expose it: the default is a resolver
     * function, and asking one offline can only tell "everyone" from the other two — an op-gated
     * node and a nobody-gated node both answer false with no player in hand. That distinction is
     * exactly what an admin is asking about, so it is recorded here rather than inferred.</p>
     *
     * <p>Declared before the nodes, like {@link #FIXED}, for the static-initialisation-order
     * reason the comment there gives.</p>
     */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    // --- switches ---
    public static final PermissionNode<Boolean> FLY = node("fly", Default.OPS);
    public static final PermissionNode<Boolean> FLY_OTHERS = node("fly.others", Default.OPS);
    public static final PermissionNode<Boolean> GOD = node("god", Default.OPS);
    public static final PermissionNode<Boolean> GOD_OTHERS = node("god.others", Default.OPS);

    /** Hide from other players. */
    public static final PermissionNode<Boolean> VANISH = node("vanish", Default.OPS);
    public static final PermissionNode<Boolean> VANISH_OTHERS = node("vanish.others", Default.OPS);
    /**
     * See through someone else's vanish. Ops by default so staff are not invisible to each other —
     * two moderators unable to find one another is its own problem.
     */
    public static final PermissionNode<Boolean> VANISH_SEE = node("vanish.see", Default.OPS);
    /** Call down lightning. A gamemaster's tool, not a toy. */
    public static final PermissionNode<Boolean> SMITE = node("smite", Default.OPS);

    // --- movement ---
    public static final PermissionNode<Boolean> TOP = node("top", Default.EVERYONE);
    public static final PermissionNode<Boolean> JUMP = node("jump", Default.OPS);
    public static final PermissionNode<Boolean> BACK = node("back", Default.EVERYONE);
    /**
     * Returning to your corpse is a gameplay decision, not a convenience — a server that wants
     * death to cost something must not have that quietly handed back. Off by default even though
     * {@code /back} itself is open.
     */
    public static final PermissionNode<Boolean> BACK_ON_DEATH = node("back.ondeath", Default.NOBODY);

    // --- homes ---
    public static final PermissionNode<Boolean> HOME = node("home", Default.EVERYONE);
    public static final PermissionNode<Boolean> SETHOME = node("sethome", Default.EVERYONE);
    public static final PermissionNode<Boolean> DELHOME = node("delhome", Default.EVERYONE);
    public static final PermissionNode<Boolean> HOME_OTHERS = node("home.others", Default.OPS);
    public static final PermissionNode<Boolean> HOME_LIMIT_UNLIMITED = node("home.limit.unlimited", Default.OPS);

    // --- putting a player right, and moving them about ---
    public static final PermissionNode<Boolean> HEAL = node("heal", Default.OPS);
    public static final PermissionNode<Boolean> HEAL_OTHERS = node("heal.others", Default.OPS);
    public static final PermissionNode<Boolean> FEED = node("feed", Default.OPS);
    public static final PermissionNode<Boolean> FEED_OTHERS = node("feed.others", Default.OPS);
    public static final PermissionNode<Boolean> REST = node("rest", Default.OPS);
    public static final PermissionNode<Boolean> REST_OTHERS = node("rest.others", Default.OPS);
    public static final PermissionNode<Boolean> SPEED = node("speed", Default.OPS);
    public static final PermissionNode<Boolean> SPEED_OTHERS = node("speed.others", Default.OPS);
    public static final PermissionNode<Boolean> SPAWN = node("spawn", Default.EVERYONE);
    public static final PermissionNode<Boolean> SETSPAWN = node("setspawn", Default.OPS);
    /** Op-only: near bedrock this is a crude ore finder as much as a travel command. */
    public static final PermissionNode<Boolean> BOTTOM = node("bottom", Default.OPS);

    /** Reading server health is not sensitive; knowing the TPS helps players report problems. */
    public static final PermissionNode<Boolean> SERVER_HEALTH = node("gc", Default.EVERYONE);
    public static final PermissionNode<Boolean> KIT = node("kit", Default.EVERYONE);
    public static final PermissionNode<Boolean> SETKIT = node("setkit", Default.OPS);
    public static final PermissionNode<Boolean> MAIL = node("mail", Default.EVERYONE);
    /** Op-only: logging off should not broadcast where you were. */
    public static final PermissionNode<Boolean> TP_OFFLINE = node("tpoffline", Default.OPS);

    // --- away ---
    public static final PermissionNode<Boolean> AFK = node("afk", Default.EVERYONE);
    /** Never auto-kicked for idling. Ops and, typically, whoever runs the map render. */
    public static final PermissionNode<Boolean> AFK_EXEMPT = node("afk.exempt", Default.OPS);

    // --- talking ---
    public static final PermissionNode<Boolean> MSG = node("msg", Default.EVERYONE);
    /** Message someone who has /msgtoggle on. Staff need it; it is not for everyone. */
    public static final PermissionNode<Boolean> MSG_OVERRIDE = node("msg.override", Default.OPS);
    public static final PermissionNode<Boolean> SOCIALSPY = node("socialspy", Default.OPS);

    // --- moderation. Ops only; vanilla and LuckPerms own everything either side of these three.
    public static final PermissionNode<Boolean> TEMPBAN = node("tempban", Default.OPS);
    public static final PermissionNode<Boolean> MUTE = node("mute", Default.OPS);
    public static final PermissionNode<Boolean> INVSEE = node("invsee", Default.OPS);

    /**
     * Portable workstations — <b>nobody by default, including operators.</b>
     *
     * <p>That is the design, not an oversight. A workbench you can open anywhere is an advantage
     * to be granted rather than a utility to be assumed: a builder rank gets {@code craft}, a
     * blacksmith class gets {@code anvil}. LuckPerms grants them per group; a LegendQuest skill
     * bypasses them entirely through the {@code Stations} API, because a skill the player has
     * already earned is its own authority.</p>
     */
    public static final PermissionNode<Boolean> CRAFT = node("craft", Default.NOBODY);
    public static final PermissionNode<Boolean> ANVIL = node("anvil", Default.NOBODY);
    public static final PermissionNode<Boolean> GRINDSTONE = node("grindstone", Default.NOBODY);
    public static final PermissionNode<Boolean> ENDERCHEST = node("enderchest", Default.NOBODY);
    public static final PermissionNode<Boolean> TRASH = node("trashcan", Default.NOBODY);

    // --- teleport requests ---
    public static final PermissionNode<Boolean> TPA = node("tpa", Default.EVERYONE);
    public static final PermissionNode<Boolean> TPA_HERE = node("tpahere", Default.EVERYONE);
    public static final PermissionNode<Boolean> TPTOGGLE = node("tptoggle", Default.EVERYONE);
    /**
     * Ask someone who has /tptoggle on anyway. Staff need it, or /tptoggle becomes a place to
     * hide from moderation rather than from strangers.
     */
    public static final PermissionNode<Boolean> TPA_OVERRIDE = node("tpa.override", Default.OPS);

    // --- warps ---
    public static final PermissionNode<Boolean> WARP = node("warp", Default.EVERYONE);
    public static final PermissionNode<Boolean> SETWARP = node("setwarp", Default.OPS);

    // --- economy ---
    public static final PermissionNode<Boolean> BALANCE = node("balance", Default.EVERYONE);
    public static final PermissionNode<Boolean> BALANCE_OTHERS = node("balance.others", Default.OPS);
    public static final PermissionNode<Boolean> BALTOP = node("baltop", Default.EVERYONE);
    public static final PermissionNode<Boolean> PAY = node("pay", Default.EVERYONE);
    public static final PermissionNode<Boolean> ECO_ADMIN = node("eco", Default.OPS);

    // --- admin teleports ---
    /**
     * {@code /tpx} and {@code /tppos} — moving yourself about at will.
     *
     * <p>A node rather than an op check, which is the entire reason these exist beside vanilla's
     * {@code /tp}: a builder can be given this without also being given {@code /stop}.</p>
     */
    public static final PermissionNode<Boolean> TP = node("tp", Default.OPS);
    /** Moving <em>other</em> people — {@code /tphere}, and the two-player form of {@code /tpx}. */
    public static final PermissionNode<Boolean> TP_OTHERS = node("tp.others", Default.OPS);

    /** {@code /motd}, {@code /rules} and {@code /info} — owner-written text. Everyone reads them. */
    public static final PermissionNode<Boolean> MOTD = node("motd", Default.EVERYONE);

    /** {@code /butcher} — clearing entities in a radius. A lag tool, and a griefing tool. */
    public static final PermissionNode<Boolean> BUTCHER = node("butcher", Default.OPS);

    /**
     * {@code /i} — spawning items out of nothing. Ops, obviously: on a survival server this is
     * the single most consequential thing in the mod.
     */
    public static final PermissionNode<Boolean> ITEM = node("item", Default.OPS);

    // --- nicknames ---
    /** Set your own. Everyone, like homes — it is a social feature, not a privilege. */
    public static final PermissionNode<Boolean> NICK = node("nick", Default.EVERYONE);
    /**
     * Colour codes in a nickname.
     *
     * <p>Ops only, and not fussiness: {@code &k} is obfuscated text, which renders as animated
     * gibberish on every line its owner speaks and cannot be read, reported or typed back. A name
     * nobody can transcribe is a name nobody can report.</p>
     */
    public static final PermissionNode<Boolean> NICK_COLOR = node("nick.color", Default.OPS);
    /** Set or clear somebody else's — the moderator's undo for a nickname that had to go. */
    public static final PermissionNode<Boolean> NICK_OTHERS = node("nick.others", Default.OPS);
    /**
     * Look a nickname up. <b>Everyone</b>, deliberately.
     *
     * <p>If only staff can tell who somebody really is, a nickname is a disguise rather than a
     * flourish. The whole feature is only defensible while the real name stays one command away
     * from any player who wonders.
     */
    public static final PermissionNode<Boolean> REALNAME = node("realname", Default.EVERYONE);

    /** Founding and running a lightweight group. Everyone, like homes — it is a social feature. */
    public static final PermissionNode<Boolean> GROUP = node("group", Default.EVERYONE);

    // --- teleport bypasses ---
    /**
     * Leave a fight anyway.
     *
     * <p>A permission and not an op check, so a server can grant it to staff without also handing
     * them {@code /stop} — the same reasoning that put every other gate on a node.</p>
     */
    public static final PermissionNode<Boolean> COMBAT_BYPASS = node("combat.bypass", Default.OPS);

    public static final PermissionNode<Boolean> TP_INSTANT = node("teleport.instant", Default.OPS);
    public static final PermissionNode<Boolean> TP_NO_COOLDOWN = node("teleport.nocooldown", Default.OPS);

    // --- admin ---
    public static final PermissionNode<Boolean> ADMIN = node("admin", Default.OPS);
    /**
     * Editing the built-in permission handler's groups and grants.
     *
     * <p>Separate from {@link #ADMIN} because the two are different jobs: reloading messages is a
     * caretaker's task, and handing out permissions is how somebody becomes an operator by proxy.
     * A server that wants a moderator able to run {@code /standards reload} should not have to
     * make them able to grant themselves {@code standards.*}.</p>
     */
    public static final PermissionNode<Boolean> PERMISSIONS = node("permissions", Default.OPS);

    private static PermissionNode<Boolean> node(String path, Default fallback) {
        PermissionNode<Boolean> created = new PermissionNode<>(
                Standards.MODID, path, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> switch (fallback) {
                    case EVERYONE -> Boolean.TRUE;
                    case NOBODY -> Boolean.FALSE;
                    // An offline check has no player to ask about op status. Answering "no" is the
                    // safe half of the guess, and the online path — which is every real use — has
                    // the player in hand.
                    case OPS -> player != null
                            && Commands.LEVEL_GAMEMASTERS.check(player.permissions());
                });
        FIXED.add(created);
        DEFAULTS.put(created.getNodeName(), fallback.name().toLowerCase(java.util.Locale.ROOT));
        return created;
    }

    @SubscribeEvent
    static void onGather(PermissionGatherEvent.Nodes event) {
        FIXED.forEach(event::addNodes);

        // Numbered home limits. These must be enumerated up front — the API gathers nodes, it does
        // not let us ask "what standards.home.limit.* does this player have?" — so the ceiling is a
        // config value rather than something discovered from what an admin happened to grant.
        HOME_LIMITS.clear();
        int ceiling = StandardsConfig.MAX_HOME_LIMIT_NODE.get();
        for (int n = 1; n <= ceiling; n++) {
            PermissionNode<Boolean> limit = new PermissionNode<>(
                    Standards.MODID, "home.limit." + n, PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> Boolean.FALSE);
            HOME_LIMITS.put(n, limit);
            event.addNodes(limit);
        }
        // Per-kit nodes, for the kits that exist right now. A kit made later has no node and is
        // answered from its own declared access instead — see canUseKit.
        KIT_NODES.clear();
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (String kit : Kits.get(server).names()) {
                PermissionNode<Boolean> node = new PermissionNode<>(
                        Standards.MODID, "kit." + kit.toLowerCase(java.util.Locale.ROOT),
                        PermissionTypes.BOOLEAN,
                        // The kit's access IS this node's default, resolved per call rather than
                        // captured: an admin who runs /kitaccess mid-session must not need a
                        // restart for it to take effect. A permissions manager still overrides it
                        // in either direction, which is how one rank gets one particular kit.
                        (player, uuid, context) -> player != null
                                && allowedBy(player, accessOf(player.level().getServer(), kit)));
                KIT_NODES.put(kit.toLowerCase(java.util.Locale.ROOT), node);
                event.addNodes(node);
            }
        }

        Standards.LOGGER.info("Registered {} permission nodes ({} home limits, {} kits)",
                FIXED.size() + HOME_LIMITS.size() + KIT_NODES.size(),
                HOME_LIMITS.size(), KIT_NODES.size());
    }

    /**
     * Offer Standards' own handler to NeoForge, for servers with no permissions mod.
     *
     * <p><b>Offering is not choosing.</b> {@code PermissionAPI} is the facade and the server owner
     * picks the active handler by name in {@code neoforge-server.toml}; this only puts ours on the
     * list. A server running LuckPerms registers this and never calls it, which is exactly the
     * intended outcome — see {@link com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler}.</p>
     *
     * <p>Fired before {@code ServerStartingEvent}, so nothing here may touch the world: the
     * handler resolves its store lazily on the first question instead.</p>
     */
    @SubscribeEvent
    static void onGatherHandler(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(
                com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler.IDENTIFIER,
                com.sablednah.standards.neoforge.permissions.StandardsPermissionHandler::new);
    }

    /**
     * Every node this build declares, with the default it was declared with.
     *
     * <p>For {@code /standards nodes} and for the generated node reference. Sorted, because the
     * declaration order is grouped by feature and an admin looking one up wants alphabetical.</p>
     */
    public static java.util.List<Map.Entry<String, String>> declaredNodes() {
        return DEFAULTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> (Map.Entry<String, String>) new java.util.AbstractMap.SimpleEntry<>(
                        e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * What a node defaults to, or {@code "runtime"} for one built at server start.
     *
     * <p>The per-kit and numbered home-limit nodes are not declared in source — they are built
     * from whatever the server happens to hold — so they have no static default to report.</p>
     */
    public static String defaultOf(String node) {
        return DEFAULTS.getOrDefault(node, "runtime");
    }

    /** Ask a node about a player. */
    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        return PermissionAPI.getPermission(player, node);
    }

    /**
     * The requires() predicate for a command: a player is asked about the node, and anything that
     * is not a player (console, command block, datapack function) is judged on the vanilla
     * permission level instead. Without that second half, {@code /fly Steve on} from a command
     * block — the case this whole mod was built for — would silently never run.
     */
    /**
     * As {@link #require}, with a config-driven fallback for nodes nobody can otherwise hold.
     *
     * <p><b>Why this exists.</b> The workstations and {@code /back} on death default to
     * {@code Default.NOBODY} on purpose — they are capabilities a mod hands out temporarily, not
     * things every player has. That works while a permissions mod is installed to grant them.</p>
     *
     * <p>Without one, NeoForge's default handler answers every question with the node's own
     * default, so "nobody" means <em>nobody, ever</em>. Decision 7 saves us from the worst of it —
     * a failed {@code requires()} hides the command entirely, so it does not sit in tab-complete
     * taunting people. But the result is that a documented feature is silently absent: an owner
     * reads that Standards has {@code /craft}, installs it, and the command does not exist, with
     * nothing anywhere explaining why.</p>
     *
     * <p>So an owner with no permissions mod can say who these are for. A permissions mod still
     * overrides it in both directions — this only decides the answer where nothing else can.</p>
     */
    public static java.util.function.Predicate<CommandSourceStack> requireOr(
            PermissionNode<Boolean> node, java.util.function.Supplier<String> access) {
        return source -> require(node).test(source) || allowedBy(source, access.get());
    }

    /** The same fallback, for the direct {@link #has} checks that are not command gates. */
    public static boolean hasOr(ServerPlayer player, PermissionNode<Boolean> node,
            java.util.function.Supplier<String> access) {
        return has(player, node) || allowedBy(player.createCommandSourceStack(), access.get());
    }

    private static boolean allowedBy(CommandSourceStack source, String access) {
        return switch (access == null ? "nobody" : access.toLowerCase(java.util.Locale.ROOT)) {
            case "everyone" -> true;
            case "ops" -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
            default -> false;
        };
    }

    public static java.util.function.Predicate<CommandSourceStack> require(PermissionNode<Boolean> node) {
        return source -> source.getEntity() instanceof ServerPlayer player
                ? has(player, node)
                : Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    /**
     * May this player claim this kit?
     *
     * <p>Three things get a say, in this order: the plain {@code standards.kit} node, then the
     * per-kit node {@code standards.kit.<name>} if one was gathered, and behind that the kit's own
     * {@link Kits.Access} — which is what the per-kit node's default resolver reads.</p>
     *
     * <p><b>The kit's access is stored on the kit precisely because the nodes are not.</b>
     * NeoForge gathers permission nodes once at server start, so a kit created this afternoon has
     * none. That case used to answer "open to everybody", which meant a new op-only or rank-only
     * kit was claimable by the entire server until the next restart — and no grant or deny could
     * close it, because there was no node to grant. Asking the kit fixes it at the moment the kit
     * exists.</p>
     */
    public static boolean canUseKit(ServerPlayer player, String kit) {
        if (!has(player, KIT)) {
            return false;
        }
        PermissionNode<Boolean> node = KIT_NODES.get(kit.toLowerCase(java.util.Locale.ROOT));
        // A registered node carries the kit's own access as its default resolver, so this single
        // call gets both: a permissions manager's explicit answer where there is one, and the
        // kit's declared access where there is not.
        if (node != null) {
            return has(player, node);
        }
        // No node means the kit was created since the last gather, so there is nothing for a
        // permissions manager to have had an opinion about. Ask the kit itself.
        return allowedBy(player, accessOf(player.level().getServer(), kit));
    }

    /**
     * Whether an access level lets this player through, before any node is consulted.
     *
     * <p><b>Must not ask a permission node.</b> This is the default resolver behind each kit's own
     * node, so a lookup here would resolve that node, which would call this again. The recursion
     * is not hypothetical — it was written, and caught before it ran.</p>
     */
    private static boolean allowedBy(ServerPlayer player, Kits.Access access) {
        return switch (access) {
            case EVERYONE -> true;
            case OPS -> Commands.LEVEL_GAMEMASTERS.check(player.permissions());
            case PERMISSION -> false;
        };
    }

    /** A kit's declared access, defaulting to open if the kit has gone missing under us. */
    private static Kits.Access accessOf(net.minecraft.server.MinecraftServer server, String kit) {
        if (server == null) {
            return Kits.Access.EVERYONE;
        }
        return Kits.get(server).byName(kit).map(Kits.Kit::access).orElse(Kits.Access.EVERYONE);
    }

    /**
     * How many homes this player may have: the highest granted numbered node, or the configured
     * default if none is granted. {@code -1} means unlimited.
     */
    public static int homeLimit(ServerPlayer player) {
        if (has(player, HOME_LIMIT_UNLIMITED)) return -1;
        int best = -1;
        for (Map.Entry<Integer, PermissionNode<Boolean>> entry : HOME_LIMITS.entrySet()) {
            if (entry.getKey() > best && has(player, entry.getValue())) {
                best = entry.getKey();
            }
        }
        return best >= 0 ? best : StandardsConfig.DEFAULT_HOME_LIMIT.get();
    }

    private StandardsPermissions() {}
}

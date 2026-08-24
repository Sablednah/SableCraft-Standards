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

    /** Founding and running a lightweight group. Everyone, like homes — it is a social feature. */
    public static final PermissionNode<Boolean> GROUP = node("group", Default.EVERYONE);

    // --- teleport bypasses ---
    public static final PermissionNode<Boolean> TP_INSTANT = node("teleport.instant", Default.OPS);
    public static final PermissionNode<Boolean> TP_NO_COOLDOWN = node("teleport.nocooldown", Default.OPS);

    // --- admin ---
    public static final PermissionNode<Boolean> ADMIN = node("admin", Default.OPS);

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
        // Per-kit nodes, for the kits that exist right now. Kits made later are covered by the
        // plain 'standards.kit' node until a restart — see canUseKit.
        KIT_NODES.clear();
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (String kit : Kits.get(server).names()) {
                PermissionNode<Boolean> node = new PermissionNode<>(
                        Standards.MODID, "kit." + kit.toLowerCase(java.util.Locale.ROOT),
                        PermissionTypes.BOOLEAN, (player, uuid, context) -> Boolean.TRUE);
                KIT_NODES.put(kit.toLowerCase(java.util.Locale.ROOT), node);
                event.addNodes(node);
            }
        }

        Standards.LOGGER.info("Registered {} permission nodes ({} home limits, {} kits)",
                FIXED.size() + HOME_LIMITS.size() + KIT_NODES.size(),
                HOME_LIMITS.size(), KIT_NODES.size());
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
    public static java.util.function.Predicate<CommandSourceStack> require(PermissionNode<Boolean> node) {
        return source -> source.getEntity() instanceof ServerPlayer player
                ? has(player, node)
                : Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    /**
     * May this player claim this kit?
     *
     * <p>Open when no node exists, which is the case for any kit created since the last restart.
     * That is the deliberate half of the trade: NeoForge wants permission nodes enumerated up
     * front, and the alternative — a brand-new kit silently claimable by nobody — reads as the
     * kit system being broken.</p>
     */
    public static boolean canUseKit(ServerPlayer player, String kit) {
        if (!has(player, KIT)) return false;
        PermissionNode<Boolean> node = KIT_NODES.get(kit.toLowerCase(java.util.Locale.ROOT));
        return node == null || has(player, node);
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

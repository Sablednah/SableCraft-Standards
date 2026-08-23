package com.sablednah.standards.neoforge.commands;

import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.PlayerState;
import com.sablednah.standards.neoforge.SafeLoc;
import com.sablednah.standards.neoforge.StandardsAttachments;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import com.sablednah.standards.StandardsConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Getting about: {@code /top}, {@code /jump}, {@code /back}.
 *
 * <p>{@code /top} is the command whose absence from FTB Essentials started this mod, and it is
 * implemented by scanning rather than by asking the heightmap — see {@link #top}.</p>
 */
public final class MoveCommands {

    /** How far {@code /jump} will look for something to land on. */
    private static final double JUMP_RANGE = 192.0D;

    /** @param name the literal to build under, so aliases are real trees rather than redirects */
    public static LiteralArgumentBuilder<CommandSourceStack> top(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.TOP))
                .executes(MoveCommands::top);
    }

    /** @param name the literal to build under, so aliases are real trees rather than redirects */
    public static LiteralArgumentBuilder<CommandSourceStack> bottom(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.BOTTOM))
                .executes(MoveCommands::bottom);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> jump(String name) {
        return Commands.literal(name)
                .requires(StandardsPermissions.require(StandardsPermissions.JUMP))
                .executes(MoveCommands::jump);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> back() {
        return Commands.literal("back")
                .requires(StandardsPermissions.require(StandardsPermissions.BACK))
                .executes(ctx -> back(ctx, 1))
                .then(Commands.argument("steps", IntegerArgumentType.integer(1))
                        .executes(ctx -> back(ctx, IntegerArgumentType.getInteger(ctx, "steps"))));
    }

    /**
     * Straight up to the first place it is safe to stand.
     *
     * <p><b>Scanned, not read off the heightmap</b>, which is the difference between a command
     * that works everywhere and one that works in the overworld. A heightmap answers "the highest
     * non-air block in this column", which in the Nether is the bedrock roof and in a cave means
     * the surface far above your head — so the classic {@code /top} either kills you or takes you
     * somewhere you did not mean to go. Scanning upward from where you stand finds the first
     * <em>floor above you</em>, which is what everyone actually wants: out of the cave, onto the
     * roof, up through the ceiling.</p>
     *
     * <p><b>But the scan stops at the dimension's logical height, not its build height</b>, and
     * the two are different in exactly the place it matters. The Nether's blocks go up to y255
     * while its <em>ceiling</em> is the bedrock at y127, so the first safe floor above a player
     * standing underneath it is the top of that bedrock — a flat, featureless plane you cannot
     * easily get down from and were certainly not asking for. Reported from the Nether: "/top
     * took me above bedrock, because 16 blocks up the next empty space was the ceiling."</p>
     *
     * <p>{@link DimensionType#logicalHeight()} is the game's own answer to "where does this
     * dimension actually end" — it is what caps portals and respawn anchors — so it is the right
     * ceiling to respect here. In the overworld it equals the build height and nothing changes.</p>
     */
    private static int top(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos from = player.blockPosition();

        int ceiling = ceilingFor(level);
        boolean stranded = stranded(level, from);
        for (int y = from.getY() + 1; y <= ceiling; y++) {
            BlockPos candidate = new BlockPos(from.getX(), y, from.getZ());
            if (!stranded && isBarrier(level.getBlockState(candidate))) {
                // The world's own roof is not somebody's protection, and saying it is reads as
                // nonsense in the Nether — where hitting it is the common case rather than the
                // exception. The roof is the bedrock slab immediately under the logical ceiling,
                // so anything in the last few blocks of it is the world, not a player.
                boolean worldRoof = isWorldEdge(candidate.getY(), level.getMinY(), ceiling,
                        level.dimensionType().hasCeiling());
                Feedback.chat(player, worldRoof
                        ? Lang.get("msg.tp.top_ceiling")
                        : Lang.fmt("msg.tp.blocked",
                                "block", level.getBlockState(candidate).getBlock()
                                        .getName().getString()));
                return 0;
            }
            if (SafeLoc.isSafe(level, candidate)) {
                return go(ctx, player, new Waypoint(level.dimension(),
                        candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                        player.getYRot(), player.getXRot()),
                        Lang.fmt("msg.tp.top", "y", candidate.getY() - from.getY()));
            }
        }
        Feedback.chat(player, Lang.get("msg.tp.top_already"));
        return 0;
    }

    /**
     * Whether this block stops {@code /top} dead.
     *
     * <p>A bedrock or barrier box around a build is protection somebody put there on purpose, and
     * scanning through it lands the player on the roof of a base they were being kept out of. The
     * ids are compared as written, so an id naming a block from an absent mod simply never
     * matches — no parsing, nothing to get wrong in the config.</p>
     */
    private static boolean isBarrier(BlockState state) {
        java.util.List<? extends String> configured = StandardsConfig.TOP_BARRIERS.get();
        if (configured.isEmpty()) {
            return false;
        }
        return matchesAny(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), configured);
    }

    /** Pure, so the self-test can prove the matching without a world. */
    public static boolean matchesAny(String blockId, java.util.List<? extends String> configured) {
        for (String candidate : configured) {
            if (blockId.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the player is somewhere the world does not really extend to: falling through the
     * void, or standing on the Nether roof above the dimension's own ceiling.
     *
     * <p>Barriers are protection, but only for people inside the world being protected. Someone
     * stranded outside it needs a way back more than the box needs defending — and the failure
     * mode of getting this wrong is that the command meant to rescue them is the thing that
     * traps them. {@code /bottom} off the Nether roof is exactly that case: the first block
     * below your feet is the bedrock you are standing on.</p>
     */
    private static boolean stranded(ServerLevel level, BlockPos from) {
        return from.getY() > ceilingFor(level) || overVoid(level, from);
    }

    /** Nothing solid anywhere beneath this column. */
    private static boolean overVoid(ServerLevel level, BlockPos from) {
        for (int y = from.getY() - 1; y >= level.getMinY(); y--) {
            BlockPos below = new BlockPos(from.getX(), y, from.getZ());
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * How thick the world's own bedrock shell is, at either end. Both the Nether roof and every
     * dimension's floor are patchy across roughly four layers, so a barrier found within this of
     * the edge is the world rather than anything a player built.
     */
    private static final int WORLD_SHELL = 5;

    /**
     * Whether a blocking block at this height is the world's own edge rather than someone's box.
     *
     * <p>It matters because the message is the whole point of stopping. "Blocks like that are
     * usually there on purpose" is exactly right for a bedrock box and nonsense for the Nether
     * roof or the bottom of the world, and those are the <em>common</em> cases — {@code /top} in
     * the Nether reaches the ceiling constantly, and {@code /bottom} while stood on bedrock hits
     * the floor on its very first step.</p>
     *
     * <p>Pure, so the self-test can prove it without a world.</p>
     */
    public static boolean isWorldEdge(int y, int minY, int ceiling, boolean hasCeiling) {
        return (hasCeiling && y >= ceiling - WORLD_SHELL) || y <= minY + WORLD_SHELL;
    }

    /** The highest Y {@code /top} may land on in this world. See {@link #top}. */
    private static int ceilingFor(ServerLevel level) {
        return highestStandableY(level.getMinY(), level.getMaxY(),
                level.dimensionType().logicalHeight());
    }

    /**
     * Pure so the self-test can prove it without a Nether loaded.
     *
     * <p>Never returns more than the build height — a dimension is free to declare a logical
     * height larger than the space it actually has, and trusting it blindly would scan into
     * nothing.</p>
     */
    public static int highestStandableY(int minY, int maxY, int logicalHeight) {
        return Math.min(maxY, minY + logicalHeight - 1);
    }

    /**
     * Straight down to the lowest place it is safe to stand — the mirror of {@link #top}.
     *
     * <p>Scanned downward for the same reason {@code /top} scans upward: it finds the floor of
     * whatever you are standing over, which is the cave, the mineshaft or the bedrock layer you
     * were actually asking about.</p>
     *
     * <p>It respects the same barriers, because the hole is symmetric — a bedrock vault with air
     * in it is as reachable from above as a bedrock box is from below. Landing <em>on</em>
     * bedrock is still fine: the check only fires when the scan would pass <em>through</em> it,
     * so {@code /bottom} still puts you on the world floor.</p>
     */
    private static int bottom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos from = player.blockPosition();

        // The same barrier rule as /top, for the same reason: a bedrock vault with air inside is
        // reachable from above, and "/bottom into somebody's base" is the identical hole.
        boolean stranded = stranded(level, from);
        for (int y = from.getY() - 1; y >= level.getMinY(); y--) {
            BlockPos candidate = new BlockPos(from.getX(), y, from.getZ());
            if (!stranded && isBarrier(level.getBlockState(candidate))) {
                // Standing on the world floor and asking to go down hits bedrock on the first
                // step, and calling that somebody's protection is nonsense. Same rule as /top.
                boolean worldFloor = isWorldEdge(candidate.getY(), level.getMinY(),
                        ceilingFor(level), level.dimensionType().hasCeiling());
                Feedback.chat(player, worldFloor
                        ? Lang.get("msg.tp.bottom_already")
                        : Lang.fmt("msg.tp.blocked",
                                "block", level.getBlockState(candidate).getBlock()
                                        .getName().getString()));
                return 0;
            }
            if (SafeLoc.isSafe(level, candidate)) {
                return go(ctx, player, new Waypoint(level.dimension(),
                        candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                        player.getYRot(), player.getXRot()),
                        Lang.fmt("msg.tp.bottom", "y", from.getY() - candidate.getY()));
            }
        }
        Feedback.chat(player, Lang.get("msg.tp.bottom_already"));
        return 0;
    }

    /**
     * To wherever you are looking. Lands on <em>top</em> of the block hit, scanning up from it if
     * that spot is occupied — aiming at the side of a cliff should put you on the clifftop, not
     * inside the cliff.
     */
    private static int jump(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();

        HitResult hit = player.pick(JUMP_RANGE, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            Feedback.chat(player, Lang.get("msg.tp.jump_nothing"));
            return 0;
        }
        BlockPos landing = ((BlockHitResult) hit).getBlockPos().above();
        Optional<BlockPos> safe = SafeLoc.find(level, landing);
        if (safe.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.tp.unsafe"));
            return 0;
        }
        BlockPos target = safe.get();
        int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(target)));
        return go(ctx, player, new Waypoint(level.dimension(),
                target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                player.getYRot(), player.getXRot()),
                Lang.fmt("msg.tp.jump_done", "blocks", distance));
    }

    /**
     * Back up the trail. {@code /back} is the previous place, {@code /back 2} the one before that.
     *
     * <p>The step is only consumed once the teleport is actually accepted — a {@code /back} that
     * bounced off a cooldown must not have eaten the destination, or the player has lost the very
     * thing they were trying to get to.</p>
     */
    private static int back(CommandContext<CommandSourceStack> ctx, int steps) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerState state = StandardsAttachments.of(player);

        // They died since their last /back, and the death site was deliberately not stored. Say so
        // once, and clear it — a second /back then does the ordinary thing. Refusing silently, or
        // teleporting them to a warp they used ten minutes ago, both read as the command being
        // broken; only one of them also moves them somewhere they never asked to go.
        if (steps == 1 && state.deathNotRecorded()) {
            state.setDeathNotRecorded(false);
            Feedback.chat(player, Lang.get("msg.tp.back_death_disabled"));
            return 0;
        }

        Optional<Waypoint> destination = state.peekBack(steps);
        if (destination.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.tp.back_none"));
            return 0;
        }
        boolean wasDeath = steps == 1 && state.backWasDeath();

        // recordBack = false: the trail must not grow an entry every time it is walked, or /back
        // becomes a two-step loop between the last two places you stood.
        Teleports.Attempt attempt = Teleports.request(player, destination.get(), false,
                Lang.fmt(wasDeath ? "msg.tp.back_death" : "msg.tp.back_done",
                        "place", destination.get().describe()));
        if (!report(player, attempt)) {
            return 0;
        }
        state.popBack(steps);
        return 1;
    }

    /** Ask for a teleport and say the right thing about the answer. */
    private static int go(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
            Waypoint destination, String successMessage) {
        Teleports.Attempt attempt = Teleports.request(player, destination, true, successMessage);
        if (!report(player, attempt)) {
            return 0;
        }
        return 1;
    }

    /**
     * Turn an {@link Teleports.Attempt} into words.
     *
     * @return true if the teleport happened or was queued
     */
    static boolean report(ServerPlayer player, Teleports.Attempt attempt) {
        if (attempt.accepted()) {
            if (attempt.queued()) {
                Feedback.chat(player, Lang.fmt("msg.tp.warmup", "sec", attempt.secondsLeft()));
            }
            return true;
        }
        Feedback.chat(player, switch (attempt.refusal()) {
            case COOLDOWN -> Lang.fmt("msg.tp.cooldown", "sec", attempt.secondsLeft());
            case UNSAFE -> Lang.get("msg.tp.unsafe");
            case NO_WORLD -> Lang.get("msg.tp.no_world");
            case NONE -> Lang.get("msg.tp.unsafe");
        });
        return false;
    }

    private MoveCommands() {}
}

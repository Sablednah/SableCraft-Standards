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
import net.minecraft.server.level.ServerLevel;
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

    public static LiteralArgumentBuilder<CommandSourceStack> top() {
        return Commands.literal("top")
                .requires(StandardsPermissions.require(StandardsPermissions.TOP))
                .executes(MoveCommands::top);
    }

    /** @param name the literal to build under, so aliases are real trees rather than redirects */
    public static LiteralArgumentBuilder<CommandSourceStack> bottom() {
        return Commands.literal("bottom")
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
     */
    private static int top(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos from = player.blockPosition();

        for (int y = from.getY() + 1; y <= level.getMaxY(); y++) {
            BlockPos candidate = new BlockPos(from.getX(), y, from.getZ());
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
     * Straight down to the lowest place it is safe to stand — the mirror of {@link #top}.
     *
     * <p>Scanned downward for the same reason {@code /top} scans upward: it finds the floor of
     * whatever you are standing over, which is the cave, the mineshaft or the bedrock layer you
     * were actually asking about.</p>
     */
    private static int bottom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos from = player.blockPosition();

        for (int y = from.getY() - 1; y >= level.getMinY(); y--) {
            BlockPos candidate = new BlockPos(from.getX(), y, from.getZ());
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

        Optional<Waypoint> destination = state.peekBack(steps);
        if (destination.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.tp.back_none"));
            return 0;
        }
        boolean wasDeath = steps == 1 && state.backWasDeath();

        // recordBack = false: the trail must not grow an entry every time it is walked, or /back
        // becomes a two-step loop between the last two places you stood.
        Teleports.Attempt attempt = Teleports.request(player, destination.get(), false);
        if (!report(player, attempt)) {
            return 0;
        }
        state.popBack(steps);
        if (!attempt.queued()) {
            Feedback.chat(player, Lang.fmt(wasDeath ? "msg.tp.back_death" : "msg.tp.back_done",
                    "place", destination.get().describe()));
        }
        return 1;
    }

    /** Ask for a teleport and say the right thing about the answer. */
    private static int go(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
            Waypoint destination, String successMessage) {
        Teleports.Attempt attempt = Teleports.request(player, destination, true);
        if (!report(player, attempt)) {
            return 0;
        }
        if (!attempt.queued()) {
            Feedback.chat(player, successMessage);
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

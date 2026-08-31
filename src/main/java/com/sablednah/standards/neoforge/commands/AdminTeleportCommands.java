package com.sablednah.standards.neoforge.commands;

import java.util.Collection;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;
import com.sablednah.standards.neoforge.Teleports;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /tp}, {@code /tphere} and {@code /tppos} — the admin teleports.
 *
 * <pre>
 *   /tp &lt;player&gt;                    you, to them
 *   /tp &lt;player&gt; &lt;player&gt;           them, to the second one
 *   /tphere &lt;players&gt;               them, to you
 *   /tppos &lt;x&gt; &lt;y&gt; &lt;z&gt; [dimension] you, to a coordinate
 * </pre>
 *
 * <h2>Why, when vanilla has {@code /tp}</h2>
 *
 * <p>The catalogue was honest that this is the weakest entry on the list, and the two reasons it
 * gave are the only two:</p>
 *
 * <ul>
 * <li><b>Permission consistency.</b> Vanilla's {@code /tp} is gated on op level, full stop. Ours
 *     is a node, so a server can hand a builder {@code standards.tp} without also handing them
 *     {@code /stop} — the same argument that put every other gate in this mod on a node.</li>
 * <li><b>{@code /tppos} with a dimension.</b> Vanilla makes you {@code /execute in} first, which
 *     is two commands and a syntax nobody remembers under pressure.</li>
 * </ul>
 *
 * <p><b>Vanilla's {@code /tp} and {@code /teleport} are left alone.</b> Registering our own
 * {@code tp} literal merges onto vanilla's node, and brigadier tries children in insertion order,
 * so the outcome would depend on mod load order — the exact trap decision 10 documents. Ours are
 * therefore reached through the routing below only where vanilla has no matching child, and the
 * self-test asserts vanilla's own {@code /tp @s ~ ~ ~} still parses.</p>
 *
 * <h2>They go through {@link Teleports}, not straight to {@code teleportTo}</h2>
 *
 * <p>So they get the safe-landing search, the {@code /back} trail and the warmup — an admin who
 * teleports into the void wanted the same protection everybody else gets. Warmup and cooldown are
 * skipped for anyone holding {@code standards.teleport.instant}, which ops have by default, so in
 * practice these are immediate.</p>
 */
public final class AdminTeleportCommands {

    /**
     * {@code /tpx} — ours, at a name vanilla does not use.
     *
     * <p>Named rather than merged for the reason in the class note: merging a {@code tp} literal
     * onto vanilla's puts the outcome at the mercy of registration order. {@code /tpx} is
     * unambiguous, and a server that wants the short name can alias it.</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> tp(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.TP))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(AdminTeleportCommands::toPlayer)
                        .then(Commands.argument("destination", EntityArgument.player())
                                .requires(StandardsPermissions.require(
                                        StandardsPermissions.TP_OTHERS))
                                .executes(AdminTeleportCommands::playerToPlayer)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tpHere(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.TP_OTHERS))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(AdminTeleportCommands::here));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tpPos(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.TP))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> toPos(ctx, null))
                        // The half vanilla makes you write '/execute in' for.
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> toPos(ctx,
                                        DimensionArgument.getDimension(ctx, "dimension")))));
    }

    private static int toPlayer(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        return go(ctx, self, Waypoint.of(target),
                Lang.fmt("msg.tp.to_player", "player", target.getName().getString()));
    }

    private static int playerToPlayer(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer moved = EntityArgument.getPlayer(ctx, "target");
        ServerPlayer destination = EntityArgument.getPlayer(ctx, "destination");
        return go(ctx, moved, Waypoint.of(destination),
                Lang.fmt("msg.tp.moved", "player", moved.getName().getString(),
                        "to", destination.getName().getString()));
    }

    private static int here(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        Waypoint destination = Waypoint.of(self);
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int moved = 0;
        for (ServerPlayer target : targets) {
            // Each one is its own attempt: one player standing in a warmup must not stop the rest
            // arriving, and a selector that half-worked is worse than one that reports what it did.
            if (go(ctx, target, destination,
                    Lang.fmt("msg.tp.summoned", "by", ctx.getSource().getTextName())) > 0) {
                moved++;
            }
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.tp.summoned_count",
                "count", String.valueOf(moved)), true);
        return moved;
    }

    private static int toPos(CommandContext<CommandSourceStack> ctx, ServerLevel dimension)
            throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
        ServerLevel level = dimension != null ? dimension : (ServerLevel) self.level();
        Waypoint where = new Waypoint(level.dimension(), pos.x, pos.y, pos.z,
                self.getYRot(), self.getXRot());
        // describe() already carries the dimension and the rounded coordinates, so one
        // placeholder rather than four that could disagree with each other.
        return go(ctx, self, where, Lang.fmt("msg.tp.to_pos", "where", where.describe()));
    }

    /** One road out, so every admin teleport gets the safe landing and the /back trail. */
    private static int go(CommandContext<CommandSourceStack> ctx, ServerPlayer who,
            Waypoint where, String message) {
        Teleports.Attempt attempt = Teleports.request(who, where, true, message);
        return MoveCommands.report(who, attempt) ? 1 : 0;
    }

    private AdminTeleportCommands() {}
}

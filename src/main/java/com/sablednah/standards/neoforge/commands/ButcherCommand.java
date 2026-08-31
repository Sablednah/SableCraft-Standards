package com.sablednah.standards.neoforge.commands;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * {@code /butcher} — clear mobs in a radius, when the server is dying under them.
 *
 * <pre>
 *   /butcher              everything hostile within the default radius
 *   /butcher &lt;radius&gt;     …within that many blocks
 *   /butcher &lt;radius&gt; all  …including passive mobs
 * </pre>
 *
 * <h2>What it will not kill, and why that list is the whole command</h2>
 *
 * <p>A radius kill is a lag tool and a griefing tool wearing the same coat, and the difference is
 * entirely in what it skips. Nothing here touches:</p>
 *
 * <ul>
 * <li><b>Players.</b> Obviously, but it has to be said in code as well as in a sentence.</li>
 * <li><b>Tamed animals.</b> Somebody's dog is not lag. This is the one that turns a helpful
 *     command into a grief report, and it is the reason {@code /killall} has a bad name.</li>
 * <li><b>Named mobs.</b> A name tag is a player saying "this one is mine" in the only vocabulary
 *     vanilla gives them, and it costs an anvil and a tag to say it.</li>
 * <li><b>Passive mobs</b>, unless {@code all} is asked for — a cleared farm is somebody's evening.</li>
 * </ul>
 *
 * <p>The default is hostile mobs only, because that is what a server drowning in entities actually
 * needs cleared, and it is the version that cannot be regretted.</p>
 */
public final class ButcherCommand {

    private static final int DEFAULT_RADIUS = 64;

    public static LiteralArgumentBuilder<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.BUTCHER))
                .executes(ctx -> butcher(ctx, DEFAULT_RADIUS, false))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                        .executes(ctx -> butcher(ctx,
                                IntegerArgumentType.getInteger(ctx, "radius"), false))
                        .then(Commands.literal("all")
                                .executes(ctx -> butcher(ctx,
                                        IntegerArgumentType.getInteger(ctx, "radius"), true))));
    }

    private static int butcher(CommandContext<CommandSourceStack> ctx, int radius, boolean passive)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(radius);

        List<Entity> doomed = level.getEntities(player, box, e -> clearable(e, passive));
        for (Entity entity : doomed) {
            entity.discard();
        }

        Feedback.reply(ctx.getSource(), doomed.isEmpty()
                ? Lang.fmt("msg.butcher.none", "radius", String.valueOf(radius))
                : Lang.fmt("msg.butcher.done",
                        "count", String.valueOf(doomed.size()),
                        "radius", String.valueOf(radius)), true);
        return doomed.size();
    }

    /**
     * The predicate that decides whether this command is useful or a grief report.
     *
     * <p>Ordered so the refusals come first and read as a list of promises.</p>
     */
    private static boolean clearable(Entity entity, boolean includePassive) {
        if (entity instanceof Player) {
            return false;
        }
        if (!(entity instanceof Mob mob)) {
            // Item frames, armour stands, boats, dropped items, paintings — all somebody's build
            // or somebody's belongings. A mob clear clears mobs.
            return false;
        }
        if (mob.hasCustomName()) {
            return false;
        }
        if (mob instanceof TamableAnimal tame && tame.isTame()) {
            return false;
        }
        if (mob.isPersistenceRequired()) {
            // Vanilla's own flag for "this one was placed deliberately and must not despawn" —
            // a spawner-egg mob, a villager moved into a build. Reusing it means we agree with
            // the game about what counts as furniture.
            return false;
        }
        return includePassive || mob.getType().getCategory().isFriendly() == false;
    }

    private ButcherCommand() {}
}

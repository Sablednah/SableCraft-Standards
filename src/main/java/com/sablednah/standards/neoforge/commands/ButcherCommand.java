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
 *   /butcher                    everything hostile within the default radius
 *   /butcher &lt;radius&gt;           …within that many blocks
 *   /butcher &lt;radius&gt; all       …including passive mobs
 *   /butcher &lt;radius&gt; force     …ignoring named and persistent mobs too
 *   /butcher &lt;radius&gt; all force …both
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
 *
 * <h2>{@code force}, and the assumption that made it necessary</h2>
 *
 * <p>Two of the skips above are guesses about intent, and on a modded server both guess wrong.
 * ZombieMod gives every zombie a <b>custom name</b> carrying its genus, with the nameplate
 * deliberately hidden, and marks some <b>persistence-required</b>. So a {@code /butcher} on a
 * server drowning in ZombieMod zombies cleared the skeletons and left the horde — found the first
 * time it was pointed at a real modpack.</p>
 *
 * <p>The reasoning was wrong rather than the code. A name tag <em>is</em> a player saying "this one
 * is mine" — but <b>mods use custom names as a data channel</b>, a label that happens to live in
 * the same field, and there is no reliable way to tell the two apart. Vanilla's persistence flag
 * has the same problem: it means "do not despawn", which a mod sets for its own bookkeeping.</p>
 *
 * <p>So {@code force} drops those two guesses and keeps the two certainties — never a player,
 * never a tamed animal, never anything that is not a mob. It is the honest shape for this: the
 * safe default stays safe, and the escape hatch is named for what it does rather than pretending
 * the heuristics were right.</p>
 */
public final class ButcherCommand {

    private static final int DEFAULT_RADIUS = 64;

    public static LiteralArgumentBuilder<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.BUTCHER))
                .executes(ctx -> butcher(ctx, DEFAULT_RADIUS, false, false))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                        .executes(ctx -> butcher(ctx, radius(ctx), false, false))
                        .then(Commands.literal("all")
                                .executes(ctx -> butcher(ctx, radius(ctx), true, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> butcher(ctx, radius(ctx), true, true))))
                        .then(Commands.literal("force")
                                .executes(ctx -> butcher(ctx, radius(ctx), false, true))));
    }

    private static int radius(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "radius");
    }

    private static int butcher(CommandContext<CommandSourceStack> ctx, int radius, boolean passive,
            boolean force) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(radius);

        List<Entity> doomed = level.getEntities(player, box, e -> clearable(e, passive, force));
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
    private static boolean clearable(Entity entity, boolean includePassive, boolean force) {
        if (entity instanceof Player) {
            return false;
        }
        if (!(entity instanceof Mob mob)) {
            // Item frames, armour stands, boats, dropped items, paintings — all somebody's build
            // or somebody's belongings. A mob clear clears mobs. Never overridden, not even by
            // force: nothing here is a lag problem and all of it is somebody's work.
            return false;
        }
        if (mob instanceof TamableAnimal tame && tame.isTame()) {
            // Somebody's dog, ever. This is the one that turns a helpful command into a grief
            // report, so force does not reach it either.
            return false;
        }
        if (!force) {
            if (mob.hasCustomName()) {
                return false;
            }
            if (mob.isPersistenceRequired()) {
                return false;
            }
        }
        return includePassive || !mob.getType().getCategory().isFriendly();
    }

    private ButcherCommand() {}
}

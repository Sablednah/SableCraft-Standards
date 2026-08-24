package com.sablednah.standards.neoforge.commands;

import java.util.Collection;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /smite} — the one survivor of EssentialsX's joke commands.
 *
 * <p>It stays because it is not actually a joke: it is
 * {@code /execute at <player> run summon lightning_bolt} in one word, op-gated, and it is exactly
 * the kind of thing a gamemaster narrating a scene reaches for. The rest of that family
 * ({@code /kittycannon}, {@code /nuke}, {@code /antioch}) can be a LegendQuest skill running a
 * command, which is where server-specific silliness belongs.</p>
 *
 * <p>Bare {@code /smite} strikes wherever you are looking, so it works as a piece of theatre
 * rather than only as a punishment.</p>
 */
public final class GameMasterCommands {

    private static final double LOOK_RANGE = 192.0D;

    /**
     * {@code /smite [players]} — lightning on a target, or wherever you are looking.
     *
     * <p><b>The target is deliberately told nothing.</b> Every switch in this mod names whoever
     * changed it, because flight or invulnerability appearing from nowhere reads as a glitch. A
     * smite is the opposite: entirely self-evident, with a sound, a flash and a large hole in
     * your health bar. Naming the caster would only turn an act of God into an admin with a
     * command, and that joke is most of why this exists.</p>
     *
     * <p>Noticed during testing as an inconsistency with {@code /fly} and {@code /heal}, and kept
     * on purpose. If {@code /smite} ever becomes a moderation tool rather than a toy, revisit
     * this — anything staff do to a player in anger should be accountable.</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> smite() {
        return Commands.literal("smite")
                .requires(StandardsPermissions.require(StandardsPermissions.SMITE))
                .executes(GameMasterCommands::smiteWhereLooking)
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(GameMasterCommands::smitePlayers));
    }

    private static int smitePlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        ServerPlayer cause = ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        String lastName = "";
        int struck = 0;
        for (ServerPlayer target : targets) {
            strike(target.level(), target.position(), cause);
            lastName = target.getName().getString();
            struck++;
        }
        if (struck == 1) {
            final String name = lastName;
            Feedback.reply(ctx.getSource(), Lang.fmt("msg.smite.done", "player", name), true);
        } else if (struck > 1) {
            final int count = struck;
            Feedback.reply(ctx.getSource(), Lang.fmt("msg.smite.many", "count", count), true);
        }
        return struck;
    }

    private static int smiteWhereLooking(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HitResult hit = player.pick(LOOK_RANGE, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            Feedback.chat(player, Lang.get("msg.smite.nothing"));
            return 0;
        }
        strike(player.level(), Vec3.atBottomCenterOf(((BlockHitResult) hit).getBlockPos().above()), player);
        Feedback.reply(ctx.getSource(), Lang.get("msg.smite.here"), true);
        return 1;
    }

    /**
     * Real lightning, not the visual-only kind: setting fire and dealing damage is the whole
     * point, and a purely cosmetic smite would be a worse joke than the ones we dropped.
     */
    private static void strike(ServerLevel level, Vec3 at, ServerPlayer cause) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(at);
        bolt.setCause(cause);
        level.addFreshEntity(bolt);
    }

    private GameMasterCommands() {}
}

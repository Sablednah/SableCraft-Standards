package com.sablednah.standards.neoforge.commands;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsAttachments;
import com.sablednah.standards.neoforge.StandardsEvents;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /speed [walk|fly] <multiplier> [players]}.
 *
 * <p>A multiplier of vanilla's own speed rather than a raw value, because vanilla's numbers
 * ({@code 0.1} walking, {@code 0.05} flying) are meaningless to anyone typing this. {@code 1} is
 * normal, {@code 2} is twice as fast, and the ceiling is config — above about 10 the client's
 * movement prediction starts disagreeing with the server and players rubber-band.</p>
 *
 * <p>Bare {@code /speed <n>} sets whichever mode the player is currently in, which is what they
 * meant: someone flying who types {@code /speed 3} does not want their walking speed changed.</p>
 *
 * <p>Persisted like flight and god, and re-applied by {@code applySwitches} — vanilla rebuilds
 * ability flags on respawn and game-mode change, so a speed that is not re-applied silently
 * resets the first time you die.</p>
 */
public final class SpeedCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("speed")
                .requires(StandardsPermissions.require(StandardsPermissions.SPEED))
                .then(amount(null));
        root.then(Commands.literal("walk").then(amount(Boolean.FALSE)));
        root.then(Commands.literal("fly").then(amount(Boolean.TRUE)));
        root.then(Commands.literal("reset").executes(ctx -> reset(ctx)));
        return root;
    }

    /** @param flying true for fly speed, false for walk, null for "whichever they are doing" */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Float> amount(
            Boolean flying) {
        return Commands.argument("multiplier", FloatArgumentType.floatArg(0.1F))
                .executes(ctx -> apply(ctx, flying, false))
                .then(Commands.argument("players", EntityArgument.players())
                        .requires(StandardsPermissions.require(StandardsPermissions.SPEED_OTHERS))
                        .executes(ctx -> apply(ctx, flying, true)));
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, Boolean flying, boolean others)
            throws CommandSyntaxException {
        float multiplier = FloatArgumentType.getFloat(ctx, "multiplier");
        float max = StandardsConfig.MAX_SPEED.get().floatValue();
        if (multiplier > max) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.speed.too_fast", "max", trim(max)));
            return 0;
        }

        var targets = others
                ? EntityArgument.getPlayers(ctx, "players")
                : java.util.List.of(ctx.getSource().getPlayerOrException());

        for (ServerPlayer target : targets) {
            boolean toFlying = flying != null ? flying : target.getAbilities().flying;
            var state = StandardsAttachments.of(target);
            if (toFlying) {
                state.setFlySpeed(multiplier);
            } else {
                state.setWalkSpeed(multiplier);
            }
            StandardsEvents.applySwitches(target);
            Feedback.chat(target, Lang.fmt("msg.speed.set",
                    "what", Lang.get(toFlying ? "msg.speed.flying" : "msg.speed.walking"),
                    "amount", trim(multiplier)));
        }
        return targets.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var state = StandardsAttachments.of(player);
        state.setWalkSpeed(1.0F);
        state.setFlySpeed(1.0F);
        StandardsEvents.applySwitches(player);
        Feedback.chat(player, Lang.get("msg.speed.reset"));
        return 1;
    }

    private static String trim(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    private SpeedCommand() {}
}

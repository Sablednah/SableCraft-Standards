package com.sablednah.standards.neoforge.commands;

import java.util.function.Consumer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.api.Stations;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;

/**
 * Portable workstations: {@code /craft}, {@code /anvil}, {@code /grindstone},
 * {@code /enderchest}, {@code /trashcan}.
 *
 * <p><b>All of them are permission-denied by default</b>, which is the whole design rather than an
 * oversight. A workbench you can open anywhere is not a utility, it is an advantage — so it is
 * something a player is <em>granted</em>: a builder rank gets {@code standards.craft}, a
 * blacksmith class gets {@code standards.anvil}. LuckPerms does that per group, and a LegendQuest
 * skill does it per class via {@link Stations}, which skips the permission check because the skill
 * is itself the authority.</p>
 */
public final class StationCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> station(
            String name, PermissionNode<Boolean> node, Consumer<ServerPlayer> open) {
        return Commands.literal(name)
                // requireOr, not require: these nodes are Default.NOBODY, so on a server with no
                // permissions mod they can never be granted and the command simply never exists.
                // stationAccess lets such a server say who they are for. See requireOr.
                .requires(StandardsPermissions.requireOr(node,
                        com.sablednah.standards.StandardsConfig.STATION_ACCESS::get))
                .executes(ctx -> run(ctx, open));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Consumer<ServerPlayer> open)
            throws CommandSyntaxException {
        open.accept(ctx.getSource().getPlayerOrException());
        return 1;
    }

    /** Registers the lot, so adding a station is one line here and one permission node. */
    public static void registerAll(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(station("craft", StandardsPermissions.CRAFT, Stations::openCrafting));
        dispatcher.register(station("workbench", StandardsPermissions.CRAFT, Stations::openCrafting));
        dispatcher.register(station("anvil", StandardsPermissions.ANVIL, Stations::openAnvil));
        dispatcher.register(station("grindstone", StandardsPermissions.GRINDSTONE, Stations::openGrindstone));
        dispatcher.register(station("enderchest", StandardsPermissions.ENDERCHEST, Stations::openEnderChest));
        dispatcher.register(station("ec", StandardsPermissions.ENDERCHEST, Stations::openEnderChest));
        dispatcher.register(station("trashcan", StandardsPermissions.TRASH, Stations::openTrash));
        dispatcher.register(station("disposal", StandardsPermissions.TRASH, Stations::openTrash));
    }

    private StationCommands() {}
}

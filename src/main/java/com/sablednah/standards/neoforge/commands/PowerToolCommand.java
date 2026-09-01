package com.sablednah.standards.neoforge.commands;

import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.standards.Standards;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsData;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /powertool} — bind a command to the item you are holding, and run it by right-clicking.
 *
 * <pre>
 *   /powertool jump        right-clicking this item runs /jump
 *   /powertool clear       unbind this item
 *   /powertool clearall    unbind everything
 *   /powertool list        what you have bound
 * </pre>
 *
 * <h2>This is an op tool. LegendQuest's {@code /bind} is a game tool</h2>
 *
 * <p>The two look alike and are not the same thing, which is why both exist. {@code /bind} gives a
 * <em>player</em> an ability on an item as part of playing — earned, balanced, part of the world.
 * This binds an arbitrary command to an item for staff convenience: a stick that runs {@code /jump}
 * so you can get about a build without typing.</p>
 *
 * <p>Because it runs an arbitrary command it is op-gated, and it stays that way. But note the
 * command is dispatched <b>as the holder, with the holder's permissions</b> — so a bound tool is
 * not a way to hand somebody an ability they do not have. If they could not type it, the stick will
 * not run it either.</p>
 *
 * <h2>Main hand only</h2>
 *
 * <p>A bound tool fires from the <b>main hand</b> and nowhere else. Put it in your off-hand and it
 * does nothing — confirmed in testing, and deliberate rather than an oversight.</p>
 *
 * <p>Two reasons. Vanilla raises the interact event for <em>both</em> hands on a single click, so
 * something has to be ignored or every bound {@code /jump} takes you twice as far as you meant.
 * And the off-hand is where a shield, a torch or a map lives: a command firing from it would go off
 * during ordinary play, which is exactly what a staff shortcut must not do.</p>
 *
 * <h2>Main hand only</h2>
 *
 * <p>A bound tool fires from the <b>main hand</b> and nowhere else. Put it in your off-hand and it
 * does nothing — confirmed in testing, and deliberate rather than an oversight.</p>
 *
 * <p>Two reasons. Vanilla raises the interact event for <em>both</em> hands on a single click, so
 * one of them has to be ignored or every bound {@code /jump} takes you twice as far as you meant.
 * And the off-hand is where a shield, a torch or a map lives: a command firing from there would go
 * off during ordinary play, which is exactly what a staff shortcut must not do.</p>
 *
 * <h2>Bound to the item type, not the stack</h2>
 *
 * <p>Every stick you own runs it, not the particular one. Storing it on the stack would need a
 * registered data component and would travel to whoever you gave the item to — which is a
 * surprising thing for a staff shortcut to do. Per player, per item type, and gone when you unbind
 * it.</p>
 */
public final class PowerToolCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build(String alias) {
        return Commands.literal(alias)
                .requires(StandardsPermissions.require(StandardsPermissions.POWERTOOL))
                .executes(PowerToolCommand::show)
                .then(Commands.literal("list").executes(PowerToolCommand::list))
                .then(Commands.literal("clear").executes(ctx -> bind(ctx, null)))
                .then(Commands.literal("clearall").executes(PowerToolCommand::clearAll))
                .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(ctx -> bind(ctx,
                                StringArgumentType.getString(ctx, "command"))));
    }

    /** The registry id of what they are holding, or null if their hand is empty. */
    private static String heldId(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
    }

    private static int bind(CommandContext<CommandSourceStack> ctx, String rawCommand)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String item = heldId(player);
        if (item == null) {
            Feedback.chat(player, Lang.get("msg.item.empty_hand"));
            return 0;
        }
        StandardsData data = StandardsData.get(ctx.getSource().getServer());
        if (rawCommand == null) {
            boolean had = data.setPowerTool(player.getUUID(), item, null);
            Feedback.chat(player, had
                    ? Lang.fmt("msg.pt.cleared", "item", item)
                    : Lang.fmt("msg.pt.nothing_bound", "item", item));
            return had ? 1 : 0;
        }
        String command = rawCommand.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.pt.empty"));
            return 0;
        }
        // No binding /powertool to a powertool. It is the same loop /sudo refuses, arriving by a
        // door you open by right-clicking, which makes it considerably harder to get out of.
        String head = command.split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
        if (head.equals("powertool") || head.equals("pt")) {
            Feedback.chat(player, Lang.get("msg.pt.recursive"));
            return 0;
        }
        data.setPowerTool(player.getUUID(), item, command);
        Feedback.chat(player, Lang.fmt("msg.pt.bound", "item", item, "command", command));
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String item = heldId(player);
        if (item == null) {
            Feedback.chat(player, Lang.get("msg.item.empty_hand"));
            return 0;
        }
        var bound = StandardsData.get(ctx.getSource().getServer())
                .powerTool(player.getUUID(), item);
        Feedback.chat(player, bound
                .map(c -> Lang.fmt("msg.pt.current", "item", item, "command", c))
                .orElseGet(() -> Lang.fmt("msg.pt.nothing_bound", "item", item)));
        return bound.isPresent() ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Map<String, String> all = StandardsData.get(ctx.getSource().getServer())
                .powerToolsOf(player.getUUID());
        if (all.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.pt.none"));
            return 0;
        }
        StringBuilder sb = new StringBuilder(Lang.fmt("msg.pt.list_header",
                "count", String.valueOf(all.size())));
        all.forEach((item, command) -> sb.append("\n").append(
                Lang.fmt("msg.pt.list_row", "item", item, "command", command)));
        Feedback.chat(player, sb.toString());
        return all.size();
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int gone = StandardsData.get(ctx.getSource().getServer())
                .clearPowerTools(player.getUUID());
        Feedback.chat(player, gone == 0
                ? Lang.get("msg.pt.none")
                : Lang.fmt("msg.pt.cleared_all", "count", String.valueOf(gone)));
        return gone;
    }

    /**
     * Fire the bound command, if the item they used has one.
     *
     * <p>Called from {@code StandardsEvents} on a right-click. Returns whether it ran, so the
     * caller can cancel the interaction — otherwise right-clicking a bound block places it, which
     * is exactly what a staff member flying around a build did not want.</p>
     *
     * <p>Dispatched through the <b>player's own</b> command source, so their permissions decide.
     * A bound tool is a shortcut, never an escalation.</p>
     */
    public static boolean use(ServerPlayer player) {
        if (!StandardsPermissions.has(player, StandardsPermissions.POWERTOOL)) {
            // Their node was taken away since they bound it. Silence is right: they are not doing
            // anything wrong, and a refusal on every right-click would be unusable.
            return false;
        }
        String item = heldId(player);
        if (item == null) {
            return false;
        }
        var server = player.level().getServer();
        if (server == null) {
            return false;
        }
        var bound = StandardsData.get(server).powerTool(player.getUUID(), item);
        if (bound.isEmpty()) {
            return false;
        }
        Standards.LOGGER.debug("[powertool] {} ran '{}' from {}",
                player.getName().getString(), bound.get(), item);
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), bound.get());
        return true;
    }

    private PowerToolCommand() {}
}

package com.sablednah.standards.neoforge.commands;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sablednah.standards.core.Duration;
import com.sablednah.standards.neoforge.Feedback;
import com.sablednah.standards.neoforge.Kits;
import com.sablednah.standards.neoforge.Lang;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /kit}, {@code /kits}, {@code /setkit}, {@code /delkit}, {@code /showkit}.
 *
 * <p>The interesting command is {@code /setkit <name> [scope] [cooldown]}: equip yourself the way
 * the kit should look, then save it. Scope picks how much of you to copy —
 * {@code armour}, {@code hotbar}, {@code inventory} or {@code all} — so a "knight" kit can be
 * exactly the armour without whatever happened to be in your pockets.</p>
 *
 * <p><b>Per-kit permissions have a caveat worth knowing.</b> NeoForge's permission API wants nodes
 * enumerated up front, but kits are made at runtime, so {@code standards.kit.<name>} nodes can
 * only be registered for kits that existed at server start. A kit created today is claimable by
 * anyone holding {@code standards.kit} until the next restart registers its own node. Stated
 * plainly rather than hidden, because the alternative — silently gating a brand-new kit to nobody
 * — is worse.</p>
 */
public final class KitCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> kit() {
        return Commands.literal("kit")
                .requires(StandardsPermissions.require(StandardsPermissions.KIT))
                .executes(KitCommands::list)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(KitCommands::suggestKits)
                        .executes(KitCommands::claim));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> kits() {
        return Commands.literal("kits")
                .requires(StandardsPermissions.require(StandardsPermissions.KIT))
                .executes(KitCommands::list);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> setKit() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("setkit")
                .requires(StandardsPermissions.require(StandardsPermissions.SETKIT))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(KitCommands::suggestKits)
                        .executes(ctx -> define(ctx, Kits.Scope.ALL, 0L)));
        for (Kits.Scope scope : Kits.Scope.values()) {
            root.then(Commands.argument("name", StringArgumentType.word())
                    .suggests(KitCommands::suggestKits)
                    .then(Commands.literal(scope.key())
                            .executes(ctx -> define(ctx, scope, 0L))
                            .then(Commands.argument("cooldown", StringArgumentType.word())
                                    .executes(ctx -> defineWithCooldown(ctx, scope)))));
        }
        return root;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> delKit() {
        return Commands.literal("delkit")
                .requires(StandardsPermissions.require(StandardsPermissions.SETKIT))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(KitCommands::suggestKits)
                        .executes(KitCommands::delete));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> showKit() {
        return Commands.literal("showkit")
                .requires(StandardsPermissions.require(StandardsPermissions.KIT))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(KitCommands::suggestKits)
                        .executes(KitCommands::show));
    }

    // --- claiming ---

    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Kits store = Kits.get(player.level().getServer());
        String name = StringArgumentType.getString(ctx, "name");

        Optional<Kits.Kit> found = store.byName(name);
        if (found.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.kit.unknown",
                    "name", name, "list", String.join(", ", store.names())));
            return 0;
        }
        Kits.Kit kit = found.get();
        if (!StandardsPermissions.canUseKit(player, kit.name())) {
            Feedback.chat(player, Lang.fmt("msg.kit.not_yours", "name", kit.name()));
            return 0;
        }
        long wait = store.cooldownLeft(player.getUUID(), kit);
        if (wait > 0) {
            Feedback.chat(player, Lang.fmt("msg.kit.cooldown",
                    "name", kit.name(), "duration", Duration.describe(wait)));
            return 0;
        }

        int dropped = 0;
        for (ItemStack stack : kit.items()) {
            ItemStack copy = stack.copy();
            // Anything that will not fit goes on the floor rather than vanishing. Silently eating
            // half a kit because the player had a full inventory is the classic complaint.
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
                dropped++;
            }
        }
        store.recordClaim(player.getUUID(), kit);
        Feedback.chat(player, dropped == 0
                ? Lang.fmt("msg.kit.given", "name", kit.name())
                : Lang.fmt("msg.kit.given_dropped", "name", kit.name(), "count", dropped));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Kits store = Kits.get(player.level().getServer());
        List<String> available = store.names().stream()
                .filter(n -> StandardsPermissions.canUseKit(player, n))
                .toList();
        if (available.isEmpty()) {
            Feedback.chat(player, Lang.get("msg.kit.none"));
            return 0;
        }
        Feedback.chat(player, Lang.fmt("msg.kit.list", "list", String.join("&7, &f", available)));
        return available.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Kits store = Kits.get(player.level().getServer());
        String name = StringArgumentType.getString(ctx, "name");
        Optional<Kits.Kit> found = store.byName(name);
        if (found.isEmpty()) {
            Feedback.chat(player, Lang.fmt("msg.kit.unknown",
                    "name", name, "list", String.join(", ", store.names())));
            return 0;
        }
        Kits.Kit kit = found.get();
        StringBuilder sb = new StringBuilder(Lang.fmt("msg.kit.contents_header",
                "name", kit.name(),
                "cooldown", kit.cooldownSeconds() <= 0
                        ? Lang.get("msg.kit.no_cooldown") : Kits.describeCooldown(kit)));
        for (ItemStack stack : kit.items()) {
            sb.append("\n").append(Lang.fmt("msg.kit.contents_line",
                    "count", stack.getCount(), "item", stack.getHoverName().getString()));
        }
        Feedback.chat(player, sb.toString());
        return kit.items().size();
    }

    // --- defining ---

    private static int defineWithCooldown(CommandContext<CommandSourceStack> ctx, Kits.Scope scope)
            throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "cooldown");
        Optional<Long> seconds = Duration.parse(raw);
        if (seconds.isEmpty() || seconds.get() == Duration.PERMANENT) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.mod.bad_duration", "input", raw));
            return 0;
        }
        return define(ctx, scope, seconds.get());
    }

    private static int define(CommandContext<CommandSourceStack> ctx, Kits.Scope scope, long cooldown)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        if (!name.matches("[A-Za-z0-9_\\-]{1,32}")) {
            Feedback.chat(player, Lang.get("msg.kit.name_rules"));
            return 0;
        }
        Kits store = Kits.get(player.level().getServer());
        boolean replaced = store.define(name, player, scope, cooldown);
        int count = store.byName(name).map(k -> k.items().size()).orElse(0);
        if (count == 0) {
            Feedback.chat(player, Lang.get("msg.kit.empty_capture"));
            store.delete(name);
            return 0;
        }
        Feedback.reply(ctx.getSource(), Lang.fmt(replaced ? "msg.kit.redefined" : "msg.kit.defined",
                "name", name, "count", count, "scope", scope.key()), true);
        return count;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Kits store = Kits.get(ctx.getSource().getServer());
        if (!store.delete(name)) {
            Feedback.fail(ctx.getSource(), Lang.fmt("msg.kit.unknown",
                    "name", name, "list", String.join(", ", store.names())));
            return 0;
        }
        Feedback.reply(ctx.getSource(), Lang.fmt("msg.kit.deleted", "name", name), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestKits(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Kits.get(ctx.getSource().getServer()).names(), builder);
    }

    private KitCommands() {}
}

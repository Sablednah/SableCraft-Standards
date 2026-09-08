package com.sablednah.standards.neoforge;

import net.minecraft.resources.Identifier;

import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.PlayerSwitches;
import com.sablednah.standards.api.actions.Action;
import com.sablednah.standards.api.actions.Actions;

/**
 * Standards' own entries on the action bar.
 *
 * <p>Registered through the public seam rather than by a private route, so the seam is exercised by
 * its owner before anybody else arrives. A meeting point its author does not use is one nobody has
 * checked — the same argument that makes {@code /group} a real consumer of the groups API.</p>
 *
 * <h2>What earns a place</h2>
 *
 * <p>Only switches and one-word acts. Anything needing an argument stays a command, because a
 * button that opens a box to type an argument into is a worse way to type. And each is gated on the
 * <b>config switch first, then the permission</b>: decision 7 unregisters a command that is off in
 * config, so no permission can make it exist and asking permission first would offer a button for
 * something nobody can run.</p>
 *
 * <p>The three switches carry an {@code active} predicate, so the bar shows what you <em>are</em>
 * rather than only what you may do. That is the half a gamemaster tool asked for and the half worth
 * more than another button: when the game and the operator disagree about whether you are hidden,
 * the bar is what settles it.</p>
 */
public final class StandardsActions {

    public static void registerAll() {
        // Priority is closeness to the anchor, the chat decorators' rule. Standards' own switches
        // sit nearest the inventory at 100 and other mods stack outward from there.
        if (StandardsConfig.ENABLE_FLY.get()) {
            Actions.register(new Action("fly", 100,
                    Identifier.withDefaultNamespace("feather"), "msg.toggle.fly", "fly",
                    p -> StandardsPermissions.has(p, StandardsPermissions.FLY),
                    PlayerSwitches::fly));
        }
        if (StandardsConfig.ENABLE_GOD.get()) {
            Actions.register(new Action("god", 99,
                    Identifier.withDefaultNamespace("totem_of_undying"), "msg.toggle.god", "god",
                    p -> StandardsPermissions.has(p, StandardsPermissions.GOD),
                    PlayerSwitches::god));
        }
        if (StandardsConfig.ENABLE_VANISH.get()) {
            Actions.register(new Action("vanish", 98,
                    Identifier.withDefaultNamespace("glass"), "msg.toggle.vanish", "vanish",
                    p -> StandardsPermissions.has(p, StandardsPermissions.VANISH),
                    Vanish::isVanished));
        }
        if (StandardsConfig.ENABLE_HOMES.get()) {
            // An act rather than a state, so no lit/dim — but a hint, because "how many homes do I
            // have" is a real question and the number is free to compute.
            // Left-click goes home; right-click offers one button per home. The hint is the
            // count, so the button answers "how many have I got" without being clicked at all.
            Actions.register(new Action("home", 90,
                    Identifier.withDefaultNamespace("red_bed"), "msg.actions.home", "home",
                    p -> StandardsPermissions.has(p, StandardsPermissions.HOME),
                    null,
                    p -> {
                        int held = StandardsData.get(p.level().getServer())
                                .homesOf(p.getUUID()).size();
                        return held == 0 ? "" : String.valueOf(held);
                    },
                    p -> StandardsData.get(p.level().getServer()).homesOf(p.getUUID())
                            .keySet().stream()
                            .map(name -> new Action.Child(name, "home " + name))
                            .toList()));
        }
        if (StandardsConfig.ENABLE_WARPS.get()) {
            // A category: there is no sensible "default warp", so left-clicking runs nothing and
            // the whole button is the list underneath. Exactly the grouping case a placeholder
            // button was wanted for, arriving from a real need rather than as a demonstration.
            Actions.register(Action.category("warp", 87,
                    Identifier.withDefaultNamespace("lodestone"), "msg.actions.warp",
                    p -> StandardsPermissions.has(p, StandardsPermissions.WARP)
                            && !StandardsData.get(p.level().getServer()).warpNames().isEmpty(),
                    p -> StandardsData.get(p.level().getServer()).warpNames().stream()
                            .map(name -> new Action.Child(name, "warp " + name))
                            .toList()));
        }
        if (StandardsConfig.ENABLE_SPAWN.get()) {
            Actions.register(new Action("spawn", 89,
                    Identifier.withDefaultNamespace("compass"), "msg.actions.spawn", "spawn",
                    p -> StandardsPermissions.has(p, StandardsPermissions.SPAWN)));
        }
        if (StandardsConfig.ENABLE_BACK.get()) {
            Actions.register(new Action("back", 88,
                    Identifier.withDefaultNamespace("ender_pearl"), "msg.actions.back", "back",
                    p -> StandardsPermissions.has(p, StandardsPermissions.BACK)));
        }
    }

    private StandardsActions() {}
}

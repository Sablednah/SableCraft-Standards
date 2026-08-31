package com.sablednah.standards;

import com.mojang.logging.LogUtils;
import com.sablednah.standards.neoforge.StandardsAttachments;
import com.sablednah.standards.neoforge.StandardsCommands;
import com.sablednah.standards.neoforge.StandardsEconomy;
import com.sablednah.standards.neoforge.StandardsEvents;
import com.sablednah.standards.neoforge.StandardsPermissions;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * Standards — the commands a server always ends up needing.
 *
 * <p>Server-authoritative by design: every command works for an unmodified vanilla client,
 * because the point of a server utility mod is that players do not have to install anything.
 * The optional client half only adds convenience.</p>
 *
 * <p>Layout follows the house style: loader-light logic under {@code core}, NeoForge glue under
 * {@code neoforge}, wire formats under {@code network}, and the stable surface other mods
 * compile against under {@code api} — nothing in {@code api} may move without a version bump.</p>
 */
@Mod(Standards.MODID)
public class Standards {

    /** Must match mod_id in gradle.properties and modId in neoforge.mods.toml. */
    public static final String MODID = "standards";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Standards(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Standards initialising");

        modContainer.registerConfig(ModConfig.Type.COMMON, StandardsConfig.SPEC);

        // Mod bus: attachments and the optional network channel.
        StandardsAttachments.register(modEventBus);
        modEventBus.addListener(com.sablednah.standards.network.StandardsNetwork::register);
        // Common setup, not the constructor: the economy provider's priority is a config value,
        // and config is not loaded yet while mods are still being constructed. Setup still runs
        // long before any server starts, so the choice of ledger is settled before it can matter.
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> {
                    StandardsEconomy.registerIfEnabled();
                    com.sablednah.standards.neoforge.Vanish.install();
                    com.sablednah.standards.neoforge.StandardsEvents.installChatGates();
                    installCombat();
                }));

        // Game bus: player lifecycle, teleport bookkeeping, permissions, commands.
        NeoForge.EVENT_BUS.register(StandardsEvents.class);
        NeoForge.EVENT_BUS.register(StandardsPermissions.class);
        // Dormant unless -Dstandards.selftest=true; see SelfTest.
        NeoForge.EVENT_BUS.register(com.sablednah.standards.neoforge.SelfTest.class);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                StandardsCommands.register(event.getDispatcher(), event.getBuildContext()));
    }

    /**
     * Hand the combat facade its durations.
     *
     * <p>At setup rather than in the constructor, because these are config values and config is
     * not loaded while mods are still being built — the same reason the economy provider registers
     * here. Until this runs every duration is zero, which means no tagging at all: the safe
     * direction for a facade another mod may call before we are ready.</p>
     */
    private static void installCombat() {
        com.sablednah.standards.api.combat.Combat.install(
                ordinal -> switch (com.sablednah.standards.api.combat.CombatKind.values()[ordinal]) {
                    case PVE -> StandardsConfig.COMBAT_PVE_SECONDS.get();
                    case PVP -> StandardsConfig.COMBAT_PVP_SECONDS.get();
                    case SKILL -> StandardsConfig.COMBAT_SKILL_SECONDS.get();
                },
                kind -> switch (kind) {
                    case PVE -> StandardsConfig.COMBAT_PVE_BLOCKS_TELEPORT.get();
                    case PVP -> StandardsConfig.COMBAT_PVP_BLOCKS_TELEPORT.get();
                    case SKILL -> StandardsConfig.COMBAT_SKILL_BLOCKS_TELEPORT.get();
                });
    }
}

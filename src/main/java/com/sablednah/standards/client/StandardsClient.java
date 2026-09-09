package com.sablednah.standards.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

import com.sablednah.standards.Standards;

/**
 * The client half, and the only class a dedicated server must never load.
 *
 * <p>A second {@code @Mod} marked {@link Dist#CLIENT}, the same shape LegendQuest uses. Everything
 * that touches a rendering class hangs off here, so a dedicated server never sees one — which is
 * what lets Standards stay a server mod that happens to be nicer with a client.</p>
 *
 * <p>Nothing here is load-bearing. With this class absent — a vanilla client, or a server — every
 * command still works, which is decision 2 and the reason the bar is allowed to exist at all.</p>
 */
@Mod(value = Standards.MODID, dist = Dist.CLIENT)
public class StandardsClient {

    public StandardsClient(ModContainer container, IEventBus modEventBus) {
        modEventBus.addListener(StandardsKeys::register);
        NeoForge.EVENT_BUS.register(ActionBar.class);
        // Registered BEFORE the bar's own click handling matters, though order between them is not
        // load-bearing: the pane sits in the left margin and the bar sits under the inventory, so
        // their rectangles cannot overlap.
        NeoForge.EVENT_BUS.register(
                com.sablednah.standards.client.panels.PanelHost.class);
        NeoForge.EVENT_BUS.register(ClientLifecycle.class);
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) ->
                        StandardsKeys.onClientTick());
    }
}

package com.sablednah.standards.api.places;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.sablednah.standards.core.Waypoint;
import com.sablednah.standards.neoforge.StandardsData;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * The server's public places — warps and {@code /spawn} — for a mod that shows them somewhere
 * else, such as SableCraft Diorama's web map.
 *
 * <p><b>Read-only, and public places only.</b> Warps are open to everyone ({@code standards.warp}
 * defaults to everyone and {@code /warps} lists them all), so publishing them gives nothing away.
 * Homes are deliberately absent: a home is where somebody sleeps, and a map that showed it would be
 * the most useful thing on the server for whoever wants to find them.</p>
 *
 * <p>Server thread. Each call is a copy, so holding the result costs nothing and nothing changes
 * under you.</p>
 */
public final class Places {

    /** A named point. {@code y} is the block it was set on, which an isometric map needs. */
    public record Place(String name, ResourceKey<Level> dimension, double x, double y, double z) {}

    private Places() {}

    /** Every warp, sorted by name, case-insensitively. */
    public static List<Place> warps(MinecraftServer server) {
        StandardsData data = StandardsData.get(server);
        List<Place> out = new ArrayList<>();
        for (String name : data.warpNames()) {
            data.warp(name).ifPresent(w -> out.add(place(name, w)));
        }
        return List.copyOf(out);
    }

    /** Where {@code /spawn} sends people, if it has been set; empty means the world spawn. */
    public static Optional<Place> spawn(MinecraftServer server) {
        return StandardsData.get(server).spawn().map(w -> place("spawn", w));
    }

    private static Place place(String name, Waypoint w) {
        return new Place(name, w.dimension(), w.x(), w.y(), w.z());
    }
}

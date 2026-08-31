package com.sablednah.standards.neoforge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import com.sablednah.standards.Standards;

/**
 * Move this mod's saved data where Minecraft 26.1 expects to find it.
 *
 * <h2>The failure this exists to prevent</h2>
 *
 * <p>26.1 changed two things at once, and missing the second is what made this hard to see.</p>
 *
 * <p><b>The filename.</b> A {@code SavedDataType} id used to be a plain string and became an
 * {@link net.minecraft.resources.Identifier}, resolving as {@code root.resolve(namespace, path)} —
 * so {@code standards_kits.dat} became {@code standards/kits.dat}.</p>
 *
 * <p><b>And the folder.</b> Per-dimension saved data moved out of the world root. The overworld's
 * store used to sit in {@code world/data/}; it now sits in
 * {@code world/dimensions/minecraft/overworld/data/}, and {@code world/data/} keeps only the
 * genuinely world-global files — scoreboard, weather, wandering trader.</p>
 *
 * <p>So the destination is asked of {@link net.minecraft.world.level.dimension.DimensionType
 * DimensionType} rather than spelled out. Getting only the filename right puts a perfect copy
 * somewhere nothing reads, which fails exactly as silently as not copying at all — and did.</p>
 *
 * <p><b>Nothing about that is a compile error, and nothing about it looks like a fault.</b> A world
 * upgraded from 1.21.11 simply finds no file, creates an empty one, and carries on: every home,
 * warp, kit, mailbox, mute, balance and group is gone, with no exception and no warning. The first
 * anybody knows is a player asking where their base went — which is the worst possible way to find
 * out, and unrecoverable by then if the empty file has been saved over the top.</p>
 *
 * <h2>Copy, never move</h2>
 *
 * <p>The old file is <b>left where it is</b>. Copying costs a few kilobytes once; moving means a
 * server that upgrades, hits some unrelated problem and rolls back to 1.21.11 finds its data gone —
 * and would have been better off if this class had never run. The old file is also the only
 * evidence available if the copy turns out to have been wrong.</p>
 *
 * <p>Runs before anything reads saved data, and skips any destination that already exists, so it is
 * safe on every start rather than only the first.</p>
 */
public final class SaveMigration {

    /**
     * Old flat filename → new namespaced path, for everything this mod persists.
     *
     * <p>Kept as data rather than derived, because the new names were deliberately tidied when the
     * namespace arrived — {@code standards_kits} became {@code standards:kits}, since repeating the
     * mod name inside its own namespace reads badly. A rule could not know that.</p>
     */
    private static final Map<String, String> MOVED = Map.of(
            "standards",        "standards/data",
            "standards_kits",   "standards/kits",
            "standards_mail",   "standards/mail",
            "standards_mutes",  "standards/mutes",
            "standards_groups", "standards/groups",
            // Added in 1.3.0, which shipped on all three lines at once — so a 1.21.11 world can
            // already hold ranks and grants before it is ever upgraded. Missing this would lose
            // every permission group on the way across, silently, exactly as the others would.
            "standards_permissions", "standards/permissions");

    public static void run(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) {
            return; // a brand new world; nothing to carry forward
        }
        // Where the overworld's saved data lives now. Everything this mod stores is overworld-only,
        // deliberately, because it is all questions about players rather than about places.
        Path dest = net.minecraft.world.level.dimension.DimensionType
                .getStorageFolder(net.minecraft.world.level.Level.OVERWORLD, root)
                .resolve("data");
        int moved = 0;
        for (Map.Entry<String, String> entry : MOVED.entrySet()) {
            Path from = data.resolve(entry.getKey() + ".dat");
            Path to = dest.resolve(entry.getValue() + ".dat");
            try {
                if (!Files.isRegularFile(from) || Files.exists(to)) {
                    continue;
                }
                Files.createDirectories(to.getParent());
                Files.copy(from, to);
                moved++;
                Standards.LOGGER.info("Moved {} to {} for Minecraft 26.1+",
                        from.getFileName(), root.relativize(to));
            } catch (IOException e) {
                // Loud, and not fatal. A world that starts with one store missing is recoverable;
                // a world that refuses to start is a server nobody can get into to fix it.
                Standards.LOGGER.error("Could not carry {} forward — that data will look empty. "
                        + "The original is untouched at {}", entry.getKey(), from, e);
            }
        }
        if (moved > 0) {
            Standards.LOGGER.info("Carried {} save file(s) forward from before Minecraft 26.1. "
                    + "The originals are left in place.", moved);
        }
    }

    private SaveMigration() {}
}

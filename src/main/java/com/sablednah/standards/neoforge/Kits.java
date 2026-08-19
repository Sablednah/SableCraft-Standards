package com.sablednah.standards.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.standards.core.Duration;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Kits: a saved bundle of items, handed out on request.
 *
 * <p>Defined <b>in game</b> by equipping yourself the way the kit should look and running
 * {@code /setkit}. That is the design decision that makes kits actually get used — a kit system
 * you configure by writing item ids into a file is one that never gets a second kit, because
 * nobody wants to look up the id for a sharpness-3 diamond sword with a custom name.</p>
 *
 * <p>Item stacks go through {@code ItemStack.CODEC}, which carries components — enchantments,
 * custom names, dyed leather, everything. Save data is encoded with registry access, so that
 * works here where it would not in a plain NBT blob.</p>
 */
public final class Kits extends net.minecraft.world.level.saveddata.SavedData {

    /** Which part of the player to copy. */
    public enum Scope {
        ARMOUR, HOTBAR, INVENTORY, ALL;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** @param cooldownSeconds 0 means it can be claimed as often as they like */
    public record Kit(String name, List<ItemStack> items, long cooldownSeconds) {
        static final Codec<Kit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Kit::name),
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(Kit::items),
                Codec.LONG.optionalFieldOf("cooldownSeconds", 0L).forGetter(Kit::cooldownSeconds))
                .apply(i, Kit::new));
    }

    private record Claim(UUID player, String kit, long at) {
        static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(Claim::player),
                Codec.STRING.fieldOf("kit").forGetter(Claim::kit),
                Codec.LONG.fieldOf("at").forGetter(Claim::at))
                .apply(i, Claim::new));
    }

    private record Snapshot(List<Kit> kits, List<Claim> claims) {
        static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kit.CODEC.listOf().optionalFieldOf("kits", List.of()).forGetter(Snapshot::kits),
                Claim.CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(Snapshot::claims))
                .apply(i, Snapshot::new));
    }

    private static final Codec<Kits> CODEC =
            Snapshot.CODEC.xmap(Kits::new, Kits::snapshot);

    public static final net.minecraft.world.level.saveddata.SavedDataType<Kits> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>("standards_kits", Kits::new, CODEC, null);

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    /** "uuid|kit" → when it was last taken. Persisted: a cooldown a restart clears is an exploit. */
    private final Map<String, Long> claims = new LinkedHashMap<>();

    private Kits() {}

    private Kits(Snapshot snapshot) {
        snapshot.kits().forEach(k -> kits.put(k.name().toLowerCase(Locale.ROOT), k));
        snapshot.claims().forEach(c -> claims.put(key(c.player(), c.kit()), c.at()));
    }

    private Snapshot snapshot() {
        List<Claim> out = new ArrayList<>();
        claims.forEach((k, at) -> {
            int split = k.indexOf('|');
            out.add(new Claim(UUID.fromString(k.substring(0, split)), k.substring(split + 1), at));
        });
        return new Snapshot(List.copyOf(kits.values()), out);
    }

    public static Kits get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static String key(UUID player, String kit) {
        return player + "|" + kit.toLowerCase(Locale.ROOT);
    }

    // --- definitions ---

    public Optional<Kit> byName(String name) {
        return Optional.ofNullable(kits.get(name.toLowerCase(Locale.ROOT)));
    }

    public List<String> names() {
        return kits.values().stream().map(Kit::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /**
     * Capture what the player is currently carrying.
     *
     * <p>Empty slots are dropped rather than stored: a kit is a list of things, not a picture of an
     * inventory layout, and {@code ItemStack.CODEC} refuses empty stacks anyway.</p>
     *
     * @return true if this replaced an existing kit
     */
    public boolean define(String name, ServerPlayer from, Scope scope, long cooldownSeconds) {
        List<ItemStack> items = new ArrayList<>();
        var inventory = from.getInventory();
        for (int slot : slotsFor(scope, inventory.getContainerSize())) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        boolean replaced = kits.containsKey(name.toLowerCase(Locale.ROOT));
        kits.put(name.toLowerCase(Locale.ROOT), new Kit(name, items, cooldownSeconds));
        setDirty();
        return replaced;
    }

    private static List<Integer> slotsFor(Scope scope, int inventorySize) {
        List<Integer> slots = new ArrayList<>();
        switch (scope) {
            case HOTBAR -> addRange(slots, 0, 9);
            case INVENTORY -> addRange(slots, 9, 36);
            // Everything past the 36 storage slots is armour and offhand, however many vanilla
            // currently exposes — asking the inventory rather than hardcoding 4 survives a version
            // that adds a slot.
            case ARMOUR -> addRange(slots, 36, inventorySize);
            case ALL -> addRange(slots, 0, inventorySize);
        }
        return slots;
    }

    private static void addRange(List<Integer> into, int from, int toExclusive) {
        for (int i = from; i < toExclusive; i++) into.add(i);
    }

    public boolean delete(String name) {
        boolean removed = kits.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            claims.keySet().removeIf(k -> k.endsWith("|" + name.toLowerCase(Locale.ROOT)));
            setDirty();
        }
        return removed;
    }

    // --- claiming ---

    /** Seconds still to wait, 0 if they may take it now. */
    public long cooldownLeft(UUID player, Kit kit) {
        if (kit.cooldownSeconds() <= 0) return 0;
        Long last = claims.get(key(player, kit.name()));
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        return Math.max(0, kit.cooldownSeconds() - elapsed);
    }

    public void recordClaim(UUID player, Kit kit) {
        claims.put(key(player, kit.name()), System.currentTimeMillis());
        setDirty();
    }

    /** For the message: "every 1d 12h". */
    public static String describeCooldown(Kit kit) {
        return kit.cooldownSeconds() <= 0 ? "" : Duration.describe(kit.cooldownSeconds());
    }
}

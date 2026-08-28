package com.sablednah.standards.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.standards.StandardsConfig;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Letters waiting to be read.
 *
 * <p>The whole point of {@code /mail} is that the recipient is <em>not online</em>, so this is
 * save data rather than anything hung off a player. Read state lives on the letter rather than as
 * a separate marker, so a letter cannot end up read-but-missing or missing-but-unread.</p>
 *
 * <p>Mailboxes are capped. An uncapped one is a griefing tool: a thousand letters is a thousand
 * lines of chat on login, and a save file that grows forever.</p>
 */
public final class Mailbox extends SavedData {

    public record Letter(UUID from, String fromName, String text, long sentAt, boolean read) {
        static final Codec<Letter> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("from").forGetter(Letter::from),
                Codec.STRING.optionalFieldOf("fromName", "?").forGetter(Letter::fromName),
                Codec.STRING.fieldOf("text").forGetter(Letter::text),
                Codec.LONG.optionalFieldOf("sentAt", 0L).forGetter(Letter::sentAt),
                Codec.BOOL.optionalFieldOf("read", false).forGetter(Letter::read))
                .apply(i, Letter::new));

        Letter markRead() {
            return new Letter(from, fromName, text, sentAt, true);
        }
    }

    private record Box(UUID owner, List<Letter> letters) {
        static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Box::owner),
                Letter.CODEC.listOf().fieldOf("letters").forGetter(Box::letters))
                .apply(i, Box::new));
    }

    private static final Codec<Mailbox> CODEC = Box.CODEC.listOf()
            .xmap(Mailbox::new, m -> m.boxes.entrySet().stream()
                    .map(e -> new Box(e.getKey(), List.copyOf(e.getValue())))
                    .toList())
            .fieldOf("mailboxes").codec();

    public static final SavedDataType<Mailbox> TYPE =
            new SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            "standards", "mail"), Mailbox::new, CODEC, null);

    private final Map<UUID, List<Letter>> boxes = new LinkedHashMap<>();

    private Mailbox() {}

    private Mailbox(List<Box> loaded) {
        loaded.forEach(b -> boxes.put(b.owner(), new ArrayList<>(b.letters())));
    }

    public static Mailbox get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** @return false if the recipient's mailbox is full */
    public boolean send(UUID to, UUID from, String fromName, String text) {
        List<Letter> box = boxes.computeIfAbsent(to, k -> new ArrayList<>());
        if (box.size() >= StandardsConfig.MAIL_LIMIT.get()) {
            return false;
        }
        box.add(new Letter(from, fromName, text, System.currentTimeMillis(), false));
        setDirty();
        return true;
    }

    public List<Letter> read(UUID owner) {
        return List.copyOf(boxes.getOrDefault(owner, List.of()));
    }

    public long unread(UUID owner) {
        return boxes.getOrDefault(owner, List.of()).stream().filter(l -> !l.read()).count();
    }

    /** Mark everything read. Called when they actually look, not when they are told they have post. */
    public void markAllRead(UUID owner) {
        List<Letter> box = boxes.get(owner);
        if (box == null || box.isEmpty()) return;
        box.replaceAll(letter -> letter.read() ? letter : letter.markRead());
        setDirty();
    }

    /** @return how many were thrown away */
    public int clear(UUID owner) {
        List<Letter> box = boxes.remove(owner);
        if (box == null) return 0;
        setDirty();
        return box.size();
    }
}

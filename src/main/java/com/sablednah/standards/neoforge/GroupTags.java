package com.sablednah.standards.neoforge;

import java.util.Optional;

import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.chat.Chat;
import com.sablednah.standards.api.chat.NameDecorator;
import com.sablednah.standards.api.groups.Group;
import com.sablednah.standards.api.groups.GroupKind;
import com.sablednah.standards.api.groups.Groups;

import net.minecraft.server.level.ServerPlayer;

/**
 * Renders group tags in chat — {@code [SBL] Sablednah: hello}.
 *
 * <p>Standards consuming two of its own seams at once: it reads {@link Groups} and contributes
 * through {@link NameDecorator}, exactly as an outside mod would. Nothing here reaches into
 * {@link StandardsGroups} directly, so a tag from LegendQuest's parties or a faction mod renders
 * by the same path with no code change.</p>
 *
 * <h2>One decorator per configured kind</h2>
 *
 * <p>Registered separately rather than as one decorator emitting several tags, because the
 * decorator API already has an ordering rule — priority is closeness to the name — and reusing it
 * is better than inventing a second ordering inside a single decorator. The config lists kinds
 * outermost first, so the first entry gets the lowest priority and drifts left.</p>
 *
 * <h2>No tag, no render</h2>
 *
 * <p>A group without a short tag is skipped rather than rendered by its name. {@code [The Crimson
 * Brotherhood]} on every line is what makes a server turn group tags off, at which point the
 * whole seam has been built for nothing.</p>
 */
public final class GroupTags {

    /** Installed at server start, once providers can exist. */
    public static void install() {
        java.util.List<? extends String> kinds = StandardsConfig.CHAT_GROUP_KINDS.get();
        int priority = 0;
        for (String kindId : kinds) {
            // Outermost first in config, and lower priority renders further from the name.
            Chat.register(decorator(kindId, priority));
            priority += 10;
        }
    }

    private static NameDecorator decorator(String kindId, int priority) {
        return new NameDecorator() {
            @Override
            public String id() {
                return "standards:grouptag/" + kindId;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public Optional<String> prefix(ServerPlayer player) {
                // Resolved per message rather than cached: the kind may not have a provider yet
                // when this is installed, and a mod can register one later.
                Optional<GroupKind> kind = Groups.kind(kindId);
                if (kind.isEmpty()) {
                    return Optional.empty();
                }
                for (Group group : Groups.all(player, kind.get())) {
                    Optional<String> tag = group.tag();
                    if (tag.isPresent() && !tag.get().isBlank()) {
                        return Optional.of(Lang.fmt("msg.chat.group_tag", "tag", tag.get()));
                    }
                }
                return Optional.empty();
            }

            @Override
            public Optional<String> suffix(ServerPlayer player) {
                return Optional.empty();
            }
        };
    }

    private GroupTags() {}
}

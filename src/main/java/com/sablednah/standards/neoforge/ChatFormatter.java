package com.sablednah.standards.neoforge;

import java.util.List;

import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.chat.Chat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns the registered decorators into the line everyone sees.
 *
 * <p>Rendered server-side and pushed out as a plain component, so a <b>vanilla client</b> shows
 * the decorated name exactly like a modded one. That is the same reason {@link Lang} resolves text
 * on the server rather than shipping translation keys.</p>
 *
 * <p>The shape of the line is a config template rather than something baked in here — a server
 * that wants {@code <name> message} instead of {@code name: message}, or brackets somewhere else
 * entirely, should not need a code change.</p>
 */
public final class ChatFormatter {

    /**
     * Build the finished chat line.
     *
     * @return the formatted component, or empty when there is nothing to add and vanilla's own
     *         formatting should be left alone
     */
    public static java.util.Optional<Component> format(ServerPlayer player, String message) {
        List<String> prefixes = Chat.prefixes(player);
        List<String> suffixes = Chat.suffixes(player);
        boolean decorated = !prefixes.isEmpty() || !suffixes.isEmpty();

        // Nothing registered and no custom template: leave chat exactly as vanilla renders it.
        // Reformatting an undecorated line would throw away vanilla's hover cards and team
        // colours for no gain whatever.
        if (!decorated && !StandardsConfig.CHAT_ALWAYS_FORMAT.get()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(Feedback.colored(compose(
                StandardsConfig.CHAT_FORMAT.get(),
                StandardsConfig.CHAT_AFFIX_SEPARATOR.get(),
                player.getName().getString(), prefixes, suffixes, message)));
    }

    /**
     * The finished line as text. Pure, so the self-test can prove its shape without a player.
     *
     * <p>Worth testing precisely because the line is <em>complete</em> — it carries the name
     * itself, which is what makes it wrong to hand back to something that will add a name of its
     * own. That mistake produced "&lt;Steve&gt; Lord Steve the saintly: hello" in the wild.</p>
     */
    public static String compose(String template, String separator, String name,
            List<String> prefixes, List<String> suffixes, String message) {
        return template
                .replace("{prefixes}", join(prefixes, separator, true))
                .replace("{suffixes}", join(suffixes, separator, false))
                .replace("{name}", name)
                .replace("{message}", message);
    }

    /**
     * Join the affixes, with the separator only <em>between</em> them and a single space against
     * the name.
     *
     * <p>Fiddly on purpose: the alternative is either {@code [A][B]Sablednah} with no breathing
     * room or {@code [A] [B] Sablednah the noble } with a trailing space, and both look like a
     * bug to everyone who reads chat all day.</p>
     */
    private static String join(List<String> parts, String separator, boolean prefix) {
        if (parts.isEmpty()) return "";
        String joined = String.join(separator, parts);
        return prefix ? joined + " " : " " + joined;
    }

    private ChatFormatter() {}
}

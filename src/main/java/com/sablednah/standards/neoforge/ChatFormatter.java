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
        String name = displayName(player);
        boolean decorated = !prefixes.isEmpty() || !suffixes.isEmpty();
        // A nickname is a reason to format all by itself. Without this the whole feature is dead
        // on the commonest server there is: no decorators registered means format() bails early,
        // and the nickname is computed correctly and shown to nobody. Exactly the shape of bug
        // this codebase keeps producing — see 'the category of bug this mod keeps producing'.
        boolean renamed = !name.equals(player.getName().getString());

        // Nothing registered, no nickname and no custom template: leave chat exactly as vanilla
        // renders it. Reformatting an undecorated line would throw away vanilla's hover cards and
        // team colours for no gain whatever.
        if (!decorated && !renamed && !StandardsConfig.CHAT_ALWAYS_FORMAT.get()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(Feedback.colored(compose(
                StandardsConfig.CHAT_FORMAT.get(),
                StandardsConfig.CHAT_AFFIX_SEPARATOR.get(),
                name, prefixes, suffixes,
                // What the player typed is text, never formatting. See Feedback.stripCodes.
                Feedback.stripCodes(message))));
    }

    /**
     * What to call this player in chat: their nickname if they have one, otherwise their name.
     *
     * <p>Marked with a configurable prefix — {@code ~Bob} — so a reader can tell a chosen name
     * from a real one without asking. That marker is the cheap half of what stops a nickname being
     * an impersonation; the expensive half is {@link StandardsData#impersonates}, which refuses
     * the nickname in the first place.</p>
     *
     * <p><b>Chat only.</b> The tab list and the nameplate keep the real name, deliberately — see
     * {@link com.sablednah.standards.neoforge.commands.NickCommands}. A player who wonders who
     * somebody is can look at tab without knowing {@code /realname} exists.</p>
     *
     * <p>Falls back to the real name if the server is somehow unavailable, which is the safe
     * direction: showing a real name where a nickname was expected is a cosmetic surprise, and
     * showing a nickname where the real name was expected is the failure this whole feature has
     * to avoid.</p>
     */
    public static String displayName(ServerPlayer player) {
        String real = player.getName().getString();
        if (!StandardsConfig.ENABLE_NICK.get()) {
            return real;
        }
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server == null) {
            return real;
        }
        return com.sablednah.standards.neoforge.StandardsData.get(server).nick(player.getUUID())
                .map(nick -> StandardsConfig.NICK_PREFIX.get() + nick)
                .orElse(real);
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

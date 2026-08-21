package com.sablednah.standards.neoforge;

import net.minecraft.server.level.ServerLevel;

import net.minecraft.server.MinecraftServer;

import com.sablednah.standards.core.Waypoint;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything Standards says to a player.
 *
 * <p>Text arrives here as a plain string with {@code &} colour codes, resolved by {@link Lang}
 * from the owner-editable catalogue. The conversion to {@code §} happens exactly once, here, so
 * no message elsewhere has to remember to do it.</p>
 */
public final class Feedback {

    public static void chat(ServerPlayer player, String text) {
        player.displayClientMessage(colored(text), false);
    }

    public static void actionBar(ServerPlayer player, String text) {
        player.displayClientMessage(colored(text), true);
    }

    /**
     * Command output for a source that may not be a player at all — a command block, the console,
     * a datapack function. {@code success} messages are what get echoed to other operators and
     * logged, which is why an admin action passes true and a personal one does not.
     */
    public static void reply(CommandSourceStack source, String text, boolean broadcastToOps) {
        source.sendSuccess(() -> colored(text), broadcastToOps);
    }

    public static void fail(CommandSourceStack source, String text) {
        source.sendFailure(colored(text));
    }

    /** The codes Minecraft actually recognises: 0-9, a-f, k-o, r. */
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    /**
     * Turn {@code &} colour codes into the section sign Minecraft renders.
     *
     * <p><b>Only where a real code follows.</b> The obvious implementation is
     * {@code text.replace('&', '§')} and it is wrong in a way that takes a while to notice:
     * "Tom &amp; Jerry" becomes "Tom § Jerry", where the section sign eats the following space as
     * a colour code and the ampersand disappears. Every ordinary use of the word "and" in a
     * player-facing string is quietly corrupted. (Found next door in LegendQuest, which had the
     * same blind replace and the same symptom.)</p>
     */
    public static Component colored(String text) {
        return Component.literal(translateCodes(text));
    }

    /** As {@link #colored}, as a String. */
    public static String translateCodes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isCode = c == '&' && i + 1 < text.length()
                    && CODES.indexOf(text.charAt(i + 1)) >= 0;
            out.append(isCode ? '§' : c);
        }
        return out.toString();
    }

    /**
     * Remove formatting from text a player wrote, so it cannot become formatting.
     *
     * <p><b>Every path that carries player-authored text into a message must call this.</b> Chat,
     * {@code /me}, {@code /msg}, mail, home and warp names. {@link Lang#fmt} substitutes values
     * into a template <em>before</em> the template's codes are translated, so an unfiltered value
     * is a formatting injection: a player typing {@code &c&l} gets red bold text, and one typing
     * {@code &r} followed by a plausible prefix can dress their words up as somebody else's — or
     * as a server message. {@code &k} is worse still, since obfuscated text cannot be read back.</p>
     *
     * <p>Both the ampersand form and a literal section sign are stripped: a client cannot type
     * {@code §} into chat, but text can reach us from a book, a sign, a command block or another
     * mod, and the assumption that it cannot is exactly the kind that stops being true later.</p>
     */
    public static String stripCodes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isCode = (c == '&' || c == '§') && i + 1 < text.length()
                    && CODES.indexOf(text.charAt(i + 1)) >= 0;
            if (isCode) {
                i++; // drop the code letter too
                continue;
            }
            if (c == '§') {
                continue; // a bare section sign has no business in player text
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * A clickable button in chat, e.g. {@code [Accept]} running {@code /tpaccept Steve}.
     *
     * <p>Click events are plain vanilla chat components, so this works on an <b>unmodified
     * client</b> — which is the whole reason it is worth doing. "How do I accept?" is the single
     * most common question a {@code /tpa} feature generates, and a button answers it without any
     * client mod, any tutorial, or any reading.</p>
     */
    public static Component button(String label, String command, String tooltip) {
        return colored(label).copy().withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(colored(tooltip))));
    }

    /** A line of text with buttons appended, sent as one message. */
    public static void chatWithButtons(ServerPlayer player, String text, Component... buttons) {
        var line = colored(text).copy();
        for (Component b : buttons) {
            line.append(Component.literal(" ")).append(b);
        }
        player.sendSystemMessage(line);
    }

    private Feedback() {}

    /**
     * Warn if a just-saved location is one a non-flying player cannot arrive at.
     *
     * <p>Saving is still allowed — someone setting a warp on a platform they have not built yet
     * is doing something reasonable, and refusing would be the mod second-guessing them. But the
     * alternative to saying so <em>now</em> is saying nothing until somebody else types
     * {@code /spawn} and gets "nowhere safe to land there", which is the same information
     * delivered hours later to the person least able to act on it.</p>
     *
     * <p>Found exactly that way: an overnight test left the world spawn hanging at y100 and the
     * failure turned up the next morning, three commands removed from its cause.</p>
     */
    public static void warnIfUnreachable(ServerPlayer player, Waypoint saved) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = saved.level(server);
        if (level == null) {
            return;
        }
        if (SafeLoc.find(level, saved.blockPos(), true).isEmpty()) {
            chat(player, Lang.get("msg.tp.set_unreachable"));
        }
    }
}

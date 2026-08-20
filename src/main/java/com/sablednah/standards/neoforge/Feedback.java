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

    public static Component colored(String text) {
        return Component.literal(text.replace('&', '§'));
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

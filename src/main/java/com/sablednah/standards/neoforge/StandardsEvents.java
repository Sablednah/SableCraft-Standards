package com.sablednah.standards.neoforge;

import com.sablednah.standards.Standards;
import com.sablednah.standards.StandardsConfig;
import com.sablednah.standards.api.economy.Economy;
import com.sablednah.standards.core.Waypoint;

import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import com.sablednah.standards.api.chat.Chat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus handlers: player lifecycle, keeping the switches applied, and driving teleport warmups.
 *
 * <p>Most of this file exists because <b>Minecraft does not keep a player's abilities for us.</b>
 * Respawning, changing dimension and changing game mode all rebuild the ability flags from the
 * game mode, so a player who was flying stops flying and never gets told why. Every essentials
 * package eventually grows these handlers; putting them in from the start is cheaper than
 * discovering them one bug report at a time.</p>
 */
public final class StandardsEvents {

    @SubscribeEvent
    static void onServerStarting(ServerAboutToStartEvent event) {
        // messages.yml: written with the full catalogue on first run, merged thereafter.
        Lang.load();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Teleports.tick(event.getServer());
        TeleportRequests.tick(event.getServer());
        Afk.tick(event.getServer());
    }

    // --- lifecycle ---

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        healStaleWalkSpeed(player);

        // The name cache is what makes /eco give <offline player> and /baltop rows possible.
        StandardsData.get(player.level().getServer()).rememberName(player);
        if (StandardsConfig.ENABLE_ECONOMY.get() && Economy.isAvailable()) {
            Economy.createAccount(player.getUUID());
        }
        applySwitches(player);
        Vanish.onLogin(player);
        remindOfPersistedSwitches(player);
        if (StandardsConfig.ENABLE_MAIL.get()) {
            com.sablednah.standards.neoforge.commands.MailCommands.announceOnLogin(player);
        }
    }

    /**
     * Undo a walking speed persisted by a broken earlier build of this mod.
     *
     * <p>Between the first {@code /speed walk} and its fix, Standards wrote
     * {@code abilities.walkingSpeed}. That value is saved in the player's NBT, and
     * {@code Player.readAdditionalSaveData} seeds the {@code MOVEMENT_SPEED} base from it at every
     * login — so our multiplier would apply on top of an already-inflated base and double again on
     * each relog. 2x becomes 4x becomes 8x, silently.</p>
     *
     * <p>Only fires when the value is not vanilla's <em>and</em> the attribute base matches it,
     * which together mean it was seeded from the ability rather than set by somebody else. And
     * only at login, because that is the one moment the stale value can do any harm — repeating it
     * on every respawn would stomp any mod that legitimately sets a walking baseline, which is
     * exactly the complaint we would raise if it were done to us.</p>
     */
    private static void healStaleWalkSpeed(ServerPlayer player) {
        var abilities = player.getAbilities();
        if (abilities.getWalkingSpeed() == 0.1F) {
            return;
        }
        AttributeInstance walking = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (walking == null || walking.getBaseValue() != abilities.getWalkingSpeed()) {
            return;
        }
        Standards.LOGGER.info("Resetting a stale walking speed of {} for {}",
                abilities.getWalkingSpeed(), player.getName().getString());
        abilities.setWalkingSpeed(0.1F);
        walking.setBaseValue(0.1D);
        player.onUpdateAbilities();
    }

    /**
     * Tell a returning player which switches are still on.
     *
     * <p>Flight, god mode and vanish all survive a logout on purpose. The trouble is that two of
     * them are <em>silent</em>: a vanished player looks exactly like a lonely server, and the only
     * hint is mobs quietly ignoring you. Reported by exactly that route — "interesting, zombies
     * are ignoring me" — after being invisible across three server restarts without knowing.</p>
     *
     * <p>Only mentions what is actually on, so an ordinary player sees nothing at all.</p>
     */
    private static void remindOfPersistedSwitches(ServerPlayer player) {
        PlayerState state = StandardsAttachments.of(player);
        boolean vanished = Vanish.isVanished(player);

        // Order is by how surprising the state is to be in without knowing.
        java.util.List<String> states = new java.util.ArrayList<>();
        java.util.List<String> commands = new java.util.ArrayList<>();
        if (vanished) {
            states.add(Lang.get("term.state.vanished"));
            commands.add("/vanish off");
        }
        if (state.god()) {
            states.add(Lang.get("term.state.god"));
            commands.add("/god off");
        }
        if (state.fly()) {
            states.add(Lang.get("term.state.fly"));
            commands.add("/fly off");
        }
        if (states.isEmpty()) {
            return;
        }

        // Vanish takes damage away too, which reads exactly like being stuck in god mode. Say so.
        boolean hidden = vanished && StandardsConfig.VANISH_INVULNERABLE.get();
        Feedback.chat(player, Lang.fmt(hidden ? "msg.toggle.still_on_hidden" : "msg.toggle.still_on",
                "what", joinStates(states),
                "commands", String.join(", ", commands)));
    }

    /** "a", "a and b", "a, b and c" — so the reminder reads as a sentence, not a list. */
    static String joinStates(java.util.List<String> parts) {
        int last = parts.size() - 1;
        if (last == 0) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, last))
                + " " + Lang.get("term.list.and") + " " + parts.get(last);
    }

        @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Teleports.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer leaving) {
            // Where they stood, for /tpoffline. Written once, on the way out.
            StandardsData.get(leaving.level().getServer()).rememberLogout(leaving);
        }
        // Their open requests go with them. Leaving them would let someone accept a request from
        // a player who logged off ten minutes ago and teleport to wherever they happened to be.
        TeleportRequests.closeAllInvolving(event.getEntity().getUUID());
        com.sablednah.standards.neoforge.commands.MessageCommands.forget(
                event.getEntity().getUUID());
        Afk.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            Vanish.onLogout(player);
        }
    }

    /**
     * Mobs do not notice someone who is not there.
     *
     * <p>The entity tracker hides a vanished player from other <em>players</em>; it says nothing
     * about mob AI, which happily keeps pathing toward an invisible staff member. A zombie
     * following nothing across a build is the tell that gives a half-built vanish away.</p>
     */
    @SubscribeEvent
    static void onChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer target && Vanish.isVanished(target)) {
            event.setCanceled(true);
        }
    }

    /**
     * Re-apply the switches <em>before</em> the client is told anything, on death.
     *
     * <p>{@code PlayerList.respawn} runs in this order:</p>
     *
     * <pre>
     *   restoreFrom(...)                  // PlayerEvent.Clone fires at the end of this
     *   send(ClientboundRespawnPacket)    // the client draws whatever it is given here
     *   firePlayerRespawnEvent(...)       // PlayerRespawnEvent — too late
     * </pre>
     *
     * <p>So anything repaired on {@code PlayerRespawnEvent} is repaired after the client has
     * already been sent the wrong values. LegendQuest hit the visible version of this — a health
     * bar showing ten hearts for a tick before snapping to twenty-eight — and passed the finding
     * on. Ours is less visible and worse: there is a window in which {@code invulnerable} is false
     * for a {@code /god} player and {@code mayfly} is false for a flying one, so respawning into
     * lava can cost a hit and a flying player can drop.</p>
     *
     * <p><b>Split rather than moved.</b> {@code applySwitches} ends with
     * {@code onUpdateAbilities()}, which sends a packet, and at Clone time the player is not in a
     * level yet. So the state is written here and the packet is left to the respawn event below,
     * which stays as an idempotent safety net for respawn paths that never clone.</p>
     */
    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return; // returning from the End keeps everything; nothing to repair
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            applySwitches(player, false);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applySwitches(player);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applySwitches(player);
        }
    }

    /**
     * A game-mode change rebuilds the ability flags wholesale, so the switches have to be put
     * back afterwards — and after, not during: the event fires before the change is applied, so
     * writing abilities here would simply be overwritten a moment later.
     */
    @SubscribeEvent
    static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        GameType next = event.getNewGameMode();
        player.level().getServer().execute(() -> {
            if (player.isRemoved()) return;
            // Creative and spectator fly on their own terms; re-asserting ours would fight the
            // game mode rather than serve the player.
            if (next != GameType.CREATIVE && next != GameType.SPECTATOR) {
                applySwitches(player);
            }
        });
    }

    /** Our flight grant, held as an attribute modifier so it can be removed without collateral. */
    private static final Identifier FLIGHT_MODIFIER =
            Identifier.fromNamespaceAndPath(Standards.MODID, "fly_command");

    /** Our slice of movement speed, so /speed composes with potions and other mods' modifiers. */
    private static final Identifier WALK_SPEED_MODIFIER =
            Identifier.fromNamespaceAndPath(Standards.MODID, "walk_speed");

    /**
     * Put the player's saved switches back into effect.
     *
     * <h2>Why flight is an attribute and not a flag</h2>
     *
     * <p>The obvious implementation is {@code abilities.mayfly = true}, and NeoForge has
     * deprecated exactly that: it is a single boolean that any number of mods want to own, so
     * whoever writes {@code false} last takes flight away from everyone else's feature. The
     * replacement is {@code NeoForgeMod.CREATIVE_FLIGHT}, a boolean attribute where each grant is
     * a modifier with its own id — ours goes on and comes off without touching anybody else's.</p>
     *
     * <p><b>But the attribute alone is not enough here</b>, and this is the part worth writing
     * down: {@code ClientboundPlayerAbilitiesPacket} is built from {@code abilities.mayfly}
     * directly, so a <em>vanilla</em> client is never told about the attribute. The server would
     * happily accept it flying while its own client refuses to leave the ground — the mod's
     * central promise broken in the quietest possible way. So the attribute stays the source of
     * truth and {@code mayfly} is written as a derived cache of it, purely so the packet carries
     * the right answer.</p>
     */
    @SuppressWarnings("deprecation") // mayfly: deliberately mirrored, see above
    public static void applySwitches(ServerPlayer player) {
        applySwitches(player, true);
    }

    /**
     * @param sync whether to push the result to the client. False only from
     *             {@link #onClone}, where the player has no level yet and the respawn packet
     *             that follows carries the values anyway.
     */
    public static void applySwitches(ServerPlayer player, boolean sync) {
        PlayerState state = StandardsAttachments.of(player);
        boolean creativeish = player.isCreative() || player.isSpectator();

        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) {
            flight.removeModifier(FLIGHT_MODIFIER);
            if (state.fly()) {
                // Transient: our copy of the truth is the attachment, re-applied on every login,
                // respawn and dimension change. A saved modifier would just be a second one to
                // keep in sync.
                flight.addTransientModifier(new AttributeModifier(
                        FLIGHT_MODIFIER, 1.0D, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        var abilities = player.getAbilities();
        boolean mayFly = creativeish
                || (flight != null && flight.getValue() > 0.0D)
                || abilities.instabuild;
        abilities.mayfly = mayFly;
        if (!mayFly) {
            // Otherwise /fly off leaves them airborne until they happen to touch a block.
            abilities.flying = false;
        }

        // WALKING SPEED IS AN ATTRIBUTE, mirrored to the ability — decision 3 all over again.
        //
        // abilities.walkingSpeed is what the client is told, and it drives the FOV stretch and
        // the client's own movement prediction. It is NOT what the server moves the player by:
        // that is minecraft:movement_speed, and the server validates incoming positions against
        // it. Set only the ability and you get the exact symptom reported — "the FOV changed but
        // it felt the same" — because the client is showing a speed the server will not honour.
        //
        // So the attribute is the whole of the truth here, and the ability is deliberately left
        // at vanilla's 0.1 — see the note further down, which is the other half of this fix.
        AttributeInstance walking = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (walking != null) {
            walking.removeModifier(WALK_SPEED_MODIFIER);
            if (state.walkSpeed() != 1.0F) {
                walking.addTransientModifier(new AttributeModifier(
                        WALK_SPEED_MODIFIER, state.walkSpeed() - 1.0D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        // WALKING SPEED IS NEVER WRITTEN HERE, and that is the fix rather than an omission.
        //
        // Player.readAdditionalSaveData does, at LOGIN ONLY:
        //     getAttribute(MOVEMENT_SPEED).setBaseValue(abilities.getWalkingSpeed())
        //
        // so the ability seeds the attribute's BASE, once, and the client's FOV is the ratio
        //     getAttributeValue(MOVEMENT_SPEED) / abilities.getWalkingSpeed()
        // which is precisely "how much faster than your own baseline are you". Leaving the
        // ability at vanilla's 0.1 therefore gets the speed-potion behaviour for free: the
        // modifier above makes the player genuinely faster AND widens their view, with no packet
        // of our own.
        //
        // Writing it breaks both halves. Set it at runtime and the attribute base does not follow
        // until the next login, so the player sees a changed FOV and moves at the old speed —
        // the reported symptom. Set it alongside the modifier and the ratio becomes 1, so a
        // genuinely faster player gets no visual cue at all. And worst, the value persists in the
        // abilities NBT, so at the next login it seeds the base and our modifier multiplies it
        // AGAIN: /speed walk 2 becomes 4x, then 8x.
        //
        // Not written here at all. Normalising a stale value is a one-time migration and belongs
        // at login (see healStaleWalkSpeed) — doing it on every respawn, dimension change and
        // game-mode change would stomp any other mod that legitimately sets a walking baseline,
        // which is precisely the complaint we would raise if somebody did it to us.

        // Flying is genuinely the other way round: there is no flight-speed attribute, so
        // abilities.flyingSpeed IS the authority. Which is why /speed fly worked all along.
        abilities.setFlyingSpeed(0.05F * state.flySpeed());

        if (state.god()) {
            abilities.invulnerable = true;
        } else if (!creativeish) {
            abilities.invulnerable = false;
        }
        if (sync) {
            player.onUpdateAbilities();
        }
    }

    // --- god mode ---

    /**
     * {@code abilities.invulnerable} covers most damage but not all of it — starvation, the void
     * and {@code /kill} route around it — so god mode also vetoes the damage event outright.
     * High priority so it settles before anything that reacts to damage having landed.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (StandardsAttachments.of(player).god()) {
            event.setCanceled(true);
            return;
        }
        // Invisible is not the same as untouchable, and the gap is exploitable: an entity that is
        // hidden still has a hitbox, so someone who knows roughly where staff are standing can
        // find them with arrows. Found by hitting a vanished player on purpose, aiming from the
        // other screen.
        if (StandardsConfig.VANISH_INVULNERABLE.get() && Vanish.isVanished(player)) {
            event.setCanceled(true);
            return;
        }
        Teleports.onDamaged(player);
    }

    /**
     * A muted player's chat does not reach anyone.
     *
     * <p>Cancelled rather than silently swallowed: they are told how long is left and why, every
     * time they try. A mute that gives no feedback just reads as broken chat, and the player
     * spends the next ten minutes asking staff why nobody can hear them — in a channel that also
     * does not work.</p>
     */
    @SubscribeEvent
    static void onChat(net.neoforged.neoforge.event.ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Afk.onActivity(player);
        var server = player.level().getServer();
        if (server == null) return;
        // Decoration happens after the mute check below, so a muted player's message is stopped
        // rather than formatted and then thrown away.
        Mutes.get(server).active(player.getUUID()).ifPresent(mute -> {
            event.setCanceled(true);
            long left = mute.remaining(System.currentTimeMillis());
            Feedback.chat(player, mute.permanent()
                    ? Lang.fmt("msg.mod.mute_blocked_perm", "reason", mute.reason())
                    : Lang.fmt("msg.mod.mute_blocked",
                            "duration", com.sablednah.standards.core.Duration.describe(left),
                            "reason", mute.reason()));
        });
        if (event.isCanceled()) return;

        // Cancel and broadcast, rather than setMessage. ServerChatEvent.setMessage replaces the
        // message *body* only — vanilla still wraps whatever it is given in its own "<name> %s"
        // chat type, so handing it a finished "Lord Steve the saintly: hello" line produces
        // "<Steve> Lord Steve the saintly: hello" and the name appears twice. There is no
        // set-the-whole-line on the event, so the formatted path has to take over delivery.
        //
        // Reported from the LegendQuest side, against a genuinely unmodded client — and invisible
        // here until then, because with no decorator registered format() returns empty and this
        // line never runs. The first real decorator was the first execution of this code path.
        // Offer the message to any registered channel before it goes to the server at large.
        // Deliberately AFTER the mute gate and the AFK note above: a channel that could bypass
        // those would make a mute a lie, which is the entire reason this seam exists rather than
        // channel mods cancelling the event themselves. See ChatRouter.
        java.util.Optional<String> claimed = Chat.route(player, event.getRawText());
        if (claimed.isPresent()) {
            event.setCanceled(true);
            Standards.LOGGER.debug("chat claimed by router '{}'", claimed.get());
            return;
        }

        ChatFormatter.format(player, event.getRawText()).ifPresent(line -> {
            event.setCanceled(true);
            deliver(server, player, line);
        });
    }

    /**
     * Hand the Chat API the two questions it cannot answer itself.
     *
     * <p>Functions rather than direct calls, because {@code Mutes} and {@code Afk} live here in
     * {@code neoforge} and the API must not depend on the implementation it is a seam for. Same
     * arrangement as {@code VanishGate}, installed at setup for the same reason.</p>
     */
    public static void installChatGates() {
        Chat.installGates(StandardsEvents::muteReason, Afk::onActivity);
    }

    /** The mute check, worded for the player — shared by chat, {@code /msg}, {@code /me}. */
    private static java.util.Optional<Component> muteReason(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return java.util.Optional.empty();
        }
        return Mutes.get(server).active(player.getUUID()).map(mute -> {
            long left = mute.remaining(System.currentTimeMillis());
            return Feedback.colored(mute.permanent()
                    ? Lang.fmt("msg.mod.mute_blocked_perm", "reason", mute.reason())
                    : Lang.fmt("msg.mod.mute_blocked",
                            "duration", com.sablednah.standards.core.Duration.describe(left),
                            "reason", mute.reason()));
        });
    }

    /**
     * Send a formatted chat line ourselves, now that vanilla is not going to.
     *
     * <p>Taking over delivery means taking over what delivery did for us: {@code /ignore} was
     * vanilla's job a moment ago and is ours now, and the line still has to reach the console or
     * chat vanishes from the server log entirely.</p>
     */
    private static void deliver(MinecraftServer server, ServerPlayer from, Component line) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (StandardsAttachments.of(viewer).ignores(from.getUUID())) {
                continue;
            }
            viewer.sendSystemMessage(line);
        }
        server.sendSystemMessage(line);
    }

/**
     * Projectiles pass straight through a vanished player.
     *
     * <p>Cancelling the damage alone was not enough, and the half-fix was <em>worse</em> than
     * doing nothing: the arrow still collided, so it visibly bounced off empty air and rebounded
     * onto the shooter. An arrow deflecting off nothing does not hint that someone is there, it
     * marks the spot exactly. Found by shooting at a vanished player from close range.</p>
     *
     * <p>Note where this sits relative to the line vanish draws elsewhere (see {@link Vanish}):
     * the world's <em>reactions</em> stay visible — chests open, doors swing, footsteps sound —
     * because those are a trail worth leaving. A deflecting arrow is not a reaction to something
     * the hidden player did; it is the player's own body giving its position away, which is the
     * thing vanish exists to prevent.</p>
     */
    @SubscribeEvent
    static void onProjectileImpact(
            net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (!StandardsConfig.VANISH_INVULNERABLE.get()) return;
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof ServerPlayer target
                && Vanish.isVanished(target)) {
            event.setCanceled(true);
        }
    }

    // --- /back on death ---

    /**
     * Record where a player died so {@code /back} can return them to it. Gated on a permission
     * that is not granted by default: on a server where death is meant to cost something, handing
     * the way back to the corpse to everyone silently removes the cost.
     */
    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerState state = StandardsAttachments.of(player);
        if (!StandardsConfig.BACK_ON_DEATH.get()
                || !StandardsPermissions.has(player, StandardsPermissions.BACK_ON_DEATH)) {
            // Remember THAT they died even though we will not record WHERE, so /back can explain
            // itself rather than silently sending them to their last warp. See deathNotRecorded.
            state.setDeathNotRecorded(true);
            return;
        }
        state.pushBack(Waypoint.of(player), true);
    }

    private StandardsEvents() {}
}

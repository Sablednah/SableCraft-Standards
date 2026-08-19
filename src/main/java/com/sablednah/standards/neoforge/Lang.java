package com.sablednah.standards.neoforge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sablednah.standards.Standards;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Every string Standards ever shows a player, keyed and owner-editable.
 *
 * <p>Not vanilla translatable components, and the reason matters: a vanilla client does not carry
 * our lang file, so a {@code Component.translatable} would show it the raw key. Standards is
 * server-authoritative and its whole promise is that unmodified clients get the full experience,
 * so the <em>server</em> resolves text itself from {@code config/standards/messages.yml}.</p>
 *
 * <p>Owners edit that file for translation, for tone, or for terminology: a server whose currency
 * is "credits" sets {@code term.currency} once and every message follows. Templates use
 * <code>{placeholder}</code> for runtime values and may reference terms as
 * <code>{term.home}</code>; {@code &} colour codes work everywhere. The file is written with the
 * complete catalogue on first run, and missing keys fall back to the baked defaults — so an
 * upgrade that adds messages never breaks a customised file, and trimming the file down to just
 * your changes is fine.</p>
 */
public final class Lang {

    /** Baked defaults: the complete catalogue. Registration order is file order. */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static Map<String, String> active = new LinkedHashMap<>();

    static String def(String key, String template) {
        DEFAULTS.put(key, template);
        return key;
    }

    // --- term.* : the nouns a server re-skins ---
    static {
        def("term.prefix", "&7[&bStandards&7]&r");
        def("term.home", "home");
        def("term.homes", "homes");
        def("term.warp", "warp");
        def("term.warps", "warps");
        def("term.spawn", "spawn");
        def("term.balance", "balance");
    }

    // --- msg.common.* : the things every command needs to say ---
    static {
        def("msg.common.players_only", "&cOnly a player can run that.");
        def("msg.common.no_permission", "&cYou do not have permission to do that.");
        def("msg.common.player_not_found", "&cNo player called &f{name}&c.");
        def("msg.common.player_offline", "&c{name} is not online.");
        def("msg.common.unknown_world", "&cThat {what} is in a world this server no longer has ({dim}).");
        def("msg.common.disabled", "&cThat command is switched off on this server.");
        def("msg.common.for_other", " &7(for {player})");
    }

    // --- msg.toggle.* : /fly, /god and every other switch ---
    static {
        def("msg.toggle.self", "{term.prefix} &f{what}&7 is now {state}&7.");
        def("msg.toggle.other", "{term.prefix} &f{what}&7 for &f{player}&7 is now {state}&7.");
        def("msg.toggle.notified", "{term.prefix} &f{what}&7 was turned {state}&7 for you by &f{by}&7.");
        def("msg.toggle.on", "&aon");
        def("msg.toggle.off", "&coff");
        def("msg.toggle.already", "{term.prefix} &f{what}&7 was already {state}&7.");
        def("msg.toggle.many", "{term.prefix} &f{what}&7 {state} for &f{count}&7 players.");
        def("msg.toggle.flipped", "&eflipped");
        def("msg.toggle.fly", "Flight");
        def("msg.toggle.god", "God mode");
        def("msg.toggle.vanish", "Vanish");
    }

    // --- msg.tp.* : teleporting ---
    static {
        def("msg.tp.unsafe", "&cNowhere safe to land there.");
        def("msg.tp.no_world", "&cThat place is in a world this server no longer has.");
        def("msg.tp.warmup", "{term.prefix} &7Hold still for &f{sec}s&7...");
        def("msg.tp.warmup_tick", "&7Teleporting in &f{sec}&7...");
        def("msg.tp.warmup_moved", "&cTeleport cancelled — you moved.");
        def("msg.tp.warmup_damaged", "&cTeleport cancelled — you took damage.");
        def("msg.tp.cooldown", "&cYou must wait &f{sec}s&c before teleporting again.");
        def("msg.tp.done", "{term.prefix} &7Teleported.");
        def("msg.tp.top", "{term.prefix} &7Up you go — &f{y}&7 blocks above where you were.");
        def("msg.tp.top_already", "&7Nothing above you — you are already on top.");
        def("msg.tp.bottom", "{term.prefix} &7Down you go — &f{y}&7 blocks below where you were.");
        def("msg.tp.bottom_already", "&7Nothing below you — you are already at the bottom.");
        def("msg.tp.never_seen", "&cNo record of where &f{player}&c was last seen.");
        def("msg.tp.to_offline", "{term.prefix} &7To where &f{player}&7 logged out &8({place}).");
        def("msg.tp.jump_nothing", "&cNothing in range to jump to.");
        def("msg.tp.jump_done", "{term.prefix} &7Jumped &f{blocks}&7 blocks.");
        def("msg.tp.back_none", "&7Nowhere to go back to.");
        def("msg.tp.back_done", "{term.prefix} &7Returned to where you were &8({place}).");
        def("msg.tp.back_death", "{term.prefix} &7Returned to where you died &8({place}).");
    }

    // --- msg.spawn.* ---
    static {
        def("msg.spawn.went", "{term.prefix} &7Back to {term.spawn}.");
        def("msg.spawn.set", "{term.prefix} &7{term.spawn} set &8({place}).");
        def("msg.spawn.went_bed", "{term.prefix} &7Home to where you last slept.");
        def("msg.spawn.no_bed", "&7You have no bed or respawn anchor set.");
    }

    // --- msg.care.* : heal, feed, rest ---
    static {
        def("msg.care.heal.self", "{term.prefix} &aPatched up.");
        def("msg.care.heal.other", "{term.prefix} &7Healed &f{player}&7.");
        def("msg.care.heal.many", "{term.prefix} &7Healed &f{count}&7 players.");
        def("msg.care.heal.notified", "{term.prefix} &aYou were healed by &f{by}&a.");
        def("msg.care.feed.self", "{term.prefix} &aFed.");
        def("msg.care.feed.other", "{term.prefix} &7Fed &f{player}&7.");
        def("msg.care.feed.many", "{term.prefix} &7Fed &f{count}&7 players.");
        def("msg.care.feed.notified", "{term.prefix} &aYou were fed by &f{by}&a.");
        def("msg.care.rest.self", "{term.prefix} &aRested — the phantoms have forgotten you.");
        def("msg.care.rest.other", "{term.prefix} &7Rested &f{player}&7.");
        def("msg.care.rest.many", "{term.prefix} &7Rested &f{count}&7 players.");
        def("msg.care.rest.notified", "{term.prefix} &aYou were rested by &f{by}&a.");
    }

    // --- msg.speed.* ---
    static {
        def("msg.speed.set", "{term.prefix} &7{what} speed set to &f{amount}x&7.");
        def("msg.speed.reset", "{term.prefix} &7Speeds back to normal.");
        def("msg.speed.walking", "Walking");
        def("msg.speed.flying", "Flying");
        def("msg.speed.too_fast", "&cThe most this server allows is &f{max}x&c.");
    }

    // --- msg.gc.* : server health ---
    static {
        def("msg.gc.header", "{term.prefix} &7Server health:");
        def("msg.gc.tps", " &7Tick rate: {colour}{tps} TPS&7 &8({ms} ms/tick)");
        def("msg.gc.memory", " &7Memory: &f{used}MB&7 used of &f{allocated}MB&7 allocated, "
                + "&f{max}MB&7 max &8({percent}%)");
        def("msg.gc.uptime", " &7Uptime: &f{uptime}&7, &f{players}&7/&f{max}&7 players");
        def("msg.gc.dimension", " &7{dimension}: &f{entities}&7 entities, &f{chunks}&7 chunks");
        def("msg.gc.worst_header", " &7Most numerous entities:");
        def("msg.gc.worst_line", "  &8-&r &f{count}x&7 {type}");
    }

    // --- msg.kit.* ---
    static {
        def("msg.kit.none", "&7No kits are available to you.");
        def("msg.kit.list", "{term.prefix} &7Kits: &f{list}");
        def("msg.kit.unknown", "&cNo kit called &f{name}&c. &7Try: {list}");
        def("msg.kit.not_yours", "&cThe &f{name}&c kit is not for you.");
        def("msg.kit.cooldown", "&cYou can take &f{name}&c again in &f{duration}&c.");
        def("msg.kit.given", "{term.prefix} &7Here is your &f{name}&7 kit.");
        def("msg.kit.given_dropped", "{term.prefix} &7Here is your &f{name}&7 kit — "
                + "&e{count}&7 items would not fit and are at your feet.");
        def("msg.kit.defined", "{term.prefix} &7Kit &f{name}&7 saved: &f{count}&7 items ({scope}).");
        def("msg.kit.redefined", "{term.prefix} &7Kit &f{name}&7 replaced: &f{count}&7 items ({scope}).");
        def("msg.kit.deleted", "{term.prefix} &7Kit &f{name}&7 deleted.");
        def("msg.kit.empty_capture", "&cYou are not carrying anything to save.");
        def("msg.kit.name_rules", "&cKit names: 1-32 letters, numbers, _ or -.");
        def("msg.kit.contents_header", "{term.prefix} &7Kit &f{name}&7 &8({cooldown})&7:");
        def("msg.kit.no_cooldown", "no cooldown");
        def("msg.kit.contents_line", " &8-&r &f{count}x&7 {item}");
    }

    // --- msg.mail.* ---
    static {
        def("msg.mail.sent", "{term.prefix} &7Posted to &f{player}&7.");
        def("msg.mail.arrived", "{term.prefix} &e✉ &f{player}&7 sent you mail — &f/mail read");
        def("msg.mail.waiting", "{term.prefix} &e✉ You have &f{count}&e unread mail — &f/mail read");
        def("msg.mail.empty", "&7No mail.");
        def("msg.mail.full", "&c{player}'s mailbox is full.");
        def("msg.mail.header", "{term.prefix} &7Mail &8({count})&7:");
        def("msg.mail.line", " &8-&r &7{player} &8({ago} ago)&7: {message}");
        def("msg.mail.line_new", " &e-&r &f{player} &8({ago} ago)&f: {message}");
        def("msg.mail.cleared", "{term.prefix} &7Threw away &f{count}&7 letters.");
    }

    // --- msg.afk.* ---
    static {
        def("msg.afk.now_away", "&7* &f{player}&7 is now away.");
        def("msg.afk.now_away_reason", "&7* &f{player}&7 is now away &8({reason})");
        def("msg.afk.back", "&7* &f{player}&7 is back &8(away {duration})");
        def("msg.afk.kicked", "&cDisconnected after &f{duration}&c idle.");
    }

    // --- msg.pm.* : private messages ---
    static {
        def("msg.pm.sent", "&8[&7me &8-> &f{player}&8]&7 {message}");
        def("msg.pm.received", "&8[&f{player} &8-> &7me&8]&7 {message}");
        def("msg.pm.spy", "&8[spy] [{from} -> {to}] {message}");
        def("msg.pm.self", "&7Talking to yourself is a sign you need a break.");
        def("msg.pm.refusing", "&c{player} is not accepting messages right now.");
        def("msg.pm.nobody_to_reply", "&7Nobody has messaged you yet.");
        def("msg.pm.reply_gone", "&cThey are no longer online.");
        def("msg.pm.ignored", "{term.prefix} &7Ignoring &f{player}&7 — their messages will not reach you.");
        def("msg.pm.unignored", "{term.prefix} &7No longer ignoring &f{player}&7.");
        def("msg.pm.ignore_self", "&7You cannot ignore yourself, however tempting.");
        def("msg.pm.ignore_none", "&7You are not ignoring anyone.");
        def("msg.pm.ignore_list", "{term.prefix} &7Ignoring: &f{list}");
        def("msg.pm.toggle_name", "Incoming messages");
        def("msg.pm.spy_toggle_name", "Social spy");
    }

    // --- msg.mod.* : moderation ---
    static {
        def("msg.mod.default_reason", "No reason given");
        def("msg.mod.bad_duration", "&cNot a duration I understand: &f{input}&c. "
                + "&7Try 30m, 2h30m, 7d.");
        def("msg.mod.banned", "{term.prefix} &7Banned &f{player}&7 for &f{duration}&7. "
                + "&8({reason})");
        def("msg.mod.ban_screen", "&cYou are banned for &f{duration}&c.\n&7{reason}");
        def("msg.mod.muted", "{term.prefix} &7Muted &f{player}&7 for &f{duration}&7. "
                + "&8({reason})");
        def("msg.mod.muted_you", "&cYou have been muted for &f{duration}&c. &7{reason}");
        def("msg.mod.mute_blocked", "&cYou are muted for another &f{duration}&c. &7{reason}");
        def("msg.mod.mute_blocked_perm", "&cYou are muted. &7{reason}");
        def("msg.mod.not_muted", "&c{player} is not muted.");
        def("msg.mod.unmuted", "{term.prefix} &7Unmuted &f{player}&7.");
        def("msg.mod.unmuted_you", "&aYou can speak again.");
        def("msg.mod.invsee_self", "&7You can see your own inventory by pressing E.");
        def("msg.mod.invsee_title", "{player}'s inventory");
    }

    // --- msg.smite.* ---
    static {
        def("msg.smite.done", "{term.prefix} &e⚡ Smote &f{player}&e.");
        def("msg.smite.many", "{term.prefix} &e⚡ Smote &f{count}&e players.");
        def("msg.smite.here", "{term.prefix} &e⚡");
        def("msg.smite.nothing", "&cNothing in range to smite.");
    }

    // --- msg.tpa.* : teleport requests ---
    static {
        def("term.tpa", "teleport request");

        def("msg.tpa.sent", "{term.prefix} &7Asked &f{player}&7 if you may teleport to them. "
                + "&8(expires in {sec}s)");
        def("msg.tpa.sent_here", "{term.prefix} &7Asked &f{player}&7 to teleport to you. "
                + "&8(expires in {sec}s)");
        def("msg.tpa.received", "{term.prefix} &f{player}&7 would like to teleport to you.");
        def("msg.tpa.received_here", "{term.prefix} &f{player}&7 would like you to teleport to them.");
        def("msg.tpa.button_accept", "&a&l[Accept]");
        def("msg.tpa.button_deny", "&c&l[Deny]");
        def("msg.tpa.button_accept_tip", "Accept {player}'s {term.tpa}");
        def("msg.tpa.button_deny_tip", "Turn down {player}'s {term.tpa}");
        def("msg.tpa.self", "&7You are already where you are.");
        def("msg.tpa.duplicate", "&7You have already asked &f{player}&7 — wait for an answer.");
        def("msg.tpa.refusing", "&c{player} is not accepting {term.tpa}s right now.");
        def("msg.tpa.none_incoming", "&7No open {term.tpa}s.");
        def("msg.tpa.none_from", "&cNo open {term.tpa} from &f{name}&c.");
        def("msg.tpa.none_outgoing", "&7You have no {term.tpa}s waiting for an answer.");
        // Dead ends turned into signposts. /tpa tab-completes to tpacancel before tpaccept, and
        // on /tpahere the person who asked is not the person who accepts — both land someone in
        // the wrong command with nothing useful to do next.
        def("msg.tpa.none_outgoing_but_incoming", "&7Nothing to cancel — but &f{count}&7 "
                + "{term.tpa}(s) are waiting for YOUR answer, from &f{player}&7.");
        def("msg.tpa.none_incoming_but_outgoing", "&7Nothing to accept — you asked &f{player}&7, "
                + "so it is their answer you are waiting on.");
        def("msg.tpa.button_accept_generic", "Accept it");
        def("msg.tpa.gone", "&c{player} is no longer online.");

        // The gap this whole feature was built around: with a warmup, an accepted request used to
        // sit silent for five seconds at BOTH ends.
        def("msg.tpa.accepted_by_you", "{term.prefix} &aAccepted&7 — &f{player}&7 is on their way.");
        def("msg.tpa.accepted_by_you_wait", "{term.prefix} &aAccepted&7 — &f{player}&7 arrives in &f{sec}s&7.");
        def("msg.tpa.accepted_you_go", "{term.prefix} &f{player}&7 &aaccepted&7! Teleporting now.");
        def("msg.tpa.accepted_you_wait", "{term.prefix} &f{player}&7 &aaccepted&7! Teleporting in "
                + "&f{sec}s&7 — hold still.");
        // The /tpahere side: the person who ACCEPTED is the one who travels, so "they accepted"
        // would be nonsense addressed to them.
        def("msg.tpa.accepted_here_go", "{term.prefix} &7Off you go to &f{player}&7.");
        def("msg.tpa.accepted_here_wait", "{term.prefix} &7Teleporting to &f{player}&7 in "
                + "&f{sec}s&7 — hold still.");
        def("msg.tpa.arrived_host", "{term.prefix} &f{player}&7 has arrived.");
        def("msg.tpa.failed_host", "{term.prefix} &f{player}&7 did not make it &8({reason}).");
        def("msg.tpa.reason_moved", "they moved");
        def("msg.tpa.reason_damaged", "they were attacked");
        def("msg.tpa.reason_unsafe", "nowhere safe to land");
        def("msg.tpa.reason_left", "cancelled");

        def("msg.tpa.denied_by_you", "{term.prefix} &7Turned down &f{player}&7's {term.tpa}.");
        def("msg.tpa.denied_you", "{term.prefix} &f{player}&7 turned down your {term.tpa}.");
        def("msg.tpa.cancelled_by_you", "{term.prefix} &7Withdrew your {term.tpa} to &f{player}&7.");
        def("msg.tpa.cancelled_you", "{term.prefix} &f{player}&7 withdrew their {term.tpa}.");
        def("msg.tpa.expired_sender", "&e\u231b Your {term.tpa} to &f{player}&e lapsed &8(no answer in time).");
        def("msg.tpa.expired_target", "&e\u231b &f{player}&e's {term.tpa} lapsed &8(you did not answer in time).");

        def("msg.tpa.toggle_name", "Incoming {term.tpa}s");
        def("msg.tpa.list_header", "{term.prefix} &7Open {term.tpa}s:");
        def("msg.tpa.list_row", " &7-&r &f{player} &8({dir}, {sec}s left)");
        def("msg.tpa.dir_to_you", "to you");
        def("msg.tpa.dir_to_them", "you to them");
    }

    // --- msg.home.* ---
    static {
        def("msg.home.set", "{term.prefix} &7{term.home} &f{name}&7 set &8({place}).");
        def("msg.home.moved", "{term.prefix} &7{term.home} &f{name}&7 moved here &8({place}).");
        def("msg.home.deleted", "{term.prefix} &7{term.home} &f{name}&7 deleted.");
        def("msg.home.unknown", "&cYou have no {term.home} called &f{name}&c. &7Try: {list}");
        def("msg.home.none", "&7You have no {term.homes} yet — &f/sethome&7 sets one.");
        def("msg.home.list", "{term.prefix} &7Your {term.homes} &8({count}/{limit})&7: {list}");
        def("msg.home.limit", "&cYou may only have &f{limit}&c {term.homes}. Delete one first, "
                + "or overwrite it with &f/sethome {name}&c.");
        def("msg.home.name_rules", "&c{term.home} names: 1-32 letters, numbers, _ or -.");
        def("msg.home.went", "{term.prefix} &7Home to &f{name}&7.");
        def("msg.home.unlimited", "unlimited");
    }

    // --- msg.warp.* ---
    static {
        def("msg.warp.set", "{term.prefix} &7{term.warp} &f{name}&7 set &8({place}).");
        def("msg.warp.moved", "{term.prefix} &7{term.warp} &f{name}&7 moved here &8({place}).");
        def("msg.warp.deleted", "{term.prefix} &7{term.warp} &f{name}&7 deleted.");
        def("msg.warp.unknown", "&cNo {term.warp} called &f{name}&c. &7Try: {list}");
        def("msg.warp.none", "&7This server has no {term.warps} yet.");
        def("msg.warp.list", "{term.prefix} &7{term.warps} &8({count})&7: &f{list}");
        def("msg.warp.went", "{term.prefix} &7Warped to &f{name}&7.");
        def("msg.warp.name_rules", "&c{term.warp} names: 1-32 letters, numbers, _ or -.");
    }

    // --- msg.eco.* ---
    static {
        def("msg.eco.disabled", "&cThere is no economy on this server.");
        def("msg.eco.balance_self", "{term.prefix} &7Your {term.balance}: &a{amount}");
        def("msg.eco.balance_other", "{term.prefix} &f{player}&7's {term.balance}: &a{amount}");
        def("msg.eco.no_account", "&c{player} has no account.");
        def("msg.eco.paid", "{term.prefix} &7Paid &a{amount}&7 to &f{player}&7. "
                + "New {term.balance}: &a{balance}");
        def("msg.eco.received", "{term.prefix} &f{player}&7 paid you &a{amount}&7. "
                + "New {term.balance}: &a{balance}");
        def("msg.eco.insufficient", "&cYou only have &f{balance}&c — that costs &f{amount}&c.");
        def("msg.eco.invalid_amount", "&cThat is not an amount I can pay: &f{input}");
        def("msg.eco.not_positive", "&cPay a positive amount.");
        def("msg.eco.pay_self", "&7Moving money from one of your pockets to the other achieves little.");
        def("msg.eco.refused", "&cThe economy refused that transaction.");
        def("msg.eco.admin_gave", "{term.prefix} &7Gave &a{amount}&7 to &f{player}&7 "
                + "(now &a{balance}&7).");
        def("msg.eco.admin_took", "{term.prefix} &7Took &c{amount}&7 from &f{player}&7 "
                + "(now &a{balance}&7).");
        def("msg.eco.admin_set", "{term.prefix} &7Set &f{player}&7's {term.balance} to &a{amount}&7.");
        def("msg.eco.baltop_header", "{term.prefix} &7Richest accounts:");
        def("msg.eco.baltop_row", " &8{rank}.&r &f{player} &7— &a{amount}");
        def("msg.eco.baltop_unsupported", "&7The active economy cannot list accounts.");
        def("msg.eco.provider", "{term.prefix} &7Economy provider: &f{name}&7 (priority {priority}).");
        def("msg.eco.provider_none", "{term.prefix} &7No economy provider is registered.");
    }

    // --- msg.admin.* ---
    static {
        def("msg.admin.reloaded", "{term.prefix} &7messages.yml reloaded.");
    }

    /** Resolve a key to its (term-substituted) template. An unknown key returns itself, loudly. */
    public static String get(String key) {
        String template = active.getOrDefault(key, DEFAULTS.get(key));
        if (template == null) {
            Standards.LOGGER.warn("Missing message key '{}'", key);
            return key;
        }
        return substituteTerms(template);
    }

    /** {@code fmt("msg.home.set", "name", home, "place", where)} — key/value pairs. */
    public static String fmt(String key, Object... kv) {
        String out = get(key);
        for (int n = 0; n + 1 < kv.length; n += 2) {
            out = out.replace("{" + kv[n] + "}", String.valueOf(kv[n + 1]));
        }
        return out;
    }

    /** A term by short name: {@code term("home")} → "home", or whatever the owner renamed it to. */
    public static String term(String name) {
        return get("term." + name);
    }

    private static String substituteTerms(String template) {
        if (!template.contains("{term.")) return template;
        String out = template;
        for (String key : DEFAULTS.keySet()) {
            if (!key.startsWith("term.")) continue;
            String marker = "{" + key + "}";
            if (out.contains(marker)) {
                out = out.replace(marker, active.getOrDefault(key, DEFAULTS.get(key)));
            }
        }
        return out;
    }

    // --- file lifecycle ---

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("standards").resolve("messages.yml");
    }

    /** Load overrides; write the full catalogue out if the file is absent. */
    public static synchronized void load() {
        Path path = file();
        try {
            if (!Files.exists(path)) {
                writeDefaults(path);
            }
            Map<String, String> loaded = new LinkedHashMap<>();
            Object parsed = new org.yaml.snakeyaml.Yaml().load(Files.readString(path));
            if (parsed instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k != null && v != null) loaded.put(String.valueOf(k), String.valueOf(v));
                });
            }
            active = loaded;
            Standards.LOGGER.info("Loaded {} message overrides from messages.yml ({} keys in the catalogue)",
                    loaded.size(), DEFAULTS.size());
        } catch (Exception e) {
            Standards.LOGGER.error("Could not load messages.yml — using defaults", e);
            active = new LinkedHashMap<>();
        }
    }

    private static void writeDefaults(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# Standards messages & vocabulary — every player-facing string.\n");
        sb.append("# Edit freely: translation, tone, or wholesale terminology.\n");
        sb.append("# A space server might set:  term.warp: \"jump point\"\n");
        sb.append("# A hard-currency server:    term.balance: \"credits\"\n");
        sb.append("# {curly} placeholders are filled at runtime; {term.x} pulls a term from this\n");
        sb.append("# file; '&' colour codes work everywhere. Deleted keys fall back to these\n");
        sb.append("# defaults, so trimming the file to just your changes is fine.\n");
        sb.append("# Applied on restart and on /standards reload.\n\n");
        String section = "";
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            String key = entry.getKey();
            String prefix = key.contains(".") ? key.substring(0, key.indexOf('.')) : "";
            if (!prefix.equals(section)) {
                section = prefix;
                sb.append("\n# --- ").append(section).append(" ---\n");
            }
            sb.append(key).append(": \"")
                    .append(entry.getValue().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"\n");
        }
        Files.writeString(path, sb.toString());
        Standards.LOGGER.info("Wrote default messages.yml with {} keys", DEFAULTS.size());
    }

    private Lang() {}
}

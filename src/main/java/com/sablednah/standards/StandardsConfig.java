package com.sablednah.standards;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-owner tuning.
 *
 * <p>Two principles, both learned from what other essentials packages get wrong:</p>
 *
 * <ul>
 * <li><b>Every command can be switched off.</b> A modpack that already ships a homes mod should
 *     be able to keep ours out of the way without uninstalling anything, and a command that is
 *     off must not register at all — a greyed-out suggestion in the client's tab-complete is
 *     worse than an absent one.</li>
 * <li><b>Limits are permission-aware, not config-aware.</b> The numbers here are the fallback
 *     for a server with no permissions manager; where a per-group answer makes sense
 *     ({@code standards.home.limit.<n>}) LuckPerms wins. See {@link
 *     com.sablednah.standards.neoforge.StandardsPermissions}.</li>
 * </ul>
 *
 * <p>Values are read live via {@code .get()} so edits apply without a restart — except the
 * {@code enable*} switches, which decide what gets registered and therefore need one.</p>
 */
public final class StandardsConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- command switches ---
    public static final ModConfigSpec.BooleanValue ENABLE_FLY;
    public static final ModConfigSpec.BooleanValue ENABLE_GOD;
    public static final ModConfigSpec.BooleanValue ENABLE_TOP;
    public static final ModConfigSpec.BooleanValue ENABLE_JUMP;
    public static final ModConfigSpec.BooleanValue ENABLE_BACK;
    public static final ModConfigSpec.BooleanValue ENABLE_HOMES;
    public static final ModConfigSpec.BooleanValue ENABLE_WARPS;
    public static final ModConfigSpec.BooleanValue ENABLE_TPA;
    public static final ModConfigSpec.BooleanValue ENABLE_VANISH;
    public static final ModConfigSpec.BooleanValue ENABLE_SMITE;
    public static final ModConfigSpec.BooleanValue VANISH_INVULNERABLE;
    public static final ModConfigSpec.BooleanValue VANISH_PICKUP;
    public static final ModConfigSpec.ConfigValue<String> STATION_ACCESS;
    public static final ModConfigSpec.ConfigValue<String> BACK_ON_DEATH_ACCESS;
    public static final ModConfigSpec.BooleanValue ENABLE_GROUPS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> CHAT_GROUP_KINDS;
    public static final ModConfigSpec.IntValue GROUP_INVITE_TIMEOUT;
    public static final ModConfigSpec.IntValue GROUP_HOME_LIMIT;
    public static final ModConfigSpec.BooleanValue GROUP_TP_SKIP_COOLDOWN;
    public static final ModConfigSpec.BooleanValue GROUP_TP_SKIP_WARMUP;
    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN;
    public static final ModConfigSpec.BooleanValue ENABLE_SELFCARE;
    public static final ModConfigSpec.BooleanValue ENABLE_SPEED;
    public static final ModConfigSpec.BooleanValue ENABLE_BOTTOM;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVER_HEALTH;
    public static final ModConfigSpec.BooleanValue ENABLE_KITS;
    public static final ModConfigSpec.BooleanValue ENABLE_MAIL;
    public static final ModConfigSpec.BooleanValue ENABLE_TP_OFFLINE;
    public static final ModConfigSpec.BooleanValue ENABLE_AFK;
    public static final ModConfigSpec.BooleanValue ENABLE_MESSAGING;
    public static final ModConfigSpec.BooleanValue ENABLE_MODERATION;
    public static final ModConfigSpec.BooleanValue ENABLE_STATIONS;
    public static final ModConfigSpec.BooleanValue ENABLE_ECONOMY;

    // --- teleporting ---
    public static final ModConfigSpec.IntValue TELEPORT_WARMUP;
    public static final ModConfigSpec.BooleanValue WARMUP_CANCEL_ON_MOVE;
    public static final ModConfigSpec.BooleanValue WARMUP_CANCEL_ON_DAMAGE;
    public static final ModConfigSpec.IntValue COMBAT_PVP_SECONDS;
    public static final ModConfigSpec.IntValue COMBAT_PVE_SECONDS;
    public static final ModConfigSpec.IntValue COMBAT_SKILL_SECONDS;
    public static final ModConfigSpec.BooleanValue COMBAT_PVP_BLOCKS_TELEPORT;
    public static final ModConfigSpec.BooleanValue COMBAT_PVE_BLOCKS_TELEPORT;
    public static final ModConfigSpec.BooleanValue COMBAT_SKILL_BLOCKS_TELEPORT;
    public static final ModConfigSpec.BooleanValue COMBAT_CLEAR_ON_DEATH;
    public static final ModConfigSpec.BooleanValue COMBAT_LOG;
    public static final ModConfigSpec.IntValue TELEPORT_COOLDOWN;
    public static final ModConfigSpec.IntValue TPA_TIMEOUT;
    public static final ModConfigSpec.BooleanValue TPA_FOLLOW_TARGET;
    public static final ModConfigSpec.IntValue BACK_HISTORY;
    public static final ModConfigSpec.BooleanValue BACK_ON_DEATH;
    public static final ModConfigSpec.IntValue SAFE_LOC_SEARCH;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TOP_BARRIERS;

    // --- homes ---
    public static final ModConfigSpec.IntValue DEFAULT_HOME_LIMIT;
    public static final ModConfigSpec.IntValue MAX_HOME_LIMIT_NODE;

    // --- economy ---
    public static final ModConfigSpec.DoubleValue STARTING_BALANCE;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_SINGULAR;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_PLURAL;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_SYMBOL;
    public static final ModConfigSpec.BooleanValue CURRENCY_SYMBOL_BEFORE;
    public static final ModConfigSpec.IntValue CURRENCY_DECIMALS;
    public static final ModConfigSpec.BooleanValue ALLOW_NEGATIVE_BALANCE;
    public static final ModConfigSpec.BooleanValue PREFER_OWN_LEDGER;
    public static final ModConfigSpec.IntValue BALTOP_SIZE;
    public static final ModConfigSpec.DoubleValue MAX_SPEED;
    public static final ModConfigSpec.IntValue AFK_AFTER_SECONDS;
    public static final ModConfigSpec.IntValue AFK_KICK_SECONDS;
    public static final ModConfigSpec.BooleanValue AFK_ANNOUNCE;
    public static final ModConfigSpec.IntValue MAIL_LIMIT;
    // --- built-in permissions ---
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_PERMISSION_GROUP;
    public static final ModConfigSpec.BooleanValue PUBLISH_PERMISSION_ROLES;

    public static final ModConfigSpec.ConfigValue<String> CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> CHAT_AFFIX_SEPARATOR;
    public static final ModConfigSpec.BooleanValue CHAT_ALWAYS_FORMAT;

    static {
        BUILDER.comment("Which command families exist at all. Turning one off unregisters its",
                        "commands entirely rather than refusing them, so tab-complete stays honest.",
                        "Changing these needs a server restart.")
                .push("commands");
        ENABLE_FLY = BUILDER.define("fly", true);
        ENABLE_GOD = BUILDER.define("god", true);
        ENABLE_TOP = BUILDER.define("top", true);
        ENABLE_JUMP = BUILDER.define("jump", true);
        ENABLE_BACK = BUILDER.define("back", true);
        ENABLE_HOMES = BUILDER.define("homes", true);
        ENABLE_WARPS = BUILDER.define("warps", true);
        ENABLE_TPA = BUILDER
                .comment("/tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel and /tptoggle.")
                .define("tpa", true);
        ENABLE_VANISH = BUILDER
                .comment("/vanish and /v. Also exposed to other mods via the PlayerSwitches API.")
                .define("vanish", true);
        VANISH_INVULNERABLE = BUILDER
                .comment("A vanished player takes no damage.",
                        "On by default because 'not there' is the whole idea: hidden staff should",
                        "not be shot by someone spraying arrows at a rough guess, and should not",
                        "drown in a wall they walked into while watching a griefer. Turn it off",
                        "if you want vanish to hide you without also protecting you.")
                .define("vanishInvulnerable", true);
        VANISH_PICKUP = BUILDER
                .comment("Whether a vanished player picks items up. Off.",
                        "Named for the behaviour rather than its negation, so 'false' reads the",
                        "same way whichever end you approach it from.",
                        "Two reasons, and the first is the one that matters: an item vanishing off",
                        "the floor with nobody standing there gives you away as surely as being",
                        "seen. Unlike opening a chest, which stays deliberately visible, picking up",
                        "is not something you chose to do — you walked past it.",
                        "The second is that hidden staff watching a fight should not quietly",
                        "collect the loot. Turn it on if vanish is meant to be a free camera that",
                        "can still tidy up.")
                .define("vanishPickup", false);
        STATION_ACCESS = BUILDER
                .comment("Who may use the portable workstations — /craft, /anvil, /grindstone,",
                        "/enderchest, /trashcan — when no permissions mod is installed.",
                        "  nobody   (default) only a permissions mod can grant them. This is what",
                        "           lets a LegendQuest skill hand a builder a workbench for twenty",
                        "           seconds and take it back.",
                        "  ops      operators may use them.",
                        "  everyone anyone may.",
                        "A permissions mod still overrides this either way — it only decides the",
                        "answer for servers that have no way to grant anything, where 'nobody'",
                        "means the commands can never be used by anybody at all.")
                .define("stationAccess", "nobody");
        BACK_ON_DEATH_ACCESS = BUILDER
                .comment("Who may use /back to return to where they died, with no permissions mod.",
                        "Same three values as stationAccess, and the same reasoning: returning to",
                        "your corpse is a real gameplay decision, so it defaults to nobody — but on",
                        "a server that cannot grant permissions, nobody means nobody, ever.")
                .define("backOnDeathAccess", "nobody");
        ENABLE_GROUPS = BUILDER
                .comment("The built-in lightweight groups: /group and its subcommands, plus the",
                        "standards:group kind offered through the groups API.",
                        "Turn it off on a pack running a faction mod — that mod registers its own",
                        "kind, and two group systems side by side is exactly the confusion the API",
                        "exists to prevent.")
                .define("groups", true);
        GROUP_INVITE_TIMEOUT = BUILDER
                .comment("Seconds a group invite stays open. 0 means it never expires.",
                        "Off by default: on a small server an invite that quietly evaporates is",
                        "more annoying than one that lingers, and the person who sent it is not",
                        "told either way. Set it if invites are piling up.")
                .defineInRange("groupInviteTimeoutSeconds", 0, 0, 2_592_000);
        GROUP_HOME_LIMIT = BUILDER
                .comment("Shared homes a group may have. -1 for no limit.",
                        "Set by the owner, reachable by every member, and separate from personal",
                        "homes on purpose: 'my bedroom' and 'our base' are different things, and",
                        "merging them would mean nobody can have a private home any more.")
                .defineInRange("groupHomeLimit", 3, -1, 10_000);
        GROUP_TP_SKIP_COOLDOWN = BUILDER
                .comment("Teleporting to somebody in your own group ignores the teleport cooldown.",
                        "On by default. The cooldown is a rate limit against strangers; inside a",
                        "group it is friction with nothing to protect.")
                .define("groupTeleportSkipsCooldown", true);
        GROUP_TP_SKIP_WARMUP = BUILDER
                .comment("...and ignores the warmup countdown too.",
                        "OFF by default, and the two are separate on purpose. The warmup is the",
                        "anti-combat-log half — it is what stops somebody escaping a fight — and",
                        "your own group is exactly who you would arrange that with. Turn it on for",
                        "a co-operative server where nobody is fighting anybody.")
                .define("groupTeleportSkipsWarmup", false);
        ENABLE_SMITE = BUILDER
                .comment("/smite — lightning on a target or wherever you are looking. Op-gated.")
                .define("smite", true);
        ENABLE_SPAWN = BUILDER
                .comment("/spawn, /setspawn and /playerspawn.")
                .define("spawn", true);
        ENABLE_SELFCARE = BUILDER
                .comment("/heal, /feed and /rest.")
                .define("selfCare", true);
        ENABLE_SPEED = BUILDER
                .comment("/speed — a multiplier of vanilla's own walking and flying speeds.")
                .define("speed", true);
        ENABLE_BOTTOM = BUILDER
                .comment("/bottom — down to the lowest place it is safe to stand. Op-only by",
                        "default, since near bedrock it doubles as a crude ore finder.")
                .define("bottom", true);
        ENABLE_SERVER_HEALTH = BUILDER
                .comment("/gc, /tps, /lag, /mem — tick rate, memory, uptime and entity counts.")
                .define("serverHealth", true);
        ENABLE_KITS = BUILDER
                .comment("/kit, /kits, /setkit, /delkit and /showkit. Kits are defined in game by",
                        "equipping yourself and saving, not by writing item ids into a file.")
                .define("kits", true);
        ENABLE_MAIL = BUILDER
                .comment("/mail — the counterpart to /msg, for when they are not online.")
                .define("mail", true);
        ENABLE_TP_OFFLINE = BUILDER
                .comment("/tpoffline — to where a player logged out. Op-only.")
                .define("tpOffline", true);
        ENABLE_AFK = BUILDER
                .comment("/afk and /lurk, plus the automatic idle detection that makes them",
                        "worth having.")
                .define("afk", true);
        ENABLE_MESSAGING = BUILDER
                .comment("/msg and its aliases, /r, /ignore, /msgtoggle and /socialspy.",
                        "Vanilla has /msg but no /r, which is the half people actually use.")
                .define("messaging", true);
        ENABLE_MODERATION = BUILDER
                .comment("/tempban, /mute, /unmute and /invsee — the three gaps vanilla and",
                        "LuckPerms leave. Standards deliberately goes no further than this.")
                .define("moderation", true);
        ENABLE_STATIONS = BUILDER
                .comment("Portable workstations: /craft, /anvil, /grindstone, /enderchest,",
                        "/trashcan. Registered but denied to everyone by default — grant the",
                        "standards.<name> node to a rank, or drive them from another mod through",
                        "the Stations API.")
                .define("stations", true);
        ENABLE_ECONOMY = BUILDER
                .comment("The built-in economy: /balance, /pay, /eco and the economy API.",
                        "Off also stops Standards registering itself as an economy provider,",
                        "which is what you want if a dedicated economy mod is installed.")
                .define("economy", true);
        BUILDER.pop();

        BUILDER.comment("Teleporting: every command that moves a player goes through these.")
                .push("teleport");
        TELEPORT_WARMUP = BUILDER
                .comment("Seconds a player must stand still before a queued teleport fires.",
                        "0 teleports immediately. Bypassed by standards.teleport.instant.")
                .defineInRange("warmupSeconds", 0, 0, 300);
        WARMUP_CANCEL_ON_MOVE = BUILDER
                .comment("Moving during the warmup cancels the teleport.")
                .define("cancelOnMove", true);
        WARMUP_CANCEL_ON_DAMAGE = BUILDER
                .comment("Taking damage during the warmup cancels the teleport — the anti-combat-log",
                        "half of a warmup, and the reason a warmup is worth having at all.")
                .define("cancelOnDamage", true);
        TELEPORT_COOLDOWN = BUILDER
                .comment("Seconds between teleports. Bypassed by standards.teleport.nocooldown.")
                .defineInRange("cooldownSeconds", 0, 0, 86_400);
        TPA_TIMEOUT = BUILDER
                .comment("Seconds a /tpa request stays open before it lapses. Both ends are told",
                        "when it does — a request that vanishes silently reads as a bug.")
                .defineInRange("tpaTimeoutSeconds", 120, 5, 3600);
        TPA_FOLLOW_TARGET = BUILDER
                .comment("Where an accepted /tpa actually lands you, when a warmup is configured.",
                        "true  - wherever they are when the countdown ends. 'Teleport to the",
                        "        player' taken literally; you end up next to them even if they",
                        "        kept walking, in whichever dimension they are in by then.",
                        "false - where they were standing when they accepted. Predictable, and",
                        "        immune to being walked somewhere unpleasant during the count.",
                        "Either way the safe-landing search still applies, so neither setting",
                        "can drop you into lava.")
                .define("tpaFollowTarget", true);
        BACK_HISTORY = BUILDER
                .comment("How many previous locations /back remembers. 1 is the classic behaviour;",
                        "more lets '/back 2' walk further up the trail.")
                .defineInRange("backHistory", 5, 1, 50);
        BACK_ON_DEATH = BUILDER
                .comment("Record the death site so /back returns to it. Needs",
                        "standards.back.ondeath, which is NOT granted by default — returning to",
                        "your corpse is a real gameplay decision, not a convenience.")
                .define("backOnDeath", true);
        SAFE_LOC_SEARCH = BUILDER
                .comment("How many blocks up/down to search for a safe landing spot before giving",
                        "up. 0 disables the safety check entirely (arrive exactly where asked).")
                .defineInRange("safeSearchRange", 16, 0, 64);
        BUILDER.pop();

        BUILDER.comment("Combat, and what it stops you doing.",
                        "A teleport is an escape hatch: /home mid-fight is not a clever play, it",
                        "is the fight not happening, and whoever was winning has no recourse.",
                        "",
                        "AN ATTACKER STARTS A TAG, NOT DAMAGE. Fall, drowning, cactus, fire and",
                        "freezing never tag anybody — a player trapped in powder snow inside a",
                        "protected claim has a teleport as their only way out, and tagging them",
                        "for the freezing shuts that too.",
                        "",
                        "Tags EXTEND, they never overwrite: tagged for 12 seconds of PvP and then",
                        "clipped by a zombie for 8, the answer is 12. Otherwise a shorter tag",
                        "would rescue the person fleeing, who is exactly who this stops.")
                .push("combat");
        COMBAT_PVP_SECONDS = BUILDER
                .comment("Seconds in combat after a player hits you, or you hit them.",
                        "Includes arrows and pets — the owner is resolved, not the projectile.",
                        "0 turns the whole PvP branch off, for a co-operative server.",
                        "Modern practice is SHORT tags. Ten to fifteen seconds, against the",
                        "thirty to sixty of the Factions era: long tags punish ordinary play, and",
                        "being unable to /home for a minute because a skeleton shot you erodes",
                        "trust in the mechanic faster than the occasional escape does.")
                .defineInRange("pvpSeconds", 12, 0, 600);
        COMBAT_PVE_SECONDS = BUILDER
                .comment("Seconds in combat after a mob hits you, or you hit one.",
                        "0 for a server where the world is not meant to trap you anywhere.")
                .defineInRange("pveSeconds", 8, 0, 600);
        COMBAT_SKILL_SECONDS = BUILDER
                .comment("Default seconds for a tag another mod applies — a curse, a summon, a",
                        "channelled ritual. Acts of war with no damage event to notice.",
                        "The caller may override it: Standards knows what a punch is worth, and",
                        "only LegendQuest knows whether a skill was a blast or a ten-second",
                        "ritual.")
                .defineInRange("skillSeconds", 10, 0, 600);
        COMBAT_PVP_BLOCKS_TELEPORT = BUILDER
                .comment("Being in PvP combat stops you teleporting.")
                .define("pvpBlocksTeleport", true);
        COMBAT_PVE_BLOCKS_TELEPORT = BUILDER
                .comment("Being in PvE combat stops you teleporting.",
                        "OFF by default, and the asymmetry is the point: on a peaceful server a",
                        "skeleton plinking you must not block /home, while a player hitting you",
                        "absolutely must. Turn it on for a survival server where running from the",
                        "world is meant to cost something.")
                .define("pveBlocksTeleport", false);
        COMBAT_SKILL_BLOCKS_TELEPORT = BUILDER
                .comment("A tag applied by another mod stops you teleporting.")
                .define("skillBlocksTeleport", true);
        COMBAT_CLEAR_ON_DEATH = BUILDER
                .comment("Dying ends the fight.",
                        "On, and it should stay on: a corpse is not in combat, and a respawning",
                        "player who cannot get home is being punished for having lost already.")
                .define("clearOnDeath", true);
        COMBAT_LOG = BUILDER
                .comment("Log every tag: who, what kind, what caused it, how long.",
                        "Off by default because it is chatty on a busy server. Turn it on the",
                        "moment you wonder why something does or does not tag — 'pvp via arrow,",
                        "owner Sablednah' is the difference between tuning and guessing.")
                .define("log", false);
        BUILDER.pop();

        BUILDER.comment("Movement.").push("movement");
        TOP_BARRIERS = BUILDER
                .comment("Blocks /top refuses to scan past. A bedrock or barrier box around a",
                        "build is deliberate protection, and a /top that steps through the roof",
                        "of one is a way into somebody's base — so the scan stops at these and",
                        "says so, rather than landing you on top of the box.",
                        "Block ids. Unknown ids are ignored, so this may safely name blocks from",
                        "a mod that is not installed. Empty the list to scan through anything.")
                .defineListAllowEmpty("topBarriers",
                        java.util.List.of("minecraft:bedrock", "minecraft:barrier"),
                        () -> "minecraft:bedrock",
                        o -> o instanceof String);
        MAX_SPEED = BUILDER
                .comment("Ceiling for /speed, as a multiple of normal. Above roughly 10 the",
                        "client's movement prediction stops agreeing with the server and players",
                        "rubber-band, so the default is deliberately below where it gets silly.")
                .defineInRange("maxSpeedMultiplier", 10.0D, 1.0D, 100.0D);
        BUILDER.pop();

        BUILDER.comment("Away from keyboard.").push("afk");
        AFK_AFTER_SECONDS = BUILDER
                .comment("Idle seconds before a player is marked away automatically. 0 leaves",
                        "/afk as a purely manual marker — which in practice means one nobody sets.")
                .defineInRange("awayAfterSeconds", 300, 0, 86_400);
        AFK_KICK_SECONDS = BUILDER
                .comment("Idle seconds before a player is disconnected. 0 never kicks, which is",
                        "the default: kicking idle players is a decision about your server's",
                        "player slots, not something a utility mod should assume.",
                        "standards.afk.exempt bypasses it.")
                .defineInRange("kickAfterSeconds", 0, 0, 86_400);
        AFK_ANNOUNCE = BUILDER
                .comment("Announce going away and coming back to everyone.")
                .define("announce", true);
        BUILDER.pop();

        BUILDER.comment("Mail.").push("mail");
        MAIL_LIMIT = BUILDER
                .comment("Letters one mailbox can hold. A cap is not optional: an uncapped",
                        "mailbox is a griefing tool and a save file that grows forever.")
                .defineInRange("mailboxLimit", 50, 1, 1000);
        BUILDER.pop();

        BUILDER.comment("Chat name decoration. Other mods contribute prefixes and suffixes",
                        "through the NameDecorator API — a faction tag, a party tag, a rank from",
                        "LegendQuest — and this decides how they are assembled.")
                .push("chat");
        CHAT_FORMAT = BUILDER
                .comment("The whole line. {prefixes} {name} {suffixes} {message} are filled in;",
                        "'&' colour codes work. The default reproduces vanilla's shape, so a",
                        "server with no decorators installed sees no change at all.",
                        "Example result: [FACTION][PARTY] Lord Sablednah the noble: hello")
                .define("format", "{prefixes}{name}{suffixes}&f: {message}");
        CHAT_AFFIX_SEPARATOR = BUILDER
                .comment("Between two prefixes or two suffixes. Blank butts them together,",
                        "which is usually what you want for bracketed tags.")
                .define("affixSeparator", "");
        CHAT_ALWAYS_FORMAT = BUILDER
                .comment("Apply the format even when no decorator has anything to add. Off means",
                        "an undecorated line is left entirely alone, keeping vanilla's hover cards",
                        "and team colours — which is why off is the default.")
                .define("alwaysFormat", false);
        CHAT_GROUP_KINDS = BUILDER
                .comment("Which group kinds put a tag in chat, outermost first.",
                        "Kind ids, from whichever mods provide them — standards:group,",
                        "legendquest:party, factions:faction. Empty means no group tags at all.",
                        "Order is yours: the first listed renders furthest from the name.",
                        "A group with no short tag set is skipped rather than rendered by its",
                        "full name — [The Crimson Brotherhood] on every line is what makes people",
                        "turn this off.",
                        "Kind ids, never group names: players rename their groups, and a rename",
                        "that silently drops the styling is a bug found on somebody else's server.")
                .defineListAllowEmpty("groupTagKinds",
                        java.util.List.of("standards:group"),
                        () -> "standards:group",
                        o -> o instanceof String);
        BUILDER.pop();

        BUILDER.comment("Homes.").push("homes");
        DEFAULT_HOME_LIMIT = BUILDER
                .comment("Homes a player may set with no permissions manager installed, or with no",
                        "standards.home.limit.<n> node granted. -1 is unlimited.")
                .defineInRange("defaultLimit", 3, -1, 10_000);
        MAX_HOME_LIMIT_NODE = BUILDER
                .comment("The highest standards.home.limit.<n> node registered, since permission",
                        "nodes must be enumerated up front rather than parsed from what is granted.",
                        "Raise it if you want to grant a bigger number than this.")
                .defineInRange("maxLimitNode", 32, 1, 1_000);
        BUILDER.pop();

        BUILDER.comment("The built-in economy. Standards is a fallback, not a land grab: if another",
                        "mod registers an economy provider it wins, and these settings then only",
                        "describe how money is printed.")
                .push("economy");
        STARTING_BALANCE = BUILDER
                .comment("What a brand-new account holds.")
                .defineInRange("startingBalance", 100.0D, 0.0D, 1.0E12D);
        CURRENCY_SINGULAR = BUILDER
                .comment("Used when no symbol is set: '1 credit', '25 credits'.")
                .define("currencyNameSingular", "credit");
        CURRENCY_PLURAL = BUILDER.define("currencyNamePlural", "credits");
        CURRENCY_SYMBOL = BUILDER
                .comment("Shown instead of the currency name. Blank falls back to the name, so",
                        "clearing this gives '25 credits' rather than '\u20A125'.")
                .define("currencySymbol", "\u20A1");
        CURRENCY_SYMBOL_BEFORE = BUILDER
                .comment("true gives '\u20A125', false gives '25\u20A1'. Ignored when the symbol is blank.")
                .define("currencySymbolBefore", true);
        CURRENCY_DECIMALS = BUILDER
                .comment("Decimal places shown, and the precision balances are rounded to.",
                        "0 makes the currency whole-number only, which is the default because it",
                        "is what nearly every server actually wants — fractional pennies only ever",
                        "show up in rounding complaints.")
                .defineInRange("decimals", 0, 0, 6);
        ALLOW_NEGATIVE_BALANCE = BUILDER
                .comment("Let balances go below zero (debt). Off refuses the withdrawal instead.")
                .define("allowNegative", false);
        PREFER_OWN_LEDGER = BUILDER
                .comment("Exactly one ledger holds the money — two that disagree about your balance",
                        "is worse than either alone — and by default Standards yields to any",
                        "dedicated economy mod that registers itself. Turn this on to insist on",
                        "Standards' own ledger instead, which is what you want when a modpack",
                        "drags in an economy mod you did not ask for. Needs a restart: the choice",
                        "is settled during mod construction, before any command can run.")
                .define("preferOwnLedger", false);
        BALTOP_SIZE = BUILDER
                .comment("How many accounts /baltop lists.")
                .defineInRange("baltopSize", 10, 1, 100);
        BUILDER.pop();

        BUILDER.comment("The built-in permission handler: groups and grants for a server with no",
                        "permissions mod.",
                        "",
                        "IT IS OFF UNLESS YOU CHOOSE IT, and not here. NeoForge owns that switch:",
                        "set permissionHandler = \"standards:permissions\" in neoforge-server.toml.",
                        "Leave it alone and LuckPerms — or whatever else you run — is untouched,",
                        "and /perm is not registered at all.",
                        "",
                        "Nothing below has any effect until then.")
                .push("permissions");
        DEFAULT_PERMISSION_GROUP = BUILDER
                .comment("The group everybody is in without being put there, checked last of all.",
                        "This is how you grant something to every player at once. It does not have",
                        "to exist; if there is no group by this name the step is simply skipped.",
                        "Blank turns the idea off entirely.")
                .define("defaultGroup", "default");
        PUBLISH_PERMISSION_ROLES = BUILDER
                .comment("Publish permission groups through the groups API as 'standards:role',",
                        "so other mods can see who is a moderator and so a group can carry a chat",
                        "tag. Add standards:role to chat.groupTagKinds to render the tag.",
                        "This is the half LuckPerms cannot do — its groups are a permissions",
                        "concept and nothing else on the server can ask about them.",
                        "Harmless when the handler is not active: the groups are simply empty.")
                .define("publishAsGroups", true);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private StandardsConfig() {}
}

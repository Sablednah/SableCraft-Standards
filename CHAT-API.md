# The chat decorator API

**Status: built, and in use by three mods at once**, which is the only way the ordering rule was
ever going to be tested. `api/chat/` — `Chat`, `NameDecorator`, `ChatRouter`. **LegendQuest**
decorates (`ChatSupport`) and routes party chat (`PartyChat`); **Factions** decorates and routes
faction and ally chat (`FactionChat`); Standards itself renders group tags through `GroupTags`,
including its own permission ranks as `standards:role`.

How another mod adds a prefix or a suffix to a player's name in chat — and how several of them do
it at once without knowing about each other.

## The target

```
[FACTION][PARTY] Lord Sablednah the noble: says hello
```

Four separate mods contributed to that line. None of them had to be aware of the others:

| Part | From | Priority |
|---|---|---|
| `[FACTION]` | a faction mod | 5 |
| `[PARTY]` | a party/team mod | 10 |
| `Lord` | LegendQuest, from character level | 100 |
| `the noble` | LegendQuest, from karma | 100 |

## The one rule

**Priority is closeness to the name.** Higher priority sits nearer the name; lower priorities are
pushed outwards. Applied to both sides, so prefixes render lowest-priority-leftmost and suffixes
mirror it.

That single rule is what makes independent mods land sensibly. A party tag registers low and
drifts out to the left; a rank registers high and stays welded to the name, where a title belongs.

Rough conventions so nobody has to negotiate:

| Range | For |
|---|---|
| 0–99 | broad affiliations — faction, team, party |
| 100–199 | character-level things — rank, class, title |
| 200+ | anything that must hug the name |

## Contributing one

```java
import com.sablednah.standards.api.chat.Chat;
import com.sablednah.standards.api.chat.NameDecorator;

Chat.register(new NameDecorator() {
    public String id()    { return "legendquest:rank"; }
    public int priority() { return 100; }

    public Optional<String> prefix(ServerPlayer player) {
        return rankOf(player).map(rank -> "&6" + rank);      // "Lord"
    }

    public Optional<String> suffix(ServerPlayer player) {
        return epithetOf(player).map(word -> "&7" + word);   // "the noble"
    }
});
```

Register during your own setup, guarded so Standards stays a **soft** dependency:

```java
if (ModList.get().isLoaded("standards")) {
    ChatSupport.register();   // this class alone imports com.sablednah.standards.*
}
```

### Four things worth getting right

**Returning empty is normal.** A player in no faction has no faction tag. Empty and blank are both
skipped, so there is no need to return `""` and hope.

**Keep it cheap.** This runs on the server thread for every chat message. Read state you already
have; do not go looking things up over a network, and do not touch the database.

**`&` colour codes, resolved server-side.** Decorated names therefore appear correctly on
**unmodified clients**, which is the whole point of Standards being server-authoritative.

**A throwing decorator is skipped, not fatal.** `Chat` catches, logs the id, and carries on — one
misbehaving mod must not cost everybody their chat. Your decorator still ought not to throw.

## Additive — unlike the economy

Worth stating plainly, because the two APIs look similar and behave oppositely:

- **Economy**: exactly one provider holds the money. A balance is a single fact, and two ledgers
  disagreeing about it is worse than either alone.
- **Chat**: every decorator gets a turn. A name can carry a faction tag *and* a party tag *and* a
  rank without contradicting itself.

## The cost: a decorated line is not signed

**Worth reading before a moderation incident rather than after.**

A decorated line cannot go out as vanilla player chat. `ServerChatEvent.setMessage` replaces only
the message *body*, and vanilla wraps whatever it is given in its own `<name> %s` — so a composed
line that already carries the name comes out with the name twice. There is no set-the-whole-line
hook, so Standards cancels the event and delivers the line itself.

That means a decorated line is a **system message**, and system messages are not signed. In
practice:

| | |
|---|---|
| Client-side chat reporting | does not apply to decorated lines |
| Client-side blocking / "hide messages from" | does not apply either |
| Vanilla's hover card on the name (click-to-message, profile popup) | lost — a system message carries no sender |
| `/ignore` | **honoured by Standards directly**, so this still works |
| Server log | **echoed by Standards directly**, so chat is still moderatable after the fact |
| Undecorated chat | untouched — still vanilla, still signed, hover cards and all |

Both losses have the same single cause — a system message has no sender attached — and both are
**invisible until someone needs them**. Verified on two unmodded clients: decorated lines arrive
as `[System]`, undecorated ones as `[Not Secure]`, and in play there is no visual difference and
no warning whatever. That is exactly why it is written down.

The trade is deliberate and follows from the mod's headline claim: **an unmodded client gets
everything**. Server-side formatting works for every player; client-side signing only ever worked
for the modded half of a server. But a server owner relying on client-side reporting as their
moderation story should know it goes quiet the moment a decorator is registered.

**A registered decorator that returns nothing costs nothing.** If every decorator returns empty
prefixes and suffixes — the state most players are in most of the time — `format()` returns empty,
Standards does not touch the event, and the line goes out as ordinary signed vanilla chat with its
hover card intact. Registering a decorator does not put chat on the system-message path; only
*actually decorating a line* does. Confirmed on an unmodded client with a registered-but-silent
decorator.

If the trade is wrong for your server, leave `alwaysFormat` off and register no decorators — chat
then stays entirely vanilla.

## What the server owner controls

`config/standards-common.toml`, under `[chat]`:

| Setting | Does |
|---|---|
| `format` | The whole line. `{prefixes}` `{name}` `{suffixes}` `{message}`, with `&` colours. |
| `affixSeparator` | Between two prefixes or two suffixes. Blank butts bracketed tags together. |
| `alwaysFormat` | Apply the format even with nothing to add. **Off by default** — an undecorated line is left entirely alone, keeping vanilla's hover cards and team colours. |

That last default matters: with no decorator mods installed, Standards changes chat **not at all**.

## Who uses it

- **Standards** renders group tags through `NameDecorator`, ordered by `groupTagKinds`.
- **[Factions ReForged](https://github.com/Sablednah/Factions-ReForged)** contributes a faction tag
  on the decorator side, and is the first real consumer of `ChatRouter` — its faction and ally
  channels go through the seam rather than cancelling `ServerChatEvent` themselves, which is what
  stops a muted player switching channel and talking.

## Filtering what players say

`api/chat/MessageFilter` and `Screening`. The third seam in this package, and it borrows from both
of the others: filters are **additive** like decorators (every filter gets a turn, each seeing the
text the last one left) with the router's **early exit** for the one verdict that cannot be merged
— the first `block` ends it.

### Why it exists

Standards delivers a lot of player text that never passes through `ServerChatEvent`'s message body:
a decorated line is cancelled and re-sent from `getRawText()`, a routed party line is handed to its
channel from the raw text, and `/msg`, `/r`, `/me` and `/mail` are commands with delivery of their
own. A filter that only changed the event's message worked on a vanilla server and silently stopped
working the day Standards formatted its first line. ChatFilter ReForged found exactly that.

### Where Standards asks

| Path | Channel | Per-viewer? |
|---|---|---|
| Public chat, before any router | `chat` | yes, on Standards' own delivery; routers get the censored text |
| `/msg`, `/w`, `/tell`, `/pm`, `/m`, `/r`, `/reply` | `private` | yes — sender and recipient each get their copy |
| `/me` | `emote` | yes |
| `/mail send` | `mail` | no — stored as the recipient would see it |

Always **after** the mute gate (a muted player is told about the mute, not the filter) and, for
`/msg`, after every refusal that has nothing to do with what was said, so an undeliverable message
costs nobody a strike. Social spy and the console log see the original.

### Contributing one

```java
Chat.registerFilter(new MessageFilter() {
    public String id() { return "chatfilter:words"; }
    public Screening screen(ServerPlayer sender, String text, String channel) {
        if (!rude(text)) return Screening.pass();
        // Silent: the sender still sees what they typed; everyone else sees stars.
        return Screening.censor(starred(text), viewer -> viewer == sender);
    }
});
```

`Screening.block(reason)` stops the line; a `null` reason says nothing to the sender.

`Screening.shadow()` lets the sender believe it went out: they see their line exactly as a
delivered one would look — the same decorations, the same `/msg` confirmation, a letter reported
"sent" — and nobody else receives anything. The console and social spy still see it. Standards does
this rather than the filter because a hand-built copy of a decorated line is never quite the same
line, and that difference is the giveaway.

When filters disagree the **harsher verdict wins**: block, then shadow, then censor, then pass. Two
censors compose — the second filter sees the first one's text, and a viewer sees the original only
if both would have let them.

⚠ A shadowed line that a router would have claimed is **not routed**: the sender sees it as an
ordinary chat line, because routers render their own lines and have no per-viewer hook. Giving
routers one is the open question if a party channel ever needs a convincing shadow.

A `/msg` to several targets is **one** message: it is screened once, at the first recipient it
could actually reach, so a filter counting strikes counts it once.

### Leaving a line to vanilla

When nothing decorates a line and nobody ignores its sender, Standards normally leaves chat entirely
alone, signed and with its hover card. A censor changes that: Standards rewrites the message body so
the censored text is what goes out. A filter that has **already** arranged vanilla's delivery — by
supplying the client's chat filter mask, as ChatFilter does — marks its verdict
`.vanillaHandled()`, and Standards then leaves the line alone.

### Other mods publishing a player's words

A sign, a book, a shop label: call `Chat.screen(player, text, "yourmod:thing")` after
`Chat.speechBlocked(player)`, for the same reason that check exists.

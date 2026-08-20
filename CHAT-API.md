# The chat decorator API

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

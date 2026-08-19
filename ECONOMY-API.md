# The economy API

How another mod uses money on a server running Standards — and how a dedicated economy mod takes
over from us.

## Why there is one at all

There is no Vault on NeoForge. Nothing every economy mod implements, no shared notion of a balance,
and — checked as of August 2026 — no leader either: SG-Economy API, Real Economy, EconomyCraft and
EconomyMod all exist on 1.21.x and none of them is the obvious one to build against.

So Standards ships a ledger, because a server needs *an* answer, and puts it behind an interface,
because Standards must not be the kind of mod that assumes it is the only one. LegendQuest and
ZombieMod call one facade and never learn who answered.

---

## Spending money (the common case)

```java
import com.sablednah.standards.api.economy.Economy;
import com.sablednah.standards.api.economy.TransactionResult;

// Paying out a bounty
if (Economy.isAvailable()) {
    Economy.deposit(player.getUUID(), 25.0D, "zombiemod:bounty");
}

// Charging for something, and only doing it if they can pay
TransactionResult paid = Economy.withdraw(player.getUUID(), skill.cost(), "legendquest:skill:fireball");
if (paid.success()) {
    fire(skill);
} else if (paid.failure() == TransactionResult.Failure.INSUFFICIENT_FUNDS) {
    tell(player, "You need " + Economy.format(skill.cost()) + " and have " + Economy.format(paid.balance()));
}
```

The whole surface:

| Call | Notes |
|---|---|
| `Economy.isAvailable()` | False means "no money on this server", not "an error". |
| `Economy.balance(uuid)` | Works for offline players. |
| `Economy.has(uuid, amount)` | The question to ask before doing anything expensive. |
| `Economy.deposit(uuid, amount, reason)` | Amount must be non-negative. |
| `Economy.withdraw(uuid, amount, reason)` | Refused if unaffordable, unless the ledger permits debt. |
| `Economy.transfer(from, to, amount, reason)` | Refunds the sender if the deposit half fails. |
| `Economy.format(amount)` | Uses the *active* economy's formatting, not ours. |
| `Economy.top(limit)` | `Optional` — a ledger that cannot enumerate accounts returns empty. |
| `Economy.provider()` | Who is holding the money. For diagnostics. |

### Four things worth getting right

**Always pass a real `reason`.** `"zombiemod:bounty"`, `"legendquest:skill:fireball"`,
`"shop:sale:diamond"`. It costs nothing now and it is the only thing that will ever answer "where
did all the money go", which is the first question every time.

**Never assume the transaction happened.** `TransactionResult` carries `failure()` and the
`balance()` at the time, precisely so you do not have to re-query to write a good message.

**A throwing provider is already handled.** `Economy` catches, logs, and returns a refusal — a
foreign ledger blowing up must not take your feature down with it. The bounty still counted; the
skill still fired.

**Server thread only.** Balances live in world save data. Touching them off-thread is the same
mistake as touching a level off-thread, and the facade deliberately does not add a lock that would
only paper over it.

### Keeping Standards a *soft* dependency

Guard the call site, or keep every reference inside a class you only touch behind the check:

```java
if (ModList.get().isLoaded("standards")) {
    EconomySupport.payOut(player, amount);   // this class alone imports com.sablednah.standards.*
}
```

Making a soft dependency mandatory in practice is the classic way to get this wrong — the 1.8
ZombieMod did exactly that to Factions, calling `BoardColl` with no `hasFactions` guard.

### The alternative, when you do not want a hard dependency at all

ZombieMod already has the other shape: `Bounties.Payer`, a tiny interface a payer registers with,
so the *economy adapter* lives in a class that only loads when both mods are present. That pattern
stays valid, and Standards' provider fits it — the adapter is four lines. **Note the difference in
kind, though: bounty payers are additive on purpose** (pay an economy *and* tally a scoreboard, two
different rewards), while an economy *provider* is not — see below.

---

## Providing the money instead

Implement `EconomyProvider` and register it during your own mod's setup:

```java
public MyEconomyMod(IEventBus bus, ModContainer container) {
    bus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
        if (ModList.get().isLoaded("standards")) {
            Economy.register(new MyProvider());
        }
    }));
}
```

**Exactly one provider wins.** The highest `priority()` takes every call; the others stand by. This
is deliberate and it is the one place the API refuses to be accommodating: a bounty payer can be
additive, but a provider answers "what is my balance", and two ledgers that disagree about that is
strictly worse than either alone.

Standards registers itself at `EconomyProvider.BUILTIN_PRIORITY` — **-1000**, deliberately below
zero. Return anything higher and you displace us without either side needing to know the other
exists. The default `priority()` is `0`, so simply not overriding it is enough.

A server owner who wants Standards' ledger anyway sets `economy.preferOwnLedger = true`, which
raises ours above everything. `/standards economy` prints who actually won, which is the first
thing to check when money misbehaves.

### What you must implement

`name`, `hasAccount`, `createAccount`, `balance`, `deposit`, `withdraw`, `format`. `transfer` and
`top` have defaults — override `transfer` if your operations can fail halfway, so a failed deposit
does not vanish the sender's money, and `top` if you can enumerate accounts.

Return `TransactionResult.ok(amount, newBalance)` or
`TransactionResult.fail(Failure.INSUFFICIENT_FUNDS, amount, currentBalance)`. Refuse negative and
NaN amounts as `INVALID_AMOUNT` rather than treating them as the opposite operation.

---

## Stability

Everything under `com.sablednah.standards.api` is the compile-against surface and will not move
without a version bump. Everything under `neoforge`, `core` and `network` is internal — including
`StandardsEconomy`, our own provider. Do not reach for it; go through `Economy`.

package tally.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An append-only journal of posted transactions, and the balances derived from
 * it.
 *
 * <p>This is the piece that makes the rest of the domain a ledger rather than a
 * collection of value types. It holds invariants 3, 7 and 8. A ledger is an
 * immutable snapshot: writes return a new ledger and never alter the receiver:
 *
 * <ul>
 *   <li><b>3 — every posting references an account that exists.</b> The only
 *       invariant that cannot be enforced by construction. A {@link Posting}
 *       carries an {@link AccountId}, so nothing about it in isolation can say
 *       whether that account exists; only something holding the set of known
 *       accounts can answer, and that is this class.
 *   <li><b>7 — the journal is append-only.</b> Nothing is removed and nothing
 *       is edited. A mistake is corrected by posting
 *       {@link Transaction#reverse}, which leaves both entries in place so an
 *       auditor sees the error and the correction.
 *   <li><b>8 — balances are derived, never stored.</b> There is no balance
 *       field anywhere in this class. A balance is computed by folding the
 *       postings that mention an account, every time it is asked for.
 * </ul>
 *
 * <h2>Why balances are recomputed rather than cached</h2>
 *
 * <p>Storing a balance creates a second source of truth that can disagree with
 * the journal, and when they disagree there is no way to tell which is wrong.
 * Deriving is O(journal) per query, which is obviously not how this would work
 * at scale — but the fix for that is a measured, explicitly-invalidated
 * projection, not a mutable counter maintained by hand. Correctness first;
 * optimisation requires a measurement, and there is none.
 *
 * <h2>Accounts are registered, not owned</h2>
 *
 * <p>An account is opened independently and registered here. The alternative —
 * having the ledger mint accounts — would make invariant 3 vacuously true for
 * anything it created, which is not the same as enforcing it. Keeping the
 * lifecycles separate also matches Phase 2, where accounts and transactions are
 * different tables with different write patterns.
 *
 * <h2>Concurrency</h2>
 *
 * <p>Instances are safe to share between readers because their state never
 * changes. Concurrent writers still need coordination when choosing which
 * returned snapshot becomes current; immutable snapshots do not prevent two
 * writers from both starting with the same stale ledger.
 *
 * <p>Concurrent writes still require coordination at the application boundary.
 */
public final class Ledger {

    private final Map<AccountId, Account> accounts;
    private final Map<TransactionId, Transaction> journal;

    /** An empty ledger snapshot. */
    public Ledger() {
        this(Map.of(), Map.of());
    }

    private Ledger(Map<AccountId, Account> accounts, Map<TransactionId, Transaction> journal) {
        this.accounts = Map.copyOf(accounts);
        this.journal = Collections.unmodifiableMap(new LinkedHashMap<>(journal));
    }

    /**
     * Registers an account so transactions may reference it.
     *
     * <p>Re-registering the same account definition is accepted and does
     * nothing. A conflicting definition is refused: replacing the kind or
     * currency would reinterpret postings already in the journal.
     *
     * @throws ConflictingAccountException if the identifier is already
     *     registered with a different kind or currency
     */
    public Ledger register(Account account) {
        Objects.requireNonNull(account, "account");
        Account existing = accounts.get(account.id());
        if (existing != null
                && (existing.kind() != account.kind() || existing.currency() != account.currency())) {
            throw new ConflictingAccountException(existing, account);
        }
        if (existing != null) {
            return this;
        }
        Map<AccountId, Account> updated = new HashMap<>(accounts);
        updated.put(account.id(), account);
        return new Ledger(updated, journal);
    }

    /** Registers several accounts, returning the resulting snapshot. */
    public Ledger registerAll(Account... toRegister) {
        Ledger result = this;
        for (Account account : toRegister) {
            result = result.register(account);
        }
        return result;
    }

    /** The account with this identifier, if it is registered. */
    public Optional<Account> account(AccountId id) {
        return Optional.ofNullable(accounts.get(id));
    }

    /**
     * Appends a transaction to the journal.
     *
     * <p>The transaction is already known to balance — {@link Transaction}
     * cannot be constructed otherwise — so what is checked here is only what
     * needs the ledger's context.
     *
     * @throws UnknownAccountException if any posting references an account this
     *     ledger does not know (invariant 3)
     * @throws DuplicateTransactionException if this transaction is already in
     *     the journal, which would double every amount in it
     * @throws UnknownTransactionException if this is a reversal of a
     *     transaction the journal has no record of
     */
    public Ledger post(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (journal.containsKey(transaction.id())) {
            throw new DuplicateTransactionException(transaction.id());
        }
        transaction.postings().stream()
                .map(Posting::account)
                .filter(id -> !accounts.containsKey(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new UnknownAccountException(id);
                });
        // A reversal must correct something that actually happened. Reversing a
        // transaction that was never posted would put a correction in the
        // journal for an error the journal has no record of.
        transaction.reverses()
                .filter(original -> !journal.containsKey(original))
                .ifPresent(original -> {
                    throw new UnknownTransactionException(original);
                });
        Map<TransactionId, Transaction> updated = new LinkedHashMap<>(journal);
        updated.put(transaction.id(), transaction);
        return new Ledger(accounts, updated);
    }

    /**
     * The balance of an account: the signed sum of every posting against it.
     *
     * <p>A debit of 25 contributes {@code +25} to an asset and {@code -25} to
     * revenue. The sign comes from {@link AccountKind}, derived from the
     * accounting equation, which is why a balance is meaningful without anyone
     * having memorised a table of five rules.
     *
     * @throws UnknownAccountException if the account is not registered
     */
    public Money balanceOf(AccountId id) {
        Account account = accounts.get(id);
        if (account == null) {
            throw new UnknownAccountException(id);
        }
        return journal.values().stream()
                .flatMap(transaction -> transaction.postings().stream())
                .filter(posting -> posting.account().equals(id))
                .map(posting -> posting.effectOn(account.kind()))
                .reduce(account.zero(), Money::add);
    }

    /** The balance of an account. */
    public Money balanceOf(Account account) {
        return balanceOf(account.id());
    }

    /**
     * The journal, in the order transactions were posted.
     *
     * <p>An immutable copy: invariant 7 says the journal is append-only, and
     * handing out a mutable list would make that a matter of trust.
     */
    public List<Transaction> journal() {
        return List.copyOf(journal.values());
    }

    /** How many transactions have been posted. */
    public int size() {
        return journal.size();
    }

    /**
     * Every registered account's balance.
     *
     * <p>This is a trial balance — the report a bookkeeper runs to check the
     * books hang together.
     */
    public Map<AccountId, Money> balances() {
        Map<AccountId, Money> balances = new LinkedHashMap<>();
        for (AccountId id : new ArrayList<>(accounts.keySet())) {
            balances.put(id, balanceOf(id));
        }
        return Map.copyOf(balances);
    }

    /**
     * Whether the books balance: across the whole journal, in each currency,
     * debits equal credits.
     *
     * <p>This should be true after every posted transaction, because each
     * transaction balances on its own and a sum of zeroes is zero. It is worth
     * being able to ask anyway — it is the property the entire design exists to
     * preserve, and a cheap end-to-end check that nothing has gone wrong.
     */
    public boolean isBalanced() {
        Map<Currency, Money> nets = new LinkedHashMap<>();
        journal.values().stream()
                .flatMap(transaction -> transaction.postings().stream())
                .forEach(posting -> {
                    Currency currency = posting.amount().currency();
                    Money signed = switch (posting.direction()) {
                        case DEBIT -> posting.amount();
                        case CREDIT -> posting.amount().negate();
                    };
                    nets.merge(currency, signed, Money::add);
                });
        return nets.values().stream().allMatch(Money::isZero);
    }

    /** All accounts registered with this ledger. */
    public Collection<Account> accounts() {
        return List.copyOf(accounts.values());
    }
}

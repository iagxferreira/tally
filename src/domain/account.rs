//! Account identity, classification, and the sign rule for deriving balances.

use core::fmt;

use uuid::Uuid;

use super::currency::Currency;
use super::direction::Direction;
use super::money::{Money, MoneyError};

/// The identity of an account within a ledger.
///
/// A newtype over [`Uuid`] rather than a bare `Uuid`, so that an `AccountId`
/// cannot be passed where a `TransactionId` is expected. The wrapper is
/// `#[repr(transparent)]` and therefore free: an `AccountId` has exactly the
/// layout of the `Uuid` it contains, and the safety is entirely a compile-time
/// property.
///
/// # Why version 7
///
/// A v4 UUID is 122 random bits, which scatters inserts uniformly across a
/// B-tree index: every write touches a different page and the index working
/// set becomes the whole index. A v7 UUID carries a 48-bit Unix millisecond
/// timestamp in its high bits, so identifiers generated near in time sort near
/// each other. Inserts land at the right edge of the tree, pages stay hot, and
/// the journal reads back in roughly insertion order.
///
/// Both versions give the same uniqueness guarantee without coordination,
/// which is what lets any node mint an identifier without consulting a central
/// sequence. Version 7 simply behaves better once the identifiers reach
/// storage, and the choice is expensive to reverse after there is data.
///
/// Because UUIDs are laid out big-endian, the derived [`Ord`] sorts by those
/// timestamp bits, so ordering identifiers approximates ordering by creation
/// time. That is a convenience, not a guarantee: two identifiers minted in the
/// same millisecond order by their random bits, and clocks can move backwards.
/// Never treat this as a substitute for a real timestamp.
///
/// There is deliberately no `Default` implementation. A "default account" is
/// meaningless, and `Uuid::nil()` would silently collide for every caller that
/// forgot to set one.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
#[repr(transparent)]
pub struct AccountId(Uuid);

impl AccountId {
    /// Mints a new identifier from the current time.
    #[must_use]
    pub fn generate() -> Self {
        Self(Uuid::now_v7())
    }

    /// Reconstructs an identifier that already exists, such as one read back
    /// from storage.
    ///
    /// This does not check the UUID version: an identifier persisted before a
    /// version change is still that account's identity, and rejecting it would
    /// lose data rather than protect anything.
    #[must_use]
    pub const fn from_uuid(uuid: Uuid) -> Self {
        Self(uuid)
    }

    /// The underlying UUID, for storage and serialization boundaries.
    #[must_use]
    pub const fn as_uuid(self) -> Uuid {
        self.0
    }
}

impl fmt::Display for AccountId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        fmt::Display::fmt(&self.0, f)
    }
}

/// The classification of an account, which determines how postings affect its
/// balance.
///
/// # The rule this encodes
///
/// These five kinds are not an arbitrary taxonomy. They fall out of the
/// fundamental accounting equation:
///
/// ```text
/// Assets = Liabilities + Equity
/// ```
///
/// Revenue increases equity and expenses decrease it, so expanding those two
/// as the temporary equity accounts they are, and moving expenses to the left
/// to keep every term positive:
///
/// ```text
/// Assets + Expenses  =  Liabilities + Equity + Revenue
/// └──── debit-normal ───┘  └─────── credit-normal ───────┘
/// ```
///
/// **Debits increase the left side; credits increase the right side.** That
/// single rule is the whole of [`AccountKind::normal_side`], and every sign in
/// the ledger derives from it. Modelling it as the equation rather than as a
/// five-by-two truth table means a new account kind cannot be given a sign
/// rule inconsistent with the others.
///
/// # A note for engineers new to bookkeeping
///
/// A customer's balance held at your institution is a **liability**, not an
/// asset: you owe them that money. When a customer deposits, your assets rise
/// (you hold the cash) *and* your liabilities rise (you owe it back). Both
/// sides move, which is precisely why the bookkeeping is double-entry.
///
/// This enum is deliberately **not** `#[non_exhaustive]`: double-entry
/// bookkeeping defines exactly these five kinds, and the set is closed.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum AccountKind {
    /// Resources the entity controls: cash, receivables, reserves.
    Asset,
    /// Obligations the entity owes, including customer balances.
    Liability,
    /// The residual claim on assets after liabilities.
    Equity,
    /// Inflows that increase equity, such as fees earned.
    Revenue,
    /// Outflows that decrease equity, such as processing costs.
    Expense,
}

impl AccountKind {
    /// The side on which this kind of account normally carries its balance.
    ///
    /// A posting in this direction increases the account; a posting in the
    /// opposite direction decreases it.
    #[must_use]
    pub const fn normal_side(self) -> Direction {
        match self {
            // Left-hand side of the expanded accounting equation.
            Self::Asset | Self::Expense => Direction::Debit,
            // Right-hand side.
            Self::Liability | Self::Equity | Self::Revenue => Direction::Credit,
        }
    }

    /// Whether a posting in `direction` increases an account of this kind.
    #[must_use]
    pub const fn is_increased_by(self, direction: Direction) -> bool {
        matches!(
            (self.normal_side(), direction),
            (Direction::Debit, Direction::Debit) | (Direction::Credit, Direction::Credit)
        )
    }

    /// The signed contribution a posting makes to this account's balance.
    ///
    /// Posting amounts are always positive; this applies the sign implied by
    /// the account kind, and is the fold step by which balances are derived
    /// from the journal rather than stored.
    ///
    /// # Errors
    ///
    /// Returns [`MoneyError::Overflow`] when the amount must be negated but its
    /// magnitude is not representable — that is, `i64::MIN` minor units. Such
    /// an amount can never be a valid posting, but `Money` itself permits it,
    /// so the failure is reported rather than hidden.
    pub fn balance_effect(self, direction: Direction, amount: Money) -> Result<Money, MoneyError> {
        if self.is_increased_by(direction) {
            Ok(amount)
        } else {
            amount.checked_neg()
        }
    }
}

impl fmt::Display for AccountKind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::Asset => "asset",
            Self::Liability => "liability",
            Self::Equity => "equity",
            Self::Revenue => "revenue",
            Self::Expense => "expense",
        })
    }
}

/// An account in the chart of accounts.
///
/// An account is the first type in the domain with *identity* rather than
/// pure value semantics: two accounts with the same name and kind are two
/// different accounts. It is therefore not `Copy`, and it is compared by all
/// of its fields only because tests want that — never treat structural
/// equality as identity. Identity is [`Account::id`].
///
/// # Denominated in exactly one currency
///
/// The currency is fixed when the account is opened and cannot change. An
/// entity that holds both dollars and euros holds *two* accounts, which is how
/// core banking ledgers are actually built.
///
/// The consequence is that a balance is always a single [`Money`], never a map
/// from currency to amount, and that "what is the balance of this account" is
/// answerable without further qualification. It also pushes the per-currency
/// half of double-entry balancing down to a place where it can be checked
/// early: an amount in the wrong currency is rejected by
/// [`Account::balance_effect`] rather than surviving into a transaction.
///
/// # What this type does not enforce
///
/// That a posting references an account which *exists* is a referential
/// invariant. It needs the chart of accounts as context, so it belongs to the
/// ledger, not here.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Account {
    id: AccountId,
    kind: AccountKind,
    currency: Currency,
    name: String,
}

impl Account {
    /// Opens a new account, minting its identity.
    ///
    /// The name is trimmed of surrounding whitespace and must not be empty
    /// once trimmed: an unnamed row in a chart of accounts is an operational
    /// hazard, not a valid account.
    ///
    /// There is deliberately no constructor that accepts an existing
    /// [`AccountId`]. Reconstructing an account from storage is a boundary
    /// concern, and the boundary does not exist yet.
    ///
    /// # Errors
    ///
    /// Returns [`AccountError::EmptyName`] if `name` is empty or only
    /// whitespace.
    pub fn open(kind: AccountKind, currency: Currency, name: &str) -> Result<Self, AccountError> {
        let name = name.trim();
        if name.is_empty() {
            return Err(AccountError::EmptyName);
        }

        Ok(Self {
            id: AccountId::generate(),
            kind,
            currency,
            name: name.to_owned(),
        })
    }

    /// The account's identity.
    #[must_use]
    pub const fn id(&self) -> AccountId {
        self.id
    }

    /// The account's classification, which fixes its normal balance side.
    #[must_use]
    pub const fn kind(&self) -> AccountKind {
        self.kind
    }

    /// The currency this account is denominated in, fixed at opening.
    #[must_use]
    pub const fn currency(&self) -> Currency {
        self.currency
    }

    /// The account's human-readable name.
    ///
    /// Borrowed rather than cloned: the caller almost always wants to read or
    /// format it, and an owned `String` would allocate on every read.
    #[must_use]
    pub fn name(&self) -> &str {
        &self.name
    }

    /// An empty balance in this account's currency.
    ///
    /// This is the seed for folding postings into a balance, and the reason a
    /// balance never needs a currency argument.
    #[must_use]
    pub const fn zero_balance(&self) -> Money {
        Money::zero(self.currency)
    }

    /// The signed contribution a posting makes to this account's balance.
    ///
    /// This is [`AccountKind::balance_effect`] plus the currency check that the
    /// single-currency decision makes possible.
    ///
    /// # Errors
    ///
    /// - [`MoneyError::CurrencyMismatch`] if `amount` is not in this account's
    ///   currency. Tally never converts implicitly.
    /// - [`MoneyError::Overflow`] if the amount must be negated but is not
    ///   representable negated.
    pub fn balance_effect(&self, direction: Direction, amount: Money) -> Result<Money, MoneyError> {
        if amount.currency() != self.currency {
            return Err(MoneyError::CurrencyMismatch {
                left: self.currency,
                right: amount.currency(),
            });
        }

        self.kind.balance_effect(direction, amount)
    }
}

/// Ways opening an account can fail.
///
/// Separate from [`MoneyError`] because it describes construction, not
/// arithmetic. Operations *on* an account that fail monetarily keep reporting
/// [`MoneyError`], so a caller doing arithmetic does not have to widen its
/// error type to include naming rules.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum AccountError {
    /// The name was empty, or contained only whitespace.
    EmptyName,
}

impl fmt::Display for AccountError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::EmptyName => f.write_str("account name must not be empty"),
        }
    }
}

impl core::error::Error for AccountError {}

#[cfg(test)]
mod tests {
    use super::{Account, AccountError, AccountId, AccountKind};
    use crate::domain::{Currency, Direction, Money, MoneyError};
    use uuid::Uuid;

    const ALL_KINDS: [AccountKind; 5] = [
        AccountKind::Asset,
        AccountKind::Liability,
        AccountKind::Equity,
        AccountKind::Revenue,
        AccountKind::Expense,
    ];

    fn usd(minor_units: i64) -> Money {
        Money::from_minor_units(minor_units, Currency::Usd)
    }

    #[test]
    fn identifiers_are_sixteen_byte_copy_values() {
        assert_eq!(size_of::<AccountId>(), size_of::<Uuid>());
        let a = AccountId::generate();
        let b = a;
        assert_eq!(a, b);
    }

    #[test]
    fn generated_identifiers_are_distinct() {
        let a = AccountId::generate();
        let b = AccountId::generate();
        assert_ne!(a, b);
    }

    #[test]
    fn generated_identifiers_are_version_seven() {
        assert_eq!(AccountId::generate().as_uuid().get_version_num(), 7);
    }

    #[test]
    fn ordering_follows_the_leading_timestamp_bits() {
        // UUIDs are big-endian, so the derived Ord compares the v7 timestamp
        // prefix first. Built from fixed values rather than the clock, so the
        // property is asserted without depending on timing.
        let earlier = AccountId::from_uuid(Uuid::from_u128(1));
        let later = AccountId::from_uuid(Uuid::from_u128(2));
        assert!(earlier < later);
    }

    #[test]
    fn round_trips_through_its_uuid() {
        let id = AccountId::generate();
        assert_eq!(AccountId::from_uuid(id.as_uuid()), id);
    }

    #[test]
    fn display_is_the_hyphenated_uuid() {
        let id = AccountId::from_uuid(Uuid::nil());
        assert_eq!(id.to_string(), "00000000-0000-0000-0000-000000000000");
    }

    #[test]
    fn normal_sides_follow_the_accounting_equation() {
        // Left-hand side: Assets + Expenses.
        assert_eq!(AccountKind::Asset.normal_side(), Direction::Debit);
        assert_eq!(AccountKind::Expense.normal_side(), Direction::Debit);
        // Right-hand side: Liabilities + Equity + Revenue.
        assert_eq!(AccountKind::Liability.normal_side(), Direction::Credit);
        assert_eq!(AccountKind::Equity.normal_side(), Direction::Credit);
        assert_eq!(AccountKind::Revenue.normal_side(), Direction::Credit);
    }

    #[test]
    fn a_debit_increases_assets_and_decreases_liabilities() {
        assert!(AccountKind::Asset.is_increased_by(Direction::Debit));
        assert!(!AccountKind::Liability.is_increased_by(Direction::Debit));
    }

    #[test]
    fn a_credit_increases_liabilities_and_decreases_assets() {
        assert!(AccountKind::Liability.is_increased_by(Direction::Credit));
        assert!(!AccountKind::Asset.is_increased_by(Direction::Credit));
    }

    #[test]
    fn a_customer_deposit_raises_both_cash_and_what_we_owe() {
        // The canonical worked example: a customer deposits $100.
        let hundred = usd(10_000);
        let cash = AccountKind::Asset
            .balance_effect(Direction::Debit, hundred)
            .unwrap();
        let owed_to_customer = AccountKind::Liability
            .balance_effect(Direction::Credit, hundred)
            .unwrap();

        // Both sides increase. The customer's balance is our liability.
        assert_eq!(cash, hundred);
        assert_eq!(owed_to_customer, hundred);
    }

    #[test]
    fn posting_on_the_normal_side_contributes_positively() {
        for kind in ALL_KINDS {
            let effect = kind.balance_effect(kind.normal_side(), usd(500)).unwrap();
            assert_eq!(effect, usd(500), "{kind} on its normal side");
        }
    }

    #[test]
    fn posting_against_the_normal_side_contributes_negatively() {
        for kind in ALL_KINDS {
            let effect = kind
                .balance_effect(kind.normal_side().opposite(), usd(500))
                .unwrap();
            assert_eq!(effect, usd(-500), "{kind} against its normal side");
        }
    }

    #[test]
    fn effects_of_opposite_directions_cancel() {
        // The property that makes a balanced transaction leave the ledger's
        // total unchanged, for every account kind.
        for kind in ALL_KINDS {
            let increase = kind.balance_effect(Direction::Debit, usd(42)).unwrap();
            let decrease = kind.balance_effect(Direction::Credit, usd(42)).unwrap();
            assert!(increase.checked_add(decrease).unwrap().is_zero(), "{kind}");
        }
    }

    #[test]
    fn effect_preserves_currency() {
        let amount = Money::from_minor_units(7, Currency::Jpy);
        let effect = AccountKind::Revenue
            .balance_effect(Direction::Debit, amount)
            .unwrap();
        assert_eq!(effect.currency(), Currency::Jpy);
    }

    #[test]
    fn negating_an_unrepresentable_amount_is_reported() {
        // i64::MIN has no positive counterpart, so the sign flip must fail
        // rather than wrap. Not a valid posting amount, but Money permits it.
        assert_eq!(
            AccountKind::Asset.balance_effect(Direction::Credit, usd(i64::MIN)),
            Err(MoneyError::Overflow)
        );
    }

    #[test]
    fn an_opened_account_keeps_what_it_was_opened_with() {
        let account = Account::open(AccountKind::Liability, Currency::Brl, "customer 42").unwrap();

        assert_eq!(account.kind(), AccountKind::Liability);
        assert_eq!(account.currency(), Currency::Brl);
        assert_eq!(account.name(), "customer 42");
    }

    #[test]
    fn opening_mints_a_distinct_identity() {
        let a = Account::open(AccountKind::Asset, Currency::Usd, "cash").unwrap();
        let b = Account::open(AccountKind::Asset, Currency::Usd, "cash").unwrap();

        // Same kind, same currency, same name: still two different accounts.
        assert_ne!(a.id(), b.id());
        assert_ne!(a, b);
    }

    #[test]
    fn names_are_trimmed() {
        let account = Account::open(AccountKind::Expense, Currency::Usd, "  fees  ").unwrap();
        assert_eq!(account.name(), "fees");
    }

    #[test]
    fn an_unnamed_account_cannot_be_opened() {
        for name in ["", "   ", "\t\n"] {
            assert_eq!(
                Account::open(AccountKind::Asset, Currency::Usd, name),
                Err(AccountError::EmptyName),
                "{name:?}"
            );
        }
    }

    #[test]
    fn an_empty_balance_is_in_the_accounts_currency() {
        let account = Account::open(AccountKind::Asset, Currency::Jpy, "vault").unwrap();
        let zero = account.zero_balance();

        assert!(zero.is_zero());
        assert_eq!(zero.currency(), Currency::Jpy);
    }

    #[test]
    fn an_account_applies_its_kinds_sign_rule() {
        let cash = Account::open(AccountKind::Asset, Currency::Usd, "cash").unwrap();

        assert_eq!(
            cash.balance_effect(Direction::Debit, usd(250)).unwrap(),
            usd(250)
        );
        assert_eq!(
            cash.balance_effect(Direction::Credit, usd(250)).unwrap(),
            usd(-250)
        );
    }

    #[test]
    fn an_account_rejects_an_amount_in_another_currency() {
        // The point of fixing the currency at opening: the mismatch is caught
        // here, not after it has reached a transaction.
        let account = Account::open(AccountKind::Asset, Currency::Usd, "cash").unwrap();
        let euros = Money::from_minor_units(100, Currency::Eur);

        assert_eq!(
            account.balance_effect(Direction::Debit, euros),
            Err(MoneyError::CurrencyMismatch {
                left: Currency::Usd,
                right: Currency::Eur,
            })
        );
    }

    #[test]
    fn account_errors_describe_themselves() {
        assert_eq!(
            AccountError::EmptyName.to_string(),
            "account name must not be empty"
        );
    }

    #[test]
    fn display_names_the_kind() {
        assert_eq!(AccountKind::Liability.to_string(), "liability");
    }
}

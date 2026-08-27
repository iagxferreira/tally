//! Account identity, classification, and the sign rule for deriving balances.

use core::fmt;

use uuid::Uuid;

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

#[cfg(test)]
mod tests {
    use super::{AccountId, AccountKind};
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
    fn display_names_the_kind() {
        assert_eq!(AccountKind::Liability.to_string(), "liability");
    }
}

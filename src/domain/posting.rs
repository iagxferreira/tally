//! A single leg of a transaction.

use super::account::{Account, AccountId};
use super::currency::Currency;
use super::direction::Direction;
use super::money::Money;

/// One movement against one account: a direction and a strictly positive
/// amount.
///
/// A posting is never valid on its own — it is half of a movement at minimum,
/// and it becomes meaningful only inside a balanced transaction. What this type
/// guarantees is the *intrinsic* half of that: the amount is positive and
/// denominated in the account's currency.
///
/// # Why the amount is strictly positive
///
/// Direction is carried by [`Direction`], never by the sign of the number. If
/// amounts could be negative there would be two spellings of every movement — a
/// debit of `-50` and a credit of `50` — and `sum(debits) == sum(credits)`
/// would stop discriminating between a balanced transaction and a nonsensical
/// one. Zero is rejected for the same reason: a zero posting records no
/// economic fact, but it would let a transaction satisfy the balancing rule
/// vacuously.
///
/// # Why it holds an identifier, not an account
///
/// A posting is a journal entry. It must stay readable long after the
/// [`Account`] value that produced it has been dropped, and a leg of a
/// transaction has no business owning or borrowing the account it references.
/// Storing [`AccountId`] keeps `Posting` a `Copy` value with no lifetime
/// attached.
///
/// The price is that resolving a posting back to its account requires the chart
/// of accounts. That is the ledger's job, and it is also where the referential
/// invariant — the account exists — is checked.
///
/// # Construction
///
/// There is no public constructor here. Postings are minted by
/// [`Account::post`], so the account is necessarily in hand and the currency
/// check cannot be skipped.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct Posting {
    account: AccountId,
    direction: Direction,
    amount: Money,
}

impl Posting {
    /// Validates and constructs a posting against `account`.
    ///
    /// Deliberately `pub(super)`: the domain can reach it, the outside world
    /// cannot. The validation lives next to the type whose invariant it
    /// protects, while the only public way in is [`Account::post`] — so there
    /// is exactly one construction path and it always has an account.
    pub(super) fn new(
        account: &Account,
        direction: Direction,
        amount: Money,
    ) -> Result<Self, PostingError> {
        if amount.currency() != account.currency() {
            return Err(PostingError::CurrencyMismatch {
                account: account.currency(),
                amount: amount.currency(),
            });
        }
        if !amount.is_positive() {
            return Err(PostingError::NonPositiveAmount { amount });
        }

        Ok(Self {
            account: account.id(),
            direction,
            amount,
        })
    }

    /// The account this posting moves.
    #[must_use]
    pub const fn account(self) -> AccountId {
        self.account
    }

    /// The side of the transaction this posting sits on.
    #[must_use]
    pub const fn direction(self) -> Direction {
        self.direction
    }

    /// The amount moved. Always strictly positive.
    #[must_use]
    pub const fn amount(self) -> Money {
        self.amount
    }

    /// The currency this posting is denominated in, which is by construction
    /// the currency of its account.
    #[must_use]
    pub const fn currency(self) -> Currency {
        self.amount.currency()
    }
}

/// Ways a posting can fail to be constructed.
///
/// Both variants carry the values that were rejected, so a caller can report
/// what was wrong without re-deriving it. The fields are named for their roles
/// rather than `left`/`right`, because the two sides are not interchangeable
/// here: one is the account's fixed currency, the other is what was offered.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum PostingError {
    /// The amount was zero or negative. Direction is carried by
    /// [`Direction`], so the magnitude must be positive.
    NonPositiveAmount {
        /// The rejected amount.
        amount: Money,
    },
    /// The amount was not in the account's currency. Tally never converts
    /// implicitly.
    CurrencyMismatch {
        /// The currency fixed when the account was opened.
        account: Currency,
        /// The currency of the offered amount.
        amount: Currency,
    },
}

impl core::fmt::Display for PostingError {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            Self::NonPositiveAmount { amount } => {
                write!(f, "posting amount must be strictly positive, got {amount}")
            }
            Self::CurrencyMismatch { account, amount } => {
                write!(
                    f,
                    "posting in {amount} against an account denominated in {account}"
                )
            }
        }
    }
}

impl core::error::Error for PostingError {}

#[cfg(test)]
mod tests {
    use super::{Posting, PostingError};
    use crate::domain::{Account, AccountKind, Currency, Direction, Money};

    fn cash() -> Account {
        Account::open(AccountKind::Asset, Currency::Usd, "cash").unwrap()
    }

    fn usd(minor_units: i64) -> Money {
        Money::from_minor_units(minor_units, Currency::Usd)
    }

    #[test]
    fn a_posting_records_the_account_direction_and_amount() {
        let cash = cash();
        let posting = cash.post(Direction::Debit, usd(1_050)).unwrap();

        assert_eq!(posting.account(), cash.id());
        assert_eq!(posting.direction(), Direction::Debit);
        assert_eq!(posting.amount(), usd(1_050));
        assert_eq!(posting.currency(), Currency::Usd);
    }

    #[test]
    fn a_posting_is_a_copy_value_with_no_borrow_of_its_account() {
        // Constructed from a borrow, but outlives it: the posting holds an id,
        // not the account.
        let posting = {
            let cash = cash();
            cash.post(Direction::Credit, usd(1)).unwrap()
        };
        let copied = posting;

        assert_eq!(copied, posting);
    }

    #[test]
    fn a_zero_posting_records_no_fact_and_is_rejected() {
        // Otherwise a transaction of zero-amount postings would satisfy
        // sum(debits) == sum(credits) while recording nothing.
        assert_eq!(
            cash().post(Direction::Debit, usd(0)),
            Err(PostingError::NonPositiveAmount { amount: usd(0) })
        );
    }

    #[test]
    fn a_negative_posting_is_rejected() {
        // Direction is carried by Direction, never by the sign. Allowing this
        // would give every movement two spellings.
        assert_eq!(
            cash().post(Direction::Credit, usd(-1)),
            Err(PostingError::NonPositiveAmount { amount: usd(-1) })
        );
    }

    #[test]
    fn both_directions_take_the_same_positive_amount() {
        for direction in [Direction::Debit, Direction::Credit] {
            let posting = cash().post(direction, usd(500)).unwrap();
            assert_eq!(posting.amount(), usd(500), "{direction}");
            assert_eq!(posting.direction(), direction);
        }
    }

    #[test]
    fn a_posting_cannot_be_denominated_in_another_currency() {
        assert_eq!(
            cash().post(
                Direction::Debit,
                Money::from_minor_units(100, Currency::Jpy)
            ),
            Err(PostingError::CurrencyMismatch {
                account: Currency::Usd,
                amount: Currency::Jpy,
            })
        );
    }

    #[test]
    fn a_postings_currency_always_matches_its_account() {
        // The property the account-gated constructor buys us: there is no
        // sequence of public calls producing a posting whose currency differs
        // from its account's.
        for currency in [Currency::Usd, Currency::Jpy, Currency::Kwd] {
            let account = Account::open(AccountKind::Revenue, currency, "fees").unwrap();
            let posting = account
                .post(Direction::Credit, Money::from_minor_units(7, currency))
                .unwrap();
            assert_eq!(posting.currency(), account.currency());
        }
    }

    #[test]
    fn an_accounts_sign_rule_applies_to_its_postings_amount() {
        // Postings stay unsigned; the sign comes from the account kind. This is
        // the seam the ledger will fold over.
        let revenue = Account::open(AccountKind::Revenue, Currency::Usd, "fees").unwrap();
        let posting = revenue.post(Direction::Debit, usd(300)).unwrap();

        let effect = revenue
            .balance_effect(posting.direction(), posting.amount())
            .unwrap();

        assert!(posting.amount().is_positive());
        assert_eq!(effect, usd(-300));
    }

    #[test]
    fn posting_errors_describe_themselves() {
        assert_eq!(
            PostingError::NonPositiveAmount { amount: usd(0) }.to_string(),
            "posting amount must be strictly positive, got 0.00 USD"
        );
        assert_eq!(
            PostingError::CurrencyMismatch {
                account: Currency::Usd,
                amount: Currency::Jpy,
            }
            .to_string(),
            "posting in JPY against an account denominated in USD"
        );
    }

    #[test]
    fn the_validating_constructor_is_not_public() {
        // Reachable here because tests are a descendant module; `pub(super)`
        // keeps it out of the crate's public API, so Account::post stays the
        // only way in from outside.
        let cash = cash();
        assert!(Posting::new(&cash, Direction::Debit, usd(1)).is_ok());
    }
}

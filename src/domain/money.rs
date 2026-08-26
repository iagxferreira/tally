//! Exact monetary amounts.
//!
//! Money is represented as a signed integer count of a currency's *minor
//! unit* (cents for USD, yen for JPY, fils for KWD) paired with the currency
//! itself. There is no floating point anywhere in this module, and there is no
//! path by which a `Money` value can become inexact.
//!
//! See `docs/adr/001-money-representation.md` for the alternatives considered.

use core::fmt;

use super::currency::Currency;

/// An exact monetary amount in a single currency.
///
/// `Money` is a 16-byte `Copy` value type: 8 bytes of `i64` plus a
/// discriminant, padded to the alignment of `i64`. Copying it is a register
/// move, so it behaves like a primitive at call sites and never allocates.
///
/// # Sign
///
/// `Money` is signed. This is deliberate even though *posting* amounts must be
/// strictly positive (direction is carried by a separate `Debit`/`Credit`
/// enum, not by the sign of the number). Balances are signed by nature, so the
/// type permits negatives and the *posting* type will forbid them at its own
/// boundary.
///
/// # Equality
///
/// Two `Money` values are equal only if both the amount and the currency
/// match. `100 USD != 100 EUR`.
///
/// `Money` deliberately does **not** implement [`Ord`] or [`PartialOrd`]:
/// ordering across different currencies is meaningless, and a derived
/// implementation would silently compare the amounts first and produce
/// nonsense. Use [`Money::try_cmp`] instead, which reports the mismatch.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct Money {
    minor_units: i64,
    currency: Currency,
}

impl Money {
    /// Constructs an amount from a raw count of minor units.
    ///
    /// This is total: every `i64` is a valid amount of minor units, so there is
    /// nothing to fail on.
    ///
    /// ```
    /// # use tally::{Currency, Money};
    /// let ten_fifty = Money::from_minor_units(1_050, Currency::Usd);
    /// assert_eq!(ten_fifty.to_string(), "10.50 USD");
    /// ```
    #[must_use]
    pub const fn from_minor_units(minor_units: i64, currency: Currency) -> Self {
        Self {
            minor_units,
            currency,
        }
    }

    /// Constructs an amount from a whole number of major units.
    ///
    /// # Errors
    ///
    /// Returns [`MoneyError::Overflow`] if the amount does not fit in an `i64`
    /// once scaled to minor units.
    pub fn from_major_units(major_units: i64, currency: Currency) -> Result<Self, MoneyError> {
        major_units
            .checked_mul(currency.minor_units_per_major())
            .map(|minor_units| Self::from_minor_units(minor_units, currency))
            .ok_or(MoneyError::Overflow)
    }

    /// The additive identity for a currency.
    #[must_use]
    pub const fn zero(currency: Currency) -> Self {
        Self::from_minor_units(0, currency)
    }

    /// The raw count of minor units.
    #[must_use]
    pub const fn minor_units(self) -> i64 {
        self.minor_units
    }

    /// The currency this amount is denominated in.
    #[must_use]
    pub const fn currency(self) -> Currency {
        self.currency
    }

    /// Returns `true` if the amount is exactly zero.
    #[must_use]
    pub const fn is_zero(self) -> bool {
        self.minor_units == 0
    }

    /// Returns `true` if the amount is strictly greater than zero.
    #[must_use]
    pub const fn is_positive(self) -> bool {
        self.minor_units > 0
    }

    /// Returns `true` if the amount is strictly less than zero.
    #[must_use]
    pub const fn is_negative(self) -> bool {
        self.minor_units < 0
    }

    /// Adds two amounts of the same currency.
    ///
    /// # Errors
    ///
    /// - [`MoneyError::CurrencyMismatch`] if the currencies differ.
    /// - [`MoneyError::Overflow`] if the result does not fit in an `i64`.
    pub fn checked_add(self, rhs: Self) -> Result<Self, MoneyError> {
        self.same_currency_as(rhs)?;
        self.minor_units
            .checked_add(rhs.minor_units)
            .map(|minor_units| Self::from_minor_units(minor_units, self.currency))
            .ok_or(MoneyError::Overflow)
    }

    /// Subtracts an amount of the same currency.
    ///
    /// # Errors
    ///
    /// - [`MoneyError::CurrencyMismatch`] if the currencies differ.
    /// - [`MoneyError::Overflow`] if the result does not fit in an `i64`.
    pub fn checked_sub(self, rhs: Self) -> Result<Self, MoneyError> {
        self.same_currency_as(rhs)?;
        self.minor_units
            .checked_sub(rhs.minor_units)
            .map(|minor_units| Self::from_minor_units(minor_units, self.currency))
            .ok_or(MoneyError::Overflow)
    }

    /// Negates the amount.
    ///
    /// # Errors
    ///
    /// Returns [`MoneyError::Overflow`] for `i64::MIN`, whose negation is not
    /// representable in two's complement.
    pub fn checked_neg(self) -> Result<Self, MoneyError> {
        self.minor_units
            .checked_neg()
            .map(|minor_units| Self::from_minor_units(minor_units, self.currency))
            .ok_or(MoneyError::Overflow)
    }

    /// Compares two amounts of the same currency.
    ///
    /// # Errors
    ///
    /// Returns [`MoneyError::CurrencyMismatch`] if the currencies differ, since
    /// there is no exchange-rate-free ordering between currencies.
    pub fn try_cmp(self, rhs: Self) -> Result<core::cmp::Ordering, MoneyError> {
        self.same_currency_as(rhs)?;
        Ok(self.minor_units.cmp(&rhs.minor_units))
    }

    fn same_currency_as(self, rhs: Self) -> Result<(), MoneyError> {
        if self.currency == rhs.currency {
            Ok(())
        } else {
            Err(MoneyError::CurrencyMismatch {
                left: self.currency,
                right: rhs.currency,
            })
        }
    }
}

impl fmt::Display for Money {
    /// Renders the amount at the currency's natural scale, e.g. `-10.50 USD`,
    /// `1000 JPY`, `1.234 KWD`.
    #[expect(
        clippy::arithmetic_side_effects,
        reason = "divisor is 10^scale, so it is >= 1 and the division cannot trap"
    )]
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let sign = if self.minor_units.is_negative() {
            "-"
        } else {
            ""
        };
        // `unsigned_abs` rather than `abs`: `i64::MIN.abs()` panics, but its
        // magnitude is representable as a `u64`.
        let magnitude = self.minor_units.unsigned_abs();
        let scale = self.currency.scale() as usize;

        if scale == 0 {
            return write!(f, "{sign}{magnitude} {}", self.currency);
        }

        // Non-zero and never zero, so the division below cannot trap.
        let divisor = self.currency.minor_units_per_major().unsigned_abs();
        let major = magnitude / divisor;
        let minor = magnitude % divisor;
        write!(f, "{sign}{major}.{minor:0scale$} {}", self.currency)
    }
}

/// Ways a monetary operation can fail.
///
/// This is a closed enum so that callers can `match` on the failure and react
/// differently per case, rather than parsing a string. It is `Copy` because it
/// carries no owned data.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[non_exhaustive]
pub enum MoneyError {
    /// An operation combined two different currencies.
    ///
    /// Tally never converts implicitly: an exchange rate is a valuation
    /// opinion, not a fact, and the ledger records only facts.
    CurrencyMismatch {
        /// Currency of the left-hand operand.
        left: Currency,
        /// Currency of the right-hand operand.
        right: Currency,
    },
    /// The result is not representable in `i64` minor units.
    Overflow,
}

impl fmt::Display for MoneyError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::CurrencyMismatch { left, right } => {
                write!(f, "currency mismatch: {left} and {right}")
            }
            Self::Overflow => f.write_str("monetary amount overflowed i64 minor units"),
        }
    }
}

impl core::error::Error for MoneyError {}

#[cfg(test)]
mod tests {
    use super::{Money, MoneyError};
    use crate::domain::Currency;
    use core::cmp::Ordering;

    const fn usd(minor_units: i64) -> Money {
        Money::from_minor_units(minor_units, Currency::Usd)
    }

    #[test]
    fn money_is_a_sixteen_byte_copy_value() {
        assert_eq!(size_of::<Money>(), 16);
        // Compiles only because `Money: Copy`.
        let a = usd(1);
        let b = a;
        assert_eq!(a, b);
    }

    #[test]
    fn equality_discriminates_on_currency() {
        assert_ne!(
            Money::from_minor_units(100, Currency::Usd),
            Money::from_minor_units(100, Currency::Eur)
        );
    }

    #[test]
    fn major_units_are_scaled_by_currency() {
        assert_eq!(
            Money::from_major_units(10, Currency::Usd).unwrap(),
            usd(1_000)
        );
        assert_eq!(
            Money::from_major_units(10, Currency::Jpy).unwrap(),
            Money::from_minor_units(10, Currency::Jpy)
        );
        assert_eq!(
            Money::from_major_units(10, Currency::Kwd).unwrap(),
            Money::from_minor_units(10_000, Currency::Kwd)
        );
    }

    #[test]
    fn major_units_overflow_is_reported_not_wrapped() {
        assert_eq!(
            Money::from_major_units(i64::MAX, Currency::Usd),
            Err(MoneyError::Overflow)
        );
    }

    #[test]
    fn addition_is_exact() {
        // The canonical float failure: 0.1 + 0.2 != 0.3.
        let sum = usd(10).checked_add(usd(20)).unwrap();
        assert_eq!(sum, usd(30));
        assert_eq!(sum.minor_units(), 30);
    }

    #[test]
    fn addition_rejects_mixed_currencies() {
        let result = usd(100).checked_add(Money::from_minor_units(100, Currency::Eur));
        assert_eq!(
            result,
            Err(MoneyError::CurrencyMismatch {
                left: Currency::Usd,
                right: Currency::Eur,
            })
        );
    }

    #[test]
    fn addition_reports_overflow_instead_of_wrapping() {
        assert_eq!(usd(i64::MAX).checked_add(usd(1)), Err(MoneyError::Overflow));
    }

    #[test]
    fn subtraction_reports_overflow_instead_of_wrapping() {
        assert_eq!(usd(i64::MIN).checked_sub(usd(1)), Err(MoneyError::Overflow));
    }

    #[test]
    fn subtraction_can_produce_negative_balances() {
        assert_eq!(usd(10).checked_sub(usd(30)).unwrap(), usd(-20));
    }

    #[test]
    fn negation_of_the_minimum_is_not_representable() {
        assert_eq!(usd(i64::MIN).checked_neg(), Err(MoneyError::Overflow));
        assert_eq!(usd(5).checked_neg().unwrap(), usd(-5));
    }

    #[test]
    fn zero_is_the_additive_identity() {
        let zero = Money::zero(Currency::Usd);
        assert!(zero.is_zero());
        assert_eq!(usd(742).checked_add(zero).unwrap(), usd(742));
    }

    #[test]
    fn sign_predicates_are_strict() {
        assert!(usd(1).is_positive());
        assert!(!usd(0).is_positive());
        assert!(usd(-1).is_negative());
        assert!(!usd(0).is_negative());
    }

    #[test]
    fn comparison_requires_a_shared_currency() {
        assert_eq!(usd(1).try_cmp(usd(2)).unwrap(), Ordering::Less);
        assert!(
            usd(1)
                .try_cmp(Money::from_minor_units(2, Currency::Gbp))
                .is_err()
        );
    }

    #[test]
    fn display_respects_currency_scale() {
        assert_eq!(usd(1_050).to_string(), "10.50 USD");
        assert_eq!(usd(5).to_string(), "0.05 USD");
        assert_eq!(usd(-1_050).to_string(), "-10.50 USD");
        assert_eq!(
            Money::from_minor_units(1_000, Currency::Jpy).to_string(),
            "1000 JPY"
        );
        assert_eq!(
            Money::from_minor_units(1_234, Currency::Kwd).to_string(),
            "1.234 KWD"
        );
    }

    #[test]
    fn display_handles_the_minimum_without_panicking() {
        assert_eq!(usd(i64::MIN).to_string(), "-92233720368547758.08 USD");
    }

    #[test]
    fn errors_describe_themselves() {
        assert_eq!(
            MoneyError::CurrencyMismatch {
                left: Currency::Usd,
                right: Currency::Jpy,
            }
            .to_string(),
            "currency mismatch: USD and JPY"
        );
    }
}

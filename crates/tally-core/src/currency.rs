//! Currency identity and its decimal scale.

use core::fmt;

/// A currency supported by the ledger.
///
/// This is intentionally a *closed* enum rather than an open `code + scale`
/// struct. The trade-off is recorded in `docs/adr/001-money-representation.md`:
/// we would rather support a handful of currencies honestly than 180 badly.
/// A closed set means an invalid currency cannot be constructed at all, and
/// that every `match` over currencies is checked for exhaustiveness by the
/// compiler.
///
/// The variants below are chosen to cover all three decimal scales that exist
/// in ISO 4217 practice, so that scale-handling bugs surface in our own tests
/// rather than in production:
///
/// - scale 0: [`Currency::Jpy`]
/// - scale 2: [`Currency::Usd`], [`Currency::Eur`], [`Currency::Gbp`], [`Currency::Brl`]
/// - scale 3: [`Currency::Kwd`]
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
#[non_exhaustive]
pub enum Currency {
    /// United States dollar.
    Usd,
    /// Euro.
    Eur,
    /// Pound sterling.
    Gbp,
    /// Brazilian real.
    Brl,
    /// Japanese yen. Has no minor unit in circulation (scale 0).
    Jpy,
    /// Kuwaiti dinar. Divided into 1000 fils (scale 3).
    Kwd,
}

impl Currency {
    /// The ISO 4217 alphabetic code.
    #[must_use]
    pub const fn code(self) -> &'static str {
        match self {
            Self::Usd => "USD",
            Self::Eur => "EUR",
            Self::Gbp => "GBP",
            Self::Brl => "BRL",
            Self::Jpy => "JPY",
            Self::Kwd => "KWD",
        }
    }

    /// Number of decimal places between the major and minor unit.
    ///
    /// This is the exponent, not the divisor: USD has scale 2 because one
    /// dollar is 10^2 cents.
    #[must_use]
    pub const fn scale(self) -> u32 {
        match self {
            Self::Usd | Self::Eur | Self::Gbp | Self::Brl => 2,
            Self::Jpy => 0,
            Self::Kwd => 3,
        }
    }

    /// How many minor units make up one major unit (10^scale).
    ///
    /// Never zero, so it is always safe to divide by.
    #[must_use]
    pub const fn minor_units_per_major(self) -> i64 {
        10_i64.pow(self.scale())
    }
}

impl fmt::Display for Currency {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.code())
    }
}

#[cfg(test)]
mod tests {
    use super::Currency;

    #[test]
    fn scales_match_iso_4217_practice() {
        assert_eq!(Currency::Usd.scale(), 2);
        assert_eq!(Currency::Jpy.scale(), 0);
        assert_eq!(Currency::Kwd.scale(), 3);
    }

    #[test]
    fn minor_units_per_major_is_ten_to_the_scale() {
        assert_eq!(Currency::Usd.minor_units_per_major(), 100);
        assert_eq!(Currency::Jpy.minor_units_per_major(), 1);
        assert_eq!(Currency::Kwd.minor_units_per_major(), 1_000);
    }

    #[test]
    fn display_uses_iso_code() {
        assert_eq!(Currency::Brl.to_string(), "BRL");
    }
}

//! The two sides of a double-entry posting.

use core::fmt;

/// Which side of a transaction a posting sits on.
///
/// Debit and credit are *directions*, not signs and not "money in" and "money
/// out". A posting amount is always strictly positive; this type carries the
/// direction, so there is exactly one representation of any movement.
///
/// Whether a debit increases or decreases an account depends on the account's
/// normal balance side — see [`AccountKind::normal_side`].
///
/// This enum is deliberately **not** `#[non_exhaustive]`. Unlike currencies,
/// there will never be a third direction: two sides is what makes the
/// bookkeeping double-entry. Marking it extensible would force every consumer
/// into a wildcard match arm to guard against a case that cannot occur.
///
/// [`AccountKind::normal_side`]: super::AccountKind::normal_side
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Direction {
    /// The left side of a transaction. Increases assets and expenses.
    Debit,
    /// The right side of a transaction. Increases liabilities, equity and revenue.
    Credit,
}

impl Direction {
    /// The other direction.
    ///
    /// Every posting has a counterpart on the opposite side; that is the
    /// mechanism by which a transaction sums to zero.
    #[must_use]
    pub const fn opposite(self) -> Self {
        match self {
            Self::Debit => Self::Credit,
            Self::Credit => Self::Debit,
        }
    }
}

impl fmt::Display for Direction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::Debit => "debit",
            Self::Credit => "credit",
        })
    }
}

#[cfg(test)]
mod tests {
    use super::Direction;

    #[test]
    fn opposite_swaps_the_side() {
        assert_eq!(Direction::Debit.opposite(), Direction::Credit);
        assert_eq!(Direction::Credit.opposite(), Direction::Debit);
    }

    #[test]
    fn opposite_is_an_involution() {
        for direction in [Direction::Debit, Direction::Credit] {
            assert_eq!(direction.opposite().opposite(), direction);
        }
    }

    #[test]
    fn display_names_the_side() {
        assert_eq!(Direction::Debit.to_string(), "debit");
        assert_eq!(Direction::Credit.to_string(), "credit");
    }
}

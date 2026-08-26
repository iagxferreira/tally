//! Pure domain model for the Tally double-entry ledger.
//!
//! This crate has no dependencies and no knowledge of HTTP, SQL, or message
//! brokers. Everything here is a value type or a rule about value types.
//!
//! Currently implemented: exact monetary amounts. Accounts, postings,
//! transactions, and the ledger itself are not modelled yet.

mod currency;
mod money;

pub use currency::Currency;
pub use money::{Money, MoneyError};

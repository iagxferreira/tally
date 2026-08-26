//! The pure financial domain.
//!
//! Nothing in this module may depend on infrastructure: no HTTP types, no SQL
//! drivers, no brokers, and no async runtime unless the domain itself genuinely
//! requires one. Everything here is a value type or a rule about value types.
//!
//! Currently modelled: currencies and exact monetary amounts. Accounts,
//! postings, transactions and the ledger itself do not exist yet.

mod currency;
mod money;

pub use currency::Currency;
pub use money::{Money, MoneyError};

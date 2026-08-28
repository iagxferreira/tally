//! The pure financial domain.
//!
//! Nothing in this module may depend on infrastructure: no HTTP types, no SQL
//! drivers, no brokers, and no async runtime unless the domain itself genuinely
//! requires one. Everything here is a value type or a rule about value types.
//!
//! Currently modelled: currencies, exact monetary amounts, posting direction,
//! and accounts — their identity, their classification, and the sign rule that
//! determines how postings affect their balances. Postings, transactions and
//! the ledger do not exist yet.

mod account;
mod currency;
mod direction;
mod money;

pub use account::{Account, AccountError, AccountId, AccountKind};
pub use currency::Currency;
pub use direction::Direction;
pub use money::{Money, MoneyError};

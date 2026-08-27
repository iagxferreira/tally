//! Tally, a double-entry financial ledger.
//!
//! The crate is organised around one hard boundary: everything under
//! [`domain`] is pure. It models money, accounts and transactions as value
//! types and rules about value types, and it knows nothing about HTTP, SQL, or
//! message brokers.
//!
//! That boundary is currently a convention rather than something the compiler
//! enforces, because Tally is a single crate. See
//! `docs/adr/002-crate-and-module-layout.md` for when it becomes mechanical.

pub mod domain;

pub use domain::{Currency, Direction, Money, MoneyError};

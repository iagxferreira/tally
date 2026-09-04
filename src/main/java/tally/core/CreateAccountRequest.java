package tally.core;

import tally.domain.AccountKind;
import tally.domain.Currency;

/** JSON command for opening an account. */
public record CreateAccountRequest(AccountKind kind, Currency currency) {}

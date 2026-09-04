package tally.core;

import jakarta.validation.constraints.NotNull;
import tally.domain.AccountKind;
import tally.domain.Currency;

/** JSON command for opening an account. */
public record CreateAccountRequest(@NotNull AccountKind kind, @NotNull Currency currency) {}

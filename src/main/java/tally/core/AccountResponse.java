package tally.core;

import java.util.UUID;
import tally.domain.Account;
import tally.domain.AccountKind;
import tally.domain.Currency;

/** JSON representation of an account. */
public record AccountResponse(UUID id, AccountKind kind, Currency currency) {

    /** Maps the domain entity to the HTTP representation. */
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.id().value(), account.kind(), account.currency());
    }
}

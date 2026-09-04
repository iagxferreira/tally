package tally.core;

import java.math.BigInteger;
import tally.domain.Currency;
import tally.domain.Money;

/** JSON representation of an account balance in minor units. */
public record BalanceResponse(BigInteger minorUnits, Currency currency) {

    /** Maps the exact domain amount to the HTTP representation. */
    public static BalanceResponse from(Money balance) {
        return new BalanceResponse(balance.minorUnits(), balance.currency());
    }
}

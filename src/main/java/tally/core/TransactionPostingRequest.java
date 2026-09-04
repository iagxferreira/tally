package tally.core;

import java.math.BigInteger;
import java.util.UUID;
import tally.domain.Currency;
import tally.domain.Direction;

/** One JSON posting in a transaction command. */
public record TransactionPostingRequest(
        UUID accountId, Direction direction, BigInteger minorUnits, Currency currency) {}

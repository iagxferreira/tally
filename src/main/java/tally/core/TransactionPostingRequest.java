package tally.core;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import java.util.UUID;
import tally.domain.Currency;
import tally.domain.Direction;

/** One JSON posting in a transaction command. */
public record TransactionPostingRequest(
        @NotNull UUID accountId,
        @NotNull Direction direction,
        @NotNull @Positive BigInteger minorUnits,
        @NotNull Currency currency) {}

package tally.core;

import java.math.BigInteger;
import java.util.UUID;
import tally.domain.Posting;

/** JSON representation of one immutable journal posting. */
public record PostingResponse(UUID accountId, String direction, BigInteger minorUnits, String currency) {

    /** Maps a domain posting without exposing its package-private construction. */
    public static PostingResponse from(Posting posting) {
        return new PostingResponse(
                posting.account().value(),
                posting.direction().name(),
                posting.amount().minorUnits(),
                posting.amount().currency().code());
    }
}

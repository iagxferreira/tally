package tally.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tally.domain.Transaction;

/** JSON representation of a posted transaction. */
public record TransactionResponse(UUID id, Instant occurredAt, List<PostingResponse> postings) {

    /** Maps the immutable domain transaction to its HTTP representation. */
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id().value(),
                transaction.occurredAt(),
                transaction.postings().stream().map(PostingResponse::from).toList());
    }
}

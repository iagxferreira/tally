package tally.core;

import java.util.List;

/** JSON command for posting one balanced transaction. */
public record PostTransactionRequest(List<TransactionPostingRequest> postings) {}

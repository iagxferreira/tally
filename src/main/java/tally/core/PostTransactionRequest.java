package tally.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** JSON command for posting one balanced transaction. */
public record PostTransactionRequest(
        @NotNull @Size(min = 2) List<@NotNull @Valid TransactionPostingRequest> postings) {}

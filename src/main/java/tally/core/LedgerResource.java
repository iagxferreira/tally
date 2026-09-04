package tally.core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tally.domain.DomainException;

/** Initial HTTP route contract for the in-memory ledger. */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Ledger")
public final class LedgerResource {

    private final LedgerService ledgerService;

    /** Creates the resource with its application-scoped ledger service. */
    @Inject
    public LedgerResource(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** The account command route. */
    @POST
    @Path("accounts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an account")
    @APIResponse(responseCode = "201", description = "Account created",
            content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @APIResponse(responseCode = "400", description = "Malformed account request", content = @Content)
    public Response accounts(CreateAccountRequest request) {
        if (request == null || request.kind() == null || request.currency() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("message", "kind and currency are required"))
                    .build();
        }
        var account = AccountResponse.from(
                ledgerService.openAccount(request.kind(), request.currency()));
        return Response.status(Response.Status.CREATED).entity(account).build();
    }

    /** The account balance query route. */
    @GET
    @Path("accounts/{id}/balance")
    @Operation(summary = "Read an account balance")
    @APIResponse(responseCode = "501", description = "Balance queries are not implemented", content = @Content)
    public Response balance(@PathParam("id") String id) {
        return notImplemented();
    }

    /** The transaction command route. */
    @POST
    @Path("transactions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Post a transaction")
    @APIResponse(responseCode = "201", description = "Transaction posted", content = @Content)
    @APIResponse(responseCode = "400", description = "Invalid transaction", content = @Content)
    public Response transactions(PostTransactionRequest request) {
        if (request == null || request.postings() == null || request.postings().isEmpty()) {
            return badRequest("at least two postings are required");
        }
        try {
            return Response.status(Response.Status.CREATED)
                    .entity(TransactionResponse.from(ledgerService.postTransaction(request)))
                    .build();
        } catch (DomainException | IllegalArgumentException | NullPointerException exception) {
            return badRequest(exception.getMessage());
        }
    }

    /** The journal query route. */
    @GET
    @Path("journal")
    @Operation(summary = "Read the journal")
    @APIResponse(responseCode = "200", description = "Posted transactions")
    public List<TransactionResponse> journal() {
        return ledgerService.journal().stream().map(TransactionResponse::from).toList();
    }

    private static Response notImplemented() {
        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "not implemented"))
                .build();
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", message == null ? "invalid request" : message))
                .build();
    }
}

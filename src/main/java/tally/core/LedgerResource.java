package tally.core;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** Initial HTTP route contract for the in-memory ledger. */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public final class LedgerResource {

    /** The account command route. */
    @POST
    @Path("accounts")
    public Response accounts() {
        return notImplemented();
    }

    /** The account balance query route. */
    @GET
    @Path("accounts/{id}/balance")
    public Response balance(@PathParam("id") String id) {
        return notImplemented();
    }

    /** The transaction command route. */
    @POST
    @Path("transactions")
    public Response transactions() {
        return notImplemented();
    }

    /** The journal query route. */
    @GET
    @Path("journal")
    public Response journal() {
        return notImplemented();
    }

    private static Response notImplemented() {
        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "not implemented"))
                .build();
    }
}

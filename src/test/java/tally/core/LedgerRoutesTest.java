package tally.core;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Defines the initial HTTP route contract before route behavior is implemented. */
@QuarkusTest
class LedgerRoutesTest {

    @Test
    void accountsRouteRequiresJson() {
        given().when().post("/accounts").then().statusCode(415);
    }

    @Test
    void rejectsAnIncompleteAccountRequest() {
        given().contentType("application/json")
                .body("{}")
                .when().post("/accounts")
                .then().statusCode(400)
                .body("message", is("kind and currency are required"));
    }

    @Test
    void createsAnAccount() {
        given().contentType("application/json")
                .body("""
                        {"kind":"ASSET","currency":"USD"}
                        """)
                .when().post("/accounts")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("kind", is("ASSET"))
                .body("currency", is("USD"));
    }

    @Test
    void balanceRouteExists() {
        given().when().get("/accounts/00000000-0000-7000-8000-000000000000/balance")
                .then().statusCode(501).body("message", is("not implemented"));
    }

    @Test
    void transactionsRouteExists() {
        given().when().post("/transactions").then().statusCode(501).body("message", is("not implemented"));
    }

    @Test
    void journalRouteExists() {
        given().when().get("/journal").then().statusCode(501).body("message", is("not implemented"));
    }

    @Test
    void openApiDocumentsLedgerRoutes() {
        given().accept("application/json")
                .when().get("/q/openapi?format=json")
                .then().statusCode(200)
                .contentType(containsString("application/json"))
                .body("info.title", is("Tally Ledger API"))
                .body("info.version", is("0.1.0"))
                .body("components.schemas.AccountResponse", notNullValue())
                .body("paths.'/accounts'.post", notNullValue())
                .body("paths.'/accounts/{id}/balance'.get", notNullValue())
                .body("paths.'/transactions'.post", notNullValue())
                .body("paths.'/journal'.get", notNullValue());
    }

    @Test
    void swaggerUiIsAvailable() {
        given().when().get("/q/swagger-ui/").then().statusCode(200).contentType(containsString("text/html"));
    }
}

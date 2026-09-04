package tally.core;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
    void transactionsRouteRequiresJson() {
        given().when().post("/transactions").then().statusCode(415);
    }

    @Test
    void postsABalancedTransaction() {
        String debitAccount = createAccount("ASSET", "USD");
        String creditAccount = createAccount("REVENUE", "USD");

        String transactionId = given().contentType("application/json")
                .body(transactionJson(debitAccount, creditAccount))
                .when().post("/transactions")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("postings.size()", is(2))
                .body("postings[0].direction", is("DEBIT"))
                .extract().path("id");

        given().when().get("/journal")
                .then().statusCode(200)
                .body("id", hasItem(transactionId));
    }

    @Test
    void rejectsAnUnbalancedTransaction() {
        String debitAccount = createAccount("ASSET", "USD");
        String creditAccount = createAccount("REVENUE", "USD");

        given().contentType("application/json")
                .body(transactionJson(debitAccount, creditAccount, 1000, 900))
                .when().post("/transactions")
                .then().statusCode(400);
    }

    @Test
    void journalRouteReturnsPostedTransactions() {
        given().when().get("/journal").then().statusCode(200);
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

    private static String createAccount(String kind, String currency) {
        return given().contentType("application/json")
                .body("{\"kind\":\"" + kind + "\",\"currency\":\"" + currency + "\"}")
                .when().post("/accounts")
                .then().statusCode(201)
                .extract().path("id");
    }

    private static String transactionJson(String debitAccount, String creditAccount) {
        return transactionJson(debitAccount, creditAccount, 1000, 1000);
    }

    private static String transactionJson(
            String debitAccount, String creditAccount, int debitMinorUnits, int creditMinorUnits) {
        return """
                {"postings":[
                  {"accountId":"%s","direction":"DEBIT","minorUnits":%d,"currency":"USD"},
                  {"accountId":"%s","direction":"CREDIT","minorUnits":%d,"currency":"USD"}
                ]}
                """.formatted(debitAccount, debitMinorUnits, creditAccount, creditMinorUnits);
    }
}

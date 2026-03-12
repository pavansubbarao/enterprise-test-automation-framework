package com.framework.api;

import com.framework.base.BaseTest;
import com.framework.config.FrameworkConfig;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample API tests demonstrating the framework capabilities.
 *
 * Replace the endpoint and payload details with your actual application.
 * The @JiraIssue annotation wires each test to its Jira ticket automatically.
 */
@Epic("API Testing")
@Feature("User Management API")
public class SampleApiTest extends BaseTest {

    private String authToken;

    @BeforeClass
    public void authenticate() {
        step("Authenticate and store bearer token");

        // Example: POST /auth/login → extract token
        Response loginResp = ApiRequestBuilder.create()
                .withBody("{\"username\":\"testuser\",\"password\":\"testpass\"}")
                .post("/api/auth/login")
                .getResponse();

        // For demo purposes – replace with actual token extraction
        if (loginResp.getStatusCode() == 200) {
            authToken = loginResp.jsonPath().getString("token");
            RestAssuredBase.storeToken("default", authToken);
        }
    }

    // ------------------------------------------------------------------ //
    //  Happy path tests                                                    //
    // ------------------------------------------------------------------ //

    @Test(priority = 1)
    @Story("Health Check")
    @Description("Verify the API health endpoint returns 200 OK")
    @JiraIssue("QA-001")
    public void testHealthEndpoint() {
        step("Call GET /api/health");
        Response response = ApiRequestBuilder.create()
                .get("/api/health")
                .assertStatus(200)
                .getResponse();

        step("Verify response contains status field");
        String status = response.jsonPath().getString("status");
        assertThat(status).isNotBlank();
    }

    @Test(priority = 2)
    @Story("Create User")
    @Description("Verify a new user can be created via POST /api/users")
    @JiraIssue("QA-002")
    public void testCreateUser() {
        step("Build user creation payload");
        String payload = """
                {
                    "username": "testuser_auto_%d",
                    "email": "auto_%d@test.com",
                    "role": "viewer"
                }
                """.formatted(System.currentTimeMillis(), System.currentTimeMillis());

        step("POST /api/users and assert 201 Created");
        Response response = ApiRequestBuilder.create()
                .withBearerToken(authToken != null ? authToken : "")
                .withBody(payload)
                .post("/api/users")
                .assertStatus(201)
                .getResponse();

        step("Verify created user has an ID");
        String userId = response.jsonPath().getString("id");
        assertThat(userId).isNotBlank().describedAs("User ID should be present");

        step("Attach response body to report");
        attachText("Created User Response", response.asPrettyString());
    }

    @Test(priority = 3)
    @Story("Get User")
    @Description("Verify GET /api/users/{id} returns user details")
    @JiraIssue("QA-003")
    public void testGetUserById() {
        String userId = "1"; // Replace with dynamic ID from create test

        step("GET /api/users/" + userId);
        Response response = ApiRequestBuilder.create()
                .withBearerToken(authToken != null ? authToken : "")
                .get("/api/users/" + userId)
                .getResponse();

        int status = response.getStatusCode();
        assertThat(status).isIn(200, 404).describedAs("Expected 200 or 404");

        if (status == 200) {
            step("Verify response structure");
            assertThat(response.jsonPath().getString("id")).isNotBlank();
            assertThat(response.jsonPath().getString("email")).contains("@");
        }
    }

    // ------------------------------------------------------------------ //
    //  Negative tests                                                      //
    // ------------------------------------------------------------------ //

    @Test(priority = 4)
    @Story("Authentication")
    @Description("Verify unauthenticated requests return 401")
    @JiraIssue("QA-004")
    public void testUnauthorizedAccess() {
        step("Call protected endpoint without token");
        ApiRequestBuilder.create()
                .get("/api/users")
                .assertStatus(401);
    }

    @Test(priority = 5)
    @Story("Input Validation")
    @Description("Verify invalid payload returns 400 Bad Request")
    @JiraIssue("QA-005")
    public void testInvalidPayload() {
        step("POST with malformed JSON body");
        ApiRequestBuilder.create()
                .withBearerToken(authToken != null ? authToken : "")
                .withBody("{\"invalid\": true}")
                .post("/api/users")
                .assertStatus(400);
    }

    // ------------------------------------------------------------------ //
    //  Data-driven test                                                    //
    // ------------------------------------------------------------------ //

    @DataProvider(name = "userRoles", parallel = false)
    public Object[][] userRoles() {
        return com.framework.utils.DataProvider.builder()
                .row("admin",    "Full access user",  201)
                .row("viewer",   "Read-only user",    201)
                .row("invalid",  "Unknown role",      400)
                .build();
    }

    @Test(priority = 6, dataProvider = "userRoles")
    @Story("User Roles")
    @Description("Verify user creation with various roles")
    public void testCreateUserWithRole(String role, String description, int expectedStatus) {
        step("Create user with role: " + role + " (" + description + ")");
        String payload = """
                {"username":"test_%s_%d","email":"test_%d@test.com","role":"%s"}
                """.formatted(role, System.currentTimeMillis(), System.currentTimeMillis(), role);

        ApiRequestBuilder.create()
                .withBearerToken(authToken != null ? authToken : "")
                .withBody(payload)
                .post("/api/users")
                .assertStatus(expectedStatus);
    }
}

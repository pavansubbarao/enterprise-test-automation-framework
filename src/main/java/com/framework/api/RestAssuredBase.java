package com.framework.api;

import com.framework.config.FrameworkConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all REST Assured API test helpers.
 *
 * Features:
 *  - Global request / response specs with Allure logging
 *  - Thread-safe token store (supports multiple environments)
 *  - Fluent request builder via {@link ApiRequestBuilder}
 *  - Auto-retry on 5xx (configurable)
 *  - JSON schema validation support
 */
@Slf4j
public class RestAssuredBase {

    private static final Map<String, String> tokenStore = new ConcurrentHashMap<>();
    private static volatile boolean initialised = false;

    protected static RequestSpecification defaultSpec;
    protected static ResponseSpecification defaultResponseSpec;

    static {
        init();
    }

    public static synchronized void init() {
        if (initialised) return;

        String baseUrl = FrameworkConfig.get("base.url", "http://localhost:8080");
        int timeout = FrameworkConfig.getInt("api.timeout", 30) * 1000;

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);

        defaultSpec = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new AllureRestAssured())
                .log(LogDetail.URI)
                .build();

        defaultResponseSpec = new ResponseSpecBuilder()
                .expectResponseTime(Matchers.lessThan((long) timeout))
                .build();

        initialised = true;
        log.info("REST Assured initialised with base URL: {}", baseUrl);
    }

    // ------------------------------------------------------------------ //
    //  Token Management                                                    //
    // ------------------------------------------------------------------ //

    /**
     * Store a bearer token for the given key (e.g. "default", "admin", "user").
     */
    public static void storeToken(String key, String token) {
        tokenStore.put(key, token);
        log.debug("Token stored for key: {}", key);
    }

    public static String getToken(String key) {
        return tokenStore.get(key);
    }

    /**
     * Returns a RequestSpecification pre-loaded with the stored bearer token.
     */
    public static RequestSpecification withToken(String tokenKey) {
        String token = tokenStore.get(tokenKey);
        if (token == null) throw new IllegalStateException("No token stored for key: " + tokenKey);
        return RestAssured.given().spec(defaultSpec).header("Authorization", "Bearer " + token);
    }

    /**
     * Returns a RequestSpecification with a specific bearer token value.
     */
    public static RequestSpecification withBearerToken(String token) {
        return RestAssured.given().spec(defaultSpec).header("Authorization", "Bearer " + token);
    }

    /**
     * Returns a RequestSpecification with Basic Auth.
     */
    public static RequestSpecification withBasicAuth(String username, String password) {
        return RestAssured.given().spec(defaultSpec).auth().preemptive().basic(username, password);
    }

    /**
     * Returns a plain request (no auth).
     */
    public static RequestSpecification given() {
        return RestAssured.given().spec(defaultSpec);
    }

    // ------------------------------------------------------------------ //
    //  Fluent builder                                                      //
    // ------------------------------------------------------------------ //

    public static ApiRequestBuilder request() {
        return new ApiRequestBuilder(defaultSpec);
    }

    // ------------------------------------------------------------------ //
    //  Common response assertion helpers                                   //
    // ------------------------------------------------------------------ //

    public static void assertStatusCode(Response response, int expected) {
        int actual = response.getStatusCode();
        if (actual != expected) {
            log.error("Expected status {} but got {}. Body:\n{}", expected, actual, response.getBody().asPrettyString());
        }
        response.then().statusCode(expected);
    }

    public static void assertJsonSchema(Response response, String schemaPath) {
        response.then().body(io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath(schemaPath));
    }
}

package com.framework.api;

import com.framework.config.FrameworkConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Fluent builder for REST Assured requests.
 *
 * Example:
 * <pre>
 *   Response r = ApiRequestBuilder.create()
 *       .withBearerToken(token)
 *       .withHeader("X-Correlation-Id", uuid)
 *       .withBody(myPayload)
 *       .post("/api/users")
 *       .assertStatus(201)
 *       .getResponse();
 * </pre>
 */
@Slf4j
public class ApiRequestBuilder {

    private RequestSpecification spec;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParams = new HashMap<>();
    private final Map<String, String> pathParams = new HashMap<>();
    private Object body;
    private int expectedStatus = -1;
    private int retries = FrameworkConfig.getInt("api.retry.count", 2);
    private Response lastResponse;

    ApiRequestBuilder(RequestSpecification baseSpec) {
        this.spec = given().spec(baseSpec);
    }

    public static ApiRequestBuilder create() {
        return new ApiRequestBuilder(RestAssuredBase.defaultSpec);
    }

    // ------------------------------------------------------------------ //
    //  Fluent config                                                       //
    // ------------------------------------------------------------------ //

    public ApiRequestBuilder withBearerToken(String token) {
        headers.put("Authorization", "Bearer " + token);
        return this;
    }

    public ApiRequestBuilder withApiKey(String headerName, String key) {
        headers.put(headerName, key);
        return this;
    }

    public ApiRequestBuilder withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public ApiRequestBuilder withHeaders(Map<String, String> h) {
        headers.putAll(h);
        return this;
    }

    public ApiRequestBuilder withQueryParam(String name, String value) {
        queryParams.put(name, value);
        return this;
    }

    public ApiRequestBuilder withQueryParams(Map<String, String> params) {
        queryParams.putAll(params);
        return this;
    }

    public ApiRequestBuilder withPathParam(String name, String value) {
        pathParams.put(name, value);
        return this;
    }

    public ApiRequestBuilder withBody(Object body) {
        this.body = body;
        return this;
    }

    public ApiRequestBuilder expectStatus(int status) {
        this.expectedStatus = status;
        return this;
    }

    public ApiRequestBuilder withRetries(int count) {
        this.retries = count;
        return this;
    }

    // ------------------------------------------------------------------ //
    //  HTTP verbs                                                          //
    // ------------------------------------------------------------------ //

    public ApiRequestBuilder get(String path) {
        lastResponse = executeWithRetry(() -> buildSpec().get(path));
        log.debug("GET {} → {}", path, lastResponse.getStatusCode());
        validateStatus();
        return this;
    }

    public ApiRequestBuilder post(String path) {
        lastResponse = executeWithRetry(() -> buildSpec().post(path));
        log.debug("POST {} → {}", path, lastResponse.getStatusCode());
        validateStatus();
        return this;
    }

    public ApiRequestBuilder put(String path) {
        lastResponse = executeWithRetry(() -> buildSpec().put(path));
        log.debug("PUT {} → {}", path, lastResponse.getStatusCode());
        validateStatus();
        return this;
    }

    public ApiRequestBuilder patch(String path) {
        lastResponse = executeWithRetry(() -> buildSpec().patch(path));
        log.debug("PATCH {} → {}", path, lastResponse.getStatusCode());
        validateStatus();
        return this;
    }

    public ApiRequestBuilder delete(String path) {
        lastResponse = executeWithRetry(() -> buildSpec().delete(path));
        log.debug("DELETE {} → {}", path, lastResponse.getStatusCode());
        validateStatus();
        return this;
    }

    // ------------------------------------------------------------------ //
    //  Assertions + extraction                                             //
    // ------------------------------------------------------------------ //

    public ApiRequestBuilder assertStatus(int expected) {
        RestAssuredBase.assertStatusCode(lastResponse, expected);
        return this;
    }

    public ApiRequestBuilder assertJsonSchema(String schemaClasspath) {
        RestAssuredBase.assertJsonSchema(lastResponse, schemaClasspath);
        return this;
    }

    public <T> T extractAs(Class<T> type) {
        return lastResponse.as(type);
    }

    public String extractField(String jsonPath) {
        return lastResponse.jsonPath().getString(jsonPath);
    }

    public Response getResponse() { return lastResponse; }

    // ------------------------------------------------------------------ //
    //  Internal                                                            //
    // ------------------------------------------------------------------ //

    private RequestSpecification buildSpec() {
        RequestSpecification s = spec.headers(headers).queryParams(queryParams).pathParams(pathParams);
        if (body != null) s = s.body(body);
        return s;
    }

    private void validateStatus() {
        if (expectedStatus > 0) {
            RestAssuredBase.assertStatusCode(lastResponse, expectedStatus);
        }
    }

    private Response executeWithRetry(java.util.function.Supplier<Response> action) {
        int attempt = 0;
        while (true) {
            attempt++;
            Response r = action.get();
            if (r.getStatusCode() < 500 || attempt > retries) return r;
            log.warn("Retrying request (attempt {}/{}) due to {} response", attempt, retries, r.getStatusCode());
            try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
        }
    }

    @FunctionalInterface
    private interface RequestAction { Response execute(); }
}

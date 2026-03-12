package com.framework.database;

import com.framework.config.FrameworkConfig;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.*;

/**
 * Data Lake manager supporting:
 *  - AWS Athena queries (SQL over S3 Parquet / Delta files)
 *  - S3 object listing and metadata inspection
 *  - Row count and schema validation assertions
 *  - Partition existence checks
 *
 * Configuration:
 *   aws.region, aws.access.key, aws.secret.key
 *   aws.athena.database, aws.athena.output.location
 *   aws.s3.bucket
 */
@Slf4j
public class DataLakeManager {

    private final AthenaClient athena;
    private final S3Client s3;
    private final String database;
    private final String outputLocation;
    private final String bucket;

    public DataLakeManager() {
        Region region = Region.of(FrameworkConfig.get("aws.region", "us-east-1"));
        String accessKey = FrameworkConfig.get("aws.access.key", "");
        String secretKey = FrameworkConfig.get("aws.secret.key", "");

        StaticCredentialsProvider creds = accessKey.isBlank()
                ? null
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

        var athenaBuilder = AthenaClient.builder().region(region);
        var s3Builder = S3Client.builder().region(region);
        if (creds != null) {
            athenaBuilder.credentialsProvider(creds);
            s3Builder.credentialsProvider(creds);
        }

        this.athena = athenaBuilder.build();
        this.s3 = s3Builder.build();
        this.database = FrameworkConfig.get("aws.athena.database", "default");
        this.outputLocation = FrameworkConfig.get("aws.athena.output.location", "s3://query-results/");
        this.bucket = FrameworkConfig.get("aws.s3.bucket", "");

        log.info("DataLakeManager initialised [region={}, database={}]", region, database);
    }

    // ------------------------------------------------------------------ //
    //  Athena Query Execution                                              //
    // ------------------------------------------------------------------ //

    /**
     * Execute an Athena SQL query and return results as list of row maps.
     * Blocks until the query completes (up to maxWaitSeconds).
     */
    public List<Map<String, String>> query(String sql) {
        return query(sql, 120);
    }

    public List<Map<String, String>> query(String sql, int maxWaitSeconds) {
        log.info("Athena SQL: {}", sql);
        String queryExecutionId = startQuery(sql);
        waitForCompletion(queryExecutionId, maxWaitSeconds);
        return fetchResults(queryExecutionId);
    }

    /**
     * Count rows in a table or query result.
     */
    public long count(String tableName) {
        return count(tableName, null);
    }

    public long count(String tableName, String whereClause) {
        String sql = "SELECT COUNT(*) as cnt FROM " + database + "." + tableName
                + (whereClause != null ? " WHERE " + whereClause : "");
        List<Map<String, String>> rows = query(sql);
        return rows.isEmpty() ? 0 : Long.parseLong(rows.get(0).getOrDefault("cnt", "0"));
    }

    /**
     * Get a distinct list of values for a column (e.g. for partition validation).
     */
    public List<String> distinctValues(String table, String column) {
        String sql = "SELECT DISTINCT " + column + " FROM " + database + "." + table;
        List<Map<String, String>> rows = query(sql);
        return rows.stream().map(r -> r.get(column)).filter(Objects::nonNull).toList();
    }

    /**
     * Validate schema of a table – checks that all expected columns exist.
     */
    public boolean validateSchema(String table, List<String> expectedColumns) {
        String sql = "DESCRIBE " + database + "." + table;
        List<Map<String, String>> rows = query(sql);
        Set<String> actualCols = new HashSet<>();
        rows.forEach(r -> {
            String col = r.get("col_name");
            if (col != null && !col.isBlank() && !col.startsWith("#")) actualCols.add(col.toLowerCase());
        });
        boolean valid = true;
        for (String expected : expectedColumns) {
            if (!actualCols.contains(expected.toLowerCase())) {
                log.error("Schema mismatch: column '{}' not found in table '{}'", expected, table);
                valid = false;
            }
        }
        if (valid) log.info("Schema validated for table '{}' – all {} columns present", table, expectedColumns.size());
        return valid;
    }

    // ------------------------------------------------------------------ //
    //  Assertions                                                          //
    // ------------------------------------------------------------------ //

    public void assertRowCountGreaterThan(String table, long minRows) {
        long actual = count(table);
        if (actual <= minRows) {
            throw new AssertionError(String.format(
                    "Expected > %d rows in '%s' but found %d", minRows, table, actual));
        }
        log.info("Row count assertion passed: {} has {} rows (> {})", table, actual, minRows);
    }

    public void assertRowCountEquals(String table, String where, long expected) {
        long actual = count(table, where);
        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Expected %d rows in '%s' WHERE %s but found %d", expected, table, where, actual));
        }
    }

    public void assertPartitionExists(String table, String partitionColumn, String partitionValue) {
        List<String> values = distinctValues(table, partitionColumn);
        if (!values.contains(partitionValue)) {
            throw new AssertionError(String.format(
                    "Partition '%s=%s' not found in table '%s'", partitionColumn, partitionValue, table));
        }
        log.info("Partition check passed: {}={} exists in {}", partitionColumn, partitionValue, table);
    }

    public void assertSchemaValid(String table, List<String> columns) {
        if (!validateSchema(table, columns)) {
            throw new AssertionError("Schema validation failed for table: " + table);
        }
    }

    // ------------------------------------------------------------------ //
    //  S3 Operations                                                       //
    // ------------------------------------------------------------------ //

    /**
     * List objects in the configured S3 bucket under a given prefix.
     */
    public List<S3Object> listObjects(String prefix) {
        ListObjectsV2Request req = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
        ListObjectsV2Response resp = s3.listObjectsV2(req);
        log.info("S3 list: s3://{}/{} → {} objects", bucket, prefix, resp.contents().size());
        return resp.contents();
    }

    public boolean s3ObjectExists(String key) {
        return !listObjects(key).isEmpty();
    }

    public long s3ObjectCount(String prefix) {
        return listObjects(prefix).size();
    }

    // ------------------------------------------------------------------ //
    //  Internal Athena helpers                                             //
    // ------------------------------------------------------------------ //

    private String startQuery(String sql) {
        StartQueryExecutionRequest req = StartQueryExecutionRequest.builder()
                .queryString(sql)
                .queryExecutionContext(QueryExecutionContext.builder().database(database).build())
                .resultConfiguration(ResultConfiguration.builder().outputLocation(outputLocation).build())
                .build();
        return athena.startQueryExecution(req).queryExecutionId();
    }

    private void waitForCompletion(String queryId, int maxWaitSeconds) {
        long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            GetQueryExecutionResponse resp = athena.getQueryExecution(
                    GetQueryExecutionRequest.builder().queryExecutionId(queryId).build());
            QueryExecutionState state = resp.queryExecution().status().state();
            switch (state) {
                case SUCCEEDED -> { return; }
                case FAILED, CANCELLED -> throw new RuntimeException(
                        "Athena query " + state + ": " +
                        resp.queryExecution().status().stateChangeReason());
                default -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw new RuntimeException("Athena query timed out after " + maxWaitSeconds + "s");
    }

    private List<Map<String, String>> fetchResults(String queryId) {
        GetQueryResultsResponse resp = athena.getQueryResults(
                GetQueryResultsRequest.builder().queryExecutionId(queryId).build());
        List<Map<String, String>> rows = new ArrayList<>();
        List<ColumnInfo> columns = resp.resultSet().resultSetMetadata().columnInfo();
        boolean firstRow = true;
        for (Row row : resp.resultSet().rows()) {
            if (firstRow) { firstRow = false; continue; }
            Map<String, String> rowMap = new LinkedHashMap<>();
            List<Datum> data = row.data();
            for (int i = 0; i < columns.size(); i++) {
                rowMap.put(columns.get(i).name(), i < data.size() ? data.get(i).varCharValue() : null);
            }
            rows.add(rowMap);
        }
        log.debug("Athena returned {} rows", rows.size());
        return rows;
    }
}

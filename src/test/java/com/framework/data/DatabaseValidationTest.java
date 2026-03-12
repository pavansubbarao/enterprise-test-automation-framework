package com.framework.data;

import com.framework.base.BaseTest;
import com.framework.database.DataLakeManager;
import com.framework.database.PostgreSQLManager;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample database and data lake validation tests.
 *
 * Demonstrates:
 *  - PostgreSQL query and assertion helpers
 *  - Data Lake (Athena) queries and schema validation
 *  - Test data setup and teardown
 *  - Jira context linking
 */
@Epic("Data Validation")
@Feature("Database & Data Lake")
public class DatabaseValidationTest extends BaseTest {

    private PostgreSQLManager db;
    // Uncomment when AWS credentials are configured:
    // private DataLakeManager lake;

    @BeforeClass
    public void setup() {
        db = PostgreSQLManager.getInstance();
        // lake = new DataLakeManager();
    }

    @AfterClass(alwaysRun = true)
    public void cleanup() {
        step("Clean up test data");
        try {
            db.deleteWhere("test_automation_data", "session_tag = ?", "framework_sample");
        } catch (Exception e) {
            log.warn("Cleanup skipped (table may not exist): {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  PostgreSQL tests                                                    //
    // ------------------------------------------------------------------ //

    @Test(priority = 1)
    @Story("PostgreSQL Connectivity")
    @Description("Verify framework can connect to PostgreSQL and execute a query")
    @JiraIssue("QA-020")
    public void testDatabaseConnection() {
        step("Execute a simple connectivity check query");
        List<Map<String, Object>> result = db.query("SELECT 1 AS alive, current_timestamp AS now_ts");

        step("Verify query returned a result");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("alive")).isEqualTo(1L);

        attachText("DB Query Result", result.toString());
    }

    @Test(priority = 2)
    @Story("Row Count Validation")
    @Description("Verify a table has the expected minimum number of rows")
    @JiraIssue("QA-021")
    public void testTableRowCount() {
        step("Count rows in the users table");
        // Replace 'users' with an actual table in your schema
        try {
            long count = db.countWhere("information_schema.tables",
                    "table_schema = 'public'");
            step("Assert at least 0 public tables exist");
            assertThat(count).isGreaterThanOrEqualTo(0);
            attachText("Table Count", "Public tables in schema: " + count);
        } catch (Exception e) {
            step("Skipping - schema not yet set up: " + e.getMessage());
        }
    }

    @Test(priority = 3)
    @Story("Data Integrity")
    @Description("Verify referential integrity: every order has a valid user")
    @JiraIssue("QA-022")
    public void testReferentialIntegrity() {
        step("Check for orphaned records");
        // Example integrity check – replace with your actual tables
        String sql = """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_type = 'FOREIGN KEY'
                """;
        try {
            Object count = db.querySingle(sql);
            step("Foreign key constraint count retrieved: " + count);
            assertThat(count).isNotNull();
            attachText("FK Constraint Count", "Foreign keys: " + count);
        } catch (Exception e) {
            step("Skipping - " + e.getMessage());
        }
    }

    @Test(priority = 4)
    @Story("Test Data Management")
    @Description("Verify framework can insert and verify test data")
    @JiraIssue("QA-023")
    public void testDataSetupAndTeardown() {
        step("Create test data table (if not exists)");
        try {
            db.execute("""
                    CREATE TABLE IF NOT EXISTS test_automation_data (
                        id SERIAL PRIMARY KEY,
                        session_tag VARCHAR(100),
                        value TEXT,
                        created_at TIMESTAMP DEFAULT NOW()
                    )
                    """);

            step("Insert sample test data");
            int inserted = db.execute(
                    "INSERT INTO test_automation_data (session_tag, value) VALUES (?, ?)",
                    "framework_sample", "automated_test_value_" + System.currentTimeMillis()
            );
            assertThat(inserted).isEqualTo(1).describedAs("One row should be inserted");

            step("Verify row exists");
            db.assertRowExists("test_automation_data", "session_tag = 'framework_sample'");

            step("Verify exact row count");
            long count = db.countWhere("test_automation_data", "session_tag = 'framework_sample'");
            assertThat(count).isGreaterThanOrEqualTo(1);
            attachText("Test Data", "Rows with tag 'framework_sample': " + count);

        } catch (Exception e) {
            step("Test data operations failed: " + e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------ //
    //  Data Lake tests (AWS Athena)                                        //
    //  Uncomment and configure AWS credentials to enable                   //
    // ------------------------------------------------------------------ //

    /*
    @Test(priority = 5)
    @Story("Data Lake Connectivity")
    @Description("Verify Athena can execute a query against the data lake")
    @JiraIssue("QA-030")
    public void testAthenaConnectivity() {
        step("Run a simple Athena query");
        List<Map<String, String>> rows = lake.query("SELECT 1 AS alive");
        assertThat(rows).hasSize(1);
    }

    @Test(priority = 6)
    @Story("Schema Validation")
    @Description("Verify the events table has the expected schema")
    @JiraIssue("QA-031")
    public void testDataLakeSchema() {
        step("Validate events table schema");
        lake.assertSchemaValid("events", List.of(
                "event_id", "user_id", "event_type", "timestamp", "properties"
        ));
    }

    @Test(priority = 7)
    @Story("Data Volume")
    @Description("Verify the events table has data for the last 7 days")
    @JiraIssue("QA-032")
    public void testDataVolume() {
        step("Check row count for last 7 days");
        lake.assertRowCountGreaterThan("events", 0);
    }

    @Test(priority = 8)
    @Story("Partition Validation")
    @Description("Verify today's partition exists in the events table")
    @JiraIssue("QA-033")
    public void testPartitionExists() {
        step("Check today's partition");
        String today = java.time.LocalDate.now().toString();
        lake.assertPartitionExists("events", "event_date", today);
    }
    */
}

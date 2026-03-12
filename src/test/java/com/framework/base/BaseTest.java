package com.framework.base;

import com.framework.config.FrameworkConfig;
import com.framework.jira.JiraTestMapper;
import com.framework.mcp.MCPServer;
import com.framework.reporting.TestReporter;
import com.framework.ui.DriverFactory;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.lang.reflect.Method;

/**
 * Abstract base class for all tests.
 *
 * Handles:
 *  - WebDriver lifecycle (per-method or per-class via @Listeners)
 *  - Jira context injection
 *  - MCP server lifecycle
 *  - Allure reporting labels and attachments
 *  - Screenshot capture on UI test failure
 *  - Configuration access
 *
 * Subclass and annotate your test methods with @Test.
 * Optionally annotate with @JiraIssue("QA-123") to get Jira context injection.
 */
@Slf4j
public abstract class BaseTest {

    protected static final FrameworkConfig config = FrameworkConfig.getInstance();
    protected static final JiraTestMapper jiraMapper = new JiraTestMapper();
    protected WebDriver driver;

    // ------------------------------------------------------------------ //
    //  Suite Setup / Teardown                                              //
    // ------------------------------------------------------------------ //

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        log.info("===== TEST SUITE STARTING =====");
        log.info("Environment: {}", FrameworkConfig.get("env", "dev"));
        log.info("Base URL:    {}", FrameworkConfig.get("base.url"));
        log.info("Jira URL:    {}", FrameworkConfig.get("jira.base.url"));
        MCPServer.start();
    }

    @AfterSuite(alwaysRun = true)
    public void afterSuite() {
        MCPServer.stop();
        log.info("===== TEST SUITE COMPLETED =====");
    }

    // ------------------------------------------------------------------ //
    //  Test Setup / Teardown                                               //
    // ------------------------------------------------------------------ //

    @BeforeMethod(alwaysRun = true)
    public void beforeMethod(Method method) {
        log.info("---------- START: {} ----------", method.getName());
        Allure.label("testMethod", method.getName());
        Allure.label("testClass", getClass().getSimpleName());
        Allure.label("environment", FrameworkConfig.get("env", "dev"));

        // Attach Jira context if @JiraIssue annotation is present
        JiraIssue jiraAnnotation = method.getAnnotation(JiraIssue.class);
        if (jiraAnnotation != null) {
            for (String key : jiraAnnotation.value()) {
                try {
                    var ctx = jiraMapper.getContext(key);
                    TestReporter.attachText("Jira Context: " + key, ctx.getFullContext());
                    TestReporter.linkJira(key);
                    Allure.label("jiraIssue", key);
                    log.info("Jira context loaded for: {}", key);
                } catch (Exception e) {
                    log.warn("Could not load Jira context for {}: {}", key, e.getMessage());
                }
            }
        }
    }

    @AfterMethod(alwaysRun = true)
    public void afterMethod(ITestResult result) {
        // Screenshot on failure for UI tests
        if (driver != null && result.getStatus() == ITestResult.FAILURE) {
            if (FrameworkConfig.getBoolean("screenshot.on.failure", true)) {
                com.framework.utils.ScreenshotUtil.capture(driver, "FAILURE_" + result.getName());
            }
        }

        // Report to Allure + Jira
        switch (result.getStatus()) {
            case ITestResult.SUCCESS -> TestReporter.reportSuccess(result);
            case ITestResult.FAILURE -> TestReporter.reportFailure(result);
            case ITestResult.SKIP -> TestReporter.reportSkipped(result);
        }

        log.info("---------- END: {} [{}] ({} ms) ----------",
                result.getName(),
                statusLabel(result.getStatus()),
                result.getEndMillis() - result.getStartMillis());
    }

    // ------------------------------------------------------------------ //
    //  Driver helpers                                                      //
    // ------------------------------------------------------------------ //

    protected WebDriver initDriver() {
        driver = DriverFactory.getDriver();
        return driver;
    }

    protected void quitDriver() {
        DriverFactory.quit();
        driver = null;
    }

    // ------------------------------------------------------------------ //
    //  Utilities                                                           //
    // ------------------------------------------------------------------ //

    protected void step(String description) {
        TestReporter.step(description);
    }

    protected void attachText(String name, String content) {
        TestReporter.attachText(name, content);
    }

    private String statusLabel(int status) {
        return switch (status) {
            case ITestResult.SUCCESS -> "PASSED";
            case ITestResult.FAILURE -> "FAILED";
            case ITestResult.SKIP -> "SKIPPED";
            default -> "UNKNOWN";
        };
    }

    // ------------------------------------------------------------------ //
    //  @JiraIssue annotation                                               //
    // ------------------------------------------------------------------ //

    /**
     * Annotate a test method to auto-inject Jira context into the Allure report.
     * Supports multiple issue keys.
     *
     * Example:
     *   @JiraIssue({"QA-123", "QA-456"})
     *   @Test
     *   public void testLogin() { ... }
     */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    protected @interface JiraIssue {
        String[] value();
    }
}

package com.framework.ui;

import com.framework.base.BaseTest;
import com.framework.config.FrameworkConfig;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample UI tests demonstrating the framework with Selenium and auto-healing.
 *
 * Replace page URLs and locators with your actual application.
 * Auto-healing is active when healenium.enabled=true in config.properties.
 */
@Epic("UI Testing")
@Feature("Login & Navigation")
public class SampleUITest extends BaseTest {

    private final String baseUrl = FrameworkConfig.get("base.url", "https://your-app.com");

    @BeforeMethod(alwaysRun = true)
    public void openBrowser() {
        initDriver();
        driver.get(baseUrl);
    }

    @AfterMethod(alwaysRun = true)
    public void closeBrowser() {
        quitDriver();
    }

    // ------------------------------------------------------------------ //
    //  Login Tests                                                         //
    // ------------------------------------------------------------------ //

    @Test(priority = 1)
    @Story("Login")
    @Description("Verify valid credentials allow login to the application")
    @JiraIssue("QA-010")
    public void testSuccessfulLogin() {
        LoginPage loginPage = new LoginPage(driver);

        step("Navigate to login page");
        loginPage.navigateTo(baseUrl + "/login");

        step("Enter valid credentials");
        loginPage.enterUsername("testuser@example.com");
        loginPage.enterPassword("SecurePass123");
        loginPage.clickLoginButton();

        step("Verify redirect to dashboard");
        loginPage.waitForUrlContains("/dashboard");
        assertThat(driver.getCurrentUrl())
                .contains("/dashboard")
                .describedAs("Should redirect to dashboard after login");

        step("Capture success screenshot");
        captureScreenshot(driver, "LoginSuccess");
    }

    @Test(priority = 2)
    @Story("Login")
    @Description("Verify invalid credentials show an error message")
    @JiraIssue("QA-011")
    public void testInvalidLogin() {
        LoginPage loginPage = new LoginPage(driver);

        step("Navigate to login page");
        loginPage.navigateTo(baseUrl + "/login");

        step("Enter invalid credentials");
        loginPage.enterUsername("wrong@example.com");
        loginPage.enterPassword("WrongPassword");
        loginPage.clickLoginButton();

        step("Verify error message is displayed");
        assertThat(loginPage.isErrorMessageDisplayed())
                .isTrue()
                .describedAs("Error message should be visible for invalid login");

        step("Verify URL stays on login page");
        assertThat(driver.getCurrentUrl())
                .contains("/login")
                .describedAs("Should stay on login page");
    }

    @Test(priority = 3)
    @Story("Navigation")
    @Description("Verify main navigation links are accessible after login")
    @JiraIssue("QA-012")
    public void testMainNavigation() {
        // Simulate logged-in state by navigating directly
        // (In real tests, call login first or set auth cookie)
        step("Navigate to dashboard");
        driver.get(baseUrl + "/dashboard");

        DashboardPage dashboard = new DashboardPage(driver);
        step("Verify dashboard loads");
        assertThat(dashboard.isLoaded())
                .isTrue()
                .describedAs("Dashboard page should be loaded");

        step("Verify navigation elements are visible");
        assertThat(dashboard.isNavigationVisible())
                .isTrue()
                .describedAs("Navigation menu should be present");
    }

    // ------------------------------------------------------------------ //
    //  Page Objects (inner classes for demo – move to separate files)      //
    // ------------------------------------------------------------------ //

    static class LoginPage extends BasePage {

        // Primary locators + fallbacks for auto-healing
        private static final By USERNAME_FIELD = By.id("username");
        private static final By USERNAME_FALLBACK = By.cssSelector("input[type='email']");
        private static final By PASSWORD_FIELD = By.id("password");
        private static final By PASSWORD_FALLBACK = By.cssSelector("input[type='password']");
        private static final By LOGIN_BUTTON = By.id("login-btn");
        private static final By LOGIN_BUTTON_CSS = By.cssSelector("button[type='submit']");
        private static final By ERROR_MESSAGE = By.cssSelector(".error-message,.alert-danger,[data-testid='error']");

        LoginPage(WebDriver driver) { super(driver); }

        public void enterUsername(String username) {
            com.framework.ui.healing.HealingElementFinder.find(
                    driver, "username-field", USERNAME_FIELD, USERNAME_FALLBACK)
                    .clear();
            com.framework.ui.healing.HealingElementFinder.find(
                    driver, "username-field", USERNAME_FIELD, USERNAME_FALLBACK)
                    .sendKeys(username);
        }

        public void enterPassword(String password) {
            com.framework.ui.healing.HealingElementFinder.find(
                    driver, "password-field", PASSWORD_FIELD, PASSWORD_FALLBACK)
                    .sendKeys(password);
        }

        public void clickLoginButton() {
            com.framework.ui.healing.HealingElementFinder.find(
                    driver, "login-button", LOGIN_BUTTON, LOGIN_BUTTON_CSS)
                    .click();
        }

        public boolean isErrorMessageDisplayed() {
            return com.framework.ui.healing.HealingElementFinder.isPresent(driver, ERROR_MESSAGE);
        }
    }

    static class DashboardPage extends BasePage {
        private static final By NAVIGATION = By.cssSelector("nav,#main-nav,.navbar");
        private static final By PAGE_INDICATOR = By.cssSelector("[data-page='dashboard'],h1,.dashboard-title");

        DashboardPage(WebDriver driver) { super(driver); }

        public boolean isLoaded() {
            return isPresent(PAGE_INDICATOR);
        }

        public boolean isNavigationVisible() {
            return isVisible(NAVIGATION);
        }
    }

    private void captureScreenshot(WebDriver driver, String name) {
        com.framework.utils.ScreenshotUtil.capture(driver, name);
    }
}

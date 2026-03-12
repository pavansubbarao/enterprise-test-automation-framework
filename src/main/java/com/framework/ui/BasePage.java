package com.framework.ui;

import com.framework.config.FrameworkConfig;
import com.framework.utils.ScreenshotUtil;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Base class for all Page Objects.
 *
 * Provides:
 *  - Smart waits (explicit + fluent)
 *  - Safe interactions (click, type, select)
 *  - Auto-healing via DriverFactory (Healenium wraps the driver)
 *  - Screenshot on failure
 *  - JavaScript executor helpers
 *  - Scroll and hover utilities
 */
@Slf4j
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;
    protected final JavascriptExecutor js;
    protected final Actions actions;
    protected final int timeout;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.timeout = FrameworkConfig.getInt("browser.timeout", 30);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(timeout));
        this.js = (JavascriptExecutor) driver;
        this.actions = new Actions(driver);
        PageFactory.initElements(driver, this);
    }

    // ------------------------------------------------------------------ //
    //  Navigation                                                          //
    // ------------------------------------------------------------------ //

    public void navigateTo(String url) {
        log.debug("Navigating to: {}", url);
        driver.get(url);
        waitForPageLoad();
    }

    public String getCurrentUrl() { return driver.getCurrentUrl(); }
    public String getTitle() { return driver.getTitle(); }

    // ------------------------------------------------------------------ //
    //  Element retrieval with smart wait                                   //
    // ------------------------------------------------------------------ //

    public WebElement waitForElement(By locator) {
        log.debug("Waiting for element: {}", locator);
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement waitForClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    public List<WebElement> waitForElements(By locator) {
        return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
    }

    public boolean isPresent(By locator) {
        try {
            driver.findElement(locator);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public boolean isVisible(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ //
    //  Interactions                                                        //
    // ------------------------------------------------------------------ //

    public void click(By locator) {
        log.debug("Clicking: {}", locator);
        try {
            waitForClickable(locator).click();
        } catch (ElementClickInterceptedException e) {
            log.warn("Standard click failed, trying JS click on: {}", locator);
            jsClick(driver.findElement(locator));
        }
    }

    public void type(By locator, String text) {
        log.debug("Typing '{}' into: {}", text, locator);
        WebElement el = waitForElement(locator);
        el.clear();
        el.sendKeys(text);
    }

    public void selectByVisibleText(By locator, String text) {
        Select select = new Select(waitForElement(locator));
        select.selectByVisibleText(text);
    }

    public void selectByValue(By locator, String value) {
        Select select = new Select(waitForElement(locator));
        select.selectByValue(value);
    }

    public void hover(By locator) {
        actions.moveToElement(waitForElement(locator)).perform();
    }

    public void dragAndDrop(By source, By target) {
        actions.dragAndDrop(waitForElement(source), waitForElement(target)).perform();
    }

    public void pressKey(By locator, Keys key) {
        waitForElement(locator).sendKeys(key);
    }

    // ------------------------------------------------------------------ //
    //  Text extraction                                                     //
    // ------------------------------------------------------------------ //

    public String getText(By locator) {
        return waitForElement(locator).getText().trim();
    }

    public String getAttribute(By locator, String attribute) {
        return waitForElement(locator).getAttribute(attribute);
    }

    // ------------------------------------------------------------------ //
    //  Wait helpers                                                        //
    // ------------------------------------------------------------------ //

    public void waitForPageLoad() {
        wait.until((Function<WebDriver, Boolean>) driver ->
                js.executeScript("return document.readyState").equals("complete"));
    }

    public void waitForTextInElement(By locator, String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, text));
    }

    public void waitForUrlContains(String fragment) {
        wait.until(ExpectedConditions.urlContains(fragment));
    }

    public void waitForInvisibility(By locator) {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public <T> T waitUntil(Function<WebDriver, T> condition, int seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(condition);
    }

    // ------------------------------------------------------------------ //
    //  JavaScript utilities                                                //
    // ------------------------------------------------------------------ //

    public void jsClick(WebElement element) {
        js.executeScript("arguments[0].click();", element);
    }

    public void jsType(WebElement element, String text) {
        js.executeScript("arguments[0].value=arguments[1];", element, text);
    }

    public void scrollToElement(By locator) {
        js.executeScript("arguments[0].scrollIntoView({behavior:'smooth',block:'center'});",
                driver.findElement(locator));
    }

    public void scrollToTop() {
        js.executeScript("window.scrollTo(0, 0);");
    }

    public void scrollToBottom() {
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }

    // ------------------------------------------------------------------ //
    //  Screenshot                                                          //
    // ------------------------------------------------------------------ //

    public String captureScreenshot(String name) {
        return ScreenshotUtil.capture(driver, name);
    }

    // ------------------------------------------------------------------ //
    //  Frame & window                                                      //
    // ------------------------------------------------------------------ //

    public void switchToFrame(By locator) {
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(locator));
    }

    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
    }

    public void switchToNewWindow() {
        String current = driver.getWindowHandle();
        for (String handle : driver.getWindowHandles()) {
            if (!handle.equals(current)) {
                driver.switchTo().window(handle);
                break;
            }
        }
    }

    public void acceptAlert() {
        wait.until(ExpectedConditions.alertIsPresent()).accept();
    }

    public String getAlertText() {
        return wait.until(ExpectedConditions.alertIsPresent()).getText();
    }
}

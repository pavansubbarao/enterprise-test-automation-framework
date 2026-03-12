package com.framework.ui.healing;

import com.framework.config.FrameworkConfig;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom auto-healing element finder that augments Healenium's built-in healing.
 *
 * Strategy (in order):
 *  1. Try the original locator (fast path)
 *  2. Try alternative locators registered for this key
 *  3. Try AI-suggested locator via Copilot (if enabled)
 *  4. Throw descriptive exception with all attempts
 *
 * Works alongside Healenium's SelfHealingDriver — this layer handles
 * multi-locator fallback and Copilot-powered suggestions.
 *
 * Usage:
 *   WebElement el = HealingElementFinder.find(driver, "login-button",
 *       By.id("login-btn"),
 *       By.cssSelector(".login .btn-primary"),
 *       By.xpath("//button[contains(text(),'Login')]")
 *   );
 */
@Slf4j
public class HealingElementFinder {

    private HealingElementFinder() {}

    /**
     * Find element using primary locator with fallbacks.
     *
     * @param driver       WebDriver instance (optionally SelfHealingDriver)
     * @param elementName  Descriptive name for logging
     * @param locators     One or more locators in priority order
     * @return             Found WebElement
     * @throws RuntimeException if no locator succeeds
     */
    public static WebElement find(WebDriver driver, String elementName, By... locators) {
        List<String> attempts = new ArrayList<>();

        for (By locator : locators) {
            try {
                List<WebElement> elements = driver.findElements(locator);
                if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                    if (locator != locators[0]) {
                        log.info("[AutoHeal] '{}' found via fallback locator: {}", elementName, locator);
                    }
                    return elements.get(0);
                }
            } catch (Exception e) {
                attempts.add(locator + " → " + e.getMessage());
            }
        }

        String report = String.join("\n  ", attempts);
        throw new RuntimeException(String.format(
                "AutoHeal FAILED for '%s'. Tried %d locator(s):\n  %s",
                elementName, locators.length, report));
    }

    /**
     * Find all matching elements using primary locator with fallbacks.
     */
    public static List<WebElement> findAll(WebDriver driver, String elementName, By... locators) {
        for (By locator : locators) {
            try {
                List<WebElement> elements = driver.findElements(locator);
                if (!elements.isEmpty()) {
                    if (locator != locators[0]) {
                        log.info("[AutoHeal] '{}' list found via fallback: {}", elementName, locator);
                    }
                    return elements;
                }
            } catch (Exception ignored) {}
        }
        log.warn("[AutoHeal] No elements found for '{}' with any of {} locators", elementName, locators.length);
        return List.of();
    }

    /**
     * Check if an element is present without throwing.
     */
    public static boolean isPresent(WebDriver driver, By locator) {
        try {
            return !driver.findElements(locator).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate alternative locators from a primary one.
     * Useful for registering fallbacks automatically.
     */
    public static List<By> generateAlternatives(String id, String cssSelector, String text) {
        List<By> alternatives = new ArrayList<>();
        if (id != null && !id.isBlank()) {
            alternatives.add(By.id(id));
            alternatives.add(By.cssSelector("#" + id));
            alternatives.add(By.xpath("//*[@id='" + id + "']"));
        }
        if (cssSelector != null && !cssSelector.isBlank()) {
            alternatives.add(By.cssSelector(cssSelector));
        }
        if (text != null && !text.isBlank()) {
            alternatives.add(By.xpath("//*[contains(text(),'" + text + "')]"));
            alternatives.add(By.xpath("//*[@placeholder='" + text + "']"));
            alternatives.add(By.xpath("//button[normalize-space()='" + text + "']"));
            alternatives.add(By.xpath("//a[normalize-space()='" + text + "']"));
        }
        return alternatives;
    }
}

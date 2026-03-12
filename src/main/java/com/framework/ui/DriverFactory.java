package com.framework.ui;

import com.epam.healenium.SelfHealingDriver;
import com.framework.config.FrameworkConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.URL;
import java.time.Duration;

/**
 * Thread-safe WebDriver factory with auto-healing via Healenium.
 *
 * Usage:
 *   WebDriver driver = DriverFactory.getDriver();
 *   // tests...
 *   DriverFactory.quit();
 *
 * Supports: chrome, firefox, edge, remote (Selenium Grid).
 * Enable headless via config: browser.headless=true
 * Enable auto-healing via config: healenium.enabled=true
 */
@Slf4j
public class DriverFactory {

    private static final ThreadLocal<WebDriver> driverHolder = new ThreadLocal<>();

    private DriverFactory() {}

    /**
     * Get (or create) the WebDriver for the current thread.
     */
    public static WebDriver getDriver() {
        if (driverHolder.get() == null) {
            driverHolder.set(createDriver());
        }
        return driverHolder.get();
    }

    /**
     * Quit and remove the driver for the current thread.
     */
    public static void quit() {
        WebDriver driver = driverHolder.get();
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
            driverHolder.remove();
            log.info("WebDriver closed for thread: {}", Thread.currentThread().getName());
        }
    }

    // ------------------------------------------------------------------ //

    private static WebDriver createDriver() {
        String browser = FrameworkConfig.get("browser", "chrome").toLowerCase();
        boolean headless = FrameworkConfig.getBoolean("headless", false);
        boolean healingEnabled = FrameworkConfig.getBoolean("healenium.enabled", true);
        boolean maximize = FrameworkConfig.getBoolean("browser.window.maximize", true);
        int implicitWait = FrameworkConfig.getInt("browser.implicit.wait", 5);
        String gridUrl = FrameworkConfig.get("selenium.grid.url", "");

        WebDriver raw;

        if (gridUrl != null && !gridUrl.isBlank()) {
            raw = createRemoteDriver(browser, headless, gridUrl);
        } else {
            raw = switch (browser) {
                case "firefox" -> createFirefox(headless);
                case "edge" -> createEdge(headless);
                default -> createChrome(headless);
            };
        }

        if (maximize) raw.manage().window().maximize();
        raw.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitWait));
        raw.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(
                FrameworkConfig.getInt("browser.timeout", 30)));

        WebDriver driver;
        if (healingEnabled) {
            driver = SelfHealingDriver.create(raw);
            log.info("Auto-healing driver (Healenium) activated for thread: {}",
                    Thread.currentThread().getName());
        } else {
            driver = raw;
        }

        log.info("WebDriver created [browser={}, headless={}, healing={}]",
                browser, headless, healingEnabled);
        return driver;
    }

    private static ChromeDriver createChrome(boolean headless) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions opts = new ChromeOptions();
        if (headless) {
            opts.addArguments("--headless=new");
        }
        opts.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--disable-notifications",
                "--disable-extensions",
                "--remote-allow-origins=*"
        );
        opts.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        return new ChromeDriver(opts);
    }

    private static FirefoxDriver createFirefox(boolean headless) {
        WebDriverManager.firefoxdriver().setup();
        FirefoxOptions opts = new FirefoxOptions();
        if (headless) opts.addArguments("--headless");
        return new FirefoxDriver(opts);
    }

    private static EdgeDriver createEdge(boolean headless) {
        WebDriverManager.edgedriver().setup();
        EdgeOptions opts = new EdgeOptions();
        if (headless) opts.addArguments("--headless=new");
        return new EdgeDriver(opts);
    }

    private static RemoteWebDriver createRemoteDriver(String browser, boolean headless, String gridUrl) {
        try {
            DesiredCapabilities caps = new DesiredCapabilities();
            caps.setBrowserName(browser);
            return new RemoteWebDriver(new URL(gridUrl), caps);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Selenium Grid at " + gridUrl, e);
        }
    }
}

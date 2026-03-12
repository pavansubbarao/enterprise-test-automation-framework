package com.framework.utils;

import com.framework.config.FrameworkConfig;
import io.qameta.allure.Allure;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility for capturing and attaching screenshots to Allure reports.
 */
@Slf4j
public class ScreenshotUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final String SCREENSHOT_DIR = FrameworkConfig.get("screenshot.dir", "target/screenshots");

    private ScreenshotUtil() {}

    /**
     * Capture a screenshot, save to disk, and attach to the Allure report.
     *
     * @param driver WebDriver instance
     * @param name   Screenshot label
     * @return       Path to the saved screenshot file, or empty string on failure
     */
    public static String capture(WebDriver driver, String name) {
        if (driver == null) return "";
        try {
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            // Attach to Allure
            Allure.addAttachment(name, "image/png", new ByteArrayInputStream(screenshot), "png");

            // Save to disk
            String fileName = name.replaceAll("[^a-zA-Z0-9_-]", "_")
                    + "_" + LocalDateTime.now().format(FMT) + ".png";
            Path dir = Paths.get(SCREENSHOT_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.write(file, screenshot);
            log.info("Screenshot saved: {}", file.toAbsolutePath());
            return file.toString();
        } catch (IOException e) {
            log.warn("Failed to save screenshot: {}", e.getMessage());
            return "";
        } catch (Exception e) {
            log.warn("Screenshot capture failed: {}", e.getMessage());
            return "";
        }
    }
}

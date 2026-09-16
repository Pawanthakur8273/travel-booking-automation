package com.prym.qa.config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Every tunable value in one place, each overridable with -D on the command line.
 *
 * <p>Locators live in the page objects; this holds environment and test data only.
 */
public final class Config {

    private Config() {
    }

    private static String get(String key, String fallback) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            v = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String baseUrl() {
        return get("base.url", "https://blazedemo.com");
    }

    /**
     * "standard" launches Chrome through ChromeDriver, which is what the suite
     * uses. "attach" starts Chrome as an ordinary browser first and connects
     * over the DevTools port - needed only for sites that refuse to serve a
     * ChromeDriver-launched browser. See DriverFactory.
     */
    public static String driverMode() {
        return get("driver.mode", "standard");
    }

    /** Explicit waits use this; nothing in the suite calls Thread.sleep. */
    public static int timeoutSeconds() {
        return getInt("timeout.seconds", 40);
    }

    public static boolean headless() {
        return Boolean.parseBoolean(get("headless", "false"));
    }

    /**
     * MakeMyTrip refuses to serve its results app to a ChromeDriver-launched
     * browser, so the suite starts Chrome itself and attaches over this port.
     * See DriverFactory for the full explanation.
     */
    public static int debugPort() {
        return getInt("chrome.debug.port", 9222);
    }

    public static String chromeProfileDir() {
        return get("chrome.profile", System.getProperty("java.io.tmpdir") + "/mmt-automation-profile");
    }

    public static String chromeBinary() {
        return get("chrome.binary", "");
    }

    public static String dbUrl() {
        return get("db.url", "jdbc:sqlite:" + System.getProperty("user.dir") + "/db/test-results.db");
    }

    public static String environmentName() {
        return get("env", "local");
    }

    /** Departure date, default 30 days out so it is always a valid future date. */
    public static String departureDate() {
        int days = getInt("departure.offset.days", 30);
        return LocalDate.now().plusDays(days).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public static int dashboardPort() {
        return getInt("dashboard.port", 8090);
    }
}

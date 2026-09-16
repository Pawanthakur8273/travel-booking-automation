package com.prym.qa.driver;

import com.prym.qa.config.Config;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Creates the browser the suite drives, and it does so the awkward way on purpose.
 *
 * <p><b>Why not just {@code new ChromeDriver()}?</b> MakeMyTrip serves its flight
 * results app only to what it believes is a human browser. When ChromeDriver
 * launches Chrome, the browser carries automation switches and
 * {@code navigator.webdriver == true}; MakeMyTrip detects that and answers
 * {@code /flight/search} with a bare JSON stub ({@code 200-OK}) instead of the
 * application. No wait, retry or locator change can fix that - the HTML is
 * never sent. This was verified with stealth flags on, stealth flags off, and
 * with a warmed persistent profile: all three were blocked.
 *
 * <p><b>What works.</b> Start Chrome as an ordinary browser ourselves, then
 * attach Selenium to it over the DevTools port. Chrome is then a normal browser
 * process that happens to have debugging enabled, {@code navigator.webdriver} is
 * false, and MakeMyTrip serves the real results page.
 *
 * <p>Consequence to remember: when attached via {@code debuggerAddress},
 * {@link WebDriver#quit()} only detaches - it does not close the browser. The
 * Chrome process must be destroyed separately, which {@link #quitDriver()} does.
 */
public final class DriverFactory {

    /**
     * One browser for the whole suite, created lazily and closed by a shutdown
     * hook.
     *
     * <p>Chrome keys an instance by its {@code --user-data-dir}, so launching a
     * fresh browser per scenario means a fresh profile and a fresh port every
     * time; in practice Chrome then refuses to bind ephemeral ports reliably and
     * the attach fails with {@code SessionNotCreatedException}. Reusing a single
     * instance removes that whole class of problem and saves roughly twenty
     * seconds of browser startup per scenario. Scenario isolation is preserved
     * by clearing cookies between scenarios - see {@link #resetSession()}.
     */
    private static WebDriver driver;
    private static Process chromeProcess;
    private static Path profileDir;

    private static final List<String> WINDOWS_CHROME_PATHS = List.of(
            "C:/Program Files/Google/Chrome/Application/chrome.exe",
            "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
            System.getenv("LOCALAPPDATA") + "/Google/Chrome/Application/chrome.exe");

    private DriverFactory() {
    }

    public static synchronized WebDriver getDriver() {
        if (driver == null) {
            throw new IllegalStateException("@Before should have called createDriver() first.");
        }
        return driver;
    }

    public static synchronized WebDriver createDriver() {
        if (driver != null) {
            resetSession();
            return driver;
        }

        ChromeOptions options = new ChromeOptions();
        if ("attach".equalsIgnoreCase(Config.driverMode())) {
            chromeProcess = launchChrome();
            waitForDevToolsPort();
            options.setExperimentalOption("debuggerAddress", "127.0.0.1:" + Config.debugPort());
        } else {
            options.addArguments("--disable-notifications", "--window-size=1600,1000",
                    "--disable-blink-features=AutomationControlled");
            options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            if (Config.headless()) {
                options.addArguments("--headless=new", "--disable-gpu");
            }
        }

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
        // Attaching does not inherit the launch --window-size, and MakeMyTrip's
        // hero carousel overlaps the search widget at smaller widths, so the
        // viewport is pinned explicitly.
        driver.manage().window().setSize(new Dimension(1600, 1000));

        Runtime.getRuntime().addShutdownHook(new Thread(DriverFactory::shutdown));
        return driver;
    }

    /** Keeps scenarios independent without paying for a browser restart. */
    private static void resetSession() {
        try {
            driver.manage().deleteAllCookies();
        } catch (Exception e) {
            System.err.println("[driver] could not clear cookies: " + e.getMessage());
        }
    }

    /**
     * Each scenario gets its own debug port and its own profile directory.
     *
     * <p>This is not belt-and-braces. Chrome treats {@code --user-data-dir} as an
     * instance key: launching a second Chrome against a directory already in use
     * makes the new process hand its arguments to the running instance and exit
     * immediately. The suite would then attach to a browser it does not own and
     * die with {@code NoSuchSessionException} the moment that one closed.
     */
    /**
     * Uses one stable, persistent profile rather than a throwaway per run.
     *
     * <p>This is load-bearing. A brand-new profile has no cookies and no history,
     * and MakeMyTrip answers that with the same {@code 200-OK} stub it gives an
     * obvious bot - verified: identical code passed with a warmed profile and was
     * blocked with a fresh one. Letting the profile persist lets normal cookies
     * accumulate, which is what makes the results page load at all.
     */
    private static Process launchChrome() {
        String binary = resolveChromeBinary();
        profileDir = Path.of(Config.chromeProfileDir());
        try {
            Files.createDirectories(profileDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create Chrome profile dir: " + profileDir, e);
        }

        List<String> cmd = new ArrayList<>(List.of(
                binary,
                "--remote-debugging-port=" + Config.debugPort(),
                "--user-data-dir=" + profileDir,
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-notifications",
                "--window-size=1600,1000",
                "about:blank"));
        if (Config.headless()) {
            // Note: headless is markedly more likely to be blocked by MakeMyTrip.
            cmd.add(2, "--headless=new");
        }

        try {
            return new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start Chrome at " + binary, e);
        }
    }

    private static String resolveChromeBinary() {
        if (!Config.chromeBinary().isBlank()) {
            return Config.chromeBinary();
        }
        return WINDOWS_CHROME_PATHS.stream()
                .filter(p -> Files.exists(Path.of(p)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Chrome not found. Pass -Dchrome.binary=/path/to/chrome"));
    }

    /** Chrome needs a moment before the DevTools endpoint answers. */
    private static void waitForDevToolsPort() {
        String probe = "http://127.0.0.1:" + Config.debugPort() + "/json/version";
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(probe).toURL().openConnection();
                conn.setConnectTimeout(1500);
                conn.setReadTimeout(1500);
                if (conn.getResponseCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            sleep(500);
        }
        throw new IllegalStateException(
                "Chrome DevTools port " + Config.debugPort() + " never opened", last);
    }

    /**
     * Closes the browser and the Chrome process it was attached to.
     *
     * <p>Registered as a JVM shutdown hook, so the browser survives across
     * scenarios but never outlives the suite. Note that {@code driver.quit()}
     * only detaches when attached over {@code debuggerAddress} - the process has
     * to be destroyed explicitly.
     */
    public static synchronized void shutdown() {
        if (driver != null) {
            try {
                driver.quit();          // detaches only - see class javadoc
            } catch (Exception ignored) {
                // a browser that already died is not a test failure
            }
            driver = null;
        }
        if (chromeProcess != null) {
            chromeProcess.destroy();
            try {
                if (!chromeProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    chromeProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                chromeProcess.destroyForcibly();
            }
            chromeProcess = null;
        }
        // The profile is deliberately NOT deleted - its accumulated cookies are
        // what keep MakeMyTrip serving the results page. Delete it by hand
        // (or pass -Dchrome.profile=...) if you need a clean slate.
        profileDir = null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

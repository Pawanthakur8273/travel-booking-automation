package com.prym.qa.steps;

import com.prym.qa.db.TestResultRepository;
import com.prym.qa.driver.DriverFactory;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.util.concurrent.atomic.AtomicLong;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Setup and teardown for every scenario, plus the bridge that records each
 * execution into SQLite.
 *
 * <p>One {@code test_run} row is opened for the whole suite (lazily, on the
 * first scenario) and closed by a JVM shutdown hook, so the run totals are
 * correct however the suite ends.
 */
public class Hooks {

    private static final AtomicLong RUN_ID = new AtomicLong(-1);
    private static final String REPORT_PATH = "target/cucumber-report.html";

    private long scenarioStart;

    private static long runId() {
        if (RUN_ID.get() < 0) {
            synchronized (RUN_ID) {
                if (RUN_ID.get() < 0) {
                    long id = TestResultRepository.startRun("chrome", REPORT_PATH);
                    RUN_ID.set(id);
                    Runtime.getRuntime().addShutdownHook(
                            new Thread(() -> TestResultRepository.finishRun(id)));
                }
            }
        }
        return RUN_ID.get();
    }

    @Before
    public void setUp(Scenario scenario) {
        runId();
        scenarioStart = System.currentTimeMillis();
        DriverFactory.createDriver();
        System.out.println("[start] " + scenario.getName());
    }

    /**
     * Records the outcome before the browser is torn down, and attaches a
     * screenshot to the Cucumber report when a scenario fails.
     */
    @After
    public void tearDown(Scenario scenario) {
        long duration = System.currentTimeMillis() - scenarioStart;
        String status = scenario.isFailed() ? "FAILED" : "PASSED";

        if (scenario.isFailed()) {
            try {
                WebDriver driver = DriverFactory.getDriver();
                byte[] shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                scenario.attach(shot, "image/png", scenario.getName());
            } catch (Exception e) {
                System.err.println("[screenshot] unavailable: " + e.getMessage());
            }
        }

        TestResultRepository.saveResult(
                runId(),
                featureOf(scenario),
                scenario.getName(),
                String.join(",", scenario.getSourceTagNames()),
                status,
                duration,
                scenario.isFailed() ? "See " + REPORT_PATH : null,
                REPORT_PATH);

        System.out.printf("[%s] %s (%d ms)%n", status.toLowerCase(), scenario.getName(), duration);
        // The browser is shared across the suite and closed by DriverFactory's
        // shutdown hook; cookies are cleared before each scenario instead.
    }

    /** "…/features/flight_search.feature" -> "flight_search". */
    private String featureOf(Scenario scenario) {
        String uri = scenario.getUri().toString();
        int slash = uri.lastIndexOf('/');
        String file = slash >= 0 ? uri.substring(slash + 1) : uri;
        return file.replace(".feature", "");
    }
}

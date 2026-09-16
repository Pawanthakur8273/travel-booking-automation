package com.prym.qa.pages;

import com.prym.qa.config.Config;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * The MakeMyTrip home page flight search widget.
 *
 * <p>All locators verified against the live site: the city fields carry stable
 * ids ({@code fromCity} / {@code toCity}) and the autocomplete renders
 * {@code li.react-autosuggest__suggestion} entries.
 */
public class FlightSearchPage extends BasePage {

    private static final By FROM_CITY = By.id("fromCity");
    private static final By TO_CITY = By.id("toCity");
    private static final By SUGGESTION = By.cssSelector("li.react-autosuggest__suggestion");
    private static final By AUTOSUGGEST_INPUT = By.cssSelector(
            "input.react-autosuggest__input, input[placeholder='From'], input[placeholder='To']");
    private static final By SEARCH_BUTTON = By.cssSelector("a.widgetSearchBtn, a.primaryBtn.font24");
    private static final By DAY_CELL = By.cssSelector("div[class*='DayPicker-Day']");

    public FlightSearchPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens the flight search page, reloading if the widget does not appear.
     *
     * <p>MakeMyTrip intermittently serves a home page variant with no search
     * widget at all - the same URL renders it on one visit and not the next.
     * That is a property of the site, not a locator problem, so the fix is to
     * reload rather than to wait longer.
     */
    public FlightSearchPage open() {
        TimeoutException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            driver.get(Config.baseUrl() + "/flights/");
            dismissOverlays();
            try {
                new WebDriverWait(driver, Duration.ofSeconds(20))
                        .until(ExpectedConditions.presenceOfElementLocated(FROM_CITY));
                dismissOverlays();
                // Deliberately NOT disabling the carousel here: its wrapper also
                // contains the search widget, so blanket pointer-events:none makes
                // clicks fall straight through the city fields. It is neutralised
                // only when a click is genuinely intercepted - see clickResilient.
                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
                pause(400);
                return this;
            } catch (TimeoutException e) {
                last = e;
                System.out.println("[search] no widget on attempt " + attempt + " - reloading");
            }
        }
        throw new AssertionError("MakeMyTrip never rendered the flight search widget", last);
    }

    public FlightSearchPage searchFrom(String cityCode) {
        selectCity(FROM_CITY, cityCode);
        return this;
    }

    public FlightSearchPage searchTo(String cityCode) {
        selectCity(TO_CITY, cityCode);
        return this;
    }

    /**
     * Clicking the field turns it into a live search box; the typed text goes to
     * whichever element MakeMyTrip focuses, so the active element is used rather
     * than assuming the input keeps its id.
     */
    private void selectCity(By field, String typed) {
        dismissOverlays();
        clickResilient(field);

        // Clicking the field swaps in a fresh react-autosuggest input. Waiting
        // for that specific element beats typing into activeElement, which races
        // the swap and silently sends the keys to the old node.
        WebElement search = new WebDriverWait(driver, Duration.ofSeconds(15))
                .until(ExpectedConditions.visibilityOfElementLocated(AUTOSUGGEST_INPUT));
        search.clear();
        search.sendKeys(typed);

        wait.until(ExpectedConditions.presenceOfElementLocated(SUGGESTION));
        pause(900);
        driver.findElements(SUGGESTION).get(0).click();
        pause(1000);
    }

    /** Picks a selectable day from whichever month the calendar is showing. */
    public FlightSearchPage selectDeparture() {
        pause(700);
        var days = driver.findElements(DAY_CELL).stream()
                .filter(d -> !"true".equals(d.getAttribute("aria-disabled")))
                .filter(d -> !d.getText().isBlank())
                .toList();
        if (!days.isEmpty()) {
            WebElement target = days.get(Math.min(15, days.size() - 1));
            scrollTo(target);
            target.click();
            pause(800);
        }
        return this;
    }

    /**
     * Submits the search and follows the results wherever they open.
     *
     * <p>MakeMyTrip sometimes renders results in the same tab and sometimes in a
     * new one, so this waits for either the URL to change or a new window to
     * appear, and switches if needed.
     */
    public FlightResultsPage submitSearch() {
        dismissOverlays();
        Set<String> handlesBefore = driver.getWindowHandles();
        String urlBefore = driver.getCurrentUrl();

        WebElement button = clickable(SEARCH_BUTTON);
        scrollTo(button);
        pause(300);
        button.click();

        new WebDriverWait(driver, Duration.ofSeconds(45)).until(d ->
                d.getWindowHandles().size() > handlesBefore.size()
                        || !d.getCurrentUrl().equals(urlBefore));

        Set<String> handlesAfter = new HashSet<>(driver.getWindowHandles());
        handlesAfter.removeAll(handlesBefore);
        if (!handlesAfter.isEmpty()) {
            driver.switchTo().window(handlesAfter.iterator().next());
        }
        return new FlightResultsPage(driver);
    }

    public boolean isLoaded() {
        return isPresent(FROM_CITY);
    }
}

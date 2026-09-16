package com.prym.qa.pages;

import com.prym.qa.config.Config;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * The MakeMyTrip flight results listing.
 *
 * <p>Verified locators: each flight is a {@code div.listingCardWrap} carrying the
 * airline, flight number, times and fare in its text; refinements in the left
 * rail are {@code label.checkboxContainer} elements labelled "Non Stop",
 * "IndiGo", "Air India" and so on.
 */
public class FlightResultsPage extends BasePage {

    private static final By FLIGHT_CARD = By.cssSelector("div[class*='listingCardWrap']");
    private static final By FILTER_CHECKBOX = By.cssSelector("label[class*='checkboxContainer']");

    /** Fares render as "₹ 6,040"; the group is the number without separators. */
    private static final Pattern FARE = Pattern.compile("₹\\s*([\\d,]+)");

    public FlightResultsPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Results stream in, and MakeMyTrip shows overlays while they do. Waits for
     * cards to actually exist rather than for a fixed period.
     */
    public FlightResultsPage waitForResults() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(Config.timeoutSeconds() + 40))
                    .until(d -> {
                        dismissOverlays();
                        return !d.findElements(FLIGHT_CARD).isEmpty();
                    });
        } catch (org.openqa.selenium.TimeoutException e) {
            // A bare "condition failed" says nothing useful about a live site.
            String body = "";
            try {
                body = driver.findElement(By.tagName("body")).getText();
                body = body.substring(0, Math.min(220, body.length()));
            } catch (Exception ignored) {
                body = "<unreadable>";
            }
            throw new AssertionError(
                    "No flight cards appeared.\n  url:   " + driver.getCurrentUrl()
                            + "\n  title: " + driver.getTitle()
                            + "\n  body:  " + body.replace("\n", " | "), e);
        }
        dismissOverlays();
        return this;
    }

    public int flightCount() {
        return driver.findElements(FLIGHT_CARD).size();
    }

    public boolean hasResults() {
        return flightCount() > 0;
    }

    public List<WebElement> flights() {
        return driver.findElements(FLIGHT_CARD);
    }

    /** Applies a left-rail refinement by its visible label, e.g. "Non Stop". */
    public FlightResultsPage applyFilter(String label) {
        dismissOverlays();
        WebElement target = allPresent(FILTER_CHECKBOX).stream()
                .filter(el -> el.getText() != null
                        && el.getText().trim().equalsIgnoreCase(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No results filter labelled '" + label + "'. Available: "
                                + availableFilters()));
        scrollTo(target);
        pause(400);
        target.click();
        // the grid re-renders asynchronously after a refinement
        pause(3500);
        dismissOverlays();
        return this;
    }

    public List<String> availableFilters() {
        return driver.findElements(FILTER_CHECKBOX).stream()
                .map(WebElement::getText)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList();
    }

    public boolean hasFilter(String label) {
        return availableFilters().stream().anyMatch(f -> f.equalsIgnoreCase(label));
    }

    /** Fare of the first listed flight, as a plain number. */
    public int firstFlightFare() {
        return fareOf(firstFlightText());
    }

    public String firstFlightText() {
        wait.until(ExpectedConditions.presenceOfElementLocated(FLIGHT_CARD));
        return driver.findElements(FLIGHT_CARD).get(0).getText();
    }

    /**
     * Parses "₹ 6,040" into 6040. Throws rather than returning 0, because a
     * silent zero would turn a broken locator into a passing assertion.
     */
    public static int fareOf(String cardText) {
        Matcher m = FARE.matcher(cardText == null ? "" : cardText);
        if (!m.find()) {
            throw new AssertionError("No fare found in flight card text: " + cardText);
        }
        return Integer.parseInt(m.group(1).replace(",", ""));
    }

    public boolean everyFlightIsNonStop() {
        return flights().stream()
                .map(WebElement::getText)
                .allMatch(t -> t.toLowerCase().contains("non stop"));
    }

    public FlightResultsPage select(int index) {
        List<WebElement> cards = flights();
        if (index >= cards.size()) {
            throw new AssertionError(
                    "Asked for flight " + index + " but only " + cards.size() + " are listed.");
        }
        WebElement card = cards.get(index);
        scrollTo(card);
        pause(500);
        card.click();
        pause(2500);
        dismissOverlays();
        return this;
    }
}

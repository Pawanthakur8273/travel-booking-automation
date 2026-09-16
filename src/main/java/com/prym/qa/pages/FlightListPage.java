package com.prym.qa.pages;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * The list of flights returned by a search.
 *
 * <p>Columns, verified against the live page:
 * {@code Choose | Flight # | Airline | Departs | Arrives | Price}.
 */
public class FlightListPage extends BasePage {

    private static final By ROW = By.cssSelector("table tbody tr");
    private static final By HEADING = By.tagName("h3");
    private static final By CHOOSE_BUTTON = By.cssSelector("input.btn-small");

    private static final int COL_FLIGHT_NUMBER = 1;
    private static final int COL_AIRLINE = 2;
    private static final int COL_PRICE = 5;

    public FlightListPage(WebDriver driver) {
        super(driver);
    }

    public FlightListPage waitForFlights() {
        wait.until(ExpectedConditions.presenceOfElementLocated(ROW));
        return this;
    }

    public int flightCount() {
        return driver.findElements(ROW).size();
    }

    /** e.g. "Flights from Boston to London:" */
    public String heading() {
        return visible(HEADING).getText().trim();
    }

    private WebElement row(int index) {
        List<WebElement> rows = driver.findElements(ROW);
        if (index >= rows.size()) {
            throw new AssertionError(
                    "Asked for flight " + index + " but only " + rows.size() + " are listed.");
        }
        return rows.get(index);
    }

    private String cell(int rowIndex, int column) {
        return row(rowIndex).findElements(By.tagName("td")).get(column).getText().trim();
    }

    public String airlineOf(int index) {
        return cell(index, COL_AIRLINE);
    }

    public String flightNumberOf(int index) {
        return cell(index, COL_FLIGHT_NUMBER);
    }

    /** "$472.56" -> 472.56 */
    public double priceOf(int index) {
        return parseMoney(cell(index, COL_PRICE));
    }

    /**
     * Strips currency formatting. Throws rather than defaulting to zero, because
     * a silent 0.0 would turn a broken locator into a passing assertion.
     */
    public static double parseMoney(String text) {
        if (text == null) {
            throw new AssertionError("Cannot read a price from null.");
        }
        String cleaned = text.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) {
            throw new AssertionError("No number in price text: '" + text + "'");
        }
        return Double.parseDouble(cleaned);
    }

    public boolean everyFlightHasAirlineAndPrice() {
        int count = flightCount();
        for (int i = 0; i < count; i++) {
            if (airlineOf(i).isEmpty() || flightNumberOf(i).isEmpty()) {
                return false;
            }
            parseMoney(cell(i, COL_PRICE));      // throws if unparseable
        }
        return count > 0;
    }

    public PurchasePage chooseFlight(int index) {
        row(index).findElement(CHOOSE_BUTTON).click();
        return new PurchasePage(driver).waitForSummary();
    }
}

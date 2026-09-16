package com.prym.qa.pages;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * The traveller details and payment page - the last step before money changes
 * hands, and where every journey in this suite deliberately stops.
 *
 * <p>The booking summary is rendered as plain lines of text rather than a table
 * ("Airline: United", "Price: 400", "Total Cost: 914.76"), so it is read with
 * labelled regexes rather than cell lookups.
 */
public class PurchasePage extends BasePage {

    private static final By NAME = By.id("inputName");
    private static final By ADDRESS = By.id("address");
    private static final By CITY = By.id("city");
    private static final By STATE = By.id("state");
    private static final By ZIP_CODE = By.id("zipCode");
    private static final By CARD_TYPE = By.id("cardType");
    private static final By CARD_NUMBER = By.id("creditCardNumber");
    private static final By CARD_MONTH = By.id("creditCardMonth");
    private static final By CARD_YEAR = By.id("creditCardYear");
    private static final By NAME_ON_CARD = By.id("nameOnCard");
    private static final By PURCHASE_BUTTON = By.cssSelector("input[type='submit']");
    private static final By SUMMARY_HEADING = By.tagName("h2");

    public PurchasePage(WebDriver driver) {
        super(driver);
    }

    public PurchasePage waitForSummary() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(NAME));
        return this;
    }

    public boolean isLoaded() {
        return isPresent(NAME) && isPresent(PURCHASE_BUTTON);
    }

    public String heading() {
        return visible(SUMMARY_HEADING).getText().trim();
    }

    private String bodyText() {
        return driver.findElement(By.tagName("body")).getText();
    }

    /** Reads a "Label: value" line out of the summary block. */
    private String summaryValue(String label) {
        Matcher m = Pattern.compile(Pattern.quote(label) + ":\\s*([^\\n]+)").matcher(bodyText());
        if (!m.find()) {
            throw new AssertionError("No '" + label + "' line in the booking summary.");
        }
        return m.group(1).trim();
    }

    public String summaryAirline() {
        return summaryValue("Airline");
    }

    public String summaryFlightNumber() {
        return summaryValue("Flight Number");
    }

    public double summaryPrice() {
        return FlightListPage.parseMoney(summaryValue("Price"));
    }

    public double summaryFees() {
        return FlightListPage.parseMoney(summaryValue("Arbitrary Fees and Taxes"));
    }

    public double summaryTotal() {
        return FlightListPage.parseMoney(summaryValue("Total Cost"));
    }

    public PurchasePage enterTravellerDetails(String name, String address, String city,
                                              String state, String zip) {
        visible(NAME).sendKeys(name);
        visible(ADDRESS).sendKeys(address);
        visible(CITY).sendKeys(city);
        visible(STATE).sendKeys(state);
        visible(ZIP_CODE).sendKeys(zip);
        return this;
    }

    public PurchasePage enterCardDetails(String cardType, String number, String month,
                                         String year, String nameOnCard) {
        new Select(visible(CARD_TYPE)).selectByValue(cardType);
        visible(CARD_NUMBER).sendKeys(number);
        visible(CARD_MONTH).clear();
        visible(CARD_MONTH).sendKeys(month);
        visible(CARD_YEAR).clear();
        visible(CARD_YEAR).sendKeys(year);
        visible(NAME_ON_CARD).sendKeys(nameOnCard);
        return this;
    }

    public boolean travellerDetailsComplete() {
        return !visible(NAME).getAttribute("value").isBlank()
                && !visible(ADDRESS).getAttribute("value").isBlank()
                && !visible(CITY).getAttribute("value").isBlank()
                && !visible(ZIP_CODE).getAttribute("value").isBlank();
    }

    /**
     * The suite never clicks Purchase Flight. This asserts we are still on the
     * form and no confirmation was reached.
     */
    public boolean paymentNotSubmitted() {
        return driver.getCurrentUrl().contains("purchase")
                && !driver.getCurrentUrl().contains("confirmation")
                && isPresent(PURCHASE_BUTTON);
    }
}

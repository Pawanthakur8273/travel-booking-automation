package com.prym.qa.pages;

import com.prym.qa.config.Config;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * The travel agency home page: choose a departure city, a destination, and search.
 */
public class HomePage extends BasePage {

    private static final By FROM_PORT = By.name("fromPort");
    private static final By TO_PORT = By.name("toPort");
    private static final By FIND_FLIGHTS = By.cssSelector("input.btn-primary");
    private static final By HEADING = By.tagName("h1");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage open() {
        driver.get(Config.baseUrl() + "/");
        wait.until(ExpectedConditions.visibilityOfElementLocated(FROM_PORT));
        return this;
    }

    public boolean isLoaded() {
        return isPresent(FROM_PORT) && isPresent(TO_PORT);
    }

    public String heading() {
        return visible(HEADING).getText().trim();
    }

    public HomePage departingFrom(String city) {
        new Select(visible(FROM_PORT)).selectByVisibleText(city);
        return this;
    }

    public HomePage arrivingAt(String city) {
        new Select(visible(TO_PORT)).selectByVisibleText(city);
        return this;
    }

    public String selectedDeparture() {
        return new Select(visible(FROM_PORT)).getFirstSelectedOption().getText().trim();
    }

    public String selectedDestination() {
        return new Select(visible(TO_PORT)).getFirstSelectedOption().getText().trim();
    }

    public FlightListPage findFlights() {
        click(FIND_FLIGHTS);
        return new FlightListPage(driver).waitForFlights();
    }
}

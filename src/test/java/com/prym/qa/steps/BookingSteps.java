package com.prym.qa.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.prym.qa.driver.DriverFactory;
import com.prym.qa.pages.FlightListPage;
import com.prym.qa.pages.HomePage;
import com.prym.qa.pages.PurchasePage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions: they translate Gherkin into page-object calls and make the
 * assertions. Deliberately free of locators and WebDriver calls - all of that
 * lives in the page objects.
 */
public class BookingSteps {

    private HomePage homePage;
    private FlightListPage flightList;
    private PurchasePage purchasePage;

    @Given("the traveller is on the flight search page")
    public void openSearchPage() {
        homePage = new HomePage(DriverFactory.getDriver()).open();
        assertTrue(homePage.isLoaded(), "The flight search form never rendered.");
    }

    @When("the traveller searches for flights from {string} to {string}")
    public void searchFlights(String from, String to) {
        flightList = homePage.departingFrom(from).arrivingAt(to).findFlights();
    }

    @Then("the flight list shows at least {int} flight")
    public void listShowsAtLeast(int minimum) {
        int actual = flightList.flightCount();
        assertTrue(actual >= minimum,
                "Expected at least " + minimum + " flights but the page listed " + actual + ".");
    }

    @And("the results are headed {string}")
    public void resultsHeaded(String expected) {
        assertEquals(expected, flightList.heading(),
                "The results heading does not name the route that was searched.");
    }

    @And("every listed flight shows an airline, a flight number and a fare")
    public void everyFlightIsComplete() {
        assertTrue(flightList.everyFlightHasAirlineAndPrice(),
                "At least one listed flight is missing its airline, flight number or fare.");
    }

    @And("the traveller chooses the first flight")
    public void chooseFirstFlight() {
        purchasePage = flightList.chooseFlight(0);
    }

    @Then("the booking summary shows an airline, a flight number and a price")
    public void summaryIsPopulated() {
        assertTrue(!purchasePage.summaryAirline().isBlank(),
                "The booking summary has no airline.");
        assertTrue(!purchasePage.summaryFlightNumber().isBlank(),
                "The booking summary has no flight number.");
        assertTrue(purchasePage.summaryPrice() > 0,
                "The booking summary shows a non-positive price.");
    }

    @And("the traveller details form is ready to be filled")
    public void formIsReady() {
        assertTrue(purchasePage.isLoaded(),
                "The traveller details and payment form is not present.");
    }

    /**
     * The arithmetic check that makes this more than a click-through: whatever
     * the fare and fees are, the total the traveller is asked to pay must be
     * their sum.
     */
    @Then("the total cost equals the price plus the fees and taxes")
    public void totalIsPricePlusFees() {
        double price = purchasePage.summaryPrice();
        double fees = purchasePage.summaryFees();
        double total = purchasePage.summaryTotal();
        assertEquals(price + fees, total, 0.01,
                "Total cost " + total + " is not price " + price + " plus fees " + fees + ".");
    }

    @When("the traveller fills in their details")
    public void fillDetails() {
        purchasePage
                .enterTravellerDetails("Priya Otwani", "12 Marine Drive", "Mumbai", "MH", "400020")
                .enterCardDetails("visa", "4111111111111111", "12", "2030", "Priya Otwani");
    }

    @Then("the traveller details are complete")
    public void detailsAreComplete() {
        assertTrue(purchasePage.travellerDetailsComplete(),
                "The traveller details did not stick in the form.");
    }

    @And("no payment has been submitted")
    public void noPaymentSubmitted() {
        assertTrue(purchasePage.paymentNotSubmitted(),
                "The journey went past the payment form - it must stop before paying.");
    }
}

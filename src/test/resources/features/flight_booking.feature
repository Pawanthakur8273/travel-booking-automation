@booking
Feature: Selecting a flight and reviewing the booking
  As a traveller who has found a suitable flight
  I want to select it and review the cost before paying
  So that I know exactly what I am committing to

  # No payment is ever submitted. Every scenario stops at the traveller details
  # and payment form, which is as far as a booking can go without spending money.

  Background:
    Given the traveller is on the flight search page

  @smoke @TC04
  Scenario: TC04 - Choosing a flight opens the booking summary and payment form
    When the traveller searches for flights from "Boston" to "London"
    And the traveller chooses the first flight
    Then the booking summary shows an airline, a flight number and a price
    And the traveller details form is ready to be filled

  @regression @TC05
  Scenario: TC05 - The total payable is the fare plus fees, and nothing is paid
    When the traveller searches for flights from "Boston" to "London"
    And the traveller chooses the first flight
    Then the total cost equals the price plus the fees and taxes
    When the traveller fills in their details
    Then the traveller details are complete
    And no payment has been submitted

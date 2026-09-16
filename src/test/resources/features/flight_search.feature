@flights
Feature: Searching for flights
  As a traveller planning a trip
  I want to search for flights between two cities
  So that I can compare the options available to me

  Background:
    Given the traveller is on the flight search page

  @smoke @TC01
  Scenario: TC01 - Searching a route returns available flights
    When the traveller searches for flights from "Boston" to "London"
    Then the flight list shows at least 1 flight
    And the results are headed "Flights from Boston to London:"

  @smoke @TC02
  Scenario: TC02 - Every listed flight shows the details needed to choose one
    When the traveller searches for flights from "Paris" to "Rome"
    Then the flight list shows at least 1 flight
    And every listed flight shows an airline, a flight number and a fare

  @regression @datadriven @TC03
  Scenario Outline: TC03 - Flights are available from <from> to <to>
    When the traveller searches for flights from "<from>" to "<to>"
    Then the flight list shows at least <minimum> flight
    And every listed flight shows an airline, a flight number and a fare

    Examples:
      | from         | to           | minimum |
      | Boston       | London       | 1       |
      | Philadelphia | Berlin       | 1       |
      | San Diego    | New York     | 1       |
      | Portland     | Buenos Aires | 1       |

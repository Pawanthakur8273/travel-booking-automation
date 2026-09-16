# Travel Booking Automation — Selenium + Java + Cucumber BDD + SQLite

An end-to-end flight booking suite written with **Selenium 4**, **Cucumber 7 (BDD)** and
**JUnit 5**, using the **Page Object Model**. Every scenario execution is written to a
**SQLite** database, and a small **dashboard** reads that database back to show what passed,
what failed, when, and where the report is.

The journey under test: **search flights → review the results → choose a flight →
reach the traveller details and payment page → verify the booking summary adds up →
stop before paying.** No payment is ever submitted.

---

## Quick start

```powershell
# 1. prerequisites: JDK 21+ and Maven on PATH, plus Chrome installed
java -version
mvn -v

# 2. run the whole suite
mvn test

# 3. see the results dashboard
mvn exec:java -Dexec.mainClass=com.prym.qa.dashboard.DashboardServer
#    then open http://localhost:8090
```

The Chrome driver itself is handled automatically by Selenium Manager — nothing to download.

---

## Running the suite

| Command | What it runs |
| --- | --- |
| `mvn test` | All 5 test cases (8 executions, because one is data-driven) |
| `mvn test -Dcucumber.filter.tags="@smoke"` | Smoke only |
| `mvn test -Dcucumber.filter.tags="@regression"` | Regression only |
| `mvn test -Dcucumber.filter.tags="@TC05"` | One specific test case |
| `mvn test -Dcucumber.filter.tags="@smoke and not @booking"` | Tag expressions are supported |
| `mvn test -Dheadless=true` | No visible browser window |
| `mvn test -Dbase.url=https://blazedemo.com` | Point at a different environment |

Everything configurable lives in [`Config.java`](src/main/java/com/prym/qa/config/Config.java)
and can be overridden with `-D` on the command line — no file edits needed.

---

## The five test cases

| ID | Scenario | Tags | What it proves |
| --- | --- | --- | --- |
| **TC01** | Searching a route returns available flights | `@smoke` | Search works and the results are headed with the route actually searched |
| **TC02** | Every listed flight shows the details needed to choose one | `@smoke` | Every row carries an airline, a flight number and a parseable fare |
| **TC03** | Flights are available from *X* to *Y* | `@regression @datadriven` | **Scenario Outline** over 4 city pairs — one keyword, four executions |
| **TC04** | Choosing a flight opens the booking summary and payment form | `@smoke` | Selection reaches the traveller details page with a populated summary |
| **TC05** | The total payable is the fare plus fees, and nothing is paid | `@regression` | **Total Cost == Price + Fees**, details persist, and no payment is submitted |

TC05 is the one that earns its keep: it is arithmetic on the real page, not a click-through.
`mvn test` produces **8 executions** because TC03 expands to four.

---

## How it is put together

```
src/main/java/com/prym/qa/
├── config/Config.java            environment + test data, all -D overridable
├── driver/DriverFactory.java     browser lifecycle (two strategies, see below)
├── pages/                        Page Object Model - the only place locators live
│   ├── BasePage.java             waits, resilient clicks, overlay handling
│   ├── HomePage.java             search form
│   ├── FlightListPage.java       results table
│   ├── PurchasePage.java         traveller details + payment + booking summary
│   ├── FlightSearchPage.java     MakeMyTrip search widget  (see note below)
│   └── FlightResultsPage.java    MakeMyTrip results        (see note below)
├── db/TestResultRepository.java  JDBC: schema, inserts, dashboard queries
└── dashboard/DashboardServer.java  reads the DB, serves the dashboard

src/test/java/com/prym/qa/
├── runners/RunCucumberTest.java  JUnit 5 suite + Cucumber plugins
└── steps/
    ├── BookingSteps.java         Gherkin -> page objects + assertions
    └── Hooks.java                @Before / @After, screenshots, DB recording

src/test/resources/features/
├── flight_search.feature         TC01, TC02, TC03
└── flight_booking.feature        TC04, TC05
```

The separation is strict and deliberate:

- **Feature files** state intent in Gherkin. No technical detail.
- **Step definitions** translate Gherkin into page-object calls and assert. **No locators,
  no WebDriver calls.**
- **Page objects** own every locator, wait and interaction.
- **Config** owns environment and data.

A markup change on the site is a one-line edit in a page object. If fixing a locator ever
forces you to edit a step definition, the separation has been broken.

### Hooks

[`Hooks.java`](src/test/java/com/prym/qa/steps/Hooks.java) does the setup and teardown:

- `@Before` — opens one `test_run` row for the suite (lazily) and prepares the browser
- `@After` — records the scenario's outcome, duration and tags into SQLite, attaches a
  **screenshot to the report when a scenario fails**, and leaves the browser ready for the next

---

## Reports

`mvn test` writes:

| File | What it is |
| --- | --- |
| `target/cucumber-report.html` | The Cucumber HTML report — every step, with failure screenshots |
| `target/cucumber.json` | Machine-readable, for CI or other reporting tools |
| `target/surefire-reports/` | JUnit XML |

---

## Results database

Execution history goes into SQLite at `db/test-results.db`. Two tables:

**`test_run`** — one row per suite run: when it started and finished, environment, browser,
totals, and the report path.

**`test_result`** — one row per scenario execution: feature, scenario, tags, status,
duration, error, timestamp, report path.

SQLite was chosen because it needs no server, no service and no password — clone the repo and
run, with nothing to install. The code is ordinary JDBC, so **switching to MySQL or PostgreSQL
is a URL and driver change**, nothing more:

```powershell
mvn test -Ddb.url="jdbc:mysql://localhost:3306/qa_results?user=root&password=secret"
```

Query it directly if you want:

```powershell
sqlite3 db/test-results.db "SELECT scenario, status, duration_ms FROM test_result ORDER BY id DESC LIMIT 10;"
```

---

## Dashboard

```powershell
mvn exec:java -Dexec.mainClass=com.prym.qa.dashboard.DashboardServer
# http://localhost:8090   (change with -Ddashboard.port=9000)
```

It reads the database live and shows total executions, passed, failed, pass rate and suite
count, then the full execution history — status, scenario, feature, tags, timestamp, duration
and report path. Re-run the suite and refresh to see new results. Built on the JDK's own HTTP
server, so there is no web framework to install.

---

## A note on the target site

The assignment named **MakeMyTrip**, and this suite was built against it first. MakeMyTrip
**cannot be automated end to end**, and it is worth recording exactly why, because it is not a
locator problem:

- The home page and search widget automate fine. `FlightSearchPage` and `FlightResultsPage`
  are real, working Page Objects for it — city autocomplete, calendar, filters and all.
- The **results endpoint refuses to serve the application to an automated browser**. Requesting
  `/flight/search?...` returns a bare JSON stub (`200-OK`) instead of HTML. There is no page to
  wait for and no popup to dismiss.
- Verified against: stealth flags on, stealth flags off, a warmed persistent profile, and a
  fresh one. All blocked.
- Launching Chrome as an ordinary browser and attaching over the DevTools port **did work**
  initially — that is what `driver.mode=attach` in `DriverFactory` implements, and it rendered
  live flights and fares. But after sustained automated traffic MakeMyTrip escalated to
  blocking the whole IP, and even that route began returning the stub.

So the suite runs against **BlazeDemo**, a purpose-built flight-booking site with the same
shape of journey — search, results, selection, traveller details and payment — which the
assignment's own "MakeMyTrip (or similar)" wording allows. The MakeMyTrip page objects and the
attach-mode driver are kept in the repository as working code and as the record of that
investigation.

To try the MakeMyTrip path yourself:

```powershell
mvn test -Ddriver.mode=attach -Dbase.url=https://www.makemytrip.com
```

---

## Prerequisites

| Tool | Version used |
| --- | --- |
| JDK | Temurin 21 |
| Maven | 3.9.16 |
| Chrome | any current version (driver auto-resolved by Selenium Manager) |

Dependencies (all fetched by Maven): Selenium 4.27, Cucumber 7.20, JUnit 5.11,
`sqlite-jdbc` 3.47.

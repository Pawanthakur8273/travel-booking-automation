package com.prym.qa.runners;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Entry point for the suite.
 *
 * <p>Filter by tag from the command line, for example:
 * {@code mvn test -Dcucumber.filter.tags="@smoke"}
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.prym.qa.steps")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty,"
                + "html:target/cucumber-report.html,"
                + "json:target/cucumber.json,"
                + "summary")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
public class RunCucumberTest {
}

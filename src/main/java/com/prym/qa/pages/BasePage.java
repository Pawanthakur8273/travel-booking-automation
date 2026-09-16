package com.prym.qa.pages;

import com.prym.qa.config.Config;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Shared browser mechanics. Page objects extend this; step definitions never
 * touch WebDriver directly.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    /**
     * MakeMyTrip throws login and offer overlays at unpredictable moments. They
     * are not part of any journey under test, so they are cleared on a
     * best-effort basis before meaningful interactions. A missing overlay is the
     * normal case, never a failure.
     */
    private static final List<By> OVERLAYS = List.of(
            By.cssSelector("span.commonModal__close"),
            By.cssSelector(".commonModal__close"),
            By.cssSelector("[data-cy='closeModal']"),
            By.cssSelector("button[aria-label='Close']"));

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(Config.timeoutSeconds()));
    }

    protected WebElement visible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement clickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected List<WebElement> allPresent(By locator) {
        wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        return driver.findElements(locator);
    }

    protected boolean isPresent(By locator) {
        return !driver.findElements(locator).isEmpty();
    }

    /**
     * Clicks natively. This matters: MakeMyTrip's search widget is React, and a
     * JavaScript click does not fire the synthetic events that open the city
     * autocomplete. Verified - the JS-click version silently never opens it.
     */
    protected void click(By locator) {
        clickable(locator).click();
    }

    /**
     * A native click that survives MakeMyTrip's floating promo carousel.
     *
     * <p>The home page drifts a {@code div.sliderItemContent} banner over the
     * search widget, which intercepts the click. Falling back to a JavaScript
     * click is not an option here - React would not open the autocomplete - so
     * instead the element is scrolled clear, overlays are dismissed and the real
     * click is retried.
     */
    protected WebElement clickResilient(By locator) {
        ElementClickInterceptedException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                WebElement element = clickable(locator);
                scrollTo(element);
                pause(350);
                element.click();
                return element;
            } catch (ElementClickInterceptedException e) {
                last = e;
                dismissOverlays();
                hideBlockingCarousel();
                // nudge the viewport so the floating banner no longer covers it
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, -140);");
                pause(700);
            }
        }
        // last resort: drive the mouse to the element's own centre
        try {
            WebElement element = clickable(locator);
            new Actions(driver).moveToElement(element).pause(Duration.ofMillis(200)).click().perform();
            return element;
        } catch (Exception ignored) {
            throw new AssertionError(
                    "Could not click " + locator + " - something kept intercepting it", last);
        }
    }

    /**
     * MakeMyTrip's home page hero carousel ({@code imageSlideContainer} /
     * {@code sliderItemContent}) animates across the search widget and swallows
     * clicks. It is pure decoration and belongs to no journey under test, so it
     * is hidden rather than fought.
     */
    protected void hideBlockingCarousel() {
        // Only the moving banner itself - NOT .slideContainer, which also wraps
        // the search widget and would make its fields unclickable.
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelectorAll('.imageSlideContainer, .sliderItemContent')"
                        + ".forEach(function(e){ e.style.pointerEvents='none'; });");
    }

    protected void scrollTo(WebElement element) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});", element);
    }

    public void dismissOverlays() {
        for (By overlay : OVERLAYS) {
            for (WebElement el : driver.findElements(overlay)) {
                try {
                    if (el.isDisplayed()) {
                        el.click();
                        pause(400);
                    }
                } catch (Exception ignored) {
                    // overlay vanished on its own between find and click
                }
            }
        }
    }

    /**
     * Deliberately not a general-purpose sleep: only used to let an overlay
     * animation finish. Everything else waits on a condition.
     */
    protected void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    public String title() {
        return driver.getTitle();
    }
}

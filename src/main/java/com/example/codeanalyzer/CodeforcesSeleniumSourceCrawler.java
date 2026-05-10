package com.example.codeanalyzer;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class CodeforcesSeleniumSourceCrawler {
    private static final String LOGIN_URL = "https://codeforces.com/enter";
    private static final String SAMPLE_SUBMISSION_URL =
            "https://codeforces.com/contest/1971/submission/260384511";

    public static void main(String[] args) {
        String username = firstNonBlank(System.getenv("CF_USERNAME"), systemProperty("cf.username"));
        String password = firstNonBlank(System.getenv("CF_PASSWORD"), systemProperty("cf.password"));
        String browser = firstNonBlank(System.getenv("BROWSER"), systemProperty("browser"), "chrome");
        String driverPath = firstNonBlank(System.getenv("WEBDRIVER_PATH"), systemProperty("webdriver.path"));

        if (username == null || password == null) {
            System.out.println("Thiếu CF_USERNAME/CF_PASSWORD hoặc -Dcf.username/-Dcf.password.");
            System.out.println("Chương trình vẫn mở trang login để bạn đăng nhập thủ công.");
        }

        WebDriver driver = createDriver(browser, driverPath);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        try {
            driver.get(LOGIN_URL);
            waitForPageReady(driver);

            if (looksLikeCloudflareChallenge(driver)) {
                System.out.println("Codeforces/Cloudflare đang yêu cầu xác minh.");
                System.out.println("Hãy hoàn tất xác minh thủ công trong cửa sổ trình duyệt, rồi nhấn Enter ở console.");
                waitForEnter();
            }

            if (username != null && password != null && isLoginPage(driver)) {
                login(driver, wait, username, password);
            } else if (isLoginPage(driver)) {
                System.out.println("Hãy đăng nhập thủ công trong trình duyệt, rồi nhấn Enter ở console.");
                waitForEnter();
            }

            wait.until(d -> !isLoginPage(d));
            System.out.println("Đăng nhập hoặc phiên đăng nhập đã sẵn sàng.");

            String sourceCode = fetchSubmissionSource(driver, wait, SAMPLE_SUBMISSION_URL);
            System.out.println("===== SOURCE CODE =====");
            System.out.println(sourceCode);
        } finally {
            driver.quit();
        }
    }

private static WebDriver createDriver(String browser, String driverPath) {
        if ("chrome".equalsIgnoreCase(browser)) {
            if (driverPath != null) {
                System.setProperty("webdriver.chrome.driver", driverPath);
            }
            ChromeOptions options = new ChromeOptions();
            
            options.setExperimentalOption("excludeSwitches", java.util.Collections.singletonList("enable-automation"));
            options.setExperimentalOption("useAutomationExtension", false);
            options.addArguments("--disable-blink-features=AutomationControlled");
            
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            options.addArguments("--start-maximized");
            options.addArguments("--disable-extensions");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            return new ChromeDriver(options);
        }

        if ("edge".equalsIgnoreCase(browser)) {
            if (driverPath != null) {
                System.setProperty("webdriver.edge.driver", driverPath);
            }
            EdgeOptions options = new EdgeOptions();
            options.addArguments("--start-maximized");
            return new EdgeDriver(options);
        }

        throw new IllegalArgumentException("Trình duyệt không hỗ trợ: " + browser);
    }

    private static void login(WebDriver driver, WebDriverWait wait, String username, String password) {
        WebElement usernameInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[name='handleOrEmail']")));
        WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[name='password']")));

        usernameInput.clear();
        usernameInput.sendKeys(username);
        passwordInput.clear();
        passwordInput.sendKeys(password);

        WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[type='submit'], button[type='submit']")));
        loginButton.click();

        wait.until(d -> !isLoginPage(d));
    }

    private static String fetchSubmissionSource(WebDriver driver, WebDriverWait wait, String submissionUrl) {
        driver.get(submissionUrl);
        waitForPageReady(driver);

        if (looksLikeCloudflareChallenge(driver)) {
            System.out.println("Trang submission đang yêu cầu xác minh.");
            System.out.println("Hãy hoàn tất xác minh thủ công trong trình duyệt, rồi nhấn Enter ở console.");
            waitForEnter();
        }

        WebElement source = wait.until(d -> findFirstWithText(d, List.of(
                By.id("program-source-text"),
                By.cssSelector("pre#program-source-text"),
                By.cssSelector(".program-source"),
                By.cssSelector("pre.prettyprint")
        )));

        String code = source.getText();
        if (code == null || code.isBlank()) {
            code = source.getDomProperty("textContent");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Tìm thấy khung source nhưng nội dung rỗng.");
        }
        return code;
    }

    private static WebElement findFirstWithText(WebDriver driver, List<By> selectors) {
        for (By selector : selectors) {
            for (WebElement element : driver.findElements(selector)) {
                String text = element.getText();
                String textContent = element.getDomProperty("textContent");
                if ((text != null && !text.isBlank()) || (textContent != null && !textContent.isBlank())) {
                    return element;
                }
            }
        }
        return null;
    }

    private static boolean isLoginPage(WebDriver driver) {
        String url = driver.getCurrentUrl();
        if (url != null && url.contains("/enter")) {
            return true;
        }
        return !driver.findElements(By.cssSelector("input[name='handleOrEmail']")).isEmpty()
                && !driver.findElements(By.cssSelector("input[name='password']")).isEmpty();
    }

    private static boolean looksLikeCloudflareChallenge(WebDriver driver) {
        String title = safe(driver::getTitle);
        String body = "";
        List<WebElement> bodies = driver.findElements(By.tagName("body"));
        if (!bodies.isEmpty()) {
            body = bodies.get(0).getText();
        }
        String text = (title + " " + body).toLowerCase();
        return text.contains("checking your browser")
                || text.contains("verify you are human")
                || text.contains("chrome for testing is only for automated testing")
                || text.contains("cloudflare");
    }

    private static void waitForPageReady(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(d -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
    }

    private static void waitForEnter() {
        try {
            while (System.in.read() != '\n') {
                // Wait until the user confirms the manual browser step.
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Không đọc được Enter từ console.", ex);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String systemProperty(String name) {
        return System.getProperty(name);
    }

    private static String safe(SupplierWithException<String> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private interface SupplierWithException<T> {
        T get();
    }
}

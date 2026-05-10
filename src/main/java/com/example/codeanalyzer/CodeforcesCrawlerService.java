package com.example.codeanalyzer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import javax.swing.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CodeforcesCrawlerService {
    private static final int MAX_SUBMISSIONS_PER_ACCOUNT = 50;
    private static final int API_DELAY_MS = 2200;
    private static final int PAGE_DELAY_MS = 1200;

    private final DatabaseService database;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public CodeforcesCrawlerService(DatabaseService database) {
        this.database = database;
    }

    public List<Models.CodeforcesSubmission> crawlAllAccounts(JFrame parent) throws Exception {
        List<Models.Account> accounts = database.loadCodeforcesAccounts();
        if (accounts.isEmpty()) {
            return new ArrayList<>();
        }

        WebDriver driver = createDriver();
        try {
            driver.get("https://codeforces.com/enter");
            JOptionPane.showMessageDialog(parent,
                    "Trình duyệt Codeforces đã mở.\n"
                            + "Nếu chưa đăng nhập, hãy đăng nhập thủ công trong cửa sổ đó rồi bấm OK để bắt đầu crawl.");

            List<Models.CodeforcesSubmission> saved = new ArrayList<>();
            for (Models.Account account : accounts) {
                List<Models.CodeforcesSubmission> candidates = loadRecentSubmissions(account);
                for (Models.CodeforcesSubmission submission : candidates) {
                    String source;
                    try {
                        source = fetchSourceCode(parent, driver, submission);
                    } catch (RuntimeException ex) {
                        System.err.println("Skip Codeforces submission " + submission.submissionId + ": " + ex.getMessage());
                        continue;
                    }
                    if (source == null || source.isBlank()) {
                        continue;
                    }
                    submission.sourceCode = source;
                    submission.databaseId = database.saveSubmission(submission);
                    database.updateLastSubmissionId(account.id, submission.submissionId);
                    saved.add(submission);
                    Thread.sleep(PAGE_DELAY_MS);
                }
                Thread.sleep(API_DELAY_MS);
            }
            return saved;
        } finally {
            driver.quit();
        }
    }

    private List<Models.CodeforcesSubmission> loadRecentSubmissions(Models.Account account) throws Exception {
        String handle = URLEncoder.encode(account.handle, StandardCharsets.UTF_8);
        String url = "https://codeforces.com/api/user.status?handle=" + handle
                + "&from=1&count=" + MAX_SUBMISSIONS_PER_ACCOUNT;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JSONObject root = new JSONObject(resp.body());
        if (!"OK".equalsIgnoreCase(root.optString("status"))) {
            throw new IllegalStateException("Codeforces API lỗi với " + account.handle + ": " + root.optString("comment"));
        }

        List<Models.CodeforcesSubmission> out = new ArrayList<>();
        JSONArray result = root.optJSONArray("result");
        if (result == null) {
            return out;
        }

        long lastSeen = Math.max(0, account.lastSubmissionId);
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.getJSONObject(i);
            long submissionId = item.optLong("id", -1);
            long contestId = item.optLong("contestId", -1);
            if (submissionId <= lastSeen || submissionId <= 0 || contestId <= 0) {
                continue;
            }

            JSONObject problem = item.optJSONObject("problem");
            Models.CodeforcesSubmission submission = new Models.CodeforcesSubmission();
            submission.accountId = account.id;
            submission.handle = account.handle;
            submission.submissionId = submissionId;
            submission.contestId = contestId;
            submission.problemIndex = problem == null ? "" : problem.optString("index", "");
            submission.language = item.optString("programmingLanguage", "");
            submission.verdict = item.optString("verdict", "");
            submission.creationTimeSeconds = item.optLong("creationTimeSeconds", 0);
            out.add(submission);
        }

        out.sort(Comparator.comparingLong(s -> s.submissionId));
        return out;
    }

    private String fetchSourceCode(JFrame parent, WebDriver driver, Models.CodeforcesSubmission submission) {
        String url;
        if (submission.contestId >= 100000) {
            url = "https://codeforces.com/gym/" + submission.contestId + "/submission/" + submission.submissionId;
        } else {
            url = "https://codeforces.com/contest/" + submission.contestId + "/submission/" + submission.submissionId;
        }

        try {
            // Thay vì điều hướng (gây ra lỗi Cloudflare), ta dùng JS fetch ngầm từ trang hiện tại (đã được xác minh)
            String js = "var callback = arguments[arguments.length - 1];"
                    + "fetch('" + url + "')"
                    + ".then(response => response.text())"
                    + ".then(html => {"
                    + "    var doc = new DOMParser().parseFromString(html, 'text/html');"
                    + "    var el = doc.getElementById('program-source-text') || doc.querySelector('.program-source') || doc.querySelector('pre.prettyprint');"
                    + "    if (el) {"
                    + "        callback({status: 'OK', data: el.textContent});"
                    + "    } else {"
                    + "        if (doc.title.includes('Just a moment') || html.includes('cf-browser-verification')) {"
                    + "            callback({status: 'CF_BLOCK', data: ''});"
                    + "        } else {"
                    + "            callback({status: 'NOT_FOUND', data: html.substring(0, 200)});"
                    + "        }"
                    + "    }"
                    + "})"
                    + ".catch(err => callback({status: 'ERROR', data: err.toString()}));";

            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) ((JavascriptExecutor) driver).executeAsyncScript(js);

            if (result == null) {
                throw new IllegalStateException("Kết quả JS fetch bị null");
            }

            String status = (String) result.get("status");
            String data = (String) result.get("data");

            if ("OK".equals(status)) {
                if (data != null && !data.isBlank()) {
                    return data;
                }
                throw new IllegalStateException("Tìm thấy vùng code nhưng nội dung rỗng.");
            } else if ("CF_BLOCK".equals(status)) {
                // Nếu fetch ngầm cũng bị chặn, yêu cầu người dùng F5
                JOptionPane.showMessageDialog(parent, "Cloudflare đang chặn fetch ngầm!\n"
                        + "Vui lòng F5 (tải lại) trang web hiện tại trên Edge và xác minh Cloudflare,\n"
                        + "sau đó bấm OK ở đây để thử lại.");
                // Thử lại đệ quy 1 lần (hoặc có thể dùng vòng lặp, ở đây throw để skip hoặc user thử lại)
                throw new IllegalStateException("Bị Cloudflare chặn Ajax fetch. Hãy tải lại trang và xác minh.");
            } else if ("NOT_FOUND".equals(status)) {
                throw new IllegalStateException("Không tìm thấy thẻ source code trong HTML trả về. (Preview: " + data.replace("\n", " ") + ")");
            } else {
                throw new IllegalStateException("JS fetch lỗi: " + data);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Không lấy được source code submission "
                    + submission.submissionId + ". " + ex.getMessage(), ex);
        }
    }

    private WebDriver createDriver() {
        String browser = firstNonBlank(System.getenv("BROWSER"), System.getProperty("browser"), "edge");

        if ("chrome".equalsIgnoreCase(browser)) {
            org.openqa.selenium.chrome.ChromeOptions options = new org.openqa.selenium.chrome.ChromeOptions();
            
            String driverPath = firstNonBlank(
                    System.getenv("CHROME_DRIVER_PATH"),
                    System.getenv("WEBDRIVER_CHROME_DRIVER"),
                    System.getenv("WEBDRIVER_PATH"),
                    System.getProperty("webdriver.chrome.driver")
            );
            if (driverPath != null) {
                validateDriverPath(driverPath);
                System.setProperty("webdriver.chrome.driver", driverPath);
            }

            String chromeBinary = firstNonBlank(
                    System.getenv("CHROME_BINARY_PATH")
            );
            if (chromeBinary != null) {
                options.setBinary(chromeBinary);
            }

            options.setExperimentalOption("excludeSwitches", java.util.Collections.singletonList("enable-automation"));
            options.setExperimentalOption("useAutomationExtension", false);
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            options.addArguments("--start-maximized");

            String profileDir = firstNonBlank(
                    System.getenv("CODEFORCES_CHROME_PROFILE_DIR")
            );
            if (profileDir != null && !profileDir.isBlank()) {
                options.addArguments("--user-data-dir=" + profileDir.trim());
            }

            return new org.openqa.selenium.chrome.ChromeDriver(options);
        }

        EdgeOptions options = new EdgeOptions();
        
        // 1. Cấu hình đường dẫn Driver (Giữ nguyên logic của bạn)
        String driverPath = firstNonBlank(
                System.getenv("EDGE_DRIVER_PATH"),
                System.getenv("MSEDGEDRIVER_PATH"),
                System.getenv("WEBDRIVER_EDGE_DRIVER"),
                System.getenv("WEBDRIVER_PATH"),
                System.getProperty("webdriver.edge.driver")
        );
        if (driverPath != null) {
            validateDriverPath(driverPath);
            System.setProperty("webdriver.edge.driver", driverPath);
        }

        // 2. Cấu hình Binary (Giữ nguyên logic của bạn)
        String edgeBinary = firstNonBlank(
                System.getenv("EDGE_BINARY_PATH"),
                System.getenv("MSEDGE_BINARY_PATH")
        );
        if (edgeBinary != null) {
            options.setBinary(edgeBinary);
        }

        // --- 3. PHẦN NGỤY TRANG (QUAN TRỌNG NHẤT ĐỂ LÁCH CLOUDFLARE) ---
        
        // Loại bỏ dòng chữ "Chrome/Edge is being controlled by automated test software"
        options.setExperimentalOption("excludeSwitches", java.util.Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        
        // Ẩn biến navigator.webdriver (Xóa dấu vết Selenium)
        options.addArguments("--disable-blink-features=AutomationControlled");
        
        options.addArguments("--start-maximized");

        // --- 4. Cấu hình Profile (Giữ nguyên logic của bạn) ---
        String profileDir = firstNonBlank(
                System.getenv("CODEFORCES_EDGE_PROFILE_DIR"),
                System.getenv("CODEFORCES_CHROME_PROFILE_DIR")
        );
        if (profileDir != null && !profileDir.isBlank()) {
            options.addArguments("--user-data-dir=" + profileDir.trim());
        }

        return new EdgeDriver(options);
    }

    private void validateDriverPath(String driverPath) {
        Path path = Path.of(driverPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Không tìm thấy EdgeDriver tại " + driverPath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("EdgeDriver không phải file thường: " + driverPath);
        }
        if (!Files.isExecutable(path)) {
            throw new IllegalStateException("EdgeDriver chưa có quyền chạy. Hãy chạy: chmod +x " + driverPath);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

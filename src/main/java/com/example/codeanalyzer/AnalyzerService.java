package com.example.codeanalyzer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class AnalyzerService {
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    private static final int HTTP_TIMEOUT_SECONDS = 20;
    private static final int MAX_RETRIES = 1; // Gọi 1 lần duy nhất
    private static final long[] RETRY_BACKOFF_MS = {30000L}; // Chỉ có 1 phần tử
    private static final long RATE_LIMIT_DELAY_MS = 30000L; // 60 giây delay giữa các request

    private final HttpClient http = HttpClient.newHttpClient();
    private final String geminiKey;
    private final String geminiModel;
    private final Map<String, Models.AnalysisResult> cache = new HashMap<>();
    private long lastRequestTime = 0;
    private final Semaphore requestSemaphore = new Semaphore(1); // Chỉ cho phép 1 request cùng lúc

    public AnalyzerService(String geminiKey) {
        this.geminiKey = geminiKey;
        String envModel = System.getenv("GEMINI_MODEL");
        this.geminiModel = (envModel == null || envModel.isBlank()) ? DEFAULT_GEMINI_MODEL : envModel.trim();
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }

    public Models.AnalysisResult analyzeCode(Models.Submission s) throws IOException, InterruptedException {
        if (s == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }

        if (geminiKey == null || geminiKey.isBlank()) {
            throw new IllegalArgumentException("Gemini API key is not configured");
        }

        if (s.code == null || s.code.isBlank()) {
            return emptyAnalysis(s.submissionId);
        }

        // Kiểm tra cache trước
        String cacheKey = generateCacheKey(s.code);
        if (cache.containsKey(cacheKey)) {
            Models.AnalysisResult cached = cache.get(cacheKey);
            cached.submissionId = s.submissionId; // Update submission ID
            System.out.println("Cache hit for submission " + s.submissionId);
            return cached;
        }

        // Chờ semaphore để đảm bảo chỉ 1 request cùng lúc
        requestSemaphore.acquire();
        try {
            // Áp dụng rate limiting (chờ 60 giây trước khi gọi)
            applyRateLimit();

            System.out.println("Calling Gemini API for submission " + s.submissionId + "...");
            
            String prompt = buildAnalysisPrompt(s.code);
            JSONObject body = buildGeminiRequestBody(prompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String responseBody = resp.body() == null ? "" : resp.body();

                JSONObject respJson;
                try {
                    respJson = new JSONObject(responseBody);
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to parse Gemini response as JSON", ex);
                }

                if (status == 200) {
                    String content = extractGeminiContent(respJson);
                    if (content.isBlank()) {
                        throw new RuntimeException("Gemini returned blank content");
                    }

                    JSONObject j;
                    try {
                        j = extractJsonObject(content);
                    } catch (Exception ex) {
                        throw new RuntimeException("Failed to extract JSON object from Gemini content", ex);
                    }

                    Models.AnalysisResult out = new Models.AnalysisResult();
                    out.submissionId = s.submissionId;
                    out.dsAndAlgos = "ds:" + j.optString("ds", "") + "; alg:" + j.optString("algorithms", "");
                    out.usedAI = j.optString("usedAI", "unknown");
                    out.confidence = clamp(j.optDouble("confidence", 0.5), 0.0, 1.0);
                    out.dsScore = clamp(j.optDouble("dsScore", 0.0), 0.0, 10.0);
                    out.algoScore = clamp(j.optDouble("algoScore", 0.0), 0.0, 10.0);
                    out.aiScore = clamp(j.optDouble("aiScore", 0.0), 0.0, 10.0);
                    
                    // Lưu vào cache
                    Models.AnalysisResult toCache = new Models.AnalysisResult();
                    toCache.submissionId = 0; // Placeholder
                    toCache.dsAndAlgos = out.dsAndAlgos;
                    toCache.usedAI = out.usedAI;
                    toCache.confidence = out.confidence;
                    toCache.dsScore = out.dsScore;
                    toCache.algoScore = out.algoScore;
                    toCache.aiScore = out.aiScore;
                    cache.put(cacheKey, toCache);
                    
                    System.out.println("API call successful for submission " + s.submissionId);
                    return out;
                }

                // Nếu không phải 200, throw lỗi (không retry)
                String errorMsg = mapGeminiHttpError(respJson, status);
                throw new RuntimeException("Gemini API error (status " + status + "): " + errorMsg);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (IOException e) {
                throw new RuntimeException("Network error calling Gemini API", e);
            }
        } finally {
            requestSemaphore.release();
        }
    }

    private boolean sleepBeforeRetry(int attempt) {
        if (attempt >= RETRY_BACKOFF_MS.length) {
            return false;
        }
        try {
            Thread.sleep(RETRY_BACKOFF_MS[attempt]);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private synchronized void applyRateLimit() throws InterruptedException {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime;
        
        if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
            long waitTime = RATE_LIMIT_DELAY_MS - timeSinceLastRequest;
            Thread.sleep(waitTime);
        }
        
        lastRequestTime = System.currentTimeMillis();
    }

    private String generateCacheKey(String code) {
        // Sử dụng hash code của code làm cache key
        return "code_" + Integer.toHexString(code.hashCode());
    }

    private Models.AnalysisResult emptyAnalysis(long submissionId) {
        Models.AnalysisResult out = new Models.AnalysisResult();
        out.submissionId = submissionId;
        out.dsAndAlgos = "(no source)";
        out.usedAI = "unknown";
        out.confidence = 0.0;
        out.dsScore = 0.0;
        out.algoScore = 0.0;
        out.aiScore = 0.0;
        return out;
    }

    private String buildAnalysisPrompt(String code) {
        return "You are a code reviewer for competitive programming. "
                + "Analyze this source code and return ONLY valid compact JSON (no markdown code block) with keys: "
                + "ds(string), algorithms(string), usedAI(string yes/no/uncertain), confidence(number 0..1), "
                + "dsScore(number 0..10), algoScore(number 0..10), aiScore(number 0..10 where higher means more likely AI generated). "
                + "Keep ds and algorithms short: use comma-separated names only, no explanations, no full sentences. "
                + "Examples: ds=\"vector\", algorithms=\"sorting, binary search\". "
                + "Use common short labels such as array, vector, list, map, set, queue, stack, heap, graph, tree, sorting, binary search, greedy, DP, BFS, DFS.\n"
                + "Code:\n" + code;
    }

    private JSONObject buildGeminiRequestBody(String prompt) {
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject contentItem = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        contentItem.put("parts", parts);
        contents.put(contentItem);
        body.put("contents", contents);
        body.put("generationConfig", new JSONObject().put("temperature", 0.1));
        return body;
    }

    private String mapGeminiHttpError(JSONObject respJson, int status) {
        JSONObject errorObj = respJson.optJSONObject("error");
        String code = errorObj != null ? String.valueOf(errorObj.opt("code")) : "http_" + status;
        if (code == null || code.equals("null") || code.isBlank()) {
            code = "http_" + status;
        }

        if (status == 429) {
            return "gemini_quota_or_rate_limited";
        }
        if (status == 401 || status == 403) {
            return "gemini_auth_error";
        }
        return "gemini_error_" + code;
    }

    private String extractGeminiContent(JSONObject respJson) {
        JSONArray candidates = respJson.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        JSONObject firstCandidate = candidates.getJSONObject(0);
        String finishReason = firstCandidate.optString("finishReason", "");
        if ("SAFETY".equalsIgnoreCase(finishReason) || "BLOCKED".equalsIgnoreCase(finishReason)) {
            return "";
        }

        JSONObject candidateContent = firstCandidate.optJSONObject("content");
        JSONArray candidateParts = candidateContent != null ? candidateContent.optJSONArray("parts") : null;
        if (candidateParts == null || candidateParts.isEmpty()) {
            return "";
        }

        StringBuilder allText = new StringBuilder();
        for (int i = 0; i < candidateParts.length(); i++) {
            JSONObject part = candidateParts.optJSONObject(i);
            if (part == null) {
                continue;
            }
            String t = part.optString("text", "");
            if (!t.isBlank()) {
                if (allText.length() > 0) {
                    allText.append('\n');
                }
                allText.append(t);
            }
        }

        return allText.toString().trim();
    }


    private int countTrue(boolean... values) {
        int c = 0;
        for (boolean v : values) {
            if (v) c++;
        }
        return c;
    }

    private String trimComma(String s) {
        String out = s.trim();
        if (out.endsWith(",")) {
            return out.substring(0, out.length() - 1).trim();
        }
        return out;
    }

    private JSONObject extractJsonObject(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.startsWith("```")) {
            int firstNewLine = raw.indexOf('\n');
            int lastFence = raw.lastIndexOf("```");
            if (firstNewLine > 0 && lastFence > firstNewLine) {
                raw = raw.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            raw = raw.substring(start, end + 1);
        }
        return new JSONObject(raw);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

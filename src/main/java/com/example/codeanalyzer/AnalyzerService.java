package com.example.codeanalyzer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class AnalyzerService {
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    private static final int HTTP_TIMEOUT_SECONDS = 20;
    private static final int MAX_RETRIES = 5;
    private static final long[] RETRY_BACKOFF_MS = {1000L, 2000L, 4000L, 8000L, 16000L};

    private final HttpClient http = HttpClient.newHttpClient();
    private final String geminiKey;
    private final String geminiModel;

    public AnalyzerService(String geminiKey) {
        this.geminiKey = geminiKey;
        String envModel = System.getenv("GEMINI_MODEL");
        this.geminiModel = (envModel == null || envModel.isBlank()) ? DEFAULT_GEMINI_MODEL : envModel.trim();
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public Models.AnalysisResult analyzeCode(Models.Submission s) {
        if (s == null) {
            Models.Submission empty = new Models.Submission();
            return analyzeWithHeuristic(empty, "invalid_submission");
        }

        if (geminiKey == null || geminiKey.isBlank()) {
            return analyzeWithHeuristic(s, "missing_gemini_key");
        }

        if (s.code == null || s.code.isBlank()) {
            return emptyAnalysis(s.submissionId);
        }

        String prompt = buildAnalysisPrompt(s.code);
        JSONObject body = buildGeminiRequestBody(prompt);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiKey))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        String lastFallbackReason = "gemini_unknown_error";

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String responseBody = resp.body() == null ? "" : resp.body();

                JSONObject respJson;
                try {
                    respJson = new JSONObject(responseBody);
                } catch (Exception ex) {
                    return analyzeWithHeuristic(s, "gemini_invalid_response");
                }

                if (status == 200) {
                    String content = extractGeminiContent(respJson);
                    if (content.isBlank()) {
                        return analyzeWithHeuristic(s, "gemini_blank_content");
                    }

                    JSONObject j;
                    try {
                        j = extractJsonObject(content);
                    } catch (Exception ex) {
                        return analyzeWithHeuristic(s, "gemini_invalid_content_json");
                    }

                    Models.AnalysisResult out = new Models.AnalysisResult();
                    out.submissionId = s.submissionId;
                    out.dsAndAlgos = "ds:" + j.optString("ds", "") + "; alg:" + j.optString("algorithms", "");
                    out.usedAI = j.optString("usedAI", "unknown");
                    out.confidence = clamp(j.optDouble("confidence", 0.5), 0.0, 1.0);
                    out.dsScore = clamp(j.optDouble("dsScore", 0.0), 0.0, 10.0);
                    out.algoScore = clamp(j.optDouble("algoScore", 0.0), 0.0, 10.0);
                    out.aiScore = clamp(j.optDouble("aiScore", 0.0), 0.0, 10.0);
                    return out;
                }

                if (status == 429) {
                    lastFallbackReason = "gemini_quota_or_rate_limited";
                    if (!sleepBeforeRetry(attempt)) {
                        return analyzeWithHeuristic(s, "gemini_interrupted");
                    }
                    continue;
                }

                lastFallbackReason = mapGeminiHttpError(respJson, status);
                return analyzeWithHeuristic(s, lastFallbackReason);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return analyzeWithHeuristic(s, "gemini_interrupted");
            } catch (IOException e) {
                lastFallbackReason = "gemini_network_error";
                if (attempt == MAX_RETRIES - 1) {
                    break;
                }
                if (!sleepBeforeRetry(attempt)) {
                    return analyzeWithHeuristic(s, "gemini_interrupted");
                }
            }
        }

        return analyzeWithHeuristic(s, lastFallbackReason + "_retries_exhausted");
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


    private Models.AnalysisResult analyzeWithHeuristic(Models.Submission s, String fallbackReason) {
        Models.AnalysisResult out = new Models.AnalysisResult();
        out.submissionId = s.submissionId;

        if (s.code == null || s.code.isBlank()) {
            out.dsAndAlgos = "(no source)";
            out.usedAI = "unknown";
            out.confidence = 0.0;
            out.dsScore = 0.0;
            out.algoScore = 0.0;
            out.aiScore = 0.0;
            return out;
        }

        String code = s.code.toLowerCase(Locale.ROOT);

        boolean hasVector = code.contains("vector<") || code.contains("arraylist") || code.contains("list<");
        boolean hasArray = code.contains("int[]") || code.contains("long[]") || code.contains("char[]") || code.contains("new int[");

        boolean hasMap = code.contains("map<") || code.contains("unordered_map") || code.contains("hashmap") || code.contains("dict");
        boolean hasSet = code.contains("set<") || code.contains("unordered_set") || code.contains("hashset") || code.contains("set(");

        boolean hasQueue = code.contains("queue<") || code.contains("priority_queue") || code.contains("deque<") || code.contains("pq");
        boolean hasStack = code.contains("stack<") || code.contains(" stack");

        boolean hasTree = code.contains("treenode") || code.contains("node*") || code.contains("struct node") || (code.contains("left") && code.contains("right"));
        boolean hasGraph = code.contains("adj[") || code.contains("adjacency") || code.contains("graph") || code.contains("dfs") || code.contains("bfs");
        boolean hasLinkedList = code.contains("listnode") || code.contains("linkedlist") || code.contains("next");

        boolean hasSort = code.contains("sort(") || code.contains("arrays.sort") || code.contains("collections.sort") || code.contains("qsort");
        boolean hasBfs = code.contains("bfs") || code.contains("breadth") || (code.contains("queue") && code.contains("visited"));
        boolean hasDfs = code.contains("dfs") || code.contains("depth") || (code.contains("recurs") && code.contains("visited"));
        boolean hasDp = code.contains("dp[") || code.contains("dynamic programming") || code.contains("memo") || code.contains("bottom-up") || code.contains("top-down");
        boolean hasBinarySearch = code.contains("binary_search") || (code.contains("mid") && code.contains("left") && code.contains("right"));
        boolean hasDijkstra = code.contains("dijkstra") || (code.contains("shortest") && code.contains("path"));
        boolean hasGreedy = code.contains("greedy") || (code.contains("sort") && code.contains("for") && code.length() > 200);
        boolean hasBacktracking = code.contains("backtrack") || (code.contains("recurs") && code.contains("return") && code.contains("false"));
        boolean hasUnionFind = code.contains("union") || code.contains("find(") || code.contains("parent[");

        StringBuilder ds = new StringBuilder();
        if (hasArray) ds.append("array, ");
        if (hasVector) ds.append("vector/list, ");
        if (hasMap) ds.append("hashmap, ");
        if (hasSet) ds.append("hashset, ");
        if (hasQueue) ds.append("queue/heap, ");
        if (hasStack) ds.append("stack, ");
        if (hasTree) ds.append("tree, ");
        if (hasGraph) ds.append("graph, ");
        if (hasLinkedList) ds.append("linked list, ");

        StringBuilder alg = new StringBuilder();
        if (hasSort) alg.append("sorting, ");
        if (hasBfs) alg.append("BFS, ");
        if (hasDfs) alg.append("DFS, ");
        if (hasDp) alg.append("DP, ");
        if (hasBinarySearch) alg.append("binary search, ");
        if (hasDijkstra) alg.append("dijkstra, ");
        if (hasGreedy) alg.append("greedy, ");
        if (hasBacktracking) alg.append("backtracking, ");
        if (hasUnionFind) alg.append("union-find, ");

        String dsText = trimComma(ds.length() == 0 ? "basic types" : ds.toString());
        String algText = trimComma(alg.length() == 0 ? "implementation/brute force" : alg.toString());

        double dsScore = Math.min(10.0, countTrue(hasArray, hasVector, hasMap, hasSet, hasQueue, hasStack, hasTree, hasGraph, hasLinkedList) * 1.1);
        double algoScore = Math.min(10.0, countTrue(hasSort, hasBfs, hasDfs, hasDp, hasBinarySearch, hasDijkstra, hasGreedy, hasBacktracking, hasUnionFind) * 1.1);

        int aiSignal = 0;
        if (code.contains("chatgpt") || code.contains("generated by ai") || code.contains("openai") || code.contains("claude")) aiSignal += 5;
        if ((code.contains("# explanation") || code.contains("// explanation") || (code.contains("/*") && code.contains("solution")))) aiSignal += 2;
        if (code.contains("time complexity") && code.contains("space complexity")) aiSignal += 2;
        if (code.contains("approach:") || code.contains("algorithm:")) aiSignal += 1;
        if (code.contains("this solution") || code.contains("this approach")) aiSignal += 1;
        if (s.code.length() > 8000 && (code.contains("# ") || code.contains("//"))) aiSignal += 1;

        if (countTrue(hasMap, hasSet, hasQueue, hasTree, hasGraph, hasDijkstra, hasUnionFind, hasDp) >= 4) {
            if (code.contains("import") || code.contains("using namespace")) {
                aiSignal -= 1;
            }
        }

        double aiScore = Math.min(10.0, aiSignal * 1.5);
        String usedAI;
        if (aiScore >= 7) {
            usedAI = "likely (heuristic)";
        } else if (aiScore <= 2) {
            usedAI = "no (heuristic)";
        } else {
            usedAI = "uncertain (heuristic)";
        }

        out.dsAndAlgos = "ds:" + dsText + "; alg:" + algText;
        out.usedAI = usedAI;
        out.confidence = 0.50;
        out.dsScore = dsScore;
        out.algoScore = algoScore;
        out.aiScore = Math.max(0, aiScore);
        return out;
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

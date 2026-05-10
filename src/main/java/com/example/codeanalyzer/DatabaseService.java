package com.example.codeanalyzer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {
    private static final String DEFAULT_SQLITE_URL = "jdbc:sqlite:code_analyzer.db";
    
    private final String url;
    private final String user;
    private final String password;
    private boolean available;
    private String statusMessage = "Chưa kiểm tra CSDL";
    private final boolean isSQLite;

    private DatabaseService(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.isSQLite = url.startsWith("jdbc:sqlite:");
    }

    public static DatabaseService fromEnvironment() {
        String url = System.getenv("DB_URL");
        if (url == null || url.isBlank()) {
            url = DEFAULT_SQLITE_URL;
        }
        String user = envOrDefault("DB_USER", "root");
        String password = envOrDefault("DB_PASSWORD", "");
        return new DatabaseService(url, user, password);
    }

    public void initialize() {
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            if (isSQLite) {
                // SQLite specific initialization
                st.executeUpdate("CREATE TABLE IF NOT EXISTS accounts ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "handle TEXT NOT NULL, "
                        + "platform TEXT NOT NULL, "
                        + "last_submission_id INTEGER, "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "UNIQUE (platform, handle)"
                        + ")");

                st.executeUpdate("CREATE TABLE IF NOT EXISTS submissions ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "external_submission_id INTEGER, "
                        + "account_id INTEGER, "
                        + "handle TEXT, "
                        + "platform TEXT NOT NULL, "
                        + "contest_id INTEGER, "
                        + "problem_index TEXT, "
                        + "language_name TEXT, "
                        + "verdict TEXT, "
                        + "creation_time INTEGER, "
                        + "source_code TEXT NOT NULL, "
                        + "source_type TEXT NOT NULL DEFAULT 'MANUAL', "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "UNIQUE (platform, external_submission_id), "
                        + "FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL"
                        + ")");

                st.executeUpdate("CREATE TABLE IF NOT EXISTS analyses ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "submission_id INTEGER NOT NULL, "
                        + "model TEXT, "
                        + "ds_and_algos TEXT, "
                        + "used_ai TEXT, "
                        + "confidence REAL NOT NULL DEFAULT 0, "
                        + "ds_score REAL NOT NULL DEFAULT 0, "
                        + "algo_score REAL NOT NULL DEFAULT 0, "
                        + "ai_score REAL NOT NULL DEFAULT 0, "
                        + "analyzed_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE"
                        + ")");
                
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_submissions_account ON submissions(account_id)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_analyses_submission ON analyses(submission_id)");
                
                statusMessage = "SQLite đã kết nối (" + url + ")";
            } else {
                // MySQL specific initialization
                st.executeUpdate("CREATE TABLE IF NOT EXISTS accounts ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                        + "handle VARCHAR(128) NOT NULL, "
                        + "platform VARCHAR(32) NOT NULL, "
                        + "last_submission_id BIGINT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                        + "UNIQUE KEY uq_accounts_platform_handle (platform, handle)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                st.executeUpdate("CREATE TABLE IF NOT EXISTS submissions ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                        + "external_submission_id BIGINT NULL, "
                        + "account_id BIGINT NULL, "
                        + "handle VARCHAR(128) NULL, "
                        + "platform VARCHAR(32) NOT NULL, "
                        + "contest_id BIGINT NULL, "
                        + "problem_index VARCHAR(32) NULL, "
                        + "language_name VARCHAR(128) NULL, "
                        + "verdict VARCHAR(64) NULL, "
                        + "creation_time BIGINT NULL, "
                        + "source_code MEDIUMTEXT NOT NULL, "
                        + "source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL', "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "UNIQUE KEY uq_submissions_platform_external (platform, external_submission_id), "
                        + "INDEX idx_submissions_account (account_id), "
                        + "CONSTRAINT fk_submissions_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                st.executeUpdate("CREATE TABLE IF NOT EXISTS analyses ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                        + "submission_id BIGINT NOT NULL, "
                        + "model VARCHAR(128) NULL, "
                        + "ds_and_algos TEXT NULL, "
                        + "used_ai VARCHAR(64) NULL, "
                        + "confidence DOUBLE NOT NULL DEFAULT 0, "
                        + "ds_score DOUBLE NOT NULL DEFAULT 0, "
                        + "algo_score DOUBLE NOT NULL DEFAULT 0, "
                        + "ai_score DOUBLE NOT NULL DEFAULT 0, "
                        + "analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "INDEX idx_analyses_submission (submission_id), "
                        + "CONSTRAINT fk_analyses_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                
                statusMessage = "MySQL đã kết nối";
            }

            available = true;
        } catch (SQLException ex) {
            available = false;
            statusMessage = "CSDL chưa sẵn sàng: " + ex.getMessage();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public long saveCodeforcesAccount(String handle) throws SQLException {
        String sql = "INSERT INTO accounts (handle, platform) VALUES (?, 'Codeforces') "
                + (isSQLite ? "ON CONFLICT(platform, handle) DO NOTHING" : "ON DUPLICATE KEY UPDATE handle=handle");
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, handle);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return getAccountId("Codeforces", handle);
    }

    public void updateLastSubmissionId(long accountId, long lastSubmissionId) throws SQLException {
        String sql = "UPDATE accounts SET last_submission_id = ? WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lastSubmissionId);
            ps.setLong(2, accountId);
            ps.executeUpdate();
        }
    }

    public List<Models.Account> loadCodeforcesAccounts() throws SQLException {
        List<Models.Account> list = new ArrayList<>();
        String sql = "SELECT id, handle, platform, last_submission_id FROM accounts WHERE platform = 'Codeforces'";
        try (Connection conn = openConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Models.Account acc = new Models.Account();
                acc.id = rs.getLong("id");
                acc.handle = rs.getString("handle");
                acc.platform = rs.getString("platform");
                acc.lastSubmissionId = rs.getLong("last_submission_id");
                list.add(acc);
            }
        }
        return list;
    }

    public long saveSubmission(Models.CodeforcesSubmission sub) throws SQLException {
        String sql = "INSERT INTO submissions (external_submission_id, account_id, handle, platform, contest_id, problem_index, language_name, verdict, creation_time, source_code, source_type) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CRAWLED') "
                + (isSQLite ? "ON CONFLICT(platform, external_submission_id) DO NOTHING" : "ON DUPLICATE KEY UPDATE external_submission_id=external_submission_id");
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, sub.submissionId);
            ps.setLong(2, sub.accountId);
            ps.setString(3, sub.handle);
            ps.setString(4, "Codeforces");
            ps.setLong(5, sub.contestId);
            ps.setString(6, sub.problemIndex);
            ps.setString(7, sub.language);
            ps.setString(8, sub.verdict);
            ps.setLong(9, sub.creationTimeSeconds);
            ps.setString(10, sub.sourceCode);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return getSubmissionDbId(sub.submissionId);
    }

    private long getAccountId(String platform, String handle) throws SQLException {
        String sql = "SELECT id FROM accounts WHERE platform = ? AND handle = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, platform);
            ps.setString(2, handle);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        }
        return -1;
    }

    private long getSubmissionDbId(long externalId) throws SQLException {
        String sql = "SELECT id FROM submissions WHERE platform = 'Codeforces' AND external_submission_id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        }
        return -1;
    }

    public List<Models.CodeforcesSubmission> loadSubmissionsByHandle(String handle) throws SQLException {
        List<Models.CodeforcesSubmission> list = new ArrayList<>();
        String sql = "SELECT s.id, external_submission_id, account_id, handle, platform, contest_id, problem_index, "
                   + "language_name, verdict, creation_time, source_code, (SELECT 1 FROM analyses a WHERE a.submission_id = s.id LIMIT 1) as is_analyzed "
                   + "FROM submissions s WHERE platform = 'Codeforces' AND handle = ? ORDER BY external_submission_id DESC";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, handle);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Models.CodeforcesSubmission sub = new Models.CodeforcesSubmission();
                    sub.databaseId = rs.getLong("id");
                    sub.submissionId = rs.getLong("external_submission_id");
                    sub.accountId = rs.getLong("account_id");
                    sub.handle = rs.getString("handle");
                    sub.contestId = rs.getLong("contest_id");
                    sub.problemIndex = rs.getString("problem_index");
                    sub.language = rs.getString("language_name");
                    sub.verdict = rs.getString("verdict");
                    sub.creationTimeSeconds = rs.getLong("creation_time");
                    sub.sourceCode = rs.getString("source_code");
                    sub.isAnalyzed = rs.getObject("is_analyzed") != null;
                    list.add(sub);
                }
            }
        }
        return list;
    }

    public void saveAnalysis(long submissionDbId, Models.AnalysisResult result, String model) throws SQLException {
        String sql = "INSERT INTO analyses "
                + "(submission_id, model, ds_and_algos, used_ai, confidence, ds_score, algo_score, ai_score) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, submissionDbId);
            ps.setString(2, model);
            ps.setString(3, result.dsAndAlgos);
            ps.setString(4, result.usedAI);
            ps.setDouble(5, result.confidence);
            ps.setDouble(6, result.dsScore);
            ps.setDouble(7, result.algoScore);
            ps.setDouble(8, result.aiScore);
            ps.executeUpdate();
        }
    }

    public Models.AnalysisResult getAnalysis(long submissionDbId) throws SQLException {
        String sql = "SELECT ds_and_algos, used_ai, confidence, ds_score, algo_score, ai_score " +
                     "FROM analyses WHERE submission_id = ? LIMIT 1";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, submissionDbId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Models.AnalysisResult res = new Models.AnalysisResult();
                    res.submissionId = submissionDbId;
                    res.dsAndAlgos = rs.getString(1);
                    res.usedAI = rs.getString(2);
                    res.confidence = rs.getDouble(3);
                    res.dsScore = rs.getDouble(4);
                    res.algoScore = rs.getDouble(5);
                    res.aiScore = rs.getDouble(6);
                    return res;
                }
            }
        }
        return null;
    }

    public Models.AccountEvaluation evaluateAccount(String handle) throws SQLException {
        Models.AccountEvaluation eval = new Models.AccountEvaluation();
        eval.handle = handle;

        String countSql = "SELECT COUNT(*) FROM submissions WHERE handle = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setString(1, handle);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) eval.totalSubmissions = rs.getInt(1);
            }
        }

        String statsSql = "SELECT COUNT(*), AVG(ds_score), AVG(algo_score), AVG(ai_score) " +
                "FROM analyses a JOIN submissions s ON a.submission_id = s.id " +
                "WHERE s.handle = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(statsSql)) {
            ps.setString(1, handle);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    eval.totalAnalyzed = rs.getInt(1);
                    eval.avgDsScore = rs.getDouble(2);
                    eval.avgAlgoScore = rs.getDouble(3);
                    eval.avgAiScore = rs.getDouble(4);
                }
            }
        }

        String tagsSql = "SELECT ds_and_algos FROM analyses a JOIN submissions s ON a.submission_id = s.id WHERE s.handle = ?";
        java.util.Map<String, Integer> dsCount = new java.util.HashMap<>();
        java.util.Map<String, Integer> algCount = new java.util.HashMap<>();
        
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(tagsSql)) {
            ps.setString(1, handle);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String raw = rs.getString(1);
                    if (raw == null) continue;
                    
                    String[] parts = raw.split(";");
                    for (String p : parts) {
                        p = p.trim();
                        if (p.startsWith("ds:")) {
                            String list = p.substring(3).trim();
                            for (String item : list.split(",")) {
                                String t = item.trim().toLowerCase();
                                if (!t.isEmpty() && !t.equals("basic types")) {
                                    dsCount.put(t, dsCount.getOrDefault(t, 0) + 1);
                                }
                            }
                        } else if (p.startsWith("alg:")) {
                            String list = p.substring(4).trim();
                            for (String item : list.split(",")) {
                                String t = item.trim().toLowerCase();
                                if (!t.isEmpty() && !t.equals("implementation/brute force")) {
                                    algCount.put(t, algCount.getOrDefault(t, 0) + 1);
                                }
                            }
                        }
                    }
                }
            }
        }

        eval.topDs = dsCount.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.joining(", "));
        
        eval.topAlgos = algCount.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.joining(", "));

        return eval;
    }
    public void deleteSubmission(long submissionDbId) throws SQLException {
        String sql = "DELETE FROM submissions WHERE id = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, submissionDbId);
            ps.executeUpdate();
        }
    }

    public void deleteAccount(long accountId) throws SQLException {
        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete submissions first (which will cascade to analyses)
                String deleteSubmissionsSql = "DELETE FROM submissions WHERE account_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSubmissionsSql)) {
                    ps.setLong(1, accountId);
                    ps.executeUpdate();
                }
                
                // Delete the account
                String deleteAccountSql = "DELETE FROM accounts WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteAccountSql)) {
                    ps.setLong(1, accountId);
                    ps.executeUpdate();
                }
                
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }


    private Connection openConnection() throws SQLException {
        if (isSQLite) {
            return DriverManager.getConnection(url);
        } else {
            return DriverManager.getConnection(url, user, password);
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

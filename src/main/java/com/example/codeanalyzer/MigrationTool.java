package com.example.codeanalyzer;

import java.sql.*;

/**
 * Run this tool to migrate data from MySQL to SQLite.
 * Usage: Change the MySQL credentials below and run.
 */
public class MigrationTool {
    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/code_analyzer?useSSL=false&serverTimezone=UTC";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASS = "";

    private static final String SQLITE_URL = "jdbc:sqlite:code_analyzer.db";

    public static void main(String[] args) {
        System.out.println("Bắt đầu di chuyển dữ liệu từ MySQL sang SQLite...");

        try (Connection mysql = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS);
                Connection sqlite = DriverManager.getConnection(SQLITE_URL)) {

            sqlite.setAutoCommit(false);
            
            // 0. Create tables in SQLite if not exist
            try (Statement st = sqlite.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS accounts ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "handle TEXT NOT NULL, "
                        + "platform TEXT NOT NULL, "
                        + "last_submission_id INTEGER, "
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                        + "UNIQUE (platform, handle))");

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
                        + "FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE SET NULL)");

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
                        + "FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE)");
            }

            // 1. Migrate accounts
            System.out.println("Migrating accounts...");
            copyTable(mysql, sqlite, "accounts",
                    "INSERT INTO accounts (id, handle, platform, last_submission_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)");

            // 2. Migrate submissions
            System.out.println("Migrating submissions...");
            copyTable(mysql, sqlite, "submissions",
                    "INSERT INTO submissions (id, external_submission_id, account_id, handle, platform, contest_id, problem_index, language_name, verdict, creation_time, source_code, source_type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            // 3. Migrate analyses
            System.out.println("Migrating analyses...");
            copyTable(mysql, sqlite, "analyses",
                    "INSERT INTO analyses (id, submission_id, model, ds_and_algos, used_ai, confidence, ds_score, algo_score, ai_score, analyzed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            sqlite.commit();
            System.out.println("DI CHUYỂN THÀNH CÔNG!");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Lỗi di chuyển: " + e.getMessage());
        }
    }

    private static void copyTable(Connection src, Connection dest, String tableName, String insertSql)
            throws SQLException {
        try (Statement st = src.createStatement();
                ResultSet rs = st.executeQuery("SELECT * FROM " + tableName);
                PreparedStatement ps = dest.prepareStatement(insertSql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            int count = 0;
            while (rs.next()) {
                for (int i = 1; i <= colCount; i++) {
                    ps.setObject(i, rs.getObject(i));
                }
                ps.addBatch();
                count++;
                if (count % 100 == 0)
                    ps.executeBatch();
            }
            ps.executeBatch();
            System.out.println("Copied " + count + " rows from " + tableName);
        }
    }
}

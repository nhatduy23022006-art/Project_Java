package com.example.codeanalyzer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.EventObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private final AnalyzerService analyzer;
    private final DatabaseService database;
    private final CodeforcesCrawlerService codeforcesCrawler;
    private ScheduledExecutorService scheduler;
    private final JTextArea resultArea = new JTextArea();
    private JTable accountTable;
    private JFrame mainFrame;
    private final List<CodeEntry> queue = new ArrayList<>();
    private final Object queueLock = new Object();
    private final DefaultTableModel queueTableModel = new DefaultTableModel(
            new String[]{"#", "Trạng thái", "Xem trước", "CTDL", "Thuật toán", "AI", "Dùng AI", "Độ tin cậy", "Xem"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == getColumnCount() - 1;
        }
    };
    private final DefaultTableModel accountTableModel = new DefaultTableModel(
            new String[]{"ID", "Platform", "Handle", "Last submission", "Xóa"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 4;
        }
    };

    private final List<Models.CodeforcesSubmission> currentSubmissions = new ArrayList<>();
    private final DefaultTableModel submissionTableModel = new DefaultTableModel(
            new String[]{"ID", "Contest", "Bài", "Ngôn ngữ", "Kết quả", "Xem Code", "Phân tích AI", "Xóa"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 5 || column == 6 || column == 7;
        }
    };


    private Main(AnalyzerService analyzer, DatabaseService database) {
        this.analyzer = analyzer;
        this.database = database;
        this.codeforcesCrawler = new CodeforcesCrawlerService(database);
    }

    private void createAndShowGUI() {
        mainFrame = new JFrame("Phân tích mã nguồn");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1200, 850);
        mainFrame.setLocationRelativeTo(null);

        JTable submissionTable = new JTable(submissionTableModel);
        submissionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        submissionTable.getColumnModel().getColumn(5).setCellRenderer(new ButtonColumnRenderer("Xem Code"));
        submissionTable.getColumnModel().getColumn(5).setCellEditor(new ButtonColumnEditor(submissionTable, this::showSourceCodeDialog));
        submissionTable.getColumnModel().getColumn(6).setCellRenderer(new ButtonColumnRenderer("Phân tích AI"));
        submissionTable.getColumnModel().getColumn(6).setCellEditor(new ButtonColumnEditor(submissionTable, this::addSubmissionToQueue));
        submissionTable.getColumnModel().getColumn(7).setCellRenderer(new ButtonColumnRenderer("Xóa"));
        submissionTable.getColumnModel().getColumn(7).setCellEditor(new ButtonColumnEditor(submissionTable, this::deleteSubmissionAction));

        JScrollPane submissionScroll = new JScrollPane(submissionTable);

        resultArea.setEditable(false);
        resultArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        resultArea.setLineWrap(false);
        JScrollPane resultScroll = new JScrollPane(resultArea);

        accountTable = new JTable(accountTableModel);
        accountTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountTable.getColumnModel().getColumn(4).setCellRenderer(new ButtonColumnRenderer("Xóa"));
        accountTable.getColumnModel().getColumn(4).setCellEditor(new ButtonColumnEditor(accountTable, this::deleteAccountAction));

        accountTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = accountTable.getSelectedRow();
                if (row >= 0) {
                    String handle = (String) accountTableModel.getValueAt(row, 2);
                    loadSubmissions(handle);
                }
            }
        });
        JScrollPane accountScroll = new JScrollPane(accountTable);

        accountScroll.setPreferredSize(new Dimension(360, 130));

        JTable queueTable = new JTable(queueTableModel);
        queueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        queueTable.getColumnModel().getColumn(queueTableModel.getColumnCount() - 1)
            .setCellRenderer(new ButtonColumnRenderer("Xem"));
        queueTable.getColumnModel().getColumn(queueTableModel.getColumnCount() - 1)
            .setCellEditor(new ButtonColumnEditor(queueTable, this::showEntryDialog));
        JScrollPane queueScroll = new JScrollPane(queueTable);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, queueScroll, resultScroll);
        rightSplit.setResizeWeight(0.55);
        rightSplit.setDividerLocation(350);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, submissionScroll, rightSplit);
        mainSplit.setResizeWeight(0.5);
        mainSplit.setDividerLocation(600);

        JLabel title = new JLabel("Hàng đợi phân tích mã nguồn bằng AI");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));

        JTextField handleField = new JTextField(18);
        JButton saveAccountBtn = new JButton("Lưu nick Codeforces");
        JButton crawlBtn = new JButton("Crawl Codeforces");
        JCheckBox autoCrawlCheck = new JCheckBox("Tự động Crawl (12h)");
        JButton evaluateBtn = new JButton("Đánh giá năng lực");
        JButton checkBtn = new JButton("Kiểm tra hàng đợi");
        JButton clearQueueBtn = new JButton("Xóa hàng đợi");

        JTextField modelField = new JTextField(analyzer.getGeminiModel(), 18);
        modelField.setEditable(false);
        JLabel dbStatusLabel = new JLabel(formatDbStatus(database.getStatusMessage()));
        dbStatusLabel.setToolTipText(database.getStatusMessage());
        dbStatusLabel.setForeground(database.isAvailable() ? new Color(0, 120, 0) : new Color(170, 70, 0));

        JPanel accountActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        accountActions.add(new JLabel("Codeforces handle:"));
        accountActions.add(handleField);
        accountActions.add(saveAccountBtn);
        accountActions.add(crawlBtn);
        accountActions.add(autoCrawlCheck);
        accountActions.add(evaluateBtn);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(checkBtn);
        actions.add(clearQueueBtn);
        actions.add(new JLabel("Mô hình:"));
        actions.add(modelField);

        saveAccountBtn.addActionListener((ActionEvent e) -> {
            if (!database.isAvailable()) {
                JOptionPane.showMessageDialog(mainFrame, "MySQL chưa kết nối, không thể lưu nick Codeforces.");
                return;
            }
            String handle = handleField.getText().trim();
            if (handle.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Vui lòng nhập nick Codeforces.");
                return;
            }
            try {
                long accountId = database.saveCodeforcesAccount(handle);
                handleField.setText("");
                refreshAccounts();
                resultArea.setText("Đã lưu nick Codeforces " + handle + " với account_id=" + accountId + ".\n");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Không lưu được nick Codeforces: " + ex.getMessage());
            }
        });

        crawlBtn.addActionListener((ActionEvent e) -> {
            if (!database.isAvailable()) {
                JOptionPane.showMessageDialog(mainFrame, "MySQL chưa kết nối, không thể crawl Codeforces.");
                return;
            }

            crawlBtn.setEnabled(false);
            saveAccountBtn.setEnabled(false);
            resultArea.setText("Đang mở trình duyệt và crawl Codeforces...\n");

            SwingWorker<List<Models.CodeforcesSubmission>, String> worker = new SwingWorker<>() {
                @Override
                protected List<Models.CodeforcesSubmission> doInBackground() throws Exception {
                    return codeforcesCrawler.crawlAllAccounts(mainFrame);
                }

                @Override
                protected void done() {
                    try {
                        List<Models.CodeforcesSubmission> crawled = get();
                        for (Models.CodeforcesSubmission submission : crawled) {
                            CodeEntry entry = new CodeEntry(nextId(), submission.sourceCode);
                            entry.submissionDbId = submission.databaseId;
                            entry.usedAi = "Codeforces " + submission.handle + " #" + submission.submissionId;
                            synchronized (queueLock) {
                                queue.add(entry);
                            }
                            queueTableModel.addRow(toRow(entry));
                        }
                        refreshAccounts();
                        resultArea.setText("Đã crawl " + crawled.size() + " source code mới từ Codeforces.\n"
                                + "Các code mới đã được thêm vào hàng đợi. Bấm Kiểm tra hàng đợi để phân tích bằng Gemini.\n");
                    } catch (Exception ex) {
                        resultArea.setText("Crawl Codeforces thất bại: " + rootMessage(ex) + "\n");
                        JOptionPane.showMessageDialog(mainFrame, "Crawl Codeforces thất bại: " + rootMessage(ex));
                    } finally {
                        crawlBtn.setEnabled(true);
                        saveAccountBtn.setEnabled(true);
                    }
                }
            };

            worker.execute();
        });

        autoCrawlCheck.addActionListener(e -> {
            if (autoCrawlCheck.isSelected()) {
                startAutoCrawl(crawlBtn);
            } else {
                stopAutoCrawl();
            }
        });

        evaluateBtn.addActionListener(e -> {
            int row = accountTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(mainFrame, "Vui lòng chọn một tài khoản ở danh sách phía trên để đánh giá.");
                return;
            }
            String handle = (String) accountTableModel.getValueAt(row, 2);
            try {
                Models.AccountEvaluation eval = database.evaluateAccount(handle);
                showEvaluationDialog(mainFrame, eval);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Lỗi khi đánh giá tài khoản: " + ex.getMessage());
            }
        });



        checkBtn.addActionListener((ActionEvent e) -> {
            List<Integer> pendingRows = new ArrayList<>();
            synchronized (queueLock) {
                for (int i = 0; i < queue.size(); i++) {
                    if (queue.get(i).status == CodeStatus.PENDING) {
                        pendingRows.add(i);
                    }
                }
            }

            if (pendingRows.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "Không có mã nguồn nào đang chờ kiểm tra.");
                return;
            }

            checkBtn.setEnabled(false);
            resultArea.setText("Đang phân tích " + pendingRows.size() + " mục đang chờ...\n");

            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    for (int rowIndex : pendingRows) {
                        CodeEntry entry;
                        synchronized (queueLock) {
                            if (rowIndex < 0 || rowIndex >= queue.size()) {
                                continue;
                            }
                            entry = queue.get(rowIndex);
                            entry.status = CodeStatus.ANALYZING;
                        }
                        publish("START:" + rowIndex);

                        try {
                            Models.Submission tempSubmission = new Models.Submission();
                            tempSubmission.submissionId = entry.id;
                            tempSubmission.code = entry.code;

                            Models.AnalysisResult result = analyzer.analyzeCode(tempSubmission);
                            synchronized (queueLock) {
                                entry.status = CodeStatus.DONE;
                                entry.ds = result.dsAndAlgos;
                                entry.algo = String.format("%.2f", result.algoScore);
                                entry.ai = String.format("%.2f", result.aiScore);
                                entry.usedAi = result.usedAI;
                                entry.confidence = String.format("%.1f%%", result.confidence * 100);
                            }
                            if (database.isAvailable() && entry.submissionDbId > 0) {
                                try {
                                    database.saveAnalysis(entry.submissionDbId, result, analyzer.getGeminiModel());
                                } catch (Exception dbEx) {
                                    synchronized (queueLock) {
                                        entry.usedAi = entry.usedAi + " | Lỗi lưu CSDL: " + dbEx.getMessage();
                                    }
                                }
                            }
                            publish("DONE:" + rowIndex);
                        } catch (Exception ex) {
                            synchronized (queueLock) {
                                entry.status = CodeStatus.ERROR;
                                entry.usedAi = ex.getMessage();
                                entry.confidence = "0.0%";
                            }
                            publish("ERROR:" + rowIndex + ":" + ex.getMessage());
                        }
                    }
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String chunk : chunks) {
                        if (chunk.startsWith("START:")) {
                            int rowIndex = Integer.parseInt(chunk.substring(6));
                            updateRow(rowIndex);
                            resultArea.setText("Đang phân tích mục #" + queue.get(rowIndex).id + " trong hàng đợi...\n");
                        } else if (chunk.startsWith("DONE:")) {
                            int rowIndex = Integer.parseInt(chunk.substring(5));
                            updateRow(rowIndex);
                        } else if (chunk.startsWith("ERROR:")) {
                            int first = chunk.indexOf(':', 6);
                            int rowIndex = Integer.parseInt(chunk.substring(6, first));
                            updateRow(rowIndex);
                        }
                    }
                }

                @Override
                protected void done() {
                    refreshTable();
                    int row = accountTable.getSelectedRow();
                    if (row >= 0) {
                        String handle = (String) accountTableModel.getValueAt(row, 2);
                        loadSubmissions(handle);
                    }
                    resultArea.append("\nHoàn tất. Các mục đang chờ đã được kiểm tra.\n");
                    checkBtn.setEnabled(true);
                }
            };

            worker.execute();
        });

        clearQueueBtn.addActionListener((ActionEvent e) -> {
            synchronized (queueLock) {
                queue.clear();
            }
            queueTableModel.setRowCount(0);
            resultArea.setText("");
        });

        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(dbStatusLabel, BorderLayout.EAST);

        JPanel accountsPanel = new JPanel(new BorderLayout(0, 6));
        accountsPanel.add(accountActions, BorderLayout.NORTH);
        accountsPanel.add(accountScroll, BorderLayout.CENTER);

        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(header, BorderLayout.NORTH);
        north.add(accountsPanel, BorderLayout.CENTER);
        north.add(actions, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(north, BorderLayout.NORTH);
        root.add(mainSplit, BorderLayout.CENTER);

        mainFrame.setContentPane(root);
        refreshAccounts();
        mainFrame.setVisible(true);
    }

    private void loadSubmissions(String handle) {
        submissionTableModel.setRowCount(0);
        currentSubmissions.clear();
        if (!database.isAvailable()) return;
        try {
            List<Models.CodeforcesSubmission> list = database.loadSubmissionsByHandle(handle);
            currentSubmissions.addAll(list);
            for (Models.CodeforcesSubmission sub : list) {
                submissionTableModel.addRow(new Object[]{
                    sub.submissionId, sub.contestId, sub.problemIndex, sub.language, sub.verdict, "Xem Code", sub.isAnalyzed ? "Xem phân tích" : "Phân tích AI", "Xóa"
                });
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Lỗi tải danh sách bài nộp: " + ex.getMessage());
        }
    }

    private void showSourceCodeDialog(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= currentSubmissions.size()) return;
        Models.CodeforcesSubmission sub = currentSubmissions.get(rowIndex);
        JDialog dialog = new JDialog((Frame) null, "Mã nguồn #" + sub.submissionId, true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(null);
        JTextArea area = new JTextArea(sub.sourceCode);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setEditable(false);
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private void addSubmissionToQueue(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= currentSubmissions.size()) return;
        Models.CodeforcesSubmission sub = currentSubmissions.get(rowIndex);
        
        if (sub.isAnalyzed) {
            try {
                Models.AnalysisResult result = database.getAnalysis(sub.databaseId);
                if (result != null) {
                    CodeEntry entry = new CodeEntry(nextId(), sub.sourceCode);
                    entry.submissionDbId = sub.databaseId;
                    entry.status = CodeStatus.DONE;
                    entry.ds = result.dsAndAlgos;
                    entry.algo = String.format("%.2f", result.algoScore);
                    entry.ai = String.format("%.2f", result.aiScore);
                    entry.usedAi = result.usedAI;
                    entry.confidence = String.format("%.1f%%", result.confidence * 100);
                    
                    synchronized (queueLock) {
                        queue.add(entry);
                    }
                    queueTableModel.addRow(toRow(entry));
                    resultArea.setText("Đã tải kết quả phân tích cũ cho bài #" + sub.submissionId + " từ CSDL.\n");
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Lỗi khi tải phân tích cũ: " + ex.getMessage());
            }
        }

        CodeEntry entry = new CodeEntry(nextId(), sub.sourceCode);
        entry.submissionDbId = sub.databaseId;
        entry.usedAi = "Codeforces " + sub.handle + " #" + sub.submissionId;
        synchronized (queueLock) {
            queue.add(entry);
        }
        queueTableModel.addRow(toRow(entry));
    }

    private void deleteAccountAction(int rowIndex) {
        long accountId = (long) accountTableModel.getValueAt(rowIndex, 0);
        String handle = (String) accountTableModel.getValueAt(rowIndex, 2);
        
        int choice = JOptionPane.showConfirmDialog(mainFrame, 
            "Bạn có chắc chắn muốn xóa tài khoản '" + handle + "'?\nToàn bộ bài nộp và phân tích liên quan sẽ bị xóa.", 
            "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
            
        if (choice == JOptionPane.YES_OPTION) {
            try {
                database.deleteAccount(accountId);
                refreshAccounts();
                submissionTableModel.setRowCount(0);
                currentSubmissions.clear();
                resultArea.setText("Đã xóa tài khoản " + handle + ".\n");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Lỗi khi xóa tài khoản: " + ex.getMessage());
            }
        }
    }

    private void deleteSubmissionAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= currentSubmissions.size()) return;
        Models.CodeforcesSubmission sub = currentSubmissions.get(rowIndex);
        
        int choice = JOptionPane.showConfirmDialog(mainFrame, 
            "Bạn có chắc chắn muốn xóa bài nộp #" + sub.submissionId + "?", 
            "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
            
        if (choice == JOptionPane.YES_OPTION) {
            try {
                database.deleteSubmission(sub.databaseId);
                loadSubmissions(sub.handle);
                resultArea.setText("Đã xóa bài nộp #" + sub.submissionId + ".\n");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Lỗi khi xóa bài nộp: " + ex.getMessage());
            }
        }
    }


    private void refreshAccounts() {
        accountTableModel.setRowCount(0);
        if (!database.isAvailable()) {
            return;
        }
        try {
            for (Models.Account account : database.loadCodeforcesAccounts()) {
                accountTableModel.addRow(new Object[]{
                        account.id,
                        account.platform,
                        account.handle,
                        account.lastSubmissionId > 0 ? account.lastSubmissionId : "",
                        "Xóa"
                });

            }
        } catch (Exception ex) {
            accountTableModel.addRow(new Object[]{"", "Lỗi tải danh sách", ex.getMessage(), ""});
        }
    }

    private void refreshTable() {
        synchronized (queueLock) {
            queueTableModel.setRowCount(0);
            for (CodeEntry entry : queue) {
                queueTableModel.addRow(toRow(entry));
            }
        }
    }

    private void updateRow(int rowIndex) {
        synchronized (queueLock) {
            if (rowIndex < 0 || rowIndex >= queue.size()) {
                return;
            }
            CodeEntry entry = queue.get(rowIndex);
            Object[] row = toRow(entry);
            for (int i = 0; i < row.length; i++) {
                queueTableModel.setValueAt(row[i], rowIndex, i);
            }
        }
    }

    private Object[] toRow(CodeEntry entry) {
        return new Object[]{
                entry.id,
                entry.status.label,
                preview(entry.code),
                entry.ds,
                entry.algo,
                entry.ai,
                entry.usedAi,
                entry.confidence,
                "Xem"
        };
    }

    private void showEntryDialog(int rowIndex) {
        CodeEntry entry;
        synchronized (queueLock) {
            if (rowIndex < 0 || rowIndex >= queue.size()) {
                return;
            }
            entry = queue.get(rowIndex);
        }

        JDialog dialog = new JDialog((Frame) null, "Bài nộp #" + entry.id, true);
        dialog.setSize(900, 700);
        dialog.setLocationRelativeTo(null);

        JTextArea detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        detailArea.setText(buildDetailText(entry));

        dialog.add(new JScrollPane(detailArea), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private String buildDetailText(CodeEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(entry.id).append("\n");
        if (entry.submissionDbId > 0) {
            sb.append("MySQL submission_id: ").append(entry.submissionDbId).append("\n");
        }
        sb.append("Trạng thái: ").append(entry.status.label).append("\n");
        sb.append("Dùng AI: ").append(entry.usedAi).append("\n");
        sb.append("Độ tin cậy: ").append(entry.confidence).append("\n");
        sb.append("CTDL: ").append(entry.ds).append("\n");
        sb.append("Thuật toán: ").append(entry.algo).append("\n");
        sb.append("AI: ").append(entry.ai).append("\n\n");
        sb.append("=== MÃ NGUỒN ===\n");
        sb.append(entry.code);
        return sb.toString();
    }

    private String preview(String code) {
        if (code == null) {
            return "";
        }
        String singleLine = code.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() <= 120) {
            return singleLine;
        }
        return singleLine.substring(0, 117) + "...";
    }

    private String formatDbStatus(String message) {
        String text = message == null || message.isBlank() ? "Không rõ trạng thái MySQL" : message;
        if (text.length() > 55) {
            text = text.substring(0, 52) + "...";
        }
        return "CSDL: " + text;
    }

    private String rootMessage(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private long nextId() {
        synchronized (queueLock) {
            return queue.isEmpty() ? 1 : queue.get(queue.size() - 1).id + 1;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String geminiKey = System.getenv("GEMINI_API_KEY");
            AnalyzerService analyzer = new AnalyzerService(geminiKey);
            DatabaseService database = DatabaseService.fromEnvironment();
            database.initialize();
            new Main(analyzer, database).createAndShowGUI();
        });
    }

    private enum CodeStatus {
        PENDING("Đang chờ"),
        ANALYZING("Đang phân tích"),
        DONE("Hoàn tất"),
        ERROR("Lỗi");

        private final String label;

        CodeStatus(String label) {
            this.label = label;
        }
    }

    private static final class CodeEntry {
        private final long id;
        private final String code;
        private long submissionDbId = -1;
        private CodeStatus status = CodeStatus.PENDING;
        private String ds = "";
        private String algo = "";
        private String ai = "";
        private String usedAi = "";
        private String confidence = "";

        private CodeEntry(long id, String code) {
            this.id = id;
            this.code = code;
        }
    }

    @SuppressWarnings("serial")
    private static final class ButtonColumnRenderer extends JButton implements TableCellRenderer {
        private static final long serialVersionUID = 1L;

        private ButtonColumnRenderer(String text) {
            setText(text);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "Xem" : value.toString());
            return this;
        }
    }

    @SuppressWarnings("serial")
    private static final class ButtonColumnEditor extends AbstractCellEditor implements TableCellEditor {
        private static final long serialVersionUID = 1L;
        private final JButton button = new JButton("Xem");

        private int row = -1;

        private ButtonColumnEditor(JTable table, RowAction action) {
            button.addActionListener(e -> {
                fireEditingStopped();
                if (row >= 0) {
                    action.run(row);
                }
            });
        }

        @Override
        public Object getCellEditorValue() {
            return "Xem";
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            return true;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = table.convertRowIndexToModel(row);
            button.setText(value == null ? "Xem" : value.toString());
            return button;
        }
    }

    @FunctionalInterface
    private interface RowAction {
        void run(int rowIndex);
    }
    private void startAutoCrawl(JButton crawlBtn) {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            SwingUtilities.invokeLater(() -> {
                if (crawlBtn.isEnabled()) {
                    crawlBtn.doClick();
                }
            });
        }, 12, 12, TimeUnit.HOURS);
        System.out.println("Đã kích hoạt chế độ Tự động Crawl (mỗi 12 giờ)");
    }

    private void stopAutoCrawl() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        System.out.println("Đã tắt chế độ Tự động Crawl");
    }

    private void showEvaluationDialog(JFrame parent, Models.AccountEvaluation eval) {
        StringBuilder sb = new StringBuilder();
        sb.append("BÁO CÁO ĐÁNH GIÁ NĂNG LỰC ACCOUNT: ").append(eval.handle).append("\n\n");
        sb.append("- Tổng số bài đã crawl: ").append(eval.totalSubmissions).append("\n");
        sb.append("- Số bài đã phân tích AI: ").append(eval.totalAnalyzed).append("\n\n");
        
        if (eval.totalAnalyzed == 0) {
            sb.append("Chưa có dữ liệu phân tích AI cho tài khoản này.\n");
            sb.append("Vui lòng phân tích ít nhất một bài nộp để xem đánh giá.");
        } else {
            sb.append(String.format("- Điểm CTDL trung bình: %.2f/10\n", eval.avgDsScore));
            sb.append(String.format("- Điểm Thuật toán trung bình: %.2f/10\n", eval.avgAlgoScore));
            sb.append(String.format("- Tỷ lệ nghi vấn dùng AI: %.2f/10\n\n", eval.avgAiScore));
            
            sb.append("- CTDL hay dùng: ").append(eval.topDs.isEmpty() ? "Cơ bản" : eval.topDs).append("\n");
            sb.append("- Thuật toán hay dùng: ").append(eval.topAlgos.isEmpty() ? "Brute force / Implementation" : eval.topAlgos).append("\n\n");
            
            sb.append("KẾT LUẬN:\n");
            if (eval.avgAiScore >= 7.0) {
                sb.append(">>> CẢNH BÁO: Tài khoản này có dấu hiệu lạm dụng AI rất cao.");
            } else if (eval.avgAiScore >= 4.0) {
                sb.append(">>> CHÚ Ý: Có sử dụng AI hỗ trợ trong quá trình làm bài.");
            } else {
                sb.append(">>> ĐÁNH GIÁ: Khả năng tự code tốt, ít phụ thuộc vào AI.");
            }
        }

        JTextArea area = new JTextArea(sb.toString());
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 14));
        area.setMargin(new Insets(10,10,10,10));
        
        JDialog dialog = new JDialog(parent, "Đánh giá năng lực - " + eval.handle, true);
        dialog.add(new JScrollPane(area));
        dialog.setSize(500, 450);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}

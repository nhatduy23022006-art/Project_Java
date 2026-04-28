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

public class Main {
    private final AnalyzerService analyzer;
    private final List<CodeEntry> queue = new ArrayList<>();
    private final Object queueLock = new Object();
    private final DefaultTableModel queueTableModel = new DefaultTableModel(
            new String[]{"#", "Status", "Preview", "DS", "Algo", "AI", "Used AI", "Confidence", "View"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == getColumnCount() - 1;
        }
    };

    private Main(AnalyzerService analyzer) {
        this.analyzer = analyzer;
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame("Code Analyzer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 850);
        frame.setLocationRelativeTo(null);

        JTextArea codeInputArea = new JTextArea();
        codeInputArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        codeInputArea.setLineWrap(false);
        JScrollPane inputScroll = new JScrollPane(codeInputArea);

        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        resultArea.setLineWrap(false);
        JScrollPane resultScroll = new JScrollPane(resultArea);

        JTable queueTable = new JTable(queueTableModel);
        queueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        queueTable.getColumnModel().getColumn(queueTableModel.getColumnCount() - 1)
            .setCellRenderer(new ButtonColumnRenderer("View"));
        queueTable.getColumnModel().getColumn(queueTableModel.getColumnCount() - 1)
            .setCellEditor(new ButtonColumnEditor(queueTable, this::showEntryDialog));
        JScrollPane queueScroll = new JScrollPane(queueTable);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, queueScroll, resultScroll);
        rightSplit.setResizeWeight(0.55);
        rightSplit.setDividerLocation(350);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputScroll, rightSplit);
        mainSplit.setResizeWeight(0.42);
        mainSplit.setDividerLocation(480);

        JLabel title = new JLabel("Manual AI Code Analyzer Queue");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));

        JButton addBtn = new JButton("Add");
        JButton checkBtn = new JButton("Check Pending");
        JButton clearBtn = new JButton("Clear Input");
        JButton clearQueueBtn = new JButton("Clear Queue");

        JTextField modelField = new JTextField(System.getenv("GEMINI_MODEL") != null ? System.getenv("GEMINI_MODEL") : "gemini-2.5-flash", 18);
        modelField.setEditable(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(addBtn);
        actions.add(checkBtn);
        actions.add(clearBtn);
        actions.add(clearQueueBtn);
        actions.add(new JLabel("Model:"));
        actions.add(modelField);

        addBtn.addActionListener((ActionEvent e) -> {
            String code = codeInputArea.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please paste code first.");
                return;
            }

            CodeEntry entry = new CodeEntry(nextId(), code);
            synchronized (queueLock) {
                queue.add(entry);
            }
            queueTableModel.addRow(toRow(entry));
            codeInputArea.setText("");
            resultArea.setText("Added code #" + entry.id + " to queue.\n");
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
                JOptionPane.showMessageDialog(frame, "No pending code to check.");
                return;
            }

            checkBtn.setEnabled(false);
            addBtn.setEnabled(false);
            resultArea.setText("Analyzing " + pendingRows.size() + " pending code item(s)...\n");

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
                            resultArea.setText("Analyzing queue item #" + queue.get(rowIndex).id + "...\n");
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
                    resultArea.append("\nDone. Pending items have been checked.\n");
                    checkBtn.setEnabled(true);
                    addBtn.setEnabled(true);
                }
            };

            worker.execute();
        });

        clearBtn.addActionListener((ActionEvent e) -> codeInputArea.setText(""));
        clearQueueBtn.addActionListener((ActionEvent e) -> {
            synchronized (queueLock) {
                queue.clear();
            }
            queueTableModel.setRowCount(0);
            resultArea.setText("");
        });

        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);

        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(header, BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(north, BorderLayout.NORTH);
        root.add(mainSplit, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setVisible(true);
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
                "View"
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

        JDialog dialog = new JDialog((Frame) null, "Submit #" + entry.id, true);
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
        sb.append("Status: ").append(entry.status.label).append("\n");
        sb.append("Used AI: ").append(entry.usedAi).append("\n");
        sb.append("Confidence: ").append(entry.confidence).append("\n");
        sb.append("DS: ").append(entry.ds).append("\n");
        sb.append("Algo: ").append(entry.algo).append("\n");
        sb.append("AI: ").append(entry.ai).append("\n\n");
        sb.append("=== CODE ===\n");
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

    private long nextId() {
        synchronized (queueLock) {
            return queue.isEmpty() ? 1 : queue.get(queue.size() - 1).id + 1;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String geminiKey = System.getenv("GEMINI_API_KEY");
            AnalyzerService analyzer = new AnalyzerService(geminiKey);
            new Main(analyzer).createAndShowGUI();
        });
    }

    private enum CodeStatus {
        PENDING("Pending"),
        ANALYZING("Analyzing"),
        DONE("Done"),
        ERROR("Error");

        private final String label;

        CodeStatus(String label) {
            this.label = label;
        }
    }

    private static final class CodeEntry {
        private final long id;
        private final String code;
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

    private static final class ButtonColumnRenderer extends JButton implements TableCellRenderer {
        private ButtonColumnRenderer(String text) {
            setText(text);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "View" : value.toString());
            return this;
        }
    }

    private static final class ButtonColumnEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton("View");
        private final JTable table;
        private final RowAction action;
        private int row = -1;

        private ButtonColumnEditor(JTable table, RowAction action) {
            this.table = table;
            this.action = action;
            button.addActionListener(e -> {
                fireEditingStopped();
                if (row >= 0) {
                    action.run(row);
                }
            });
        }

        @Override
        public Object getCellEditorValue() {
            return "View";
        }

        @Override
        public boolean isCellEditable(EventObject e) {
            return true;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = table.convertRowIndexToModel(row);
            button.setText(value == null ? "View" : value.toString());
            return button;
        }
    }

    @FunctionalInterface
    private interface RowAction {
        void run(int rowIndex);
    }
}

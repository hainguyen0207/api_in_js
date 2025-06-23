import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class APITab {
    private final JPanel panel;
    private final JTable table;
    private final DefaultTableModel model;
    private final MontoyaApi api;
    private final Set<String> seenApis = new HashSet<>();
    private File currentSaveFile;
    private final String defaultSavePath = System.getenv("LOCALAPPDATA") + "/ApiJS/api.csv";
    private final String configPath = System.getenv("LOCALAPPDATA") + "/ApiJS/config.txt";

    public APITab(MontoyaApi api) {
        this.api = api;
        panel = new JPanel(new BorderLayout());

        model = new DefaultTableModel(new String[]{"STT", "URL", "API", "Status"}, 0);
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Kích thước cột
        table.getColumnModel().getColumn(0).setPreferredWidth(20);  // STT nhỏ
        table.getColumnModel().getColumn(1).setPreferredWidth(200); // URL
        table.getColumnModel().getColumn(2).setPreferredWidth(250); // API
        table.getColumnModel().getColumn(3).setPreferredWidth(80);  // Status

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        sorter.setComparator(0, Comparator.comparingInt(o -> Integer.parseInt(o.toString())));

        JScrollPane scrollPane = new JScrollPane(table);

        JButton resetButton = new JButton("Reset Status");
        resetButton.addActionListener(e -> resetStatuses());

        JButton exportButton = new JButton("Location Save File");
        exportButton.addActionListener(e -> chooseSaveLocation());

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearTable());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(resetButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(exportButton);

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        File configDir = new File(System.getenv("LOCALAPPDATA") + "/ApiJS");
        if (!configDir.exists()) configDir.mkdirs();

        loadConfig();
        loadData();

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveData));
    }

    public Component getComponent() {
        return panel;
    }

    public void addEntry(String fullUrl, String apiExtracted, String request, String response, boolean inSiteMap) {
        try {
            if (seenApis.contains(apiExtracted)) return;
            seenApis.add(apiExtracted);

            model.addRow(new Object[]{model.getRowCount() + 1, fullUrl, apiExtracted, inSiteMap ? "✅" : "❌"});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void chooseSaveLocation() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn nơi lưu file CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));

        int userSelection = fileChooser.showSaveDialog(panel);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                JOptionPane.showMessageDialog(panel, "Vui lòng chọn file có đuôi .csv!", "Lỗi định dạng", JOptionPane.ERROR_MESSAGE);
                return;
            }

            currentSaveFile = file;
            saveConfig(file.getAbsolutePath());
            saveData();
        }
    }

    private void saveConfig(String path) {
        try (FileWriter fw = new FileWriter(configPath)) {
            fw.write(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        File configFile = new File(configPath);
        if (configFile.exists()) {
            try {
                String path = Files.readString(configFile.toPath()).trim();
                currentSaveFile = new File(path);
            } catch (IOException e) {
                currentSaveFile = new File(defaultSavePath);
            }
        } else {
            currentSaveFile = new File(defaultSavePath);
        }
    }

    public void saveData() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(currentSaveFile))) {
            for (int row = 0; row < model.getRowCount(); row++) {
                List<String> cols = new ArrayList<>();
                for (int col = 0; col < model.getColumnCount(); col++) {
                    String val = model.getValueAt(row, col).toString();
                    cols.add('"' + val.replace("\"", "\"") + '"');
                }
                writer.println(String.join(",", cols));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadData() {
        if (currentSaveFile == null || !currentSaveFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(currentSaveFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (cols.length == 4) {
                    Object[] cleaned = new Object[4];
                    for (int i = 0; i < 4; i++) cleaned[i] = cols[i].replaceAll("^\"|\"$", "");
                    seenApis.add((String) cleaned[2]);
                    model.addRow(cleaned);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void resetStatuses() {
        Set<String> sitemapUrls = api.siteMap().requestResponses().stream().filter(item -> item.response() != null).map(item -> item.request().url()).collect(Collectors.toSet());

        for (int row = 0; row < model.getRowCount(); row++) {
            String status = model.getValueAt(row, 3).toString();
            if ("❌".equals(status)) {
                String apiPath = model.getValueAt(row, 2).toString();
                if (sitemapUrls.stream().anyMatch(url -> url.contains(apiPath))) {
                    model.setValueAt("✅", row, 3);
                }
            }
        }

        JOptionPane.showMessageDialog(panel, "Đã reset lại các API có trạng thái ❌.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void clearTable() {
        int confirm = JOptionPane.showConfirmDialog(panel, "Bạn có chắc chắn muốn xóa toàn bộ dữ liệu?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            model.setRowCount(0);
            seenApis.clear();

            if (currentSaveFile != null && currentSaveFile.exists()) {
                currentSaveFile.delete();
            }

            JOptionPane.showMessageDialog(panel, "Đã xóa toàn bộ dữ liệu.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

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
    private final List<Object[]> allRows = new ArrayList<>();
    private File currentSaveFile;
    private final String defaultSavePath = System.getenv("LOCALAPPDATA") + "/ApiJS/api.csv";
    private final String configPath = System.getenv("LOCALAPPDATA") + "/ApiJS/config.txt";
    private final JLabel filePathLabel;
    private final JTextField filterField;
    private final JComboBox<String> statusCombo;

    public APITab(MontoyaApi api) {
        // Khởi tạo các thành phần giao diện
        this.api = api;
        panel = new JPanel(new BorderLayout());

        model = new DefaultTableModel(new String[]{"STT", "URL", "API", "Status"}, 0);
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(0).setPreferredWidth(20);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        sorter.setComparator(0, Comparator.comparingInt(o -> Integer.parseInt(o.toString())));

        JScrollPane scrollPane = new JScrollPane(table);

        filePathLabel = new JLabel();
        filterField = new JTextField(20);
        statusCombo = new JComboBox<>(new String[]{"Tất cả", "✅", "❌"});

        JButton filterButton = new JButton("Lọc");
        filterButton.addActionListener(e -> applyFilter());

        JButton resetButton = new JButton("Reset Status");
        resetButton.addActionListener(e -> resetStatuses());

        JButton exportErrorButton = new JButton("Export ❌");
        exportErrorButton.addActionListener(e -> exportErrors());

        JButton loadButton = new JButton("Load File");
        loadButton.addActionListener(e -> loadFromFile());

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearTable());

// --- UI phần trên ---

// Panel bên trái: hiển thị đường dẫn
        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pathPanel.add(filePathLabel);

// Panel bên phải: lọc ký tự và trạng thái
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        filterPanel.add(new JLabel("Lọc ký tự:"));
        filterPanel.add(filterField);
        filterPanel.add(new JLabel("Trạng thái:"));
        filterPanel.add(statusCombo);
        filterPanel.add(filterButton);

// Gộp cả trái và phải
        JPanel filterAndPathPanel = new JPanel(new BorderLayout());
        filterAndPathPanel.add(pathPanel, BorderLayout.WEST);
        filterAndPathPanel.add(filterPanel, BorderLayout.EAST);

// Panel các nút chức năng
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(resetButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(loadButton);
        buttonPanel.add(exportErrorButton);

// Top panel chứa cả filter và nút
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(filterAndPathPanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

// Thêm vào panel chính
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);


        File configDir = new File(System.getenv("LOCALAPPDATA") + "/ApiJS");
        if (!configDir.exists()) configDir.mkdirs();

        loadConfig();
        loadData();
        filePathLabel.setText(currentSaveFile.getAbsolutePath());

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveData));
    }

    public Component getComponent() {
        return panel;
    }

    public void addEntry(String fullUrl, String apiExtracted, String request, String response, boolean inSiteMap) {
        try {
            if (seenApis.contains(apiExtracted)) return;
            seenApis.add(apiExtracted);

            Object[] row = new Object[]{model.getRowCount() + 1, fullUrl, apiExtracted, inSiteMap ? "✅" : "❌"};
            allRows.add(row);
            model.addRow(row);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyFilter() {
        String filterText = filterField.getText().trim();
        String statusFilter = (String) statusCombo.getSelectedItem();

        model.setRowCount(0);

        for (Object[] row : allRows) {
            String api = row[2].toString();
            String status = row[3].toString();

            boolean matchesFilter = true;
            if (!filterText.isEmpty()) {
                String[] filters = filterText.split(",");
                for (String f : filters) {
                    if (api.contains(f.trim())) {
                        matchesFilter = false;
                        break;
                    }
                }
            }

            boolean matchesStatus = statusFilter.equals("Tất cả") || status.equals(statusFilter);

            if (matchesFilter && matchesStatus) {
                model.addRow(row);
            }
        }
    }

    private void exportErrors() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn nơi lưu danh sách API lỗi");
        int option = chooser.showSaveDialog(panel);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                for (Object[] row : allRows) {
                    if ("❌".equals(row[3])) {
                        writer.println(row[2]);
                    }
                }
                JOptionPane.showMessageDialog(panel, "Đã xuất API ❌ ra file: " + file.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(panel, "Lỗi khi lưu file: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn file CSV để load");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));

        int userSelection = fileChooser.showOpenDialog(panel);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                JOptionPane.showMessageDialog(panel, "Vui lòng chọn file có đuôi .csv!", "Lỗi định dạng", JOptionPane.ERROR_MESSAGE);
                return;
            }

            currentSaveFile = file;
            saveConfig(file.getAbsolutePath());
            filePathLabel.setText(file.getAbsolutePath());

            clearTable();
            loadData();
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
            for (Object[] row : allRows) {
                List<String> cols = new ArrayList<>();
                for (Object val : row) {
                    cols.add('"' + val.toString().replace("\"", "\"") + '"');
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
                    allRows.add(cleaned);
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
        model.setRowCount(0);
        seenApis.clear();
        allRows.clear();
    }
}

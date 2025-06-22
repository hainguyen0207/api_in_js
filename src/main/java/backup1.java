//import burp.api.montoya.MontoyaApi;
//import burp.api.montoya.http.message.requests.HttpRequest;
//
//import javax.swing.*;
//import javax.swing.event.ListSelectionEvent;
//import javax.swing.table.DefaultTableModel;
//import javax.swing.table.TableRowSorter;
//import java.awt.*;
//import java.awt.event.*;
//import java.io.*;
//import java.net.URL;
//import java.util.Comparator;
//import java.util.Vector;
//
//public class DecryptedHistoryTab {
//    private final JPanel panel;
//    private final JTable table;
//    private final JTextArea requestArea;
//    private final JTextArea responseArea;
//    private final DefaultTableModel model;
//    private final TableRowSorter<DefaultTableModel> sorter;
//    private int counter = 1;
//    private final MontoyaApi api;
//    private final File saveFile = new File(System.getProperty("user.home") + File.separator + "burp_api_log.bin");
//
//    public DecryptedHistoryTab(MontoyaApi api) {
//        this.api = api;
//        panel = new JPanel(new BorderLayout());
//
//        model = new DefaultTableModel(new String[]{"STT", "Scope", "URL", "API", "Request", "Response"}, 0);
//        table = new JTable(model);
//        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//
//        sorter = new TableRowSorter<>(model);
//        table.setRowSorter(sorter);
//        sorter.setComparator(0, Comparator.comparingInt(o -> Integer.parseInt(o.toString())));
//
//        for (int i = 4; i <= 5; i++) {
//            table.getColumnModel().getColumn(i).setMinWidth(0);
//            table.getColumnModel().getColumn(i).setMaxWidth(0);
//            table.getColumnModel().getColumn(i).setWidth(0);
//        }
//
//        requestArea = new JTextArea();
//        responseArea = new JTextArea();
//        setupTextArea(requestArea);
//        setupTextArea(responseArea);
//
//        JSplitPane textSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(requestArea), new JScrollPane(responseArea));
//        textSplit.setResizeWeight(0.5);
//        textSplit.setDividerLocation(700);
//
//        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), textSplit);
//        mainSplit.setResizeWeight(0.3);
//        mainSplit.setDividerLocation(250);
//
//        panel.add(mainSplit, BorderLayout.CENTER);
//
//        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
//            if (!e.getValueIsAdjusting()) {
//                int row = table.getSelectedRow();
//                if (row != -1) {
//                    int modelRow = table.convertRowIndexToModel(row);
//                    requestArea.setText((String) model.getValueAt(modelRow, 4));
//                    responseArea.setText((String) model.getValueAt(modelRow, 5));
//                }
//            }
//        });
//
//        table.addMouseListener(new MouseAdapter() {
//            public void mousePressed(MouseEvent e) {
//                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
//                    showPopup(e);
//                }
//            }
//
//            public void mouseReleased(MouseEvent e) {
//                if (e.isPopupTrigger()) {
//                    showPopup(e);
//                }
//            }
//
//            private void showPopup(MouseEvent e) {
//                int row = table.rowAtPoint(e.getPoint());
//                if (row >= 0) {
//                    table.setRowSelectionInterval(row, row);
//                    JPopupMenu popup = new JPopupMenu();
//                    JMenuItem sendItem = new JMenuItem("Send to Repeater");
//                    sendItem.addActionListener(ev -> sendToRepeater(row));
//                    popup.add(sendItem);
//                    popup.show(e.getComponent(), e.getX(), e.getY());
//                }
//            }
//        });
//
//        table.registerKeyboardAction(e -> {
//            int row = table.getSelectedRow();
//            if (row >= 0) sendToRepeater(row);
//        }, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_FOCUSED);
//
//        requestArea.registerKeyboardAction(e -> {
//            int row = table.getSelectedRow();
//            if (row >= 0) sendToRepeater(row);
//        }, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
//
//        loadFromBinary();
//    }
//
//    private void setupTextArea(JTextArea area) {
//        area.setEditable(false);
//        area.setFont(new Font("Consolas", Font.PLAIN, 17));
//        area.setLineWrap(true);
//        area.setWrapStyleWord(true);
//    }
//
//    private void sendToRepeater(int row) {
//        try {
//            int modelRow = table.convertRowIndexToModel(row);
//            String rawRequest = (String) model.getValueAt(modelRow, 4);
//            HttpRequest request = HttpRequest.httpRequest(rawRequest);
//            api.repeater().sendToRepeater(request, "Decrypted Req #" + model.getValueAt(modelRow, 0));
//        } catch (Exception ex) {
//            JOptionPane.showMessageDialog(panel, "Gửi sang Repeater thất bại:\n" + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    public void addEntry(String fullUrl, String apiExtracted, String request, String response) {
//        try {
//            URL parsedUrl = new URL(fullUrl);
//            String scope = parsedUrl.getProtocol() + "://" + parsedUrl.getHost();
//            if (parsedUrl.getPort() != -1) {
//                scope += ":" + parsedUrl.getPort();
//            }
//            scope += "/";
//
//            Vector<Object> row = new Vector<>();
//            row.add(counter++);
//            row.add(scope);
//            row.add(fullUrl);
//            row.add(apiExtracted);
//            row.add(request);
//            row.add(response);
//            model.addRow(row);
//            saveToBinary();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public Component getComponent() {
//        return panel;
//    }
//
//    private void saveToBinary() {
//        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(saveFile))) {
//            for (int i = 0; i < model.getRowCount(); i++) {
//                oos.writeObject(model.getDataVector().elementAt(i));
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void loadFromBinary() {
//        if (!saveFile.exists()) return;
//        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(saveFile))) {
//            while (true) {
//                try {
//                    Vector<?> row = (Vector<?>) ois.readObject();
//                    model.addRow(row);
//                    counter = Math.max(counter, Integer.parseInt(row.get(0).toString()) + 1);
//                } catch (EOFException e) {
//                    break;
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
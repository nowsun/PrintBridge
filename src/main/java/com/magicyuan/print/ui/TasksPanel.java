package com.magicyuan.print.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

import com.magicyuan.print.config.AppConfig;
import com.magicyuan.print.dto.PrintDTO;
import com.magicyuan.print.i18n.I18nManager;
import com.magicyuan.print.model.PrintQueueModel;
import com.magicyuan.print.service.PrintService;

/**
 * 任务页签
 * 展示打印任务记录(已完成/失败),支持刷新、清空与重新打印选中任务。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class TasksPanel extends JPanel {

    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final PrintQueueModel queueModel;
    private final PrintService printService;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final List<PrintDTO> rowTasks = new ArrayList<PrintDTO>();

    public TasksPanel(PrintQueueModel queueModel, PrintService printService) {
        this.queueModel = queueModel;
        this.printService = printService;

        setLayout(new BorderLayout());
        setBackground(UIComponentFactory.themeColor("Panel.background", AppConfig.Colors.BACKGROUND));
        setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));

        CardPanel card = new CardPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setOpaque(false);

        tableModel = new DefaultTableModel(new Object[]{
            I18nManager.zhEn("文件名", "File"),
            I18nManager.zhEn("状态", "Status"),
            I18nManager.zhEn("时间", "Time"),
            I18nManager.zhEn("来源", "Source"),
            I18nManager.zhEn("打印机", "Printer")
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFont(UIComponentFactory.FIELD_FONT);
        table.getTableHeader().setFont(UIComponentFactory.FIELD_FONT);
        Dimension headerSize = table.getTableHeader().getPreferredSize();
        table.getTableHeader().setPreferredSize(new Dimension(headerSize.width, UIComponentFactory.CONTROL_HEIGHT));
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(UIComponentFactory.CONTROL_HEIGHT);
        configureColumns();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(
            UIComponentFactory.themeColor("Component.borderColor", AppConfig.Colors.BORDER)));
        content.add(scroll, BorderLayout.CENTER);

        content.add(buildToolbar(), BorderLayout.NORTH);

        card.add(content, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        reload();

        // 监听队列变化,任务新增/失败/清空时自动刷新列表(无需手动点"刷新")
        queueModel.addChangeListener(() -> SwingUtilities.invokeLater(this::reload));
    }

    /** 按列属性设置合理列宽(文件名/来源较宽, 状态较窄) */
    private void configureColumns() {
        int[] widths = {200, 80, 150, 220, 160};
        int[] minWidths = {120, 60, 100, 120, 100};
        for (int i = 0; i < widths.length; i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(widths[i]);
            column.setMinWidth(minWidths[i]);
        }
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    }

    /** 顶部工具条: 左侧"打印任务"标签, 右侧操作按钮 */
    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);

        JLabel title = UIComponentFactory.label(I18nManager.zhEn("打印任务", "Print tasks"));
        toolbar.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton printBtn = UIComponentFactory.button(I18nManager.zhEn("打印", "Print"));
        printBtn.addActionListener(e -> printSelected());
        JButton refreshBtn = UIComponentFactory.button(I18nManager.zhEn("刷新", "Refresh"));
        refreshBtn.addActionListener(e -> reload());
        JButton clearBtn = UIComponentFactory.button(I18nManager.zhEn("清空", "Clear"));
        clearBtn.addActionListener(e -> clear());
        actions.add(printBtn);
        actions.add(refreshBtn);
        actions.add(clearBtn);
        toolbar.add(actions, BorderLayout.EAST);

        return toolbar;
    }

    private void reload() {
        tableModel.setRowCount(0);
        rowTasks.clear();
        for (PrintDTO dto : queueModel.getAllPrintTasks()) {
            addRow(dto, I18nManager.zhEn("已完成", "Done"));
        }
        for (PrintDTO dto : queueModel.getErrorTasks()) {
            addRow(dto, I18nManager.zhEn("失败", "Failed"));
        }
    }

    private void addRow(PrintDTO dto, String status) {
        rowTasks.add(dto);
        tableModel.addRow(new Object[]{
            dto.getFileName(),
            status,
            formatTime(dto.getCreateTime()),
            sourceLabel(dto),
            dto.getPrinterName()
        });
    }

    /** 任务来源标签(参考 TS 版: 网页/测试) */
    private String sourceLabel(PrintDTO dto) {
        if ("test".equals(dto.getSource())) {
            return I18nManager.zhEn("测试", "Test");
        }
        if ("web".equals(dto.getSource())) {
            return I18nManager.zhEn("网页", "Web");
        }
        if (dto.getFileUrl() != null && !dto.getFileUrl().isEmpty()) {
            return I18nManager.zhEn("网页", "Web");
        }
        return I18nManager.zhEn("本地", "Local");
    }

    private String formatTime(long createTime) {
        if (createTime <= 0) {
            return "";
        }
        return new SimpleDateFormat(TIME_PATTERN).format(new Date(createTime));
    }

    public void printSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this,
                I18nManager.zhEn("请先选择要打印的任务", "Select tasks to print"),
                AppConfig.APP_NAME, JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<PrintDTO> selected = new ArrayList<PrintDTO>();
        for (int row : rows) {
            if (row >= 0 && row < rowTasks.size()) {
                selected.add(rowTasks.get(row));
            }
        }
        if (!selected.isEmpty()) {
            printService.printWithDialogAsync(selected);
        }
    }

    private void clear() {
        int confirm = JOptionPane.showConfirmDialog(this,
            I18nManager.zhEn("确定清空所有任务记录吗?", "Clear all task records?"),
            AppConfig.APP_NAME, JOptionPane.OK_CANCEL_OPTION);
        if (confirm == JOptionPane.OK_OPTION) {
            queueModel.clearPrintQueue();
            queueModel.clearErrorQueue();
            reload();
        }
    }
}

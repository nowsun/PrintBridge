package com.magicyuan.print.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import com.magicyuan.print.SingleFileHTTPServer;
import com.magicyuan.print.config.AppConfig;
import com.magicyuan.print.config.AppSettings;
import com.magicyuan.print.config.AutoStartManager;
import com.magicyuan.print.config.ThemeManager;
import com.magicyuan.print.i18n.I18nManager;
import com.magicyuan.print.service.PrintService;

/**
 * 设置页签
 * 参考 TS 版排版: 白底圆角卡片, 三组分隔(打印设置 / 服务设置 / 偏好设置),
 * 设置即时生效, 无需保存按钮。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class SettingsPanel extends JPanel {

    /** 参考 TS 版: text-sm = 14px 字体, 控件高度 h-9 = 36px; 按需求整体加大 1 号 */
    private static final Font FIELD_FONT = new Font(AppConfig.Fonts.FONT_FAMILY, Font.PLAIN, 15);
    private static final Font LABEL_FONT = new Font(AppConfig.Fonts.FONT_FAMILY, Font.PLAIN, 15);
    private static final Font HINT_FONT = new Font(AppConfig.Fonts.FONT_FAMILY, Font.PLAIN, 13);
    private static final int CONTROL_HEIGHT = 36;

    private final AppSettings settings;
    private final PrintService printService;
    private final Runnable onChanged;
    private final Runnable onRebuild;

    private final JComboBox<String> printerCombo;
    private final JComboBox<String> paperCombo;
    private final JTextField widthField;
    private final JTextField heightField;
    private final JTextField portField;
    private final SwitchControl autostartSwitch;

    private final JButton refreshButton;
    private final JButton testPrintButton;
    private final SegmentedControl themeSeg;
    private final SegmentedControl languageSeg;

    /** 程序内部更新控件时置为 true, 抑制监听器触发 */
    private boolean updating = false;

    /** 纸张预设:名称 -> [宽度mm, 高度mm] */
    private static final Map<String, double[]> PAPER_PRESETS = new LinkedHashMap<String, double[]>();

    static {
        PAPER_PRESETS.put("A4", new double[]{210.0, 297.0});
        PAPER_PRESETS.put("A5", new double[]{148.0, 210.0});
        PAPER_PRESETS.put("Letter", new double[]{215.9, 279.4});
        PAPER_PRESETS.put("80 x 60", new double[]{80.0, 60.0});
        PAPER_PRESETS.put("58 x 40", new double[]{58.0, 40.0});
    }

    /** 端口输入过滤器: 仅允许数字(拦截键入与粘贴) */
    private static final DocumentFilter DIGITS_ONLY_FILTER = new DocumentFilter() {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            super.insertString(fb, offset, digitsOnly(string), attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            super.replace(fb, offset, length, digitsOnly(text), attrs);
        }

        private String digitsOnly(String s) {
            if (s == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    };

    public SettingsPanel(AppSettings settings, PrintService printService, Runnable onChanged, Runnable onRebuild) {
        this.settings = settings;
        this.printService = printService;
        this.onChanged = onChanged;
        this.onRebuild = onRebuild;

        this.printerCombo = new JComboBox<String>();
        this.paperCombo = new JComboBox<String>();
        this.widthField = new JTextField(6);
        this.heightField = new JTextField(6);
        this.portField = new JTextField(8);
        this.autostartSwitch = new SwitchControl();

        this.refreshButton = iconButton(I18nManager.zhEn("刷新", "Refresh"));
        this.testPrintButton = iconButton(I18nManager.zhEn("测试打印", "Test print"));

        this.themeSeg = new SegmentedControl(new String[]{
            I18nManager.zhEn("浅色", "Light"),
            I18nManager.zhEn("深色", "Dark"),
            I18nManager.zhEn("跟随系统", "System")
        }, themeIndex(), this::applyTheme);

        this.languageSeg = new SegmentedControl(
            new String[]{"中文", "English"}, languageIndex(), this::applyLanguage);

        styleCombo(printerCombo);
        styleCombo(paperCombo);
        styleField(widthField, 140);
        styleField(heightField, 140);
        styleField(portField, 0);
        // 本地端口仅允许输入数字
        ((AbstractDocument) portField.getDocument()).setDocumentFilter(DIGITS_ONLY_FILTER);

        setLayout(new BorderLayout());
        setBackground(UIComponentFactory.themeColor("Panel.background", AppConfig.Colors.BACKGROUND));
        setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));

        CardPanel card = new CardPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.add(buildCardContent(), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        loadValues();
        attachListeners();
    }

    // ---- 布局构建 ----

    private JPanel buildCardContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        content.add(buildPrintSection(), c);

        c.gridy++;
        c.insets = new Insets(16, 0, 16, 0);
        content.add(separator(), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        content.add(buildServiceSection(), c);

        c.gridy++;
        c.insets = new Insets(16, 0, 16, 0);
        content.add(separator(), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        content.add(buildPreferencesSection(), c);

        // 底部弹簧, 让内容靠上
        c.gridy++;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        content.add(spacer, c);

        return content;
    }

    /** 打印设置组: 默认打印机 + 默认纸张 */
    private JPanel buildPrintSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 0);

        // 默认打印机: [3/5] [1/5] [1/5], 按钮底对齐并各占 1/5
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 3.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 0, 8);
        section.add(field(I18nManager.zhEn("默认打印机", "Default printer"), printerCombo), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.SOUTH;
        c.insets = new Insets(0, 0, 0, 8);
        section.add(refreshButton, c);

        c.gridx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        section.add(testPrintButton, c);

        // 默认纸张: [3/5] [1/5] [1/5], 纸张下拉与打印机等宽
        c.gridy = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridx = 0;
        c.weightx = 3.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(16, 0, 0, 8);
        section.add(paperField(), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.insets = new Insets(16, 0, 0, 8);
        section.add(field(I18nManager.zhEn("宽度（mm）", "Width (mm)"), widthField), c);

        c.gridx = 2;
        c.insets = new Insets(16, 0, 0, 0);
        section.add(field(I18nManager.zhEn("高度（mm）", "Height (mm)"), heightField), c);

        return section;
    }

    /** 默认纸张列: 标签 + 下拉 + 提示 */
    private JPanel paperField() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(label(I18nManager.zhEn("默认纸张", "Default paper")), c);

        c.gridy = 1;
        c.insets = new Insets(6, 0, 0, 0);
        panel.add(paperCombo, c);

        c.gridy = 2;
        c.insets = new Insets(4, 0, 0, 0);
        panel.add(hint(I18nManager.zhEn("可选择驱动纸张，也可直接编辑尺寸",
            "Choose a driver paper or edit the size directly")), c);

        return panel;
    }

    /** 服务设置组: 本地端口 + 开机自启, 两列 */
    private JPanel buildServiceSection() {
        JPanel section = new JPanel(new GridBagLayout());
        section.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 0, 16);
        section.add(field(I18nManager.zhEn("本地端口", "Local port"), portField), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 0, 0);
        section.add(autostartField(), c);

        return section;
    }

    /** 开机自启列: 标签 + 提示与开关同一行 */
    private JPanel autostartField() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(label(I18nManager.zhEn("开机自启", "Launch at startup")), c);

        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setMinimumSize(new Dimension(0, CONTROL_HEIGHT));
        row.setPreferredSize(new Dimension(0, CONTROL_HEIGHT));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, CONTROL_HEIGHT));

        GridBagConstraints rc = new GridBagConstraints();
        rc.gridy = 0;
        rc.gridx = 0;
        rc.weightx = 1.0;
        rc.fill = GridBagConstraints.HORIZONTAL;
        rc.anchor = GridBagConstraints.WEST;
        rc.insets = new Insets(0, 0, 0, 0);
        row.add(hint(I18nManager.zhEn("系统开机启动后自动运行 PrintBridge",
            "Run PrintBridge automatically when the system starts")), rc);

        rc.gridx = 1;
        rc.weightx = 0.0;
        rc.fill = GridBagConstraints.NONE;
        rc.anchor = GridBagConstraints.EAST;
        row.add(autostartSwitch, rc);

        c.gridy = 1;
        c.insets = new Insets(6, 0, 0, 0);
        panel.add(row, c);

        return panel;
    }

    /** 偏好设置组: 界面主题 + 语言, 圆角边框容器内两列 */
    private JPanel buildPreferencesSection() {
        RoundedBox container = new RoundedBox();
        container.setLayout(new GridBagLayout());
        container.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 0, 16);
        container.add(field(I18nManager.zhEn("界面主题", "Appearance"), themeSeg), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 0, 0);
        container.add(field(I18nManager.zhEn("语言", "Language"), languageSeg), c);

        return container;
    }

    /** 标签在上、控件在下的单列容器 */
    private JPanel field(String labelText, JComponent control) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(label(labelText), c);

        c.gridy = 1;
        c.insets = new Insets(6, 0, 0, 0);
        panel.add(control, c);

        return panel;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT);
        label.setForeground(labelForeground());
        return label;
    }

    private JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(HINT_FONT);
        label.setForeground(hintForeground());
        return label;
    }

    /** 1px 分隔线 */
    private JPanel separator() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(borderColor());
        line.setPreferredSize(new Dimension(1, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return line;
    }

    private JButton iconButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FIELD_FONT);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, CONTROL_HEIGHT));
        button.setMinimumSize(new Dimension(0, CONTROL_HEIGHT));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, CONTROL_HEIGHT));
        return button;
    }

    private void styleField(JTextField field, int width) {
        field.setFont(FIELD_FONT);
        field.setPreferredSize(new Dimension(width, CONTROL_HEIGHT));
        field.setMaximumSize(new Dimension(width == 0 ? Integer.MAX_VALUE : width, CONTROL_HEIGHT));
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setFont(FIELD_FONT);
        Dimension preferred = combo.getPreferredSize();
        combo.setPreferredSize(new Dimension(preferred.width, CONTROL_HEIGHT));
        combo.setMinimumSize(new Dimension(0, CONTROL_HEIGHT));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, CONTROL_HEIGHT));
    }

    // ---- 主题感知颜色 ----

    private Color labelForeground() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : AppConfig.Colors.TEXT_PRIMARY;
    }

    private Color hintForeground() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : AppConfig.Colors.TEXT_SECONDARY;
    }

    private Color borderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : AppConfig.Colors.BORDER;
    }

    // ---- 加载与监听 ----

    private void loadValues() {
        refreshPrinters();
        loadPaper();
        portField.setText(String.valueOf(settings.getPort()));
        autostartSwitch.setSelected(settings.isAutostart());
    }

    private void attachListeners() {
        printerCombo.addActionListener(e -> applyPrinter());
        paperCombo.addActionListener(e -> {
            if (updating) {
                return;
            }
            Object selected = paperCombo.getSelectedItem();
            double[] dims = PAPER_PRESETS.get(selected == null ? null : selected.toString());
            if (dims != null) {
                widthField.setText(mm(dims[0]));
                heightField.setText(mm(dims[1]));
                settings.setPaperWidthMm(dims[0]);
                settings.setPaperHeightMm(dims[1]);
                persist();
            }
        });

        FocusAdapter paperFocus = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyPaper();
            }
        };
        widthField.addFocusListener(paperFocus);
        heightField.addFocusListener(paperFocus);

        portField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyPort();
            }
        });

        autostartSwitch.addActionListener(e -> applyAutostart());
        refreshButton.addActionListener(e -> refreshPrinters());
        testPrintButton.addActionListener(e -> testPrint());
    }

    private void refreshPrinters() {
        String current = settings.getPrinterName();
        String[] printers = printService.getAvailablePrinters();

        updating = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>();
            model.addElement(I18nManager.zhEn("系统默认", "System default"));
            int selectIndex = 0;
            int index = 1;
            for (String name : printers) {
                model.addElement(name);
                if (name.equals(current)) {
                    selectIndex = index;
                }
                index++;
            }
            printerCombo.setModel(model);
            printerCombo.setSelectedIndex(selectIndex);
        } finally {
            updating = false;
        }
    }

    private void loadPaper() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>();
        model.addElement(I18nManager.zhEn("自定义尺寸", "Custom size"));
        for (String name : PAPER_PRESETS.keySet()) {
            model.addElement(name);
        }
        paperCombo.setModel(model);

        double width = settings.getPaperWidthMm();
        double height = settings.getPaperHeightMm();
        widthField.setText(mm(width));
        heightField.setText(mm(height));

        String matched = null;
        for (Map.Entry<String, double[]> entry : PAPER_PRESETS.entrySet()) {
            double[] dims = entry.getValue();
            if (Math.abs(dims[0] - width) < 0.05 && Math.abs(dims[1] - height) < 0.05) {
                matched = entry.getKey();
                break;
            }
        }
        paperCombo.setSelectedItem(matched != null ? matched : paperCombo.getItemAt(0));
    }

    // ---- 即时应用 ----

    private void applyPrinter() {
        if (updating) {
            return;
        }
        int index = printerCombo.getSelectedIndex();
        String name = index > 0 ? printerCombo.getSelectedItem().toString() : "";
        settings.setPrinterName(name);
        persist();
    }

    private void applyPaper() {
        double width;
        double height;
        try {
            width = Double.parseDouble(widthField.getText().trim());
            height = Double.parseDouble(heightField.getText().trim());
            if (width <= 0 || height <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            widthField.setText(mm(settings.getPaperWidthMm()));
            heightField.setText(mm(settings.getPaperHeightMm()));
            return;
        }
        settings.setPaperWidthMm(width);
        settings.setPaperHeightMm(height);
        syncPaperCombo(width, height);
        persist();
    }

    private void syncPaperCombo(double width, double height) {
        String matched = null;
        for (Map.Entry<String, double[]> entry : PAPER_PRESETS.entrySet()) {
            double[] dims = entry.getValue();
            if (Math.abs(dims[0] - width) < 0.05 && Math.abs(dims[1] - height) < 0.05) {
                matched = entry.getKey();
                break;
            }
        }
        updating = true;
        try {
            paperCombo.setSelectedItem(matched != null ? matched : paperCombo.getItemAt(0));
        } finally {
            updating = false;
        }
    }

    private void applyPort() {
        int newPort;
        try {
            newPort = Integer.parseInt(portField.getText().trim());
            if (newPort <= 0 || newPort > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            portField.setText(String.valueOf(settings.getPort()));
            return;
        }
        int oldPort = settings.getPort();
        settings.setPort(newPort);
        persist();
        if (newPort != oldPort) {
            SingleFileHTTPServer.restartServer();
            JOptionPane.showMessageDialog(this,
                I18nManager.zhEn("端口已更新，服务已重启", "Port updated, service restarted"),
                AppConfig.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void applyAutostart() {
        boolean enabled = autostartSwitch.isSelected();
        settings.setAutostart(enabled);
        settings.save();
        AutoStartManager.setEnabled(enabled);
        JOptionPane.showMessageDialog(this,
            enabled ? I18nManager.zhEn("已加入开机自启", "Added to startup")
                    : I18nManager.zhEn("已取消开机自启", "Removed from startup"),
            AppConfig.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    private void testPrint() {
        printService.testPrintPageAsync().whenComplete((v, ex) -> {
            SwingUtilities.invokeLater(() -> {
                if (ex != null) {
                    JOptionPane.showMessageDialog(this,
                        I18nManager.zhEn("测试打印失败", "Test print failed"),
                        AppConfig.APP_NAME, JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                        I18nManager.zhEn("测试打印已提交", "Test print submitted"),
                        AppConfig.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
                }
            });
        });
    }

    private int themeIndex() {
        String theme = settings.getTheme();
        if ("dark".equals(theme)) {
            return 1;
        }
        if ("light".equals(theme)) {
            return 0;
        }
        return 2;
    }

    private void applyTheme(int index) {
        String[] themes = {"light", "dark", "system"};
        settings.setTheme(themes[index]);
        settings.save();
        ThemeManager.apply(themes[index]);
        onRebuild.run();
    }

    private int languageIndex() {
        return "en".equals(settings.getLanguage()) ? 1 : 0;
    }

    private void applyLanguage(int index) {
        settings.setLanguage(index == 1 ? "en" : "zh-CN");
        settings.save();
        I18nManager.setLocale(index == 1 ? Locale.US : Locale.SIMPLIFIED_CHINESE);
        onRebuild.run();
    }

    private void persist() {
        settings.save();
        onChanged.run();
    }

    private static String mm(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    // ---- 自绘组件 ----

    /** 圆角边框容器(仅描边) */
    private static class RoundedBox extends JPanel {
        RoundedBox() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color border = UIManager.getColor("Component.borderColor");
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            }
            g2.dispose();
        }
    }

    /** 开关(参考 TS 版 Switch) */
    private static class SwitchControl extends JToggleButton {
        SwitchControl() {
            setPreferredSize(new Dimension(40, 22));
            setMinimumSize(new Dimension(40, 22));
            setMaximumSize(new Dimension(40, 22));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            boolean on = isSelected();

            Color track = on ? trackOnColor() : trackOffColor();
            g2.setColor(track);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);

            int d = h - 6;
            int ty = (h - d) / 2;
            int tx = on ? w - 4 - d : 3;
            g2.setColor(trackKnobColor());
            g2.fillOval(tx, ty, d, d);
            g2.dispose();
        }

        /** 开启状态轨道色: 跟随主题的深/浅中性色(与主题、语言开关一致, 不用蓝色) */
        private Color trackOnColor() {
            Color c = UIManager.getColor("Label.foreground");
            return c != null ? c : AppConfig.Colors.TEXT_PRIMARY;
        }

        /** 开关滑块色: 跟随主题背景色, 保证与轨道形成对比 */
        private Color trackKnobColor() {
            Color c = UIManager.getColor("Panel.background");
            return c != null ? c : Color.WHITE;
        }

        private Color trackOffColor() {
            Color c = UIManager.getColor("Component.borderColor");
            return c != null ? c : new Color(203, 213, 225);
        }
    }

    /** 分段选择控件容器(灰底圆角) */
    private static class SegmentedControl extends JPanel {
        private final List<SegmentButton> buttons = new ArrayList<SegmentButton>();
        private int selected;

        SegmentedControl(String[] options, int selected, IntConsumer onSelect) {
            super(new GridLayout(1, options.length, 2, 2));
            this.selected = selected;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            setPreferredSize(new Dimension(options.length * 90, CONTROL_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, CONTROL_HEIGHT));

            for (int i = 0; i < options.length; i++) {
                final int index = i;
                SegmentButton button = new SegmentButton(options[i]);
                button.addActionListener(e -> {
                    select(index);
                    onSelect.accept(index);
                });
                buttons.add(button);
                add(button);
            }
            updateStyle();
        }

        void select(int index) {
            this.selected = index;
            updateStyle();
        }

        private void updateStyle() {
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).setActive(i == selected);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg = UIManager.getColor("Button.background");
            Color border = UIManager.getColor("Component.borderColor");
            if (bg != null) {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            g2.dispose();
        }
    }

    /** 分段控件内的单个选项按钮 */
    private static class SegmentButton extends JButton {
        private boolean active;

        SegmentButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setFont(new Font(AppConfig.Fonts.FONT_FAMILY, Font.PLAIN, 15));
        }

        void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            Color fg = UIManager.getColor("Label.foreground");
            Color panelBg = UIManager.getColor("Panel.background");
            if (active) {
                // 选中项仅用浅色/深色(跟随主题): 浅色主题=深色填充, 深色主题=浅色填充
                g2.setColor(fg != null ? fg : AppConfig.Colors.TEXT_PRIMARY);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.setColor(panelBg != null ? panelBg : AppConfig.Colors.WHITE);
            } else {
                g2.setColor(fg != null ? fg : AppConfig.Colors.TEXT_PRIMARY);
            }

            FontMetrics fm = g2.getFontMetrics();
            int x = (w - fm.stringWidth(getText())) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }
}

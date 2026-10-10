package com.magicyuan.print.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import com.magicyuan.print.SingleFileHTTPServer;
import com.magicyuan.print.config.AppConfig;
import com.magicyuan.print.config.AppSettings;
import com.magicyuan.print.i18n.I18nManager;

/**
 * 网站白名单页签
 * 管理允许连接本机服务的网页来源 Origin 列表,增删即时生效。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class WhitelistPanel extends JPanel {

    private final AppSettings settings;
    private final DefaultListModel<String> listModel = new DefaultListModel<String>();
    private final JList<String> originList = new JList<String>(listModel);
    private final JTextField inputField = new JTextField(30);

    public WhitelistPanel(AppSettings settings) {
        this.settings = settings;

        setLayout(new BorderLayout());
        setBackground(UIComponentFactory.themeColor("Panel.background", AppConfig.Colors.BACKGROUND));
        setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));

        CardPanel card = new CardPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setOpaque(false);

        // 顶部:输入 + 添加
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        UIComponentFactory.styleField(inputField, 0);
        inputField.setToolTipText("https://example.com");
        top.add(inputField, BorderLayout.CENTER);
        JButton addBtn = UIComponentFactory.button(I18nManager.zhEn("添加", "Add"));
        addBtn.addActionListener(e -> addOrigin());
        top.add(addBtn, BorderLayout.EAST);
        content.add(top, BorderLayout.NORTH);

        // 中部:Origin 列表(行高与任务列表一致)
        originList.setFont(UIComponentFactory.FIELD_FONT);
        originList.setFixedCellHeight(UIComponentFactory.CONTROL_HEIGHT);
        JScrollPane scroll = new JScrollPane(originList);
        scroll.setBorder(BorderFactory.createLineBorder(
            UIComponentFactory.themeColor("Component.borderColor", AppConfig.Colors.BORDER)));
        content.add(scroll, BorderLayout.CENTER);

        // 底部:删除(与列表左边缘对齐)
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bottom.setOpaque(false);
        JButton delBtn = UIComponentFactory.button(I18nManager.zhEn("删除选中", "Delete selected"));
        delBtn.addActionListener(e -> removeOrigin());
        bottom.add(delBtn);
        content.add(bottom, BorderLayout.SOUTH);

        card.add(content, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        reload();
    }

    private void reload() {
        listModel.clear();
        for (String origin : settings.getAllowedOrigins()) {
            listModel.addElement(origin);
        }
    }

    private void addOrigin() {
        String origin = inputField.getText().trim();
        if (origin.isEmpty()) {
            return;
        }
        if (!isValidOrigin(origin)) {
            JOptionPane.showMessageDialog(this,
                I18nManager.zhEn("请输入合法的 Origin（如 https://example.com）",
                    "Enter a valid Origin (e.g. https://example.com)"),
                AppConfig.APP_NAME, JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!settings.getAllowedOrigins().contains(origin)) {
            settings.getAllowedOrigins().add(origin);
            persist();
            reload();
        }
        inputField.setText("");
    }

    private void removeOrigin() {
        int index = originList.getSelectedIndex();
        if (index < 0) {
            JOptionPane.showMessageDialog(this,
                I18nManager.zhEn("请先选择要删除的 Origin", "Select an Origin to delete"),
                AppConfig.APP_NAME, JOptionPane.WARNING_MESSAGE);
            return;
        }
        settings.getAllowedOrigins().remove(index);
        persist();
        reload();
    }

    private void persist() {
        settings.save();
        SingleFileHTTPServer.applySettings(settings);
    }

    private boolean isValidOrigin(String origin) {
        return origin.startsWith("http://") || origin.startsWith("https://");
    }
}

package com.magicyuan.print.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.magicyuan.print.config.AppConfig;
import com.magicyuan.print.config.AppSettings;
import com.magicyuan.print.i18n.I18nManager;
import com.magicyuan.print.service.PrintService;

/**
 * 关于页签
 * 展示应用信息、服务地址、支持的格式,并提供安装多格式支持的入口。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class AboutPanel extends JPanel {

    private final AppSettings settings;
    private final PrintService printService;
    private final JLabel serviceLabel;
    private final JPanel formatsPanel;
    private final JButton installBtn;

    /** 格式条目前的勾选标记 */
    private static final String CHECK_MARK = "✅ ";

    /** 节标题字体(加粗) */
    private static final Font SECTION_FONT = new Font(AppConfig.Fonts.FONT_FAMILY, Font.BOLD, 15);

    /** 格式条目字体(逻辑字体 SansSerif, 保证 ✅ 与中文都能正确渲染) */
    private static final Font CHECK_FONT = new Font(AppConfig.Fonts.FONT_FAMILY_SANS, Font.PLAIN, 13);

    public AboutPanel(AppSettings settings, PrintService printService, Runnable onInstallMultiFormat) {
        this.settings = settings;
        this.printService = printService;

        setLayout(new BorderLayout());
        setBackground(UIComponentFactory.themeColor("Panel.background", AppConfig.Colors.BACKGROUND));
        setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));

        CardPanel card = new CardPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 头部: 名称+版本(同一行) + 服务地址(最右侧), 描述在名称正下方
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        // 名称 + 版本(PrintBridge v2.1.3, 同一行)
        JPanel nameRow = new JPanel();
        nameRow.setLayout(new BoxLayout(nameRow, BoxLayout.X_AXIS));
        nameRow.setOpaque(false);
        JLabel name = new JLabel(AppConfig.APP_NAME);
        name.setFont(new Font(AppConfig.Fonts.FONT_FAMILY, Font.BOLD, 22));
        name.setForeground(UIComponentFactory.themeColor("Label.foreground", AppConfig.Colors.TEXT_PRIMARY));
        JLabel version = new JLabel(AppConfig.APP_VERSION);
        version.setFont(UIComponentFactory.HINT_FONT);
        version.setForeground(UIComponentFactory.themeColor("Label.disabledForeground", AppConfig.Colors.TEXT_SECONDARY));
        version.setAlignmentY(Component.CENTER_ALIGNMENT);
        // 顶部留白8px: BoxLayout 居中会分摊一半, 实际下移约4px
        version.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        nameRow.add(name);
        nameRow.add(Box.createHorizontalStrut(8));
        nameRow.add(version);
        topRow.add(nameRow, BorderLayout.WEST);

        // 服务地址(最右侧, 与名称上下居中对齐); 点击跳转到浏览器健康检查页
        serviceLabel = new JLabel();
        serviceLabel.setFont(UIComponentFactory.LABEL_FONT);
        serviceLabel.setForeground(UIComponentFactory.themeColor("Label.foreground", AppConfig.Colors.TEXT_PRIMARY));
        serviceLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        serviceLabel.setToolTipText(I18nManager.zhEn("在浏览器中打开健康检查", "Open health check in browser"));
        serviceLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openHealthCheck();
            }
        });
        topRow.add(serviceLabel, BorderLayout.EAST);
        header.add(topRow, BorderLayout.NORTH);

        // 描述(名称正下方)
        JPanel subBox = new JPanel();
        subBox.setLayout(new BoxLayout(subBox, BoxLayout.Y_AXIS));
        subBox.setOpaque(false);
        JLabel desc = new JLabel(AppConfig.getAppDescription());
        desc.setFont(UIComponentFactory.HINT_FONT);
        desc.setForeground(UIComponentFactory.themeColor("Label.disabledForeground", AppConfig.Colors.TEXT_SECONDARY));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        subBox.add(desc);
        header.add(subBox, BorderLayout.CENTER);

        // 固定头部高度(防止 BoxLayout 把多余空间分给头部导致描述下移)
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));

        content.add(header);
        content.add(Box.createVerticalStrut(12));

        formatsPanel = new JPanel();
        formatsPanel.setLayout(new BoxLayout(formatsPanel, BoxLayout.Y_AXIS));
        formatsPanel.setOpaque(false);
        formatsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(formatsPanel);

        // 安装多格式支持(仅未安装LibreOffice时显示)
        installBtn = UIComponentFactory.button(
            I18nManager.zhEn("安装多格式支持", "Install multi-format support"));
        installBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension installSize = installBtn.getPreferredSize();
        // 高度略低于标准控件(36), 按钮更紧凑
        installBtn.setPreferredSize(new Dimension(installSize.width, 32));
        installBtn.setMinimumSize(new Dimension(0, 32));
        installBtn.setMaximumSize(new Dimension(installSize.width, 32));
        installBtn.addActionListener(e -> onInstallMultiFormat.run());
        content.add(Box.createVerticalStrut(6));
        content.add(installBtn);
        content.add(Box.createVerticalStrut(6));

        // 弹性空白: 吸收剩余空间, 让版权保持在底部, 上方内容(格式说明+提示+安装按钮)整体上移
        content.add(Box.createVerticalGlue());

        // 版权(关于框底部居中; 用左对齐的整宽容器包裹, 避免居中标签把 BoxLayout 整体对齐也拉成居中)
        JPanel copyrightWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        copyrightWrap.setOpaque(false);
        copyrightWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel copyright = new JLabel(AppConfig.getCopyright());
        copyright.setFont(new Font(AppConfig.Fonts.FONT_FAMILY, Font.PLAIN, 12));
        copyright.setForeground(UIComponentFactory.themeColor("Label.disabledForeground", AppConfig.Colors.TEXT_SECONDARY));
        copyrightWrap.add(copyright);
        Dimension wrapSize = copyrightWrap.getPreferredSize();
        copyrightWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapSize.height));
        content.add(copyrightWrap);

        card.add(content, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        refresh();
    }

    /**
     * 刷新服务地址与支持的格式(端口或Office可用性变化后调用)。
     */
    public void refresh() {
        serviceLabel.setText(I18nManager.zhEn("服务地址", "Service URL")
            + ": http://127.0.0.1:" + settings.getPort());

        // 安装按钮仅在未安装LibreOffice时显示
        installBtn.setVisible(!printService.isOfficeConverterAvailable());

        formatsPanel.removeAll();

        // 完全支持(绿色)
        addFormatCard(AppConfig.Colors.STATUS_SUCCESS,
            I18nManager.zhEn("完全支持:", "Fully supported:"),
            null,
            new String[]{ I18nManager.zhEn("PDF文档", "PDF documents") });

        // 图片格式(蓝色)
        addFormatCard(AppConfig.Colors.BUTTON_PRIMARY,
            I18nManager.zhEn("图片格式", "Image formats"),
            I18nManager.zhEn("(自动转换为PDF):", "(auto-converted to PDF):"),
            new String[]{ "PNG, JPG, JPEG, GIF, BMP" });

        // Office格式(橙色, 需要LibreOffice)
        addFormatCard(AppConfig.Colors.STATUS_STARTING,
            I18nManager.zhEn("Office格式", "Office formats"),
            I18nManager.zhEn("(需要安装LibreOffice):", "(requires LibreOffice):"),
            new String[]{ "Word (.docx, .doc)", "Excel (.xlsx, .xls)", "PowerPoint (.pptx, .ppt)" });

        // 未安装LibreOffice时给出提示
        if (!printService.isOfficeConverterAvailable()) {
            addFormatNote(I18nManager.zhEn(
                "如未安装LibreOffice,Office格式将无法打印。",
                "If LibreOffice is not installed, Office formats cannot be printed."));
        }

        // 固定格式区高度, 防止 BoxLayout 把多余空间分给格式区导致卡片拉伸
        formatsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, formatsPanel.getPreferredSize().height));

        formatsPanel.revalidate();
        formatsPanel.repaint();
    }

    /** 在默认浏览器中打开服务健康检查页 */
    private void openHealthCheck() {
        String url = "http://127.0.0.1:" + settings.getPort() + "/health";
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI.create(url));
                    return;
                }
            }
            // 回退: Windows 用 rundll32 打开
            Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                I18nManager.zhEn("无法自动打开浏览器，请手动访问:\n", "Cannot open browser automatically, visit:\n") + url,
                AppConfig.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** 一节格式卡片: 左侧彩色竖条 + 淡色背景, 加粗标题 + ✅ 条目 */
    private void addFormatCard(Color accent, String boldTitle, String plainTitle, String[] items) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(tinted(accent));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 标题: 加粗部分 + 普通说明
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel bold = new JLabel(boldTitle);
        bold.setFont(SECTION_FONT);
        bold.setForeground(UIComponentFactory.themeColor("Label.foreground", AppConfig.Colors.TEXT_PRIMARY));
        titleRow.add(bold);
        if (plainTitle != null && !plainTitle.isEmpty()) {
            JLabel plain = new JLabel(plainTitle);
            plain.setFont(new Font(AppConfig.Fonts.FONT_FAMILY, Font.PLAIN, 15));
            plain.setForeground(UIComponentFactory.themeColor("Label.disabledForeground", AppConfig.Colors.TEXT_SECONDARY));
            titleRow.add(plain);
        }
        card.add(titleRow);
        card.add(Box.createVerticalStrut(6));

        // ✅ 条目
        for (String item : items) {
            JLabel label = new JLabel(CHECK_MARK + item);
            label.setFont(CHECK_FONT);
            label.setForeground(UIComponentFactory.themeColor("Label.disabledForeground", AppConfig.Colors.TEXT_SECONDARY));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(label);
            card.add(Box.createVerticalStrut(3));
        }

        formatsPanel.add(card);
        formatsPanel.add(Box.createVerticalStrut(8));
    }

    /** 底部说明文字 */
    private void addFormatNote(String text) {
        formatsPanel.add(Box.createVerticalStrut(4));
        JLabel label = new JLabel(text);
        label.setFont(UIComponentFactory.HINT_FONT);
        label.setForeground(UIComponentFactory.themeColor("Label.disabledForeground", AppConfig.Colors.TEXT_SECONDARY));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        formatsPanel.add(label);
    }

    /** 依据当前主题, 将强调色淡化为卡片背景(浅色更淡、深色更深) */
    private Color tinted(Color accent) {
        Color base = UIComponentFactory.themeColor("Panel.background", AppConfig.Colors.BACKGROUND);
        return blend(accent, base, isDark(base) ? 0.16 : 0.06);
    }

    private static Color blend(Color a, Color b, double t) {
        return new Color(
            (int) Math.round(a.getRed() * t + b.getRed() * (1 - t)),
            (int) Math.round(a.getGreen() * t + b.getGreen() * (1 - t)),
            (int) Math.round(a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    private static boolean isDark(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) < 128;
    }
}

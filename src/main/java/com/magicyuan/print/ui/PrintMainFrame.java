package com.magicyuan.print.ui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import com.magicyuan.print.SingleFileHTTPServer;
import com.magicyuan.print.config.AppConfig;
import com.magicyuan.print.config.AppSettings;
import com.magicyuan.print.config.ThemeManager;
import com.magicyuan.print.i18n.I18nManager;
import com.magicyuan.print.model.PrintQueueModel;
import com.magicyuan.print.service.PrintService;
import com.magicyuan.print.service.ServerStatusService;

/**
 * 打印应用程序主窗口
 * 页签式界面: 设置 / 网站白名单 / 任务 / 关于
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class PrintMainFrame extends JFrame implements ServerStatusService.StatusChangeListener {

    // 数据模型
    private final PrintQueueModel printQueueModel;

    // 服务层
    private final PrintService printService;

    private final ServerStatusService serverStatusService;

    // 系统托盘管理器
    private final SystemTrayManager systemTrayManager;

    // 应用设置
    private final AppSettings settings;

    // 主面板(顶部 + 页签内容)
    private JPanel mainPanel;

    // 页签面板
    private SettingsPanel settingsPanel;
    private WhitelistPanel whitelistPanel;
    private TasksPanel tasksPanel;
    private AboutPanel aboutPanel;

    // 按钮式页签切换(参考 TS 版分段控件)
    private final List<JButton> tabButtons = new ArrayList<JButton>();
    private final String[] tabKeys = {"settings", "whitelist", "tasks", "about"};
    private CardLayout contentLayout;
    private JPanel contentPanel;

    // 状态栏组件
    private JLabel statusTextLabel;

    private JLabel statusIndicator;

    // 标题框内本地端口标签
    private JLabel portLabel;

    private ServerStatusService.ServerStatus currentStatus = ServerStatusService.ServerStatus.STARTING;

    /**
     * 构造函数
     */
    public PrintMainFrame() {
        // 初始化数据模型
        this.printQueueModel = new PrintQueueModel();

        // 初始化配置与打印服务
        this.settings = AppSettings.load();
        this.printService = new PrintService(printQueueModel);
        this.printService.applySettings(settings);

        this.serverStatusService = new ServerStatusService();
        this.systemTrayManager = new SystemTrayManager(this);

        // 设置PrintService到HTTP服务器
        SingleFileHTTPServer.setPrintService(this.printService);

        // 初始化UI组件
        initializeComponents();

        // 跟随系统时, 系统深色状态变化后重建界面以刷新主题颜色
        ThemeManager.addSystemThemeListener(this::rebuildContent);

        // 设置事件监听器
        setupEventListeners();

        // 设置状态变化监听器
        serverStatusService.addStatusChangeListener(this);

        // 配置窗口
        configureWindow();
    }

    /**
     * 显示LibreOffice安装引导
     */
    private void showLibreOfficeGuide() {
        // 创建自定义对话框
        JDialog dialog = new JDialog(this, I18nManager.getString("window.multiformat.title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.setBackground(new Color(245, 247, 250));

        // 主内容面板
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(new Color(245, 247, 250));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 20, 0);

        // 标题
        JLabel titleLabel = new JLabel(I18nManager.getString("multiformat.title"));
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        titleLabel.setForeground(new Color(44, 62, 80));
        mainPanel.add(titleLabel, gbc);

        // 卡片1: 当前支持的格式 (绿色)
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JPanel supportedCard = createColorCard(
            I18nManager.getString("multiformat.supported.title"),
            new String[]{
                I18nManager.getString("multiformat.supported.pdf"),
                I18nManager.getString("multiformat.supported.image")
            },
            new Color(232, 245, 233),  // 浅绿背景
            new Color(76, 175, 80)      // 绿色边框
        );
        mainPanel.add(supportedCard, gbc);

        // 卡片2: Office格式未启用 (橙色)
        gbc.gridy++;
        JPanel unsupportedCard = createColorCard(
            I18nManager.getString("multiformat.unsupported.title"),
            new String[]{
                I18nManager.getString("multiformat.unsupported.word"),
                I18nManager.getString("multiformat.unsupported.excel"),
                I18nManager.getString("multiformat.unsupported.ppt")
            },
            new Color(255, 243, 224),  // 浅橙背景
            new Color(255, 152, 0)      // 橙色边框
        );
        mainPanel.add(unsupportedCard, gbc);

        // 卡片3: 如何启用 (蓝色)
        gbc.gridy++;
        JPanel guideCard = createColorCard(
            I18nManager.getString("multiformat.guide.title"),
            new String[]{
                I18nManager.getString("multiformat.guide.step1"),
                I18nManager.getString("multiformat.guide.step2"),
                I18nManager.getString("multiformat.guide.step3")
            },
            new Color(227, 242, 253),  // 浅蓝背景
            new Color(33, 150, 243)     // 蓝色边框
        );
        mainPanel.add(guideCard, gbc);

        // 提示文字
        gbc.gridy++;
        gbc.insets = new Insets(20, 0, 0, 0);
        JLabel promptLabel = new JLabel(I18nManager.getString("multiformat.prompt"));
        promptLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        promptLabel.setForeground(new Color(102, 102, 102));
        promptLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(promptLabel, gbc);

        // 填充剩余空间
        gbc.gridy++;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(Box.createGlue(), gbc);

        // 滚动面板
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(new Color(245, 247, 250));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        buttonPanel.setBackground(Color.WHITE);
        buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(230, 230, 230)));

        // 立即下载按钮
        JButton downloadButton = new JButton(I18nManager.getString("multiformat.button.download"));
        downloadButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        downloadButton.setForeground(Color.WHITE);
        downloadButton.setBackground(new Color(52, 152, 219));
        downloadButton.setBorder(BorderFactory.createEmptyBorder(10, 30, 10, 30));
        downloadButton.setFocusPainted(false);
        downloadButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        downloadButton.setOpaque(true);

        downloadButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                downloadButton.setBackground(new Color(41, 128, 185));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                downloadButton.setBackground(new Color(52, 152, 219));
            }
        });

        downloadButton.addActionListener(e -> {
            openLibreOfficeDownloadPage();
            dialog.dispose();
        });

        // 稍后安装按钮
        JButton laterButton = new JButton(I18nManager.getString("multiformat.button.later"));
        laterButton.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        laterButton.setForeground(new Color(95, 99, 104));
        laterButton.setBackground(Color.WHITE);
        laterButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(218, 220, 224), 1),
            BorderFactory.createEmptyBorder(9, 29, 9, 29)
        ));
        laterButton.setFocusPainted(false);
        laterButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        laterButton.setOpaque(true);

        laterButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                laterButton.setBackground(new Color(248, 249, 250));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                laterButton.setBackground(Color.WHITE);
            }
        });

        laterButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(downloadButton);
        buttonPanel.add(laterButton);

        // 组装对话框
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // 设置对话框属性
        dialog.setSize(520, AppConfig.WINDOW_HEIGHT);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * 创建彩色卡片面板
     */
    private JPanel createColorCard(String title, String[] items, Color bgColor, Color borderColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, borderColor),  // 左侧彩色边框
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 标题
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        titleLabel.setForeground(new Color(51, 51, 51));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(10));

        // 内容项
        for (String item : items) {
            JLabel itemLabel = new JLabel(item);
            itemLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
            itemLabel.setForeground(new Color(68, 68, 68));
            itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(itemLabel);
            card.add(Box.createVerticalStrut(5));
        }

        // 添加底部填充
        card.add(Box.createVerticalGlue());

        return card;
    }

    /**
     * 打开LibreOffice下载页面
     */
    private void openLibreOfficeDownloadPage() {
        try {
            String url = "https://www.libreoffice.org/download/";

            // 根据操作系统选择合适的打开方式
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(java.net.URI.create(url));
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                this,
                I18nManager.zhEn("无法自动打开浏览器，请手动访问:", "Cannot open browser automatically, please visit:")
                    + "\nhttps://www.libreoffice.org/download/",
                I18nManager.getString("print.title"),
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    /**
     * 初始化UI组件
     */
    private void initializeComponents() {
        setLayout(new BorderLayout());

        // 主面板(保持原有顶部结构)
        mainPanel = UIComponentFactory.createMainPanel();

        // 顶部面板: 服务名称 + 状态显示 + 打印按钮(保持原样)
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 中部: 页签面板(替代原打印队列列表)
        mainPanel.add(createTabPanel(), BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * 创建顶部面板(服务名称 + 状态显示 + 打印按钮)
     */
    private JPanel createTopPanel() {
        JPanel topPanel = UIComponentFactory.createTopPanel();

        JPanel leftInfoPanel = UIComponentFactory.createLeftInfoPanel(settings.getPort());
        portLabel = (JLabel) ((BorderLayout) leftInfoPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        topPanel.add(leftInfoPanel, BorderLayout.WEST);

        statusIndicator = UIComponentFactory.createStatusIndicator();
        statusTextLabel = UIComponentFactory.createStatusTextLabel();
        JPanel statusPanel = UIComponentFactory.createStatusPanel(statusIndicator, statusTextLabel);
        topPanel.add(statusPanel, BorderLayout.EAST);

        return topPanel;
    }

    /**
     * 创建页签区域(按钮式分段控件 + 卡片布局, 参考 TS 版)
     */
    private JPanel createTabPanel() {
        settingsPanel = new SettingsPanel(settings, printService, new Runnable() {
            @Override
            public void run() {
                onSettingsChanged();
            }
        }, new Runnable() {
            @Override
            public void run() {
                rebuildContent();
            }
        });
        whitelistPanel = new WhitelistPanel(settings);
        tasksPanel = new TasksPanel(printQueueModel, printService);
        aboutPanel = new AboutPanel(settings, printService, new Runnable() {
            @Override
            public void run() {
                showLibreOfficeGuide();
            }
        });

        JPanel wrapper = new JPanel(new BorderLayout(0, 12));
        wrapper.setOpaque(false);

        // 顶部按钮条(靠左紧凑排列)
        JPanel northWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        northWrap.setOpaque(false);
        northWrap.add(createTabBar());
        wrapper.add(northWrap, BorderLayout.NORTH);

        // 内容区(卡片布局切换)
        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.setOpaque(false);
        contentPanel.add(settingsPanel, tabKeys[0]);
        contentPanel.add(whitelistPanel, tabKeys[1]);
        contentPanel.add(tasksPanel, tabKeys[2]);
        contentPanel.add(aboutPanel, tabKeys[3]);
        wrapper.add(contentPanel, BorderLayout.CENTER);

        // 默认选中第一个页签
        selectTab(0);
        return wrapper;
    }

    /**
     * 创建按钮式页签条
     */
    private JPanel createTabBar() {
        JPanel bar = new TabBarPanel();
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 3));

        String[] labels = {
            com.magicyuan.print.i18n.I18nManager.zhEn("设置", "Settings"),
            com.magicyuan.print.i18n.I18nManager.zhEn("网站白名单", "Whitelist"),
            com.magicyuan.print.i18n.I18nManager.zhEn("任务", "Tasks"),
            com.magicyuan.print.i18n.I18nManager.zhEn("关于", "About")
        };
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            JButton button = new TabButton(labels[i]);
            button.addActionListener(e -> selectTab(index));
            bar.add(button);
            tabButtons.add(button);
        }

        // 统一页签按钮宽度: 取各按钮最大宽度(网站白名单最宽, 含加粗态)作为标准
        int maxWidth = 0;
        for (JButton button : tabButtons) {
            Font plain = button.getFont();
            Font bold = plain.deriveFont(Font.BOLD);
            maxWidth = Math.max(maxWidth, button.getPreferredSize().width);
            button.setFont(bold);
            maxWidth = Math.max(maxWidth, button.getPreferredSize().width);
            button.setFont(plain);
        }
        for (JButton button : tabButtons) {
            Dimension size = button.getPreferredSize();
            button.setPreferredSize(new Dimension(maxWidth, size.height));
            button.setMinimumSize(new Dimension(maxWidth, size.height));
            button.setMaximumSize(new Dimension(maxWidth, size.height));
        }
        return bar;
    }

    /**
     * 切换当前页签
     */
    private void selectTab(int index) {
        for (int i = 0; i < tabButtons.size(); i++) {
            ((TabButton) tabButtons.get(i)).setActive(i == index);
        }
        contentLayout.show(contentPanel, tabKeys[index]);
    }

    /**
     * 设置保存后的回调: 应用打印设置、刷新白名单与关于页。
     */
    private void onSettingsChanged() {
        printService.applySettings(settings);
        SingleFileHTTPServer.applySettings(settings);
        aboutPanel.refresh();
        if (portLabel != null) {
            portLabel.setText(UIComponentFactory.portLabelText(settings.getPort()));
        }
    }

    /**
     * 语言或主题切换后重建内容区(刷新所有面板文本与样式)
     */
    private void rebuildContent() {
        tabButtons.clear();
        mainPanel.removeAll();
        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        mainPanel.add(createTabPanel(), BorderLayout.CENTER);
        mainPanel.revalidate();
        mainPanel.repaint();

        // 恢复当前服务器状态显示
        statusTextLabel.setText(currentStatus.getMessage());
        UIComponentFactory.updateStatusIndicator(statusIndicator, currentStatus);
    }

    /**
     * 设置事件监听器
     */
    private void setupEventListeners() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });
    }

    /**
     * 处理窗口关闭事件
     */
    private void handleWindowClosing() {
        if (!systemTrayManager.showTrayIcon()) {
            JOptionPane.showMessageDialog(
                this,
                "当前系统无法显示托盘图标，窗口保持打开。",
                "PrintBridge",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        setVisible(false);
    }

    /**
     * 启动时根据服务启动结果决定是否显示主窗体:
     * 启动成功 -> 隐藏到系统托盘(托盘不可用时回退为显示窗口);
     * 启动失败 -> 显示主窗体,便于查看状态与修改配置。
     */
    public void startInSystemTray() {
        // 启动时立即启动HTTP服务: 托盘运行同样要对外提供打印服务,不能等窗口打开
        serverStatusService.startServerAsync().thenAccept(success -> {
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    // 服务启动成功, 隐藏到系统托盘; 托盘不可用时回退为显示窗口
                    if (!systemTrayManager.showTrayIcon()) {
                        setVisible(true);
                    }
                } else {
                    // 服务启动失败, 显示主窗体以便查看/修改配置
                    setVisible(true);
                }
            });
        });
    }

    /**
     * 配置窗口属性
     */
    private void configureWindow() {
        setTitle(AppConfig.APP_NAME);
        // 应用图标(标题栏/任务栏/Alt-Tab, 提供多尺寸供系统选择)
        setIconImages(java.util.Arrays.asList(
            SystemTrayManager.createPrinterIcon(16),
            SystemTrayManager.createPrinterIcon(32),
            SystemTrayManager.createPrinterIcon(64)
        ));
        setSize(AppConfig.WINDOW_WIDTH, AppConfig.WINDOW_HEIGHT);
        setLocationRelativeTo(null);

        // 固定窗口大小,与TS版一致(960x680,不可调整/最大化)
        setResizable(false);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // 添加窗口状态监听器 - 支持从任务栏恢复
        addWindowStateListener(e -> {
            if ((e.getNewState() & java.awt.Frame.ICONIFIED) == 0 && (e.getOldState() & java.awt.Frame.ICONIFIED) != 0) {
                // 窗口从最小化恢复
                setVisible(true);
                toFront();
                requestFocus();
            }
        });
    }

    /**
     * 服务器状态变化监听器实现
     */
    @Override
    public void onStatusChanged(ServerStatusService.ServerStatus status) {
        currentStatus = status;
        // 在事件调度线程中更新UI
        SwingUtilities.invokeLater(() -> {
            statusTextLabel.setText(status.getMessage());
            UIComponentFactory.updateStatusIndicator(statusIndicator, status);
        });
    }

    /**
     * 获取打印队列模型（供外部使用）
     */
    public PrintQueueModel getPrintQueueModel() {
        return printQueueModel;
    }

    /**
     * 获取打印服务（供UI使用）
     */
    public PrintService getPrintService() {
        return printService;
    }

    /**
     * 获取系统托盘管理器
     */
    public SystemTrayManager getSystemTrayManager() {
        return systemTrayManager;
    }

    /**
     * 按钮式页签(参考 TS 版分段控件)
     */
    private static class TabButton extends JButton {
        private final Font baseFont = new Font(AppConfig.Fonts.FONT_FAMILY, Font.PLAIN, 15);
        private boolean active;
        private boolean hover;

        TabButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setFont(baseFont);

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            setFont(baseFont.deriveFont(active ? Font.BOLD : Font.PLAIN));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            Color activeBg = UIManager.getColor("Panel.background");
            Color activeFg = UIManager.getColor("Label.foreground");
            Color inactiveFg = UIManager.getColor("Button.foreground");
            Color hoverBg = UIManager.getColor("Button.hoverBackground");
            Color border = UIManager.getColor("Component.borderColor");

            if (active && activeBg != null) {
                g2.setColor(activeBg);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
                if (border != null) {
                    g2.setColor(border);
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
                }
            } else if (hover && hoverBg != null) {
                g2.setColor(hoverBg);
                g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            }

            g2.setColor(active ? activeFg : inactiveFg);
            FontMetrics fm = g2.getFontMetrics();
            int x = (w - fm.stringWidth(getText())) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    /**
     * 圆角分段控件容器(参考 TS 版 TabsList)
     */
    private static class TabBarPanel extends JPanel {
        TabBarPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color barBg = UIManager.getColor("Button.background");
            if (barBg != null) {
                g2.setColor(barBg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            g2.dispose();
        }
    }
}

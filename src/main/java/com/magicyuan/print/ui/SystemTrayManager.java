package com.magicyuan.print.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JWindow;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import com.magicyuan.print.i18n.I18nManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统托盘管理器
 * 负责创建和管理系统托盘图标
 * 
 * @author Magic_yuan
 * @version 2.1.3
 */
public class SystemTrayManager {

    private static final Logger logger = LoggerFactory.getLogger(SystemTrayManager.class);

    private SystemTray systemTray;
    private TrayIcon trayIcon;
    private JFrame mainFrame;
    private boolean isInitialized = false;
    private boolean isTrayIconVisible = false;

    public SystemTrayManager(JFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /**
     * 初始化系统托盘
     */
    public boolean initialize() {
        if (isInitialized) {
            return true;
        }

        // 检查系统是否支持托盘
        if (!SystemTray.isSupported()) {
            logger.warn("当前系统不支持系统托盘功能");
            return false;
        }

        try {
            systemTray = SystemTray.getSystemTray();

            // 创建托盘图标 (使用简单的图标,也可以加载图片)
            Image image = createPrinterIcon(16);
            
            trayIcon = new TrayIcon(image, "PrintBridge");
            trayIcon.setImageAutoSize(true);

            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                        restoreWindow();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    showTrayMenuOnPopupTrigger(e);
                }
            });

            isInitialized = true;
            logger.info("系统托盘初始化成功");
            return true;

        } catch (Exception e) {
            logger.error("初始化系统托盘失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 显示托盘图标
     */
    public boolean showTrayIcon() {
        if (isTrayIconVisible) {
            return true;
        }
        if (!isInitialized && !initialize()) {
            return false;
        }

        try {
            systemTray.add(trayIcon);
            isTrayIconVisible = true;
            logger.info("托盘图标已显示");
            
            // 延迟显示通知,避免被标题覆盖
            SwingUtilities.invokeLater(() -> {
                try {
                    Thread.sleep(100);
                    // 显示提示消息
                    trayIcon.displayMessage(
                        "PrintBridge - 打印服务",
                        "已最小化到系统托盘,服务继续运行\n双击托盘图标可恢复窗口",
                        TrayIcon.MessageType.INFO
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return true;
        } catch (AWTException e) {
            logger.error("显示托盘图标失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 隐藏托盘图标
     */
    public void hideTrayIcon() {
        if (trayIcon != null && systemTray != null) {
            systemTray.remove(trayIcon);
            isTrayIconVisible = false;
            logger.info("托盘图标已隐藏");
        }
    }

    /**
     * 恢复窗口
     */
    public void restoreWindow() {
        mainFrame.setVisible(true);
        mainFrame.setExtendedState(JFrame.NORMAL);
        mainFrame.toFront();
        mainFrame.requestFocus();
        hideTrayIcon();
        logger.info("窗口已从托盘恢复");
    }

    /**
     * 创建打印机图标(应用窗口/任务栏与托盘共用)
     *
     * @param size 图标边长(像素)
     */
    public static Image createPrinterIcon(int size) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 以 16x16 为基准, 按目标尺寸等比缩放
        double u = size / 16.0;
        Color body = new Color(115, 123, 131);
        Color bodyDark = new Color(92, 100, 108);
        Color paperLine = new Color(186, 196, 208);

        // 顶部纸张(白色, 明显露出机身, 带浅灰描边与文字线条)
        g.setColor(new Color(203, 213, 225));
        fillRound(g, 4.8 * u, 0.3 * u, 6.4 * u, 6.2 * u, 1.0 * u);
        g.setColor(Color.WHITE);
        fillRound(g, 5.1 * u, 0.6 * u, 5.8 * u, 5.8 * u, 0.8 * u);
        g.setColor(paperLine);
        fillRound(g, 5.9 * u, 1.7 * u, 4.2 * u, 0.7 * u, 0.3 * u);
        fillRound(g, 5.9 * u, 3.2 * u, 4.2 * u, 0.7 * u, 0.3 * u);
        fillRound(g, 5.9 * u, 4.7 * u, 2.8 * u, 0.7 * u, 0.3 * u);

        // 打印机机身
        g.setColor(body);
        fillRound(g, 1.5 * u, 4.5 * u, 13.0 * u, 9.0 * u, 2.5 * u);
        // 机身顶部进纸区(深色)
        g.setColor(bodyDark);
        fillRound(g, 1.5 * u, 4.5 * u, 13.0 * u, 2.2 * u, 2.5 * u);
        // 底部出纸托盘(深色, 略凸出机身)
        g.setColor(bodyDark);
        fillRound(g, 4.0 * u, 12.8 * u, 8.0 * u, 2.2 * u, 1.0 * u);

        g.dispose();
        return image;
    }

    /** 按逻辑坐标绘制圆角矩形(便于等比缩放) */
    private static void fillRound(Graphics2D g, double x, double y, double w, double h, double arc) {
        g.fillRoundRect((int) Math.round(x), (int) Math.round(y),
            (int) Math.round(w), (int) Math.round(h),
            (int) Math.round(arc), (int) Math.round(arc));
    }

    private void showTrayMenuOnPopupTrigger(MouseEvent event) {
        if (event.isPopupTrigger() || event.getButton() == MouseEvent.BUTTON3) {
            showTrayMenu(event.getXOnScreen(), event.getYOnScreen());
        }
    }

    private void showTrayMenu(int screenX, int screenY) {
        SwingUtilities.invokeLater(() -> {
            JWindow anchor = new JWindow(mainFrame);
            anchor.setType(Window.Type.POPUP);
            anchor.setAlwaysOnTop(true);
            anchor.setBounds(screenX, screenY, 1, 1);

            JPopupMenu popup = createTrayMenu();
            popup.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                    anchor.dispose();
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent event) {
                    anchor.dispose();
                }
            });

            anchor.setVisible(true);
            popup.show(anchor.getContentPane(), 0, 0);
        });
    }

    private JPopupMenu createTrayMenu() {
        JPopupMenu popup = new JPopupMenu();
        Font menuFont = new Font("Microsoft YaHei", Font.PLAIN, 12);

        JMenuItem restoreItem = new JMenuItem(I18nManager.getString("tray.menu.restore"));
        restoreItem.setFont(menuFont);
        restoreItem.addActionListener(event -> restoreWindow());
        popup.add(restoreItem);

        popup.addSeparator();

        JMenuItem exitItem = new JMenuItem(I18nManager.getString("tray.menu.exit"));
        exitItem.setFont(menuFont);
        exitItem.addActionListener(event -> {
            hideTrayIcon();
            System.exit(0);
        });
        popup.add(exitItem);

        return popup;
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        hideTrayIcon();
    }
}

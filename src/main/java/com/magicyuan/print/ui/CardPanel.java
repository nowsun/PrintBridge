package com.magicyuan.print.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * 白底(主题感知)圆角卡片, 带 1px 描边。设置页及其它页签共用的外框。
 *
 * @author Magic_yuan
 * @version 2.1.4
 */
public class CardPanel extends JPanel {

    public CardPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg = UIManager.getColor("Panel.background");
        Color border = UIManager.getColor("Component.borderColor");
        if (bg != null) {
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        }
        if (border != null) {
            g2.setColor(border);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        }
        g2.dispose();
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ui;



import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;

/**
 * Created with IntelliJ IDEA.
 * User: noah
 * Date: 7/24/12
 * Time: 8:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class ColorfulJProgressBar extends JProgressBar {
    private Color color = Color.green;

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    private class ColorfulProgressUI extends BasicProgressBarUI {
//        private double greenOverPercent=(2d/3d)*100d;

//        private double yellowOverPercent=(1d/3d)*100d;

        private JProgressBar jProgressBar;

        private ColorfulProgressUI(JProgressBar jProgressBar) {
            this.jProgressBar = jProgressBar;
        }

        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {

            double percent=100d*this.jProgressBar.getValue()/(this.jProgressBar.getMaximum()-this.jProgressBar.getMinimum());

            this.jProgressBar.setForeground(color);
//            if (percent > this.greenOverPercent) {
//                this.jProgressBar.setForeground(Color.green);
//            } else if (percent > this.yellowOverPercent) {
//                this.jProgressBar.setForeground(Color.yellow);
//            } else {
//                this.jProgressBar.setForeground(Color.red);
//            }
            super.paintDeterminate(g, c);
        }

    }

    public ColorfulJProgressBar() {
        init();
        setForeground(Color.darkGray);
        setBackground(Color.darkGray);
        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
    }

    public ColorfulJProgressBar(Color color) {
        this();
        this.color = color;
    }
    
    public ColorfulJProgressBar(int orient) {
        super(orient);
        init();
    }

    public ColorfulJProgressBar(int min, int max) {
        super(min, max);
        init();
    }

    public ColorfulJProgressBar(int orient, int min, int max) {
        super(orient, min, max);
        init();
    }

    public ColorfulJProgressBar(BoundedRangeModel newModel) {
        super(newModel);
        init();
    }

    private void init(){
        this.setBorderPainted(false);
        this.setUI(new ColorfulProgressUI(this));
    }
}
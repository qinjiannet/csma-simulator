/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ui;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author Totoro
 */
public class MainFrame extends javax.swing.JFrame {

    private SimpleDateFormat SDF = new SimpleDateFormat("HH:MM:ss.SSS");
    private boolean isChannelBusy = false;
    private Node[] nodes = null;
    private final int MAX_NUM = 5;
    private javax.swing.JPanel colorPanels[] = null;
    private javax.swing.JLabel cellLabels[] = null;
    private javax.swing.JLabel signalLabels[] = null;
    private javax.swing.JTextField timerFields[] = null;
    private javax.swing.JButton buttons[] = null;
    private Thread nodeThreads[] = null;
    private javax.swing.JTextField failFields[] = null;
    private boolean isRunning = false;
    private int machineNum = 0;
    private int rate = 2000;
    private int curSender = 0;
    private int totalNum = 0;
    private int successNum = 0;
    private DataWindow dataWindow = null;
    private HashSet<Integer> senderSet = new HashSet<Integer>();
    public boolean runFlags[] = new boolean[5];
    
    public synchronized void setTotal(int num) {
        totalNum = num;
    }
    
    public synchronized int getTotal() {
        return totalNum;
    }
    
    public synchronized void setSuccess(int num) {
        successNum = num;
    }
    
    public synchronized int getSuccess() {
        return successNum;
    }

    public synchronized void addSender(int id) {
        //curSender++;
        senderSet.add(id);
    }

    public synchronized void removeSender(int id) {
        //curSender--;
        senderSet.remove(id);
    }

    public synchronized int countSender() {
        //return curSender;
        return senderSet.size();
    }

    public synchronized void setChannelBusy(boolean flag) {
        isChannelBusy = flag;
        channelLabel.setText(flag ? "busy" : "idle");
        if (!isChannelBusy()) {
            progressBar.setBackground(Color.black);
        }
    }

    public synchronized boolean isChannelBusy() {
        return isChannelBusy;
    }
    public synchronized boolean isChannelAvailable() {
        return !isChannelBusy() || countSender() < 2;
    }

    public boolean sendMsg(int id, int length) {
        System.out.println("Node " + id + " found channelbusy =  " + isChannelBusy());
        
        if (isChannelBusy()) {
            return false;
        }
        addSender(id);
        progressBar.setBackground(colorPanels[id].getBackground());
        channelDashboard.setBackground(colorPanels[id].getBackground());
        progressBar.repaint();
        for (int i = 0; i < length; i++) {
            progressBar.setValue((int) ((i + 1.0) / length * 100));
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (!isChannelAvailable()) {
                channelDashboard.setBackground(Color.red);
                throw new RuntimeException("Collision Detected!");
            }
        }
        try {
            Thread.sleep(20);
        } catch (InterruptedException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
        removeSender(id);
        signalLabels[id].setVisible(false);
        return true;
    }

    public void sendJam(int id) {
        progressBar.setBackground(Color.red);
        try {
            Thread.sleep(400);
        } catch (InterruptedException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public class DataSender extends Thread {
        int cnt = 0;
        public DataSender() {
            super();
            dataWindow = new DataWindow();
            dataWindow.setTitle("Success Ratio Chart");
            //dataWindow.setVisible(true);
        }
        public void run() {
            while (true) {
                Date date = new Date();
                if (getTotal() > 0) {
                    int ratio = 100 * getSuccess() / getTotal();
                    dataWindow.addData(date.getTime(), ratio);
                }
                try {
                    Thread.sleep(rate); //Sample Rate = 1 / 2s
                } catch (InterruptedException ex) {
                    Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    
    public class ChannelDetector extends Thread {
        int silentCnt = 0;
        int CEILING = 1000000;

        public void run() {
            while (true) {
                if (countSender() == 0) {
                    silentCnt++;
                    silentCnt = Math.min(CEILING, silentCnt);
                    if (silentCnt >= 2) {
                        channelDashboard.setBackground(Color.white);
                        setChannelBusy(false);
                    }
                } else {
                    silentCnt = 0;
                    setChannelBusy(true);
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public synchronized void appendInfo(String str) {
        String timeStr = SDF.format(new Date());
        logPanel.setText(logPanel.getText() + timeStr + " " + str + "\n");
    }

    public void clearProgressBar() {
        progressBar.setValue(0);
        //progressBar.setBackground(Color.black);
    }

    public class Node implements Runnable {
        public final static int RANDOM_BACKOFF = 0;
        public final static int POWER_BACKOFF = 1;
        private final int MIN_SLEEP = 1000;
        private final int MAX_SLEEP = 3000;
        private final int EXP_CEILING = 8000;
        long sleepTime = 1200;
        private int id;
        private int failTime = 0;
        private int strategy = RANDOM_BACKOFF;

        public int getStrategy() {
            return strategy;
        }

        public void setStrategy(int strategy) {
            this.strategy = strategy;
        }
        
        private long getFallback() {
            if (strategy == RANDOM_BACKOFF) {
                return MIN_SLEEP + (long) ((MAX_SLEEP - MIN_SLEEP) * Math.random());
            } else {
                return Math.min(EXP_CEILING,400 * (1 << Math.min(failTime,28))) + (long) ((MAX_SLEEP - MIN_SLEEP) * Math.random());
            }
        }

        public Node() {
        }

        public Node(int id) {
            this.id = id;
        }
        
        public void run() {
            while (runFlags[id]) {
                Boolean result = null;
                try {
                    appendInfo("Node " + id + " prepared to send msg");
                    signalLabels[id].setVisible(true);
                    timerFields[id].setText("Sending");
                    timerFields[id].setBackground(Color.green);
                    result = sendMsg(id, 25);
                    if (result) {
                        setSuccess(getSuccess() + 1);
                        timerFields[id].setText("Succeeded");
                        timerFields[id].setBackground(Color.cyan);
                        failTime = 0;
                    } else {
                        timerFields[id].setText("Canceld");
                        timerFields[id].setBackground(Color.orange);
                        failTime++;
                    }
                    appendInfo("Node " + id + " data send " + (result ? "finished" : "give up"));
                } catch (RuntimeException re) {
                    appendInfo("Node " + id + " data send interuppted");
                    sendJam(id);
                    appendInfo("Node " + id + " sending Jam");
                    timerFields[id].setText("Interrupted");
                    timerFields[id].setBackground(Color.red);
                    failTime++;
                    removeSender(id);
                } finally {
                    if (result == null || result) {
                        setTotal(getTotal() + 1);
                    }
                    signalLabels[id].setVisible(false);
                    clearProgressBar();
                    dokeField.setText(getSuccess() + "/" + getTotal());
                    failFields[id].setText(failTime + "");
                }
                try {
                    Thread.sleep(getFallback());
                } catch (InterruptedException ex) {
                    Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            removeSender(id);
        }
    }

    public class Timer implements Runnable {

        public Timer() {
        }
        private int i = 0;

        public void run() {
            while (true) {
                try {
                    Thread.sleep(10 * (100 - rate) + 500);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Timer.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (isRunning) {
                    //progressBar.setValue((int) (Math.random() * 100));
                    //signal5.setVisible((Math.random() > 0.5));
                    progressBar.setBackground(colorPanels[i % machineNum].getBackground());
                    progressBar.repaint();
                    signalClear();
                    signalLabels[i % machineNum].setVisible(true);
                    i++;
                } else {
                    i = 0;
                }
            }
        }
    }

    /**
     * Creates new form MainFrame
     */
    public MainFrame() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        colorPanel1 = new javax.swing.JPanel();
        colorPanel2 = new javax.swing.JPanel();
        colorPanel3 = new javax.swing.JPanel();
        cell1 = new javax.swing.JLabel();
        progressBar = new ColorfulJProgressBar();
        cell5 = new javax.swing.JLabel();
        signal5 = new javax.swing.JLabel();
        spinner = new javax.swing.JSpinner();
        controlButton = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        cell2 = new javax.swing.JLabel();
        cell3 = new javax.swing.JLabel();
        cell4 = new javax.swing.JLabel();
        signal1 = new javax.swing.JLabel();
        signal2 = new javax.swing.JLabel();
        signal3 = new javax.swing.JLabel();
        signal4 = new javax.swing.JLabel();
        colorPanel4 = new javax.swing.JPanel();
        colorPanel5 = new javax.swing.JPanel();
        rateSlider = new javax.swing.JSlider();
        jLabel5 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        logPanel = new javax.swing.JTextPane();
        timerText1 = new javax.swing.JTextField();
        timerText2 = new javax.swing.JTextField();
        timerText3 = new javax.swing.JTextField();
        timerText4 = new javax.swing.JTextField();
        timerText5 = new javax.swing.JTextField();
        channelDashboard = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        channelLabel = new javax.swing.JTextField();
        jButton6 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        dokeField = new javax.swing.JTextField();
        failText1 = new javax.swing.JTextField();
        failText2 = new javax.swing.JTextField();
        failText3 = new javax.swing.JTextField();
        failText4 = new javax.swing.JTextField();
        failText5 = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jRadioButton1 = new javax.swing.JRadioButton();
        jRadioButton2 = new javax.swing.JRadioButton();
        chartButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("MAP Simulator");
        setResizable(false);

        jPanel1.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        colorPanel1.setBackground(new java.awt.Color(255, 175, 175));
        colorPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        colorPanel1.setPreferredSize(new java.awt.Dimension(30, 65));

        javax.swing.GroupLayout colorPanel1Layout = new javax.swing.GroupLayout(colorPanel1);
        colorPanel1.setLayout(colorPanel1Layout);
        colorPanel1Layout.setHorizontalGroup(
            colorPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );
        colorPanel1Layout.setVerticalGroup(
            colorPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 58, Short.MAX_VALUE)
        );

        jPanel1.add(colorPanel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 10, 30, 60));

        colorPanel2.setBackground(new java.awt.Color(51, 153, 255));
        colorPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        colorPanel2.setPreferredSize(new java.awt.Dimension(30, 65));

        javax.swing.GroupLayout colorPanel2Layout = new javax.swing.GroupLayout(colorPanel2);
        colorPanel2.setLayout(colorPanel2Layout);
        colorPanel2Layout.setHorizontalGroup(
            colorPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );
        colorPanel2Layout.setVerticalGroup(
            colorPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 58, Short.MAX_VALUE)
        );

        jPanel1.add(colorPanel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(60, 10, 30, 60));

        colorPanel3.setBackground(new java.awt.Color(204, 255, 255));
        colorPanel3.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        javax.swing.GroupLayout colorPanel3Layout = new javax.swing.GroupLayout(colorPanel3);
        colorPanel3.setLayout(colorPanel3Layout);
        colorPanel3Layout.setHorizontalGroup(
            colorPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );
        colorPanel3Layout.setVerticalGroup(
            colorPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 58, Short.MAX_VALUE)
        );

        jPanel1.add(colorPanel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 10, 30, 60));

        cell1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/cellphone.png"))); // NOI18N
        cell1.setText("m1");
        cell1.setToolTipText("");
        cell1.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        jPanel1.add(cell1, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 260, -1, -1));

        progressBar.setDoubleBuffered(true);
        jPanel1.add(progressBar, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 130, 410, 30));

        cell5.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/cellphone.png"))); // NOI18N
        cell5.setText("m5");
        jPanel1.add(cell5, new org.netbeans.lib.awtextra.AbsoluteConstraints(390, 260, -1, -1));

        signal5.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/signal.png"))); // NOI18N
        signal5.setText("jLabel3");
        jPanel1.add(signal5, new org.netbeans.lib.awtextra.AbsoluteConstraints(390, 170, 30, 80));

        spinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, 5, 1));
        spinner.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                spinnerPropertyChange(evt);
            }
        });
        jPanel1.add(spinner, new org.netbeans.lib.awtextra.AbsoluteConstraints(520, 10, 60, 20));

        controlButton.setFont(new java.awt.Font("Calibri", 0, 18)); // NOI18N
        controlButton.setText("stop");
        controlButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                controlButtonActionPerformed(evt);
            }
        });
        jPanel1.add(controlButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(680, 10, 70, 25));

        jLabel4.setFont(new java.awt.Font("宋体", 0, 14)); // NOI18N
        jLabel4.setText("Sample Rate:");
        jPanel1.add(jLabel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 10, -1, 20));

        cell2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/cellphone.png"))); // NOI18N
        cell2.setText("m2");
        jPanel1.add(cell2, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 260, -1, -1));

        cell3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/cellphone.png"))); // NOI18N
        cell3.setText("m3");
        jPanel1.add(cell3, new org.netbeans.lib.awtextra.AbsoluteConstraints(220, 260, 60, -1));

        cell4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/cellphone.png"))); // NOI18N
        cell4.setText("m4");
        jPanel1.add(cell4, new org.netbeans.lib.awtextra.AbsoluteConstraints(310, 260, -1, -1));

        signal1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/signal.png"))); // NOI18N
        signal1.setText("jLabel3");
        jPanel1.add(signal1, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 170, 30, 80));

        signal2.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/signal.png"))); // NOI18N
        signal2.setText("jLabel3");
        jPanel1.add(signal2, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 170, 30, 80));

        signal3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/signal.png"))); // NOI18N
        signal3.setText("jLabel3");
        jPanel1.add(signal3, new org.netbeans.lib.awtextra.AbsoluteConstraints(220, 170, 30, 80));

        signal4.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/signal.png"))); // NOI18N
        signal4.setText("jLabel3");
        jPanel1.add(signal4, new org.netbeans.lib.awtextra.AbsoluteConstraints(310, 170, 30, 80));

        colorPanel4.setBackground(new java.awt.Color(255, 200, 0));
        colorPanel4.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        javax.swing.GroupLayout colorPanel4Layout = new javax.swing.GroupLayout(colorPanel4);
        colorPanel4.setLayout(colorPanel4Layout);
        colorPanel4Layout.setHorizontalGroup(
            colorPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );
        colorPanel4Layout.setVerticalGroup(
            colorPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 58, Short.MAX_VALUE)
        );

        jPanel1.add(colorPanel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(120, 10, 30, 60));

        colorPanel5.setBackground(new java.awt.Color(204, 0, 204));
        colorPanel5.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        javax.swing.GroupLayout colorPanel5Layout = new javax.swing.GroupLayout(colorPanel5);
        colorPanel5.setLayout(colorPanel5Layout);
        colorPanel5Layout.setHorizontalGroup(
            colorPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 28, Short.MAX_VALUE)
        );
        colorPanel5Layout.setVerticalGroup(
            colorPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 58, Short.MAX_VALUE)
        );

        jPanel1.add(colorPanel5, new org.netbeans.lib.awtextra.AbsoluteConstraints(150, 10, 30, 60));

        rateSlider.setMaximum(3000);
        rateSlider.setMinimum(1000);
        rateSlider.setValue(2000);
        rateSlider.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                rateSliderPropertyChange(evt);
            }
        });
        jPanel1.add(rateSlider, new org.netbeans.lib.awtextra.AbsoluteConstraints(290, 10, 150, -1));

        jLabel5.setFont(new java.awt.Font("宋体", 0, 14)); // NOI18N
        jLabel5.setText("Number:");
        jPanel1.add(jLabel5, new org.netbeans.lib.awtextra.AbsoluteConstraints(470, 10, -1, 20));

        jScrollPane1.setViewportView(logPanel);

        jPanel1.add(jScrollPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(480, 90, 270, 330));
        jPanel1.add(timerText1, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 360, 60, 30));
        jPanel1.add(timerText2, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 360, 60, 30));
        jPanel1.add(timerText3, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 360, 60, 30));
        jPanel1.add(timerText4, new org.netbeans.lib.awtextra.AbsoluteConstraints(300, 360, 60, 30));
        jPanel1.add(timerText5, new org.netbeans.lib.awtextra.AbsoluteConstraints(390, 360, 60, 30));

        channelDashboard.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout channelDashboardLayout = new javax.swing.GroupLayout(channelDashboard);
        channelDashboard.setLayout(channelDashboardLayout);
        channelDashboardLayout.setHorizontalGroup(
            channelDashboardLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 60, Short.MAX_VALUE)
        );
        channelDashboardLayout.setVerticalGroup(
            channelDashboardLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 30, Short.MAX_VALUE)
        );

        jPanel1.add(channelDashboard, new org.netbeans.lib.awtextra.AbsoluteConstraints(380, 90, 60, 30));

        jButton1.setText("Start");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton1, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 400, -1, -1));

        jButton2.setText("Start");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton2, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 400, -1, -1));

        jButton3.setText("Start");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton3, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 400, -1, -1));

        jButton4.setText("Start");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton4, new org.netbeans.lib.awtextra.AbsoluteConstraints(300, 400, -1, -1));

        jButton5.setText("Start");
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton5, new org.netbeans.lib.awtextra.AbsoluteConstraints(390, 400, -1, -1));

        jLabel1.setText("channelStatus:");
        jPanel1.add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 100, -1, -1));
        jPanel1.add(channelLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(300, 100, 40, -1));

        jButton6.setText("Clean");
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton6, new org.netbeans.lib.awtextra.AbsoluteConstraints(680, 425, -1, -1));

        jButton7.setFont(new java.awt.Font("Calibri", 0, 18)); // NOI18N
        jButton7.setText("start");
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton7, new org.netbeans.lib.awtextra.AbsoluteConstraints(600, 10, 70, 25));

        jLabel2.setText("Statistics:");
        jPanel1.add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(490, 65, -1, -1));
        jPanel1.add(dokeField, new org.netbeans.lib.awtextra.AbsoluteConstraints(560, 60, 70, -1));
        jPanel1.add(failText1, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 430, 60, -1));
        jPanel1.add(failText2, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 430, 60, -1));
        jPanel1.add(failText3, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 430, 60, -1));
        jPanel1.add(failText4, new org.netbeans.lib.awtextra.AbsoluteConstraints(300, 430, 60, -1));
        jPanel1.add(failText5, new org.netbeans.lib.awtextra.AbsoluteConstraints(390, 430, 60, -1));

        jLabel3.setText("Failed");
        jPanel1.add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 435, -1, 10));

        jLabel6.setText("Status");
        jPanel1.add(jLabel6, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 370, -1, -1));

        buttonGroup1.add(jRadioButton1);
        jRadioButton1.setSelected(true);
        jRadioButton1.setText("Random Backoff");
        jRadioButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jRadioButton1, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 40, -1, -1));

        buttonGroup1.add(jRadioButton2);
        jRadioButton2.setText("Expo Backoff");
        jRadioButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButton2ActionPerformed(evt);
            }
        });
        jPanel1.add(jRadioButton2, new org.netbeans.lib.awtextra.AbsoluteConstraints(210, 60, -1, -1));

        chartButton.setText("show chart");
        chartButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chartButtonActionPerformed(evt);
            }
        });
        jPanel1.add(chartButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(660, 60, -1, -1));

        jTabbedPane1.addTab("CSMA", jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 772, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 503, Short.MAX_VALUE)
        );

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void demoStart() {
        controlButton.setText("stop");
        spinner.setEnabled(false);
    }

    private void signalClear() {
        for (javax.swing.JLabel signalLabel : signalLabels) {
            signalLabel.setVisible(false);
        }
    }

    private void demoStop() {
        controlButton.setText("start");
        spinner.setEnabled(true);
        signalClear();
    }

    private void spinnerPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_spinnerPropertyChange

        machineNum = new Integer(spinner.getValue() + "");
        //System.out.println(machineNum);
        colorPanels = new javax.swing.JPanel[]{colorPanel1, colorPanel2, colorPanel3, colorPanel4, colorPanel5};
        cellLabels = new javax.swing.JLabel[]{cell1, cell2, cell3, cell4, cell5};
        signalLabels = new javax.swing.JLabel[]{signal1, signal2, signal3, signal4, signal5};
        timerFields = new javax.swing.JTextField[]{timerText1, timerText2, timerText3, timerText4, timerText5};
        buttons = new javax.swing.JButton[]{jButton1,jButton2,jButton3,jButton4,jButton5};
        failFields = new javax.swing.JTextField[]{failText1, failText2, failText3, failText4, failText5};
        for (int i = 0; i < machineNum; i++) {
            if (cellLabels[i] != null) {
                cellLabels[i].setVisible(true);
            }
            if (colorPanels[i] != null) {
                colorPanels[i].setVisible(true);
            }
            if (timerFields[i] != null) {
                timerFields[i].setVisible(true);
            }
            if (buttons[i] != null) {
                buttons[i].setVisible(true);
            }
            if (failFields[i] != null) {
                failFields[i].setVisible(true);
            }
        }
        for (int i = machineNum; i < MAX_NUM; i++) {
            if (cellLabels[i] != null) {
                cellLabels[i].setVisible(false);
            }
            if (colorPanels[i] != null) {
                colorPanels[i].setVisible(false);
            }
            if (timerFields[i] != null) {
                timerFields[i].setVisible(false);
            }
            if (buttons[i] != null) {
                buttons[i].setVisible(false);
            }
            if (failFields[i] != null) {
                failFields[i].setVisible(false);
            }
        }
    }//GEN-LAST:event_spinnerPropertyChange

    private void rateSliderPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_rateSliderPropertyChange
        rate = rateSlider.getValue();
    }//GEN-LAST:event_rateSliderPropertyChange

    private void startThread(int id) {
        nodeThreads[id] = new Thread(nodes[id]);
        runFlags[id] = true;
        nodeThreads[id].start();
    }
    private void stopThread(int id) {
        runFlags[id] = false;
         //nodeThreads[id].stop();
         timerFields[id].setText("");
         timerFields[id].setBackground(Color.white);
         failFields[id].setText("");
    }
    private boolean isRunning(int id) {
        return nodeThreads[id] != null && nodeThreads[id].isAlive();
    }
    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        int idx = 0;
        boolean running = isRunning(idx);
        jButton1.setText(running ? "start" : "stop");
        if (running) {
            stopThread(idx);
        } else {
            startThread(idx);
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        int idx = 1;
        boolean running = isRunning(idx);
        jButton2.setText(running ? "start" : "stop");
        if (running) {
            stopThread(idx);
        } else {
            startThread(idx);
        }
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        int idx = 2;
        boolean running = isRunning(idx);
        jButton3.setText(running ? "start" : "stop");
        if (running) {
            stopThread(idx);
        } else {
            startThread(idx);
        }
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        int idx = 3;
        boolean running = isRunning(idx);
        jButton4.setText(running ? "start" : "stop");
        if (running) {
            stopThread(idx);
        } else {
            startThread(idx);
        }
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        int idx = 4;
        boolean running = isRunning(idx);
        jButton5.setText(running ? "start" : "stop");
        if (running) {
            stopThread(idx);
        } else {
            startThread(idx);
        }
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        logPanel.setText("");
    }//GEN-LAST:event_jButton6ActionPerformed

    public void setButtons(String str) {
        jButton1.setText(str);
        jButton2.setText(str);
        jButton3.setText(str);
        jButton4.setText(str);
        jButton5.setText(str);
    }
    private void controlButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_controlButtonActionPerformed
//        isRunning = !isRunning;
//        if (isRunning) {
//            demoStart();
//        } else {
//            demoStop();
//        }
        for (int i = 0; i < machineNum; i++) {
            stopThread(i);
        }
//        for (Thread thread : nodeThreads) {
//            if (thread != null && thread.isAlive()) {
//                thread.stop();
//            }
//        }
        //setButtons("start");
    }//GEN-LAST:event_controlButtonActionPerformed

    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
        for (int i = 0; i < machineNum; i++) {
            startThread(i);
        }
        setButtons("stop");
    }//GEN-LAST:event_jButton7ActionPerformed

    private void jRadioButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton1ActionPerformed
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null)
                nodes[i].setStrategy(Node.RANDOM_BACKOFF);
        }
    }//GEN-LAST:event_jRadioButton1ActionPerformed

    private void jRadioButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButton2ActionPerformed
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null)
                nodes[i].setStrategy(Node.POWER_BACKOFF);
        }
    }//GEN-LAST:event_jRadioButton2ActionPerformed

    private void chartButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chartButtonActionPerformed
        if (dataWindow == null) {
            return;
        }
        if (dataWindow.isVisible()) {
            dataWindow.setVisible(false);
            chartButton.setText("show chart");
        } else {
            dataWindow.setVisible(true);
            chartButton.setText("hide chart");
        }
    }//GEN-LAST:event_chartButtonActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
//        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
//        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
//         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
//         */
//        try {
//            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//                if ("Nimbus".equals(info.getName())) {
//                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
//                    break;
//                }
//            }
//        } catch (ClassNotFoundException ex) {
//            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (InstantiationException ex) {
//            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (IllegalAccessException ex) {
//            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
//            java.util.logging.Logger.getLogger(MainFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//        }
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
        //</editor-fold>
        final MainFrame mFrame = new MainFrame();
        //mFrame.demoStop();
        mFrame.signalClear();
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                mFrame.setVisible(true);
            }
        });
        //mFrame.timerThread = new Thread(mFrame.new Timer());
        //mFrame.timerThread.start();
        mFrame.new ChannelDetector().start();
        mFrame.nodes = new Node[5];
        mFrame.nodeThreads = new Thread[5];
        mFrame.new DataSender().start();
        for (int i = 0; i < 5; i++) {
            mFrame.nodes[i] = mFrame.new Node(i);
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JLabel cell1;
    private javax.swing.JLabel cell2;
    private javax.swing.JLabel cell3;
    private javax.swing.JLabel cell4;
    private javax.swing.JLabel cell5;
    private javax.swing.JPanel channelDashboard;
    private javax.swing.JTextField channelLabel;
    private javax.swing.JButton chartButton;
    private javax.swing.JPanel colorPanel1;
    private javax.swing.JPanel colorPanel2;
    private javax.swing.JPanel colorPanel3;
    private javax.swing.JPanel colorPanel4;
    private javax.swing.JPanel colorPanel5;
    private javax.swing.JButton controlButton;
    private javax.swing.JTextField dokeField;
    private javax.swing.JTextField failText1;
    private javax.swing.JTextField failText2;
    private javax.swing.JTextField failText3;
    private javax.swing.JTextField failText4;
    private javax.swing.JTextField failText5;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButton jRadioButton1;
    private javax.swing.JRadioButton jRadioButton2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTextPane logPanel;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JSlider rateSlider;
    private javax.swing.JLabel signal1;
    private javax.swing.JLabel signal2;
    private javax.swing.JLabel signal3;
    private javax.swing.JLabel signal4;
    private javax.swing.JLabel signal5;
    private javax.swing.JSpinner spinner;
    private javax.swing.JTextField timerText1;
    private javax.swing.JTextField timerText2;
    private javax.swing.JTextField timerText3;
    private javax.swing.JTextField timerText4;
    private javax.swing.JTextField timerText5;
    // End of variables declaration//GEN-END:variables
}

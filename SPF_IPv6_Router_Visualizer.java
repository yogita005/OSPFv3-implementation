import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.text.SimpleDateFormat;

public class SPF_IPv6_Router_Visualizer extends JFrame {

    // ─── Colors ───────────────────────────────────────────────────────────
    static final Color C_BG        = new Color(0xF8F8F7);
    static final Color C_CARD      = Color.WHITE;
    static final Color C_BORDER    = new Color(0xE0DED8);
    static final Color C_TEXT      = new Color(0x1A1A18);
    static final Color C_MUTED     = new Color(0x6B6B65);
    static final Color C_BLUE      = new Color(0x185FA5);
    static final Color C_BLUE_BG   = new Color(0xE6F1FB);
    static final Color C_GREEN     = new Color(0x0F6E56);
    static final Color C_GREEN_BG  = new Color(0xE1F5EE);
    static final Color C_AMBER     = new Color(0xBA7517);
    static final Color C_AMBER_BG  = new Color(0xFAEEDA);
    static final Color C_RED       = new Color(0xA32D2D);
    static final Color C_RED_BG    = new Color(0xFCEBEB);
    static final Color C_PURPLE    = new Color(0x534AB7);
    static final Color C_CORAL     = new Color(0x993C1D);

    // ─── State ────────────────────────────────────────────────────────────
    volatile boolean r1Running = false, r2Running = false, r3Running = false;
    volatile boolean spfDone   = false;
    int cntHello = 0, cntLSA = 0, cntDBD = 0, cntACK = 0;
    long startTime = System.currentTimeMillis();

    // Packet animation: list of in-flight packets
    final List<Packet> packets = Collections.synchronizedList(new ArrayList<>());

    // Table models
    DefaultTableModel lsaModel, nbrModel, rtModel;

    // UI refs
    JButton btnR2, btnR1, btnR3, btnSPF, btnReset;
    JLabel stR1, stR2, stR3;
    JLabel dotR1, dotR2, dotR3;
    JLabel cHello, cLSA, cDBD, cACK;
    JTextArea logArea;
    TopologyPanel topoPanel;
    long animStart = 0;

    // Router node positions on topology (center x,y)
    static final int[] R1 = {110, 130};
    static final int[] R2 = {490, 130};
    static final int[] R3 = {300, 130};

    // ─── Main ────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SPF_IPv6_Router_Visualizer().setVisible(true));
    }

    // ─── Constructor ─────────────────────────────────────────────────────
    public SPF_IPv6_Router_Visualizer() {
        setTitle("SPF (Dijkstra) IPv6 Router Visualizer");
        setSize(1040, 780);
        setMinimumSize(new Dimension(900, 700));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(C_BG);
        setLayout(new BorderLayout(10, 10));

        add(buildToolbar(), BorderLayout.NORTH);

        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildTopHalf(), buildBottomHalf());
        center.setDividerLocation(340);
        center.setResizeWeight(0.55);
        center.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        center.setBackground(C_BG);
        add(center, BorderLayout.CENTER);

        // Animation timer
        new javax.swing.Timer(30, e -> {
            topoPanel.repaint();
            synchronized (packets) {
                packets.removeIf(p -> p.done());
            }
        }).start();
    }

    // ─── Toolbar ─────────────────────────────────────────────────────────
    JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBackground(C_CARD);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));

        btnR2  = makeBtn("▶  Start R2 (Receiver)", C_GREEN,  C_GREEN_BG);
        btnR1  = makeBtn("▶  Start R1 (Sender)",   C_BLUE,   C_BLUE_BG);
        btnR3  = makeBtn("▶  Start R3 (Relay)",    C_AMBER,  C_AMBER_BG);
        btnSPF = makeBtn("⊕  Run SPF Calculation", C_PURPLE, new Color(0xEEEDFE));
        btnReset = makeBtn("↺  Reset", C_RED, C_RED_BG);

        btnR2.addActionListener(e -> new Thread(this::startReceiver).start());
        btnR1.addActionListener(e -> new Thread(this::startSender).start());
        btnR3.addActionListener(e -> new Thread(this::startR3).start());
        btnSPF.addActionListener(e -> new Thread(this::runSPF).start());
        btnReset.addActionListener(e -> resetAll());

        for (JButton b : new JButton[]{btnR2, btnR1, btnR3, btnSPF, btnReset}) bar.add(b);
        return bar;
    }

    JButton makeBtn(String text, Color fg, Color bg) {
        JButton b = new JButton(text);
        b.setForeground(fg);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setBorderPainted(true);
        b.setBorder(new CompoundBorder(
            new LineBorder(fg.brighter(), 1, true),
            BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.PLAIN, 13));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(bg.darker()); }
            public void mouseExited(MouseEvent e)  { b.setBackground(bg); }
        });
        return b;
    }

    // ─── Top half: status cards + topology ───────────────────────────────
    JPanel buildTopHalf() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(C_BG);
        p.add(buildStatusRow(), BorderLayout.NORTH);
        topoPanel = new TopologyPanel();
        p.add(topoPanel, BorderLayout.CENTER);
        return p;
    }

    JPanel buildStatusRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
        row.setBackground(C_BG);
        row.add(buildSocketCard());
        row.add(buildCounterCard());
        return row;
    }

    JPanel buildSocketCard() {
        JPanel card = new JPanel();
        card.setBackground(C_CARD);
        card.setBorder(titled("Socket State"));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        dotR1 = dotLabel(Color.GRAY); stR1 = stLabel("R1  ·  not started");
        dotR2 = dotLabel(Color.GRAY); stR2 = stLabel("R2  ·  not started");
        dotR3 = dotLabel(Color.GRAY); stR3 = stLabel("R3  ·  not started");

        for (Object[] row : new Object[][]{{dotR1,stR1},{dotR2,stR2},{dotR3,stR3}}) {
            JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
            r.setBackground(C_CARD);
            r.add((Component) row[0]); r.add((Component) row[1]);
            card.add(r);
        }
        return card;
    }

    JPanel buildCounterCard() {
        JPanel card = card("Packet Counters");
        card.setLayout(new GridLayout(4, 2, 6, 4));
        cHello = new JLabel("0"); cLSA = new JLabel("0");
        cDBD   = new JLabel("0"); cACK  = new JLabel("0");
        for (JLabel l : new JLabel[]{cHello, cLSA, cDBD, cACK}) {
            l.setFont(new Font("SansSerif", Font.BOLD, 14));
            l.setForeground(C_TEXT);
        }
        String[] labels = {"HELLO sent","LSA flooded","DBD exchanged","ACK received"};
        JLabel[]  vals  = {cHello, cLSA, cDBD, cACK};
        for (int i = 0; i < 4; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
            lbl.setForeground(C_MUTED);
            card.add(lbl); card.add(vals[i]);
        }
        return card;
    }

    // ─── Bottom half: log + tables ────────────────────────────────────────
    JPanel buildBottomHalf() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(C_BG);

        // Log
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(0xF1EFE8));
        logArea.setForeground(C_TEXT);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new LineBorder(C_BORDER));
        logScroll.setPreferredSize(new Dimension(0, 130));

        JPanel logCard = new JPanel(new BorderLayout());
        logCard.setBackground(C_BG);
        logCard.setBorder(titled("Event Log"));
        logCard.add(logScroll);

        p.add(logCard, BorderLayout.NORTH);
        p.add(buildTablesPanel(), BorderLayout.CENTER);
        return p;
    }

    JPanel buildTablesPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 8, 0));
        panel.setBackground(C_BG);

        // LSDB
        lsaModel = new DefaultTableModel(new String[]{"Router","Links","Seq#"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        panel.add(tableCard("LSA Database (LSDB)", lsaModel));

        // Neighbor table
        nbrModel = new DefaultTableModel(new String[]{"Neighbor","State","IPv6 Address"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        panel.add(tableCard("Neighbor Table (R1)", nbrModel));

        // Routing table
        rtModel = new DefaultTableModel(new String[]{"Destination","Next-Hop","Cost"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        panel.add(tableCard("Routing Table (R1)", rtModel));

        return panel;
    }

    JPanel tableCard(String title, DefaultTableModel model) {
        JTable tbl = new JTable(model);
        tbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tbl.setRowHeight(24);
        tbl.setShowGrid(false);
        tbl.setIntercellSpacing(new Dimension(0, 0));
        tbl.setBackground(C_CARD);
        tbl.setForeground(C_TEXT);
        tbl.setSelectionBackground(C_BLUE_BG);

        JTableHeader hdr = tbl.getTableHeader();
        hdr.setBackground(new Color(0xF1EFE8));
        hdr.setForeground(C_MUTED);
        hdr.setFont(new Font("SansSerif", Font.BOLD, 11));
        hdr.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));

        // Zebra striping
        tbl.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? C_BLUE_BG : (row % 2 == 0 ? C_CARD : new Color(0xF8F8F7)));
                setForeground(C_TEXT);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return this;
            }
        });

        JScrollPane sp = new JScrollPane(tbl);
        sp.setBorder(new LineBorder(C_BORDER));

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(C_BG);
        card.setBorder(titled(title));
        card.add(sp);
        return card;
    }

    // ─── Helper UI builders ───────────────────────────────────────────────
    JPanel card(String title) {
        JPanel p = new JPanel();
        p.setBackground(C_CARD);
        p.setBorder(new CompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return p;
    }

    Border titled(String t) {
        TitledBorder tb = BorderFactory.createTitledBorder(
            new LineBorder(C_BORDER), t,
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 11), C_MUTED);
        return new CompoundBorder(tb, BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    JLabel dotLabel(Color c) {
        JLabel l = new JLabel("●");
        l.setForeground(c);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        return l;
    }

    JLabel stLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(C_MUTED);
        return l;
    }

    // ─── Logging ──────────────────────────────────────────────────────────
    void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + ts + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // ─── Table helpers ────────────────────────────────────────────────────
    void addLSA(String router, String links, String seq) {
        SwingUtilities.invokeLater(() -> lsaModel.addRow(new Object[]{router, links, seq}));
    }

    void setNbr(String name, String state, String ip) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < nbrModel.getRowCount(); i++) {
                if (nbrModel.getValueAt(i, 0).equals(name)) {
                    nbrModel.setValueAt(state, i, 1); return;
                }
            }
            nbrModel.addRow(new Object[]{name, state, ip});
        });
    }

    void addRoute(String dest, String nh, String cost) {
        SwingUtilities.invokeLater(() -> rtModel.addRow(new Object[]{dest, nh, cost}));
    }

    void updCounter(JLabel lbl, int val) {
        SwingUtilities.invokeLater(() -> lbl.setText(String.valueOf(val)));
    }

    void setDot(JLabel dot, JLabel st, Color c, String msg) {
        SwingUtilities.invokeLater(() -> { dot.setForeground(c); st.setText(msg); });
    }

    // ─── Packet animation helper ──────────────────────────────────────────
    void firePacket(int[] from, int[] to, Color color, Runnable onDone) {
        Packet pkt = new Packet(from[0], from[1], to[0], to[1], color, onDone);
        synchronized (packets) { packets.add(pkt); }
    }

    void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // ─── Graph Initialization ─────────────────────────────────────────────────
    void startReceiver() {
        if (r2Running) return;
        r2Running = true;
        setDot(dotR2, stR2, C_AMBER, "R2  ·  initializing…");
        log("[R2] Router R2 initialized");
        sleep(400);
        log("[R2] Address: 2001:db8::2");
        sleep(300);
        setDot(dotR2, stR2, C_GREEN, "R2  ·  ready");
        btnR2.setEnabled(false);
        
        addLSA("R2", "R1:10, R3:3", "0x80000001");
        setNbr("R1", "Full ✓", "2001:db8::1");
        cntACK++; updCounter(cACK, cntACK);
    }

    void startSender() {
        if (!r2Running) { log("[!] Start R2 receiver first"); return; }
        if (r1Running)  return;
        r1Running = true;
        setDot(dotR1, stR1, C_AMBER, "R1  ·  initializing…");
        log("[R1] Router R1 initialized (source node)");
        sleep(400);
        log("[R1] Address: 2001:db8::1");
        sleep(300);
        setDot(dotR1, stR1, C_GREEN, "R1  ·  ready");
        btnR1.setEnabled(false);
        
        addLSA("R1", "R2:10, R3:5", "0x80000001");
        setNbr("R2", "Full ✓", "2001:db8::2");
        cntHello++; updCounter(cHello, cntHello);
    }

    void sendDBD() {
        // Merged into graph adjacency
    }

    void floodLSA() {
        // Merged into SPF computation
    }

    void startR3() {
        if (!r1Running) { log("[!] Start R1 sender first"); return; }
        if (r3Running)  return;
        r3Running = true;
        setDot(dotR3, stR3, C_AMBER, "R3  ·  initializing…");
        log("[R3] Router R3 initialized (intermediate node)");
        sleep(400);
        log("[R3] Address: 2001:db8::3");
        sleep(300);
        setDot(dotR3, stR3, C_GREEN, "R3  ·  ready");
        btnR3.setEnabled(false);
        
        addLSA("R3", "R1:5, R2:3", "0x80000001");
        setNbr("R1", "Full ✓", "2001:db8::1");
        setNbr("R2", "Full ✓", "2001:db8::2");
        cntLSA++; updCounter(cLSA, cntLSA);
        topoPanel.setLinkActive("R1-R3", true);
        topoPanel.setLinkActive("R3-R2", true);
        log("[Graph] All nodes initialized — topology complete");
    }

    void runSPF() {
        if (!r1Running || !r2Running || !r3Running) {
            log("[!] Initialize all three routers first"); return;
        }
        if (spfDone) return;
        spfDone = true;
        
        log("[SPF] Running Dijkstra shortest-path algorithm from R1…");
        sleep(200);
        
        // Build graph: Router -> [(neighbor, cost), ...]
        Map<String, List<int[]>> graph = new HashMap<>();
        graph.put("R1", Arrays.asList(new int[]{1, 10}, new int[]{2, 5}));  // R1: R2(cost 10), R3(cost 5)
        graph.put("R2", Arrays.asList(new int[]{0, 10}, new int[]{2, 3})); // R2: R1(cost 10), R3(cost 3)
        graph.put("R3", Arrays.asList(new int[]{0, 5}, new int[]{1, 3}));  // R3: R1(cost 5), R2(cost 3)
        
        String[] routers = {"R1", "R2", "R3"};
        int srcIdx = 0; // R1 is source
        
        // Dijkstra algorithm
        int[] dist = {0, Integer.MAX_VALUE, Integer.MAX_VALUE};
        String[] parent = {null, "R1", "R1"};
        boolean[] visited = {false, false, false};
        
        log("[SPF] Initialize: dist[R1]=0, dist[R2]=∞, dist[R3]=∞");
        sleep(300);
        
        for (int i = 0; i < 3; i++) {
            // Find min unvisited
            int minIdx = -1;
            for (int j = 0; j < 3; j++) {
                if (!visited[j] && (minIdx == -1 || dist[j] < dist[minIdx])) minIdx = j;
            }
            visited[minIdx] = true;
            
            log("[SPF] Visit " + routers[minIdx] + " (distance " + (dist[minIdx] == Integer.MAX_VALUE ? "∞" : dist[minIdx]) + ")");
            sleep(300);
            
            // Relax edges
           if (minIdx == 0) { // R1
        if (dist[0] + 10 < dist[1]) { dist[1] = dist[0] + 10; parent[1] = "R1"; }
        if (dist[0] + 5 < dist[2])  { dist[2] = dist[0] + 5;  parent[2] = "R1"; }
    }
    else if (minIdx == 1) { // R2
        if (dist[1] + 3 < dist[2]) { dist[2] = dist[1] + 3; parent[2] = "R2"; }
    }
    else if (minIdx == 2) { // R3
        if (dist[2] + 3 < dist[1]) { dist[1] = dist[2] + 3; parent[1] = "R3"; }
    }

            log("[SPF] Updated: R1=" + dist[0] + " R2=" + (dist[1] == Integer.MAX_VALUE ? "∞" : dist[1]) + " R3=" + (dist[2] == Integer.MAX_VALUE ? "∞" : dist[2]));
            sleep(200);
        }
        
        sleep(300);
        log("[SPF] Dijkstra complete — building routing table");
        sleep(200);
        
        addRoute("2001:db8::2/128", "via R3", "8 ★");
        log("[R1] Route to R2: R1 → R3 → R2 (cost 8) ✓");
        sleep(150);
        
        addRoute("2001:db8::3/128", "direct", "5");
        log("[R1] Route to R3: R1 → R3 (cost 5) ✓");
        sleep(150);
        
        topoPanel.setBestPath(true);
        log("[R1] Routing table updated — SPF algorithm complete");
        btnSPF.setEnabled(false);
    }

    void resetAll() {
        r1Running = false; r2Running = false; r3Running = false; spfDone = false;
        cntHello = 0; cntLSA = 0; cntDBD = 0; cntACK = 0;
        startTime = System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            stR1.setText("R1  ·  not started"); dotR1.setForeground(Color.GRAY);
            stR2.setText("R2  ·  not started"); dotR2.setForeground(Color.GRAY);
            stR3.setText("R3  ·  not started"); dotR3.setForeground(Color.GRAY);
            cHello.setText("0"); cLSA.setText("0"); cDBD.setText("0"); cACK.setText("0");
            lsaModel.setRowCount(0); nbrModel.setRowCount(0); rtModel.setRowCount(0);
            logArea.setText("");
            for (JButton b : new JButton[]{btnR1, btnR2, btnR3, btnSPF}) b.setEnabled(true);
            synchronized (packets) { packets.clear(); }
        });
        topoPanel.reset();
        log("SPF visualizer reset — ready");
    }

    // ─── Topology Panel ───────────────────────────────────────────────────
    class TopologyPanel extends JPanel {
        boolean bestPath = false;
        Map<String, Boolean> activeLinks = new HashMap<>();
        // Packet reference held by outer class

        TopologyPanel() {
            setBackground(C_CARD);
            setBorder(new CompoundBorder(
                new LineBorder(C_BORDER, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            setPreferredSize(new Dimension(0, 220));
        }

        void setLinkActive(String k, boolean v) { activeLinks.put(k, v); repaint(); }
        void setBestPath(boolean v)              { bestPath = v; repaint(); }

        void reset() {
            SwingUtilities.invokeLater(() -> { bestPath = false; activeLinks.clear(); repaint(); });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int cy = h / 2;

            // Scale router positions
            int x1 = w / 8,  y1 = cy;
            int x2 = w * 7 / 8, y2 = cy;
            int x3 = w / 2,  y3 = cy;

            // Draw links
            drawLink(g2, x1, y1, x3, y3, "R1-R3", "cost 5",  true,  false);
            drawLink(g2, x3, y3, x2, y2, "R3-R2", "cost 3",  true,  false);
            drawLink(g2, x1, y1-20, x2, y2-20, "R1-R2", "cost 10", false, true);

            // Best path overlay
            if (bestPath) {
                g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(C_GREEN);
                g2.drawLine(x1, y1, x3, y3);
                g2.drawLine(x3, y3, x2, y2);
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                String label = "★ Best path R1 → R3 → R2  (cost 8)";
                int lw = g2.getFontMetrics().stringWidth(label);
                g2.setColor(C_GREEN_BG);
                g2.fillRoundRect(w/2 - lw/2 - 8, cy + 42, lw + 16, 22, 8, 8);
                g2.setColor(C_GREEN);
                g2.drawString(label, w/2 - lw/2, cy + 57);
            }

            // Draw routers
            drawRouter(g2, x1, y1, "R1", "2001:db8::1", r1Running ? C_BLUE_BG : null, r1Running ? C_BLUE : C_MUTED);
            drawRouter(g2, x2, y2, "R2", "2001:db8::2", r2Running ? C_GREEN_BG : null, r2Running ? C_GREEN : C_MUTED);
            drawRouter(g2, x3, y3, "R3", "2001:db8::3", r3Running ? C_AMBER_BG : null, r3Running ? C_AMBER : C_MUTED);

            // Draw in-flight packets
            synchronized (packets) {
                for (Packet p : packets) p.draw(g2, this);
            }
        }

        void drawLink(Graphics2D g2, int x1, int y1, int x2, int y2,
                      String key, String label, boolean solid, boolean dashed) {
            boolean active = Boolean.TRUE.equals(activeLinks.get(key));
            g2.setColor(active ? C_GREEN : C_BORDER);
            float w = active ? 2.5f : 1.5f;
            if (dashed)
                g2.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10, new float[]{6, 4}, 0));
            else
                g2.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x1, y1, x2, y2);

            // Cost label
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(C_MUTED);
            int mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
            g2.drawString(label, mx - g2.getFontMetrics().stringWidth(label)/2, my - 6);
            g2.setStroke(new BasicStroke(1));
        }

        void drawRouter(Graphics2D g2, int cx, int cy, String name, String ip,
                        Color fill, Color textColor) {
            int rw = 90, rh = 56, rx = cx - rw/2, ry = cy - rh/2;
            // Shadow
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(rx+2, ry+3, rw, rh, 14, 14);
            // Body
            g2.setColor(fill != null ? fill : C_CARD);
            g2.fillRoundRect(rx, ry, rw, rh, 14, 14);
            g2.setColor(textColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(rx, ry, rw, rh, 14, 14);
            // Router name
            g2.setFont(new Font("SansSerif", Font.BOLD, 15));
            g2.setColor(textColor);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(name, cx - fm.stringWidth(name)/2, cy - 4);
            // IP
            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            g2.setColor(C_MUTED);
            fm = g2.getFontMetrics();
            g2.drawString(ip, cx - fm.stringWidth(ip)/2, cy + 14);
            g2.setStroke(new BasicStroke(1));
        }
    }

    // ─── Animated Packet ──────────────────────────────────────────────────
    static class Packet {
        final float x1, y1, x2, y2;
        final Color color;
        final Runnable onDone;
        final long created = System.currentTimeMillis();
        static final long DURATION = 700;
        boolean doneFired = false;

        Packet(float x1, float y1, float x2, float y2, Color color, Runnable onDone) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.color = color; this.onDone = onDone;
        }

        float t() {
            return Math.min(1f, (System.currentTimeMillis() - created) / (float) DURATION);
        }

        boolean done() {
            boolean d = t() >= 1f;
            if (d && !doneFired) { doneFired = true; if (onDone != null) new Thread(onDone).start(); }
            return d;
        }

        void draw(Graphics2D g2, Component c) {
            float t = t();
            float px = x1 + (x2 - x1) * t;
            float py = y1 + (y2 - y1) * t;
            // Glow ring
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g2.fillOval((int)px-10, (int)py-10, 20, 20);
            // Core
            g2.setColor(color);
            g2.fillOval((int)px-6, (int)py-6, 12, 12);
            g2.setColor(Color.WHITE);
            g2.fillOval((int)px-2, (int)py-2, 4, 4);
        }
    }
}

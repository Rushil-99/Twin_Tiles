import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * DominosaGame — Main controller.
 * Layout:
 *   LEFT  half → Title + Board (centre) + Scores + Controls (bottom)
 *   RIGHT half → Status + How-to-Play instructions (scrollable)
 */
public class DominosaGame extends JFrame {
    private static final String GAME_TITLE = "Twin Tiles";

    private DominoBoard   board;
    private GamePanel     gamePanel;
    private ScoringHelper scoringHelper;

    // UI widgets
    private JLabel        statusLabel;
    private JLabel        humanScoreLabel;
    private JLabel        cpuScoreLabel;
    private JButton       newGameButton;
    private JButton       hintButton;
    private JButton       undoButton;
    private JCheckBox     visualizeCheckBox;
    private JToggleButton sniperButton;
    private JButton       dpOracleButton;
    private JLabel        cpuThinkingLabel;
    private JCheckBox     heatmapCheckBox;

    // Game state
    private int     humanScore  = 0;
    private int     cpuScore    = 0;
    private boolean isHumanTurn = true;
    private boolean gameOver    = false;
    private volatile boolean cpuAnimationRunning;
    private volatile boolean oracleAnimationRunning;

    private int     hintsUsed  = 0;
    private boolean sniperUsed = false;

    private static final int MAX_HINTS    = 3;
    private static final int CROWD_BONUS  = 2;
    private static final int CPU_DELAY_MS = 1200;

    // ── Poppins font (base size 15 — increased from 13) ──────────────────
    static Font POPPINS_REGULAR;
    static Font POPPINS_BOLD;
    static Font POPPINS_SEMIBOLD;
    static {
        try {
            POPPINS_REGULAR  = Font.createFont(Font.TRUETYPE_FONT,
                new java.io.File("Poppins-Regular.ttf")).deriveFont(15f);
            POPPINS_BOLD     = Font.createFont(Font.TRUETYPE_FONT,
                new java.io.File("Poppins-Bold.ttf")).deriveFont(15f);
            POPPINS_SEMIBOLD = Font.createFont(Font.TRUETYPE_FONT,
                new java.io.File("Poppins-SemiBold.ttf")).deriveFont(15f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(POPPINS_REGULAR);
            ge.registerFont(POPPINS_BOLD);
            ge.registerFont(POPPINS_SEMIBOLD);
        } catch (Exception ex) {
            POPPINS_REGULAR  = new Font("SansSerif", Font.PLAIN, 15);
            POPPINS_BOLD     = new Font("SansSerif", Font.BOLD,  15);
            POPPINS_SEMIBOLD = new Font("SansSerif", Font.BOLD,  15);
        }
    }

    // ── Palette (Light Mode) ─────────────────────────────────────────────
    static final Color BG_DEEP      = new Color(240, 243, 252);
    static final Color BG_PANEL     = new Color(228, 233, 248);
    static final Color BG_CARD      = new Color(255, 255, 255);
    static final Color ACCENT       = new Color(37,  99,  235);
    static final Color ACCENT2      = new Color(180, 120,   0);
    static final Color TEXT_PRIMARY = new Color(25,  30,   60);
    static final Color TEXT_MUTED   = new Color(100, 110, 150);
    static final Color SUCCESS      = new Color(22,  163,  74);
    static final Color DANGER       = new Color(220,  38,  38);
    static final Color DIVIDER      = new Color(200, 210, 235);

    // ── Score breakdown ──────────────────────────────────────────────────
    public static class ScoreBreakdown {
        public final int pips, crowdBonus, multiplier, totalScore;
        ScoreBreakdown(int p, int cb, int m, int t) {
            pips=p; crowdBonus=cb; multiplier=m; totalScore=t;
        }
        String getFormattedStatus() {
            if (multiplier > 1)
                return "\uD83C\uDF1F COMBO!  " + pips + " pips + " + crowdBonus
                     + " crowd \u00D7" + multiplier + " chain = +" + totalScore + " pts!";
            return "Nice move!  " + pips + " pips + " + crowdBonus
                 + " crowd = +" + totalScore + " pts";
        }
        String getFormattedPopup() {
            StringBuilder sb = new StringBuilder();
            sb.append("+").append(totalScore);
            sb.append("\n(").append(pips).append(" pips");
            if (crowdBonus > 0) sb.append(" + ").append(crowdBonus).append(" crowd");
            sb.append(")");
            if (multiplier > 1) sb.append("\n\u00D7").append(multiplier).append(" chain!");
            return sb.toString();
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Constructor
    // ═════════════════════════════════════════════════════════════════════
    public DominosaGame() {
        setTitle(GAME_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(BG_DEEP);
        setLayout(new BorderLayout(0, 0));

        board         = new DominoBoard();
        scoringHelper = new ScoringHelper(board);
        gamePanel     = new GamePanel(board, this);

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildLeftPanel(),
            buildRightPanel());
        split.setResizeWeight(0.60);
        split.setDividerSize(3);
        split.setBorder(null);
        split.setBackground(DIVIDER);
        add(split, BorderLayout.CENTER);

        syncControlStates();
        setVisible(true);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.60));
    }

    // ═════════════════════════════════════════════════════════════════════
    // LEFT PANEL  —  Title + Board + Scores + Controls
    // ═════════════════════════════════════════════════════════════════════
    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, BG_DEEP,
                            0, getHeight(), new Color(220, 226, 245)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        // ── Title bar ─────────────────────────────────────────────────
        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 22, 10));
        titleBar.setOpaque(false);

        JLabel titleLabel = new JLabel(GAME_TITLE);
        titleLabel.setFont(POPPINS_BOLD.deriveFont(Font.BOLD, 36f));
        titleLabel.setForeground(ACCENT2);

        JLabel sub = new JLabel("  \u00B7  Match tiles to zone rules");
        sub.setFont(POPPINS_REGULAR.deriveFont(16f));
        sub.setForeground(TEXT_MUTED);

        titleBar.add(titleLabel);
        titleBar.add(sub);

        // ── Board in a scroll pane so it's always fully visible ───────
        JPanel boardWrapper = new JPanel(new GridBagLayout());
        boardWrapper.setOpaque(false);
        boardWrapper.add(gamePanel);

        JScrollPane boardScroll = new JScrollPane(boardWrapper);
        boardScroll.setBorder(null);
        boardScroll.setOpaque(false);
        boardScroll.getViewport().setOpaque(false);
        boardScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        boardScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // ── Bottom: CPU label + score bar + controls ──────────────────
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(6, 16, 12, 16));

        cpuThinkingLabel = new JLabel(" ", SwingConstants.CENTER);
        cpuThinkingLabel.setFont(POPPINS_SEMIBOLD.deriveFont(16f));
        cpuThinkingLabel.setForeground(ACCENT);
        cpuThinkingLabel.setAlignmentX(CENTER_ALIGNMENT);

        bottom.add(cpuThinkingLabel);
        bottom.add(vgap(4));
        bottom.add(buildScoreBar());
        bottom.add(vgap(10));
        bottom.add(buildControlsPanel());

        panel.add(titleBar,    BorderLayout.NORTH);
        panel.add(boardScroll, BorderLayout.CENTER);
        panel.add(bottom,      BorderLayout.SOUTH);
        return panel;
    }

    // ── Score bar ─────────────────────────────────────────────────────────
    private JPanel buildScoreBar() {
        JPanel bar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 18, 18);
                g2.setColor(DIVIDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 18, 18);
            }
        };
        bar.setOpaque(false);
        bar.setLayout(new GridLayout(1, 3, 0, 0));
        bar.setBorder(new EmptyBorder(8, 20, 8, 20));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        bar.setAlignmentX(LEFT_ALIGNMENT);

        humanScoreLabel = new JLabel("0", SwingConstants.CENTER);
        humanScoreLabel.setFont(POPPINS_BOLD.deriveFont(Font.BOLD, 46f));
        humanScoreLabel.setForeground(SUCCESS);

        cpuScoreLabel = new JLabel("0", SwingConstants.CENTER);
        cpuScoreLabel.setFont(POPPINS_BOLD.deriveFont(Font.BOLD, 46f));
        cpuScoreLabel.setForeground(DANGER);

        bar.add(scoreSide("YOU", humanScoreLabel, SUCCESS));
        bar.add(vsLabel());
        bar.add(scoreSide("CPU", cpuScoreLabel, DANGER));
        return bar;
    }

    private JPanel scoreSide(String label, JLabel num, Color accent) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        JLabel lbl = new JLabel(label, SwingConstants.CENTER);
        lbl.setFont(POPPINS_SEMIBOLD.deriveFont(Font.BOLD, 14f));
        lbl.setForeground(accent);
        lbl.setAlignmentX(CENTER_ALIGNMENT);
        num.setAlignmentX(CENTER_ALIGNMENT);
        p.add(lbl);
        p.add(num);
        return p;
    }

    private JPanel vsLabel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        JLabel vs = new JLabel("VS", SwingConstants.CENTER);
        vs.setFont(POPPINS_BOLD.deriveFont(Font.BOLD, 20f));
        vs.setForeground(TEXT_MUTED);
        p.add(vs);
        return p;
    }

    // ── Controls panel ────────────────────────────────────────────────────
    private JPanel buildControlsPanel() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setOpaque(false);
        outer.setAlignmentX(LEFT_ALIGNMENT);

        // Row 1 — main actions
        JPanel row1 = hrow();
        newGameButton = bigBtn("🔄  New Game",    ACCENT);
        newGameButton.addActionListener(e -> startNewGame());
        hintButton    = bigBtn("💡  Hint  (3)",   new Color(120, 80, 200));
        hintButton.addActionListener(e -> showHint());
        undoButton    = bigBtn("↩  Undo",         new Color(80, 100, 170));
        undoButton.addActionListener(e -> undoMove());
        row1.add(newGameButton);
        row1.add(hintButton);
        row1.add(undoButton);

        // Row 2 — tool buttons
        JPanel row2 = hrow();
        JButton graphBtn = bigBtn("🔗  Graph View",   new Color(60, 120, 190));
        graphBtn.addActionListener(e -> gamePanel.toggleGraphView());
        dpOracleButton   = bigBtn("⭐  Chain Oracle",  new Color(170, 110, 0));
        dpOracleButton.addActionListener(e -> activateDPOracle());
        sniperButton     = buildSniperToggle();
        row2.add(graphBtn);
        row2.add(dpOracleButton);
        row2.add(sniperButton);

        // Row 3 — checkboxes
        JPanel row3 = hrow();
        visualizeCheckBox = styledCB("🧠  Animate CPU thinking");
        visualizeCheckBox.setSelected(true);
        heatmapCheckBox   = styledCB("🌡  DP Chain Heatmap");
        heatmapCheckBox.addActionListener(e -> gamePanel.setShowHeatmap(heatmapCheckBox.isSelected()));
        row3.add(visualizeCheckBox);
        row3.add(heatmapCheckBox);

        outer.add(row1);
        outer.add(vgap(6));
        outer.add(row2);
        outer.add(vgap(6));
        outer.add(row3);
        return outer;
    }

    private JToggleButton buildSniperToggle() {
        JToggleButton btn = new JToggleButton("🎯  Sniper Mode") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = !isEnabled() ? new Color(200, 205, 220)
                         : isSelected() ? new Color(160, 60, 10)
                         : new Color(130, 50, 30);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        btn.setFont(POPPINS_SEMIBOLD.deriveFont(Font.BOLD, 15f));
        btn.setEnabled(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setForeground(Color.WHITE);
        btn.setBorder(new EmptyBorder(7, 14, 7, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addChangeListener(e ->
            btn.setForeground(btn.isSelected() ? new Color(255, 220, 80) : Color.WHITE));
        return btn;
    }

    // ═════════════════════════════════════════════════════════════════════
    // RIGHT PANEL  —  Status + Scrollable Instructions
    // ═════════════════════════════════════════════════════════════════════
    private JPanel buildRightPanel() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, BG_PANEL,
                            0, getHeight(), new Color(210, 218, 240)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout(0, 0));
        panel.setBorder(new EmptyBorder(22, 22, 22, 22));

        // Status pinned at top
        panel.add(buildStatusCard(), BorderLayout.NORTH);

        // Instructions scroll area
        JPanel instrWrap = new JPanel();
        instrWrap.setLayout(new BoxLayout(instrWrap, BoxLayout.Y_AXIS));
        instrWrap.setOpaque(false);
        instrWrap.add(vgap(14));
        instrWrap.add(buildInstructionsCard());

        JScrollPane scroll = new JScrollPane(instrWrap);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ── Status card ───────────────────────────────────────────────────────
    private JPanel buildStatusCard() {
        JPanel card = card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel heading = sectionHead("What's Happening");
        heading.setAlignmentX(LEFT_ALIGNMENT);

        statusLabel = new JLabel("Your turn — click any two touching cells to place a tile.");
        statusLabel.setFont(POPPINS_REGULAR.deriveFont(16f));
        statusLabel.setForeground(TEXT_PRIMARY);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        card.add(heading);
        card.add(vgap(8));
        card.add(statusLabel);
        return card;
    }

    // ── Instructions card ─────────────────────────────────────────────────
    private JPanel buildInstructionsCard() {
        JPanel card = card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // THE GOAL
        card.add(sectionHead("🏆  The Goal"));
        card.add(vgap(6));
        card.add(para(
            "Score more points than the CPU by placing domino tiles on the board. " +
            "Each tile covers two cells. When no more tiles can be placed, the " +
            "player with the highest score wins."
        ));
        card.add(vgap(18));

        // HOW TO PLACE
        card.add(sectionHead("🖱️  How to Place a Tile"));
        card.add(vgap(6));
        card.add(para(
            "Click on one cell, then click a cell right next to it (up, down, left, " +
            "or right). If the move follows that area's rule, your tile is placed and " +
            "you score points. The CPU then plays automatically."
        ));
        card.add(vgap(18));

        // ZONE RULES
        card.add(sectionHead("🗺️  The Board Has 4 Areas — Each Has Its Own Rule"));
        card.add(vgap(10));
        card.add(zoneRule("🔵  Top-Left  —  Vertical Only",
            "You must place tiles going up and down. Placing side by side is not allowed in this area."));
        card.add(vgap(10));
        card.add(zoneRule("🔴  Top-Right  —  Even Sum Only",
            "The two numbers on your tile must add up to an even number (2+4=6 ✓  or  1+3=4 ✓,  but  1+2=3 ✗)."));
        card.add(vgap(10));
        card.add(zoneRule("🟠  Bottom-Left  —  Odd Sum Only",
            "The two numbers must add up to an odd number (1+2=3 ✓  or  3+4=7 ✓,  but  2+4=6 ✗)."));
        card.add(vgap(10));
        card.add(zoneRule("🟢  Bottom-Right  —  Horizontal Only",
            "You must place tiles going left and right. Placing up and down is not allowed in this area."));
        card.add(vgap(18));

        // SCORING
        card.add(sectionHead("🧮  How Scoring Works"));
        card.add(vgap(6));
        card.add(para("Your points come from three things:"));
        card.add(vgap(6));
        card.add(bulletPara("1.  Pip Points",
            "Add up the two numbers on your tile. A tile showing 3 and 5 earns 8 base points."));
        card.add(vgap(6));
        card.add(bulletPara("2.  Crowd Bonus",
            "You get +2 extra points for each tile that is already touching the cells you just covered."));
        card.add(vgap(6));
        card.add(bulletPara("3.  Chain Multiplier",
            "If the tiles touching yours show the same number, you start a chain. " +
            "The longer the chain, the bigger the multiplier (×2, ×3…). " +
            "This can be very powerful, so always look for chains!"));
        card.add(vgap(18));

        // BUTTONS EXPLAINED
        card.add(sectionHead("🎛️  What Each Button Does"));
        card.add(vgap(10));
        card.add(toolRow("🔄  New Game",
            "Starts a brand new game with a fresh random board."));
        card.add(vgap(8));
        card.add(toolRow("💡  Hint",
            "Shows you the best tile you can place right now (highlighted on the board). " +
            "You get 3 hints per game. After all 3 are used, Sniper Mode unlocks."));
        card.add(vgap(8));
        card.add(toolRow("↩  Undo",
            "Takes back your last tile placement (and the CPU's reply too, if it already went). " +
            "Useful if you placed a tile by mistake."));
        card.add(vgap(8));
        card.add(toolRow("🔗  Graph View",
            "Draws lines between all connected tiles so you can see links across the board. " +
            "Good for spotting chain opportunities."));
        card.add(vgap(8));
        card.add(toolRow("⭐  Chain Oracle",
            "Analyses all remaining moves and highlights the one with the best chain " +
            "multiplier potential. It then traces the chain on screen, step by step."));
        card.add(vgap(8));
        card.add(toolRow("🌡  DP Chain Heatmap",
            "Puts a small coloured badge on every placed tile to show its chain depth: " +
            "Blue = 1,  Amber = 2,  Red = 3 or more."));
        card.add(vgap(8));
        card.add(toolRow("🧠  Animate CPU Thinking",
            "When turned on, the CPU shows its thinking step by step before placing a tile. " +
            "Turn it off if you want the CPU to move instantly."));
        card.add(vgap(8));
        card.add(toolRow("🎯  Sniper Mode",
            "Unlocked after using all 3 hints. Toggle it ON, then place your tile. " +
            "If you chose the single best move on the board — your score DOUBLES. " +
            "If you chose wrong — you lose those points as a penalty. High risk, high reward!"));
        card.add(vgap(18));

        // ZONE COLOUR KEY
        card.add(sectionHead("🎨  Area Colour Key"));
        card.add(vgap(8));
        card.add(zoneLegendRow("#B8DCFF", "Blue   —  Top-Left      (Vertical only)"));
        card.add(vgap(6));
        card.add(zoneLegendRow("#FFDADA", "Red    —  Top-Right     (Even pip sum)"));
        card.add(vgap(6));
        card.add(zoneLegendRow("#FFF0C8", "Amber  —  Bottom-Left   (Odd pip sum)"));
        card.add(vgap(6));
        card.add(zoneLegendRow("#CCFFCC", "Green  —  Bottom-Right  (Horizontal only)"));

        return card;
    }

    // ═════════════════════════════════════════════════════════════════════
    // UI factory helpers
    // ═════════════════════════════════════════════════════════════════════

    private JPanel card() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 18, 18);
                g2.setColor(DIVIDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 18, 18);
            }
        };
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        return p;
    }

    private JLabel sectionHead(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(POPPINS_SEMIBOLD.deriveFont(Font.BOLD, 12f));
        l.setForeground(TEXT_MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel para(String text) {
        JLabel l = new JLabel("<html><body style='width:340px'>" + text + "</body></html>");
        l.setFont(POPPINS_REGULAR.deriveFont(15f));
        l.setForeground(TEXT_PRIMARY);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel bulletPara(String title, String desc) {
        JPanel p = new JPanel(new BorderLayout(10, 4));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(POPPINS_BOLD.deriveFont(Font.BOLD, 15f));
        t.setForeground(ACCENT);
        JLabel d = new JLabel("<html><body style='width:330px'>" + desc + "</body></html>");
        d.setFont(POPPINS_REGULAR.deriveFont(14f));
        d.setForeground(TEXT_PRIMARY);
        p.add(t, BorderLayout.NORTH);
        p.add(d, BorderLayout.CENTER);
        return p;
    }

    private JPanel zoneRule(String title, String desc) {
        JPanel p = new JPanel(new BorderLayout(10, 4));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(POPPINS_BOLD.deriveFont(Font.BOLD, 15f));
        t.setForeground(ACCENT);
        JLabel d = new JLabel("<html><body style='width:330px'>" + desc + "</body></html>");
        d.setFont(POPPINS_REGULAR.deriveFont(14f));
        d.setForeground(TEXT_PRIMARY);
        p.add(t, BorderLayout.NORTH);
        p.add(d, BorderLayout.CENTER);
        return p;
    }

    private JPanel toolRow(String name, String desc) {
        JPanel p = new JPanel(new BorderLayout(10, 3));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel k = new JLabel(name);
        k.setFont(POPPINS_SEMIBOLD.deriveFont(Font.BOLD, 15f));
        k.setForeground(ACCENT);
        k.setPreferredSize(new Dimension(200, 22));
        k.setVerticalAlignment(SwingConstants.TOP);
        JLabel v = new JLabel("<html><body style='width:280px'>" + desc + "</body></html>");
        v.setFont(POPPINS_REGULAR.deriveFont(14f));
        v.setForeground(TEXT_PRIMARY);
        p.add(k, BorderLayout.WEST);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    private JPanel zoneLegendRow(String hex, String label) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        Color c = Color.decode(hex);
        JPanel swatch = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 7, 7);
                g2.setColor(c.darker());
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 7, 7);
            }
        };
        swatch.setPreferredSize(new Dimension(26, 18));
        swatch.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(POPPINS_REGULAR.deriveFont(15f));
        lbl.setForeground(TEXT_PRIMARY);
        p.add(swatch);
        p.add(lbl);
        return p;
    }

    private JButton bigBtn(String text, Color accent) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = !isEnabled()           ? new Color(195, 200, 220)
                         : getModel().isPressed() ? accent.darker().darker()
                         : getModel().isRollover()? accent.darker()
                         : accent;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        b.setFont(POPPINS_SEMIBOLD.deriveFont(Font.BOLD, 15f));
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(7, 14, 7, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(false);
        return b;
    }

    private JCheckBox styledCB(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setFont(POPPINS_REGULAR.deriveFont(15f));
        cb.setForeground(TEXT_PRIMARY);
        cb.setOpaque(false);
        return cb;
    }

    private JPanel hrow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private Component vgap(int h) { return Box.createVerticalStrut(h); }

    // ═════════════════════════════════════════════════════════════════════
    // Control state sync
    // ═════════════════════════════════════════════════════════════════════
    private void syncControlStates() {
        boolean busy = cpuAnimationRunning || oracleAnimationRunning;
        newGameButton.setEnabled(!busy);
        visualizeCheckBox.setEnabled(!busy);

        if (busy || gameOver) {
            hintButton.setEnabled(false);
            undoButton.setEnabled(!busy && !gameOver);
            dpOracleButton.setEnabled(false);
            sniperButton.setEnabled(false);
            return;
        }
        hintButton.setEnabled(hintsUsed < MAX_HINTS && isHumanTurn);
        undoButton.setEnabled(true);
        sniperButton.setEnabled(hintsUsed >= MAX_HINTS && !sniperUsed && isHumanTurn);
        dpOracleButton.setEnabled(isHumanTurn);
    }

    // ═════════════════════════════════════════════════════════════════════
    // Accessors used by GamePanel
    // ═════════════════════════════════════════════════════════════════════
    public boolean isHumanTurn() { return isHumanTurn; }
    public boolean isGameOver()  { return gameOver; }
    public boolean isBusy()      { return cpuAnimationRunning || oracleAnimationRunning; }

    // ═════════════════════════════════════════════════════════════════════
    // Game actions
    // ═════════════════════════════════════════════════════════════════════
    private void startNewGame() {
        board         = new DominoBoard();
        scoringHelper = new ScoringHelper(board);
        gamePanel.setBoard(board);
        humanScore = 0; cpuScore = 0;
        isHumanTurn = true; gameOver = false;
        cpuAnimationRunning = false; oracleAnimationRunning = false;
        hintsUsed = 0;
        hintButton.setText("💡  Hint  (3)");
        sniperUsed = false;
        sniperButton.setSelected(false);
        updateScore();
        updateStatus("Your turn — click any two touching cells to place a tile.");
        cpuThinkingLabel.setText(" ");
        syncControlStates();
    }

    public void onHumanMove(Domino domino) {
        if (!isHumanTurn) return;
        String reason = board.getInvalidPlacementReason(domino);
        if (reason != null) { updateStatus(reason); return; }

        if (board.isValidPlacement(domino)) {
            List<Domino> allValidMoves = board.getAllValidMoves();
            int rank = findMoveRank(domino, allValidMoves);

            board.placeDomino(domino, false);
            int baseScore  = calculateScore(domino);
            int finalScore = baseScore;

            if (sniperButton.isSelected() && !sniperUsed && hintsUsed >= MAX_HINTS) {
                sniperUsed = true;
                sniperButton.setSelected(false);
                if (rank == 1) {
                    finalScore = baseScore * 2;
                    humanScore += finalScore;
                    domino.setAwardedScore(finalScore);
                    JOptionPane.showMessageDialog(this,
                        "\uD83C\uDFAF JACKPOT! You picked the best move!\nScore: +" + finalScore + "  (doubled!)",
                        GAME_TITLE + " \u2014 Sniper", JOptionPane.INFORMATION_MESSAGE);
                    updateStatus("\uD83C\uDFAF SNIPER SUCCESS! +" + finalScore);
                } else {
                    finalScore = -baseScore;
                    humanScore += finalScore;
                    domino.setAwardedScore(finalScore);
                    Domino best = findBestMoveInList(allValidMoves);
                    JOptionPane.showMessageDialog(this,
                        "\u274C Missed! You were rank #" + rank + " \u2014 not the best.\n" +
                        "Best was: " + (best != null ? best : "?") +
                        "\nPenalty: \u2212" + baseScore + " pts",
                        GAME_TITLE + " \u2014 Sniper", JOptionPane.WARNING_MESSAGE);
                    updateStatus("\u274C SNIPER MISSED! Rank #" + rank + ". Penalty: \u2212" + baseScore);
                }
            } else if (sniperButton.isSelected() && sniperUsed) {
                sniperButton.setSelected(false);
                humanScore += baseScore;
                domino.setAwardedScore(baseScore);
                updateStatus("Sniper already used this game.");
            } else if (sniperButton.isSelected() && hintsUsed < MAX_HINTS) {
                sniperButton.setSelected(false);
                humanScore += baseScore;
                domino.setAwardedScore(baseScore);
                updateStatus("Use all " + MAX_HINTS + " hints first. (" + (MAX_HINTS - hintsUsed) + " left)");
            } else {
                humanScore += baseScore;
                domino.setAwardedScore(baseScore);
                ScoreBreakdown bd = getScoreBreakdown(domino);
                updateStatus(bd.getFormattedStatus());
                gamePanel.showFloatingScore(domino, bd.getFormattedPopup(), SUCCESS);
            }

            updateScore();
            gamePanel.repaint();
            checkGameEnd(false);
        } else {
            updateStatus("Invalid move! Check the zone rule for that area.");
        }
    }

    private void scheduleCPUMove() {
        if (gameOver || isHumanTurn) return;
        cpuThinkingLabel.setText("\uD83E\uDD16  CPU is thinking\u2026");
        syncControlStates();
        Timer t = new Timer(CPU_DELAY_MS, e -> SwingUtilities.invokeLater(this::executeCPUMove));
        t.setRepeats(false);
        t.start();
    }

    private void executeCPUMove() {
        if (gameOver || isHumanTurn) return;
        if (cpuAnimationRunning || oracleAnimationRunning) return;

        if (visualizeCheckBox.isSelected()) {
            cpuAnimationRunning = true;
            syncControlStates();
            new Thread(() -> {
                try { showMinimaxVisualization(); }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    SwingUtilities.invokeLater(() -> {
                        cpuAnimationRunning = false;
                        cpuThinkingLabel.setText(" ");
                        syncControlStates();
                    });
                }
            }).start();
        } else {
            MinimaxStrategy strategy = new MinimaxStrategy(board, this);
            Domino move = strategy.findMove();
            if (move != null) {
                board.placeDomino(move, true);
                int score = calculateScore(move);
                cpuScore += score;
                move.setAwardedScore(score);
                ScoreBreakdown bd = getScoreBreakdown(move);
                updateStatus("CPU played: " + bd.getFormattedStatus()
                    + "  [nodes=" + strategy.getNodesExplored()
                    + ", pruned=" + strategy.getBranchesPruned() + "]");
                gamePanel.showFloatingScore(move, bd.getFormattedPopup(), DANGER);
                updateScore();
                gamePanel.repaint();
            }
            cpuThinkingLabel.setText(" ");
            checkGameEnd(true);
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Hint / Undo / Oracle
    // ═════════════════════════════════════════════════════════════════════
    private void showHint() {
        if (gameOver || cpuAnimationRunning || oracleAnimationRunning) return;
        if (hintsUsed >= MAX_HINTS) {
            JOptionPane.showMessageDialog(this, "No hints left!", GAME_TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        GreedyStrategy s = new GreedyStrategy(board);
        Domino h = s.findBestMove();
        if (h != null) {
            gamePanel.highlightHint(h);
            hintsUsed++;
            hintButton.setText("💡  Hint  (" + (MAX_HINTS - hintsUsed) + ")");
            if (hintsUsed >= MAX_HINTS)
                JOptionPane.showMessageDialog(this,
                    "You've used all 3 hints!\n\nSniper Mode is now unlocked for one move.",
                    GAME_TITLE, JOptionPane.INFORMATION_MESSAGE);
        }
        syncControlStates();
    }

    private void undoMove() {
        if (gameOver || cpuAnimationRunning || oracleAnimationRunning) return;
        List<Domino> placed = board.getPlacedDominoes();
        if (placed.isEmpty()) { updateStatus("Nothing to undo yet."); return; }

        Domino last = placed.get(placed.size() - 1);
        if (isHumanTurn && last.isPlacedByCPU()) {
            board.removeLastDomino();
            if (!board.getPlacedDominoes().isEmpty()) board.removeLastDomino();
            updateStatus("Undid the CPU's move and your last move.");
        } else {
            board.removeLastDomino();
            isHumanTurn = true;
            updateStatus("Last move undone \u2014 your turn again.");
        }
        recalculateAllScores();
        gamePanel.repaint();
        syncControlStates();
    }

    private void activateDPOracle() {
        if (gameOver || cpuAnimationRunning || oracleAnimationRunning) return;
        List<Domino> validMoves = board.getAllValidMoves();
        if (validMoves.isEmpty()) { updateStatus("No valid moves left for the Oracle."); return; }

        oracleAnimationRunning = true;
        syncControlStates();

        Domino bestOracleMove = null;
        int bestProjected = Integer.MIN_VALUE, bestMult = 0;

        for (Domino move : validMoves) {
            Cell c1 = move.getCell1(), c2 = move.getCell2();
            int v1 = board.getValue(c1.row, c1.col), v2 = board.getValue(c2.row, c2.col);
            int pipSum = v1 + v2;
            int mult = Math.max(board.calculateDPChain(c1, v1), board.calculateDPChain(c2, v2));
            int proj  = (mult >= 2) ? pipSum * mult : pipSum;
            if (proj > bestProjected || (proj == bestProjected && mult > bestMult)) {
                bestProjected = proj; bestMult = mult; bestOracleMove = move;
            }
        }

        if (bestOracleMove == null) { oracleAnimationRunning = false; syncControlStates(); return; }

        Cell bc1 = bestOracleMove.getCell1(), bc2 = bestOracleMove.getCell2();
        int bv1 = board.getValue(bc1.row, bc1.col), bv2 = board.getValue(bc2.row, bc2.col);
        int ch1 = board.calculateDPChain(bc1, bv1), ch2 = board.calculateDPChain(bc2, bv2);
        Cell chainCell = (ch1 >= ch2) ? bc1 : bc2;
        int chainVal   = (ch1 >= ch2) ? bv1 : bv2;
        List<Cell> chainPath = board.getChainPath(chainCell, chainVal);

        final Domino om = bestOracleMove;
        final int ps = bestProjected, ml = bestMult;
        new Thread(() -> {
            try { showDPOracleAnimation(om, chainPath, ps, ml); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                SwingUtilities.invokeLater(() -> { oracleAnimationRunning = false; syncControlStates(); });
            }
        }).start();
    }

    private void showDPOracleAnimation(Domino om, List<Cell> chainPath, int proj, int mult)
            throws InterruptedException {
        SwingUtilities.invokeLater(() -> {
            gamePanel.setShowHeatmap(true);
            if (heatmapCheckBox != null) heatmapCheckBox.setSelected(true);
            gamePanel.clearChainTrace();
            updateStatus("\u2B50 Chain Oracle: showing the DP heatmap\u2026");
        });
        Thread.sleep(900);

        for (int i = 0; i < chainPath.size(); i++) {
            final int step = i + 1;
            final Cell cell = chainPath.get(i);
            SwingUtilities.invokeLater(() -> {
                gamePanel.addChainTraceCell(cell);
                updateStatus("\u2B50 Tracing chain step " + step + " of " + chainPath.size()
                    + "  (pip=" + board.getValue(cell.row, cell.col)
                    + ", depth=" + board.getDPValue(cell.row, cell.col) + ")");
            });
            Thread.sleep(450);
        }
        Thread.sleep(350);

        int v1 = board.getValue(om.getCell1().row, om.getCell1().col);
        int v2 = board.getValue(om.getCell2().row, om.getCell2().col);
        SwingUtilities.invokeLater(() -> {
            gamePanel.highlightDPOracle(om);
            updateStatus("\u2B50 Best chain move: [" + v1 + "+" + v2 + "] \u00D7 " + mult + " = projected +" + proj + " pts");
            oracleAnimationRunning = false;
            syncControlStates();
        });
    }

    private void showMinimaxVisualization() throws InterruptedException {
        List<Domino> validMoves = board.getAllValidMoves();
        SwingUtilities.invokeLater(() -> {
            updateStatus("\uD83E\uDDE0 CPU Phase 1: Looking at " + validMoves.size() + " possible moves\u2026");
            gamePanel.clearAllComparisonHighlights();
            for (Domino m : validMoves) {
                gamePanel.addLinearHighlight(m.getCell1().row, m.getCell1().col);
                gamePanel.addLinearHighlight(m.getCell2().row, m.getCell2().col);
            }
        });
        Thread.sleep(1000);

        SwingUtilities.invokeLater(() -> {
            gamePanel.clearLinearHighlights();
            updateStatus("\uD83E\uDDE0 CPU Phase 2: Thinking " + MinimaxStrategy.getMaxDepth() + " moves ahead with Minimax\u2026");
        });
        Thread.sleep(500);

        MinimaxStrategy strategy = new MinimaxStrategy(board, this);
        Domino move  = strategy.findMove();
        int explored = strategy.getNodesExplored();
        int pruned   = strategy.getBranchesPruned();

        SwingUtilities.invokeLater(() -> {
            updateStatus("\uD83E\uDDE0 CPU Phase 3: Checked " + explored + " positions, cut " + pruned + " branches early.");
            gamePanel.clearAllComparisonHighlights();
            if (move != null) {
                gamePanel.addBestMoveHighlight(move.getCell1().row, move.getCell1().col);
                gamePanel.addBestMoveHighlight(move.getCell2().row, move.getCell2().col);
            }
        });
        Thread.sleep(1800);

        SwingUtilities.invokeLater(() -> {
            cpuAnimationRunning = false;
            cpuThinkingLabel.setText(" ");
            gamePanel.clearAllComparisonHighlights();
            if (move != null) {
                board.placeDomino(move, true);
                int score = calculateScore(move);
                cpuScore += score;
                move.setAwardedScore(score);
                ScoreBreakdown bd = getScoreBreakdown(move);
                updateStatus("CPU played: " + bd.getFormattedStatus()
                    + "  [" + explored + " nodes, " + pruned + " pruned]");
                gamePanel.showFloatingScore(move, bd.getFormattedPopup(), DANGER);
                updateScore();
                gamePanel.repaint();
            }
            checkGameEnd(true);
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    // Scoring
    // ═════════════════════════════════════════════════════════════════════
    public int calculateScore(Domino domino) {
        Cell c1 = domino.getCell1(), c2 = domino.getCell2();
        int base = domino.getValue();
        int mult = Math.max(board.getDPValue(c1.row, c1.col), board.getDPValue(c2.row, c2.col));
        if (mult >= 2) base *= mult;
        int score = base;
        BoardGraph bg = board.getGraph();
        for (Cell cell : new Cell[]{c1, c2}) {
            int nb = bg.countOccupiedNeighbors(cell);
            Cell other = cell.equals(c1) ? c2 : c1;
            if (bg.hasEdge(cell, other)) nb = Math.max(0, nb - 1);
            score += nb * CROWD_BONUS;
        }
        return score;
    }

    public ScoreBreakdown getScoreBreakdown(Domino domino) {
        Cell c1 = domino.getCell1(), c2 = domino.getCell2();
        int pips = domino.getValue();
        int mult = Math.max(board.getDPValue(c1.row, c1.col), board.getDPValue(c2.row, c2.col));
        int base = (mult >= 2) ? pips * mult : pips;
        int crowd = 0;
        BoardGraph bg = board.getGraph();
        for (Cell cell : new Cell[]{c1, c2}) {
            int nb = bg.countOccupiedNeighbors(cell);
            Cell other = cell.equals(c1) ? c2 : c1;
            if (bg.hasEdge(cell, other)) nb = Math.max(0, nb - 1);
            crowd += nb * CROWD_BONUS;
        }
        if (mult <= 0) mult = 1;
        return new ScoreBreakdown(pips, crowd, mult, base + crowd);
    }

    private int findMoveRank(Domino target, List<Domino> all) {
        double ts = calcSniperScore(target), best = Double.NEGATIVE_INFINITY;
        for (Domino m : all) { double s = calcSniperScore(m); if (s > best) best = s; }
        if (ts >= best) return 1;
        int better = 0;
        for (Domino m : all) if (calcSniperScore(m) > ts) better++;
        return better + 1;
    }

    private double calcSniperScore(Domino d) {
        int score = d.getValue();
        BoardGraph bg = board.getGraph();
        for (Cell cell : new Cell[]{d.getCell1(), d.getCell2()}) {
            int nb = bg.countOccupiedNeighbors(cell);
            Cell other = cell.equals(d.getCell1()) ? d.getCell2() : d.getCell1();
            if (bg.hasEdge(cell, other)) nb = Math.max(0, nb - 1);
            score += nb * CROWD_BONUS;
        }
        return score;
    }

    private Domino findBestMoveInList(List<Domino> moves) {
        if (moves.isEmpty()) return null;
        Domino best = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (Domino m : moves) {
            double s = scoringHelper.evaluateMoveScore(m);
            if (s > bestScore) { bestScore = s; best = m; }
        }
        return best;
    }

    private void recalculateAllScores() {
        humanScore = 0; cpuScore = 0;
        for (Domino d : board.getPlacedDominoes()) {
            if (d.isPlacedByCPU()) cpuScore  += d.getAwardedScore();
            else                    humanScore += d.getAwardedScore();
        }
        updateScore();
    }

    private void checkGameEnd(boolean wasCpuTurn) {
        if (board.getAllValidMoves().isEmpty()) {
            gameOver = true;
            String winner = humanScore > cpuScore ? "You win! \uD83C\uDF89"
                : cpuScore > humanScore           ? "CPU wins! \uD83E\uDD16"
                : "It's a draw!";
            updateStatus("Game over \u2014 " + winner);
            cpuThinkingLabel.setText(" ");
            syncControlStates();
            JOptionPane.showMessageDialog(this,
                winner + "\n\nFinal Score\n  You: " + humanScore + "   \u00B7   CPU: " + cpuScore,
                GAME_TITLE, JOptionPane.INFORMATION_MESSAGE);
        } else {
            isHumanTurn = wasCpuTurn;
            if (isHumanTurn) {
                updateStatus("Your turn \u2014 click any two touching cells to place a tile.");
                cpuThinkingLabel.setText(" ");
                syncControlStates();
            } else {
                updateStatus("CPU's turn\u2026");
                syncControlStates();
                scheduleCPUMove();
            }
        }
    }

    private void updateScore() {
        humanScoreLabel.setText(String.valueOf(humanScore));
        cpuScoreLabel.setText(String.valueOf(cpuScore));
    }

    public void updateStatus(String msg) { statusLabel.setText(msg); }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(DominosaGame::new);
    }
}

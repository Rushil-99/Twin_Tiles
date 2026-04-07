import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * GamePanel - Renders the 8×8 Dominosa board with professional visuals.
 * Uses DominosaGame's palette for cohesive styling.
 * Enhanced floating score popup: multi-line, pill-shaped, Poppins-styled.
 */
public class GamePanel extends JPanel {
    private DominoBoard board;
    private DominosaGame game;

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int CELL_SIZE = 72;
    private static final int MARGIN    = 36;

    // ── Zone fill colors (light mode) ─────────────────────────────────────────
    private static final Color COL_VERT = new Color(180, 215, 255, 140);   // sky blue
    private static final Color COL_EVEN = new Color(255, 180, 180, 140);   // rose
    private static final Color COL_ODD  = new Color(255, 225, 140, 140);   // amber
    private static final Color COL_HORZ = new Color(150, 240, 180, 140);   // mint

    private static final Color COL_VERT_BORDER = new Color(50,  120, 210, 180);
    private static final Color COL_EVEN_BORDER = new Color(200, 50,  50,  180);
    private static final Color COL_ODD_BORDER  = new Color(190, 120, 0,   180);
    private static final Color COL_HORZ_BORDER = new Color(30,  150, 80,  180);

    // ── Overlay colors (light mode) ───────────────────────────────────────────
    private static final Color SELECT_FILL      = new Color(255, 200, 0,   200);
    private static final Color SELECT_BORDER    = new Color(200, 120, 0,   240);
    private static final Color HINT_FILL        = new Color(30,  180, 100, 180);
    private static final Color HINT_BORDER      = new Color(10,  130, 60,  240);
    private static final Color PLACED_H_FILL    = new Color(30,  180, 100, 150);
    private static final Color PLACED_H_BORDER  = new Color(10,  130, 60,  230);
    private static final Color PLACED_CPU_FILL  = new Color(50,  120, 210, 150);
    private static final Color PLACED_CPU_BDR   = new Color(20,  70,  180, 230);
    private static final Color DP_ORACLE_FILL   = new Color(252, 200, 60,  190);
    private static final Color DP_ORACLE_BORDER = new Color(180, 120, 0,   240);
    private static final Color CHAIN_FILL       = new Color(160, 60,  240, 110);
    private static final Color CHAIN_BORDER     = new Color(110, 20,  200, 220);

    // ── State ────────────────────────────────────────────────────────────────
    private Cell selectedCell1 = null;
    private Cell selectedCell2 = null;
    private Cell hintCell      = null;
    private boolean showGraph   = false;
    private boolean showHeatmap = false;
    private Domino dpOracleDomino = null;
    private List<Cell> chainTraceCells = new ArrayList<>();
    private Set<Cell> linearHighlights   = new HashSet<>();
    private Set<Integer> zoneHighlights  = new HashSet<>();
    private Set<Cell> bestMoveHighlights = new HashSet<>();
    private Domino ghostDomino = null;
    private List<Domino> prunedMoves = new ArrayList<>();
    private Map<Domino, String> evaluatedScores = new HashMap<>();

    // ── Floating score popups ─────────────────────────────────────────────────
    private List<FloatingText> floatingTexts = new ArrayList<>();
    private javax.swing.Timer floatingTextTimer;

    // ════════════════════════════════════════════════════════════════════════
    // FloatingText inner class
    // ════════════════════════════════════════════════════════════════════════
    private static class FloatingText {
        String[] lines;
        int x;
        int y;
        float alpha;
        Color color;
        long startTime;
        static final long DURATION = 2200;

        FloatingText(String text, int x, int y, Color color) {
            this.lines = text.split("\n");
            this.x = x;
            this.y = y;
            this.alpha = 1.0f;
            this.color = color;
            this.startTime = System.currentTimeMillis();
        }

        boolean update() {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= DURATION) return false;
            float progress = (float) elapsed / DURATION;
            // Ease-out: fast rise, then slow
            this.alpha = 1.0f - progress * progress;
            this.y -= (progress < 0.5f) ? 2 : 1;
            return true;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════════════
    public GamePanel(DominoBoard board, DominosaGame game) {
        this.board = board;
        this.game  = game;
        setBackground(DominosaGame.BG_DEEP);
        setOpaque(true);
        updatePreferredSize();

        floatingTextTimer = new javax.swing.Timer(30, e -> {
            List<FloatingText> dead = new ArrayList<>();
            for (FloatingText ft : floatingTexts) if (!ft.update()) dead.add(ft);
            floatingTexts.removeAll(dead);
            if (!floatingTexts.isEmpty() || !dead.isEmpty()) repaint();
        });
        floatingTextTimer.start();

        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleCellClick(e.getX(), e.getY()); }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Board / state updates
    // ════════════════════════════════════════════════════════════════════════
    public void setBoard(DominoBoard board) {
        this.board = board;
        selectedCell1 = null; selectedCell2 = null; hintCell = null;
        dpOracleDomino = null; showHeatmap = false;
        chainTraceCells.clear(); floatingTexts.clear();
        clearMinimaxVisuals(); clearAllComparisonHighlights();
        updatePreferredSize(); repaint();
    }
    public void toggleGraphView()           { showGraph = !showGraph; repaint(); }
    public void highlightDPOracle(Domino d) { dpOracleDomino = d; repaint(); }
    public void setShowHeatmap(boolean v)   { showHeatmap = v; repaint(); }
    public void toggleHeatmap()             { showHeatmap = !showHeatmap; repaint(); }
    public void addChainTraceCell(Cell c)   { chainTraceCells.add(c); repaint(); }
    public void clearChainTrace()           { chainTraceCells.clear(); repaint(); }
    public void addLinearHighlight(int r, int c) { linearHighlights.add(new Cell(r,c)); repaint(); }
    public void clearLinearHighlights()          { linearHighlights.clear(); repaint(); }
    public void addZoneHighlight(int z)          { zoneHighlights.add(z); repaint(); }
    public void clearZoneHighlights()            { zoneHighlights.clear(); repaint(); }
    public void addBestMoveHighlight(int r, int c){ bestMoveHighlights.add(new Cell(r,c)); repaint(); }
    public void clearBestMoveHighlights()         { bestMoveHighlights.clear(); repaint(); }
    public void clearAllComparisonHighlights()    { linearHighlights.clear(); zoneHighlights.clear(); bestMoveHighlights.clear(); repaint(); }
    public void setGhostDomino(Domino d)   { ghostDomino = d; repaint(); }
    public void clearGhostDomino()         { ghostDomino = null; repaint(); }
    public void addPrunedMove(Domino d)    { prunedMoves.add(d); repaint(); }
    public void addEvaluatedScore(Domino d, String s) { evaluatedScores.put(d, s); repaint(); }
    public void clearMinimaxVisuals()      { ghostDomino=null; prunedMoves.clear(); evaluatedScores.clear(); repaint(); }

    public void highlightHint(Domino hint) {
        int v1 = board.getValue(hint.getCell1().row, hint.getCell1().col);
        int v2 = board.getValue(hint.getCell2().row, hint.getCell2().col);
        hintCell = (v1 <= v2) ? hint.getCell1() : hint.getCell2();
        repaint();
    }

    public void showFloatingScore(Domino domino, String scoreText, Color color) {
        Cell c1 = domino.getCell1(), c2 = domino.getCell2();
        int cx = MARGIN + (Math.min(c1.col,c2.col) * CELL_SIZE) + (Math.abs(c1.col-c2.col)+1)*CELL_SIZE/2;
        int cy = MARGIN + (Math.min(c1.row,c2.row) * CELL_SIZE) + (Math.abs(c1.row-c2.row)+1)*CELL_SIZE/2;
        floatingTexts.add(new FloatingText(scoreText, cx, cy, color));
        repaint();
    }

    private void updatePreferredSize() {
        Dimension d = new Dimension(
            board.getCols() * CELL_SIZE + 2 * MARGIN,
            board.getRows() * CELL_SIZE + 2 * MARGIN);
        setPreferredSize(d);
        setMinimumSize(d);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Mouse input
    // ════════════════════════════════════════════════════════════════════════
    private void handleCellClick(int x, int y) {
        if (game.isGameOver()) { game.updateStatus("Game over \u2014 press New Game."); return; }
        if (!game.isHumanTurn()) { game.updateStatus("CPU's turn, please wait\u2026"); return; }
        if (game.isBusy())       { game.updateStatus("Please wait\u2026"); return; }

        int col = (x - MARGIN) / CELL_SIZE;
        int row = (y - MARGIN) / CELL_SIZE;

        if (row < 0 || row >= board.getRows() || col < 0 || col >= board.getCols()) return;
        if (board.getOccupied()[row][col]) { game.updateStatus("Cell already occupied!"); return; }

        dpOracleDomino = null;
        chainTraceCells.clear();

        Cell clicked = new Cell(row, col);
        if (selectedCell1 == null) {
            selectedCell1 = clicked;
            game.updateStatus("Selected [" + board.getValue(row,col) + "]. Pick a neighbor.");
        } else if (selectedCell2 == null) {
            if (clicked.equals(selectedCell1)) {
                selectedCell1 = null;
                game.updateStatus("Selection cleared.");
            } else {
                selectedCell2 = clicked;
                Domino d = new Domino(selectedCell1, selectedCell2,
                    board.getValue(selectedCell1.row, selectedCell1.col),
                    board.getValue(selectedCell2.row, selectedCell2.col));
                game.onHumanMove(d);
                selectedCell1 = null; selectedCell2 = null; hintCell = null;
            }
        }
        repaint();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Painting
    // ════════════════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,   RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY);

        // 1. Background
        g2.setColor(DominosaGame.BG_DEEP);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // 2. Zone separator lines (subtle)
        drawZoneDividers(g2);

        // 3. Board cells
        drawBoardCells(g2);

        // 4. Graph layer
        if (showGraph) drawGraphLayer(g2);

        // 5. Placed dominoes
        for (Domino d : board.getPlacedDominoes()) {
            if (d.isPlacedByCPU()) drawDominoOverlay(g2, d, PLACED_CPU_FILL, PLACED_CPU_BDR, true);
            else                    drawDominoOverlay(g2, d, PLACED_H_FILL,   PLACED_H_BORDER, true);
        }

        // 6. DP Heatmap
        if (showHeatmap) drawHeatmapLayer(g2);

        // 7. Chain trace
        if (!chainTraceCells.isEmpty()) drawChainTrace(g2);

        // 8. DP Oracle
        if (dpOracleDomino != null) {
            drawDominoOverlay(g2, dpOracleDomino, DP_ORACLE_FILL, DP_ORACLE_BORDER, false);
            drawOracleLabel(g2, dpOracleDomino);
        }

        // 9. Pruned moves
        for (Domino d : prunedMoves) {
            drawDominoOverlay(g2, d, new Color(255,0,0,60), new Color(200,0,0,150), false);
            g2.setColor(new Color(255,0,0,200));
            g2.setStroke(new BasicStroke(5));
            Cell c1=d.getCell1(), c2=d.getCell2();
            int px=MARGIN+Math.min(c1.col,c2.col)*CELL_SIZE, py=MARGIN+Math.min(c1.row,c2.row)*CELL_SIZE;
            int pw=(Math.abs(c1.col-c2.col)+1)*CELL_SIZE, ph=(Math.abs(c1.row-c2.row)+1)*CELL_SIZE;
            g2.drawLine(px+10,py+10,px+pw-10,py+ph-10);
            g2.drawLine(px+pw-10,py+10,px+10,py+ph-10);
        }

        // 10. Ghost domino
        if (ghostDomino != null)
            drawDominoOverlay(g2, ghostDomino, new Color(200,200,255,120), Color.BLUE, true);

        // 11. Evaluated scores
        for (Map.Entry<Domino,String> entry : evaluatedScores.entrySet()) {
            Domino d = entry.getKey();
            Cell c1 = d.getCell1();
            int cx = MARGIN + c1.col*CELL_SIZE + CELL_SIZE/2;
            int cy = MARGIN + c1.row*CELL_SIZE + CELL_SIZE/2;
            g2.setFont(DominosaGame.POPPINS_BOLD.deriveFont(Font.BOLD, 14f));
            g2.setColor(new Color(255, 255, 255, 180));
            g2.drawString(entry.getValue(), cx-14, cy+26);
            g2.setColor(new Color(30, 60, 180));
            g2.drawString(entry.getValue(), cx-15, cy+25);
        }

        // 12. Selection
        if (selectedCell1 != null && selectedCell2 != null) {
            Domino sel = new Domino(selectedCell1, selectedCell2,
                board.getValue(selectedCell1.row, selectedCell1.col),
                board.getValue(selectedCell2.row, selectedCell2.col));
            drawDominoOverlay(g2, sel, SELECT_FILL, SELECT_BORDER, true);
        } else if (selectedCell1 != null) {
            highlightCell(g2, selectedCell1, SELECT_FILL, SELECT_BORDER);
        }

        // 13. Hint
        if (hintCell != null) highlightCell(g2, hintCell, HINT_FILL, HINT_BORDER);

        // 14. Comparison highlights
        drawComparisonHighlights(g2);

        // 15. Zone labels (elegant, top of each quadrant)
        drawZoneLabels(g2);

        // 16. Floating score popups (top layer)
        drawFloatingTexts(g2);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Drawing helpers
    // ════════════════════════════════════════════════════════════════════════

    private void drawZoneDividers(Graphics2D g2) {
        int midX = MARGIN + 4 * CELL_SIZE;
        int midY = MARGIN + 4 * CELL_SIZE;
        int boardW = 8 * CELL_SIZE;
        int boardH = 8 * CELL_SIZE;
        g2.setColor(new Color(80, 100, 180, 160));
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(midX, MARGIN, midX, MARGIN + boardH);
        g2.drawLine(MARGIN, midY, MARGIN + boardW, midY);
    }

    private void drawBoardCells(Graphics2D g2) {
        int[][] grid = board.getGrid();
        boolean[][] occ = board.getOccupied();

        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                int x = MARGIN + c * CELL_SIZE;
                int y = MARGIN + r * CELL_SIZE;
                int zone = board.getZoneID(r, c);

                // Zone fill
                Color zoneFill, zoneBorder;
                switch (zone) {
                    case DominoBoard.ZONE_VERTICAL:   zoneFill=COL_VERT; zoneBorder=COL_VERT_BORDER; break;
                    case DominoBoard.ZONE_EVEN:        zoneFill=COL_EVEN; zoneBorder=COL_EVEN_BORDER; break;
                    case DominoBoard.ZONE_ODD:         zoneFill=COL_ODD;  zoneBorder=COL_ODD_BORDER;  break;
                    default:                            zoneFill=COL_HORZ; zoneBorder=COL_HORZ_BORDER; break;
                }

                // Occupied cells slightly darker tint
                if (occ[r][c]) {
                    zoneFill = new Color(
                        Math.max(0, zoneFill.getRed()   - 20),
                        Math.max(0, zoneFill.getGreen() - 20),
                        Math.max(0, zoneFill.getBlue()  - 20),
                        zoneFill.getAlpha());
                }

                g2.setColor(zoneFill);
                g2.fillRect(x, y, CELL_SIZE, CELL_SIZE);

                // Cell border
                g2.setColor(zoneBorder);
                g2.setStroke(new BasicStroke(1));
                g2.drawRect(x, y, CELL_SIZE, CELL_SIZE);

                // Pip number
                String num = String.valueOf(grid[r][c]);
                Font pipFont = DominosaGame.POPPINS_BOLD.deriveFont(Font.BOLD, 26f);
                g2.setFont(pipFont);
                FontMetrics fm = g2.getFontMetrics();
                int nx = x + (CELL_SIZE - fm.stringWidth(num)) / 2;
                int ny = y + (CELL_SIZE + fm.getAscent()) / 2 - 6;

                // Drop shadow for pip (lighter on light bg)
                g2.setColor(new Color(0, 0, 0, 30));
                g2.drawString(num, nx+1, ny+1);

                // Pip color: occupied = dimmer dark, free = dark primary
                g2.setColor(occ[r][c] ? new Color(60, 70, 120, 160) : new Color(25, 30, 60));
                g2.drawString(num, nx, ny);
            }
        }
    }

    private void drawGraphLayer(Graphics2D g2) {
        g2.setStroke(new BasicStroke(1.5f));
        Map<Cell, List<Cell>> adj = board.getGraph().getAdjacencyList();
        g2.setColor(new Color(37, 99, 235, 120));
        for (Map.Entry<Cell,List<Cell>> entry : adj.entrySet()) {
            Cell c1 = entry.getKey();
            int x1 = MARGIN + c1.col*CELL_SIZE + CELL_SIZE/2;
            int y1 = MARGIN + c1.row*CELL_SIZE + CELL_SIZE/2;
            for (Cell c2 : entry.getValue()) {
                int x2 = MARGIN + c2.col*CELL_SIZE + CELL_SIZE/2;
                int y2 = MARGIN + c2.row*CELL_SIZE + CELL_SIZE/2;
                g2.drawLine(x1, y1, x2, y2);
            }
        }
        g2.setColor(new Color(180, 120, 0, 220));
        for (Cell c : adj.keySet()) {
            int x = MARGIN + c.col*CELL_SIZE + CELL_SIZE/2;
            int y = MARGIN + c.row*CELL_SIZE + CELL_SIZE/2;
            g2.fillOval(x-5, y-5, 10, 10);
        }
    }

    private void drawHeatmapLayer(Graphics2D g2) {
        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                if (!board.getOccupied()[r][c]) continue;
                int dpVal = board.getDPValue(r, c);
                if (dpVal <= 0) continue;

                int x = MARGIN + c * CELL_SIZE;
                int y = MARGIN + r * CELL_SIZE;
                Color badge = dpVal == 1 ? new Color(60,130,200)
                    : dpVal == 2          ? new Color(240,150,0)
                    :                       new Color(220,40,40);

                int bs = 22;
                int bx = x + CELL_SIZE - bs - 4;
                int by = y + 4;
                g2.setColor(badge);
                g2.fillOval(bx, by, bs, bs);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(bx, by, bs, bs);
                g2.setFont(DominosaGame.POPPINS_BOLD.deriveFont(Font.BOLD, 12f));
                FontMetrics fm = g2.getFontMetrics();
                String dv = String.valueOf(dpVal);
                g2.drawString(dv, bx + (bs - fm.stringWidth(dv))/2, by + (bs + fm.getAscent())/2 - 3);
            }
        }
    }

    private void drawChainTrace(Graphics2D g2) {
        for (int i = 0; i < chainTraceCells.size(); i++) {
            Cell cell = chainTraceCells.get(i);
            int x = MARGIN + cell.col * CELL_SIZE;
            int y = MARGIN + cell.row * CELL_SIZE;
            g2.setColor(CHAIN_FILL);
            g2.fillRect(x+2, y+2, CELL_SIZE-4, CELL_SIZE-4);
            g2.setColor(CHAIN_BORDER);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRect(x+2, y+2, CELL_SIZE-4, CELL_SIZE-4);
            g2.setFont(DominosaGame.POPPINS_BOLD.deriveFont(Font.BOLD, 11f));
            g2.setColor(new Color(20, 25, 55));
            g2.drawString("x"+(i+1), x+5, y+15);
            if (i > 0) {
                Cell prev = chainTraceCells.get(i-1);
                int px = MARGIN + prev.col*CELL_SIZE + CELL_SIZE/2;
                int py = MARGIN + prev.row*CELL_SIZE + CELL_SIZE/2;
                g2.setColor(CHAIN_BORDER);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(px, py, x + CELL_SIZE/2, y + CELL_SIZE/2);
            }
        }
    }

    private void highlightCell(Graphics2D g, Cell c, Color fill, Color border) {
        int x = MARGIN + c.col * CELL_SIZE;
        int y = MARGIN + c.row * CELL_SIZE;
        g.setColor(fill);
        g.fillRoundRect(x+5, y+5, CELL_SIZE-10, CELL_SIZE-10, 14, 14);
        g.setColor(border);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(x+5, y+5, CELL_SIZE-10, CELL_SIZE-10, 14, 14);

        // Re-draw pip so it's visible on highlight
        String num = String.valueOf(board.getValue(c.row, c.col));
        g.setFont(DominosaGame.POPPINS_BOLD.deriveFont(Font.BOLD, 28f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(20, 25, 55));
        g.drawString(num, x + (CELL_SIZE - fm.stringWidth(num))/2, y + (CELL_SIZE + fm.getAscent())/2 - 5);
    }

    private void drawDominoOverlay(Graphics2D g, Domino d, Color fill, Color border, boolean showValues) {
        Cell c1 = d.getCell1(), c2 = d.getCell2();
        int r1=c1.row, col1=c1.col, r2=c2.row, col2=c2.col;
        int x = MARGIN + Math.min(col1,col2)*CELL_SIZE;
        int y = MARGIN + Math.min(r1,r2)*CELL_SIZE;
        int w = (Math.abs(col1-col2)+1)*CELL_SIZE;
        int h = (Math.abs(r1-r2)+1)*CELL_SIZE;
        int arc = 18;

        g.setColor(fill);
        g.fillRoundRect(x+4, y+4, w-8, h-8, arc, arc);
        g.setColor(border);
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawRoundRect(x+4, y+4, w-8, h-8, arc, arc);

        // Center divider line for visual clarity
        if (showValues) {
            g.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 80));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (d.isHorizontal()) {
                int mx = x + w/2;
                g.drawLine(mx, y+8, mx, y+h-8);
            } else {
                int my = y + h/2;
                g.drawLine(x+8, my, x+w-8, my);
            }

            g.setFont(DominosaGame.POPPINS_BOLD.deriveFont(Font.BOLD, 28f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(20, 25, 55));

            String num1 = String.valueOf(board.getValue(c1.row, c1.col));
            int cx1 = MARGIN + col1*CELL_SIZE + CELL_SIZE/2;
            int cy1 = MARGIN + c1.row*CELL_SIZE + CELL_SIZE/2;
            g.drawString(num1, cx1 - fm.stringWidth(num1)/2, cy1 + fm.getAscent()/2 - 4);

            String num2 = String.valueOf(board.getValue(c2.row, c2.col));
            int cx2 = MARGIN + col2*CELL_SIZE + CELL_SIZE/2;
            int cy2 = MARGIN + c2.row*CELL_SIZE + CELL_SIZE/2;
            g.drawString(num2, cx2 - fm.stringWidth(num2)/2, cy2 + fm.getAscent()/2 - 4);
        }
    }

    private void drawOracleLabel(Graphics2D g2, Domino d) {
        Cell c1=d.getCell1(), c2=d.getCell2();
        int cx = MARGIN + Math.min(c1.col,c2.col)*CELL_SIZE + (Math.abs(c1.col-c2.col)+1)*CELL_SIZE/2;
        int cy = MARGIN + Math.min(c1.row,c2.row)*CELL_SIZE + (Math.abs(c1.row-c2.row)+1)*CELL_SIZE/2;
        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        FontMetrics fm = g2.getFontMetrics();
        String label = "\u2B50";
        g2.drawString(label, cx - fm.stringWidth(label)/2, cy + fm.getAscent()/2 - 4);
    }

    private void drawComparisonHighlights(Graphics2D g2) {
        for (Cell cell : linearHighlights) {
            int x = MARGIN + cell.col*CELL_SIZE;
            int y = MARGIN + cell.row*CELL_SIZE;
            g2.setColor(new Color(220, 50, 50, 90));
            g2.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            g2.setColor(new Color(200, 30, 30, 200));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRect(x, y, CELL_SIZE, CELL_SIZE);
        }
        int midR = board.getRows()/2, midC = board.getCols()/2;
        if (zoneHighlights.contains(DominoBoard.ZONE_VERTICAL))   drawZoneOverlay(g2, 0,    0,    midC, midR);
        if (zoneHighlights.contains(DominoBoard.ZONE_EVEN))       drawZoneOverlay(g2, midC, 0,    midC, midR);
        if (zoneHighlights.contains(DominoBoard.ZONE_ODD))        drawZoneOverlay(g2, 0,    midR, midC, midR);
        if (zoneHighlights.contains(DominoBoard.ZONE_HORIZONTAL)) drawZoneOverlay(g2, midC, midR, midC, midR);
        for (Cell cell : bestMoveHighlights) {
            int x = MARGIN + cell.col*CELL_SIZE;
            int y = MARGIN + cell.row*CELL_SIZE;
            g2.setColor(new Color(22, 160, 70, 110));
            g2.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            g2.setColor(new Color(10, 130, 50, 230));
            g2.setStroke(new BasicStroke(3.5f));
            g2.drawRect(x, y, CELL_SIZE, CELL_SIZE);
        }
    }

    private void drawZoneOverlay(Graphics2D g2, int startCol, int startRow, int w, int h) {
        int x = MARGIN + startCol*CELL_SIZE;
        int y = MARGIN + startRow*CELL_SIZE;
        g2.setColor(new Color(37, 99, 235, 60));
        g2.fillRect(x, y, w*CELL_SIZE, h*CELL_SIZE);
        g2.setColor(new Color(37, 99, 235, 200));
        g2.setStroke(new BasicStroke(3));
        g2.drawRect(x, y, w*CELL_SIZE, h*CELL_SIZE);
    }

    private void drawZoneLabels(Graphics2D g2) {
        g2.setFont(DominosaGame.POPPINS_SEMIBOLD.deriveFont(Font.BOLD, 10f));
        int lx1 = MARGIN + 3;
        int lx2 = MARGIN + 4*CELL_SIZE + 3;
        // Top labels above board
        g2.setColor(COL_VERT_BORDER);
        g2.drawString("VERTICAL", lx1, MARGIN - 6);
        g2.setColor(COL_EVEN_BORDER);
        g2.drawString("EVEN SUM", lx2, MARGIN - 6);
        // Bottom labels below board
        g2.setColor(COL_ODD_BORDER);
        g2.drawString("ODD SUM", lx1, MARGIN + 8*CELL_SIZE + 14);
        g2.setColor(COL_HORZ_BORDER);
        g2.drawString("HORIZONTAL", lx2, MARGIN + 8*CELL_SIZE + 14);
    }

    /**
     * Draws floating score popups as pill-shaped cards with multi-line text.
     * Professional design: dark translucent pill, colored score, muted details.
     */
    private void drawFloatingTexts(Graphics2D g2) {
        for (FloatingText ft : floatingTexts) {
            AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ft.alpha);
            Composite old = g2.getComposite();
            g2.setComposite(ac);

            // Measure lines
            int lineH = 20;
            int padding = 14;
            Font scoreFont  = DominosaGame.POPPINS_BOLD.deriveFont(Font.BOLD, 24f);
            Font detailFont = DominosaGame.POPPINS_REGULAR.deriveFont(12f);

            g2.setFont(scoreFont);
            FontMetrics smf = g2.getFontMetrics();
            int maxW = smf.stringWidth(ft.lines[0]);
            for (int i = 1; i < ft.lines.length; i++) {
                g2.setFont(detailFont);
                FontMetrics dmf = g2.getFontMetrics();
                maxW = Math.max(maxW, dmf.stringWidth(ft.lines[i]));
            }

            int totalH = smf.getHeight() + (ft.lines.length - 1) * (lineH + 2) + padding;
            int pillW = maxW + padding * 2 + 8;
            int pillH = totalH;
            int px = ft.x - pillW / 2;
            int py = ft.y - pillH / 2;

            // Pill background
            g2.setColor(new Color(240, 244, 255, 220));
            g2.fillRoundRect(px, py, pillW, pillH, 18, 18);

            // Pill border
            g2.setColor(new Color(ft.color.getRed(), ft.color.getGreen(), ft.color.getBlue(), 200));
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawRoundRect(px, py, pillW, pillH, 18, 18);

            // Score line (large, colored)
            g2.setFont(scoreFont);
            g2.setColor(ft.color);
            FontMetrics sf = g2.getFontMetrics();
            int ty = py + padding + sf.getAscent() - 4;
            g2.drawString(ft.lines[0], ft.x - sf.stringWidth(ft.lines[0])/2, ty);

            // Detail lines (small, muted)
            g2.setFont(detailFont);
            FontMetrics df = g2.getFontMetrics();
            for (int i = 1; i < ft.lines.length; i++) {
                ty += lineH + 2;
                g2.setColor(new Color(60, 70, 110, 210));
                g2.drawString(ft.lines[i], ft.x - df.stringWidth(ft.lines[i])/2, ty);
            }

            g2.setComposite(old);
        }
    }
}

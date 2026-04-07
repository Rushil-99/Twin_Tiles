import java.util.*;

/**
 * DominoBoard Class (Review 3: DP Synergy Chain)
 * - Standard 8x8 Grid
 * - Graph-Based Logic Intact
 * - NEW: dpChainTable for bottom-up memoized chain scoring
 */
public class DominoBoard {
    private int[][] grid;
    private boolean[][] occupied;
    private int rows;
    private int cols;
    private int maxNumber;
    private Map<String, Integer> remainingDominoes;
    private List<Domino> placedDominoes;
    private BoardGraph graph;

    // DP Chain Table: dpChainTable[r][c] = longest matching chain ending at (r,c)
    private int[][] dpChainTable;

    // Zone Constants
    public static final int ZONE_VERTICAL = 1;   // Top-Left
    public static final int ZONE_EVEN = 2;       // Top-Right
    public static final int ZONE_ODD = 3;        // Bottom-Left
    public static final int ZONE_HORIZONTAL = 4; // Bottom-Right
    
    public DominoBoard() {
        this.rows = 8;
        this.cols = 8;
        this.maxNumber = 6; 
        this.grid = new int[rows][cols];
        this.occupied = new boolean[rows][cols];
        this.remainingDominoes = new HashMap<>();
        this.placedDominoes = new ArrayList<>();
        
        generateSmartBoard(); 
        initializeDeckFromBoard();
        this.graph = new BoardGraph(this);
    }
    
    /**
     * Generates a solvable board by placing dominoes according to Zone Rules.
     */
    private void generateSmartBoard() {
        // Initialize with -1 to mark empty
        for (int[] row : grid) Arrays.fill(row, -1);

        // Initialize DP table to all zeros
        dpChainTable = new int[rows][cols];
        
        Random rand = new Random();
        List<int[]> evenTiles = new ArrayList<>();
        List<int[]> oddTiles = new ArrayList<>();
        
        // Generate a large pool of valid tiles
        for(int i=0; i<50; i++) {
            int v1 = rand.nextInt(maxNumber + 1);
            int v2 = rand.nextInt(maxNumber + 1);
            if((v1+v2)%2==0) evenTiles.add(new int[]{v1,v2});
            else oddTiles.add(new int[]{v1,v2});
        }
        
        // 1. Fill Zone 2 (Top-Right): EVEN Sums
        fillZoneMath(0, 4, evenTiles, true);
        
        // 2. Fill Zone 3 (Bottom-Left): ODD Sums
        fillZoneMath(4, 0, oddTiles, false);
        
        // 3. Fill Zone 1 (Top-Left): VERTICAL Only
        List<int[]> structuralTiles = new ArrayList<>();
        structuralTiles.addAll(evenTiles);
        structuralTiles.addAll(oddTiles);
        Collections.shuffle(structuralTiles);
        fillZoneStructural(0, 0, structuralTiles, true);
        
        // 4. Fill Zone 4 (Bottom-Right): HORIZONTAL Only
        fillZoneStructural(4, 4, structuralTiles, false);
    }

    private void fillZoneMath(int rStart, int cStart, List<int[]> deck, boolean isEven) {
        for (int r = rStart; r < rStart + 4; r++) {
            for (int c = cStart; c < cStart + 4; c++) {
                if (grid[r][c] == -1) {
                    int[] tile = deck.remove(0);
                    if (c + 1 < cStart + 4 && grid[r][c+1] == -1) {
                        grid[r][c] = tile[0]; grid[r][c+1] = tile[1];
                    } else if (r + 1 < rStart + 4 && grid[r+1][c] == -1) {
                        grid[r][c] = tile[0]; grid[r+1][c] = tile[1];
                    }
                }
            }
        }
    }

    private void fillZoneStructural(int rStart, int cStart, List<int[]> deck, boolean vertical) {
        for (int r = rStart; r < rStart + 4; r++) {
            for (int c = cStart; c < cStart + 4; c++) {
                if (grid[r][c] == -1) {
                    int[] tile = deck.remove(0);
                    if (vertical) {
                        if (r + 1 < rStart + 4) {
                            grid[r][c] = tile[0]; grid[r+1][c] = tile[1];
                        }
                    } else {
                        if (c + 1 < cStart + 4) {
                            grid[r][c] = tile[0]; grid[r][c+1] = tile[1];
                        }
                    }
                }
            }
        }
    }
    
    private void initializeDeckFromBoard() {
        remainingDominoes.clear();
        for (int i = 0; i <= maxNumber; i++) {
            for (int j = i; j <= maxNumber; j++) {
                remainingDominoes.put(i + "-" + j, 1);
            }
        }
    }

    // --- DP CHAIN TABLE LOGIC ---

    /**
     * Calculates the DP chain length for placing a domino cell with the given value.
     * Checks all 4 orthogonal occupied neighbors; if a neighbor's grid value matches
     * 'value', it is a potential chain extension.
     *
     * State:    dpChainTable[r][c] = longest matching chain ending at (r,c)
     * Transition: calculateDPChain(cell, value) = 1 + max(dpChainTable of matching occupied neighbors)
     *
     * Time Complexity: O(1) — only 4 neighbors checked
     *
     * @param cell  the cell being evaluated
     * @param value the pip value to match against neighbors
     * @return the chain length (minimum 1)
     */
    public int calculateDPChain(Cell cell, int value) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int maxChain = 0;

        for (int[] dir : directions) {
            int nr = cell.row + dir[0];
            int nc = cell.col + dir[1];

            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                // Neighbor must be occupied and its grid value must match
                if (occupied[nr][nc] && grid[nr][nc] == value) {
                    maxChain = Math.max(maxChain, dpChainTable[nr][nc]);
                }
            }
        }

        return maxChain + 1;
    }

    /**
     * Returns the memoized DP chain value at a given cell.
     */
    public int getDPValue(int r, int c) {
        return dpChainTable[r][c];
    }

    // --- ZONE LOGIC ---
    public int getZoneID(int r, int c) {
        int midR = rows / 2;
        int midC = cols / 2;
        if (r < midR) return (c < midC) ? ZONE_VERTICAL : ZONE_EVEN;
        else return (c < midC) ? ZONE_ODD : ZONE_HORIZONTAL;
    }
    
    public boolean isValidPlacement(Domino domino) {
        Cell c1 = domino.getCell1();
        Cell c2 = domino.getCell2();
        
        if (!isValidCell(c1) || !isValidCell(c2)) return false;
        if (graph.isNodeOccupied(c1) || graph.isNodeOccupied(c2)) return false;
        if (!areAdjacent(c1, c2)) return false;

        int zone1 = getZoneID(c1.row, c1.col);
        int zone2 = getZoneID(c2.row, c2.col);
        if (zone1 != zone2) return false;
        int sum = grid[c1.row][c1.col] + grid[c2.row][c2.col];

        switch (zone1) {
            case ZONE_VERTICAL: if (!domino.isVertical()) return false; break;
            case ZONE_EVEN:     if (sum % 2 != 0) return false; break;
            case ZONE_ODD:      if (sum % 2 == 0) return false; break;
            case ZONE_HORIZONTAL: if (!domino.isHorizontal()) return false; break;
        }

        String key = getDominoKey(grid[c1.row][c1.col], grid[c2.row][c2.col]);
        return remainingDominoes.containsKey(key) && remainingDominoes.get(key) > 0;
    }

    public String getInvalidPlacementReason(Domino domino) {
        Cell c1 = domino.getCell1();
        Cell c2 = domino.getCell2();
        if (!isValidCell(c1) || !isValidCell(c2)) return "Selected cells are out of bounds.";
        if (graph.isNodeOccupied(c1) || graph.isNodeOccupied(c2)) return "One or both cells are already occupied.";
        if (!areAdjacent(c1, c2)) return "Cells must be adjacent to form a domino.";

        int zone1 = getZoneID(c1.row, c1.col);
        int zone2 = getZoneID(c2.row, c2.col);
        if (zone1 != zone2) return "Both tiles must be in the same zone.";

        int sum = grid[c1.row][c1.col] + grid[c2.row][c2.col];
        switch (zone1) {
            case ZONE_VERTICAL:
                if (!domino.isVertical()) return "This zone requires vertical placements.";
                break;
            case ZONE_EVEN:
                if (sum % 2 != 0) return "This zone requires tiles with an even sum.";
                break;
            case ZONE_ODD:
                if (sum % 2 == 0) return "This zone requires tiles with an odd sum.";
                break;
            case ZONE_HORIZONTAL:
                if (!domino.isHorizontal()) return "This zone requires horizontal placements.";
                break;
            default:
                break;
        }

        String key = getDominoKey(grid[c1.row][c1.col], grid[c2.row][c2.col]);
        if (!remainingDominoes.containsKey(key) || remainingDominoes.get(key) <= 0) return "No remaining domino of this type.";
        return null;
    }

    /**
     * Place a domino and set its owner (CPU or human).
     * Also updates the dpChainTable for both cells BEFORE marking them occupied,
     * so calculateDPChain sees only the pre-existing neighbors.
     *
     * Time Complexity: O(1) for DP update (4 neighbors each cell)
     */
    public void placeDomino(Domino domino, boolean placedByCPU) {
        Cell c1 = domino.getCell1();
        Cell c2 = domino.getCell2();
        domino.setPlacedByCPU(placedByCPU);

        int val1 = grid[c1.row][c1.col];
        int val2 = grid[c2.row][c2.col];

        // Calculate DP chains BEFORE marking cells occupied
        int chain1 = calculateDPChain(c1, val1);
        int chain2 = calculateDPChain(c2, val2);

        // For a double (e.g., [4,4]), both cells get the same chain value.
        // They share the same matching context, so use the better of the two chains.
        if (val1 == val2) {
            int sharedChain = Math.max(chain1, chain2);
            dpChainTable[c1.row][c1.col] = sharedChain;
            dpChainTable[c2.row][c2.col] = sharedChain;
        } else {
            dpChainTable[c1.row][c1.col] = chain1;
            dpChainTable[c2.row][c2.col] = chain2;
        }

        // Update graph first (authoritative), then mirror to the occupied array
        graph.addEdge(c1, c2);
        occupied[c1.row][c1.col] = true;
        occupied[c2.row][c2.col] = true;
        
        String key = getDominoKey(val1, val2);
        if (remainingDominoes.containsKey(key)) {
            int count = remainingDominoes.get(key);
            if (count > 1) remainingDominoes.put(key, count - 1);
            else remainingDominoes.remove(key);
        }
        placedDominoes.add(domino);
    }
    
    // --- UNDO SUPPORT ---
    public void removeLastDomino() {
        if (placedDominoes.isEmpty()) return;
        
        Domino last = placedDominoes.remove(placedDominoes.size() - 1);
        Cell c1 = last.getCell1();
        Cell c2 = last.getCell2();
        
        // Re-add to deck
        String key = getDominoKey(grid[c1.row][c1.col], grid[c2.row][c2.col]);
        remainingDominoes.put(key, remainingDominoes.getOrDefault(key, 0) + 1);
        
        // Clear occupancy
        occupied[c1.row][c1.col] = false;
        occupied[c2.row][c2.col] = false;
        
        // Rebuild Graph
        this.graph = new BoardGraph(this);
        for (Domino d : placedDominoes) {
            graph.addEdge(d.getCell1(), d.getCell2());
        }

        // Rebuild DP table to reflect current placed dominoes
        rebuildDPTable();
    }

    /**
     * Rebuilds the dpChainTable by replaying all currently placed dominoes
     * chronologically. This is called after an undo to restore accurate DP state.
     *
     * Time Complexity: O(D) where D is the number of placed dominoes.
     */
    private void rebuildDPTable() {
        // Reset the entire table
        dpChainTable = new int[rows][cols];

        // Temporarily snapshot and replay
        List<Domino> snapshot = new ArrayList<>(placedDominoes);

        // We need to simulate placement without actually modifying game state,
        // so we use a temporary occupancy array and DP table.
        boolean[][] tempOccupied = new boolean[rows][cols];
        int[][] tempDP = new int[rows][cols];

        for (Domino d : snapshot) {
            Cell c1 = d.getCell1();
            Cell c2 = d.getCell2();
            int val1 = grid[c1.row][c1.col];
            int val2 = grid[c2.row][c2.col];

            // Calculate chains using the temp state
            int chain1 = calcChainFromTemp(c1, val1, tempOccupied, tempDP);
            int chain2 = calcChainFromTemp(c2, val2, tempOccupied, tempDP);

            if (val1 == val2) {
                int sharedChain = Math.max(chain1, chain2);
                tempDP[c1.row][c1.col] = sharedChain;
                tempDP[c2.row][c2.col] = sharedChain;
            } else {
                tempDP[c1.row][c1.col] = chain1;
                tempDP[c2.row][c2.col] = chain2;
            }

            tempOccupied[c1.row][c1.col] = true;
            tempOccupied[c2.row][c2.col] = true;
        }

        // Copy rebuilt table into the real dpChainTable
        dpChainTable = tempDP;
    }

    /**
     * Helper for rebuildDPTable: calculates chain length using a temporary state.
     */
    private int calcChainFromTemp(Cell cell, int value, boolean[][] tempOccupied, int[][] tempDP) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int maxChain = 0;

        for (int[] dir : directions) {
            int nr = cell.row + dir[0];
            int nc = cell.col + dir[1];

            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (tempOccupied[nr][nc] && grid[nr][nc] == value) {
                    maxChain = Math.max(maxChain, tempDP[nr][nc]);
                }
            }
        }

        return maxChain + 1;
    }
    
    public List<Domino> getAllValidMoves() {
        List<Domino> moves = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!occupied[r][c]) {
                    if (c + 1 < cols && !occupied[r][c+1]) {
                        Domino d = new Domino(new Cell(r,c), new Cell(r,c+1), grid[r][c], grid[r][c+1]);
                        if (isValidPlacement(d)) moves.add(d);
                    }
                    if (r + 1 < rows && !occupied[r+1][c]) {
                        Domino d = new Domino(new Cell(r,c), new Cell(r+1,c), grid[r][c], grid[r+1][c]);
                        if (isValidPlacement(d)) moves.add(d);
                    }
                }
            }
        }
        return moves;
    }
    
    /**
     * Get valid moves only within a specific zone (Divide & Conquer approach).
     */
    public List<Domino> getMovesInZone(int zoneID) {
        List<Domino> moves = new ArrayList<>();
        int midR = rows / 2;
        int midC = cols / 2;
        
        int minRow = 0, maxRow = 0, minCol = 0, maxCol = 0;
        
        switch (zoneID) {
            case ZONE_VERTICAL:
                minRow = 0; maxRow = midR - 1; minCol = 0; maxCol = midC - 1; break;
            case ZONE_EVEN:
                minRow = 0; maxRow = midR - 1; minCol = midC; maxCol = cols - 1; break;
            case ZONE_ODD:
                minRow = midR; maxRow = rows - 1; minCol = 0; maxCol = midC - 1; break;
            case ZONE_HORIZONTAL:
                minRow = midR; maxRow = rows - 1; minCol = midC; maxCol = cols - 1; break;
        }
        
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                if (!occupied[r][c]) {
                    if (c + 1 <= maxCol && !occupied[r][c+1]) {
                        Domino d = new Domino(new Cell(r,c), new Cell(r,c+1), grid[r][c], grid[r][c+1]);
                        if (isValidPlacement(d)) moves.add(d);
                    }
                    if (r + 1 <= maxRow && !occupied[r+1][c]) {
                        Domino d = new Domino(new Cell(r,c), new Cell(r+1,c), grid[r][c], grid[r+1][c]);
                        if (isValidPlacement(d)) moves.add(d);
                    }
                }
            }
        }
        
        return moves;
    }

    /**
     * Traces the existing chain that a proposed move at 'cell' would extend.
     * Walks backward from the best matching occupied neighbor, following cells
     * with the same pip value and strictly decreasing DP values.
     *
     * Time Complexity: O(K) where K = chain length (at most R*C).
     *
     * @param cell  the (unoccupied) cell where a move would be placed
     * @param value the pip value to match against neighbors
     * @return ordered list of cells forming the existing chain (base → tip)
     */
    public List<Cell> getChainPath(Cell cell, int value) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        int maxNeighborDP = 0;
        Cell bestNeighbor = null;
        for (int[] dir : directions) {
            int nr = cell.row + dir[0];
            int nc = cell.col + dir[1];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                if (occupied[nr][nc] && grid[nr][nc] == value && dpChainTable[nr][nc] > maxNeighborDP) {
                    maxNeighborDP = dpChainTable[nr][nc];
                    bestNeighbor = new Cell(nr, nc);
                }
            }
        }

        List<Cell> path = new ArrayList<>();
        if (bestNeighbor == null) return path;

        Cell current = bestNeighbor;
        int currentDP = dpChainTable[current.row][current.col];
        path.add(current);

        while (currentDP > 1) {
            boolean found = false;
            for (int[] dir : directions) {
                int nr = current.row + dir[0];
                int nc = current.col + dir[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    if (occupied[nr][nc] && grid[nr][nc] == value && dpChainTable[nr][nc] == currentDP - 1) {
                        current = new Cell(nr, nc);
                        path.add(current);
                        currentDP--;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) break;
        }

        Collections.reverse(path);
        return path;
    }

    // Standard Getters/Helpers
    private boolean isValidCell(Cell c) { return c.row >= 0 && c.row < rows && c.col >= 0 && c.col < cols; }
    private boolean areAdjacent(Cell c1, Cell c2) { return (Math.abs(c1.row - c2.row) + Math.abs(c1.col - c2.col)) == 1; }
    private String getDominoKey(int v1, int v2) { return Math.min(v1, v2) + "-" + Math.max(v1, v2); }
    public int[][] getGrid() { return grid; }
    public boolean[][] getOccupied() { return occupied; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public BoardGraph getGraph() { return graph; }
    public int getValue(int r, int c) { return grid[r][c]; }
    public List<Domino> getPlacedDominoes() { return placedDominoes; } 
    public boolean isGameComplete() { return remainingDominoes.isEmpty(); }
    public boolean isOccupied(int r, int c) { return graph.isNodeOccupied(new Cell(r, c)); }
    public boolean isOccupiedNode(int r, int c) { return graph.isNodeOccupied(new Cell(r, c)); }
}

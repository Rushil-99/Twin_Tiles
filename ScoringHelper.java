import java.util.*;

/**
 * ScoringHelper Utility Class
 * Centralized scoring and evaluation logic used by CPU strategies.
 * Review 3: Integrates DP Synergy Chain multiplier into move evaluation.
 */
public class ScoringHelper {
    private DominoBoard board;
    
    public ScoringHelper(DominoBoard board) {
        this.board = board;
    }
    
    /**
     * Evaluates a move's score using Greedy heuristics + DP Chain multiplier.
     *
     * Priority:
     *   1. Base sum value (Greedy for Points), multiplied by the DP chain potential
     *   2. Constraint Score
     *   3. Rarity Score
     *   4. Isolation penalty/bonus
     *
     * DP Multiplier logic:
     *   - chain1 / chain2 = calculateDPChain() peek for each cell (no board mutation)
     *   - If the domino is a double (val1 == val2), both cells share the chain context,
     *     so multiplier = max(chain1, chain2) + 1 (the double extends one sequence by 2).
     *   - Otherwise, multiplier = max(chain1, chain2) (best chain opportunity wins).
     *   - multiplier is at least 1, so a non-chaining move is never penalised.
     *
     * Time Complexity: O(1) — all sub-calls are constant (4-neighbor checks)
     */
    public double evaluateMoveScore(Domino domino) {
        Cell c1 = domino.getCell1();
        Cell c2 = domino.getCell2();
        
        int val1 = board.getValue(c1.row, c1.col);
        int val2 = board.getValue(c2.row, c2.col);
        
        // 1. PRIMARY PRIORITY: The Sum Value (Greedy for Points)
        double baseScore = (val1 + val2) * 100;

        // DP Chain Multiplier — peek potential WITHOUT placing the domino
        int chain1 = board.calculateDPChain(c1, val1);
        int chain2 = board.calculateDPChain(c2, val2);

        int multiplier;
        if (val1 == val2) {
            // Double: both cells share the same value context.
            // Use the maximum chain between the two neighbors.
            multiplier = Math.max(chain1, chain2);
        } else {
            // Mixed: reward whichever cell connects to a longer matching run.
            multiplier = Math.max(chain1, chain2);
        }

        // Ensure multiplier is at least 1 so isolated moves are scored normally
        multiplier = Math.max(1, multiplier);

        // Apply multiplier to base score
        double score = baseScore * multiplier;
        
        // 2. Constraint Score
        score += (getConstraintScore(c1) + getConstraintScore(c2)) * 5;
        
        // 3. Rarity Score
        score += getRarityScore(val1, val2) * 3;
        
        // 4. Isolation Check
        if (!createsIsolation(domino)) {
            score += 10;
        }
        
        return score;
    }
    
    /**
     * Calculates constraint score for a cell.
     * Higher score if fewer unoccupied neighbors (more constrained).
     */
    private int getConstraintScore(Cell cell) {
        int unoccupiedNeighbors = 0;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        
        for (int[] dir : directions) {
            int newRow = cell.row + dir[0];
            int newCol = cell.col + dir[1];
            
            if (newRow >= 0 && newRow < board.getRows() && 
                newCol >= 0 && newCol < board.getCols()) {
                if (!board.getGraph().isNodeOccupied(new Cell(newRow, newCol))) {
                    unoccupiedNeighbors++;
                }
            }
        }
        
        return 4 - unoccupiedNeighbors;
    }
    
    /**
     * Calculates rarity score for a domino pair.
     * Doubles get higher score than mixed pairs.
     */
    private double getRarityScore(int val1, int val2) {
        if (val1 == val2) return 5.0;
        return (val1 + val2) / 10.0;
    }
    
    /**
     * Checks if placing a domino would isolate adjacent cells.
     * Returns true if the move creates isolation.
     */
    private boolean createsIsolation(Domino domino) {
        Cell c1 = domino.getCell1();
        Cell c2 = domino.getCell2();
        Set<Cell> adjacentCells = new HashSet<>();
        addAdjacentCells(c1, adjacentCells);
        addAdjacentCells(c2, adjacentCells);
        
        for (Cell cell : adjacentCells) {
            if (!board.getGraph().isNodeOccupied(cell)) {
                int freeNeighbors = countFreeNeighbors(cell, c1, c2);
                if (freeNeighbors <= 1) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void addAdjacentCells(Cell cell, Set<Cell> cells) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] dir : directions) {
            int newRow = cell.row + dir[0];
            int newCol = cell.col + dir[1];
            if (newRow >= 0 && newRow < board.getRows() && 
                newCol >= 0 && newCol < board.getCols()) {
                cells.add(new Cell(newRow, newCol));
            }
        }
    }
    
    private int countFreeNeighbors(Cell cell, Cell exclude1, Cell exclude2) {
        int count = 0;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        
        for (int[] dir : directions) {
            int newRow = cell.row + dir[0];
            int newCol = cell.col + dir[1];
            
            if (newRow >= 0 && newRow < board.getRows() && 
                newCol >= 0 && newCol < board.getCols()) {
                Cell neighbor = new Cell(newRow, newCol);
                if (!board.getGraph().isNodeOccupied(neighbor) && 
                    !neighbor.equals(exclude1) && !neighbor.equals(exclude2)) {
                    count++;
                }
            }
        }
        return count;
    }
}

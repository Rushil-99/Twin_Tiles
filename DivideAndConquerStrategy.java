import java.util.*;

/**
 * DivideAndConquerStrategy Class
 * Implements Divide & Conquer algorithm for CPU player to find the BEST move
 * - Divide: Extract valid moves from each of the 4 zones
 * - Conquer: Sort moves from each zone using Merge Sort
 * - Combine: Merge the 4 sorted lists into one final sorted list
 * - Select: Always pick the BEST move (highest score)
 */
public class DivideAndConquerStrategy {
    private DominoBoard board;
    private ScoringHelper scoringHelper;
    
    public DivideAndConquerStrategy(DominoBoard board) {
        this.board = board;
        this.scoringHelper = new ScoringHelper(board);
    }
    
    /**
     * Find the BEST move using Divide & Conquer Zone approach
     * Always returns the highest-scoring move
     */
    public Domino findMove() {
        List<ScoredMove> allSortedMoves = divideAndConquerBoard();
        
        if (allSortedMoves.isEmpty()) {
            return null;
        }
        
        // Always select the best move (highest score - last in sorted list)
        return allSortedMoves.get(allSortedMoves.size() - 1).move;
    }
    
    /**
     * Divide & Conquer approach:
     * 1. DIVIDE: Get valid moves from each of the 4 zones
     * 2. CONQUER: Sort moves from each zone using Merge Sort
     * 3. COMBINE: Merge the 4 sorted lists into one final sorted list
     */
    private List<ScoredMove> divideAndConquerBoard() {
        // DIVIDE: Extract moves from all 4 zones
        List<ScoredMove> movesZone1 = getAndSortMovesInZone(DominoBoard.ZONE_VERTICAL);   // Top-Left
        List<ScoredMove> movesZone2 = getAndSortMovesInZone(DominoBoard.ZONE_EVEN);       // Top-Right
        List<ScoredMove> movesZone3 = getAndSortMovesInZone(DominoBoard.ZONE_ODD);        // Bottom-Left
        List<ScoredMove> movesZone4 = getAndSortMovesInZone(DominoBoard.ZONE_HORIZONTAL); // Bottom-Right
        
        // COMBINE: Merge all 4 sorted lists into one
        // Merge Zone 1 and Zone 2
        List<ScoredMove> topHalf = mergeZoneLists(movesZone1, movesZone2);
        
        // Merge Zone 3 and Zone 4
        List<ScoredMove> bottomHalf = mergeZoneLists(movesZone3, movesZone4);
        
        // Merge top half and bottom half
        List<ScoredMove> combined = mergeZoneLists(topHalf, bottomHalf);
        
        return combined;
    }
    
    /**
     * Gets valid moves from a specific zone and sorts them using Merge Sort
     */
    private List<ScoredMove> getAndSortMovesInZone(int zoneID) {
        // Get all valid moves in this zone
        List<Domino> validMoves = board.getMovesInZone(zoneID);
        
        if (validMoves.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Score each move
        List<ScoredMove> scoredMoves = new ArrayList<>();
        for (Domino move : validMoves) {
            double score = scoringHelper.evaluateMoveScore(move);
            scoredMoves.add(new ScoredMove(move, score));
        }
        
        // CONQUER: Sort using Merge Sort (Divide & Conquer algorithm)
        mergeSortMoves(scoredMoves);
        
        return scoredMoves;
    }
    
    /**
     * Merge Sort Implementation (Divide & Conquer)
     * Sorts moves from LOWEST to HIGHEST score
     */
    private void mergeSortMoves(List<ScoredMove> moves) {
        if (moves.size() <= 1) {
            return;
        }
        
        int mid = moves.size() / 2;
        List<ScoredMove> left = new ArrayList<>(moves.subList(0, mid));
        List<ScoredMove> right = new ArrayList<>(moves.subList(mid, moves.size()));
        
        // Divide: Recursively sort both halves
        mergeSortMoves(left);
        mergeSortMoves(right);
        
        // Conquer: Merge the sorted halves
        merge(moves, left, right);
    }
    
    /**
     * Merges two sorted lists into one sorted list (ascending order by score)
     */
    private void merge(List<ScoredMove> result, List<ScoredMove> left, List<ScoredMove> right) {
        result.clear();
        int i = 0, j = 0;
        
        // Merge elements in ascending order
        while (i < left.size() && j < right.size()) {
            if (left.get(i).score <= right.get(j).score) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        
        // Add remaining elements
        while (i < left.size()) {
            result.add(left.get(i++));
        }
        
        while (j < right.size()) {
            result.add(right.get(j++));
        }
    }
    
    /**
     * Merges two zone lists (both already sorted) into one sorted list
     */
    private List<ScoredMove> mergeZoneLists(List<ScoredMove> zone1, List<ScoredMove> zone2) {
        List<ScoredMove> result = new ArrayList<>();
        int i = 0, j = 0;
        
        // Merge elements in ascending order
        while (i < zone1.size() && j < zone2.size()) {
            if (zone1.get(i).score <= zone2.get(j).score) {
                result.add(zone1.get(i++));
            } else {
                result.add(zone2.get(j++));
            }
        }
        
        // Add remaining elements
        while (i < zone1.size()) {
            result.add(zone1.get(i++));
        }
        
        while (j < zone2.size()) {
            result.add(zone2.get(j++));
        }
        
        return result;
    }
    

    
    /**
     * Internal class to pair a Domino with its score for sorting
     */
    private static class ScoredMove {
        Domino move;
        double score;
        
        ScoredMove(Domino move, double score) {
            this.move = move;
            this.score = score;
        }
    }
}

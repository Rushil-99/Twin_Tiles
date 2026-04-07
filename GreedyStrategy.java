import java.util.*;

/**
 * GreedyStrategy Class
 * Implements Greedy Algorithm for CPU player
 */
public class GreedyStrategy {
    private DominoBoard board;
    private ScoringHelper scoringHelper;
    
    public GreedyStrategy(DominoBoard board) {
        this.board = board;
        this.scoringHelper = new ScoringHelper(board);
    }
    
    public Domino findBestMove() {
        List<Domino> validMoves = board.getAllValidMoves();
        
        if (validMoves.isEmpty()) {
            return null;
        }
        
        Domino bestMove = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (Domino move : validMoves) {
            double score = scoringHelper.evaluateMoveScore(move);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        
        return bestMove;
    }
    

}
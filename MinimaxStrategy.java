import java.util.*;

/**
 * ---------------------------------------------------------------------------
 * File Name: MinimaxStrategy.java
 * Class Name: MinimaxStrategy
 * ---------------------------------------------------------------------------
 * Description:
 * Implements the Minimax algorithm with Alpha-Beta Pruning — the "Grandmaster CPU."
 * Instead of greedily picking the single highest-scoring move, the CPU simulates
 * several turns ahead, alternating between its own optimal play (maximizing) and
 * the human's best response (minimizing). It backtracks from lines of play that
 * would hand the human a large advantage.
 *
 * Algorithm:
 *   MAXIMIZE (CPU turn):  pick the move whose subtree yields the highest net advantage.
 *   MINIMIZE (Human turn): assume the human picks the move that most hurts the CPU.
 *   ALPHA-BETA PRUNING:   skip subtrees that provably cannot influence the result.
 *   MOVE ORDERING:        pre-sort candidates by heuristic (ScoringHelper) so the
 *                          best-first order maximizes pruning effectiveness.
 *
 * Recurrence (from CPU's perspective, net score advantage):
 *   V_max(depth) = max over moves m of { score(m) + V_min(depth − 1) }
 *   V_min(depth) = min over moves m of { −score(m) + V_max(depth − 1) }
 *   Base case:    depth == 0 or no valid moves → 0 (rely on accumulated scores).
 *
 * ---------------------------------------------------------------------------
 * Time Complexity:
 *   - Best case  (perfect move ordering): O(M^(d/2))
 *   - Worst case (no pruning at all):     O(M^d)
 *   where M = average branching factor, d = MAX_DEPTH.
 *   Each node costs O(D) for place/undo, D = number of placed dominoes.
 *   With depth 3, ~20 moves, and good ordering: typically a few hundred nodes.
 * ---------------------------------------------------------------------------
 */
public class MinimaxStrategy {
    private DominoBoard board;
    private ScoringHelper scoringHelper;
    private DominosaGame game;
    private static final int MAX_DEPTH = 3;

    private int nodesExplored;
    private int branchesPruned;

    /**
     * Constructor.
     * @param board the current game board
     * @param game  reference to the game controller (used for calculateScore)
     */
    public MinimaxStrategy(DominoBoard board, DominosaGame game) {
        this.board = board;
        this.scoringHelper = new ScoringHelper(board);
        this.game = game;
    }

    /**
     * Method: findMove
     * Finds the best move using Minimax with Alpha-Beta Pruning.
     *
     * Time Complexity: O(M^(d/2)) best case, O(M^d) worst case.
     *
     * @return the optimal Domino move, or null if no moves are available
     */
    public Domino findMove() {
        nodesExplored = 0;
        branchesPruned = 0;

        List<Domino> validMoves = board.getAllValidMoves();
        if (validMoves.isEmpty()) return null;
        if (validMoves.size() == 1) {
            nodesExplored = 1;
            return validMoves.get(0);
        }

        // Move ordering: best-first by heuristic for maximum alpha-beta pruning
        validMoves.sort((a, b) -> Double.compare(
            scoringHelper.evaluateMoveScore(b),
            scoringHelper.evaluateMoveScore(a)
        ));

        Domino bestMove = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        for (Domino move : validMoves) {
            board.placeDomino(move, true);
            int moveScore = game.calculateScore(move);
            nodesExplored++;

            double value = moveScore + minimax(MAX_DEPTH - 1, false, alpha, beta);

            board.removeLastDomino();

            if (value > bestValue) {
                bestValue = value;
                bestMove = move;
            }
            alpha = Math.max(alpha, bestValue);
        }

        return bestMove;
    }

    /**
     * Method: minimax
     * Recursive Minimax with Alpha-Beta Pruning.
     *
     * At maximizing nodes (CPU turn) the algorithm picks the child with the
     * highest evaluation; at minimizing nodes (Human turn) it picks the lowest.
     * Alpha-beta bounds let us skip (prune) entire subtrees that cannot
     * influence the root decision — this is the backtracking component.
     *
     * Time Complexity per call: O(1) + cost of recursive children.
     *
     * @param depth        remaining search depth (plies)
     * @param isMaximizing true on CPU's turn, false on Human's turn
     * @param alpha        best value the maximizer can guarantee so far
     * @param beta         best value the minimizer can guarantee so far
     * @return net score advantage from the CPU's perspective
     */
    private double minimax(int depth, boolean isMaximizing, double alpha, double beta) {
        List<Domino> validMoves = board.getAllValidMoves();

        if (depth == 0 || validMoves.isEmpty()) {
            return evaluate();
        }

        // Move ordering at every level for pruning efficiency
        validMoves.sort((a, b) -> Double.compare(
            scoringHelper.evaluateMoveScore(b),
            scoringHelper.evaluateMoveScore(a)
        ));

        if (isMaximizing) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (Domino move : validMoves) {
                board.placeDomino(move, true);
                int moveScore = game.calculateScore(move);
                nodesExplored++;

                double eval = moveScore + minimax(depth - 1, false, alpha, beta);

                board.removeLastDomino();

                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) {
                    branchesPruned++;
                    break;
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (Domino move : validMoves) {
                board.placeDomino(move, false);
                int moveScore = game.calculateScore(move);
                nodesExplored++;

                double eval = -moveScore + minimax(depth - 1, true, alpha, beta);

                board.removeLastDomino();

                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) {
                    branchesPruned++;
                    break;
                }
            }
            return minEval;
        }
    }

    /**
     * Method: evaluate
     * Static evaluation heuristic for leaf (frontier) nodes.
     *
     * Returns 0 — the search relies entirely on the actual game scores
     * accumulated along each path. No additional positional bias is added
     * at the search horizon.
     *
     * Time Complexity: O(1)
     */
    private double evaluate() {
        return 0;
    }

    // --- Accessors for search statistics ---
    public int getNodesExplored() { return nodesExplored; }
    public int getBranchesPruned() { return branchesPruned; }
    public static int getMaxDepth() { return MAX_DEPTH; }
}

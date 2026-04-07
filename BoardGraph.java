import java.util.*;

/**
 * BoardGraph Class
 * Graph representation of the Dominosa board
 * Each cell is a vertex, placed dominoes create edges
 */
public class BoardGraph {
    private Map<Cell, List<Cell>> adjacencyList;
    private Map<Cell, Integer> cellValues;
    private Set<Cell> occupiedNodes;
    private DominoBoard board;
    private int vertexCount;
    private int edgeCount;
    
    public BoardGraph(DominoBoard board) {
        this.board = board;
        this.adjacencyList = new HashMap<>();
        this.cellValues = new HashMap<>();
        this.occupiedNodes = new HashSet<>();
        this.vertexCount = 0;
        this.edgeCount = 0;
        
        initializeGraph();
    }
    
    /**
     * Initialize graph with all board cells as vertices
     */
    private void initializeGraph() {
        int[][] grid = board.getGrid();
            
        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                Cell cell = new Cell(r, c);
                adjacencyList.put(cell, new ArrayList<>());
                cellValues.put(cell, grid[r][c]);
                // Initially, no nodes are occupied
                // occupiedNodes remains empty until dominoes are placed
                vertexCount++;
            }
        }
    }
    
    /**
     * Add an edge between two cells (when domino is placed)
     */
    public void addEdge(Cell c1, Cell c2) {
        // Avoid adding duplicate edges (which would cause double-counting)
        if (!adjacencyList.get(c1).contains(c2)) {
            adjacencyList.get(c1).add(c2);
            adjacencyList.get(c2).add(c1);
            edgeCount++;
        }

        // Mark nodes occupied when an edge (domino) is placed
        occupiedNodes.add(new Cell(c1.row, c1.col));
        occupiedNodes.add(new Cell(c2.row, c2.col));

        // Connect each newly placed cell to any already-occupied orthogonal neighbors
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        for (Cell cell : java.util.List.of(c1, c2)) {
            for (int[] d : dirs) {
                int nr = cell.row + d[0];
                int nc = cell.col + d[1];
                // Check bounds via board
                if (nr >= 0 && nr < board.getRows() && nc >= 0 && nc < board.getCols()) {
                    Cell neighbor = new Cell(nr, nc);
                    // If the neighbor is already occupied, create an edge between them
                    if (isNodeOccupied(neighbor) && !adjacencyList.get(cell).contains(neighbor)) {
                        adjacencyList.get(cell).add(neighbor);
                        adjacencyList.get(neighbor).add(cell);
                        edgeCount++;
                    }
                }
            }
        }
    }

    /**
     * Returns true if the node is occupied (i.e., part of a placed domino)
     */
    public boolean isNodeOccupied(Cell cell) {
        return occupiedNodes.contains(cell);
    }
    
    /**
     * Check if two cells are connected by an edge
     */
    public boolean hasEdge(Cell c1, Cell c2) {
        return adjacencyList.get(c1).contains(c2);
    }
    
    /**
     * Get all neighbors of a cell in the graph
     */
    public List<Cell> getNeighbors(Cell cell) {
        return new ArrayList<>(adjacencyList.getOrDefault(cell, new ArrayList<>()));
    }
    
    /**
     * Get the degree of a vertex (number of edges connected)
     */
    public int getDegree(Cell cell) {
        return adjacencyList.get(cell).size();
    }

    public int getOccupiedNeighborCount(Cell cell) {
        int count = 0;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    
        for (int[] dir : directions) {
            int newRow = cell.row + dir[0];
            int newCol = cell.col + dir[1];
        
            // Check bounds
            if (newRow >= 0 && newRow < board.getRows() && 
                newCol >= 0 && newCol < board.getCols()) {
            
                // Query the graph state
                Cell neighbor = new Cell(newRow, newCol);
                if (isNodeOccupied(neighbor)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Count occupied neighbors using the graph adjacency list only.
     * This ensures scoring must rely on graph traversal rather than grid indices.
     */
    public int countOccupiedNeighbors(Cell cell) {
        int count = 0;
        for (Cell neighbor : getNeighbors(cell)) {
            if (isNodeOccupied(neighbor)) {
                count++;
            }
        }
        return count;
    }

    
    // Getters
    public int getVertexCount() { return vertexCount; }
    public int getEdgeCount() { return edgeCount; }
    public Map<Cell, List<Cell>> getAdjacencyList() { return adjacencyList; }
}
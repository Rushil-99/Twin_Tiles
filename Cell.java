/**
 * Cell Class - Represents a cell position on the board
 * Shared utility class used across all project files
 */
public class Cell {
    public int row;
    public int col;
    
    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Cell)) return false;
        Cell other = (Cell) obj;
        return this.row == other.row && this.col == other.col;
    }
    
    @Override
    public int hashCode() {
        return row * 1000 + col;
    }
    
    @Override
    public String toString() {
        return "(" + row + "," + col + ")";
    }
}

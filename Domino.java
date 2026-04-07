/**
 * Domino Class - Represents a domino tile with two cells
 * Shared utility class used across all project files
 */
public class Domino {
    private Cell cell1;
    private Cell cell2;
    private int value1;
    private int value2;
    private boolean placedByCPU = false;
    private int awardedScore = 0;
    
    public Domino(Cell cell1, Cell cell2) {
        this.cell1 = cell1;
        this.cell2 = cell2;
    }
    
    public Domino(Cell cell1, Cell cell2, int value1, int value2) {
        this.cell1 = cell1;
        this.cell2 = cell2;
        this.value1 = value1;
        this.value2 = value2;
    }

    // optional owner flag setter/getter
    public boolean isPlacedByCPU() { return placedByCPU; }
    public void setPlacedByCPU(boolean placedByCPU) { this.placedByCPU = placedByCPU; }

    public int getAwardedScore() { return awardedScore; }
    public void setAwardedScore(int score) { this.awardedScore = score; }
    
    public Cell getCell1() { return cell1; }
    public Cell getCell2() { return cell2; }
    
    public int getValue() {
        return value1 + value2;
    }

    // Optional explicit getters for left/right values
    public int getLeftValue() { return value1; }
    public int getRightValue() { return value2; }
    
    public boolean isHorizontal() {
        return cell1.row == cell2.row;
    }
    
    public boolean isVertical() {
        return cell1.col == cell2.col;
    }
    
    @Override
    public String toString() {
        return "Domino[" + cell1 + "-" + cell2 + " values:" + value1 + "," + value2 + "]";
    }
}

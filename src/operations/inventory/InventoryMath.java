package operations.inventory;

public final class InventoryMath {
    private InventoryMath() {
    }

    public static InventoryState reservedState(boolean blocked, boolean sold) {
        if (blocked) {
            return InventoryState.BLOCKED;
        }
        return sold ? InventoryState.SOLD : InventoryState.AVAILABLE;
    }

    public static int soldQuantity(int totalCapacity, int remainingCapacity) {
        if (totalCapacity < 0 || remainingCapacity < 0 || remainingCapacity > totalCapacity) {
            throw new IllegalArgumentException("Capacity values are inconsistent");
        }
        return totalCapacity - remainingCapacity;
    }
}

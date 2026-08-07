package operations.booking;

import java.math.BigDecimal;
import java.util.List;

public final class BookingSummary {
    private final int transactionId;
    private final List<Integer> ticketIds;
    private final BigDecimal total;

    public BookingSummary(int transactionId, List<Integer> ticketIds, BigDecimal total) {
        this.transactionId = transactionId;
        this.ticketIds = List.copyOf(ticketIds);
        this.total = total;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public List<Integer> getTicketIds() {
        return ticketIds;
    }

    public BigDecimal getTotal() {
        return total;
    }
}

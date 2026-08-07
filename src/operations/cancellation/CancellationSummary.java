package operations.cancellation;

import java.math.BigDecimal;

public final class CancellationSummary {
    private final int cancelledTicketCount;
    private final BigDecimal refundTotal;

    public CancellationSummary(int cancelledTicketCount, BigDecimal refundTotal) {
        this.cancelledTicketCount = cancelledTicketCount;
        this.refundTotal = refundTotal;
    }

    public int getCancelledTicketCount() {
        return cancelledTicketCount;
    }

    public BigDecimal getRefundTotal() {
        return refundTotal;
    }
}

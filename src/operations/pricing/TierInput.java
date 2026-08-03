package operations.pricing;

import java.math.BigDecimal;

public final class TierInput {
    private final String tierCode;
    private final BigDecimal price;

    public TierInput(String tierCode, BigDecimal price) {
        this.tierCode = tierCode;
        this.price = price;
    }

    public String getTierCode() {
        return tierCode;
    }

    public BigDecimal getPrice() {
        return price;
    }
}

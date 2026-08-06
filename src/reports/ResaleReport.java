package reports;

import java.math.BigDecimal;

public final class ResaleReport {

    private final int eventId;
    private final String eventTitle;
    private final int resaleCount;
    private final BigDecimal avgMarkupPct;
    private final BigDecimal pctAtCap;


    public ResaleReport(
            int eventId,
            String eventTitle,
            int resaleCount,
            BigDecimal avgMarkupPct,
            BigDecimal pctAtCap
    ) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.resaleCount = resaleCount;
        this.avgMarkupPct = avgMarkupPct;
        this.pctAtCap = pctAtCap;
    }


    public int getEventId() {
        return eventId;
    }


    public String getEventTitle() {
        return eventTitle;
    }


    public int getResaleCount() {
        return resaleCount;
    }


    public BigDecimal getAvgMarkupPct() {
        return avgMarkupPct;
    }


    public BigDecimal getPctAtCap() {
        return pctAtCap;
    }
}
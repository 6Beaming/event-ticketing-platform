package operations.event;

public final class ArtistBillingInput {
    private final int artistId;
    private final int billingRank;

    public ArtistBillingInput(int artistId, int billingRank) {
        this.artistId = artistId;
        this.billingRank = billingRank;
    }

    public int getArtistId() {
        return artistId;
    }

    public int getBillingRank() {
        return billingRank;
    }
}

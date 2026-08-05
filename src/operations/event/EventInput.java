package operations.event;

import java.math.BigDecimal;
import java.util.List;

public final class EventInput {
    private final int organizerId;
    private final String title;
    private final String description;
    private final BigDecimal resaleCapMultiplier;
    private final int genreId;
    private final List<ArtistBillingInput> artists;

    public EventInput(
            int organizerId,
            String title,
            String description,
            BigDecimal resaleCapMultiplier,
            int genreId,
            List<ArtistBillingInput> artists
    ) {
        this.organizerId = organizerId;
        this.title = title;
        this.description = description;
        this.resaleCapMultiplier = resaleCapMultiplier;
        this.genreId = genreId;
        this.artists = artists == null ? List.of() : List.copyOf(artists);
    }

    public int getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getResaleCapMultiplier() {
        return resaleCapMultiplier;
    }

    public int getGenreId() {
        return genreId;
    }

    public List<ArtistBillingInput> getArtists() {
        return artists;
    }
}

package queries;

public final class LocationSearchInput {
    public enum Type {
        COORDINATES,
        POSTAL_CODE,
        ADDRESS
    }

    private final Type type;
    private final Double latitude;
    private final Double longitude;
    private final Double radiusKm;
    private final String sortBy;
    private final String postalCode;
    private final String address;

    private LocationSearchInput(
            Type type,
            Double latitude,
            Double longitude,
            Double radiusKm,
            String sortBy,
            String postalCode,
            String address
    ) {
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
        this.sortBy = sortBy;
        this.postalCode = postalCode;
        this.address = address;
    }

    public static LocationSearchInput coordinates(
            double latitude,
            double longitude,
            double radiusKm,
            String sortBy
    ) {
        return new LocationSearchInput(
                Type.COORDINATES,
                latitude,
                longitude,
                radiusKm,
                sortBy,
                null,
                null
        );
    }

    public static LocationSearchInput postalCode(String postalCode) {
        return new LocationSearchInput(
                Type.POSTAL_CODE,
                null,
                null,
                null,
                null,
                postalCode,
                null
        );
    }

    public static LocationSearchInput address(String address) {
        return new LocationSearchInput(
                Type.ADDRESS,
                null,
                null,
                null,
                null,
                null,
                address
        );
    }

    public Type getType() {
        return type;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getRadiusKm() {
        return radiusKm;
    }

    public String getSortBy() {
        return sortBy;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getAddress() {
        return address;
    }
}

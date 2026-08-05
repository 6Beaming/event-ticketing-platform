package operations.profile;

import java.time.LocalDate;

public final class ProfileInput {
    private final String name;
    private final String address;
    private final String email;
    private final LocalDate dateOfBirth;

    public ProfileInput(String name, String address, String email, LocalDate dateOfBirth) {
        this.name = name;
        this.address = address;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }
}

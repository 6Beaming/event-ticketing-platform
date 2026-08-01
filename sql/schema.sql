-- ============================================================================
-- Creates all tables, keys, and constraints.
-- Foreign key checks are disabled during creation so mutually dependent tables,
-- such as Transactions → ResaleListing → Tickets → Transactions,
-- can declare their relationships inline.
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0; 

-- ============================================================================
-- CLUSTER 1: Users
-- ============================================================================

CREATE TABLE Users (
    user_id         INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    date_of_birth   DATE NOT NULL,
    UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Customer (
    user_id         INT PRIMARY KEY,
    FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Organizer (
    user_id         INT PRIMARY KEY,
    FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE PaymentInfo (
    payment_info_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id     INT NOT NULL,
    card_number     VARCHAR(25) NOT NULL,
    card_holder_name VARCHAR(150) NOT NULL,
    expiry_date     DATE NOT NULL,
    billing_zip     VARCHAR(20) NOT NULL,
    UNIQUE (payment_info_id, customer_id),
    FOREIGN KEY (customer_id) REFERENCES Customer(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- CLUSTER 2: Event
-- ============================================================================

CREATE TABLE Segment (
    segment_id      INT AUTO_INCREMENT PRIMARY KEY,
    segment_name    VARCHAR(100) NOT NULL,
    UNIQUE (segment_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Genre (
    genre_id        INT AUTO_INCREMENT PRIMARY KEY,
    genre_name      VARCHAR(100) NOT NULL,
    segment_id      INT NOT NULL,
    FOREIGN KEY (segment_id) REFERENCES Segment(segment_id),
    UNIQUE (genre_name, segment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Event (
    event_id        INT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    resale_cap_pct  DECIMAL(4,2) NOT NULL DEFAULT 1.20,
    organizer_id    INT NOT NULL,
    genre_id        INT NOT NULL,
    FOREIGN KEY (organizer_id) REFERENCES Organizer(user_id),
    FOREIGN KEY (genre_id) REFERENCES Genre(genre_id),
    CHECK (resale_cap_pct >= 1.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ArtistsTeams (
    artist_id       INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    type            VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE BillingOrder (
    event_id        INT NOT NULL,
    artist_id       INT NOT NULL,
    billing_rank    INT NOT NULL,
    PRIMARY KEY (event_id, artist_id),
    FOREIGN KEY (event_id) REFERENCES Event(event_id) ON DELETE CASCADE,
    FOREIGN KEY (artist_id) REFERENCES ArtistsTeams(artist_id),
    CHECK (billing_rank > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- CLUSTER 3: Venue & seating
-- ============================================================================

CREATE TABLE Venue (
    venue_id        INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    latitude        DECIMAL(9,6) NOT NULL,
    longitude       DECIMAL(9,6) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    postal_code     VARCHAR(20) NOT NULL,
    country         VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Section (
    venue_id        INT NOT NULL,
    section_name    VARCHAR(100) NOT NULL,
    section_type    ENUM('reserved','general') NOT NULL,
    PRIMARY KEY (venue_id, section_name),
    FOREIGN KEY (venue_id) REFERENCES Venue(venue_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- section_type disjointness (a section is reserved XOR general) is enforced by
-- the trg_reservedsection_disjoint / trg_generalseating_disjoint triggers below.

CREATE TABLE ReservedSection (
    venue_id        INT NOT NULL,
    section_name    VARCHAR(100) NOT NULL,
    PRIMARY KEY (venue_id, section_name),
    FOREIGN KEY (venue_id, section_name) REFERENCES Section(venue_id, section_name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE GeneralSeating (
    venue_id            INT NOT NULL,
    section_name        VARCHAR(100) NOT NULL,
    standing_capacity   INT NOT NULL,
    PRIMARY KEY (venue_id, section_name),
    FOREIGN KEY (venue_id, section_name) REFERENCES Section(venue_id, section_name) ON DELETE CASCADE,
    CHECK (standing_capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE SeatRows (
    venue_id        INT NOT NULL,
    section_name    VARCHAR(100) NOT NULL,
    row_name        VARCHAR(20) NOT NULL,
    PRIMARY KEY (venue_id, section_name, row_name),
    FOREIGN KEY (venue_id, section_name) REFERENCES ReservedSection(venue_id, section_name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Seats (
    venue_id        INT NOT NULL,
    section_name    VARCHAR(100) NOT NULL,
    row_name        VARCHAR(20) NOT NULL,
    seat_number     INT NOT NULL,
    PRIMARY KEY (venue_id, section_name, row_name, seat_number),
    FOREIGN KEY (venue_id, section_name, row_name) REFERENCES SeatRows(venue_id, section_name, row_name) ON DELETE CASCADE,
    CHECK (seat_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- CLUSTER 4: Performance & pricing
-- ============================================================================

CREATE TABLE Performance (
    performance_id      INT AUTO_INCREMENT PRIMARY KEY,
    event_id            INT NOT NULL,
    venue_id            INT NOT NULL,
    date_time           DATETIME NOT NULL,
    status              ENUM('scheduled','cancelled','completed') NOT NULL DEFAULT 'scheduled',
    cancellation_date   DATETIME NULL,
    cancellation_reason VARCHAR(255) NULL,
    FOREIGN KEY (event_id) REFERENCES Event(event_id),
    FOREIGN KEY (venue_id) REFERENCES Venue(venue_id),
    UNIQUE (performance_id, venue_id),
    CHECK (
        (status = 'cancelled' AND cancellation_date IS NOT NULL)
        OR (status <> 'cancelled' AND cancellation_date IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE PriceTier (
    performance_id  INT NOT NULL,
    tier_code       VARCHAR(20) NOT NULL,
    price           DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (performance_id, tier_code),
    FOREIGN KEY (performance_id) REFERENCES Performance(performance_id) ON DELETE CASCADE,
    CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE SectionTierAssignment (
    performance_id  INT NOT NULL,
    venue_id        INT NOT NULL,
    section_name    VARCHAR(100) NOT NULL,
    tier_code       VARCHAR(20) NOT NULL,
    PRIMARY KEY (performance_id, venue_id, section_name),
    FOREIGN KEY (performance_id, venue_id) REFERENCES Performance(performance_id, venue_id),
    FOREIGN KEY (venue_id, section_name) REFERENCES Section(venue_id, section_name),
    FOREIGN KEY (performance_id, tier_code) REFERENCES PriceTier(performance_id, tier_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE GeneralAdmissionCapacity (
    ga_capacity_id      INT AUTO_INCREMENT UNIQUE,
    performance_id      INT NOT NULL,
    venue_id            INT NOT NULL,
    section_name        VARCHAR(100) NOT NULL,
    remaining_capacity  INT NOT NULL,
    PRIMARY KEY (performance_id, venue_id, section_name),
    UNIQUE (ga_capacity_id, performance_id),
    FOREIGN KEY (performance_id, venue_id) REFERENCES Performance(performance_id, venue_id),
    FOREIGN KEY (venue_id, section_name) REFERENCES GeneralSeating(venue_id, section_name),
    CHECK (remaining_capacity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- ga_capacity_id: surrogate key added purely so Tickets can hold a single-column
-- FK (general_seats_ref) instead of a 3-column composite; the natural composite
-- PK above remains the real identifier and is what SectionTierAssignment-style
-- joins use.

CREATE TABLE PerformanceSeats (
    performance_seat_id INT AUTO_INCREMENT UNIQUE,
    performance_id      INT NOT NULL,
    venue_id             INT NOT NULL,
    section_name         VARCHAR(100) NOT NULL,
    row_name              VARCHAR(20) NOT NULL,
    seat_number           INT NOT NULL,
    blocked_status         BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (performance_id, venue_id, section_name, row_name, seat_number),
    UNIQUE (performance_seat_id, performance_id),
    FOREIGN KEY (performance_id, venue_id) REFERENCES Performance(performance_id, venue_id),
    FOREIGN KEY (venue_id, section_name, row_name, seat_number)
        REFERENCES Seats(venue_id, section_name, row_name, seat_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- performance_seat_id: same rationale as ga_capacity_id above.

-- ============================================================================
-- CLUSTER 5: Ticketing
-- ============================================================================

CREATE TABLE Transactions (
    transaction_id   INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    payment_info_id  INT NOT NULL,
    transaction_type ENUM('purchase','resale') NOT NULL,
    performance_id   INT NULL,
    listing_id       INT NULL,
    transaction_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (transaction_id, customer_id),
    UNIQUE (transaction_id, performance_id),
    UNIQUE (listing_id),
    FOREIGN KEY (customer_id) REFERENCES Customer(user_id),
    FOREIGN KEY (payment_info_id, customer_id)
        REFERENCES PaymentInfo(payment_info_id, customer_id),
    FOREIGN KEY (performance_id) REFERENCES Performance(performance_id),
    FOREIGN KEY (listing_id) REFERENCES ResaleListing(listing_id),
    CHECK (
        (transaction_type = 'purchase' AND performance_id IS NOT NULL AND listing_id IS NULL)
        OR (transaction_type = 'resale' AND performance_id IS NULL AND listing_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Tickets (
    ticket_id             INT AUTO_INCREMENT PRIMARY KEY,
    purchase_id           INT NOT NULL,
    performance_id        INT NOT NULL,
    performance_seats_ref INT NULL,
    general_seats_ref     INT NULL,
    face_value            DECIMAL(10,2) NOT NULL,
    status                ENUM('active','cancelled') NOT NULL DEFAULT 'active',
    cancellation_date     DATETIME NULL,
    cancelled_by          ENUM('customer','organizer') NULL,
    cancellation_reason   VARCHAR(255) NULL,
    FOREIGN KEY (purchase_id, performance_id)
        REFERENCES Transactions(transaction_id, performance_id),
    FOREIGN KEY (performance_seats_ref, performance_id)
        REFERENCES PerformanceSeats(performance_seat_id, performance_id),
    FOREIGN KEY (general_seats_ref, performance_id)
        REFERENCES GeneralAdmissionCapacity(ga_capacity_id, performance_id),
    CHECK (
        (performance_seats_ref IS NOT NULL AND general_seats_ref IS NULL)
        OR (performance_seats_ref IS NULL AND general_seats_ref IS NOT NULL)
    ),
    CHECK (
        (status = 'cancelled' AND cancellation_date IS NOT NULL AND cancelled_by IS NOT NULL)
        OR (status <> 'cancelled' AND cancellation_date IS NULL AND cancelled_by IS NULL)
    ),
    CHECK (face_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ResaleListing (
    listing_id       INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id        INT NOT NULL,
    listing_price    DECIMAL(10,2) NOT NULL,
    status           ENUM('active','sold','withdrawn') NOT NULL DEFAULT 'active',
    listed_date      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_ticket_id INT GENERATED ALWAYS AS (
        CASE WHEN status = 'active' THEN ticket_id ELSE NULL END
    ) STORED,
    UNIQUE (active_ticket_id),
    FOREIGN KEY (ticket_id) REFERENCES Tickets(ticket_id),
    CHECK (listing_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Each ticket keeps one stable identity. Ownership transfers close the current
-- history row and append a new row for the acquiring customer's transaction.

CREATE TABLE TicketOwnership (
    ownership_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id               INT NOT NULL,
    customer_id             INT NOT NULL,
    acquired_transaction_id INT NOT NULL,
    acquired_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at                DATETIME NULL,
    current_ticket_id       INT GENERATED ALWAYS AS (
        CASE WHEN ended_at IS NULL THEN ticket_id ELSE NULL END
    ) STORED,
    UNIQUE (ticket_id, acquired_transaction_id),
    UNIQUE (current_ticket_id),
    FOREIGN KEY (ticket_id) REFERENCES Tickets(ticket_id),
    FOREIGN KEY (acquired_transaction_id, customer_id)
        REFERENCES Transactions(transaction_id, customer_id),
    CHECK (ended_at IS NULL OR ended_at >= acquired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Reviews (
    customer_id     INT NOT NULL,
    performance_id  INT NOT NULL,
    comment_text    TEXT,
    event_rating    TINYINT NOT NULL,
    venue_rating    TINYINT NOT NULL,
    review_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, performance_id),
    FOREIGN KEY (customer_id) REFERENCES Customer(user_id),
    FOREIGN KEY (performance_id) REFERENCES Performance(performance_id),
    CHECK (event_rating BETWEEN 1 AND 5),
    CHECK (venue_rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- TRIGGERS (constraints that can't express as single-row CHECKs)
-- cross-row uniqueness (seat double-sell), cross-table disjointness (Section subtype),
-- and database-level protection against conflicting inventory records.
-- ============================================================================

DELIMITER $$

-- ---------------------------------------------------------------------------
-- Section subtype disjointness: a (venue_id, section_name) may have a row in
-- ReservedSection OR GeneralSeating, never both, and must match Section.section_type.
-- ---------------------------------------------------------------------------

CREATE TRIGGER trg_reservedsection_disjoint
BEFORE INSERT ON ReservedSection
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(20);
    DECLARE v_conflict INT;
    SELECT section_type INTO v_type FROM Section
        WHERE venue_id = NEW.venue_id AND section_name = NEW.section_name;
    IF v_type IS NULL OR v_type <> 'reserved' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Section.section_type must be reserved for a ReservedSection row';
    END IF;
    SELECT COUNT(*) INTO v_conflict FROM GeneralSeating
        WHERE venue_id = NEW.venue_id AND section_name = NEW.section_name;
    IF v_conflict > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Section already has a GeneralSeating row: cannot also be reserved';
    END IF;
END$$

CREATE TRIGGER trg_generalseating_disjoint
BEFORE INSERT ON GeneralSeating
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(20);
    DECLARE v_conflict INT;
    SELECT section_type INTO v_type FROM Section
        WHERE venue_id = NEW.venue_id AND section_name = NEW.section_name;
    IF v_type IS NULL OR v_type <> 'general' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Section.section_type must be general for a GeneralSeating row';
    END IF;
    SELECT COUNT(*) INTO v_conflict FROM ReservedSection
        WHERE venue_id = NEW.venue_id AND section_name = NEW.section_name;
    IF v_conflict > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Section already has a ReservedSection row: cannot also be general';
    END IF;
END$$

-- ---------------------------------------------------------------------------
-- Seat double-sell prevention: at most one non-cancelled ticket per
-- performance_seats_ref at any time. Replaces a partial/filtered unique index.
-- ---------------------------------------------------------------------------

CREATE TRIGGER trg_tickets_no_double_sell_ins
BEFORE INSERT ON Tickets
FOR EACH ROW
BEGIN
    DECLARE v_conflict INT;
    IF NEW.performance_seats_ref IS NOT NULL AND NEW.status <> 'cancelled' THEN
        SELECT COUNT(*) INTO v_conflict FROM Tickets
            WHERE performance_seats_ref = NEW.performance_seats_ref
              AND status <> 'cancelled';
        IF v_conflict > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Seat already sold for this performance';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_tickets_no_double_sell_upd
BEFORE UPDATE ON Tickets
FOR EACH ROW
BEGIN
    DECLARE v_conflict INT;
    IF NEW.performance_seats_ref IS NOT NULL AND NEW.status <> 'cancelled' THEN
        SELECT COUNT(*) INTO v_conflict FROM Tickets
            WHERE performance_seats_ref = NEW.performance_seats_ref
              AND status <> 'cancelled'
              AND ticket_id <> OLD.ticket_id;
        IF v_conflict > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Seat already sold for this performance';
        END IF;
    END IF;
END$$

DELIMITER ;

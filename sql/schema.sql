-- ============================================================================
-- MyTix — schema.sql
-- Single-execution DDL for MySQL 8. Creates all tables, keys, and constraints.
-- Table order follows FK dependency order (see Part 4 of relational_schema_
-- normalized.md), except ResaleSale <-> ResaleListing, which are mutually
-- dependent: ResaleSale.listing_id's FK is added via ALTER TABLE at the end,
-- after ResaleListing exists.
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
    transaction_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES Customer(user_id),
    FOREIGN KEY (payment_info_id) REFERENCES PaymentInfo(payment_info_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Purchase (
    transaction_id   INT PRIMARY KEY,
    performance_id   INT NOT NULL,
    FOREIGN KEY (transaction_id) REFERENCES Transactions(transaction_id) ON DELETE CASCADE,
    FOREIGN KEY (performance_id) REFERENCES Performance(performance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ResaleSale (
    transaction_id   INT PRIMARY KEY,
    listing_id       INT NOT NULL,   -- FK added via ALTER TABLE after ResaleListing exists
    FOREIGN KEY (transaction_id) REFERENCES Transactions(transaction_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Tickets (
    ticket_id             INT AUTO_INCREMENT PRIMARY KEY,
    purchase_id           INT NULL,
    resale_sale_id        INT NULL,
    performance_seats_ref INT NULL,
    general_seats_ref     INT NULL,
    face_value            DECIMAL(10,2) NOT NULL,
    resold_count          INT NOT NULL DEFAULT 0,
    status                ENUM('active','resold','cancelled') NOT NULL DEFAULT 'active',
    cancellation_date     DATETIME NULL,
    cancelled_by          ENUM('customer','organizer') NULL,
    cancellation_reason   VARCHAR(255) NULL,
    FOREIGN KEY (purchase_id) REFERENCES Purchase(transaction_id),
    FOREIGN KEY (resale_sale_id) REFERENCES ResaleSale(transaction_id),
    FOREIGN KEY (performance_seats_ref) REFERENCES PerformanceSeats(performance_seat_id),
    FOREIGN KEY (general_seats_ref) REFERENCES GeneralAdmissionCapacity(ga_capacity_id),
    CHECK (
        (purchase_id IS NOT NULL AND resale_sale_id IS NULL)
        OR (purchase_id IS NULL AND resale_sale_id IS NOT NULL)
    ),
    CHECK (
        (performance_seats_ref IS NOT NULL AND general_seats_ref IS NULL)
        OR (performance_seats_ref IS NULL AND general_seats_ref IS NOT NULL)
    ),
    CHECK (
        (status = 'cancelled' AND cancellation_date IS NOT NULL AND cancelled_by IS NOT NULL)
        OR (status <> 'cancelled' AND cancellation_date IS NULL AND cancelled_by IS NULL)
    ),
    CHECK (resold_count >= 0),
    CHECK (face_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ResaleListing (
    listing_id      INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       INT NOT NULL,
    listing_price   DECIMAL(10,2) NOT NULL,
    status          ENUM('active','sold','withdrawn') NOT NULL DEFAULT 'active',
    listed_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES Tickets(ticket_id),
    CHECK (listing_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Close the ResaleSale <-> ResaleListing cycle now that both tables exist.
ALTER TABLE ResaleSale
    ADD FOREIGN KEY (listing_id) REFERENCES ResaleListing(listing_id);

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
-- TRIGGERS
-- Enforce the constraints MySQL 8 can't express as single-row CHECKs: cross-row
-- uniqueness (seat double-sell), cross-table disjointness (Section subtype),
-- and cross-table comparisons (resale cap, GA capacity bookkeeping).
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
-- performance_seats_ref at any time. Replaces a partial/filtered unique index,
-- which MySQL 8 does not support natively.
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

-- ---------------------------------------------------------------------------
-- GA capacity bookkeeping: keep GeneralAdmissionCapacity.remaining_capacity in
-- sync with active Tickets automatically, rather than leaving it as an
-- independently-maintained counter that can drift (the open item flagged in
-- Part 6/7 of the design doc).
-- ---------------------------------------------------------------------------

CREATE TRIGGER trg_tickets_ga_capacity_ins
BEFORE INSERT ON Tickets
FOR EACH ROW
BEGIN
    DECLARE v_remaining INT;
    IF NEW.general_seats_ref IS NOT NULL AND NEW.status <> 'cancelled' THEN
        SELECT remaining_capacity INTO v_remaining FROM GeneralAdmissionCapacity
            WHERE ga_capacity_id = NEW.general_seats_ref FOR UPDATE;
        IF v_remaining <= 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No remaining GA capacity for this section';
        END IF;
        UPDATE GeneralAdmissionCapacity SET remaining_capacity = remaining_capacity - 1
            WHERE ga_capacity_id = NEW.general_seats_ref;
    END IF;
END$$

CREATE TRIGGER trg_tickets_ga_capacity_upd
AFTER UPDATE ON Tickets
FOR EACH ROW
BEGIN
    IF NEW.general_seats_ref IS NOT NULL AND OLD.status <> 'cancelled' AND NEW.status = 'cancelled' THEN
        UPDATE GeneralAdmissionCapacity SET remaining_capacity = remaining_capacity + 1
            WHERE ga_capacity_id = NEW.general_seats_ref;
    ELSEIF NEW.general_seats_ref IS NOT NULL AND OLD.status = 'cancelled' AND NEW.status <> 'cancelled' THEN
        UPDATE GeneralAdmissionCapacity SET remaining_capacity = remaining_capacity - 1
            WHERE ga_capacity_id = NEW.general_seats_ref;
    END IF;
END$$

-- ---------------------------------------------------------------------------
-- Resale cap validation: listing_price must not exceed the resold ticket's
-- face_value * the parent Event's resale_cap_pct. Traced through whichever
-- seat path (reserved or GA) the ticket used, up to Performance -> Event.
-- ---------------------------------------------------------------------------

CREATE TRIGGER trg_resalelisting_cap_ins
BEFORE INSERT ON ResaleListing
FOR EACH ROW
BEGIN
    DECLARE v_face DECIMAL(10,2);
    DECLARE v_cap DECIMAL(4,2);
    DECLARE v_perf INT;

    SELECT face_value,
           COALESCE(
               (SELECT performance_id FROM PerformanceSeats WHERE performance_seat_id = t.performance_seats_ref),
               (SELECT performance_id FROM GeneralAdmissionCapacity WHERE ga_capacity_id = t.general_seats_ref)
           )
      INTO v_face, v_perf
      FROM Tickets t WHERE t.ticket_id = NEW.ticket_id;

    SELECT e.resale_cap_pct INTO v_cap
      FROM Performance p JOIN Event e ON e.event_id = p.event_id
      WHERE p.performance_id = v_perf;

    IF NEW.listing_price > v_face * v_cap THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Listing price exceeds the event resale cap';
    END IF;
END$$

DELIMITER ;

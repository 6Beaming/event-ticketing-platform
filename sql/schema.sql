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
    UNIQUE (event_id, billing_rank),
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
    venue_id          INT NOT NULL,
    section_name      VARCHAR(100) NOT NULL,
    section_type      ENUM('reserved','general') NOT NULL,
    standing_capacity INT NULL,
    PRIMARY KEY (venue_id, section_name),
    UNIQUE (venue_id, section_name, section_type),
    UNIQUE (venue_id, section_name, section_type, standing_capacity),
    FOREIGN KEY (venue_id) REFERENCES Venue(venue_id) ON DELETE CASCADE,
    CHECK (
        (section_type = 'reserved' AND standing_capacity IS NULL)
        OR (section_type = 'general' AND standing_capacity > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE SeatRows (
    venue_id        INT NOT NULL,
    section_name    VARCHAR(100) NOT NULL,
    section_type    ENUM('reserved','general') NOT NULL DEFAULT 'reserved',
    row_name        VARCHAR(20) NOT NULL,
    PRIMARY KEY (venue_id, section_name, row_name),
    FOREIGN KEY (venue_id, section_name, section_type)
        REFERENCES Section(venue_id, section_name, section_type) ON DELETE CASCADE,
    CHECK (section_type = 'reserved')
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
    section_type        ENUM('reserved','general') NOT NULL DEFAULT 'general',
    total_capacity      INT NOT NULL,
    remaining_capacity  INT NOT NULL,
    PRIMARY KEY (performance_id, venue_id, section_name),
    UNIQUE (ga_capacity_id, performance_id),
    FOREIGN KEY (performance_id, venue_id) REFERENCES Performance(performance_id, venue_id),
    FOREIGN KEY (venue_id, section_name, section_type, total_capacity)
        REFERENCES Section(venue_id, section_name, section_type, standing_capacity),
    CHECK (section_type = 'general'),
    CHECK (remaining_capacity BETWEEN 0 AND total_capacity)
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
    active_reserved_seat_ref INT GENERATED ALWAYS AS (
        CASE WHEN status = 'active' THEN performance_seats_ref ELSE NULL END
    ) STORED,
    UNIQUE (active_reserved_seat_ref),
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
    UNIQUE (ownership_id, ticket_id),
    UNIQUE (current_ticket_id),
    FOREIGN KEY (ticket_id) REFERENCES Tickets(ticket_id),
    FOREIGN KEY (acquired_transaction_id, customer_id)
        REFERENCES Transactions(transaction_id, customer_id),
    CHECK (ended_at IS NULL OR ended_at >= acquired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE TicketCancellation (
    cancellation_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id            INT NOT NULL,
    ownership_id         BIGINT NOT NULL,
    cancelled_by_user_id INT NOT NULL,
    cancellation_date    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason               VARCHAR(255) NULL,
    UNIQUE (ticket_id),
    UNIQUE (ownership_id),
    FOREIGN KEY (ownership_id, ticket_id)
        REFERENCES TicketOwnership(ownership_id, ticket_id),
    FOREIGN KEY (cancelled_by_user_id) REFERENCES Users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Refund (
    refund_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    cancellation_id BIGINT NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    refund_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (cancellation_id),
    FOREIGN KEY (cancellation_id)
        REFERENCES TicketCancellation(cancellation_id),
    CHECK (amount >= 0)
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

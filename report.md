# MyTix Project Report

## Group members

| Member | Student number | Main contributions |
|---|---:|---|
| Eric Liu | 1011195939 | Core schema and transaction design; profile, booking, cancellation, resale, review, inventory, Q4-Q5, R4-R8, sample-data integration, validation, and documentation |
| Nihar Samant | 1011185566 | Initial schema and MySQL connection; Q1-Q7, R1-R9, OpenNLP noun-phrase analysis, organizer toolkit, and organizer/search/report terminal integration |

Both members contributed to integration, review, sample-data verification, and the final terminal application. The table identifies primary areas of work rather than exclusive ownership.

## 1. Purpose

MyTix is a Java and MySQL 8 ticketing system for customers and event organizers. It stores users, events, artists or teams, venues, performances, performance-specific prices, reserved seats, general-admission capacity, purchases, ownership transfers, cancellations, refunds, resale listings, restrictions, and reviews.

The terminal application supports the operations required by the project specification. Customers can create profiles, book and cancel eligible tickets, resell tickets within an organizer-defined cap, view histories, and review attended performances. Organizers can create events and performances, set price tiers, manage reserved-seat availability, inspect sales, use analytical reports, and request pricing recommendations. Q1-Q7 and R1-R9 are executed from Java through SQL, except that R9 uses OpenNLP after SQL retrieves the review comments.

## 2. Conceptual design problems and decisions

### 2.1 One user identity with two roles

Customers and organizers share a name, address, email, date of birth, and account status. Storing these fields independently would duplicate the identity rules and could allow one identifier to be both roles accidentally.

`Users` is therefore the supertype, while `Customer` and `Organizer` are disjoint subtypes. The subtype tables use the same `user_id` as their primary and foreign key. A typed composite foreign key and a role check make the intended subtype explicit. Deactivation anonymizes the profile instead of deleting sales and ownership history that reports still need.

### 2.2 Event identity versus a scheduled performance

An event describes the title, genre, organizer, artists or teams, billing order, and resale cap. A performance places that event at one venue and one date and time. Separating `Event` from `Performance` allows a tour or recurring event to have many performances without repeating descriptive data. `BillingOrder` resolves the many-to-many relationship between events and artists or teams and records a unique positive rank within each event.

### 2.3 Reusable venues and two inventory models

A venue owns reusable sections, reserved rows and seats, or a standing capacity. A performance then receives its own sellable inventory.

- Reserved seating is materialized in `PerformanceSeats`. Each physical seat has an independent blocked state and can be sold at most once while its ticket is active.
- General admission is represented by `GeneralAdmissionCapacity`. It stores the total and remaining quantity for one performance section, so concurrent purchases can lock and decrement one counter.

The split avoids inventing seat numbers for standing areas and supports mixed venues.

### 2.4 Performance-specific prices

Organizers can divide one performance into two or more tiers. `PriceTier` identifies a tier only within its performance, and `SectionTierAssignment` assigns every venue section to one of those tiers. A ticket copies the tier and face value at sale time. This preserves what the customer actually paid even if a future price update changes the tier price.

A full tier-map replacement is allowed only for a future scheduled performance with no ticket history. Once tickets exist, organizers may update a tier price but cannot reinterpret already sold inventory.

### 2.5 Orders, tickets, and payment history

One `Transactions` row represents an original purchase or a resale purchase. It may pay for several original tickets, so `Tickets` is separate. The transaction stores a payment snapshot as well as the selected payment record. This ensures that historical receipts do not change if the customer's current payment details later change.

Each ticket has exactly one inventory reference: either one performance-seat reference or one general-admission reference. Database checks and foreign keys enforce this exclusive choice.

### 2.6 Stable tickets and ownership transfer

Resale does not create a new ticket. `TicketOwnership` closes the seller's current ownership row and appends a new row for the buyer. This produces a complete chain while `current_ticket_id` guarantees at most one current owner. `ResaleListing` records the seller's ownership, the price, the cap at listing time, status, and date. A generated unique `active_ticket_id` permits historical listings but only one active listing per ticket.

### 2.7 Cancellation and refunds

Ticket state, the cancellation event, and the refund are separate facts. `Tickets.status` answers the frequent active/cancelled question. `TicketCancellation` records who cancelled which current ownership and why. `Refund` records the money returned. Unique constraints make cancellation and refund one-to-one where required.

Customer cancellation is permitted only more than seven days before the performance. Organizer cancellation affects every active ticket for the performance and records the organizer associated with the event. All affected rows change in one transaction.

### 2.8 Scalper detection and restrictions

R4 considers a customer a possible scalper when, in the preceding 12 months and in the same city, the customer bought at least 10 tickets and listed more than half of those purchased tickets for resale. The report evaluates each customer/city group with SQL and counts distinct ownership acquisitions for purchases and distinct ticket IDs for listings, so joins do not inflate the totals.

The report synchronizes `CustomerRestriction`: current qualifying customers receive a `possible_scalper` restriction, and a prior restriction of that type is ended when the customer no longer qualifies. A restricted customer cannot make a new booking, list a ticket, or buy a resale ticket. The restriction does not erase history or prevent a legitimate cancellation/refund.

### 2.9 Reviews and text analysis

`Reviews` uses `(customer_id, performance_id)` as its key, which permits at most one review of an attended performance per customer. Java checks attendance, completion, and the configured recent-attendance window before inserting. Ratings are constrained to 1-5.

For R9, SQL selects and groups the relevant event comments. OpenNLP performs the only permitted non-SQL step: noun-phrase extraction. The application then counts and ranks phrases per event.

### 2.10 Transaction and concurrency design

Booking, cancellation, resale listing, listing withdrawal, resale purchase, pricing replacement, and seat blocking are multi-row operations. They execute inside JDBC transactions and roll back on any failure.

- Reserved booking locks the selected performance-seat rows before checking availability and inserting the order, tickets, and ownership rows.
- General-admission booking locks the capacity row and performs an atomic guarded decrement.
- Ticket cancellation locks the tickets and their current ownership rows before closing ownership, cancelling tickets, ending active listings, inserting cancellation rows, and inserting refunds.
- Resale purchase locks the active listing, ticket, and seller ownership; it then closes the seller's ownership and creates the buyer transaction and ownership row.
- Unique generated-key constraints provide a final database guard against two active claims on one seat, ticket, listing, ownership, or restriction.

InnoDB foreign keys, row locks, guarded updates, commits, and rollbacks are used together; correctness does not depend only on terminal validation.

## 3. Assumptions

The project PDF defines the required business rules. The following implementation choices fill gaps without replacing those rules. The maintained list is in `docs/assumptions.md`.

- Email addresses are unique across all active and deactivated users.
- “Recently attended” means the preceding 365 days.
- Upcoming searches include only scheduled performances later than the current UTC time.
- Search and report date ranges are inclusive calendar dates and are implemented internally as a half-open UTC interval.
- Q1 uses a default radius of 50 km and the Haversine formula with Earth radius 6,371 km.
- Q2 compares the first three normalized postal-code characters.
- Q3 may return several venues when their normalized address is the same.
- Q5 applies price and availability to the requested reserved/general section type; performances without matching inventory are omitted.
- Toolkit comparables use completed performances in the same genre, prefer the same city and a nearby venue capacity, and use the documented fallback when evidence is insufficient.

All remaining report- and toolkit-specific interpretations, including empty-result behavior, ranking tie handling, and the fallback tier mix, are listed in `docs/assumptions.md` so they remain separate from facts stated in the PDF.

## 4. ER diagram

The ER diagram is intentionally not included in this document draft because it is the teammate-owned deliverable. It must be inserted into the exported `report.pdf` before submission and must agree with the relations and keys below.

## 5. Relation schemas and keys

Notation: `PK` is the primary key, `AK` is an alternate or conditionally unique key, and `FK` is a foreign key. Generated conditional keys are described because they enforce important temporal rules.

| Relation | Attributes (primary key first) | Alternate/conditional keys and foreign keys |
|---|---|---|
| `Users` | `user_id`, name, address, email, date_of_birth, user_role, account_status, deleted_at | AK email; AK (user_id, user_role) |
| `Customer` | `user_id`, user_role | FK (user_id, user_role) -> Users; role fixed to customer |
| `Organizer` | `user_id`, user_role | FK (user_id, user_role) -> Users; role fixed to organizer |
| `PaymentInfo` | `payment_info_id`, customer_id, card_number, card_holder_name, expiry_date, billing_zip | FK customer_id -> Customer; typed AK (payment_info_id, customer_id) |
| `CustomerRestriction` | `restriction_id`, customer_id, restriction_reason, details, started_at, ended_at, current_customer_id | FK customer_id -> Customer; conditional AK current_customer_id |
| `Segment` | `segment_id`, segment_name | AK segment_name |
| `Genre` | `genre_id`, genre_name, segment_id | AK (genre_name, segment_id); FK segment_id -> Segment |
| `Event` | `event_id`, title, description, resale_cap_pct, organizer_id, genre_id | AK (event_id, organizer_id); FKs organizer_id -> Organizer, genre_id -> Genre |
| `ArtistsTeams` | `artist_id`, name, type | No alternate key required |
| `BillingOrder` | `(event_id, artist_id)`, billing_rank | AK (event_id, billing_rank); FKs to Event and ArtistsTeams |
| `Venue` | `venue_id`, name, latitude, longitude, address, city, postal_code, country | Search indexes on coordinates, postal code, city, and address |
| `Section` | `(venue_id, section_name)`, section_type, standing_capacity | Typed AKs; FK venue_id -> Venue |
| `SeatRows` | `(venue_id, section_name, row_name)`, section_type | Typed FK to a reserved Section |
| `Seats` | `(venue_id, section_name, row_name, seat_number)` | FK to SeatRows |
| `Performance` | `performance_id`, event_id, venue_id, date_time, status, cancellation_date, cancellation_reason, cancelled_by_organizer_id | AK (performance_id, venue_id); FKs to Event and Venue; typed cancellation FK to Event organizer |
| `PriceTier` | `(performance_id, tier_code)`, price | FK performance_id -> Performance |
| `SectionTierAssignment` | `(performance_id, venue_id, section_name)`, tier_code | FKs to Performance, Section, and PriceTier |
| `GeneralAdmissionCapacity` | `(performance_id, venue_id, section_name)`, ga_capacity_id, section_type, total_capacity, remaining_capacity | AK ga_capacity_id; typed FKs to Performance and general Section |
| `PerformanceSeats` | `(performance_id, venue_id, section_name, row_name, seat_number)`, performance_seat_id, blocked_status | AK performance_seat_id; FKs to Performance and Seats |
| `Transactions` | `transaction_id`, customer_id, payment_info_id, payment snapshot, transaction_type, performance_id, listing_id, transaction_date | Conditional AK listing_id; typed FKs to Customer, PaymentInfo, Performance, and ResaleListing |
| `Tickets` | `ticket_id`, purchase_id, performance_id, tier_code, performance_seats_ref, general_seats_ref, face_value, status, active_reserved_seat_ref | Conditional AK active_reserved_seat_ref; FKs to Transactions, PriceTier, PerformanceSeats, and GeneralAdmissionCapacity |
| `ResaleListing` | `listing_id`, ticket_id, seller_ownership_id, listing_price, cap_price_at_listing, status, listed_date, active_ticket_id | Conditional AK active_ticket_id; typed FKs to Tickets and TicketOwnership |
| `TicketOwnership` | `ownership_id`, ticket_id, customer_id, acquired_transaction_id, acquired_listing_id, acquired_at, ended_at, original_purchase_id, current_ticket_id | AK (ticket_id, acquired_transaction_id); conditional AK current_ticket_id; FKs to Ticket, Transactions, and ResaleListing |
| `TicketCancellation` | `cancellation_id`, ticket_id, ownership_id, cancelled_by_user_id, cancellation_date, reason | AK ticket_id; AK ownership_id; FKs to TicketOwnership and Users |
| `Refund` | `refund_id`, cancellation_id, amount, refund_date | AK cancellation_id; FK cancellation_id -> TicketCancellation |
| `Reviews` | `(customer_id, performance_id)`, comment_text, event_rating, venue_rating, review_date | FKs to Customer and Performance |

## 6. Functional dependencies

The important nontrivial dependencies are listed below. Every relation also has the ordinary dependency from each declared candidate key to all its attributes.

- `Users`: `user_id -> name, address, email, date_of_birth, user_role, account_status, deleted_at`; `email ->` the same non-key attributes.
- `PaymentInfo`: `payment_info_id -> customer_id, card_number, card_holder_name, expiry_date, billing_zip`.
- `CustomerRestriction`: `restriction_id -> customer_id, reason, details, started_at, ended_at`; when non-null, `current_customer_id -> restriction_id`.
- `Segment`: `segment_id -> segment_name`; `segment_name -> segment_id`.
- `Genre`: `genre_id -> genre_name, segment_id`; `(genre_name, segment_id) -> genre_id`.
- `Event`: `event_id -> title, description, resale_cap_pct, organizer_id, genre_id`.
- `ArtistsTeams`: `artist_id -> name, type`.
- `BillingOrder`: `(event_id, artist_id) -> billing_rank`; `(event_id, billing_rank) -> artist_id`.
- `Venue`: `venue_id -> name, latitude, longitude, address, city, postal_code, country`.
- `Section`: `(venue_id, section_name) -> section_type, standing_capacity`.
- `SeatRows`: `(venue_id, section_name, row_name) -> section_type`.
- `Performance`: `performance_id -> event_id, venue_id, date_time, status, cancellation data`.
- `PriceTier`: `(performance_id, tier_code) -> price`.
- `SectionTierAssignment`: `(performance_id, venue_id, section_name) -> tier_code`.
- `GeneralAdmissionCapacity`: `(performance_id, venue_id, section_name) -> ga_capacity_id, section_type, total_capacity, remaining_capacity`; `ga_capacity_id -> performance_id, venue_id, section_name`.
- `PerformanceSeats`: `(performance_id, venue_id, section_name, row_name, seat_number) -> performance_seat_id, blocked_status`; `performance_seat_id ->` its performance and physical seat.
- `Transactions`: `transaction_id ->` customer, payment snapshot, transaction type, linked performance/listing, and date; a non-null `listing_id -> transaction_id`.
- `Tickets`: `ticket_id -> purchase_id, performance_id, tier_code, inventory reference, face_value, status`; a non-null `active_reserved_seat_ref -> ticket_id`.
- `ResaleListing`: `listing_id -> ticket_id, seller_ownership_id, prices, status, listed_date`; a non-null `active_ticket_id -> listing_id`.
- `TicketOwnership`: `ownership_id -> ticket_id, customer_id, acquisition data, ended_at`; `(ticket_id, acquired_transaction_id) -> ownership_id`; a non-null `current_ticket_id -> ownership_id`.
- `TicketCancellation`: `cancellation_id -> ticket_id, ownership_id, cancelled_by_user_id, date, reason`; `ticket_id -> cancellation_id`; `ownership_id -> cancellation_id`.
- `Refund`: `refund_id -> cancellation_id, amount, refund_date`; `cancellation_id -> refund_id`.
- `Reviews`: `(customer_id, performance_id) -> comment_text, event_rating, venue_rating, review_date`.

`Seats` has no non-key attribute, and `Customer` and `Organizer` contain only their identity plus a checked subtype discriminator.

## 7. Normal forms and decompositions

The logical design is in BCNF: user details, payments, events, artists, venues, sections, physical seats, performances, tiers, transactions, tickets, listings, ownership intervals, cancellations, refunds, and reviews are separate relations, and each fact is normally determined by a candidate key.

The main decompositions are:

1. User data was decomposed into `Users`, `Customer`, `Organizer`, and `PaymentInfo` so role-specific and repeatable payment facts do not depend transitively on a user row.
2. Event data was decomposed into `Segment`, `Genre`, `Event`, `ArtistsTeams`, and `BillingOrder` to remove repeating artist groups and segment/genre transitive dependencies.
3. Venue data was decomposed into `Venue`, `Section`, `SeatRows`, and `Seats` so a seat definition is not repeated per performance.
4. Performance configuration was decomposed into `Performance`, `PriceTier`, `SectionTierAssignment`, `GeneralAdmissionCapacity`, and `PerformanceSeats` so price, assignment, capacity, and seat state change independently.
5. Sales history was decomposed into `Transactions`, `Tickets`, `ResaleListing`, and `TicketOwnership` so one transaction can cover multiple tickets and one stable ticket can have multiple owners over time.
6. `TicketCancellation` and `Refund` were separated because cancellation facts and financial-refund facts have different keys and lifecycles.
7. `Reviews` was separated from customers, tickets, and performances because it depends on the customer-performance pair, not on an order or ticket row.

The executable schema includes a small physical constraint layer. These copied columns are deliberately controlled by composite foreign keys, generated expressions, or immutable snapshots rather than treated as independent facts:

| Physical field | Logical source/decomposition | Reason retained in executable schema |
|---|---|---|
| subtype `user_role` | `Users(user_id, user_role)` | Prevents inserting a user into the wrong subtype with declarative SQL constraints |
| `SeatRows.section_type` | `Section(venue_id, section_name, section_type)` | Typed FK guarantees rows exist only in reserved sections |
| performance-scoped `venue_id` | `Performance(performance_id, venue_id)` | Composite FKs prevent assigning inventory from another venue |
| GA `section_type` and `total_capacity` | `Section(venue_id, section_name, section_type, standing_capacity)` | Typed FK fixes the section as general and initializes a bounded counter |
| transaction payment snapshot | `PaymentInfo` at purchase time | Historical receipt values must not change with current profile data |
| `Tickets.performance_id` | purchase transaction and inventory references | Composite FKs guarantee the order, tier, and inventory all belong to one performance |
| ownership acquisition customer/listing | acquisition `Transactions` row | Composite FKs prove that each ownership row matches its purchase or resale transaction |

If these implementation-only fields were writable facts, they would create dependencies whose determinants are not always superkeys. In MyTix they are protected copies used for cross-table integrity or historical snapshots. The conceptual relations above remain the normalized source of each fact, while the physical additions trade a small amount of controlled redundancy for constraints that MySQL can enforce directly.

## 8. DDL types, constraints, and indexes

The complete executable DDL is `sql/schema.sql`; it is included verbatim in Appendix A of the submitted report so the report and grading script use the same statements.

- Numeric identifiers use `INT AUTO_INCREMENT`; long-lived history identifiers use `BIGINT AUTO_INCREMENT`.
- Money uses `DECIMAL(10,2)`, resale multipliers use `DECIMAL(4,2)`, and coordinates use `DECIMAL(9,6)`.
- Calendar values use `DATE`; performance and audit values use `DATETIME`.
- Bounded states use `ENUM`; ratings use `TINYINT`; seat blocking uses `BOOLEAN`.
- Required facts are `NOT NULL`. Optional descriptions, completed intervals, and mutually exclusive references are nullable.
- Primary, unique, and foreign keys enforce identity, role, event ownership, venue consistency, tier consistency, payment ownership, one current ticket owner, one active listing, and one active reserved-seat ticket.
- `CHECK` constraints enforce coordinate ranges, capacity bounds, positive ranks and prices, valid cancellation state, exactly one inventory type per ticket, rating ranges, and valid history intervals.
- Search/report indexes cover venue coordinates, postal code, city, address, upcoming performance status/date, transaction date/customer, ticket performance/status/tier, listing status/date, and restriction history.

Creation temporarily disables foreign-key checking only because `Transactions`, `Tickets`, `ResaleListing`, and `TicketOwnership` contain mutually dependent foreign keys. Every relationship is declared inline, and checks are re-enabled at the end.

## 9. Operations and terminal interface

The application separates terminal input/output from JDBC operation classes. Shared result objects distinguish success, invalid input, not found, conflict, forbidden, and database error outcomes. Identifiers are checked before mutation, but the transaction repeats every decisive check under a lock.

The ten main-menu areas cover profiles; organizer events and performances; pricing and inventory; booking and cancellation; resale and histories; reviews; Q1-Q7; R1-R9; organizer toolkit; and database reconnection. The detailed user procedure is in `manual.md`.

## 10. Query design highlights (Q1-Q7)

- **Q1:** Computes Haversine distance in SQL, filters upcoming performances within the radius, and sorts by distance, lowest price ascending, or lowest price descending. Sold-out performances remain visible with their sold-out state.
- **Q2:** Normalizes the supplied postal code, compares its first three characters, and returns upcoming performances in matching venues.
- **Q3:** Normalizes the exact venue address and returns upcoming performances at every matching venue.
- **Q4:** Filters upcoming performances by location mode, inclusive date range, and minimum available tickets. Reserved seats count only active, unblocked, unsold seats; GA uses remaining capacity.
- **Q5:** Combines optional date, city, segment, genre, price, minimum-availability, and section-type filters. Price and availability are evaluated for the requested inventory type.
- **Q6:** Aggregates one performance by section and tier, reporting price, available/remaining inventory, sold count, and reserved-seat blocked count.
- **Q7:** Builds the seat map for one performance and chooses the cheapest tier that can satisfy the group. For reserved seats it prefers consecutive seats in one row; when those do not exist it returns the best nonconsecutive fallback. The output states whether the result is consecutive.

## 11. Report design highlights (R1-R9)

- **R1:** Counts originally sold tickets and gross face-value revenue for an inclusive date range, grouped by city or by venue within a chosen city.
- **R2:** Counts events and performances by segment or genre and by geographic rollup: country, country/city, or country/city/venue.
- **R3:** Ranks organizers by original face-value ticket revenue overall, by country, or by city within a country.
- **R4:** In a rolling 12-month window, evaluates customer/city groups, identifies customers with at least 10 purchased tickets who listed more than half, and synchronizes restrictions used by booking and resale guards.
- **R5:** Ranks customers by order count in a supplied period and also reports one-year customer rankings within each city.
- **R6:** Reports customers with the most cancelled tickets and organizers with the most cancelled performances in the preceding year.
- **R7:** Reports sell-through by performance or by tier and separately finds sold-out or below-25% performances for a supplied year/month, grouped by city.
- **R8:** Reports resale statistics per event and returns the top 10 events by completed-resale volume in a supplied period.
- **R9:** Retrieves comments for selected events with SQL, extracts noun phrases with OpenNLP, and ranks the most frequent normalized phrases for each event.

All report ranges accept calendar dates. The implementation converts the inclusive end date to the next midnight internally, avoiding a hidden `23:59:59` assumption.

## 12. Sample-data strategy

`sql/load.sql` invokes the deterministic development-data generator. A fresh load supplies stable identifier ranges and dates relative to the day of loading. It includes 5 organizers, 100 customers, 8 venues in 6 cities and 2 countries, 20 events, 60 performances, 320 original orders, 960 tickets, customer and organizer cancellations, sold/withdrawn/active listings, a twice-resold ticket, possible-scalper cases, and reviews for 10 events.

The dataset deliberately covers available, blocked, sold, sold-out, low-sell-through, cancelled, completed, future, reserved, GA, resale-cap, and consecutive-seat cases. `data/README.md` explains the stable IDs; `data/queries-reports-toolkits.md` gives time-relative inputs and expected result shapes. Because dates are generated relative to load time, documentation describes invariant outcomes instead of claiming that every displayed timestamp or total is universal across modified datasets.

## 13. Organizer toolkit

The pricing recommendation asks for a genre, city, and planned venue capacity. It first searches completed primary-market performances from the preceding 24 months in the same genre and city, with capacity within 25% of the target. If evidence is insufficient, it widens to 36 months, any city, and capacity within 50%.

For comparable performances, SQL computes capacity, tier price, tier capacity, tickets sold, and sell-through. Java orders tiers from low to high, chooses the modal comparable tier count, derives a weighted price for each rank, and normalizes recommended tier capacity shares to exactly 100%. The explanation lists the method and comparable performances used.

When there is no usable history, the documented fallback is three tiers: 50% of capacity at $60, 30% at $100, and 20% at $150. The optional revenue-impact estimator compares comparable-tier observations around the current and proposed prices; it is an estimate, not a forecast guarantee.

## 14. Limitations and tradeoffs

- The terminal has no authentication or authorization session. An identifier proves which row to use, not who is operating the terminal. In particular, organizer cancellation currently accepts a performance ID without proving that the operator controls its event.
- Card details are fictional sample data and are stored for coursework demonstration. A production system should use tokenized payment-provider references and stronger access controls.
- Location searches use coordinates, postal prefixes, or normalized exact addresses; they do not call a geocoder and do not account for driving distance.
- UTC-based date handling is consistent but does not model a separate time zone for each venue.
- Scalper detection follows the required threshold and can produce false positives. A real system would use risk review, appeals, account/device/payment signals, and carefully governed enforcement.
- Toolkit recommendations depend on the quantity and quality of completed historical data. Sparse history triggers a transparent fallback rather than pretending to be precise.
- The terminal is single-process coursework software. Database constraints protect concurrent writes, but there is no web API, distributed cache, queue, observability platform, or high-availability deployment.

## 15. Possibilities for improvement

1. Add secure authentication, role-based authorization, and organizer-event ownership checks. Organizer cancellation should at minimum request and verify the organizer ID; a production design should derive it from an authenticated session and require reauthentication for destructive actions.
2. Encrypt sensitive fields, replace stored card numbers with provider tokens, add secret management, and maintain an auditable permission log.
3. Store venue time zones and make local-time display and daylight-saving conversion explicit.
4. Add geocoding and address canonicalization, with spatial indexes for larger datasets.
5. Add a reviewed scalper workflow with explanations, expiration, appeals, and more behavioral signals.
6. Improve toolkit evaluation with confidence intervals, seasonal features, artist popularity, day/time effects, and held-out accuracy measurements.
7. Add automated end-to-end tests for every terminal path and export report results to CSV or PDF.
8. Add a web or mobile interface with searchable selectors so users do not need to know numeric IDs.

## Appendix A — complete schema DDL

The following is the complete DDL submitted as `sql/schema.sql`. It is repeated here to satisfy the report requirement that the DDL statements be included. If the executable schema changes, this appendix must be regenerated from that file before `report.pdf` is exported.

```sql
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE Users (
    user_id         INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    date_of_birth   DATE NOT NULL,
    user_role       ENUM('customer','organizer') NOT NULL,
    account_status  ENUM('active','deleted') NOT NULL DEFAULT 'active',
    deleted_at      DATETIME NULL,
    UNIQUE (email),
    UNIQUE (user_id, user_role),
    CHECK (
        (account_status = 'active' AND deleted_at IS NULL)
        OR (account_status = 'deleted' AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Customer (
    user_id         INT PRIMARY KEY,
    user_role       ENUM('customer','organizer') NOT NULL DEFAULT 'customer',
    FOREIGN KEY (user_id, user_role)
        REFERENCES Users(user_id, user_role) ON DELETE CASCADE,
    CHECK (user_role = 'customer')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Organizer (
    user_id         INT PRIMARY KEY,
    user_role       ENUM('customer','organizer') NOT NULL DEFAULT 'organizer',
    FOREIGN KEY (user_id, user_role)
        REFERENCES Users(user_id, user_role) ON DELETE CASCADE,
    CHECK (user_role = 'organizer')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE PaymentInfo (
    payment_info_id  INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    card_number      VARCHAR(25) NOT NULL,
    card_holder_name VARCHAR(150) NOT NULL,
    expiry_date      DATE NOT NULL,
    billing_zip      VARCHAR(20) NOT NULL,
    UNIQUE (payment_info_id, customer_id),
    FOREIGN KEY (customer_id) REFERENCES Customer(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE CustomerRestriction (
    restriction_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id          INT NOT NULL,
    restriction_reason   ENUM('possible_scalper','manual_review','other') NOT NULL,
    details              VARCHAR(255) NULL,
    started_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at             DATETIME NULL,
    current_customer_id  INT GENERATED ALWAYS AS (
        CASE WHEN ended_at IS NULL THEN customer_id ELSE NULL END
    ) STORED,
    UNIQUE (current_customer_id),
    INDEX idx_customer_restriction_history (customer_id, started_at),
    FOREIGN KEY (customer_id) REFERENCES Customer(user_id),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    UNIQUE (event_id, organizer_id),
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

CREATE TABLE Venue (
    venue_id        INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    latitude        DECIMAL(9,6) NOT NULL,
    longitude       DECIMAL(9,6) NOT NULL,
    address         VARCHAR(255) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    postal_code     VARCHAR(20) NOT NULL,
    country         VARCHAR(100) NOT NULL,
    INDEX idx_venue_coordinates (latitude, longitude),
    INDEX idx_venue_postal_code (postal_code),
    INDEX idx_venue_city (city),
    INDEX idx_venue_address (address),
    CHECK (latitude BETWEEN -90.000000 AND 90.000000),
    CHECK (longitude BETWEEN -180.000000 AND 180.000000)
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
    FOREIGN KEY (venue_id, section_name, row_name)
        REFERENCES SeatRows(venue_id, section_name, row_name) ON DELETE CASCADE,
    CHECK (seat_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Performance (
    performance_id      INT AUTO_INCREMENT PRIMARY KEY,
    event_id            INT NOT NULL,
    venue_id            INT NOT NULL,
    date_time           DATETIME NOT NULL,
    status              ENUM('scheduled','cancelled','completed') NOT NULL DEFAULT 'scheduled',
    cancellation_date   DATETIME NULL,
    cancellation_reason VARCHAR(255) NULL,
    cancelled_by_organizer_id INT NULL,
    FOREIGN KEY (event_id) REFERENCES Event(event_id),
    FOREIGN KEY (venue_id) REFERENCES Venue(venue_id),
    FOREIGN KEY (event_id, cancelled_by_organizer_id)
        REFERENCES Event(event_id, organizer_id),
    UNIQUE (performance_id, venue_id),
    INDEX idx_performance_upcoming (status, date_time, venue_id),
    CHECK (
        (status = 'cancelled'
            AND cancellation_date IS NOT NULL
            AND cancelled_by_organizer_id IS NOT NULL)
        OR (status <> 'cancelled'
            AND cancellation_date IS NULL
            AND cancellation_reason IS NULL
            AND cancelled_by_organizer_id IS NULL)
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
    FOREIGN KEY (performance_id, venue_id)
        REFERENCES Performance(performance_id, venue_id),
    FOREIGN KEY (venue_id, section_name) REFERENCES Section(venue_id, section_name),
    FOREIGN KEY (performance_id, tier_code)
        REFERENCES PriceTier(performance_id, tier_code)
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
    FOREIGN KEY (performance_id, venue_id)
        REFERENCES Performance(performance_id, venue_id),
    FOREIGN KEY (venue_id, section_name, section_type, total_capacity)
        REFERENCES Section(venue_id, section_name, section_type, standing_capacity),
    CHECK (section_type = 'general'),
    CHECK (remaining_capacity BETWEEN 0 AND total_capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    FOREIGN KEY (performance_id, venue_id)
        REFERENCES Performance(performance_id, venue_id),
    FOREIGN KEY (venue_id, section_name, row_name, seat_number)
        REFERENCES Seats(venue_id, section_name, row_name, seat_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Transactions (
    transaction_id   INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    payment_info_id  INT NOT NULL,
    payment_card_number      VARCHAR(25) NOT NULL,
    payment_card_holder_name VARCHAR(150) NOT NULL,
    payment_expiry_date      DATE NOT NULL,
    payment_billing_zip      VARCHAR(20) NOT NULL,
    transaction_type ENUM('purchase','resale') NOT NULL,
    performance_id   INT NULL,
    listing_id       INT NULL,
    transaction_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (transaction_id, customer_id),
    UNIQUE (transaction_id, performance_id),
    UNIQUE (transaction_id, listing_id),
    UNIQUE (listing_id),
    INDEX idx_transaction_date_customer (transaction_date, customer_id),
    FOREIGN KEY (customer_id) REFERENCES Customer(user_id),
    FOREIGN KEY (payment_info_id, customer_id)
        REFERENCES PaymentInfo(payment_info_id, customer_id),
    FOREIGN KEY (performance_id) REFERENCES Performance(performance_id),
    FOREIGN KEY (listing_id) REFERENCES ResaleListing(listing_id),
    CHECK (
        (transaction_type = 'purchase'
            AND performance_id IS NOT NULL
            AND listing_id IS NULL)
        OR (transaction_type = 'resale'
            AND performance_id IS NULL
            AND listing_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE Tickets (
    ticket_id             INT AUTO_INCREMENT PRIMARY KEY,
    purchase_id           INT NOT NULL,
    performance_id        INT NOT NULL,
    tier_code             VARCHAR(20) NOT NULL,
    performance_seats_ref INT NULL,
    general_seats_ref     INT NULL,
    face_value            DECIMAL(10,2) NOT NULL,
    status                ENUM('active','cancelled') NOT NULL DEFAULT 'active',
    active_reserved_seat_ref INT GENERATED ALWAYS AS (
        CASE WHEN status = 'active' THEN performance_seats_ref ELSE NULL END
    ) STORED,
    UNIQUE (active_reserved_seat_ref),
    UNIQUE (ticket_id, purchase_id),
    INDEX idx_ticket_performance_status_tier (performance_id, status, tier_code),
    FOREIGN KEY (purchase_id, performance_id)
        REFERENCES Transactions(transaction_id, performance_id),
    FOREIGN KEY (performance_id, tier_code)
        REFERENCES PriceTier(performance_id, tier_code),
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
    listing_id           INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id            INT NOT NULL,
    seller_ownership_id  BIGINT NOT NULL,
    listing_price        DECIMAL(10,2) NOT NULL,
    cap_price_at_listing DECIMAL(10,2) NOT NULL,
    status               ENUM('active','sold','withdrawn') NOT NULL DEFAULT 'active',
    listed_date          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_ticket_id     INT GENERATED ALWAYS AS (
        CASE WHEN status = 'active' THEN ticket_id ELSE NULL END
    ) STORED,
    UNIQUE (active_ticket_id),
    UNIQUE (listing_id, ticket_id),
    INDEX idx_resale_listing_status_date (status, listed_date),
    FOREIGN KEY (ticket_id) REFERENCES Tickets(ticket_id),
    FOREIGN KEY (seller_ownership_id, ticket_id)
        REFERENCES TicketOwnership(ownership_id, ticket_id),
    CHECK (listing_price BETWEEN 0 AND cap_price_at_listing),
    CHECK (cap_price_at_listing >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE TicketOwnership (
    ownership_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id               INT NOT NULL,
    customer_id             INT NOT NULL,
    acquired_transaction_id INT NOT NULL,
    acquired_listing_id     INT NULL,
    acquired_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at                DATETIME NULL,
    original_purchase_id    INT GENERATED ALWAYS AS (
        CASE WHEN acquired_listing_id IS NULL THEN acquired_transaction_id ELSE NULL END
    ) STORED,
    current_ticket_id       INT GENERATED ALWAYS AS (
        CASE WHEN ended_at IS NULL THEN ticket_id ELSE NULL END
    ) STORED,
    UNIQUE (ticket_id, acquired_transaction_id),
    UNIQUE (ownership_id, ticket_id),
    UNIQUE (current_ticket_id),
    FOREIGN KEY (ticket_id) REFERENCES Tickets(ticket_id),
    FOREIGN KEY (ticket_id, original_purchase_id)
        REFERENCES Tickets(ticket_id, purchase_id),
    FOREIGN KEY (acquired_transaction_id, customer_id)
        REFERENCES Transactions(transaction_id, customer_id),
    FOREIGN KEY (acquired_transaction_id, acquired_listing_id)
        REFERENCES Transactions(transaction_id, listing_id),
    FOREIGN KEY (acquired_listing_id, ticket_id)
        REFERENCES ResaleListing(listing_id, ticket_id),
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
    refund_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    cancellation_id BIGINT NOT NULL,
    amount           DECIMAL(10,2) NOT NULL,
    refund_date      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (cancellation_id),
    FOREIGN KEY (cancellation_id) REFERENCES TicketCancellation(cancellation_id),
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
```

The grading sequence is `sql/schema.sql`, `sql/load.sql`, then `run.sh`. No manual schema or data repair is required between those steps.

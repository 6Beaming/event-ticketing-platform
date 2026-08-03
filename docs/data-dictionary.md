# Data Dictionary

`sql/schema.sql` is the executable source of truth. This dictionary records the
foundation contract in a review-friendly form. `NN` means `NOT NULL`; `NULL`
means optional; `PK`, `FK`, and `UQ` identify primary, foreign, and unique keys.

## Users and profiles

| Relation | Columns | Keys, indexes, and allowed values |
|---|---|---|
| `Users` | `user_id INT NN AUTO_INCREMENT`; `name VARCHAR(150) NN`; `address VARCHAR(255) NN`; `email VARCHAR(255) NN`; `date_of_birth DATE NN`; `user_role ENUM NN`; `account_status ENUM NN DEFAULT 'active'`; `deleted_at DATETIME NULL` | PK `user_id`; UQ `email`, `(user_id,user_role)`; roles `customer`,`organizer`; statuses `active`,`deleted`; status/deletion consistency check |
| `Customer` | `user_id INT NN`; `user_role ENUM NN DEFAULT 'customer'` | PK `user_id`; FK `(user_id,user_role)` to `Users`; role must be `customer` |
| `Organizer` | `user_id INT NN`; `user_role ENUM NN DEFAULT 'organizer'` | PK `user_id`; FK `(user_id,user_role)` to `Users`; role must be `organizer` |
| `PaymentInfo` | `payment_info_id INT NN AUTO_INCREMENT`; `customer_id INT NN`; `card_number VARCHAR(25) NN`; `card_holder_name VARCHAR(150) NN`; `expiry_date DATE NN`; `billing_zip VARCHAR(20) NN` | PK `payment_info_id`; UQ `(payment_info_id,customer_id)`; FK `customer_id` to `Customer` |
| `CustomerRestriction` | `restriction_id BIGINT NN AUTO_INCREMENT`; `customer_id INT NN`; `restriction_reason ENUM NN`; `details VARCHAR(255) NULL`; `started_at DATETIME NN`; `ended_at DATETIME NULL`; `current_customer_id INT GENERATED NULL` | PK `restriction_id`; UQ `current_customer_id`; history index `(customer_id,started_at)`; reasons `possible_scalper`,`manual_review`,`other`; FK `customer_id` to `Customer` |

## Events and taxonomy

| Relation | Columns | Keys, indexes, and allowed values |
|---|---|---|
| `Segment` | `segment_id INT NN AUTO_INCREMENT`; `segment_name VARCHAR(100) NN` | PK `segment_id`; UQ `segment_name` |
| `Genre` | `genre_id INT NN AUTO_INCREMENT`; `genre_name VARCHAR(100) NN`; `segment_id INT NN` | PK `genre_id`; UQ `(genre_name,segment_id)`; FK `segment_id` to `Segment` |
| `Event` | `event_id INT NN AUTO_INCREMENT`; `title VARCHAR(200) NN`; `description TEXT NULL`; `resale_cap_pct DECIMAL(4,2) NN DEFAULT 1.20`; `organizer_id INT NN`; `genre_id INT NN` | PK `event_id`; UQ `(event_id,organizer_id)`; FKs to `Organizer` and `Genre`; cap at least `1.00` |
| `ArtistsTeams` | `artist_id INT NN AUTO_INCREMENT`; `name VARCHAR(150) NN`; `type VARCHAR(50) NN` | PK `artist_id` |
| `BillingOrder` | `event_id INT NN`; `artist_id INT NN`; `billing_rank INT NN` | PK `(event_id,artist_id)`; UQ `(event_id,billing_rank)`; FKs to `Event` and `ArtistsTeams`; positive rank |

## Venues and physical seating

| Relation | Columns | Keys, indexes, and allowed values |
|---|---|---|
| `Venue` | `venue_id INT NN AUTO_INCREMENT`; `name VARCHAR(200) NN`; `latitude DECIMAL(9,6) NN`; `longitude DECIMAL(9,6) NN`; `address VARCHAR(255) NN`; `city VARCHAR(100) NN`; `postal_code VARCHAR(20) NN`; `country VARCHAR(100) NN` | PK `venue_id`; indexes on coordinates, postal code, city, and address; latitude/longitude range checks |
| `Section` | `venue_id INT NN`; `section_name VARCHAR(100) NN`; `section_type ENUM NN`; `standing_capacity INT NULL` | PK `(venue_id,section_name)`; typed UQs; FK to `Venue`; types `reserved`,`general`; reserved has no standing capacity, general has positive capacity |
| `SeatRows` | `venue_id INT NN`; `section_name VARCHAR(100) NN`; `section_type ENUM NN DEFAULT 'reserved'`; `row_name VARCHAR(20) NN` | PK `(venue_id,section_name,row_name)`; typed FK to `Section`; type must be `reserved` |
| `Seats` | `venue_id INT NN`; `section_name VARCHAR(100) NN`; `row_name VARCHAR(20) NN`; `seat_number INT NN` | PK `(venue_id,section_name,row_name,seat_number)`; FK to `SeatRows`; positive seat number |

## Performances, pricing, and inventory

| Relation | Columns | Keys, indexes, and allowed values |
|---|---|---|
| `Performance` | `performance_id INT NN AUTO_INCREMENT`; `event_id INT NN`; `venue_id INT NN`; `date_time DATETIME NN`; `status ENUM NN DEFAULT 'scheduled'`; `cancellation_date DATETIME NULL`; `cancellation_reason VARCHAR(255) NULL`; `cancelled_by_organizer_id INT NULL` | PK `performance_id`; UQ `(performance_id,venue_id)`; FKs to `Event`, `Venue`, and owning organizer; status `scheduled`,`cancelled`,`completed`; upcoming index `(status,date_time,venue_id)` |
| `PriceTier` | `performance_id INT NN`; `tier_code VARCHAR(20) NN`; `price DECIMAL(10,2) NN` | PK `(performance_id,tier_code)`; FK to `Performance`; nonnegative price |
| `SectionTierAssignment` | `performance_id INT NN`; `venue_id INT NN`; `section_name VARCHAR(100) NN`; `tier_code VARCHAR(20) NN` | PK `(performance_id,venue_id,section_name)`; FKs to the same performance/venue, physical section, and performance tier |
| `GeneralAdmissionCapacity` | `ga_capacity_id INT NN AUTO_INCREMENT`; `performance_id INT NN`; `venue_id INT NN`; `section_name VARCHAR(100) NN`; `section_type ENUM NN DEFAULT 'general'`; `total_capacity INT NN`; `remaining_capacity INT NN` | PK `(performance_id,venue_id,section_name)`; UQ `ga_capacity_id`, `(ga_capacity_id,performance_id)`; typed FKs to `Performance` and `Section`; remaining capacity from zero through total |
| `PerformanceSeats` | `performance_seat_id INT NN AUTO_INCREMENT`; `performance_id INT NN`; `venue_id INT NN`; `section_name VARCHAR(100) NN`; `row_name VARCHAR(20) NN`; `seat_number INT NN`; `blocked_status BOOLEAN NN DEFAULT FALSE` | PK `(performance_id,venue_id,section_name,row_name,seat_number)`; UQ `performance_seat_id`, `(performance_seat_id,performance_id)`; FKs to `Performance` and `Seats` |

## Transactions, tickets, resale, and reviews

| Relation | Columns | Keys, indexes, and allowed values |
|---|---|---|
| `Transactions` | `transaction_id INT NN AUTO_INCREMENT`; `customer_id INT NN`; `payment_info_id INT NN`; payment snapshot fields `VARCHAR/DATE NN`; `transaction_type ENUM NN`; `performance_id INT NULL`; `listing_id INT NULL`; `transaction_date DATETIME NN` | PK `transaction_id`; UQs with customer/performance/listing and UQ `listing_id`; FKs to customer, payment, performance, listing; types `purchase`,`resale` with matching target check; index `(transaction_date,customer_id)` |
| `Tickets` | `ticket_id INT NN AUTO_INCREMENT`; `purchase_id INT NN`; `performance_id INT NN`; `tier_code VARCHAR(20) NN`; `performance_seats_ref INT NULL`; `general_seats_ref INT NULL`; `face_value DECIMAL(10,2) NN`; `status ENUM NN DEFAULT 'active'`; `active_reserved_seat_ref INT GENERATED NULL` | PK `ticket_id`; UQ `(ticket_id,purchase_id)`, `active_reserved_seat_ref`; FKs to purchase/performance, tier, reserved or GA inventory; exactly one inventory reference; statuses `active`,`cancelled`; performance/status/tier index |
| `ResaleListing` | `listing_id INT NN AUTO_INCREMENT`; `ticket_id INT NN`; `seller_ownership_id BIGINT NN`; `listing_price DECIMAL(10,2) NN`; `cap_price_at_listing DECIMAL(10,2) NN`; `status ENUM NN DEFAULT 'active'`; `listed_date DATETIME NN`; `active_ticket_id INT GENERATED NULL` | PK `listing_id`; UQ `active_ticket_id`, `(listing_id,ticket_id)`; FKs to ticket and seller ownership; statuses `active`,`sold`,`withdrawn`; nonnegative price at or below cap; status/date index |
| `TicketOwnership` | `ownership_id BIGINT NN AUTO_INCREMENT`; `ticket_id INT NN`; `customer_id INT NN`; `acquired_transaction_id INT NN`; `acquired_listing_id INT NULL`; `acquired_at DATETIME NN`; `ended_at DATETIME NULL`; generated `original_purchase_id INT NULL`, `current_ticket_id INT NULL` | PK `ownership_id`; UQs `(ticket_id,acquired_transaction_id)`, `(ownership_id,ticket_id)`, `current_ticket_id`; FKs tie ticket, customer, acquisition transaction, and optional listing together; valid ownership period check |
| `TicketCancellation` | `cancellation_id BIGINT NN AUTO_INCREMENT`; `ticket_id INT NN`; `ownership_id BIGINT NN`; `cancelled_by_user_id INT NN`; `cancellation_date DATETIME NN`; `reason VARCHAR(255) NULL` | PK `cancellation_id`; UQ `ticket_id`, `ownership_id`; FKs to the matching ownership/ticket and cancelling user |
| `Refund` | `refund_id BIGINT NN AUTO_INCREMENT`; `cancellation_id BIGINT NN`; `amount DECIMAL(10,2) NN`; `refund_date DATETIME NN` | PK `refund_id`; UQ `cancellation_id`; FK to `TicketCancellation`; nonnegative amount |
| `Reviews` | `customer_id INT NN`; `performance_id INT NN`; `comment_text TEXT NULL`; `event_rating TINYINT NN`; `venue_rating TINYINT NN`; `review_date DATETIME NN` | PK `(customer_id,performance_id)`; FKs to `Customer` and `Performance`; both ratings from 1 through 5 |

## Index review

- Location and date searches use `Venue` indexes for coordinates, postal code,
  city, and address together with `Performance(status,date_time,venue_id)`.
- Availability uses `Tickets(performance_id,status,tier_code)`, the unique
  active reserved-seat reference, performance-seat keys, and GA capacity keys.
- Ownership and resale use the unique current-ownership/current-listing keys
  and `ResaleListing(status,listed_date)`.
- Time-period customer reports use
  `Transactions(transaction_date,customer_id)`; MySQL also creates supporting
  indexes for declared foreign keys when an equivalent index does not exist.
- Before the August 4-6 report work, review `EXPLAIN` output for cancellation
  date/actor and ownership customer/date filters. Add report-specific indexes
  only when the real SQL demonstrates they are needed.

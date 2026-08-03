# Relational Schema


## Relations

`Users` (<u>user_id</u>, name, address, email, date_of_birth, user_role, account_status, deleted_at)

`Customer` (<u>user_id</u>, user_role)

`Organizer` (<u>user_id</u>, user_role)

`PaymentInfo` (<u>payment_info_id</u>, customer_id, card_number, card_holder_name, expiry_date, billing_zip)

`CustomerRestriction` (<u>restriction_id</u>, customer_id, restriction_reason, details, started_at, ended_at, current_customer_id)

`Segment` (<u>segment_id</u>, segment_name)

`Genre` (<u>genre_id</u>, genre_name, segment_id)

`Event` (<u>event_id</u>, title, description, resale_cap_pct, organizer_id, genre_id)

`ArtistsTeams` (<u>artist_id</u>, name, type)

`BillingOrder` (<u>event_id</u>, <u>artist_id</u>, billing_rank)

`Venue` (<u>venue_id</u>, name, latitude, longitude, address, city, postal_code, country)

`Section` (<u>venue_id</u>, <u>section_name</u>, section_type, standing_capacity)

`SeatRows` (<u>venue_id</u>, <u>section_name</u>, section_type, <u>row_name</u>)

`Seats` (<u>venue_id</u>, <u>section_name</u>, <u>row_name</u>, <u>seat_number</u>)

`Performance` (<u>performance_id</u>, event_id, venue_id, date_time, status, cancellation_date, cancellation_reason, cancelled_by_organizer_id)

`PriceTier` (<u>performance_id</u>, <u>tier_code</u>, price)

`SectionTierAssignment` (<u>performance_id</u>, <u>venue_id</u>, <u>section_name</u>, tier_code)

`GeneralAdmissionCapacity` (ga_capacity_id, <u>performance_id</u>, <u>venue_id</u>, <u>section_name</u>, section_type, total_capacity, remaining_capacity)

`PerformanceSeats` (performance_seat_id, <u>performance_id</u>, <u>venue_id</u>, <u>section_name</u>, <u>row_name</u>, <u>seat_number</u>, blocked_status)

`Transactions` (<u>transaction_id</u>, customer_id, payment_info_id, payment_card_number, payment_card_holder_name, payment_expiry_date, payment_billing_zip, transaction_type, performance_id, listing_id, transaction_date)

`Tickets` (<u>ticket_id</u>, purchase_id, performance_id, tier_code, performance_seats_ref, general_seats_ref, face_value, status, active_reserved_seat_ref)

`ResaleListing` (<u>listing_id</u>, ticket_id, seller_ownership_id, listing_price, cap_price_at_listing, status, listed_date, active_ticket_id)

`TicketOwnership` (<u>ownership_id</u>, ticket_id, customer_id, acquired_transaction_id, acquired_listing_id, acquired_at, ended_at, original_purchase_id, current_ticket_id)

`TicketCancellation` (<u>cancellation_id</u>, ticket_id, ownership_id, cancelled_by_user_id, cancellation_date, reason)

`Refund` (<u>refund_id</u>, cancellation_id, amount, refund_date)

`Reviews` (<u>customer_id</u>, <u>performance_id</u>, comment_text, event_rating, venue_rating, review_date)

## Foreign-Key Relationships

The subset symbol shows that each non-null foreign-key value on the left must
match a referenced key value on the right.

### User relationships

`Customer [user_id, user_role]` ⊆ `Users [user_id, user_role]`

`Organizer [user_id, user_role]` ⊆ `Users [user_id, user_role]`

`PaymentInfo [customer_id]` ⊆ `Customer [user_id]`

`CustomerRestriction [customer_id]` ⊆ `Customer [user_id]`

### Event relationships

`Genre [segment_id]` ⊆ `Segment [segment_id]`

`Event [organizer_id]` ⊆ `Organizer [user_id]`

`Event [genre_id]` ⊆ `Genre [genre_id]`

`BillingOrder [event_id]` ⊆ `Event [event_id]`

`BillingOrder [artist_id]` ⊆ `ArtistsTeams [artist_id]`

### Venue and seating relationships

`Section [venue_id]` ⊆ `Venue [venue_id]`

`SeatRows [venue_id, section_name, section_type]` ⊆ `Section [venue_id, section_name, section_type]`

`Seats [venue_id, section_name, row_name]` ⊆ `SeatRows [venue_id, section_name, row_name]`

### Performance and pricing relationships

`Performance [event_id]` ⊆ `Event [event_id]`

`Performance [venue_id]` ⊆ `Venue [venue_id]`

`Performance [event_id, cancelled_by_organizer_id]` ⊆ `Event [event_id, organizer_id]`

`PriceTier [performance_id]` ⊆ `Performance [performance_id]`

`SectionTierAssignment [performance_id, venue_id]` ⊆ `Performance [performance_id, venue_id]`

`SectionTierAssignment [venue_id, section_name]` ⊆ `Section [venue_id, section_name]`

`SectionTierAssignment [performance_id, tier_code]` ⊆ `PriceTier [performance_id, tier_code]`

`GeneralAdmissionCapacity [performance_id, venue_id]` ⊆ `Performance [performance_id, venue_id]`

`GeneralAdmissionCapacity [venue_id, section_name, section_type, total_capacity]` ⊆ `Section [venue_id, section_name, section_type, standing_capacity]`

`PerformanceSeats [performance_id, venue_id]` ⊆ `Performance [performance_id, venue_id]`

`PerformanceSeats [venue_id, section_name, row_name, seat_number]` ⊆ `Seats [venue_id, section_name, row_name, seat_number]`

### Ticketing relationships

`Transactions [customer_id]` ⊆ `Customer [user_id]`

`Transactions [payment_info_id, customer_id]` ⊆ `PaymentInfo [payment_info_id, customer_id]`

`Transactions [performance_id]` ⊆ `Performance [performance_id]`

`Transactions [listing_id]` ⊆ `ResaleListing [listing_id]`

`Tickets [purchase_id, performance_id]` ⊆ `Transactions [transaction_id, performance_id]`

`Tickets [performance_id, tier_code]` ⊆ `PriceTier [performance_id, tier_code]`

`Tickets [performance_seats_ref, performance_id]` ⊆ `PerformanceSeats [performance_seat_id, performance_id]`

`Tickets [general_seats_ref, performance_id]` ⊆ `GeneralAdmissionCapacity [ga_capacity_id, performance_id]`

`ResaleListing [ticket_id]` ⊆ `Tickets [ticket_id]`

`ResaleListing [seller_ownership_id, ticket_id]` ⊆ `TicketOwnership [ownership_id, ticket_id]`

`TicketOwnership [ticket_id]` ⊆ `Tickets [ticket_id]`

`TicketOwnership [ticket_id, original_purchase_id]` ⊆ `Tickets [ticket_id, purchase_id]`

`TicketOwnership [acquired_transaction_id, customer_id]` ⊆ `Transactions [transaction_id, customer_id]`

`TicketOwnership [acquired_transaction_id, acquired_listing_id]` ⊆ `Transactions [transaction_id, listing_id]`

`TicketOwnership [acquired_listing_id, ticket_id]` ⊆ `ResaleListing [listing_id, ticket_id]`

`TicketCancellation [ownership_id, ticket_id]` ⊆ `TicketOwnership [ownership_id, ticket_id]`

`TicketCancellation [cancelled_by_user_id]` ⊆ `Users [user_id]`

`Refund [cancellation_id]` ⊆ `TicketCancellation [cancellation_id]`

`Reviews [customer_id]` ⊆ `Customer [user_id]`

`Reviews [performance_id]` ⊆ `Performance [performance_id]`

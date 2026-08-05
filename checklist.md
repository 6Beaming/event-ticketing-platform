## Complete PDF requirement checklist

The dated work blocks below are complete only when every item in this section is implemented and testable.

### Information and constraints to represent

- [ ] Venues store name, latitude, longitude, street address, postal code, city, and country.
- [ ] Each venue has named sections.
- [ ] A section is either reserved seating or general admission, never both.
- [ ] Reserved sections have named rows and numbered seats.
- [ ] General-admission sections have standing capacity but no rows or seats.
- [ ] Section names are unique within a venue, row names within a section, and seat numbers within a row.
- [ ] Each event is managed by an organizer.
- [ ] Events use the official Ticketmaster segment/genre taxonomy.
- [ ] Each event has one or more artists or teams with billing order.
- [ ] Each event has one or more performances and a resale price cap.
- [ ] Each performance has a venue, date, and time.
- [ ] Every performance has price tiers and assigns every venue section to exactly one tier.
- [ ] Section-to-tier assignments can differ between performances at the same venue.
- [ ] General-admission tickets reduce the remaining performance-section capacity.
- [ ] Individual reserved seats can be blocked for a particular performance.
- [ ] Users store name, address, email, and date of birth and must be at least 18.
- [ ] A user can be a customer or organizer.
- [ ] Customers have fictional payment information and orders retain a payment snapshot.
- [ ] No real payment processing or card-number validation is required; only fictional order payment data is recorded.
- [ ] Customer order/ticket history and organizer event/sales history are retained.
- [ ] Every order belongs to one customer and one performance and contains at least one ticket.
- [ ] Each ticket records its face value at the time of sale.
- [ ] A ticket identifies either a reserved seat or a general-admission section allocation.
- [ ] Customer and organizer cancellations, refunds, and their history are retained.
- [ ] Resale listings enforce the event cap and support active, sold, and withdrawn states.
- [ ] Every ticket retains its complete ownership history.
- [ ] Reviews store free-form comments plus separate event and venue ratings from 1-5.
- [ ] Review eligibility requires a past, non-cancelled attended performance and is limited to once per customer/performance.
- [ ] The chosen meaning of “recently attended” is documented as a project assumption.

### Operations to support

- [x] Create customer and organizer profiles while collecting all required information.
- [x] Delete users according to a documented history-preserving policy.
- [x] Create an event and associate its organizer, taxonomy, artists, and billing order.
- [x] Add performances to an event.
- [x] Define performance price tiers and prices.
- [x] Assign every venue section to one tier for the performance.
- [x] Set the event resale cap.
- [x] Update a future tier price only when no ticket has been sold in that tier.
- [x] Inform the organizer when a tier-price change is rejected.
- [x] Block only an available seat for a performance and unblock it later.
- [x] Reject blocking a sold seat.
- [x] Book available reserved seats without double-selling.
- [x] Book general-admission tickets without exceeding capacity.
- [x] Reject the entire booking if any requested inventory is unavailable.
- [ ] Allow only the customer who placed the order to cancel its eligible tickets.
- [ ] Enforce the seven-day customer cancellation deadline and full refund.
- [ ] Allow only the event organizer to cancel its performance.
- [ ] Refund every active ticket when a performance is cancelled.
- [ ] Restore or close inventory consistently and retain cancellation history.
- [ ] List only a currently owned ticket at or below its resale cap.
- [ ] Withdraw an unsold resale listing.
- [ ] Purchase another customer’s active listing and transfer ownership atomically.
- [ ] Insert eligible event/venue reviews and reject ineligible or duplicate reviews.
- [ ] Flag and prohibit customers who satisfy the possible-scalper rule.

### Queries Q1-Q7

- [ ] **Q1:** Accept latitude/longitude plus a user-selectable distance with a documented default.
- [ ] **Q1:** Return nearby upcoming performances ranked by the chosen distance calculation.
- [ ] **Q1:** Alternatively rank by cheapest available ticket, ascending or descending.
- [ ] **Q2:** Return upcoming performances in the same and adjacent postal codes.
- [ ] **Q2:** Document how postal-code adjacency is determined.
- [ ] **Q3:** Find a venue by exact address and return its upcoming performances.
- [ ] **Q4:** Apply date-range and minimum-ticket-availability refinement to the location searches.
- [ ] **Q5:** Support city, segment, genre, date, cheapest-price range, minimum availability, reserved seating, and general admission in any combination.
- [ ] **Q6:** For every section report tier, price, available count/remaining capacity, sold count, and blocked count.
- [ ] **Q7:** Given performance, quantity `q`, and optional budget, return the lowest-total-price `q` consecutive seat numbers in one row.

### Reports R1-R9

All reports must be implemented in SQL and invoked from Java. R9 may use Java text processing only for noun-phrase extraction.

- [ ] **R1:** Ticket count and gross revenue for a date range by city and by venue within a city.
- [ ] **R2:** Event/performance totals per segment and genre at country, country/city, and country/city/venue levels.
- [ ] **R3:** Organizer gross-revenue rankings overall, per country, and optionally by city.
- [ ] **R4:** For every city, customers who bought at least 10 tickets and listed more than half within the past year.
- [ ] **R5:** Customer order rankings for a time period and by venue city; city ranking includes customers with at least two orders in the year.
- [ ] **R6:** Customers with the most cancelled tickets and organizers with the most cancelled performances within a year.
- [ ] **R7:** Performance and tier sell-through using sellable capacity, excluding blocked seats and including general-admission capacity.
- [ ] **R7:** For a given month/city, report sold-out performances and those below 25% sell-through.
- [ ] **R8:** Per-event completed resales, average markup over face value, and fraction of listings exactly at the cap.
- [ ] **R8:** Top 10 events by resale volume in a requested period.
- [ ] **R9:** Most popular noun phrases for each event; no word-cloud visualization is required.

### Organizer toolkit

- [ ] Provide a working function that suggests the number of tiers.
- [ ] Suggest a price for each tier.
- [ ] Suggest the share of venue capacity assigned to each tier.
- [ ] Use documented comparable-performance criteria such as genre, venue capacity, city, and recent dates.
- [ ] Document and justify the algorithm, assumptions, insufficient-data fallback, and an example result.
- [ ] Optional extra credit: estimate the expected revenue change from a suggested tier-price change.

### Required sample data

- [x] Generated data uses plausible real city/postal-code names, sensible coordinates, prices, and dates and loads reasonably quickly.
- [x] At least 8 venues across at least 4 cities and 2 countries with realistic coordinates and addresses.
- [x] At least 3 nearby venues in the same or adjacent postal codes.
- [x] Venues vary in size and every venue has several sections.
- [x] At least 2 venues have both reserved and general-admission sections.
- [x] At least 20 events managed by at least 5 organizers.
- [x] At least 3 segments, 6 genres, and 15 artists or teams.
- [x] Some events have multiple artists with different billing orders.
- [x] At least 60 past and upcoming performances.
- [x] Include a touring event at different venues.
- [x] Include a theatre-style event with many performances at one venue.
- [x] At least one venue hosts two performances with different tier assignments and prices.
- [x] Every performance has at least 2 price tiers.
- [x] Include an upcoming tier with no sales and another with sales to test both tier-price outcomes.
- [x] Include performance-specific blocked seats.
- [x] At least 100 adult customers with fictional personal/card information.
- [x] At least 300 orders containing at least 800 tickets across the past 12 months and the future.
- [x] Several customers have at least 2 recent orders in more than one city.
- [x] Include past sold-out performances across several months/cities.
- [x] Include past performances below 25% sell-through across several months/cities.
- [x] Include a performance more than 7 days away with reserved and general-admission availability.
- [x] Include a row with at least 4 consecutive available seats.
- [x] Include a row with only nonconsecutive available seats.
- [x] Include a performance fewer than 7 days away with sold tickets.
- [x] Include cancellations by several customers.
- [x] Include at least 2 organizer-cancelled performances within the past year.
- [x] Include sold, withdrawn, and currently active resale listings.
- [x] Include listings priced exactly at the resale cap.
- [x] Include at least one ticket that changes owners twice.
- [x] Include at least 2 customers who bought 10 or more tickets and listed more than half within the past year.
- [x] Include several reviews for at least 10 events.
- [x] Review comments contain several meaningful sentences for R9.
- [x] `sql/load.sql` loads the complete dataset in one execution with no manual preparation.

### Submission and implementation requirements

- [ ] Final archive is named after the group, for example `mytix_lastname1_lastname2.zip`.
- [ ] Application uses Java embedded SQL with MySQL 8.
- [ ] Terminal interface exposes every required operation, query, report, and toolkit function.
- [ ] `sql/schema.sql` creates all tables and constraints in one execution.
- [ ] `sql/drop.sql` drops the complete schema safely.
- [x] `sql/load.sql` loads all sample data in one execution.
- [ ] `run.sh` compiles and runs the application with one command.
- [ ] The extensionless `README` contains only names, student numbers, and `run.sh`.
- [ ] Reasonable assumptions are documented and do not conflict with the PDF.
- [ ] Both members understand the full codebase and record their contributions.

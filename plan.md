# MyTix Project Plan

**Project period:** July 29-August 7  
**Team size:** Two members  
**Delivery date:** August 7

## Assumptions

- `sql/schema.sql` already creates the complete MySQL 8 schema.
- Both members must approve schema changes because the Java application, SQL queries, reports, and sample data depend on it.
- The priority is a complete and reliable text-based terminal application.
- All operations, queries, and reports use SQL invoked from Java, except the noun-phrase processing for R9.
- Member A and Member B are primary owners, but both members must review and understand the complete project.

Use [checklist.md](checklist.md) as the acceptance checklist for each implementation task. A job is complete only when its terminal route works, its SQL is parameterized, its required acceptance/rejection behavior is demonstrated with the sample data, and the related checklist items can be marked complete.

Keeping [business logic constraint map](docs/business-logic-constraints.md) in mind when building and testing the schema and operations without overlooking a rule. 

## Primary ownership

### Member A — transactions and inventory

- JDBC transaction and rollback utilities
- Customer profile creation and deletion
- Reserved-seat and general-admission booking
- Customer ticket cancellation and refunds
- Organizer performance cancellation and refunds
- Resale listing, withdrawal, purchase, and ownership transfer
- Possible-scalper prohibition
- Q6 seat-map summary and Q7 best consecutive seats
- R4 possible scalpers, R5 customer orders, R6 cancellations, R7 sell-through, and R8 resale
- Primary author of `manual.pdf`

### Member B — organizer tools, discovery, and analytics

- Organizer profile, event, artist, and performance setup
- Price tiers, section assignments, resale caps, and tier-price updates
- Seat blocking and unblocking
- Attendance-based reviews and ratings
- Q1-Q5 location, date, availability, and filter searches
- R1 sales, R2 event counts, R3 organizer revenue, and R9 noun phrases
- Organizer pricing and tier-structure toolkit
- Sample-data generator and `sql/load.sql`
- Primary author of `report.pdf`

### Shared responsibilities

- Review schema constraints, indexes, foreign keys, and transaction requirements.
- Use prepared statements for all user-provided values.
- Maintain stable test IDs and expected results.
- Integrate completed features into the terminal.
- Review and test the other member's work.
- Run the grading sequence on a clean MySQL 8 database.
- Be able to explain the entire project.

## Workload balance

| Work block | Member A focus | Member B focus |
|---|---|---|
| July 29-31 | JDBC transactions, customer operations, inventory lookups, and transaction-side development data | Data dictionary, generator foundation, organizer/event operations, and pricing foundation |
| August 1-3 | Booking, cancellation, resale, scalper control, Q6, and Q7 | Event/pricing completion, blocking, reviews, and Q1-Q5 |
| August 4-6 | R4-R8, transaction-heavy final data, transaction/report terminal integration, manual lead, and assigned report sections | R1-R3/R9, toolkit, base data/load integration, organizer/search terminal integration, report lead, and assigned manual sections |
| August 7 | Terminal/manual verification | Database/report/data verification |

## Milestones

| Date | Required result |
|---|---|
| July 31 | Schema contract, Java foundation, development data, and document outlines are ready. |
| August 3 | All required operations and Q1-Q7 are implemented and integrated. |
| August 6 | R1-R9, toolkit, final sample data, `report.pdf`, and `manual.pdf` are complete. |
| August 7 | Final archive passes the grading sequence and is delivered. |


## July 29-31 — schema contract and application foundation

### Member A

- [x] **Map the database tables used by transaction features.** Cover users, customers, payments, orders, tickets, inventory, cancellations, refunds, resale, and ownership history.
  - [x] Record the table, key, and status columns each operation will use.
  - [x] Identify the records that must already exist before inserting orders, tickets, cancellations, listings, or ownership history.
  - [x] Identify which inventory, ticket, listing, and ownership rows require `SELECT ... FOR UPDATE`.
- [x] **Build the shared JDBC transaction helper.**
  - [x] Use the active connection from `DatabaseConnection`.
  - [x] Turn off auto-commit before an operation with several database changes.
  - [x] Commit only when every change succeeds.
  - [x] Roll back when validation or SQL fails, then restore the previous auto-commit setting.
  - [x] Show clear terminal errors without exposing passwords or raw stack traces.
- [x] **Agree on one result format for all operations.**
  - [x] Handle success, invalid input, not found, no permission, and database failure consistently.
  - [x] Return a clear message plus any IDs or rows the terminal needs to display.
- [x] **Build customer profile operations and terminal screens.**
  - [x] Collect name, address, email, date of birth, and fictional card/payment information.
  - [x] Check required fields and confirm the customer is at least 18.
  - [x] Save the user, customer, and payment records in one transaction.
  - [x] Retrieve a customer profile without displaying a full card number.
  - [x] Follow the agreed delete/deactivate rule without losing purchase history.
- [x] **Build reusable inventory lookups.**
  - [x] For reserved seating, return section, row, seat number, tier, price, and available/sold/blocked status for one performance.
  - [x] For general admission, return tier, price, total capacity, sold quantity, and remaining capacity.
  - [x] Exclude cancelled performances from saleable results.
- [x] **Check the foundation with known examples.**
  - [x] Successful and rejected customer creation
  - [x] Under-18 profiles
  - [x] Commit and rollback behavior
  - [x] Reserved and general-admission availability calculations
- [x] **Add repeatable transaction data for development.**
  - [x] Create stable customer, payment, order, ticket, cancellation, listing, and ownership IDs.
  - [x] Add one known reserved order and one known general-admission order for early transaction checks.
  - [x] Coordinate foreign keys and generation order with Member B’s venue/event/performance records.

### Member B

- [x] **Map the database tables used by organizer and discovery features.** Cover venues, seating, organizers, events, artists, performances, pricing, blocked seats, taxonomy, and reviews.
  - [x] Record each table’s primary key, foreign keys, and status columns.
  - [x] Confirm how reserved and general-admission sections are distinguished.
  - [x] Confirm how a tier is tied to its performance and how every performance section is assigned once.
- [x] **Create the shared data dictionary.**
  - [x] List column types, required/optional fields, unique rules, indexes, and allowed statuses.
  - [x] Note the indexes needed for location/date searches, availability, ownership, reports, and date ranges.
  - [x] Record any schema correction that both members approve.
- [x] **Build the repeatable sample-data generator foundation.**
  - [x] Use fixed IDs or a fixed seed so both members can use the same records.
  - [x] Generate data in foreign-key-safe order.
  - [x] Produce files or statements that `sql/load.sql` can load without manual edits.
  - [x] Add the venue, event, performance, tier, and inventory portion of the development dataset, including reserved/general-admission sections and future/past performances.
  - [x] Integrate Member A’s customer, order, ticket, and ownership records into the same deterministic dataset.
- [x] **Build organizer profile operations and terminal screens.**
  - [x] Collect the required user fields and organizer information.
  - [x] Check age and required fields.
  - [x] Save the user and organizer records in one transaction.
  - [x] Follow the agreed delete/deactivate rule without losing event history.
- [x] **Build the first event and performance setup operations.**
  - [x] Create an event for the selected organizer.
  - [x] Require a valid Ticketmaster segment and genre.
  - [x] Store the resale-cap value.
  - [x] Add one or more artists/teams with explicit billing order.
  - [x] Add a performance with venue, date, and time.
- [x] **Start performance pricing.**
  - [x] Create at least two named price tiers with positive prices.
  - [x] Load every section belonging to the performance venue.
  - [x] Begin assigning each section to exactly one tier for that performance.
- [x] **Check the foundation with known examples.**
  - [x] Organizer and event creation
  - [x] Invalid taxonomy or venue IDs
  - [x] Duplicate artist billing order
  - [x] Missing or duplicate section-to-tier assignments

### Shared work

- [x] Execute `sql/schema.sql` on an empty MySQL 8 database.
- [x] Compare the schema with `docs/business-logic-constraints.md`.
- [x] Agree on column names, time zone, money representation, status values, and history/deletion behavior.
- [x] Select stable test IDs for:
  - [x] One organizer
  - [x] Two customers
  - [x] One reserved-seating performance
  - [x] One general-admission performance
  - [x] One past performance
- [x] Confirm the Java application connects through Connector/J.
- [x] Add terminal routes for completed operations.
- [x] Create outlines for `report.pdf` and `manual.pdf`.

### Block exit criteria

- [ ] Both members approve the schema contract.
- [ ] Both members can run the application against the same development data.
- [x] Customer, organizer, event, performance, tier, and inventory records can be created or retrieved.
- [x] Database transactions can commit and roll back correctly.
- [x] No implementation depends on an unresolved schema question.

## August 1-3 — all operations and Q1-Q7

### Member A

- [ ] **Build reserved-seat booking as one transaction.**
  - [ ] Accept customer, performance, and one or more requested seat IDs.
  - [ ] Reject cancelled or past performances and prohibited customers.
  - [ ] Lock requested seats in a consistent order so two customers cannot buy the same seat.
  - [ ] Check that every seat is available and not blocked before creating the order.
  - [ ] Save the order, payment snapshot, tickets, original prices, and first ownership records.
  - [ ] Mark every requested seat sold; roll back the whole booking if one seat fails.
- [ ] **Build general-admission booking as one transaction.**
  - [ ] Accept customer, performance, section, and requested quantity.
  - [ ] Lock the performance-section inventory row.
  - [ ] Check the requested quantity against the remaining capacity.
  - [ ] Save the order, tickets, and ownership records and update capacity together.
- [ ] **Build customer ticket cancellation.**
  - [ ] Lock the order, ticket, current ownership, performance, active listing, and inventory rows involved.
  - [ ] Confirm the customer placed the order and the performance is at least seven days away.
  - [ ] Record the cancellation and full refund instead of deleting history.
  - [ ] Withdraw any active listing and return reserved or general-admission inventory.
- [ ] **Build organizer performance cancellation.**
  - [ ] Confirm the organizer manages the event.
  - [ ] Lock and mark the performance cancelled.
  - [ ] Close active resale listings and refund every active ticket.
  - [ ] Preserve cancellation history and keep the cancelled performance unsellable.
- [ ] **Build the complete resale workflow.**
  - [ ] List only a currently owned, active, non-cancelled ticket.
  - [ ] Calculate the maximum listing price from face value and the event resale cap.
  - [ ] Allow the seller to withdraw only an active unsold listing.
  - [ ] When sold, lock the listing, ticket, and ownership records; reject the seller as buyer; save the resale order/payment; end old ownership; add new ownership; and mark the listing sold.
  - [ ] Ensure two buyers cannot complete the same listing.
- [ ] **Add the possible-scalper check.**
  - [ ] Calculate the past-year purchased and listed counts.
  - [ ] Flag customers who bought at least 10 tickets and listed more than half.
  - [ ] Block the prohibited actions chosen by the design and show a clear terminal message.
- [ ] **Build Q6: seat-map summary.**
  - [ ] Accept a performance ID.
  - [ ] Return every section with tier, price, available/remaining capacity, sold, and blocked counts.
  - [ ] Handle reserved and general-admission sections in one consistent result.
- [ ] **Build Q7: best consecutive seats.**
  - [ ] Accept performance ID, quantity `q`, and optional budget.
  - [ ] Find consecutive numeric seats in the same row.
  - [ ] Exclude sold and blocked seats.
  - [ ] Return the qualifying group with the lowest total tier price, or a clear no-result message.
- [ ] Demonstrate the required booking, cancellation, resale, Q6, and Q7 success/rejection behavior before marking each operation complete.

### Member B

- [ ] **Finish event and performance setup.**
  - [ ] Require organizer ownership, valid segment/genre, at least one artist/team, and billing order.
  - [ ] Add performances with a valid venue, future/past date-time as appropriate, and active status.
  - [ ] Set and update the event resale-cap value.
  - [ ] Return created event/performance IDs to the terminal.
- [ ] **Finish performance pricing.**
  - [ ] Create named tiers with positive prices.
  - [ ] Assign every section of the selected venue to exactly one tier for that performance.
  - [ ] Reject missing, duplicate, cross-performance, or cross-venue assignments.
  - [ ] Display the completed tier/section map for confirmation.
- [ ] **Build the safe tier-price update.**
  - [ ] Lock the tier and relevant ticket rows.
  - [ ] Confirm the performance is in the future.
  - [ ] Reject the update if any ticket has been sold from the tier and explain why.
  - [ ] Update and commit only when both conditions pass.
- [ ] **Build seat blocking and unblocking.**
  - [ ] Accept performance and reserved-seat IDs.
  - [ ] Lock the performance-seat inventory row.
  - [ ] Block only an available seat, reject a sold seat, and allow only a blocked seat to be unblocked.
  - [ ] Retain performance-specific status so the physical seat can differ across performances.
- [ ] **Build attendance reviews.**
  - [ ] Accept customer, performance, event rating, venue rating, and free-form comment.
  - [ ] Confirm the performance occurred within the documented “recent” period.
  - [ ] Confirm the customer held a non-cancelled ticket for that performance.
  - [ ] Enforce ratings from 1-5 and one review per customer/performance.
  - [ ] Save the event rating, venue rating, and comment together.
- [ ] **Build Q1: nearby performances.**
  - [ ] Accept latitude, longitude, optional search distance, and sort choice.
  - [ ] Apply the documented default distance when none is supplied.
  - [ ] Return upcoming performances within range.
  - [ ] Support distance ranking and cheapest-available-price ranking in ascending or descending order.
- [ ] **Build Q2: postal-code search.**
  - [ ] Accept a postal code and apply the documented same/adjacent-postal-code rule.
  - [ ] Return upcoming performances with venue and date/time information.
- [ ] **Build Q3: exact-address search.**
  - [ ] Accept an exact address.
  - [ ] Return the matching venue and its upcoming performances or a clear no-result message.
- [ ] **Build Q4: date and availability refinement.**
  - [ ] Accept start date, end date, and minimum available quantity.
  - [ ] Reject invalid ranges and filter out performances without enough inventory.
- [ ] **Build Q5: combined filters.**
  - [ ] Support city, segment, genre, date range, cheapest-ticket price range, minimum availability, reserved seating, and general admission in any combination.
  - [ ] Use prepared parameters for values and safely add only the selected filters.
  - [ ] Ensure cheapest price and availability include both reserved and general-admission inventory.
- [ ] **Check event setup, pricing, blocking, reviews, and each Q1-Q5 sort/filter path with known data.**

### Shared work

- [ ] Integrate every operation and query into the terminal.
- [ ] Test two customers attempting to buy the same reserved seat.
- [ ] Test general-admission requests below, equal to, and above remaining capacity.
- [ ] Test failed multi-seat booking and confirm the entire order rolls back.
- [ ] Test customer cancellation more than and fewer than seven days before the performance.
- [ ] Verify a cancelled performance cannot accept new bookings.
- [ ] Test two buyers attempting to purchase the same resale listing.
- [ ] Verify Q6 totals against raw inventory counts.
- [ ] Test Q7 success, budget failure, insufficient quantity, and nonconsecutive-seat cases.
- [ ] Cross-review all ownership, authorization, and transaction checks.

### Block exit criteria

- [ ] All required operations are implemented and reachable from the terminal.
- [ ] Reserved and general-admission inventory cannot be oversold.
- [ ] Cancellation, resale, and ownership-history rules work.
- [ ] Review and possible-scalper restrictions work.
- [ ] Q1-Q7 return verified results.

## August 4-6 — reports, toolkit, sample data, integration, and documents

### Member A

- [ ] **Build R4: possible scalpers by city.**
  - [ ] Group by venue city.
  - [ ] Use the past-year purchase/listing window.
  - [ ] Return customers who purchased at least 10 tickets and listed more than half.
  - [ ] Make sure the result matches the application’s scalper flag/prohibition logic.
- [ ] **Build R5: customer order rankings.**
  - [ ] Rank customers by order count for a requested period.
  - [ ] Rank customers by the city of the performance venue.
  - [ ] Apply the at-least-two-orders-in-the-year rule to the city ranking.
  - [ ] Add terminal period/city inputs, headings, and empty-result handling.
- [ ] **Build R6: cancellation rankings.**
  - [ ] Use the required one-year reporting window.
  - [ ] Rank customers by cancelled-ticket count.
  - [ ] Rank organizers by cancelled-performance count.
  - [ ] Keep customer and organizer results clearly separated.
- [ ] **Build R7: sell-through reports.**
  - [ ] Calculate sellable capacity with blocked reserved seats excluded and general-admission capacity included.
  - [ ] Report sell-through per performance and per performance tier.
  - [ ] Accept month and city for sold-out and below-25% performance results.
  - [ ] Handle zero-capacity cases safely.
- [ ] **Build R8: resale reports.**
  - [ ] Report completed resale count per event.
  - [ ] Calculate average markup over face value.
  - [ ] Calculate the fraction of listings priced exactly at the cap.
  - [ ] Accept a period for the top 10 events by resale volume.
- [ ] **Finish the R4-R8 terminal screens.**
  - [ ] Add prompts and check date, city, and period inputs.
  - [ ] Use clear headings, money/percentage formatting, and empty-result messages.
- [ ] **Build the transaction-heavy part of the final sample data.**
  - [ ] Add reserved and general-admission orders with known face values and inventory totals.
  - [ ] Add eligible and ineligible customer-cancellation examples.
  - [ ] Add at least two organizer-cancelled performances with refunded tickets.
  - [ ] Add active, withdrawn, sold, and exactly-at-cap resale listings.
  - [ ] Add one ticket that changes owners twice.
  - [ ] Add two customers who meet the possible-scalper threshold.
  - [ ] Add the reserved rows needed for the consecutive and nonconsecutive Q7 cases.
  - [ ] Give Member B the generated records and stable IDs needed by `load.sql`.
- [ ] **Finish transaction-side terminal integration.**
  - [ ] Check that profile, booking, cancellation, resale, Q6, Q7, and R4-R8 screens accept the documented inputs.
  - [ ] Use consistent headings, money/percentage output, empty results, and validation messages.
  - [ ] Confirm the final data produces the expected inventory, cancellation, resale, and report totals.
- [ ] **Write expected results** for transaction operations, Q6-Q7, and R4-R8 using stable IDs.
- [ ] **Write and finalize `manual.pdf`.**
  - [ ] Document prerequisites and exact startup sequence.
  - [ ] Explain every terminal menu option and required input.
  - [ ] Provide example success and rejection flows for booking, cancellation, resale, and reviews.
  - [ ] Explain inputs and output columns for Q1-Q7, R1-R9, and the toolkit.
  - [ ] Include connection troubleshooting, limitations, improvements, and one complete example session.
- [ ] **Write Member A’s sections of `report.pdf`.**
  - [ ] Customer, order, and ticket operation design
  - [ ] Booking, cancellation, and resale transaction/concurrency decisions
  - [ ] Q6-Q7 and R4-R8 implementation highlights
  - [ ] Transaction-related limitations and Member A contribution notes

### Member B

- [ ] **Build R1: sales and revenue.**
  - [ ] Accept a date range.
  - [ ] Return total sold-ticket count and gross revenue by city.
  - [ ] Support the venue-within-city breakdown.
- [ ] **Build R2: event and performance counts.**
  - [ ] Count events and performances per segment and genre.
  - [ ] Produce country, country/city, and country/city/venue rollups.
- [ ] **Build R3: organizer revenue rankings.**
  - [ ] Rank organizers by gross revenue overall and per country.
  - [ ] Support optional city refinement.
- [ ] **Build R9: popular noun phrases.**
  - [ ] Query comments grouped by event.
  - [ ] Extract noun phrases with the chosen Java text-processing approach.
  - [ ] Rank or count phrases and display the most popular set per event.
  - [ ] Do not spend time building a visualization.
- [ ] **Finish the R1-R3 and R9 terminal screens.**
  - [ ] Add prompts and check date, location, and grouping inputs.
  - [ ] Use clear headings, money formatting, and empty-result messages.
- [ ] **Build the organizer toolkit.**
  - [ ] Find comparable performances using genre, venue capacity, city, and recent dates.
  - [ ] Suggest tier count, a price for each tier, and capacity share per tier.
  - [ ] Ensure capacity shares total 100%.
  - [ ] Provide a fallback when comparable data is insufficient.
  - [ ] Explain which comparable performances influenced the recommendation.
  - [ ] Attempt expected-revenue-change estimation only after all required features pass.
- [ ] **Finish the repeatable sample-data generator and `sql/load.sql`.**
  - [ ] Generate every count and edge case in `checklist.md`, including Member A’s transaction records.
  - [ ] Keep the load order safe for foreign keys and preserve stable IDs.
  - [ ] Use plausible locations, dates, prices, statuses, and fictional customer/card data.
  - [ ] Add count checks for venues, events, performances, customers, orders, tickets, cancellations, listings, and reviews.
  - [ ] Confirm `load.sql` succeeds in one execution on a clean schema.
- [ ] **Finish organizer, search, report, and toolkit terminal integration.**
  - [ ] Check that organizer/event/pricing, blocking, review, Q1-Q5, R1-R3, R9, and toolkit screens accept the documented inputs.
  - [ ] Use consistent date/location filters, table headings, empty results, and validation messages.
  - [ ] Confirm the final location, review, report, and toolkit data produces the documented results.
- [ ] **Write expected results** for organizer/pricing/review operations, Q1-Q5, R1-R3, R9, and the toolkit using stable IDs.
- [ ] **Write and finalize `report.pdf`.**
  - [ ] Document purpose, conceptual problems, assumptions, and justified decisions.
  - [ ] Include the ER diagram, relational schema, keys, functional dependencies, normalization, decompositions, and DDL constraints.
  - [ ] Explain concurrency protection for booking, cancellation, and resale.
  - [ ] Explain Q1-Q7, R1-R9, sample-data strategy, and the toolkit algorithm.
  - [ ] Record each member’s contribution.
- [ ] **Write Member B’s sections of `manual.pdf`.**
  - [ ] Organizer, event, performance, pricing, blocking, and review instructions
  - [ ] Q1-Q5, R1-R3, R9, and toolkit input/output descriptions
  - [ ] Location/search assumptions and analytics/toolkit validation messages

### Shared work

- [ ] Hand-calculate small expected result sets for R1-R9 and compare them with SQL output.
- [ ] Demonstrate every operation, Q1-Q7, R1-R9, and the organizer toolkit.
- [ ] Execute:
  1. `sql/drop.sql`
  2. `sql/schema.sql`
  3. `sql/load.sql`
  4. `run.sh`
- [ ] Confirm every feature is testable immediately after `load.sql`.
- [ ] Cross-review `report.pdf` and `manual.pdf` against the assignment PDF.
- [ ] Fix integration, data, and documentation inconsistencies.
- [ ] Freeze code, data, and documents by the end of August 6.

### Block exit criteria

- [ ] R1-R9 are complete and verified.
- [ ] The organizer toolkit produces documented suggestions.
- [ ] Sample data satisfies every minimum and supports every test case.
- [ ] The full terminal application works after a clean schema and data load.
- [ ] `report.pdf` and `manual.pdf` are complete.
- [ ] Only final packaging and delivery remain.

## August 7 — final verification and delivery

### Member A

- Test the terminal exactly as a TA would.
- Confirm `manual.pdf` matches the final interface.
- Verify the extensionless `README` contains names, student numbers, and `run.sh`.
- Confirm validation and error messages are understandable.

### Member B

- Prepare a clean MySQL 8 instance.
- Confirm `report.pdf`, the ER diagram, relation schemas, dependencies, and SQL files agree.
- Confirm all sample-data paths work after archiving.
- Verify report results against the expected-result checklist.

### Shared work

- Run the official grading sequence twice:
  1. Execute `sql/schema.sql`.
  2. Execute `sql/load.sql`.
  3. Execute `run.sh`.
  4. Test all operations, Q1-Q7, R1-R9, and the organizer toolkit.
- Verify the final archive contains:
  - `report.pdf`
  - `manual.pdf`
  - `sql/schema.sql`
  - `sql/drop.sql`
  - `sql/load.sql`
  - `data/`
  - `src/`
  - `lib/mysql-connector-java-8.0.29.jar`
  - `run.sh`
  - `README`
- Confirm the archive excludes `.build`, `.class`, credentials, editor settings, and unrelated files.
- Extract the archive into a new directory and repeat the grading sequence.
- Record each member's contribution.
- Deliver only after both members approve the extracted archive.

### Delivery criteria

- The grading sequence passes twice without manual data repair or code changes.
- Both PDFs are complete and readable.
- Both members can explain the entire project.
- The extracted archive is reproducible and ready for delivery.

## `report.pdf` checklist

**Primary author:** Member B  
**Required reviewer:** Member A

- [ ] Group-member names and student numbers
- [ ] Project purpose
- [ ] Conceptual problems and justified solutions
- [ ] Clearly stated assumptions
- [ ] ER diagram with attributes, entities, relationships, and primary keys
- [ ] Relation schemas and all keys
- [ ] Nontrivial functional dependencies
- [ ] 3NF/BCNF justification and decompositions
- [ ] DDL types, constraints, indexes, and concurrency decisions
- [ ] DDL statements that create the complete schema
- [ ] Operation and transaction design
- [ ] Q1-Q7 design highlights
- [ ] R1-R9 design highlights
- [ ] Organizer-toolkit strategy and justification
- [ ] Design limitations and tradeoffs
- [ ] Contribution accounting for both members

## `manual.pdf` checklist

**Primary author:** Member A  
**Required reviewer:** Member B

- [ ] Prerequisites and supported MySQL/Java versions
- [ ] Exact database initialization and `run.sh` instructions
- [ ] Terminal navigation conventions
- [ ] Instructions for every operation
- [ ] Inputs and output interpretation for Q1-Q7
- [ ] Inputs and output interpretation for R1-R9
- [ ] Organizer-toolkit instructions
- [ ] Expected validation and rejection messages
- [ ] Database connection troubleshooting
- [ ] System limitations
- [ ] Possible improvements
- [ ] One complete example workflow from startup to exit

## Working rules

1. Keep commits focused on one operation, query/report group, data change, or document section.
2. Do not change shared table, column, or status names without notifying the other member.
3. Add or update a repeatable test whenever a defect is fixed.
4. Review the other member's work during each work block.
5. Integrate completed work before the end of every block.
6. Record completed work, blockers, and next priorities at each checkpoint.

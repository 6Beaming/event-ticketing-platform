# MyTix

MyTix is a database design project for an event ticketing platform similar to Ticketmaster. The finished system will let organizers manage events, performances, venues, inventory, pricing, and sales while customers buy, cancel, resell, and review tickets.

## Core data requirements

The final relational design must represent:

- Venues, geographic coordinates, addresses, and postal codes
- Reserved-seating and general-admission sections
- Rows and numbered seats for reserved sections
- Organizers, customers, profiles, addresses, and payment information
- Events, Ticketmaster segments and genres, artists or teams, and billing order
- Performances at a venue on a specific date and time
- Performance-specific price tiers and section-to-tier assignments
- Performance-specific blocked seats
- Orders, tickets, face values, and general-admission inventory
- Customer and organizer cancellations and refunds
- Resale listings, resale caps, transfers, and complete ticket ownership history
- Attendance-based event and venue reviews with ratings and comments

## Required operations

- Create and delete user profiles.
- Create events and performances.
- Define price tiers and assign venue sections to them per performance.
- Change an unsold future tier's price and reject changes after a ticket in that tier is sold.
- Block or unblock an available seat without allowing a sold seat to be blocked.
- Book available reserved seats or general-admission capacity without double-selling inventory.
- Cancel eligible tickets at least seven days before a performance and retain cancellation history.
- Cancel an entire performance and refund every sold ticket.
- List an owned ticket for resale at or below the event cap, withdraw a listing, and purchase another customer's listing.
- Preserve every ticket ownership transfer.
- Submit one eligible event and venue review per attended performance.
- Flag and prohibit customers who meet the project's possible-scalper rule.

## Required queries

- **Q1:** Find nearby upcoming performances by latitude and longitude, ranked by distance or cheapest available ticket price.
- **Q2:** Find upcoming performances in the same or adjacent postal codes.
- **Q3:** Find a venue by exact address and list its upcoming performances.
- **Q4:** Refine location searches by date range and minimum ticket availability.
- **Q5:** Combine city, segment, genre, date, price, availability, reserved-seating, and general-admission filters.
- **Q6:** Produce a section-level seat-map summary with tier, price, available, sold, and blocked counts.
- **Q7:** Find the lowest-priced set of consecutive seats in one row for a requested quantity and optional budget.

## Required reports

- **R1:** Ticket sales and gross revenue by city and by venue for a date range.
- **R2:** Event and performance counts by segment, genre, country, city, and venue.
- **R3:** Organizer revenue rankings overall, by country, and optionally by city.
- **R4:** Possible ticket scalpers by city during the past year.
- **R5:** Customer order rankings overall and by city for a time period.
- **R6:** Customers with the most cancelled tickets and organizers with the most cancelled performances in a year.
- **R7:** Performance and price-tier sell-through rates, including monthly city summaries for sold-out and low-selling performances.
- **R8:** Resale volume, average markup, cap-priced listing share, and the top ten events by resale volume.
- **R9:** Popular noun phrases from comments for each event.

## Organizer toolkit

The organizer toolkit will suggest price tiers, prices, and capacity shares for a new performance using comparable performances. The final strategy must be documented and justified. Estimating the expected revenue effect of a proposed tier price change is optional extra credit.

## Repository structure

```text
mytix/
├── README                  # Names, student numbers, and execution instructions (run.sh)
├── run.sh                  # Top-level shell script to compile and run the Java app
├── report.pdf              # Comprehensive project report (ER diagram, schema, dependencies)
├── manual.pdf              # User manual, system limitations, and improvement ideas
│
├── sql/
│   ├── schema.sql          # DDL file to create all tables and constraints on MySQL 8
│   ├── drop.sql            # Script to drop all tables, views, and constraints
│   └── load.sql            # Script to bulk load sample data into the database
│
├── data/                   # Sample data files (CSV, TXT, or SQL files used by load.sql)
│   ├── venues.csv
│   ├── events.csv
│   ├── performances.csv
│   └── ...
│
└── src/                    # Full Java source code of your application
    ├── Main.java           # Entry point for the application
    ├── database/           # JDBC connections and database utility classes
    ├── operations/         # User, organizer, customer, and booking logic
    ├── queries/            # Q1–Q7 SQL search query implementations
    └── reports/            # R1–R9 SQL report generators and text analysis
```

## Sample data targets

The final bulk-load data must include at least:

- 8 venues across 4 cities and 2 countries
- 20 events, 5 organizers, 3 segments, 6 genres, and 15 artists
- 60 past and upcoming performances
- 2 price tiers per performance
- 100 customers, 300 orders, and 800 tickets
- Sold-out and below-25%-sold past performances
- Reserved and general-admission availability, blocked seats, and consecutive-seat test cases
- Customer and organizer cancellations
- Sold, withdrawn, and active resale listings, including repeat transfers and cap-priced listings
- Reviews for 10 events with multiple meaningful comments per event


## Required grading sequence

The finished project must work without manual intervention when the grader executes:

1. `sql/schema.sql`
2. `sql/load.sql`
3. `run.sh`

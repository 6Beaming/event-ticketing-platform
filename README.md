# MyTix

MyTix is a database design project for an event ticketing platform similar to Ticketmaster. The  system will let organizers manage events, performances, venues, inventory, pricing, and sales while customers buy, cancel, resell, and review tickets.

## Prerequisites

- MySQL 8 installed and running.
- A JDK with `java` and `javac` available.
- `lib/mysql-connector-java-8.0.29.jar` (MySQL JDBC driver). *Java's standard `java.sql` API does not natively understand the MySQL protocol. Connector/J provides that MySQL-specific implementation so `DriverManager` can open the JDBC connection and send SQL to MySQL.*

Keeping [business logic constraint map](docs/business-logic-constraints.md) in mind when building and testing the schema and operations without overlooking a rule. 

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
├── docs/
│   └── business-logic-constraints.md
├── lib/
│   └── mysql-connector-java-8.0.29.jar
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
    ├── database/           # JDBC configuration and connection lifecycle
    ├── operations/         # Profile, organizer, booking, cancellation, resale, and review logic
    ├── queries/            # Q1–Q7 SQL search query implementations
    ├── reports/            # R1–R9 SQL report generators and text analysis
    └── ui/                 # Persistent text-based terminal loop
```


## Required grading sequence

The finished project must work without manual intervention when the grader executes:

1. `sql/schema.sql`
2. `sql/load.sql`
3. `run.sh`

### Run the Java foundation

```sh
sh run.sh
```

`run.sh` compiles every Java source file into `.build/classes`, adds `lib/mysql-connector-java-8.0.29.jar` to the Java classpath, and starts `Main`.

The application defaults to the local `mytix` database with the username
`root` and an empty password. Override those values with an ignored
`config.properties` copied from `config.properties.example`. If the initial
connection fails, the terminal starts in offline mode and option 10 can retry.

### Development commands

```sh
sh run.sh --generate-data
sh run.sh --self-test
sh run.sh --database-check
```

- `--generate-data` deterministically rewrites `data/development-data.sql`.
- `--self-test` checks validation, commit/rollback behavior, inventory math,
  organizer controls, booking/cancellation/resale rules, reviews,
  and deterministic generation without MySQL.
- `--database-check` drops and recreates only the configured MyTix tables,
  executes `schema.sql` and `load.sql`, exercises all required operations,
  and restores the deterministic dataset. Use it only against the development
  MyTix database.

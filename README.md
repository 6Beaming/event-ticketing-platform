# MyTix

MyTix is a terminal-based event ticketing application built with Java and MySQL. It supports the ticket lifecycle from event creation and pricing to booking, cancellation, resale, ownership tracking, and attendance reviews. Customers can search for performances and available seats, while organizers can explore sales reports and historical pricing recommendations.

## Features

- **Profiles and history:** Create customer and organizer profiles, deactivate accounts while preserving transaction history, and view customer orders and organizer sales.
- **Events and pricing:** Create events with artist/team billing, schedule performances, configure performance-specific price tiers and section assignments, and update eligible unsold tiers.
- **Inventory and booking:** Manage reserved seats and general-admission capacity, block available seats, and book tickets atomically using JDBC transactions and row locks.
- **Cancellations and refunds:** Cancel eligible customer tickets at least seven days before a performance, or cancel an entire performance and record refunds for active tickets.
- **Resale and ownership:** List tickets within the event's resale cap, withdraw or purchase listings, and inspect each ticket's ownership history.
- **Reviews:** Submit attendance-backed event and venue ratings and comments.
- **Organizer toolkit:** Recommend tier prices and capacity shares from comparable performances, and estimate the revenue effect of a proposed price change. Recommendations use historical heuristics and fallbacks when data is limited.

### Searches and reports

| Searches | Purpose |
|---|---|
| Q1-Q3 | Find upcoming performances by coordinates, nearby postal codes, or exact venue address |
| Q4-Q5 | Filter by location, dates, city, segment, genre, price, availability, and seating type |
| Q6 | Summarize available, sold, and blocked seats by section and price tier |
| Q7 | Find the lowest-priced consecutive seats in one row for a group and optional budget |

| Reports | Purpose |
|---|---|
| R1-R3 | Ticket revenue, event/performance counts, and organizer revenue rankings |
| R4 | Identify possible scalpers and synchronize customer restrictions |
| R5-R6 | Customer order rankings and customer/organizer cancellation rankings |
| R7-R8 | Sell-through rates, monthly city summaries, resale volume, and markups |
| R9 | Extract the ten most frequent noun phrases per event from review comments using Apache OpenNLP |

## Technology

- **Java 17** with a persistent terminal menu and direct JDBC access.
- **MySQL 8 / InnoDB** with 26 relational tables, foreign keys, check constraints, transactions, and `FOR UPDATE` locks for booking inventory.
- **SQL window functions** for consecutive-seat discovery.
- **Apache OpenNLP 2.3.3** for part-of-speech tagging and noun-phrase analysis.

## Getting started

### 1. Check prerequisites

- MySQL 8 installed and running.
- A JDK with `java` and `javac` available.
- Bash for `run.sh`
- `lib/mysql-connector-java-8.0.29.jar` (MySQL JDBC driver). 

*Java's standard `java.sql` API does not natively understand the MySQL protocol. Connector/J provides that MySQL-specific implementation so `DriverManager` can open the JDBC connection and send SQL to MySQL.*

### 2. Set up the database (run once)

1. Open a terminal and log into MySQL:

   ```sh
   sudo mysql
   ```

2. Create the database for the application:

   ```sql
   CREATE DATABASE IF NOT EXISTS <YOUR_DB_NAME>;
   EXIT;
   ```

Use a clean database and run these commands from the project root in this exact order:

1. Execute `sudo mysql <YOUR_DB_NAME> < sql/schema.sql`.
2. Execute `sudo mysql <YOUR_DB_NAME> < sql/load.sql`.
3. Run `./run.sh`.


`schema.sql` creates the tables and constraints. `load.sql` creates the complete sample dataset. `run.sh` compiles the Java source and starts the terminal. No manual insert or code edit is required.

### 3. Configure the connection

Copy `config.properties.example` to `config.properties` and enter the database URL, user, and password for your MySQL instance.

### 4. Launch MyTix

```sh
./run.sh
```

The script compiles all Java sources into `.build/classes`, adds `lib/*` to the classpath, and launches `Main`. A successful connection displays `Database: CONNECTED`.

If MySQL is unavailable, the terminal starts in offline mode. After fixing the server or connection settings, use main menu option `10` and enter `R` to retry.

## Exploring the application

| Main menu | Area |
|---|---|
| 1 | User profiles |
| 2 | Organizer events and performances |
| 3 | Performance pricing and inventory |
| 4 | Ticket booking and cancellations |
| 5 | Ticket resale and histories |
| 6 | Attendance reviews |
| 7 | Searches (Q1-Q7) |
| 8 | Reports (R1-R9) |
| 9 | Organizer toolkit |
| 10 | Database connection |
| 0 | Exit |

Try these examples on a freshly loaded dataset. In the notation below, `4 -> 1` means main menu option `4`, then submenu option `1`.

- **View inventory:** Choose `4 -> 1` and enter performance `6001` to see reserved seats and their availability.
- **Find group seating:** Choose `7 -> 7`, enter performance `6001`, request `4` seats, and leave the budget blank.
- **Trace resale ownership:** Choose `5 -> 6` and enter ticket `8007` to see its original owner and two resale transfers.
- **Explore pricing:** Choose `9` and request a recommendation for genre `4101`, city `Toronto`, and capacity `120`.

See the [user manual](docs/manual.pdf) for full input instructions.


## Repository structure

```text
MyTix/
|-- run.sh                      # Compile, launch, and verification commands
|-- config.properties.example   # Local JDBC configuration template
|-- src/
|   |-- Main.java               # Application entry point
|   |-- database/               # Connections, transactions, and SQL script execution
|   |-- operations/             # Ticketing workflows and business rules
|   |-- queries/                # Performance searches and seat selection
|   |-- reports/                # Analytics and noun-phrase extraction
|   |-- toolkit/                # Pricing recommendations and revenue estimates
|   |-- ui/                     # Terminal menus and input handling
|   |-- data/                   # Deterministic sample-data generator
|   `-- testing/                # Self-tests and database checks
|-- sql/                        # Schema, load, and drop scripts
|-- data/                       # Generated SQL and sample scenarios
|-- lib/                        # Bundled Java dependencies
|-- models/                     # OpenNLP English POS model
`-- docs/
    |-- manual.pdf              # User manual
    `-- relation_schema.md      # Relational schema
```

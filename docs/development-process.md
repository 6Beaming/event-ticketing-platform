# MyTix Development Process

MyTix is a Java and MySQL terminal application that manages event ticketing from performance setup through booking, cancellation, resale, and reviews.

This summary organizes the implemented project into a logical development sequence for a portfolio flowchart. It is not a dated record of the exact order in which every feature was built. Each numbered heading can become a flowchart node, with its short description used as supporting text.

## 1. Define the ticketing workflows

Identify what customers and organizers need: profiles, events, performances, pricing, ticket purchases, cancellations, resale, and reviews. Define rules for seat availability, cancellation deadlines, resale price caps, and review eligibility.

Outcome: A clear scope covering the ticket lifecycle, seven search workflows, nine reporting categories, and an organizer pricing toolkit.

## 2. Design the relational database

Model users, venues, sections, seats, events, performances, tickets, transactions, and ownership history. Separate reserved-seat inventory from general-admission capacity, and associate pricing with individual performances.

Outcome: A MySQL schema with 26 tables, keys, relationships, and constraints that connect ticketing records and preserve history.

## 3. Build repeatable sample data

Create a deterministic Java generator for sample SQL with stable identifiers and dates relative to load time. Include upcoming and completed performances, available and sold inventory, cancellations, resales, and attendance reviews.

Outcome: Repeatable scenarios for development, testing, and demonstrations, including 60 performances and 960 tickets.

## 4. Establish the Java database foundation

Connect Java to MySQL through JDBC, centralize connection settings, and provide shared transaction handling. Organize the application into operations, queries, reports, toolkit, and terminal-interface modules.

Outcome: A reusable foundation for database access, input validation, consistent operation results, and commit or rollback behavior.

## 5. Implement ticketing operations

Build profile management, event creation, performance pricing, inventory controls, and ticket booking. Extend the lifecycle with cancellations, refund records, resale listings and purchases, ownership transfers, and attendance-backed reviews.

Use transactions and row locks when checking and updating booking inventory so a purchase completes as a whole and competing requests cannot sell the same inventory twice.

Outcome: Connected customer and organizer workflows that enforce business rules while retaining transaction and ownership history.

## 6. Add performance and seat searches

Implement seven search workflows covering location, dates, event categories, prices, availability, and seating types. Use SQL window functions to identify consecutive available seats and find the cheapest group that meets a requested quantity and optional budget.

Outcome: Customers can discover suitable performances and find seats that let their group sit together.

## 7. Add reporting and review analysis

Implement nine reporting categories for revenue, event activity, organizer and customer rankings, possible scalpers, cancellations, sell-through, and resale trends. Integrate Apache OpenNLP to extract the top ten noun phrases from review comments per event.

Outcome: Structured sales analysis and a summary of recurring language in customer feedback. Possible-scalper reporting also synchronizes customer restrictions.

## 8. Develop the organizer pricing toolkit

Use comparable historical performances to recommend tier prices and capacity shares. Add fallback recommendations when comparable data is limited and estimate the revenue effect of proposed price changes.

Outcome: Historical pricing guidance with explanations of the comparisons used; estimates remain heuristics rather than guaranteed forecasts.

## 9. Integrate the terminal experience and verification

Connect the features through persistent menus, input validation, retry prompts, readable results, and database reconnection support. Provide self-tests for validation, transaction behavior, and calculations, plus database checks for integrated ticketing workflows.

Outcome: An interactive application with repeatable checks for successful operations and rejected requests.

Flowchart decision: Do the checks and demonstration scenarios behave as expected? If no, return to the relevant schema, operation, query, or interface stage, correct the issue, and repeat verification. If yes, proceed to documentation and delivery. This describes the verification loop, not a claim that tests were run when this summary was written.

## 10. Document and package the project

Provide schema and data-loading scripts, a shell runner that compiles and launches the application, connection configuration, a README, a relational schema document, and a user manual.

Outcome: A reproducible coursework application that another person can set up and explore. Its scope includes a terminal interface and recorded payment/refund activity; authentication and external payment processing remain outside the implementation.

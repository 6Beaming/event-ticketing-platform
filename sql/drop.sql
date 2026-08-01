-- ============================================================================
-- MyTix — drop.sql
-- Cleanly drops everything created by schema.sql, in reverse dependency order.
-- ============================================================================

DROP TRIGGER IF EXISTS trg_tickets_no_double_sell_upd;
DROP TRIGGER IF EXISTS trg_tickets_no_double_sell_ins;
DROP TRIGGER IF EXISTS trg_generalseating_disjoint;
DROP TRIGGER IF EXISTS trg_reservedsection_disjoint;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS Reviews;
DROP TABLE IF EXISTS ResaleListing;
DROP TABLE IF EXISTS Tickets;
DROP TABLE IF EXISTS ResaleSale;
DROP TABLE IF EXISTS Purchase;
DROP TABLE IF EXISTS Transactions;
DROP TABLE IF EXISTS PerformanceSeats;
DROP TABLE IF EXISTS GeneralAdmissionCapacity;
DROP TABLE IF EXISTS SectionTierAssignment;
DROP TABLE IF EXISTS PriceTier;
DROP TABLE IF EXISTS Performance;
DROP TABLE IF EXISTS Seats;
DROP TABLE IF EXISTS SeatRows;
DROP TABLE IF EXISTS GeneralSeating;
DROP TABLE IF EXISTS ReservedSection;
DROP TABLE IF EXISTS Section;
DROP TABLE IF EXISTS Venue;
DROP TABLE IF EXISTS BillingOrder;
DROP TABLE IF EXISTS ArtistsTeams;
DROP TABLE IF EXISTS Event;
DROP TABLE IF EXISTS Genre;
DROP TABLE IF EXISTS Segment;
DROP TABLE IF EXISTS PaymentInfo;
DROP TABLE IF EXISTS Organizer;
DROP TABLE IF EXISTS Customer;
DROP TABLE IF EXISTS Users;

SET FOREIGN_KEY_CHECKS = 1;

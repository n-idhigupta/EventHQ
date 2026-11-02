# EventHQ source structure

```text
eventhq/
+-- .mvn/
|   `-- wrapper/
|       +-- maven-wrapper.jar
|       `-- maven-wrapper.properties
+-- docs/
|   +-- PROJECT_STRUCTURE.md
|   `-- VALIDATION.md
+-- scripts/
|   `-- browser-smoke.cjs
+-- src/
|   +-- main/
|   |   +-- java/
|   |   |   `-- com/
|   |   |       `-- eventhq/
|   |   |           +-- auth/
|   |   |           |   +-- AuthController.java
|   |   |           |   `-- AuthService.java
|   |   |           +-- booking/
|   |   |           |   +-- Booking.java
|   |   |           |   +-- BookingController.java
|   |   |           |   +-- BookingDtos.java
|   |   |           |   +-- BookingItem.java
|   |   |           |   +-- BookingItemRepository.java
|   |   |           |   +-- BookingRepository.java
|   |   |           |   +-- BookingService.java
|   |   |           |   `-- HoldExpiryJob.java
|   |   |           +-- config/
|   |   |           |   +-- AdminBootstrap.java
|   |   |           |   +-- CoreConfiguration.java
|   |   |           |   +-- DevSampleData.java
|   |   |           |   `-- EventHqProperties.java
|   |   |           +-- event/
|   |   |           |   +-- AdminCatalogController.java
|   |   |           |   +-- CatalogController.java
|   |   |           |   +-- CatalogDtos.java
|   |   |           |   +-- CatalogService.java
|   |   |           |   +-- Event.java
|   |   |           |   +-- EventRepository.java
|   |   |           |   +-- EventSeat.java
|   |   |           |   `-- EventSeatRepository.java
|   |   |           +-- exception/
|   |   |           |   +-- ApiError.java
|   |   |           |   +-- ApiException.java
|   |   |           |   +-- ErrorResponses.java
|   |   |           |   `-- GlobalExceptionHandler.java
|   |   |           +-- integration/
|   |   |           |   +-- CurrencyCache.java
|   |   |           |   +-- ExternalApiService.java
|   |   |           |   `-- IntegrationController.java
|   |   |           +-- legacy/
|   |   |           |   +-- AdminMetricsServlet.java
|   |   |           |   `-- ServletConfiguration.java
|   |   |           +-- notification/
|   |   |           |   +-- BookingConfirmedEvent.java
|   |   |           |   +-- BookingNotificationListener.java
|   |   |           |   +-- NotificationOutbox.java
|   |   |           |   +-- NotificationOutboxRepository.java
|   |   |           |   +-- NotificationSink.java
|   |   |           |   `-- NotificationWorker.java
|   |   |           +-- payment/
|   |   |           |   +-- PaymentAttempt.java
|   |   |           |   +-- PaymentAttemptRepository.java
|   |   |           |   +-- PaymentConfiguration.java
|   |   |           |   +-- PaymentGateway.java
|   |   |           |   +-- PaymentGatewayFactory.java
|   |   |           |   `-- SimulatedPaymentGateway.java
|   |   |           +-- pricing/
|   |   |           |   +-- MultiplierPricingStrategy.java
|   |   |           |   +-- PricingConfiguration.java
|   |   |           |   +-- PricingStrategy.java
|   |   |           |   `-- PricingStrategyFactory.java
|   |   |           +-- security/
|   |   |           |   +-- CorrelationFilter.java
|   |   |           |   `-- SecurityConfiguration.java
|   |   |           +-- sponsor/
|   |   |           |   +-- Sponsor.java
|   |   |           |   `-- SponsorRepository.java
|   |   |           +-- ticket/
|   |   |           |   +-- Ticket.java
|   |   |           |   +-- TicketController.java
|   |   |           |   +-- TicketRepository.java
|   |   |           |   `-- TicketService.java
|   |   |           +-- user/
|   |   |           |   +-- User.java
|   |   |           |   `-- UserRepository.java
|   |   |           +-- venue/
|   |   |           |   +-- Seat.java
|   |   |           |   +-- SeatRepository.java
|   |   |           |   +-- Venue.java
|   |   |           |   `-- VenueRepository.java
|   |   |           `-- EventHqApplication.java
|   |   `-- resources/
|   |       +-- db/
|   |       |   `-- migration/
|   |       |       `-- V1__schema.sql
|   |       +-- static/
|   |       |   +-- assets/
|   |       |   |   +-- app.css
|   |       |   |   +-- app.js
|   |       |   |   `-- favicon.svg
|   |       |   `-- index.html
|   |       +-- application-dev.properties
|   |       +-- application-mysql.properties
|   |       `-- application.properties
|   `-- test/
|       +-- java/
|       |   `-- com/
|       |       `-- eventhq/
|       |           +-- security/
|       |           |   `-- JwtTest.java
|       |           +-- BookingScenarios.java
|       |           +-- ExternalApiTest.java
|       |           +-- H2BookingTest.java
|       |           +-- MySqlBookingIT.java
|       |           +-- PricingTest.java
|       |           `-- TestConfiguration.java
|       `-- resources/
|           `-- application-test.properties
+-- .dockerignore
+-- .editorconfig
+-- .env.example
+-- .gitignore
+-- Dockerfile
+-- README.md
+-- compose.yaml
+-- mvnw
+-- mvnw.cmd
+-- pom.xml
`-- run.cmd
```

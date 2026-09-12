# Implementation Roadmap

## Phase 1: Foundations

1. Create Firebase project and Android app registration.
2. Configure Firebase Auth providers and download `google-services.json`.
3. Provision PostgreSQL and run [schema.sql](../backend/sql/schema.sql).
4. Set backend environment variables and Firebase service account JSON.
5. Configure Razorpay keys and webhook settings.

## Phase 2: Backend Core

1. Implement auth sync and role bootstrap.
2. Build hotel listing, search, and detail endpoints.
3. Build room inventory generation and pricing management tooling.
4. Implement booking creation transaction with inventory locks.
5. Implement payment verification and release-on-failure logic.
6. Add booking history, cancellation rules, favorites, and reviews.

## Phase 3: Android Core

1. Wire Firebase Auth screens and token-aware Retrofit client.
2. Connect home listing and search filters to backend APIs.
3. Build hotel detail, room detail, and availability UX.
4. Implement booking confirmation state and Razorpay checkout.
5. Add booking history, favorites, reviews, and profile sync.

## Phase 4: Admin Operations

1. Add admin role assignment strategy.
2. Create dashboard or admin client consuming admin APIs.
3. Add booking exception handling and refund tooling.
4. Add review moderation, hotel CRUD, room CRUD, and pricing updates.

## Phase 5: Hardening

1. Add integration tests for booking overlap and payment verification.
2. Add scheduled cleanup for expired `payment_pending` bookings.
3. Add rate limiting, API logging correlation IDs, and audit trails.
4. Add image upload pipeline and CDN-backed media delivery.
5. Add crash analytics, observability, and release workflows.

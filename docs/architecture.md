# Innly Architecture

## Product Scope

Innly is a startup-MVP hotel booking platform with:

- Consumer Android application
- Admin backend APIs
- Firebase-authenticated users
- PostgreSQL-backed hotel inventory and bookings
- Razorpay checkout for payment capture

## Android Architecture

```text
presentation (Compose screens, ViewModels, navigation)
        ↓
domain (models, repository contracts, use cases)
        ↓
data (Retrofit services, DTOs, repository implementations)
        ↓
backend API
```

### Android Package Structure

```text
com.innly.hotelbooking
├── core
│   ├── common
│   ├── designsystem
│   ├── network
│   └── ui
├── data
│   ├── local
│   ├── remote
│   └── repository
├── di
├── domain
│   ├── model
│   ├── repository
│   └── usecase
└── presentation
    ├── auth
    ├── booking
    ├── favorites
    ├── home
    ├── hoteldetail
    ├── navigation
    ├── onboarding
    ├── profile
    ├── reviews
    ├── roomdetail
    └── search
```

## Backend Architecture

```text
routes → controllers → services → repositories → PostgreSQL
                  ↘ middlewares / validators / utils
```

### Backend Structure

```text
backend/src
├── config
├── controllers
├── middlewares
├── repositories
├── routes
│   └── admin
├── services
├── utils
└── validators
```

## Booking Availability Strategy

To prevent overlapping bookings and overselling, the backend uses a daily inventory table:

- `room_inventory(room_id, inventory_date, total_inventory, booked_inventory, price_override)`
- One row per room type per date
- Booking flow locks all requested inventory rows with `FOR UPDATE`
- Availability is confirmed only if every requested date has enough unbooked inventory

### Transaction Flow

1. Validate `checkIn < checkOut`.
2. Start SQL transaction with `SERIALIZABLE` isolation.
3. Lock the relevant `room_inventory` rows.
4. Check `total_inventory - booked_inventory >= roomsRequested` for each day.
5. Create booking in `payment_pending` status.
6. Increment `booked_inventory`.
7. Create payment row.
8. Commit.
9. Create Razorpay order and return it to the app.

If payment fails or expires, a release flow decrements `booked_inventory` and marks the booking canceled/failed.

## Payment Verification Flow

1. Android requests a booking from backend.
2. Backend creates a pending booking and Razorpay order.
3. Android launches Razorpay checkout with order details.
4. Razorpay returns `payment_id`, `order_id`, and `signature`.
5. Android sends the payload to backend.
6. Backend recomputes the HMAC signature using `RAZORPAY_KEY_SECRET`.
7. On success:
   - payment status → `captured`
   - booking status → `confirmed`
8. On failure:
   - payment status → `failed`
   - booking status → `payment_failed`
   - reserved inventory released

## Security

- Firebase ID token is attached to protected API requests.
- Backend verifies the token with Firebase Admin SDK.
- Admin endpoints require a user role check.
- Input payloads are validated with Zod.
- Secrets live in environment variables only.

# API Reference

Base URL: `/api/v1`

## Auth

### `POST /auth/sync`

Create or update the backend user record from a verified Firebase user.

Request:

```json
{
  "displayName": "Aarav Mehta",
  "phoneNumber": "+919876543210"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "id": "ff2a7c52-b1fc-4bea-b786-7fdaff6a6bb7",
    "firebaseUid": "firebase-uid",
    "email": "aarav@example.com",
    "displayName": "Aarav Mehta",
    "role": "user"
  }
}
```

## Hotels

### `GET /hotels`

Query params:

- `city`
- `minPrice`
- `maxPrice`
- `minRating`
- `amenities=wifi,pool,spa`
- `page`
- `limit`

Response:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "hotel_123",
        "name": "The Marine Grand",
        "city": "Mumbai",
        "rating": 4.5,
        "startingPrice": 5400,
        "thumbnailUrl": "https://cdn.example.com/hotels/hotel_123.jpg",
        "amenities": ["wifi", "pool", "breakfast"]
      }
    ],
    "page": 1,
    "limit": 10,
    "total": 1
  }
}
```

### `GET /hotels/:hotelId`

Response:

```json
{
  "success": true,
  "data": {
    "id": "hotel_123",
    "name": "The Marine Grand",
    "description": "Sea-facing luxury business hotel.",
    "city": "Mumbai",
    "address": "Nariman Point, Mumbai",
    "rating": 4.5,
    "reviewCount": 328,
    "images": [
      "https://cdn.example.com/hotels/hotel_123_1.jpg"
    ],
    "amenities": ["wifi", "pool", "spa", "parking"],
    "rooms": [
      {
        "id": "room_deluxe",
        "name": "Deluxe King",
        "capacity": 2,
        "basePrice": 5400,
        "thumbnailUrl": "https://cdn.example.com/rooms/deluxe.jpg"
      }
    ]
  }
}
```

### `GET /hotels/:hotelId/availability?roomId=room_deluxe&checkIn=2026-04-10&checkOut=2026-04-12&rooms=1`

Response:

```json
{
  "success": true,
  "data": {
    "available": true,
    "nights": 2,
    "totalAmount": 10800,
    "currency": "INR",
    "dailyBreakdown": [
      {
        "date": "2026-04-10",
        "price": 5400,
        "availableInventory": 4
      },
      {
        "date": "2026-04-11",
        "price": 5400,
        "availableInventory": 4
      }
    ]
  }
}
```

## Bookings

### `POST /bookings`

Request:

```json
{
  "hotelId": "hotel_123",
  "roomId": "room_deluxe",
  "checkIn": "2026-04-10",
  "checkOut": "2026-04-12",
  "rooms": 1,
  "guests": 2,
  "guestName": "Aarav Mehta",
  "guestEmail": "aarav@example.com",
  "guestPhone": "+919876543210"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "bookingId": "booking_123",
    "status": "payment_pending",
    "amount": 10800,
    "currency": "INR",
    "razorpayOrder": {
      "id": "order_Pabc123",
      "amount": 1080000,
      "currency": "INR"
    }
  }
}
```

### `GET /bookings`

Response:

```json
{
  "success": true,
  "data": [
    {
      "id": "booking_123",
      "hotelName": "The Marine Grand",
      "roomName": "Deluxe King",
      "checkIn": "2026-04-10",
      "checkOut": "2026-04-12",
      "status": "confirmed",
      "amount": 10800
    }
  ]
}
```

### `POST /bookings/:bookingId/cancel`

Request:

```json
{
  "reason": "Plan changed"
}
```

## Payments

### `POST /payments/verify`

Request:

```json
{
  "bookingId": "booking_123",
  "razorpayOrderId": "order_Pabc123",
  "razorpayPaymentId": "pay_Pdef456",
  "razorpaySignature": "generated_signature"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "bookingId": "booking_123",
    "bookingStatus": "confirmed",
    "paymentStatus": "captured"
  }
}
```

## Favorites

### `GET /favorites`
### `POST /favorites/:hotelId`
### `DELETE /favorites/:hotelId`

## Reviews

### `POST /reviews`

Request:

```json
{
  "hotelId": "hotel_123",
  "rating": 5,
  "title": "Great stay",
  "comment": "Fast check-in, clean rooms, and excellent breakfast."
}
```

## Admin

### `POST /admin/hotels`
### `PATCH /admin/hotels/:hotelId`
### `POST /admin/rooms`
### `PATCH /admin/rooms/:roomId`
### `PATCH /admin/pricing/rooms/:roomId`
### `GET /admin/bookings`
### `GET /admin/payments`
### `PATCH /admin/reviews/:reviewId/moderate`

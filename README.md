# Innly — Modern Hotel Booking Platform

Innly is a full-stack, mobile-first hotel discovery and booking application engineered with **Kotlin**, **Jetpack Compose**, **Node.js**, **Express**, and **PostgreSQL**. Built as an engineering showcase, it models a real-world booking platform featuring multi-criteria hotel search, dynamic room availability checks, concurrency-safe inventory reservations, Firebase user authentication, and Razorpay payment capture with automated refund reconciliation.

---

## Screenshot Showcase

<p align="center">
  <img src="docs/screenshots/explore.png" width="19%" alt="Explore Screen" />
  <img src="docs/screenshots/search.png" width="19%" alt="Search Screen" />
  <img src="docs/screenshots/hotel-details.png" width="19%" alt="Hotel Details Screen" />
  <img src="docs/screenshots/room-details.png" width="19%" alt="Room Details Screen" />
  <img src="docs/screenshots/favorites.png" width="19%" alt="Favorites Screen" />
</p>

| Explore | Search & Filter | Hotel Details | Room Details | Favorites & Account |
|---|---|---|---|---|
| Curated hotel collection, location previews, and Indian currency formatting. | Real-time query search by destination, price filters, and star ratings. | Comprehensive amenity tags, photo gallery, and room options. | Suite specifications, guest capacity, bed configurations, and pricing. | Saved properties, guest authentication, and cloud-synced profile. |

---

## Key Features

- **Curated Exploration**: Discover hand-picked boutique hotels and luxury stays across major destinations with localized currency display (`₹5,900 / night`).
- **Granular Search & Filtering**: Query properties by destination city, price range constraints, and minimum guest rating thresholds (`3.0+`, `4.0+`, `4.5+`).
- **Detailed Property & Room Overviews**: Explore verified amenity badges (Wi-Fi, swimming pool, gym, parking), room capacity, and bed arrangements.
- **Concurrency-Safe Booking**: Daily inventory model with `SERIALIZABLE` transactions and row-level locks (`SELECT ... FOR UPDATE`) preventing double-booking or inventory overselling.
- **Integrated Payment Gateway**: Razorpay checkout integration with client-side order initiation and server-side cryptographic HMAC-SHA256 signature verification.
- **Lifecycle & Automated Refunds**: Seamless cancellation handling with automated inventory restoration and a background reconciliation worker with exponential backoff.
- **Guest Reviews & Dynamic Ratings**: Authenticated guest review submissions with server-side stay verification and recalculation of rating averages and distributions from approved reviews after moderation.
- **Favorites Synchronization**: Optimistic favorites management with backend synchronization when the user is authenticated.

---

## Technology Stack

### Android Mobile Client
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3 Design
- **Architecture**: MVVM with Unidirectional Data Flow (`StateFlow`, `SharedFlow`)
- **Dependency Injection**: Dagger Hilt
- **Asynchronous Processing**: Kotlin Coroutines & Flow
- **Networking**: Retrofit 2, OkHttp 3 with authentication interceptors
- **Image Loading**: Coil 3 Compose
- **Navigation**: Navigation Compose with safe routing
- **Authentication**: Firebase Authentication SDK
- **Testing**: JUnit 4, MockK, Kotlinx Coroutines Test, Compose UI Test

### Backend API Services
- **Runtime**: Node.js (ES Modules)
- **Framework**: Express.js
- **Database**: PostgreSQL with connection pooling (`pg`)
- **Schema Validation**: Zod for runtime schema and request validation
- **Security**: Firebase Admin SDK (token verification), Helmet, CORS, Rate Limiting
- **Payments**: Razorpay Node SDK with HMAC-SHA256 signature validation
- **Logging & Monitoring**: Morgan HTTP request logging and health probes (`/api/v1/health/liveness`, `/api/v1/health/readiness`)
- **Testing**: Node.js Native Test Runner (`node --test`)

---

## System Architecture

### Android Client Architecture

The mobile application uses a Clean Architecture-inspired layered structure:

```text
com.innly.hotelbooking
├── core/                  # Design system, common tokens, network interceptors, formatters
├── data/                  # Remote Retrofit data sources, DTOs, and repository implementations
├── di/                    # Dagger Hilt modules for network, repository, and service bindings
├── domain/                # Business models, repository interfaces, and use cases
└── presentation/          # Jetpack Compose UI, ViewModels, and navigation graphs
    ├── auth/              # Sign-in, account registration, and authentication state
    ├── booking/           # Room booking flow, guest details, and Razorpay initiation
    ├── favorites/         # Saved properties with local-remote synchronization
    ├── home/              # Curated hotel feed and featured destinations
    ├── hoteldetail/       # Hotel overview, amenity lists, and room selection
    ├── navigation/        # Compose navigation routes, tabs, and arguments
    ├── profile/           # User account, booking history, and preferences
    ├── reviews/           # Verified guest review submission and rating breakdown
    ├── roomdetail/        # Room specifications, bedding, and availability checks
    └── search/            # Filter controls, price range sliders, and search results
```

### Backend Architecture & Concurrency Control

The backend implements a decoupled layered architecture: `Routes → Controllers → Services → Repositories → PostgreSQL`.

```text
backend/
├── src/
│   ├── config/            # Environment variable validation and service configs
│   ├── controllers/       # HTTP request handlers and response formatting
│   ├── middlewares/       # Firebase token verification, validation, error handler
│   ├── repositories/      # SQL queries and database access layer
│   ├── routes/            # Express routers (public, protected, and admin)
│   ├── services/          # Business logic, booking transactions, payment capture
│   ├── utils/             # Database pool, migration runner, date helpers
│   └── validators/        # Zod request validation schemas
├── sql/                   # PostgreSQL schema and migration scripts
└── tests/                 # Automated test suites
```

#### Booking Availability & Concurrency Strategy

To guarantee that inventory is never oversold under concurrent high-traffic conditions:
1. **Daily Room Inventory**: Each room type maintains an inventory record per calendar date in `room_inventory`.
2. **Row-Level Locking**: When a reservation is requested, all matching inventory rows across the check-in to check-out window are locked inside a `SERIALIZABLE` transaction using `SELECT ... FOR UPDATE`.
3. **Availability Verification**: Booking proceeds only if `(total_inventory - booked_inventory) >= requested_rooms` holds true for every day in the reservation period.
4. **Failure Compensation**: If payment capture fails or times out, a compensation routine automatically decrements `booked_inventory` and marks the booking `payment_failed`.

---

## Security & Reliability

- **Firebase Token Verification**: Protected endpoints validate Firebase ID tokens via the Firebase Admin SDK and synchronize user profile records.
- **Fail-Closed Payment Validation**: Payment captures recompute the cryptographic HMAC-SHA256 signature using the server-side `RAZORPAY_KEY_SECRET`. Transactions reject unverified signatures or currency mismatches immediately.
- **Deterministic Schema Migrations**: Custom database migration engine (`src/utils/migrate.js`) verifies baseline integrity and computes SHA-256 checksums to prevent untracked schema deviations.
- **Build-Time Guardrails**: Custom Gradle tasks (`validateReleaseEnvironment`, `validateFirebaseConfig`) fail release builds if API endpoints point to localhost or if test credentials are used.
- **Idempotent Refund Worker**: A background lease worker manages cancellation refunds with exponential backoff and idempotency keys (`X-Refund-Idempotency`), preventing duplicate payout gateway calls.

---

## Testing & Quality Assurance

The repository includes **360 automated backend and JVM unit tests**, along with a separate **12-test connected-device UI/instrumentation test suite** for Android.

### Backend Test Suite (131 Tests across 18 Suites)
- **Booking & Concurrency**: Validates atomic booking reservations, inventory lock acquisition, and race condition prevention (`bookingAvailability.test.js`, `concurrencyRefund.test.js`).
- **Payment Verification & Expiry**: Tests fail-closed Razorpay verification, signature validation, and payment timeout handling (`paymentVerificationAndExpiry.test.js`).
- **Cancellation & Refunds**: Verifies cancellation deadlines, idempotent refund submission, and inventory restoration (`cancellationRefund.test.js`, `refundWorker.test.js`).
- **Auth & Reviews**: Tests token verification, user synchronization, review eligibility, and rating recalculation (`authSync.test.js`, `reviewsEligibilityAndState.test.js`).
- **Migration & Security**: Tests SQL baseline verification, SHA-256 checksum computation, rate limiting, and health probes (`migrationRunner.test.js`, `securityAndProbes.test.js`).

### Android JVM Unit Tests (229 Tests across 17 Suites)
- **ViewModel Logic**: State transition tests for `BookingViewModel`, `FavoritesViewModel`, `HotelDetailReviewsViewModel`, `SearchViewModel`, and `ProfileViewModel`.
- **UI & Regression Tests**: Validates bottom navigation active indicator behavior, component state propagation, and screen flow contracts (`BottomNavigationRegressionTest.kt`).
- **Formatting & Validation**: Tests Indian rupee formatting (`₹5,900`, `₹1,25,000`), localized friendly dates, phone numbers, and booking form inputs (`PresentationFormattersTest.kt`, `BookingHistoryFormatterTest.kt`).
- **Security & Network Configuration**: Tests environment config validators, HTTPS enforcement, and Razorpay key mode detection (`NetworkSecurityTest.kt`).

### Android Connected-Device Tests (12 Tests across 4 Suites)
- **UI Flow Verification**: Covers screen flows, review composition, and room detail interactions on a connected device or emulator (`HomeScreenTest.kt`, `HotelDetailReviewsTest.kt`, `RoomDetailScreenTest.kt`, `WriteReviewScreenTest.kt`).
- Executed via `./gradlew connectedDebugAndroidTest`.

---

## Local Development Setup

### Prerequisites
- **Node.js**: Node.js 20 LTS recommended
- **PostgreSQL**: PostgreSQL 14+ recommended
- **Android Studio & SDK**: Recent Android Studio with JDK 17 and Android SDK 35

---

### Backend Setup

1. **Navigate to the backend directory and install dependencies**:
   ```bash
   cd backend
   npm install
   ```

2. **Configure Environment Variables**:
   Copy the example environment configuration:
   ```bash
   cp .env.example .env
   ```
   Edit `backend/.env` with your local PostgreSQL database URL and development credentials:
   ```env
   PORT=8080
   NODE_ENV=development
   DATABASE_URL=postgres://postgres:postgres@localhost:5432/innly
   FIREBASE_PROJECT_ID=your-project-id
   FIREBASE_SERVICE_ACCOUNT_PATH=/path/to/firebase-service-account.json
   RAZORPAY_KEY_ID=rzp_test_placeholder
   RAZORPAY_KEY_SECRET=placeholder_secret
   CLIENT_APP_NAME=Innly
   ```

3. **Run Database Migrations**:
   ```bash
   npm run migrate
   ```

4. **Start the Backend Server**:
   ```bash
   npm run dev
   ```
   The backend will be available at `http://localhost:8080/api/v1`.

5. **Run Backend Tests**:
   ```bash
   npm test
   ```

---

### Android Client Setup

1. **Navigate to the Android directory**:
   ```bash
   cd android
   ```

2. **Configure Local Properties**:
   Create or update `android/local.properties` with your Android SDK location and API endpoint:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   API_BASE_URL=http://10.0.2.2:8080/api/v1/
   RAZORPAY_KEY_ID=rzp_test_placeholder
   ```
   *(Note: `10.0.2.2` represents host localhost when running in the Android emulator).*

3. **Configure Firebase Client Configuration**:
   Copy the debug example configuration:
   ```bash
   cp app/src/debug/google-services.json.example app/src/debug/google-services.json
   ```
   *(Replace with your Firebase project's debug `google-services.json` if testing live authentication).*

4. **Build and Run the Application**:
   Open the `android/` directory in Android Studio and select **Run 'app'**, or execute via Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

5. **Run Android Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## Project Structure

```text
Innly/
├── android/                           # Native Android Mobile Application
│   ├── app/
│   │   ├── src/
│   │   │   ├── androidTest/java/.../  # Connected UI & instrumentation tests (12 tests)
│   │   │   ├── debug/                 # Debug-specific configurations & google-services.json
│   │   │   ├── main/java/.../         # Application source (core, data, di, domain, presentation)
│   │   │   └── test/java/.../         # JVM unit & ViewModel tests (229 tests)
│   │   └── build.gradle.kts           # App-level build config, dependencies, and safety checks
│   ├── build.gradle.kts               # Root Android Gradle configuration
│   └── settings.gradle.kts            # Plugin management and repository settings
├── backend/                           # Express.js REST API & Services
│   ├── src/                           # Controllers, services, repositories, middlewares, utils
│   ├── sql/                           # Database schema and versioned migrations
│   ├── tests/                         # Node.js automated test suites (131 tests)
│   ├── package.json                   # Dependencies and npm scripts
│   └── .env.example                   # Sanitized backend environment template
├── docs/                              # Project Documentation
│   ├── api.md                         # Detailed REST API endpoint specification
│   ├── architecture.md                # In-depth architectural design document
│   ├── roadmap.md                     # Implementation milestones and technical roadmap
│   └── screenshots/                   # Curated application showcase images
├── LICENSE                            # MIT License
├── THIRD_PARTY_NOTICES.md             # Third-party licensing and image attributions
└── README.md                          # Project documentation
```

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

## Third-Party Attributions

Hotel demonstration imagery is sourced from [Unsplash](https://unsplash.com/license) and [Lorem Picsum](https://picsum.dev/license) under their respective licenses. All third-party open-source libraries and frameworks remain subject to their original licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for full attribution details.

---

## Author

**Abhishek Ghosh**<br>
*Full-Stack & Mobile Software Engineer*

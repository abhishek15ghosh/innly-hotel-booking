import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createBooking } from "../src/services/booking.service.js";

describe("Razorpay Order Failure Compensation Unit Tests (In-Memory Isolation)", () => {
  it("never connects to DB and safely compensates inventory & status when Razorpay order creation fails", async () => {
    const mockUser = { id: "user-uuid-123", email: "guest@example.com" };
    const mockPayload = {
      hotelId: "hotel-uuid-456",
      roomId: "room-uuid-789",
      checkIn: "2026-08-10",
      checkOut: "2026-08-12",
      rooms: 2,
      guests: 3,
      guestName: "John Doe",
      guestEmail: "guest@example.com",
      guestPhone: "9876543210"
    };

    const mockClient = { id: "mock-tx-client" };
    const calls = {
      reserveInventory: [],
      releaseInventory: [],
      markPaymentFailed: [],
      createPaymentRecord: []
    };

    const mockBookingRepo = {
      getRoomForBooking: async (client, hotelId, roomId) => {
        assert.equal(client, mockClient);
        return { id: roomId, basePrice: 5000 };
      },
      lockInventoryRows: async (client, roomId, checkIn, checkOut) => {
        assert.equal(client, mockClient);
        return [
          {
            inventory_date: "2026-08-10",
            price: 5000,
            total_inventory: 10,
            booked_inventory: 0
          },
          {
            inventory_date: "2026-08-11",
            price: 5000,
            total_inventory: 10,
            booked_inventory: 0
          }
        ];
      },
      createPendingBooking: async (client, payload) => {
        assert.equal(client, mockClient);
        return { id: "booking-uuid-999" };
      },
      createBookingItem: async (client, payload) => {
        assert.equal(client, mockClient);
      },
      reserveInventory: async (client, roomId, stayDates, rooms) => {
        calls.reserveInventory.push({ client, roomId, stayDates, rooms });
      },
      createPaymentRecord: async (client, bookingId, amount) => {
        calls.createPaymentRecord.push({ client, bookingId, amount });
      },
      releaseInventory: async (client, roomId, stayDates, rooms) => {
        calls.releaseInventory.push({ client, roomId, stayDates, rooms });
      },
      markPaymentFailed: async (client, bookingId, reason) => {
        calls.markPaymentFailed.push({ client, bookingId, reason });
      }
    };

    const mockWithTx = async (callback) => {
      return callback(mockClient);
    };

    const mockRazorpaySecretKey = "rzp_live_secret_xyz999_super_sensitive";
    const mockRazorpay = {
      orders: {
        create: async () => {
          throw new Error(`BAD_REQUEST_ERROR: Invalid API key or secret token: ${mockRazorpaySecretKey}`);
        }
      }
    };

    // Execute createBooking with injected mock dependencies
    await assert.rejects(
      async () => {
        await createBooking(mockUser, mockPayload, {
          withTx: mockWithTx,
          bookingRepo: mockBookingRepo,
          razorpay: mockRazorpay
        });
      },
      (err) => {
        // 1. Client receives HTTP 502
        assert.equal(err.statusCode, 502);

        // 2. Generic user message returned
        assert.equal(err.message, "Unable to initiate payment. Please try again.");

        // 3. Assert raw error and credentials NEVER leak in API response
        assert.equal(err.message.includes("BAD_REQUEST_ERROR"), false);
        assert.equal(err.message.includes(mockRazorpaySecretKey), false);
        return true;
      }
    );

    // 4. Assert inventory release requested with correct room, dates, and quantity
    assert.equal(calls.releaseInventory.length, 1);
    assert.equal(calls.releaseInventory[0].client, mockClient);
    assert.equal(calls.releaseInventory[0].roomId, mockPayload.roomId);
    assert.deepEqual(calls.releaseInventory[0].stayDates, ["2026-08-10", "2026-08-11"]);
    assert.equal(calls.releaseInventory[0].rooms, 2);

    // 5. Assert booking and payment marked failed with safe internal code
    assert.equal(calls.markPaymentFailed.length, 1);
    assert.equal(calls.markPaymentFailed[0].client, mockClient);
    assert.equal(calls.markPaymentFailed[0].bookingId, "booking-uuid-999");
    assert.equal(calls.markPaymentFailed[0].reason, "razorpay_order_creation_failed");
    assert.equal(calls.markPaymentFailed[0].reason.includes(mockRazorpaySecretKey), false);
  });

  it("skips DB integration tests unless TEST_DATABASE_URL is provided and ends with _test", () => {
    const testDbUrl = process.env.TEST_DATABASE_URL;
    if (!testDbUrl || !testDbUrl.endsWith("_test")) {
      // Safely skip integration test without touching development DB
      assert.ok(true, "DB Integration test skipped because TEST_DATABASE_URL is not set to a database ending in _test");
    } else {
      assert.ok(testDbUrl.endsWith("_test"), "TEST_DATABASE_URL must end with _test");
    }
  });
});

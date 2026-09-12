import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { getStayDates, formatDateString } from "../src/utils/bookingAvailability.js";
import { processExpiredBookings } from "../src/services/bookingExpiry.service.js";

describe("Booking Expiry & Date Normalization Safety Tests", () => {
  it("1. Normalizes PostgreSQL-style Date objects correctly into stay dates", () => {
    const checkInDate = new Date("2026-08-06T00:00:00.000Z");
    const checkOutDate = new Date("2026-08-08T00:00:00.000Z");

    assert.equal(formatDateString(checkInDate), "2026-08-06");
    assert.equal(formatDateString(checkOutDate), "2026-08-08");

    const stayDates = getStayDates(checkInDate, checkOutDate);
    assert.deepEqual(stayDates, ["2026-08-06", "2026-08-07"]);
  });

  it("2. Normalizes ISO date strings correctly into stay dates", () => {
    const checkInIso = "2026-08-06T18:30:00.000Z";
    const checkOutIso = "2026-08-08T18:30:00.000Z";

    const stayDates = getStayDates(checkInIso, checkOutIso);
    assert.deepEqual(stayDates, ["2026-08-06", "2026-08-07"]);
  });

  it("3. One malformed booking does not stop another valid expired booking from processing", async () => {
    const expiredBookings = [
      {
        bookingId: "b-malformed-1",
        bookingStatus: "payment_pending",
        paymentStatus: "pending",
        checkIn: "invalid-date",
        checkOut: "2026-08-08",
        roomId: "room-1",
        roomsBooked: 1
      },
      {
        bookingId: "b-valid-2",
        bookingStatus: "payment_pending",
        paymentStatus: "pending",
        checkIn: "2026-08-06",
        checkOut: "2026-08-08",
        roomId: "room-2",
        roomsBooked: 1
      }
    ];

    let releasedInventoryCount = 0;
    let paymentFailedCount = 0;
    const warnings = [];

    const originalWarn = console.warn;
    console.warn = (msg) => warnings.push(msg);

    const mockRepo = {
      getExpiredPendingBookings: async () => expiredBookings,
      releaseInventory: async (client, roomId, dates, count) => {
        releasedInventoryCount++;
        assert.equal(roomId, "room-2");
        assert.deepEqual(dates, ["2026-08-06", "2026-08-07"]);
      },
      markPaymentFailed: async (client, bookingId, reason) => {
        paymentFailedCount++;
        assert.equal(bookingId, "b-valid-2");
        assert.equal(reason, "payment_timeout");
      }
    };

    try {
      const result = await processExpiredBookings({
        withTx: async (cb) => cb({}),
        bookingRepo: mockRepo,
        nowFn: () => new Date("2026-08-08T12:00:00.000Z")
      });

      assert.equal(result.processedCount, 1);
      assert.equal(releasedInventoryCount, 1);
      assert.equal(paymentFailedCount, 1);
      assert.equal(warnings.length, 1);
      assert.ok(warnings[0].includes("Booking expiry warning [Booking ID: b-malformed-1]"));
    } finally {
      console.warn = originalWarn;
    }
  });

  it("4. Inventory is never released for an invalid date range", async () => {
    const invalidBookings = [
      {
        bookingId: "b-invalid-range",
        bookingStatus: "payment_pending",
        paymentStatus: "pending",
        checkIn: "2026-08-10",
        checkOut: "2026-08-08", // checkIn > checkOut!
        roomId: "room-1",
        roomsBooked: 1
      }
    ];

    let releaseInventoryCalled = false;

    const mockRepo = {
      getExpiredPendingBookings: async () => invalidBookings,
      releaseInventory: async () => {
        releaseInventoryCalled = true;
      },
      markPaymentFailed: async () => {}
    };

    const result = await processExpiredBookings({
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      nowFn: () => new Date("2026-08-08T12:00:00.000Z")
    });

    assert.equal(result.processedCount, 0);
    assert.equal(releaseInventoryCalled, false);
  });

  it("5. A valid expired pending booking releases inventory exactly once and becomes payment-failed", async () => {
    const validBooking = [
      {
        bookingId: "b-valid-single",
        bookingStatus: "payment_pending",
        paymentStatus: "pending",
        checkIn: "2026-08-06",
        checkOut: "2026-08-07",
        roomId: "room-10",
        roomsBooked: 2
      }
    ];

    let releaseCount = 0;
    let failedCount = 0;

    const mockRepo = {
      getExpiredPendingBookings: async () => validBooking,
      releaseInventory: async (client, roomId, dates, rooms) => {
        releaseCount++;
        assert.equal(roomId, "room-10");
        assert.equal(rooms, 2);
        assert.deepEqual(dates, ["2026-08-06"]);
      },
      markPaymentFailed: async (client, bookingId, reason) => {
        failedCount++;
        assert.equal(bookingId, "b-valid-single");
        assert.equal(reason, "payment_timeout");
      }
    };

    const result = await processExpiredBookings({
      withTx: async (cb) => cb({}),
      bookingRepo: mockRepo,
      nowFn: () => new Date("2026-08-08T12:00:00.000Z")
    });

    assert.equal(result.processedCount, 1);
    assert.equal(releaseCount, 1);
    assert.equal(failedCount, 1);
  });
});

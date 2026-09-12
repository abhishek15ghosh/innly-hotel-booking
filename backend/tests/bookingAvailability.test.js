import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  getStayDates,
  formatDateString,
  summarizeAvailability
} from "../src/utils/bookingAvailability.js";

describe("bookingAvailability utility tests", () => {
  describe("getStayDates", () => {
    it("returns correct single-night stay date array", () => {
      const dates = getStayDates("2026-08-02", "2026-08-03");
      assert.deepEqual(dates, ["2026-08-02"]);
    });

    it("returns correct multi-night stay date array", () => {
      const dates = getStayDates("2026-08-02", "2026-08-04");
      assert.deepEqual(dates, ["2026-08-02", "2026-08-03"]);
    });

    it("throws 400 error when check-in is after or equal to check-out", () => {
      assert.throws(
        () => getStayDates("2026-08-04", "2026-08-02"),
        (err) => err.statusCode === 400
      );
      assert.throws(
        () => getStayDates("2026-08-02", "2026-08-02"),
        (err) => err.statusCode === 400
      );
    });

    it("throws 400 error for invalid date strings", () => {
      assert.throws(
        () => getStayDates("invalid-date", "2026-08-03"),
        (err) => err.statusCode === 400
      );
    });
  });

  describe("formatDateString", () => {
    it("formats ISO string date", () => {
      assert.equal(formatDateString("2026-08-02"), "2026-08-02");
      assert.equal(formatDateString("2026-08-02T00:00:00.000Z"), "2026-08-02");
    });

    it("formats JS Date instance using local date components", () => {
      const date = new Date(2026, 7, 2); // August 2, 2026 in local time
      assert.equal(formatDateString(date), "2026-08-02");
    });

    it("handles null or undefined safely", () => {
      assert.equal(formatDateString(null), "");
      assert.equal(formatDateString(undefined), "");
    });
  });

  describe("summarizeAvailability", () => {
    it("returns totalAmount ₹11,800 for 2 nights at ₹5,900 per night", () => {
      const stayDates = ["2026-08-02", "2026-08-03"];
      const inventoryRows = [
        {
          inventory_date: "2026-08-02",
          price: 5900,
          total_inventory: 7,
          booked_inventory: 0
        },
        {
          inventory_date: "2026-08-03",
          price: 5900,
          total_inventory: 7,
          booked_inventory: 0
        }
      ];

      const result = summarizeAvailability(inventoryRows, stayDates, 1);
      assert.equal(result.available, true);
      assert.equal(result.nights, 2);
      assert.equal(result.totalAmount, 11800);
      assert.equal(result.dailyBreakdown.length, 2);
      assert.equal(result.dailyBreakdown[0].price, 5900);
      assert.equal(result.dailyBreakdown[1].price, 5900);
    });

    it("handles Date objects across timezones safely", () => {
      const stayDates = ["2026-08-02", "2026-08-03"];
      const inventoryRows = [
        {
          inventory_date: new Date(2026, 7, 2),
          price: 5900,
          total_inventory: 5,
          booked_inventory: 1
        },
        {
          inventory_date: new Date(2026, 7, 3),
          price: 5900,
          total_inventory: 5,
          booked_inventory: 0
        }
      ];

      const result = summarizeAvailability(inventoryRows, stayDates, 1);
      assert.equal(result.available, true);
      assert.equal(result.totalAmount, 11800);
    });

    it("returns available=false when room is sold out for one of the nights", () => {
      const stayDates = ["2026-08-02", "2026-08-03"];
      const inventoryRows = [
        {
          inventory_date: "2026-08-02",
          price: 5900,
          total_inventory: 5,
          booked_inventory: 5 // sold out
        },
        {
          inventory_date: "2026-08-03",
          price: 5900,
          total_inventory: 5,
          booked_inventory: 0
        }
      ];

      const result = summarizeAvailability(inventoryRows, stayDates, 1);
      assert.equal(result.available, false);
      assert.equal(result.totalAmount, 0);
    });

    it("returns available=false when inventory row is missing for a stay date", () => {
      const stayDates = ["2026-08-02", "2026-08-03"];
      const inventoryRows = [
        {
          inventory_date: "2026-08-02",
          price: 5900,
          total_inventory: 5,
          booked_inventory: 0
        }
      ];

      const result = summarizeAvailability(inventoryRows, stayDates, 1);
      assert.equal(result.available, false);
      assert.equal(result.totalAmount, 0);
    });
  });
});

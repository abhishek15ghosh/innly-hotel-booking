import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { syncUserProfile } from "../src/services/auth.service.js";
import { createBooking } from "../src/services/booking.service.js";

describe("Restored Session Profile Sync Unit Tests (In-Memory Isolation)", () => {
  it("syncUserProfile passes profile data to upsertUser without connecting to database", async () => {
    const mockClient = { id: "mock-client" };
    const mockPayload = {
      firebaseUid: "firebase-uid-123",
      email: "user@example.com",
      displayName: "Jane Doe",
      phoneNumber: "+1234567890"
    };

    let upsertCalledWith = null;
    const mockUserRepo = {
      upsertUser: async (client, data) => {
        assert.equal(client, mockClient);
        upsertCalledWith = data;
        return { id: "db-user-id-999", ...data };
      }
    };

    const mockWithTx = async (cb) => cb(mockClient);

    const result = await syncUserProfile(mockPayload, {
      withTx: mockWithTx,
      userRepo: mockUserRepo
    });

    assert.deepEqual(upsertCalledWith, mockPayload);
    assert.equal(result.id, "db-user-id-999");
    assert.equal(result.firebaseUid, "firebase-uid-123");
  });

  it("createBooking rejects requests missing user.id with HTTP 403 ApiError", async () => {
    const userWithoutDbId = {
      firebaseUid: "firebase-uid-restored-session",
      email: "restored@example.com"
      // id is missing/null because profile sync has not completed yet
    };

    const bookingPayload = {
      hotelId: "hotel-1",
      roomId: "room-1",
      checkIn: "2026-08-10",
      checkOut: "2026-08-12",
      rooms: 1,
      guests: 2,
      guestName: "Restored User",
      guestEmail: "restored@example.com",
      guestPhone: "9876543210"
    };

    await assert.rejects(
      async () => {
        await createBooking(userWithoutDbId, bookingPayload);
      },
      (err) => {
        assert.equal(err.statusCode, 403);
        assert.equal(err.message, "Sync your profile before creating bookings");
        return true;
      }
    );
  });

  it("createBooking proceeds past auth validation once user.id is populated following profile sync", async () => {
    const userWithDbId = {
      id: "db-user-uuid-777",
      firebaseUid: "firebase-uid-restored-session",
      email: "restored@example.com"
    };

    const bookingPayload = {
      hotelId: "hotel-1",
      roomId: "room-1",
      checkIn: "2026-08-10",
      checkOut: "2026-08-12",
      rooms: 1,
      guests: 2,
      guestName: "Restored User",
      guestEmail: "restored@example.com",
      guestPhone: "9876543210"
    };

    const mockClient = { id: "mock-tx" };
    const mockRepo = {
      getRoomForBooking: async () => null // Room not found triggers 404, proving user auth check passed!
    };
    const mockWithTx = async (cb) => cb(mockClient);

    await assert.rejects(
      async () => {
        await createBooking(userWithDbId, bookingPayload, {
          withTx: mockWithTx,
          bookingRepo: mockRepo
        });
      },
      (err) => {
        // Assert it passed 403 user.id check and reached room lookup (404)
        assert.equal(err.statusCode, 404);
        assert.equal(err.message, "Room not found");
        return true;
      }
    );
  });

  it("differentiates profile-sync 403 error message from general 403 errors", () => {
    const profileSyncError = { statusCode: 403, message: "Sync your profile before creating bookings" };
    const adminError = { statusCode: 403, message: "Admin access required" };

    const isProfileSync = (msg) => msg.includes("Sync your profile");

    assert.equal(isProfileSync(profileSyncError.message), true);
    assert.equal(isProfileSync(adminError.message), false);
  });

  it("syncProfileSchema safely parses empty strings, nulls, and valid profile values without 400 validation failures", async () => {
    const { syncProfileSchema } = await import("../src/validators/auth.validator.js");

    const validPayload = syncProfileSchema.safeParse({ body: { displayName: "Jane Doe", phoneNumber: "9090909090" } });
    assert.equal(validPayload.success, true);
    assert.equal(validPayload.data.body.displayName, "Jane Doe");
    assert.equal(validPayload.data.body.phoneNumber, "9090909090");

    const emptyStringPayload = syncProfileSchema.safeParse({ body: { displayName: "", phoneNumber: "" } });
    assert.equal(emptyStringPayload.success, true);
    assert.equal(emptyStringPayload.data.body.displayName, undefined);
    assert.equal(emptyStringPayload.data.body.phoneNumber, undefined);

    const nullPayload = syncProfileSchema.safeParse({ body: { displayName: null, phoneNumber: null } });
    assert.equal(nullPayload.success, true);
    assert.equal(nullPayload.data.body.displayName, undefined);
    assert.equal(nullPayload.data.body.phoneNumber, undefined);

    const invalidShortName = syncProfileSchema.safeParse({ body: { displayName: "a" } });
    assert.equal(invalidShortName.success, false);
  });
});

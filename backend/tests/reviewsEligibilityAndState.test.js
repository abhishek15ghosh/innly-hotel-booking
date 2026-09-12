import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  createReview,
  getMyReviewAndEligibility
} from "../src/services/review.service.js";

describe("Review Eligibility and State Service Unit Tests", () => {
  it("getMyReviewAndEligibility throws 401 when user is unauthenticated", async () => {
    await assert.rejects(
      async () => {
        await getMyReviewAndEligibility(null, "hotel-123");
      },
      (err) => {
        assert.equal(err.statusCode, 401);
        assert.match(err.message, /Authentication required/);
        return true;
      }
    );
  });

  it("createReview throws 403 when user has not synced profile", async () => {
    await assert.rejects(
      async () => {
        await createReview({ firebaseUid: "uid-only" }, { hotelId: "hotel-123", bookingId: "b-1", rating: 5, title: "Great", comment: "Awesome hotel" });
      },
      (err) => {
        assert.equal(err.statusCode, 403);
        assert.match(err.message, /User profile required/);
        return true;
      }
    );
  });

  it("returns canWriteReview: false when user has no completed stays for hotel", async () => {
    const mockUser = { id: "user-1" };
    let findEligibleArgs = null;
    let getUserReviewsArgs = null;

    const mockRepo = {
      findEligibleBookingForUser: async (client, userId, hotelId) => {
        findEligibleArgs = { client, userId, hotelId };
        return null;
      },
      getUserReviewsForHotel: async (client, userId, hotelId) => {
        getUserReviewsArgs = { client, userId, hotelId };
        return [];
      }
    };

    const result = await getMyReviewAndEligibility(mockUser, "hotel-1", {
      reviewRepo: mockRepo
    });

    assert.equal(findEligibleArgs.client, undefined);
    assert.equal(findEligibleArgs.userId, "user-1");
    assert.equal(findEligibleArgs.hotelId, "hotel-1");

    assert.equal(getUserReviewsArgs.client, undefined);
    assert.equal(getUserReviewsArgs.userId, "user-1");
    assert.equal(getUserReviewsArgs.hotelId, "hotel-1");

    assert.equal(result.canWriteReview, false);
    assert.equal(result.eligibleBooking, null);
    assert.deepEqual(result.myReviews, []);
    assert.equal(result.latestReview, null);
  });

  it("returns canWriteReview: true with eligibleBooking preserving bookingId from repository", async () => {
    const mockUser = { id: "user-1" };
    const mockEligibleBooking = {
      bookingId: "b-100",
      checkIn: "2026-08-10",
      checkOut: "2026-08-12",
      roomName: "Deluxe King"
    };

    const mockRepo = {
      findEligibleBookingForUser: async (client, userId, hotelId) => {
        assert.equal(client, undefined);
        assert.equal(userId, "user-1");
        assert.equal(hotelId, "hotel-1");
        return mockEligibleBooking;
      },
      getUserReviewsForHotel: async (client, userId, hotelId) => {
        assert.equal(client, undefined);
        return [];
      }
    };

    const result = await getMyReviewAndEligibility(mockUser, "hotel-1", {
      reviewRepo: mockRepo
    });

    assert.equal(result.canWriteReview, true);
    assert.equal(result.eligibleBooking.bookingId, "b-100");
    assert.equal(result.eligibleBooking.checkIn, "2026-08-10");
    assert.equal(result.eligibleBooking.checkOut, "2026-08-12");
    assert.equal(result.eligibleBooking.roomName, "Deluxe King");
    assert.deepEqual(result.myReviews, []);
  });

  it("handles repeat stay: older approved review + newer eligible completed stay", async () => {
    const mockUser = { id: "user-1" };
    const mockNewerBooking = {
      bookingId: "b-new-200",
      checkIn: "2026-08-15",
      checkOut: "2026-08-18",
      roomName: "Executive Suite"
    };
    const mockOlderReview = {
      id: "rev-old-1",
      bookingId: "b-old-100",
      rating: 5,
      title: "Loved July stay",
      comment: "Super clean",
      status: "approved",
      createdAt: "2026-07-20T10:00:00.000Z",
      updatedAt: "2026-07-20T10:00:00.000Z",
      moderatedAt: "2026-07-21T09:00:00.000Z"
    };

    const mockRepo = {
      findEligibleBookingForUser: async () => mockNewerBooking,
      getUserReviewsForHotel: async () => [mockOlderReview]
    };

    const result = await getMyReviewAndEligibility(mockUser, "hotel-1", {
      reviewRepo: mockRepo
    });

    assert.equal(result.canWriteReview, true);
    assert.equal(result.eligibleBooking.bookingId, "b-new-200");
    assert.equal(result.myReviews.length, 1);
    assert.equal(result.myReviews[0].id, "rev-old-1");
    assert.equal(result.myReviews[0].status, "approved");
    assert.equal(result.latestReview.id, "rev-old-1");
  });

  it("handles repeat stay: older pending review + newer eligible completed stay", async () => {
    const mockUser = { id: "user-1" };
    const mockNewerBooking = {
      bookingId: "b-new-300",
      checkIn: "2026-08-18",
      checkOut: "2026-08-20",
      roomName: "Deluxe King"
    };
    const mockOlderPendingReview = {
      id: "rev-old-2",
      bookingId: "b-old-200",
      rating: 4,
      title: "Nice first visit",
      comment: "Good location",
      status: "pending",
      createdAt: "2026-08-01T10:00:00.000Z"
    };

    const mockRepo = {
      findEligibleBookingForUser: async () => mockNewerBooking,
      getUserReviewsForHotel: async () => [mockOlderPendingReview]
    };

    const result = await getMyReviewAndEligibility(mockUser, "hotel-1", {
      reviewRepo: mockRepo
    });

    assert.equal(result.canWriteReview, true);
    assert.equal(result.eligibleBooking.bookingId, "b-new-300");
    assert.equal(result.myReviews[0].status, "pending");
  });

  it("handles repeat stay: older rejected review + newer eligible completed stay", async () => {
    const mockUser = { id: "user-1" };
    const mockNewerBooking = {
      bookingId: "b-new-400",
      checkIn: "2026-08-18",
      checkOut: "2026-08-20",
      roomName: "Studio"
    };
    const mockOlderRejectedReview = {
      id: "rev-old-3",
      bookingId: "b-old-300",
      rating: 3,
      title: "Average",
      comment: "Short comment",
      status: "rejected",
      createdAt: "2026-08-01T10:00:00.000Z"
    };

    const mockRepo = {
      findEligibleBookingForUser: async () => mockNewerBooking,
      getUserReviewsForHotel: async () => [mockOlderRejectedReview]
    };

    const result = await getMyReviewAndEligibility(mockUser, "hotel-1", {
      reviewRepo: mockRepo
    });

    assert.equal(result.canWriteReview, true);
    assert.equal(result.eligibleBooking.bookingId, "b-new-400");
    assert.equal(result.myReviews[0].status, "rejected");
  });

  it("createReview rejects submission if stay has not passed checkout date", async () => {
    const mockUser = { id: "user-1", displayName: "Abhishek Ghosh" };
    const mockClient = { id: "mock-client" };
    const mockWithTx = async (cb) => cb(mockClient);

    const mockRepo = {
      getBookingForReviewValidation: async () => ({
        bookingId: "b-future",
        userId: "user-1",
        hotelId: "hotel-1",
        bookingStatus: "confirmed",
        checkOut: "2026-08-25",
        paymentStatus: "captured"
      })
    };

    await assert.rejects(
      async () => {
        await createReview(
          mockUser,
          {
            hotelId: "hotel-1",
            bookingId: "b-future",
            rating: 5,
            title: "Future stay",
            comment: "Cannot review yet"
          },
          {
            withTx: mockWithTx,
            reviewRepo: mockRepo,
            now: new Date("2026-08-21T00:00:00.000Z")
          }
        );
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.match(err.message, /after completing your stay/);
        return true;
      }
    );
  });

  it("createReview catches unique constraint violation and returns HTTP 409", async () => {
    const mockUser = { id: "user-1", displayName: "Abhishek Ghosh" };
    const mockClient = { id: "mock-client" };
    const mockWithTx = async (cb) => cb(mockClient);

    const mockRepo = {
      getBookingForReviewValidation: async () => ({
        bookingId: "b-100",
        userId: "user-1",
        hotelId: "hotel-1",
        bookingStatus: "confirmed",
        checkOut: "2026-08-15",
        paymentStatus: "captured"
      }),
      createReview: async () => {
        const error = new Error("duplicate key value violates unique constraint");
        error.code = "23505";
        throw error;
      }
    };

    await assert.rejects(
      async () => {
        await createReview(
          mockUser,
          {
            hotelId: "hotel-1",
            bookingId: "b-100",
            rating: 5,
            title: "Second attempt",
            comment: "Should fail"
          },
          {
            withTx: mockWithTx,
            reviewRepo: mockRepo,
            now: new Date("2026-08-21T00:00:00.000Z")
          }
        );
      },
      (err) => {
        assert.equal(err.statusCode, 409);
        assert.match(err.message, /already been submitted for this stay/);
        return true;
      }
    );
  });

  it("resubmitting rejected review updates exact same review ID, sets status to pending, and maintains single row", async () => {
    const mockUser = { id: "user-1", displayName: "Abhishek Ghosh" };
    const mockClient = { id: "mock-client" };
    const mockWithTx = async (cb) => cb(mockClient);

    let resubmitArgs = null;
    const mockRepo = {
      getBookingForReviewValidation: async () => ({
        bookingId: "b-rejected-1",
        userId: "user-1",
        hotelId: "hotel-1",
        bookingStatus: "completed",
        checkOut: "2026-08-10",
        paymentStatus: "captured"
      }),
      resubmitRejectedReview: async (_client, args) => {
        resubmitArgs = args;
        return {
          id: args.reviewId,
          hotelId: args.hotelId,
          bookingId: args.bookingId,
          rating: args.rating,
          title: args.title,
          comment: args.comment,
          status: "pending",
          createdAt: "2026-08-01T10:00:00.000Z",
          updatedAt: "2026-08-21T12:00:00.000Z",
          moderatedAt: null
        };
      }
    };

    const result = await createReview(
      mockUser,
      {
        hotelId: "hotel-1",
        bookingId: "b-rejected-1",
        reviewId: "rev-rejected-1",
        rating: 4,
        title: "Revised title after rejection",
        comment: "Detailed helpful comment for the hotel stay."
      },
      {
        withTx: mockWithTx,
        reviewRepo: mockRepo,
        now: new Date("2026-08-21T00:00:00.000Z")
      }
    );

    assert.equal(result.id, "rev-rejected-1");
    assert.equal(result.status, "pending");
    assert.equal(result.title, "Revised title after rejection");
    assert.equal(result.moderatedAt, null);
    assert.equal(resubmitArgs.reviewId, "rev-rejected-1");
    assert.equal(resubmitArgs.userId, "user-1");
    assert.equal(resubmitArgs.bookingId, "b-rejected-1");
  });

  it("resubmission is rejected if booking payment is not captured", async () => {
    const mockUser = { id: "user-1" };
    const mockWithTx = async (cb) => cb({});

    const mockRepo = {
      getBookingForReviewValidation: async () => ({
        bookingId: "b-1",
        userId: "user-1",
        hotelId: "hotel-1",
        bookingStatus: "confirmed",
        checkOut: "2026-08-10",
        paymentStatus: "authorized" // Non-captured
      })
    };

    await assert.rejects(
      async () => {
        await createReview(
          mockUser,
          { hotelId: "hotel-1", bookingId: "b-1", reviewId: "rev-1", rating: 5, title: "Title", comment: "Comment" },
          { withTx: mockWithTx, reviewRepo: mockRepo, now: new Date("2026-08-21") }
        );
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.match(err.message, /confirmed captured payments/);
        return true;
      }
    );
  });

  it("resubmission is rejected if booking was cancelled", async () => {
    const mockUser = { id: "user-1" };
    const mockWithTx = async (cb) => cb({});

    const mockRepo = {
      getBookingForReviewValidation: async () => ({
        bookingId: "b-1",
        userId: "user-1",
        hotelId: "hotel-1",
        bookingStatus: "cancelled",
        checkOut: "2026-08-10",
        paymentStatus: "captured"
      })
    };

    await assert.rejects(
      async () => {
        await createReview(
          mockUser,
          { hotelId: "hotel-1", bookingId: "b-1", reviewId: "rev-1", rating: 5, title: "Title", comment: "Comment" },
          { withTx: mockWithTx, reviewRepo: mockRepo, now: new Date("2026-08-21") }
        );
      },
      (err) => {
        assert.equal(err.statusCode, 400);
        assert.match(err.message, /confirmed or completed stays/);
        return true;
      }
    );
  });

  it("resubmission is rejected if rejected review does not match user or stay", async () => {
    const mockUser = { id: "user-1" };
    const mockWithTx = async (cb) => cb({});

    const mockRepo = {
      getBookingForReviewValidation: async () => ({
        bookingId: "b-1",
        userId: "user-1",
        hotelId: "hotel-1",
        bookingStatus: "completed",
        checkOut: "2026-08-10",
        paymentStatus: "captured"
      }),
      resubmitRejectedReview: async () => null // Review row not found or not in rejected status
    };

    await assert.rejects(
      async () => {
        await createReview(
          mockUser,
          { hotelId: "hotel-1", bookingId: "b-1", reviewId: "rev-wrong", rating: 5, title: "Title", comment: "Comment" },
          { withTx: mockWithTx, reviewRepo: mockRepo, now: new Date("2026-08-21") }
        );
      },
      (err) => {
        assert.equal(err.statusCode, 404);
        assert.match(err.message, /Unable to resubmit review/);
        return true;
      }
    );
  });
});

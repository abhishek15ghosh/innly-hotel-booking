import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  listHotelReviews,
  moderateReview,
  formatSafeAuthorName
} from "../src/services/review.service.js";
import { refreshHotelRating } from "../src/repositories/review.repository.js";

describe("Review Public Listing, Privacy and Rating Recalculation Tests", () => {
  it("formatSafeAuthorName formats names safely and falls back to Innly Guest", () => {
    assert.equal(formatSafeAuthorName(null), "Innly Guest");
    assert.equal(formatSafeAuthorName(""), "Innly Guest");
    assert.equal(formatSafeAuthorName("   "), "Innly Guest");
    assert.equal(formatSafeAuthorName("Abhishek"), "Abhishek");
    assert.equal(formatSafeAuthorName("Abhishek Ghosh"), "Abhishek G.");
    assert.equal(formatSafeAuthorName("Abhishek Kumar Ghosh"), "Abhishek G.");
    assert.equal(formatSafeAuthorName("  Priya   Sharma  "), "Priya S.");
  });

  it("listHotelReviews passes real repository signatures (client=undefined, limit, offset) and maps return shapes", async () => {
    const mockHotelId = "11111111-1111-1111-1111-111111111111";
    let getApprovedArgs = null;
    let getDistArgs = null;

    const mockRepo = {
      getApprovedReviewsByHotelId: async (client, hotelId, limit, offset) => {
        getApprovedArgs = { client, hotelId, limit, offset };
        return [
          {
            id: "rev-2",
            hotelId: mockHotelId,
            rating: 5,
            title: "Superb Stay",
            comment: "Loved the food",
            createdAt: "2026-08-19T10:00:00.000Z",
            rawAuthorName: "Abhishek Ghosh",
            isVerifiedStay: true
          },
          {
            id: "rev-1",
            hotelId: mockHotelId,
            rating: 4,
            title: "Comfortable",
            comment: "Great staff",
            createdAt: "2026-08-18T08:00:00.000Z",
            rawAuthorName: null,
            isVerifiedStay: false
          }
        ];
      },
      getHotelRatingDistribution: async (client, hotelId) => {
        getDistArgs = { client, hotelId };
        return {
          avgRating: 4.5,
          reviewCount: 25,
          ratingDistribution: {
            "5": 15,
            "4": 8,
            "3": 2,
            "2": 0,
            "1": 0
          }
        };
      }
    };

    // Page 2 with limit 10 -> offset should be (2 - 1) * 10 = 10
    const response = await listHotelReviews(mockHotelId, { page: 2, limit: 10 }, {
      reviewRepo: mockRepo
    });

    // Check repository invocation contracts
    assert.equal(getApprovedArgs.client, undefined);
    assert.equal(getApprovedArgs.hotelId, mockHotelId);
    assert.equal(getApprovedArgs.limit, 10);
    assert.equal(getApprovedArgs.offset, 10);

    assert.equal(getDistArgs.client, undefined);
    assert.equal(getDistArgs.hotelId, mockHotelId);

    // Response structure
    assert.equal(response.page, 2);
    assert.equal(response.limit, 10);
    assert.equal(response.total, 25); // Total comes from reviewCount
    assert.equal(response.items.length, 2);

    assert.equal(response.items[0].id, "rev-2");
    assert.equal(response.items[0].authorName, "Abhishek G.");
    assert.equal(response.items[0].isVerifiedStay, true);
    assert.equal(response.items[0].rawAuthorName, undefined);
    assert.equal(response.items[0].userId, undefined);
    assert.equal(response.items[0].bookingId, undefined);

    assert.equal(response.items[1].authorName, "Innly Guest");
    assert.equal(response.items[1].isVerifiedStay, false);

    // Verify flat top-level rating and distribution fields
    assert.equal(response.avgRating, 4.5);
    assert.equal(response.reviewCount, 25);
    assert.equal(response.ratingDistribution["5"], 15);
    assert.equal(response.ratingDistribution["4"], 8);
    assert.equal(response.ratingDistribution["3"], 2);
    assert.equal(response.ratingDistribution["2"], 0);
    assert.equal(response.ratingDistribution["1"], 0);

    // Verify ratingSummary nested object is completely absent
    assert.equal(response.ratingSummary, undefined);
  });

  it("moderateReview updates review status and recalculates hotel rating in same transaction", async () => {
    const mockClient = { id: "mock-tx-client" };
    const mockWithTx = async (cb) => cb(mockClient);

    let moderateArgs = null;
    let refreshArgs = null;

    const mockRepo = {
      moderateReview: async (client, reviewId, status) => {
        assert.equal(client, mockClient);
        moderateArgs = { reviewId, status };
        return { id: reviewId, hotelId: "hotel-99", status };
      },
      refreshHotelRating: async (client, hotelId) => {
        assert.equal(client, mockClient);
        refreshArgs = { hotelId };
      }
    };

    const result = await moderateReview("rev-10", "approved", {
      withTx: mockWithTx,
      reviewRepo: mockRepo
    });

    assert.equal(result.id, "rev-10");
    assert.equal(result.status, "approved");
    assert.deepEqual(moderateArgs, { reviewId: "rev-10", status: "approved" });
    assert.deepEqual(refreshArgs, { hotelId: "hotel-99" });
  });

  it("refreshHotelRating executes zero-safe scalar update query", async () => {
    let executedQuery = null;
    let executedValues = null;

    const fakeClient = {
      query: async (sql, values) => {
        executedQuery = sql;
        executedValues = values;
        return { rowCount: 1 };
      }
    };

    await refreshHotelRating(fakeClient, "hotel-123");

    assert.ok(executedQuery.includes("UPDATE hotels"));
    assert.ok(executedQuery.includes("COALESCE"));
    assert.ok(executedQuery.includes("ROUND(AVG(rating)::numeric, 2)"));
    assert.ok(executedQuery.includes("COUNT(*)"));
    assert.ok(executedQuery.includes("status = 'approved'"));
    assert.deepEqual(executedValues, ["hotel-123"]);
  });
});

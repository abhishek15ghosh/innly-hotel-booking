package com.innly.hotelbooking

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.innly.hotelbooking.data.remote.ApiResponse
import com.innly.hotelbooking.data.remote.ReviewListResponseDto
import com.innly.hotelbooking.data.remote.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewResponseParsingTest {

    private val gson = Gson()

    @Test
    fun `parse flat public reviews backend JSON response into ReviewListResponseDto and verify all fields`() {
        val backendJson = """
            {
              "success": true,
              "data": {
                "items": [
                  {
                    "id": "rev-1",
                    "rating": 5,
                    "title": "Outstanding hospitality",
                    "comment": "The room was spacious and the staff went above and beyond.",
                    "createdAt": "2026-08-20T14:30:00.000Z",
                    "authorName": "Abhishek G.",
                    "isVerifiedStay": true
                  },
                  {
                    "id": "rev-2",
                    "rating": 4,
                    "title": "Good city view",
                    "comment": "Nice stay overall, breakfast was great.",
                    "createdAt": "2026-08-19T09:15:00.000Z",
                    "authorName": "Innly Guest",
                    "isVerifiedStay": false
                  }
                ],
                "page": 2,
                "limit": 10,
                "total": 42,
                "avgRating": 4.65,
                "reviewCount": 42,
                "ratingDistribution": {
                  "5": 28,
                  "4": 10,
                  "3": 3,
                  "2": 1,
                  "1": 0
                }
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<ReviewListResponseDto>>() {}.type
        val apiResponse: ApiResponse<ReviewListResponseDto> = gson.fromJson(backendJson, type)

        assertTrue(apiResponse.success)
        val dto = apiResponse.data
        assertNotNull(dto)

        // Pagination fields
        assertEquals(2, dto.page)
        assertEquals(10, dto.limit)
        assertEquals(42, dto.total)

        // Top-level Rating summary fields
        assertEquals(4.65, dto.avgRating, 0.001)
        assertEquals(42, dto.reviewCount)
        assertEquals(28, dto.ratingDistribution["5"])
        assertEquals(10, dto.ratingDistribution["4"])
        assertEquals(3, dto.ratingDistribution["3"])
        assertEquals(1, dto.ratingDistribution["2"])
        assertEquals(0, dto.ratingDistribution["1"])

        // Items fields
        assertEquals(2, dto.items.size)
        val firstItem = dto.items[0]
        assertEquals("rev-1", firstItem.id)
        assertEquals(5, firstItem.rating)
        assertEquals("Outstanding hospitality", firstItem.title)
        assertEquals("The room was spacious and the staff went above and beyond.", firstItem.comment)
        assertEquals("Abhishek G.", firstItem.authorName)
        assertTrue(firstItem.isVerifiedStay)

        val secondItem = dto.items[1]
        assertEquals("rev-2", secondItem.id)
        assertEquals(4, secondItem.rating)
        assertEquals("Innly Guest", secondItem.authorName)
        assertFalse(secondItem.isVerifiedStay)

        // Verify domain mapping
        val domain = dto.toDomain()
        assertEquals(2, domain.page)
        assertEquals(10, domain.limit)
        assertEquals(42, domain.total)
        assertEquals(4.65, domain.ratingSummary.avgRating, 0.001)
        assertEquals(42, domain.ratingSummary.reviewCount)
        assertEquals(28, domain.ratingSummary.ratingDistribution["5"])
        assertEquals(2, domain.items.size)
        assertEquals("Abhishek G.", domain.items[0].authorName)
        assertTrue(domain.items[0].isVerifiedStay)
    }
}

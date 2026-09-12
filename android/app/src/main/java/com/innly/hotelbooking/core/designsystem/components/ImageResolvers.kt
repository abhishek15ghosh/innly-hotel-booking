package com.innly.hotelbooking.core.designsystem.components

import com.innly.hotelbooking.domain.model.Hotel
import com.innly.hotelbooking.domain.model.Room

private val curatedBedroomImages = listOf(
    "https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1455587734955-081b22074882?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1445019980597-93fa8acb246c?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1760573776062-7d2a7baeb49d?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1744187170993-6591089e2674?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1770232274485-b35ee5092cbe?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1739590269025-07766e4ab657?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1732089059979-c35ea80150d8?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1755613708939-d572099433ab?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1762117360848-be7490786979?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1754294681773-25c7a42e503b?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1725962479542-1be0a6b0d444?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1648383228240-6ed939727ad6?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1743410973975-c676fa6bf885?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1719464515608-dcc7343fba4e?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1737517302831-e7b8a8eaa97c?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1740324351912-b9189685ab1a?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1742039953129-e4edcc82d319?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1743410974154-1f8c5f9269f3?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1683237854477-d626c7983841?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1750277104428-b60723c23eb0?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1761470371217-a4de0ff0e8df?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1757524808357-01d16abdb1b1?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1763559992588-68db5ae23ff9?auto=format&fit=crop&w=1200&q=80",
    "https://images.unsplash.com/photo-1760067537116-de1f76fe8f95?auto=format&fit=crop&w=1200&q=80",
)

private fun stableIndex(key: String, offset: Int = 0): Int {
    val hash = key.fold(17L) { acc, char -> (acc * 31L) + char.code.toLong() }
    val positiveHash = ((hash % Int.MAX_VALUE) + Int.MAX_VALUE) % Int.MAX_VALUE
    return ((positiveHash.toInt() + offset) % curatedBedroomImages.size)
}

private fun hotelBedroomImageUrl(hotelId: String): String =
    curatedBedroomImages[stableIndex(hotelId, offset = 7)]

private fun roomBedroomImageUrl(roomId: String): String =
    curatedBedroomImages[stableIndex(roomId, offset = 19)]

fun Hotel.cardImageUrl(): String {
    return thumbnailUrl.takeIf { it.isNotBlank() }
        ?: images.firstOrNull { it.isNotBlank() }
        ?: hotelBedroomImageUrl(id)
}

fun Hotel.detailImageUrl(): String {
    return rooms.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl
        ?: rooms.firstOrNull()?.images?.firstOrNull { it.isNotBlank() }
        ?: images.firstOrNull { it.isNotBlank() }
        ?: thumbnailUrl.takeIf { it.isNotBlank() }
        ?: hotelBedroomImageUrl("$id-detail")
}

fun Room.displayImageUrl(): String {
    return thumbnailUrl.takeIf { it.isNotBlank() }
        ?: images.firstOrNull { it.isNotBlank() }
        ?: roomBedroomImageUrl(id)
}

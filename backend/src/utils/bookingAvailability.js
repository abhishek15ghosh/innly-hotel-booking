import { ApiError } from "./apiError.js";

export function formatDateString(dateVal) {
  if (!dateVal) return "";
  if (typeof dateVal === "string") {
    return dateVal.slice(0, 10);
  }
  if (dateVal instanceof Date) {
    const year = dateVal.getFullYear();
    const month = String(dateVal.getMonth() + 1).padStart(2, "0");
    const day = String(dateVal.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }
  return String(dateVal).slice(0, 10);
}

export function getStayDates(checkIn, checkOut) {
  const cleanCheckIn = formatDateString(checkIn);
  const cleanCheckOut = formatDateString(checkOut);

  const start = new Date(`${cleanCheckIn}T00:00:00.000Z`);
  const end = new Date(`${cleanCheckOut}T00:00:00.000Z`);

  if (!cleanCheckIn || !cleanCheckOut || Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || start >= end) {
    throw new ApiError(400, "Invalid check-in/check-out date range");
  }

  const dates = [];
  const cursor = new Date(start);
  while (cursor < end) {
    dates.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return dates;
}

export function summarizeAvailability(inventoryRows, stayDates, roomsRequested) {
  const rowMap = new Map(
    inventoryRows.map((row) => [formatDateString(row.inventory_date), row])
  );

  const dailyBreakdown = stayDates.map((date) => {
    const row = rowMap.get(date);
    return {
      date,
      price: row?.price ?? 0,
      availableInventory: row ? row.total_inventory - row.booked_inventory : 0
    };
  });

  const available =
    dailyBreakdown.length === stayDates.length &&
    dailyBreakdown.every((row) => row.availableInventory >= roomsRequested && row.price > 0);

  const totalAmount = available
    ? dailyBreakdown.reduce((sum, day) => sum + day.price * roomsRequested, 0)
    : 0;

  return {
    available,
    nights: stayDates.length,
    totalAmount,
    currency: "INR",
    dailyBreakdown
  };
}

WITH hotel_image_order AS (
  SELECT
    hi.id,
    row_number() OVER (ORDER BY hi.hotel_id, hi.position, hi.id) AS seq
  FROM hotel_images hi
),
curated_images AS (
  SELECT ARRAY[
    'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1455587734955-081b22074882?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1445019980597-93fa8acb246c?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1760573776062-7d2a7baeb49d?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1744187170993-6591089e2674?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1770232274485-b35ee5092cbe?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1739590269025-07766e4ab657?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1732089059979-c35ea80150d8?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1755613708939-d572099433ab?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1762117360848-be7490786979?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1754294681773-25c7a42e503b?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1725962479542-1be0a6b0d444?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1648383228240-6ed939727ad6?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1743410973975-c676fa6bf885?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1719464515608-dcc7343fba4e?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1737517302831-e7b8a8eaa97c?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1740324351912-b9189685ab1a?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1742039953129-e4edcc82d319?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1743410974154-1f8c5f9269f3?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1683237854477-d626c7983841?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1750277104428-b60723c23eb0?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1761470371217-a4de0ff0e8df?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1757524808357-01d16abdb1b1?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1763559992588-68db5ae23ff9?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1760067537116-de1f76fe8f95?auto=format&fit=crop&w=1200&q=80'
  ] AS urls
)
UPDATE hotel_images hi
SET image_url = curated_images.urls[1 + ((hotel_image_order.seq - 1) % array_length(curated_images.urls, 1))]
FROM hotel_image_order, curated_images
WHERE hi.id = hotel_image_order.id;

WITH room_image_order AS (
  SELECT
    ri.id,
    row_number() OVER (ORDER BY ri.room_id, ri.position, ri.id) AS seq
  FROM room_images ri
),
curated_images AS (
  SELECT ARRAY[
    'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1455587734955-081b22074882?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1445019980597-93fa8acb246c?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1760573776062-7d2a7baeb49d?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1744187170993-6591089e2674?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1770232274485-b35ee5092cbe?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1739590269025-07766e4ab657?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1732089059979-c35ea80150d8?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1755613708939-d572099433ab?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1762117360848-be7490786979?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1754294681773-25c7a42e503b?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1725962479542-1be0a6b0d444?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1648383228240-6ed939727ad6?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1743410973975-c676fa6bf885?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1719464515608-dcc7343fba4e?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1737517302831-e7b8a8eaa97c?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1740324351912-b9189685ab1a?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1742039953129-e4edcc82d319?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1743410974154-1f8c5f9269f3?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1683237854477-d626c7983841?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1750277104428-b60723c23eb0?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1761470371217-a4de0ff0e8df?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1757524808357-01d16abdb1b1?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1763559992588-68db5ae23ff9?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1760067537116-de1f76fe8f95?auto=format&fit=crop&w=1200&q=80'
  ] AS urls
)
UPDATE room_images ri
SET image_url = curated_images.urls[1 + ((room_image_order.seq - 1) % array_length(curated_images.urls, 1))]
FROM room_image_order, curated_images
WHERE ri.id = room_image_order.id;

UPDATE hotel_images
SET image_url = CASE
  WHEN position = 1 THEN 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80'
  WHEN position = 2 THEN 'https://images.unsplash.com/photo-1455587734955-081b22074882?auto=format&fit=crop&w=1200&q=80'
  ELSE image_url
END
WHERE hotel_id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'::uuid
  AND position IN (1, 2);

UPDATE room_images
SET image_url = CASE
  WHEN room_id = 'eeeeeeee-1000-0000-0000-000000000001'::uuid THEN 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80'
  WHEN room_id = 'eeeeeeee-2000-0000-0000-000000000002'::uuid THEN 'https://images.unsplash.com/photo-1455587734955-081b22074882?auto=format&fit=crop&w=1200&q=80'
  ELSE image_url
END
WHERE room_id IN (
  'eeeeeeee-1000-0000-0000-000000000001'::uuid,
  'eeeeeeee-2000-0000-0000-000000000002'::uuid
)
  AND position = 1;

UPDATE hotel_images
SET image_url = CASE
  WHEN position = 1 THEN 'https://images.unsplash.com/photo-1760573776062-7d2a7baeb49d?auto=format&fit=crop&w=1200&q=80'
  WHEN position = 2 THEN 'https://images.unsplash.com/photo-1744187170993-6591089e2674?auto=format&fit=crop&w=1200&q=80'
  ELSE image_url
END
WHERE hotel_id = '23232323-2323-2323-2323-232323232323'::uuid
  AND position IN (1, 2);

UPDATE room_images
SET image_url = CASE
  WHEN room_id = '23232323-1000-0000-0000-000000000001'::uuid THEN 'https://images.unsplash.com/photo-1760573776062-7d2a7baeb49d?auto=format&fit=crop&w=1200&q=80'
  WHEN room_id = '23232323-2000-0000-0000-000000000002'::uuid THEN 'https://images.unsplash.com/photo-1744187170993-6591089e2674?auto=format&fit=crop&w=1200&q=80'
  ELSE image_url
END
WHERE room_id IN (
  '23232323-1000-0000-0000-000000000001'::uuid,
  '23232323-2000-0000-0000-000000000002'::uuid
)
  AND position = 1;

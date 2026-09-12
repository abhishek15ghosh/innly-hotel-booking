INSERT INTO hotels (
  id, name, slug, description, city, address, star_rating, avg_rating, review_count, cancellation_policy
)
VALUES
  (
    '11111111-1111-1111-1111-111111111111',
    'The Marine Grand',
    'the-marine-grand',
    'Sea-facing luxury hotel for business trips and weekend escapes.',
    'Mumbai',
    'Nariman Point, Mumbai',
    4.5,
    4.6,
    328,
    'Free cancellation up to 24 hours before check-in.'
  ),
  (
    '22222222-2222-2222-2222-222222222222',
    'Palm Horizon Resort',
    'palm-horizon-resort',
    'Relaxed beach resort with family rooms, pool access, and sunset dining.',
    'Goa',
    'Candolim Beach Road, Goa',
    4.0,
    4.4,
    211,
    'Free cancellation up to 48 hours before check-in.'
  ),
  (
    '33333333-3333-3333-3333-333333333333',
    'Skyline Suites',
    'skyline-suites',
    'Modern urban stay with premium workspace amenities and rooftop dining.',
    'Bengaluru',
    'Indiranagar 100 Feet Road, Bengaluru',
    4.5,
    4.7,
    156,
    'Free cancellation up to 24 hours before check-in.'
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO hotel_images (hotel_id, image_url, position)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80', 1),
  ('11111111-1111-1111-1111-111111111111', 'https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?auto=format&fit=crop&w=1200&q=80', 2),
  ('22222222-2222-2222-2222-222222222222', 'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?auto=format&fit=crop&w=1200&q=80', 1),
  ('22222222-2222-2222-2222-222222222222', 'https://images.unsplash.com/photo-1522798514-97ceb8c4f1c8?auto=format&fit=crop&w=1200&q=80', 2),
  ('33333333-3333-3333-3333-333333333333', 'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80', 1),
  ('33333333-3333-3333-3333-333333333333', 'https://images.unsplash.com/photo-1445019980597-93fa8acb246c?auto=format&fit=crop&w=1200&q=80', 2)
ON CONFLICT DO NOTHING;

INSERT INTO hotel_amenities (hotel_id, amenity)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'wifi'),
  ('11111111-1111-1111-1111-111111111111', 'pool'),
  ('11111111-1111-1111-1111-111111111111', 'breakfast'),
  ('11111111-1111-1111-1111-111111111111', 'parking'),
  ('22222222-2222-2222-2222-222222222222', 'wifi'),
  ('22222222-2222-2222-2222-222222222222', 'pool'),
  ('22222222-2222-2222-2222-222222222222', 'beach-access'),
  ('22222222-2222-2222-2222-222222222222', 'spa'),
  ('33333333-3333-3333-3333-333333333333', 'wifi'),
  ('33333333-3333-3333-3333-333333333333', 'workspace'),
  ('33333333-3333-3333-3333-333333333333', 'gym'),
  ('33333333-3333-3333-3333-333333333333', 'breakfast')
ON CONFLICT DO NOTHING;

INSERT INTO rooms (
  id, hotel_id, name, description, capacity, bed_type, base_price, total_inventory
)
VALUES
  (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    '11111111-1111-1111-1111-111111111111',
    'Deluxe King',
    'Sea-view king room with breakfast and work desk.',
    2,
    'King Bed',
    5400,
    6
  ),
  (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
    '11111111-1111-1111-1111-111111111111',
    'Executive Twin',
    'Spacious business room with twin beds and lounge access.',
    2,
    'Twin Beds',
    6200,
    4
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
    '22222222-2222-2222-2222-222222222222',
    'Garden Villa',
    'Private garden-facing villa for couples and families.',
    3,
    'Queen Bed',
    4800,
    5
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
    '22222222-2222-2222-2222-222222222222',
    'Family Suite',
    'Large suite with separate living area and resort access.',
    4,
    '2 Queen Beds',
    7600,
    3
  ),
  (
    'cccccccc-cccc-cccc-cccc-ccccccccccc1',
    '33333333-3333-3333-3333-333333333333',
    'Studio Room',
    'Smart city room built for short business stays.',
    2,
    'Queen Bed',
    4300,
    8
  ),
  (
    'cccccccc-cccc-cccc-cccc-ccccccccccc2',
    '33333333-3333-3333-3333-333333333333',
    'Skyline Suite',
    'Premium suite with skyline view, meeting nook, and lounge chair.',
    3,
    'King Bed',
    6900,
    4
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO room_images (room_id, image_url, position)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80', 1),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80', 1),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80', 1),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80', 1),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc1', 'https://images.unsplash.com/photo-1455587734955-081b22074882?auto=format&fit=crop&w=1200&q=80', 1),
  ('cccccccc-cccc-cccc-cccc-ccccccccccc2', 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80', 1)
ON CONFLICT DO NOTHING;

INSERT INTO room_inventory (room_id, inventory_date, total_inventory, booked_inventory, price_override)
SELECT room_id, inventory_date, total_inventory, 0, price_override
FROM (
  SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid AS room_id,
    gs::date AS inventory_date,
    6 AS total_inventory,
    5400 AS price_override
  FROM generate_series(current_date, current_date + INTERVAL '120 day', INTERVAL '1 day') gs

  UNION ALL

  SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2'::uuid,
    gs::date,
    4,
    6200
  FROM generate_series(current_date, current_date + INTERVAL '120 day', INTERVAL '1 day') gs

  UNION ALL

  SELECT
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1'::uuid,
    gs::date,
    5,
    4800
  FROM generate_series(current_date, current_date + INTERVAL '120 day', INTERVAL '1 day') gs

  UNION ALL

  SELECT
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2'::uuid,
    gs::date,
    3,
    7600
  FROM generate_series(current_date, current_date + INTERVAL '120 day', INTERVAL '1 day') gs

  UNION ALL

  SELECT
    'cccccccc-cccc-cccc-cccc-ccccccccccc1'::uuid,
    gs::date,
    8,
    4300
  FROM generate_series(current_date, current_date + INTERVAL '120 day', INTERVAL '1 day') gs

  UNION ALL

  SELECT
    'cccccccc-cccc-cccc-cccc-ccccccccccc2'::uuid,
    gs::date,
    4,
    6900
  FROM generate_series(current_date, current_date + INTERVAL '120 day', INTERVAL '1 day') gs
) inventory_seed
ON CONFLICT (room_id, inventory_date) DO UPDATE
SET
  total_inventory = EXCLUDED.total_inventory,
  price_override = EXCLUDED.price_override,
  updated_at = NOW();

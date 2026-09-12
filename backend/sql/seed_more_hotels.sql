WITH hotel_seed (
  id,
  name,
  slug,
  description,
  city,
  address,
  star_rating,
  avg_rating,
  review_count,
  cancellation_policy,
  image_primary,
  image_secondary,
  amenities,
  room_one_name,
  room_one_price,
  room_one_inventory,
  room_two_name,
  room_two_price,
  room_two_inventory
) AS (
  VALUES
    (
      '44444444-4444-4444-4444-444444444444'::uuid,
      'Grand Lotus Palace',
      'grand-lotus-palace',
      'Polished city hotel built for corporate stays and weekend luxury in central Delhi.',
      'New Delhi',
      'Aerocity, New Delhi',
      4.5,
      4.8,
      412,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/grand-lotus-palace-1/1200/800',
      'https://picsum.photos/seed/grand-lotus-palace-2/1200/800',
      ARRAY['wifi', 'breakfast', 'parking', 'gym']::text[],
      'Premier Room',
      5900,
      7,
      'Capital Suite',
      9100,
      4
    ),
    (
      '55555555-5555-5555-5555-555555555555'::uuid,
      'Amber Courtyard',
      'amber-courtyard',
      'A warm Jaipur stay inspired by courtyard architecture and slow luxury details.',
      'Jaipur',
      'MI Road, Jaipur',
      4.0,
      4.5,
      287,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/amber-courtyard-1/1200/800',
      'https://picsum.photos/seed/amber-courtyard-2/1200/800',
      ARRAY['wifi', 'pool', 'breakfast', 'parking']::text[],
      'Courtyard Room',
      4700,
      6,
      'Amber Suite',
      7600,
      3
    ),
    (
      '66666666-6666-6666-6666-666666666666'::uuid,
      'Lakeview Haveli',
      'lakeview-haveli',
      'Heritage-style stay with rooftop dining and views across the old city skyline.',
      'Udaipur',
      'Hanuman Ghat, Udaipur',
      4.5,
      4.7,
      198,
      'Free cancellation up to 48 hours before check-in.',
      'https://picsum.photos/seed/lakeview-haveli-1/1200/800',
      'https://picsum.photos/seed/lakeview-haveli-2/1200/800',
      ARRAY['wifi', 'restaurant', 'rooftop', 'breakfast']::text[],
      'Heritage Room',
      5200,
      5,
      'Lake Suite',
      8400,
      3
    ),
    (
      '77777777-7777-7777-7777-777777777777'::uuid,
      'The Chennai House',
      'the-chennai-house',
      'Business-forward hospitality with efficient rooms and strong food options near the city core.',
      'Chennai',
      'T Nagar, Chennai',
      4.0,
      4.3,
      244,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/the-chennai-house-1/1200/800',
      'https://picsum.photos/seed/the-chennai-house-2/1200/800',
      ARRAY['wifi', 'breakfast', 'workspace', 'parking']::text[],
      'Business Room',
      4100,
      8,
      'Marina Suite',
      6800,
      4
    ),
    (
      '88888888-8888-8888-8888-888888888888'::uuid,
      'Deccan Orchard',
      'deccan-orchard',
      'Contemporary Hyderabad stay with calm interiors, banquet spaces, and fast airport access.',
      'Hyderabad',
      'Gachibowli, Hyderabad',
      4.5,
      4.6,
      265,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/deccan-orchard-1/1200/800',
      'https://picsum.photos/seed/deccan-orchard-2/1200/800',
      ARRAY['wifi', 'pool', 'gym', 'parking']::text[],
      'Orchard Room',
      5000,
      7,
      'Executive Suite',
      7900,
      4
    ),
    (
      '99999999-9999-9999-9999-999999999999'::uuid,
      'Monsoon Residency',
      'monsoon-residency',
      'Modern Pune hotel aimed at startup teams, conferences, and long work trips.',
      'Pune',
      'Baner, Pune',
      4.0,
      4.4,
      173,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/monsoon-residency-1/1200/800',
      'https://picsum.photos/seed/monsoon-residency-2/1200/800',
      ARRAY['wifi', 'workspace', 'breakfast', 'gym']::text[],
      'Studio Deluxe',
      3900,
      9,
      'Monsoon Suite',
      6400,
      4
    ),
    (
      'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
      'Riverfront Pavilion',
      'riverfront-pavilion',
      'A polished riverside property with banquet halls and city-center access.',
      'Kolkata',
      'Prinsep Ghat Road, Kolkata',
      4.0,
      4.3,
      221,
      'Free cancellation up to 48 hours before check-in.',
      'https://picsum.photos/seed/riverfront-pavilion-1/1200/800',
      'https://picsum.photos/seed/riverfront-pavilion-2/1200/800',
      ARRAY['wifi', 'breakfast', 'parking', 'restaurant']::text[],
      'Pavilion Room',
      4500,
      7,
      'River Suite',
      7100,
      3
    ),
    (
      'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid,
      'Spice Harbor Hotel',
      'spice-harbor-hotel',
      'Coastal Kochi stay with heritage accents and easy ferry access.',
      'Kochi',
      'Fort Kochi, Kochi',
      4.5,
      4.7,
      194,
      'Free cancellation up to 48 hours before check-in.',
      'https://picsum.photos/seed/spice-harbor-hotel-1/1200/800',
      'https://picsum.photos/seed/spice-harbor-hotel-2/1200/800',
      ARRAY['wifi', 'breakfast', 'spa', 'parking']::text[],
      'Harbor Room',
      5300,
      6,
      'Spice Suite',
      8600,
      3
    ),
    (
      'cccccccc-cccc-cccc-cccc-cccccccccccc'::uuid,
      'Ganga Heritage Stay',
      'ganga-heritage-stay',
      'Boutique Varanasi stay designed for culture-rich visits and calm river mornings.',
      'Varanasi',
      'Dashashwamedh Ghat, Varanasi',
      4.0,
      4.5,
      149,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/ganga-heritage-stay-1/1200/800',
      'https://picsum.photos/seed/ganga-heritage-stay-2/1200/800',
      ARRAY['wifi', 'breakfast', 'rooftop', 'airport-transfer']::text[],
      'Ghats View Room',
      4200,
      5,
      'Heritage Suite',
      6900,
      2
    ),
    (
      'dddddddd-dddd-dddd-dddd-dddddddddddd'::uuid,
      'Sabarmati Grand',
      'sabarmati-grand',
      'A practical upscale stay with polished event spaces and reliable business amenities.',
      'Ahmedabad',
      'Ashram Road, Ahmedabad',
      4.0,
      4.4,
      205,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/sabarmati-grand-1/1200/800',
      'https://picsum.photos/seed/sabarmati-grand-2/1200/800',
      ARRAY['wifi', 'parking', 'breakfast', 'gym']::text[],
      'Grand Room',
      4300,
      8,
      'Sabarmati Suite',
      7000,
      3
    ),
    (
      'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'::uuid,
      'Cedar Peak Retreat',
      'cedar-peak-retreat',
      'A mountain retreat with warm interiors, fireplaces, and long-view balconies.',
      'Manali',
      'Old Manali Road, Manali',
      4.5,
      4.8,
      267,
      'Free cancellation up to 72 hours before check-in.',
      'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1455587734955-081b22074882?auto=format&fit=crop&w=1200&q=80',
      ARRAY['wifi', 'mountain-view', 'bonfire', 'breakfast']::text[],
      'Valley Room',
      6100,
      5,
      'Cedar Suite',
      9400,
      3
    ),
    (
      'ffffffff-ffff-ffff-ffff-ffffffffffff'::uuid,
      'Snowline Manor',
      'snowline-manor',
      'Comfort-first hillside hotel with a strong winter-season occupancy profile.',
      'Shimla',
      'Mall Road, Shimla',
      4.0,
      4.5,
      189,
      'Free cancellation up to 48 hours before check-in.',
      'https://picsum.photos/seed/snowline-manor-1/1200/800',
      'https://picsum.photos/seed/snowline-manor-2/1200/800',
      ARRAY['wifi', 'breakfast', 'heater', 'parking']::text[],
      'Hillside Room',
      5600,
      6,
      'Snowline Suite',
      8600,
      3
    ),
    (
      '12121212-1212-1212-1212-121212121212'::uuid,
      'Desert Crown Camp & Hotel',
      'desert-crown-camp-hotel',
      'Hybrid camp-hotel property built for sunset dune experiences and premium desert stays.',
      'Jaisalmer',
      'Sam Sand Dunes, Jaisalmer',
      4.5,
      4.7,
      176,
      'Free cancellation up to 72 hours before check-in.',
      'https://picsum.photos/seed/desert-crown-camp-hotel-1/1200/800',
      'https://picsum.photos/seed/desert-crown-camp-hotel-2/1200/800',
      ARRAY['wifi', 'cultural-show', 'breakfast', 'parking']::text[],
      'Luxury Tent',
      6500,
      6,
      'Desert Suite',
      9800,
      3
    ),
    (
      '13131313-1313-1313-1313-131313131313'::uuid,
      'Royal Neem Hotel',
      'royal-neem-hotel',
      'Classic Lucknow hospitality with banquet dining and family-friendly room planning.',
      'Lucknow',
      'Hazratganj, Lucknow',
      4.0,
      4.4,
      158,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/royal-neem-hotel-1/1200/800',
      'https://picsum.photos/seed/royal-neem-hotel-2/1200/800',
      ARRAY['wifi', 'breakfast', 'parking', 'restaurant']::text[],
      'Classic Room',
      4000,
      8,
      'Royal Suite',
      6800,
      3
    ),
    (
      '14141414-1414-1414-1414-141414141414'::uuid,
      'Harbourline Suites',
      'harbourline-suites',
      'A practical sea-facing stay for business and port-city travel with larger suite inventory.',
      'Visakhapatnam',
      'Beach Road, Visakhapatnam',
      4.0,
      4.3,
      134,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/harbourline-suites-1/1200/800',
      'https://picsum.photos/seed/harbourline-suites-2/1200/800',
      ARRAY['wifi', 'sea-view', 'breakfast', 'parking']::text[],
      'Harbor Room',
      4700,
      7,
      'Ocean Suite',
      7300,
      3
    ),
    (
      '15151515-1515-1515-1515-151515151515'::uuid,
      'Nilgiri Nest',
      'nilgiri-nest',
      'Cool-weather Ooty retreat with tea-garden proximity and slow-travel comfort.',
      'Ooty',
      'Charing Cross, Ooty',
      4.5,
      4.7,
      142,
      'Free cancellation up to 48 hours before check-in.',
      'https://picsum.photos/seed/nilgiri-nest-1/1200/800',
      'https://picsum.photos/seed/nilgiri-nest-2/1200/800',
      ARRAY['wifi', 'breakfast', 'garden', 'parking']::text[],
      'Tea Garden Room',
      5400,
      5,
      'Nilgiri Suite',
      8200,
      3
    ),
    (
      '16161616-1616-1616-1616-161616161616'::uuid,
      'Temple Tree Residency',
      'temple-tree-residency',
      'Efficient Madurai stay with premium service standards and family-booking flexibility.',
      'Madurai',
      'KK Nagar, Madurai',
      4.0,
      4.2,
      121,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/temple-tree-residency-1/1200/800',
      'https://picsum.photos/seed/temple-tree-residency-2/1200/800',
      ARRAY['wifi', 'breakfast', 'parking', 'airport-transfer']::text[],
      'Temple Room',
      3800,
      9,
      'Family Suite',
      6200,
      4
    ),
    (
      '17171717-1717-1717-1717-171717171717'::uuid,
      'Pearl Bay Hotel',
      'pearl-bay-hotel',
      'A Pondicherry stay mixing French Quarter charm with reliable modern comfort.',
      'Puducherry',
      'White Town, Puducherry',
      4.5,
      4.8,
      203,
      'Free cancellation up to 48 hours before check-in.',
      'https://picsum.photos/seed/pearl-bay-hotel-1/1200/800',
      'https://picsum.photos/seed/pearl-bay-hotel-2/1200/800',
      ARRAY['wifi', 'breakfast', 'pool', 'parking']::text[],
      'Bay Room',
      5200,
      6,
      'Pearl Suite',
      8300,
      3
    ),
    (
      '18181818-1818-1818-1818-181818181818'::uuid,
      'Orchid Square',
      'orchid-square',
      'Clean-lined Chandigarh stay with wide rooms and strong business-travel convenience.',
      'Chandigarh',
      'Sector 17, Chandigarh',
      4.0,
      4.3,
      167,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/orchid-square-1/1200/800',
      'https://picsum.photos/seed/orchid-square-2/1200/800',
      ARRAY['wifi', 'workspace', 'breakfast', 'parking']::text[],
      'Square Room',
      4500,
      8,
      'Orchid Suite',
      6900,
      4
    ),
    (
      '19191919-1919-1919-1919-191919191919'::uuid,
      'Valley Crest Resort',
      'valley-crest-resort',
      'Premium Srinagar resort with elevated views, larger leisure inventory, and seasonal demand.',
      'Srinagar',
      'Boulevard Road, Srinagar',
      4.5,
      4.8,
      214,
      'Free cancellation up to 72 hours before check-in.',
      'https://picsum.photos/seed/valley-crest-resort-1/1200/800',
      'https://picsum.photos/seed/valley-crest-resort-2/1200/800',
      ARRAY['wifi', 'lake-view', 'breakfast', 'spa']::text[],
      'Valley Room',
      6700,
      5,
      'Crest Suite',
      9900,
      3
    ),
    (
      '20202020-2020-2020-2020-202020202020'::uuid,
      'Maple Junction',
      'maple-junction',
      'Reliable city hotel close to transport hubs, built for short stays and repeat business.',
      'Bhopal',
      'MP Nagar, Bhopal',
      4.0,
      4.2,
      112,
      'Free cancellation up to 24 hours before check-in.',
      'https://picsum.photos/seed/maple-junction-1/1200/800',
      'https://picsum.photos/seed/maple-junction-2/1200/800',
      ARRAY['wifi', 'breakfast', 'workspace', 'parking']::text[],
      'Transit Room',
      3600,
      10,
      'Maple Suite',
      5900,
      4
    ),
    (
      '21212121-2121-2121-2121-212121212121'::uuid,
      'Coral Reef Residency',
      'coral-reef-residency',
      'Leisure-friendly coastal stay positioned for long weekends, local dining, and water-view bookings.',
      'Mangaluru',
      'Panambur Beach Road, Mangaluru',
      4.0,
      4.4,
      126,
      'Free cancellation up to 48 hours before check-in.',
      'https://picsum.photos/seed/coral-reef-residency-1/1200/800',
      'https://picsum.photos/seed/coral-reef-residency-2/1200/800',
      ARRAY['wifi', 'sea-view', 'breakfast', 'parking']::text[],
      'Coral Room',
      4300,
      7,
      'Reef Suite',
      6800,
      3
    ),
    (
      '23232323-2323-2323-2323-232323232323'::uuid,
      'The Trident Bloom',
      'the-trident-bloom',
      'A fresh Chandigarh-region premium stay with event-ready spaces and modern room mix.',
      'Mohali',
      'Airport Road, Mohali',
      4.5,
      4.6,
      151,
      'Free cancellation up to 24 hours before check-in.',
      'https://images.unsplash.com/photo-1760573776062-7d2a7baeb49d?auto=format&fit=crop&w=1200&q=80',
      'https://images.unsplash.com/photo-1744187170993-6591089e2674?auto=format&fit=crop&w=1200&q=80',
      ARRAY['wifi', 'breakfast', 'gym', 'parking']::text[],
      'Bloom Room',
      4900,
      8,
      'Trident Suite',
      7600,
      4
    )
),
insert_hotels AS (
  INSERT INTO hotels (
    id,
    name,
    slug,
    description,
    city,
    address,
    star_rating,
    avg_rating,
    review_count,
    cancellation_policy
  )
  SELECT
    id,
    name,
    slug,
    description,
    city,
    address,
    star_rating,
    avg_rating,
    review_count,
    cancellation_policy
  FROM hotel_seed
  ON CONFLICT (id) DO NOTHING
  RETURNING id
),
image_seed AS (
  SELECT id AS hotel_id, image_primary AS image_url, 1 AS position
  FROM hotel_seed
  UNION ALL
  SELECT id, image_secondary, 2
  FROM hotel_seed
),
insert_hotel_images AS (
  INSERT INTO hotel_images (hotel_id, image_url, position)
  SELECT seeded.hotel_id, seeded.image_url, seeded.position
  FROM image_seed seeded
  WHERE NOT EXISTS (
    SELECT 1
    FROM hotel_images hi
    WHERE hi.hotel_id = seeded.hotel_id
      AND hi.position = seeded.position
  )
  RETURNING hotel_id
),
amenity_seed AS (
  SELECT id AS hotel_id, unnest(amenities) AS amenity
  FROM hotel_seed
),
insert_amenities AS (
  INSERT INTO hotel_amenities (hotel_id, amenity)
  SELECT hotel_id, amenity
  FROM amenity_seed
  ON CONFLICT DO NOTHING
  RETURNING hotel_id
),
room_seed AS (
  SELECT
    (split_part(hs.id::text, '-', 1) || '-1000-0000-0000-000000000001')::uuid AS room_id,
    hs.id AS hotel_id,
    hs.room_one_name AS name,
    format('Comfort-focused stay at %s with quick access to %s highlights.', hs.name, hs.city) AS description,
    2 AS capacity,
    'Queen Bed'::text AS bed_type,
    hs.room_one_price AS base_price,
    hs.room_one_inventory AS total_inventory,
    format('https://picsum.photos/seed/%s-room-1/1200/800', hs.slug) AS image_url
  FROM hotel_seed hs
  UNION ALL
  SELECT
    (split_part(hs.id::text, '-', 1) || '-2000-0000-0000-000000000002')::uuid,
    hs.id,
    hs.room_two_name,
    format('Large suite layout in %s with extra lounge space and premium finishes.', hs.city),
    3,
    'King Bed',
    hs.room_two_price,
    hs.room_two_inventory,
    format('https://picsum.photos/seed/%s-room-2/1200/800', hs.slug)
  FROM hotel_seed hs
),
insert_rooms AS (
  INSERT INTO rooms (
    id,
    hotel_id,
    name,
    description,
    capacity,
    bed_type,
    base_price,
    total_inventory
  )
  SELECT
    room_id,
    hotel_id,
    name,
    description,
    capacity,
    bed_type,
    base_price,
    total_inventory
  FROM room_seed
  ON CONFLICT (id) DO NOTHING
  RETURNING id
),
insert_room_images AS (
  INSERT INTO room_images (room_id, image_url, position)
  SELECT rs.room_id, rs.image_url, 1
  FROM room_seed rs
  WHERE NOT EXISTS (
    SELECT 1
    FROM room_images ri
    WHERE ri.room_id = rs.room_id
      AND ri.position = 1
  )
  RETURNING room_id
)
INSERT INTO room_inventory (
  room_id,
  inventory_date,
  total_inventory,
  booked_inventory,
  price_override
)
SELECT
  rs.room_id,
  gs::date,
  rs.total_inventory,
  0,
  rs.base_price
FROM room_seed rs
CROSS JOIN generate_series(current_date, current_date + INTERVAL '120 day', INTERVAL '1 day') gs
ON CONFLICT (room_id, inventory_date) DO UPDATE
SET
  total_inventory = EXCLUDED.total_inventory,
  price_override = EXCLUDED.price_override,
  updated_at = NOW();

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

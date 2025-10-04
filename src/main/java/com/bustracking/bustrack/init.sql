-- Enable UUID generation
create extension if not exists "pgcrypto";

-- 0. App-level user mapping + allowed emails

create table allowed_emails (
                                email text primary key,
                                role text not null ,
                                note text
);

-- 1. Master stops
create table stops (
                       id uuid primary key default gen_random_uuid(),
                       name text not null,
                       lat double precision,
                       lng double precision
);

-- 2. Riders
create table riders (
                        id uuid primary key default gen_random_uuid(),
                        name text not null,
                        year int,
                        department text,
                        home_stop_id uuid references stops(id),
                        college text,
                        email text,
                        digital_id text,
                        created_at timestamptz default now()
);

-- 3. Profiles (schedule snapshots)
create table profiles (
                          id uuid primary key default gen_random_uuid(),
                          name text not null,
                          status text check (status in ('draft','active')) default 'draft',
                          created_at timestamptz default now()
);

-- 4. Buses (master)
create table buses (
                       id uuid primary key default gen_random_uuid(),
                       bus_number text unique,
                       capacity int check (capacity > 0),
                       brand text,
                       created_at timestamptz default now()
);

-- 5. Profile_Buses (buses assigned to a profile)
create table profile_buses (
                               id uuid primary key default gen_random_uuid(),
                               profile_id uuid references profiles(id) on delete cascade,
                               bus_id uuid references buses(id),
                               bus_number text not null                     -- column kept but no foreign key
);

-- 6. Profile_Stops (ordered stops per bus in a profile)
create table profile_stops (
                               id uuid primary key default gen_random_uuid(),
                               profile_bus_id uuid references profile_buses(id) on delete cascade,
                               stop_id uuid references stops(id),
                               stop_order int not null,
                               stop_time text
);

-- 7. Profile_Rider_Stops (daily rider assignments in a profile)
create table profile_rider_stops (
                                     id uuid primary key default gen_random_uuid(),
                                     profile_id uuid references profiles(id) on delete cascade,
                                     rider_id uuid references riders(id) on delete cascade ,
                                     profile_stop_id uuid references profile_stops(id) on delete cascade,
                                     created_at timestamptz default now()
);


-- Indexes for faster lookups
create index idx_profile_riders on profile_rider_stops(profile_id, rider_id);
create index idx_profile_stops  on profile_rider_stops(profile_stop_id);
create index idx_profile_buses  on profile_buses(profile_id);
create index idx_profile_routes on profile_stops(profile_bus_id);
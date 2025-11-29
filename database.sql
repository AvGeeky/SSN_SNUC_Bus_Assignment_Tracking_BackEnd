-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.allowed_emails (
  email text NOT NULL,
  role text DEFAULT 'user'::text CHECK (role = ANY (ARRAY['admin'::text, 'user'::text])),
  note text,
  CONSTRAINT allowed_emails_pkey PRIMARY KEY (email)
);
CREATE TABLE public.app_users (
  auth_uid uuid NOT NULL,
  email text UNIQUE,
  role text NOT NULL DEFAULT 'user'::text CHECK (role = ANY (ARRAY['admin'::text, 'user'::text])),
  rider_id uuid,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT app_users_pkey PRIMARY KEY (auth_uid)
);
CREATE TABLE public.buses (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  capacity integer CHECK (capacity > 0),
  created_at timestamp with time zone DEFAULT now(),
  bus_number text UNIQUE,
  brand text,
  CONSTRAINT buses_pkey PRIMARY KEY (id)
);
CREATE TABLE public.profile_buses (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  profile_id uuid,
  bus_id uuid,
  bus_number text NOT NULL,
  CONSTRAINT profile_buses_pkey PRIMARY KEY (id),
  CONSTRAINT profile_buses_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id),
  CONSTRAINT profile_buses_bus_id_fkey FOREIGN KEY (bus_id) REFERENCES public.buses(id),
  CONSTRAINT fk_num FOREIGN KEY (bus_number) REFERENCES public.buses(bus_number)
);
CREATE TABLE public.profile_rider_stops (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  profile_id uuid,
  rider_id uuid,
  profile_stop_id uuid,
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT profile_rider_stops_pkey PRIMARY KEY (id),
  CONSTRAINT profile_rider_stops_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id),
  CONSTRAINT profile_rider_stops_rider_id_fkey FOREIGN KEY (rider_id) REFERENCES public.riders(id),
  CONSTRAINT profile_rider_stops_profile_stop_id_fkey FOREIGN KEY (profile_stop_id) REFERENCES public.profile_stops(id)
);
CREATE TABLE public.profile_stops (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  profile_bus_id uuid,
  stop_id uuid,
  stop_order integer NOT NULL,
  stop_time text,
  CONSTRAINT profile_stops_pkey PRIMARY KEY (id),
  CONSTRAINT profile_stops_profile_bus_id_fkey FOREIGN KEY (profile_bus_id) REFERENCES public.profile_buses(id),
  CONSTRAINT profile_stops_stop_id_fkey FOREIGN KEY (stop_id) REFERENCES public.stops(id)
);
CREATE TABLE public.profiles (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  name text NOT NULL,
  status text DEFAULT 'draft'::text CHECK (status = ANY (ARRAY['draft'::text, 'active'::text])),
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT profiles_pkey PRIMARY KEY (id)
);
CREATE TABLE public.riders (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  name text NOT NULL,
  year integer,
  department text,
  home_stop_id uuid,
  created_at timestamp with time zone DEFAULT now(),
  college text,
  email text,
  CONSTRAINT riders_pkey PRIMARY KEY (id),
  CONSTRAINT riders_home_stop_id_fkey FOREIGN KEY (home_stop_id) REFERENCES public.stops(id)
);
CREATE TABLE public.stops (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  name text NOT NULL,
  lat double precision,
  lng double precision,
  CONSTRAINT stops_pkey PRIMARY KEY (id)
);
create table vehicle_rno_mapping(id uuid NOT NULL DEFAULT gen_random_uuid() primary key,route_no varchar(2),
                                 vehicle_no varchar(50) references buses(bus_number)
);
------------------------------------------------------
-- Indexes
------------------------------------------------------
create index idx_profile_riders on profile_rider_stops(profile_id, rider_id);
create index idx_profile_stops on profile_rider_stops(profile_stop_id);
create index idx_profile_buses on profile_buses(profile_id);
create index idx_profile_routes on profile_stops(profile_bus_id);

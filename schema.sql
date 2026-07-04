-- Run this in the Supabase SQL Editor for your project.

create table public.programs (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references auth.users(id) on delete cascade,
  program_name      text not null,
  current_vertical  numeric not null,
  target_vertical   numeric not null,
  height            numeric,
  bodyweight        numeric,
  days_per_week     int not null,
  experience_level  text not null,
  notes             text,
  active            boolean not null default true,
  created_at        timestamptz not null default now()
);
create index idx_programs_user_id on public.programs(user_id);

create table public.weeks (
  id             uuid primary key default gen_random_uuid(),
  program_id     uuid not null references public.programs(id) on delete cascade,
  week_number    int not null,
  cycle_position int not null,
  cycle_number   int not null,
  phase          text not null,
  is_deload      boolean not null,
  created_at     timestamptz not null default now(),
  unique (program_id, week_number)
);
create index idx_weeks_program_id on public.weeks(program_id);

create table public.days (
  id            uuid primary key default gen_random_uuid(),
  week_id       uuid not null references public.weeks(id) on delete cascade,
  day_index     int not null,
  day_label     text not null,
  focus         text not null,
  -- Free-text context the athlete can add for this day (e.g. "on vacation
  -- next week, no gym access"). Read by the backend when generating the
  -- FOLLOWING week so Claude can adapt around it.
  athlete_note  text,
  unique (week_id, day_index)
);
create index idx_days_week_id on public.days(week_id);

create table public.exercises (
  id              uuid primary key default gen_random_uuid(),
  day_id          uuid not null references public.days(id) on delete cascade,
  exercise_index  int not null,
  name            text not null,
  sets            int not null,
  reps            text not null,
  target_weight   text,
  notes           text,
  logged_weight   numeric,
  logged_reps     int,
  created_at      timestamptz not null default now(),
  unique (day_id, exercise_index)
);
create index idx_exercises_day_id on public.exercises(day_id);

create table public.vertical_checkins (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  inches       numeric not null,
  recorded_at  timestamptz not null default now(),
  notes        text
);
create index idx_checkins_user_recorded on public.vertical_checkins(user_id, recorded_at);

-- Per-user override of the default daily cap on paid (Anthropic-backed) API
-- calls. Absence of a row means the default limit applies.
create table public.user_limits (
  user_id           uuid primary key references auth.users(id) on delete cascade,
  daily_call_limit  int not null default 5
);

-- One row per user per calendar day; call_count is incremented atomically
-- (INSERT ... ON CONFLICT ... DO UPDATE) before each paid API call.
create table public.api_usage_daily (
  user_id     uuid not null references auth.users(id) on delete cascade,
  usage_date  date not null,
  call_count  int not null default 0,
  primary key (user_id, usage_date)
);
create index idx_api_usage_daily_user_date on public.api_usage_daily(user_id, usage_date);

alter table public.user_limits enable row level security;
alter table public.api_usage_daily enable row level security;

create policy "own limits" on public.user_limits
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own usage" on public.api_usage_daily
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- RLS enabled as defense-in-depth; the Java backend connects via a trusted
-- direct Postgres role (bypasses RLS) and is the actual enforcement layer,
-- scoping every query by the JWT's `sub` claim.
alter table public.programs enable row level security;
alter table public.weeks enable row level security;
alter table public.days enable row level security;
alter table public.exercises enable row level security;
alter table public.vertical_checkins enable row level security;

create policy "own programs" on public.programs
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own weeks" on public.weeks
  for all using (exists (select 1 from public.programs p where p.id = program_id and p.user_id = auth.uid()));
create policy "own days" on public.days
  for all using (exists (
    select 1 from public.weeks w join public.programs p on p.id = w.program_id
    where w.id = week_id and p.user_id = auth.uid()));
create policy "own exercises" on public.exercises
  for all using (exists (
    select 1 from public.days d join public.weeks w on w.id = d.week_id
    join public.programs p on p.id = w.program_id
    where d.id = day_id and p.user_id = auth.uid()));
create policy "own checkins" on public.vertical_checkins
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

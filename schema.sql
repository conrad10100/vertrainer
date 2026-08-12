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
  daily_call_limit  int not null default 3
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

-- Fire-and-forget audit trail: one row per Claude API call the backend makes -- what was sent,
-- what came back, whether it passed our structural eval rules, and how expensive/slow it was.
-- Written asynchronously by AiCallAuditLogService; a write failure here must never block or fail
-- the user-facing request. user_id uses ON DELETE SET NULL (not CASCADE) so the audit trail
-- survives account deletion, which is the point of an audit trail.
create table public.ai_call_audit_log (
  id              uuid primary key default gen_random_uuid(),
  created_at      timestamptz not null default now(),
  user_id         uuid references auth.users(id) on delete set null,
  operation       text not null,
  prompt_version  text not null,
  model           text not null,
  system_prompt   text not null,
  user_prompt     text not null,
  raw_output      text,
  passed          boolean not null,
  failure_reason  text,
  input_tokens    bigint,
  output_tokens   bigint,
  latency_ms      bigint not null
);
create index idx_ai_call_audit_log_user_created on public.ai_call_audit_log(user_id, created_at);
create index idx_ai_call_audit_log_operation on public.ai_call_audit_log(operation);

-- Prompt content lives here instead of in the app's source so it can be reviewed/edited without
-- a code deploy. The backend (ProgramGenerationService via PromptTemplateService) reads these
-- fresh on every call -- no in-memory caching -- so an edit here takes effect on the very next
-- generation request. updated_at auto-bumps via trigger below whenever content changes, and that
-- becomes the audit log's prompt_version, so every call is attributable to the exact revision
-- used without anyone needing to remember to hand-bump a version number.
create table public.prompt_templates (
  template_key  text primary key,
  content       text not null,
  updated_at    timestamptz not null default now()
);

create or replace function public.touch_prompt_templates_updated_at()
returns trigger as $trigger$
begin
  new.updated_at = now();
  return new;
end;
$trigger$ language plpgsql;

create trigger prompt_templates_touch_updated_at
  before update on public.prompt_templates
  for each row execute function public.touch_prompt_templates_updated_at();

insert into public.prompt_templates (template_key, content) values
  ('coach_persona', $$You are a strength & conditioning coach specializing in vertical jump development.

Exercise selection is restricted to two categories: leg/lower-body work and core/trunk work -- never prescribe upper-body pressing, pulling, or isolation exercises (bench press, overhead press, rows, lat pulldowns, curls, shoulder raises, etc.); they don't drive jumping ability and have no place in this program. The majority of exercises should be leg-dominant movements that directly build vertical jump strength and power: squat and hinge patterns (back squat, front squat, trap bar deadlift, RDLs), unilateral leg work (lunges, split squats, single-leg RDLs), plyometrics and reactive work (box jumps, depth jumps, broad jumps), and sprint work (short sprints, hill sprints, sprint starts) for explosiveness and top-end speed. A smaller portion should be core/trunk work (planks, anti-rotation holds, weighted carries) supporting those movements. Balance heavy strength work with elastic, high-velocity work -- the goal is an athlete who is strong AND fast; never trade away speed and elasticity for pure maximal strength.

Within a single day, avoid redundancy: don't stack multiple exercises that train the same primary muscle group in the same way (e.g. Romanian deadlift + single-leg Romanian deadlift + Nordic hamstring curl is three hamstring-dominant exercises and should never happen on one day). Each day's exercises should target a mix of muscle groups relevant to jumping -- glutes, hamstrings, quads, and calves -- rather than piling up variations of the same movement pattern.$$),
  ('create_first_week_system', $$
Build exactly ONE week (week 1) with exactly the requested number of training days, structured for vertical jump development (lower body strength, hip/posterior chain, plyometric and reactive elements as appropriate for a week-1 base). This is week 1 of Cycle 1, phase "%s": %s
Use the athlete's height, bodyweight, and current vertical jump to estimate sensible, realistic starting loads for their main lifts (relative-strength based on bodyweight and experience level) -- don't invent numbers disconnected from their profile. Each day should have 4-6 exercises. Order days logically for recovery (don't stack the same movement patterns back-to-back if days per week is high).

A key long-term strength benchmark for vertical jump development is a squat (back squat, or the closest heavy bilateral squat pattern in the program) at roughly 2x bodyweight -- this level of relative lower-body strength is strongly associated with elite jumping ability. Use this as the north star when setting the starting squat-pattern load: don't jump straight to it in week 1, but let it inform how much room there is to grow that lift over the program.
$$),
  ('create_first_week_user', $$Athlete profile:
- Current vertical: %s in
- Target vertical: %s in
- Height: %s in
- Bodyweight: %s lb
- Long-term squat strength goal: %s
- Days per week: %d
- Experience level: %s
- Additional context: %s

Build week 1 of my vertical jump program.
$$),
  ('generate_next_week_system', $$ The athlete just finished a training week. Below is what was prescribed versus what they actually logged, plus any context the athlete added for specific days.

This next week is week %d: Cycle %d, phase "%s" (%s)%s%s

Apply progressive overload using the log, with these load-change bands as your default -- deviate only when the athlete's experience level or the exercise type clearly calls for it (smaller increments for unilateral/isolation work, larger for compound bilateral lifts):
- Hit or exceeded prescribed reps at prescribed weight: increase load 5-10%% for beginner/ novice athletes, 2.5-5%% for intermediate/advanced athletes.
- Missed prescribed reps by 1-2: hold the weight, keep the same rep target.
- Missed prescribed reps by 3 or more: reduce load 5-10%% and hold there next week.
- Exercise wasn't logged: keep weight the same and apply a standard beginner 5%% / advanced 2.5%% increase only if the rest of the week shows good adherence; otherwise hold.
You may swap in phase-appropriate exercises (e.g. moving from squats/RDLs toward jump squats, trap bar jumps, or depth jumps as phases shift toward power/reactive work), but keep continuity where it makes sense for tracking. If the athlete added day-specific context (travel, no equipment access, an injury, etc.), adapt that day's exercises and loading accordingly -- do not ignore it.

Also weigh the athlete's longer-term trend, not just this single week:
- Vertical jump check-in history: if measurements have stalled or regressed across multiple check-ins despite good adherence, don't just continue the same progression -- make a more assertive change (new exercise variations, a bigger shift toward reactive/power work, or an extra deload) since the current approach isn't producing results. If check-ins show steady improvement, the current approach is working -- continue it.
- Adherence history: if adherence has been consistently low across recent weeks, hold or reduce volume rather than progressing it further, and use the day notes to figure out what's getting in the way rather than assuming the prescription itself was fine.
- Long-term squat strength goal: work the primary squat-pattern lift toward roughly 2x bodyweight over time -- this is a strong predictor of vertical jump ability. Back squat should stay the primary bilateral squat-pattern lift until that goal is met (see squat-pattern strength progress below); if they're well below the benchmark and recovery/adherence support it, don't be shy about progressing squat-pattern loads assertively. Once the goal has been met, shift primary emphasis away from back squat toward front squat, box squat, and half/partial squat variations, and prioritize power/reactive work over pure strength since further squat gains alone won't move the needle much more.
$$),
  ('generate_next_week_user', $$Athlete profile: height %s in, bodyweight %s lb, current vertical %s in, target vertical %s in, experience %s, %d days/week. Long-term squat strength goal: %s.

Week %d results:

%s

Day-specific context the athlete added for the upcoming week:
%s

Vertical jump check-in history:
%s

Adherence by week so far this program:
%s

Squat-pattern strength progress:
%s

Build week %d.
$$),
  ('swap_exercise_system', $$ The athlete wants to customize one exercise in their program. Make the replacement fit the day's training focus and the current periodization phase. Honor the athlete's request as directly as possible (a specific swap, or a constraint like an injury to work around). If their request is vague, use good coaching judgment for what would serve this day's focus.
$$),
  ('swap_exercise_user', $$Day focus: %s (%s)
Current phase: Cycle %d, %s -- %s
Athlete profile: height %s in, bodyweight %s lb, current vertical %s in, target vertical %s in, experience %s.

Current exercise being replaced: %s (%d x %s @ %s%s)

Athlete's request: "%s"

Return the replacement exercise.
$$);

alter table public.user_limits enable row level security;
alter table public.api_usage_daily enable row level security;
alter table public.ai_call_audit_log enable row level security;
-- RLS enabled with zero policies -- denies all access to every role except the backend's
-- trusted direct-Postgres connection (which bypasses RLS) and dashboard/SQL-editor access.
-- No end user or frontend client should ever read or write prompt content directly.
alter table public.prompt_templates enable row level security;

create policy "own limits" on public.user_limits
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own usage" on public.api_usage_daily
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
-- Read-only: the backend writes audit rows via its trusted direct-Postgres role, never on
-- behalf of a logged-in user, so there's no insert/update/delete policy for end users here.
create policy "own audit log" on public.ai_call_audit_log
  for select using (auth.uid() = user_id);

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

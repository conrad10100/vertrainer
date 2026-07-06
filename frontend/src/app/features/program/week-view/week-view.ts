import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ProgramApi } from '../program';
import { Day, Exercise, Program, Week } from '../../../shared/models/program.model';
import { ProgramIntake } from '../program-intake/program-intake';
import { PhaseBadge } from '../phase-badge/phase-badge';

@Component({
  selector: 'app-week-view',
  imports: [FormsModule, ProgramIntake, PhaseBadge],
  templateUrl: './week-view.html',
  styleUrl: './week-view.css',
})
export class WeekView implements OnInit {
  private readonly programApi = inject(ProgramApi);

  loading = signal(true);
  program = signal<Program | null>(null);
  activeWeekIndex = signal(0);
  generatingNext = signal(false);
  errorMsg = signal('');
  restarting = signal(false);

  openSwapKey = signal<string | null>(null);
  swapInputs: Record<string, string> = {};
  swappingKey = signal<string | null>(null);
  private readonly revealedWeightIds = new Set<string>();

  private readonly drafts = new Map<string, { weight: string; reps: string }>();
  savingDayId = signal<string | null>(null);
  private readonly savedDayIds = signal<ReadonlySet<string>>(new Set());

  activeWeek = computed<Week | null>(() => this.program()?.weeks[this.activeWeekIndex()] ?? null);
  prevWeek = computed<Week | null>(() => {
    const idx = this.activeWeekIndex();
    return idx > 0 ? (this.program()?.weeks[idx - 1] ?? null) : null;
  });
  isLatestWeek = computed(() => {
    const p = this.program();
    return !!p && this.activeWeekIndex() === p.weeks.length - 1;
  });

  async ngOnInit() {
    try {
      const program = await this.programApi.getActiveProgram();
      this.program.set(program);
      this.activeWeekIndex.set(program.weeks.length - 1);
    } catch (err) {
      if (!(err instanceof HttpErrorResponse && err.status === 404)) {
        console.error(err);
      }
      // no active program yet -- show the intake form instead
    } finally {
      this.loading.set(false);
    }
  }

  onProgramCreated(program: Program) {
    this.program.set(program);
    this.activeWeekIndex.set(0);
    this.restarting.set(false);
  }

  requestRestart() {
    if (confirm('Start a new program? Your current one will be archived (not deleted) and replaced.')) {
      this.restarting.set(true);
    }
  }

  cancelRestart() {
    this.restarting.set(false);
  }

  selectWeek(i: number) {
    this.activeWeekIndex.set(i);
    this.openSwapKey.set(null);
  }

  // Nested mutations of the exercises/day-notes inside `program()` (swap, remove,
  // note edits) happen after an `await`, outside the DOM event handler's synchronous
  // scope -- in this zoneless app, that means no re-render happens unless some
  // signal is actually written afterward. Re-setting a shallow clone of the top-level
  // Program forces `program` (and anything computed from it, like activeWeek) to
  // notify, so the already-mutated nested data gets picked up and rendered.
  private touchProgram() {
    const p = this.program();
    if (p) this.program.set({ ...p });
  }

  previousTargetWeight(dayIndex: number, exerciseName: string): string | null {
    const prevDay = this.prevWeek()?.days[dayIndex];
    const prevEx = prevDay?.exercises.find((e) => e.name === exerciseName);
    return prevEx?.targetWeight ?? null;
  }

  weightDelta(dayIndex: number, exerciseName: string, targetWeight: string): number | null {
    const prev = this.previousTargetWeight(dayIndex, exerciseName);
    if (!prev) return null;
    const curNum = parseFloat(targetWeight.match(/[\d.]+/)?.[0] ?? '');
    const prevNum = parseFloat(prev.match(/[\d.]+/)?.[0] ?? '');
    if (isNaN(curNum) || isNaN(prevNum) || curNum <= prevNum) return null;
    return curNum - prevNum;
  }

  isBodyweight(targetWeight: string): boolean {
    // Claude appends box/drop height for jump variants, e.g. "Bodyweight (12 in box)".
    return targetWeight?.trim().toLowerCase().startsWith('bodyweight') ?? false;
  }

  showWeightInput(ex: Exercise): boolean {
    return !this.isBodyweight(ex.targetWeight) || ex.loggedWeight !== null || this.revealedWeightIds.has(ex.id);
  }

  revealWeightInput(id: string) {
    this.revealedWeightIds.add(id);
  }

  // Draft values are typed locally and only sent to the server when the athlete
  // taps "Save" for the day -- typing alone (with no explicit save) must not be
  // able to lose data, unlike the old save-on-blur behavior.
  draftFor(ex: Exercise): { weight: string; reps: string } {
    let draft = this.drafts.get(ex.id);
    if (!draft) {
      draft = {
        weight: ex.loggedWeight != null ? String(ex.loggedWeight) : '',
        reps: ex.loggedReps != null ? String(ex.loggedReps) : '',
      };
      this.drafts.set(ex.id, draft);
    }
    return draft;
  }

  isDaySaved(dayId: string): boolean {
    return this.savedDayIds().has(dayId);
  }

  private markDaySaved(dayId: string, saved: boolean) {
    this.savedDayIds.update((current) => {
      const next = new Set(current);
      if (saved) {
        next.add(dayId);
      } else {
        next.delete(dayId);
      }
      return next;
    });
  }

  async saveDay(day: Day) {
    this.savingDayId.set(day.id);
    this.markDaySaved(day.id, false);
    const week = this.activeWeek();
    try {
      for (const ex of day.exercises) {
        const draft = this.draftFor(ex);
        const weight = this.showWeightInput(ex)
          ? parseOrNull(draft.weight, parseFloat)
          : ex.loggedWeight;
        const reps = parseOrNull(draft.reps, (s) => parseInt(s, 10));

        const updated = await this.programApi.logExercise(ex.id, weight, reps);
        if (week) {
          const idx = day.exercises.findIndex((e) => e.id === ex.id);
          if (idx !== -1) {
            day.exercises[idx] = updated;
          }
        }
      }
      this.touchProgram();
      this.markDaySaved(day.id, true);
    } catch (err) {
      console.error(err);
      this.errorMsg.set("Couldn't save that day — try again.");
    } finally {
      this.savingDayId.set(null);
    }
  }

  toggleSwap(key: string) {
    this.openSwapKey.update((k) => (k === key ? null : key));
  }

  async confirmSwap(exerciseId: string, key: string) {
    const requestText = (this.swapInputs[key] || '').trim();
    if (!requestText) return;
    const week = this.activeWeek();
    if (!week) return;
    this.swappingKey.set(key);
    try {
      const replacement = await this.programApi.swapExercise(exerciseId, requestText);
      for (const day of week.days) {
        const idx = day.exercises.findIndex((e) => e.id === exerciseId);
        if (idx !== -1) {
          day.exercises[idx] = replacement;
        }
      }
      this.drafts.delete(exerciseId);
      delete this.swapInputs[key];
      this.openSwapKey.set(null);
      this.touchProgram();
    } catch (err) {
      console.error(err);
      this.errorMsg.set("Couldn't swap that exercise — try again.");
    } finally {
      this.swappingKey.set(null);
    }
  }

  async removeExercise(dayIndex: number, exerciseId: string) {
    const week = this.activeWeek();
    if (!week) return;
    await this.programApi.removeExercise(exerciseId);
    const day = week.days[dayIndex];
    day.exercises = day.exercises.filter((e) => e.id !== exerciseId);
    this.drafts.delete(exerciseId);
    this.touchProgram();
  }

  async saveDayNote(dayId: string, note: string) {
    const week = this.activeWeek();
    if (!week) return;
    const updated = await this.programApi.updateDayNote(dayId, note);
    const day = week.days.find((d) => d.id === dayId);
    if (day) day.athleteNote = updated.athleteNote;
    this.touchProgram();
  }

  async buildNextWeek() {
    const program = this.program();
    const week = this.activeWeek();
    if (!program || !week) return;
    this.generatingNext.set(true);
    this.errorMsg.set('');
    try {
      const nextWeek = await this.programApi.generateNextWeek(program.id);
      program.weeks.push(nextWeek);
      this.activeWeekIndex.set(program.weeks.length - 1);
    } catch (err) {
      console.error(err);
      const backendMessage = err instanceof HttpErrorResponse ? err.error?.error : null;
      this.errorMsg.set(backendMessage ?? "Couldn't build next week — try again.");
    } finally {
      this.generatingNext.set(false);
    }
  }
}

function parseOrNull(value: string, parser: (s: string) => number): number | null {
  if (value.trim() === '') return null;
  const parsed = parser(value);
  return isNaN(parsed) ? null : parsed;
}

import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ProgramApi } from '../program';
import { Exercise, Program, Week } from '../../../shared/models/program.model';
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

  openSwapKey: string | null = null;
  swapInputs: Record<string, string> = {};
  swappingKey: string | null = null;
  private readonly revealedWeightIds = new Set<string>();

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
  }

  selectWeek(i: number) {
    this.activeWeekIndex.set(i);
    this.openSwapKey = null;
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
    return targetWeight?.trim().toLowerCase() === 'bodyweight';
  }

  showWeightInput(ex: Exercise): boolean {
    return !this.isBodyweight(ex.targetWeight) || ex.loggedWeight !== null || this.revealedWeightIds.has(ex.id);
  }

  revealWeightInput(id: string) {
    this.revealedWeightIds.add(id);
  }

  async logWeight(ex: Exercise, weightStr: string) {
    const parsed = weightStr.trim() === '' ? null : parseFloat(weightStr);
    await this.updateLog(ex, parsed != null && !isNaN(parsed) ? parsed : null, ex.loggedReps);
  }

  async logReps(ex: Exercise, repsStr: string) {
    const parsed = repsStr.trim() === '' ? null : parseInt(repsStr, 10);
    await this.updateLog(ex, ex.loggedWeight, parsed != null && !isNaN(parsed) ? parsed : null);
  }

  private async updateLog(ex: Exercise, loggedWeight: number | null, loggedReps: number | null) {
    const week = this.activeWeek();
    if (!week) return;
    const updated = await this.programApi.logExercise(ex.id, loggedWeight, loggedReps);
    for (const day of week.days) {
      const idx = day.exercises.findIndex((e) => e.id === ex.id);
      if (idx !== -1) {
        day.exercises[idx] = updated;
      }
    }
  }

  toggleSwap(key: string) {
    this.openSwapKey = this.openSwapKey === key ? null : key;
  }

  async confirmSwap(exerciseId: string, key: string) {
    const requestText = (this.swapInputs[key] || '').trim();
    if (!requestText) return;
    const week = this.activeWeek();
    if (!week) return;
    this.swappingKey = key;
    try {
      const replacement = await this.programApi.swapExercise(exerciseId, requestText);
      for (const day of week.days) {
        const idx = day.exercises.findIndex((e) => e.id === exerciseId);
        if (idx !== -1) {
          day.exercises[idx] = replacement;
        }
      }
      delete this.swapInputs[key];
      this.openSwapKey = null;
    } catch (err) {
      console.error(err);
      this.errorMsg.set("Couldn't swap that exercise — try again.");
    } finally {
      this.swappingKey = null;
    }
  }

  async removeExercise(dayIndex: number, exerciseId: string) {
    const week = this.activeWeek();
    if (!week) return;
    await this.programApi.removeExercise(exerciseId);
    const day = week.days[dayIndex];
    day.exercises = day.exercises.filter((e) => e.id !== exerciseId);
  }

  async saveDayNote(dayId: string, note: string) {
    const week = this.activeWeek();
    if (!week) return;
    const updated = await this.programApi.updateDayNote(dayId, note);
    const day = week.days.find((d) => d.id === dayId);
    if (day) day.athleteNote = updated.athleteNote;
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
      this.errorMsg.set("Couldn't build next week — try again.");
    } finally {
      this.generatingNext.set(false);
    }
  }
}

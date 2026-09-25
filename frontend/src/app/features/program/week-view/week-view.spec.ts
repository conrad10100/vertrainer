import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WeekView } from './week-view';
import { ProgramApi } from '../program';
import { Program, Week } from '../../../shared/models/program.model';

function makeWeek(weekNumber: number): Week {
  return { id: `week-${weekNumber}`, weekNumber, cyclePosition: 1, cycleNumber: 1, phase: 'BASE', deload: false, days: [] };
}

function makeProgram(overrides: Partial<Program> = {}): Program {
  return {
    id: 'prog-1',
    programName: 'Test Program',
    currentVertical: 24,
    targetVertical: 30,
    height: null,
    bodyweight: null,
    daysPerWeek: 3,
    experienceLevel: 'beginner',
    notes: null,
    weeks: [makeWeek(1)],
    generationInProgress: false,
    generationStartedAt: null,
    ...overrides,
  };
}

describe('WeekView -- next-week generation UX', () => {
  let programApi: { generateNextWeek: ReturnType<typeof vi.fn>; getActiveProgram: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.useFakeTimers();
    programApi = { generateNextWeek: vi.fn(), getActiveProgram: vi.fn() };
    TestBed.configureTestingModule({
      imports: [WeekView],
      providers: [{ provide: ProgramApi, useValue: programApi }],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // Constructed directly (not via TestBed.createComponent) so ngOnInit doesn't fire on its own --
  // most of these tests only care about buildNextWeek()'s behavior, called explicitly below.
  function createComponent(program: Program) {
    const component = TestBed.runInInjectionContext(() => new WeekView());
    component.program.set(program);
    component.activeWeekIndex.set(program.weeks.length - 1);
    return component;
  }

  it('applies the new week directly when the request resolves normally', async () => {
    const program = makeProgram();
    const component = createComponent(program);
    programApi.generateNextWeek.mockResolvedValue(makeWeek(2));

    await Promise.all([component.buildNextWeek(), vi.runAllTimersAsync()]);

    expect(component.errorMsg()).toBe('');
    expect(component.generatingNext()).toBe(false);
    expect(component.program()!.weeks.map((w) => w.weekNumber)).toEqual([1, 2]);
  });

  it('recovers automatically by polling when the connection drops mid-request (status 0)', async () => {
    const program = makeProgram();
    const component = createComponent(program);
    // Simulates the real bug found in production: the client aborts (e.g. a backgrounded
    // mobile tab) while generation is still running server-side, so the POST rejects with a
    // network-level error even though the week eventually gets created.
    programApi.generateNextWeek.mockRejectedValue(new HttpErrorResponse({ status: 0 }));
    programApi.getActiveProgram.mockResolvedValue(makeProgram({ weeks: [makeWeek(1), makeWeek(2)] }));

    await Promise.all([component.buildNextWeek(), vi.runAllTimersAsync()]);

    expect(component.errorMsg()).toBe('');
    expect(component.generatingNext()).toBe(false);
    expect(component.program()!.weeks.map((w) => w.weekNumber)).toEqual([1, 2]);
  });

  it('recovers automatically when a retry hits "already generating" (409) instead of erroring', async () => {
    const program = makeProgram();
    const component = createComponent(program);
    programApi.generateNextWeek.mockRejectedValue(
      new HttpErrorResponse({ status: 409, error: { error: 'already generating' } })
    );
    programApi.getActiveProgram.mockResolvedValue(makeProgram({ weeks: [makeWeek(1), makeWeek(2)] }));

    await Promise.all([component.buildNextWeek(), vi.runAllTimersAsync()]);

    expect(component.errorMsg()).toBe('');
    expect(component.program()!.weeks.map((w) => w.weekNumber)).toEqual([1, 2]);
  });

  it('shows the real error immediately for a non-transient failure, without waiting on polling', async () => {
    const program = makeProgram();
    const component = createComponent(program);
    programApi.generateNextWeek.mockRejectedValue(
      new HttpErrorResponse({ status: 429, error: { error: 'Daily limit reached' } })
    );
    // Should never be consulted -- a 429 is real and final, not something polling can resolve.
    programApi.getActiveProgram.mockResolvedValue(program);

    await Promise.all([component.buildNextWeek(), vi.runAllTimersAsync()]);

    expect(component.errorMsg()).toBe('Daily limit reached');
    expect(component.generatingNext()).toBe(false);
  });

  it('resumes watching on page load when the server reports generation already in progress', async () => {
    programApi.getActiveProgram.mockResolvedValueOnce(
      makeProgram({ generationInProgress: true, generationStartedAt: new Date().toISOString() })
    );
    const component = TestBed.runInInjectionContext(() => new WeekView());

    const initDone = component.ngOnInit();
    await vi.advanceTimersByTimeAsync(0);
    expect(component.generatingNext()).toBe(true);

    programApi.getActiveProgram.mockResolvedValue(makeProgram({ weeks: [makeWeek(1), makeWeek(2)] }));
    await Promise.all([initDone, vi.runAllTimersAsync()]);

    expect(component.errorMsg()).toBe('');
    expect(component.generatingNext()).toBe(false);
    expect(component.program()!.weeks.map((w) => w.weekNumber)).toEqual([1, 2]);
  });
});

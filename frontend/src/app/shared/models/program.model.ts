export interface Exercise {
  id: string;
  name: string;
  sets: number;
  reps: string;
  targetWeight: string;
  notes: string | null;
  loggedWeight: number | null;
  loggedReps: number | null;
}

export interface Day {
  id: string;
  dayLabel: string;
  focus: string;
  athleteNote: string | null;
  exercises: Exercise[];
}

export interface Week {
  id: string;
  weekNumber: number;
  cyclePosition: number;
  cycleNumber: number;
  phase: string;
  deload: boolean;
  days: Day[];
}

export interface Program {
  id: string;
  programName: string;
  currentVertical: number;
  targetVertical: number;
  height: number | null;
  bodyweight: number | null;
  daysPerWeek: number;
  experienceLevel: string;
  notes: string | null;
  weeks: Week[];
}

export interface CreateProgramRequest {
  currentVertical: number;
  targetVertical: number;
  height: number | null;
  bodyweight: number | null;
  daysPerWeek: number;
  experienceLevel: string;
  notes: string;
}

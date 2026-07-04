export interface CheckinPoint {
  recordedAt: string;
  inches: number;
}

export interface LiftPoint {
  weekNumber: number;
  targetWeight: number;
}

export interface AdherencePoint {
  weekNumber: number;
  loggedFraction: number;
}

export interface DashboardData {
  verticalJumpHistory: CheckinPoint[];
  liftProgression: LiftPoint[];
  adherence: AdherencePoint[];
}

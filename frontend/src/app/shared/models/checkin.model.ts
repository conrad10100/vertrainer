export interface Checkin {
  id: string;
  inches: number;
  recordedAt: string;
  notes: string | null;
}

export interface CreateCheckinRequest {
  inches: number;
  notes: string;
}

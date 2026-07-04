import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateProgramRequest, Day, Exercise, Program, Week } from '../../shared/models/program.model';

@Service()
export class ProgramApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  createProgram(req: CreateProgramRequest): Promise<Program> {
    return firstValueFrom(this.http.post<Program>(`${this.base}/programs`, req));
  }

  getActiveProgram(): Promise<Program> {
    return firstValueFrom(this.http.get<Program>(`${this.base}/programs/active`));
  }

  generateNextWeek(programId: string): Promise<Week> {
    return firstValueFrom(this.http.post<Week>(`${this.base}/programs/${programId}/weeks/next`, {}));
  }

  logExercise(exerciseId: string, loggedWeight: number | null, loggedReps: number | null): Promise<Exercise> {
    return firstValueFrom(
      this.http.patch<Exercise>(`${this.base}/exercises/${exerciseId}/log`, { loggedWeight, loggedReps })
    );
  }

  swapExercise(exerciseId: string, requestText: string): Promise<Exercise> {
    return firstValueFrom(
      this.http.post<Exercise>(`${this.base}/exercises/${exerciseId}/swap`, { requestText })
    );
  }

  removeExercise(exerciseId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/exercises/${exerciseId}`));
  }

  updateDayNote(dayId: string, athleteNote: string): Promise<Day> {
    return firstValueFrom(this.http.patch<Day>(`${this.base}/days/${dayId}/note`, { athleteNote }));
  }
}

import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardData } from '../../shared/models/dashboard.model';

@Service()
export class DashboardApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listExerciseNames(): Promise<string[]> {
    return firstValueFrom(this.http.get<string[]>(`${this.base}/dashboard/exercises`));
  }

  getDashboard(exercise: string | null): Promise<DashboardData> {
    const url = exercise
      ? `${this.base}/dashboard?exercise=${encodeURIComponent(exercise)}`
      : `${this.base}/dashboard`;
    return firstValueFrom(this.http.get<DashboardData>(url));
  }
}

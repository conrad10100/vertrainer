import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminUserUsage } from '../../shared/models/admin.model';
import { DashboardData } from '../../shared/models/dashboard.model';

@Service()
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  listUsers(): Promise<AdminUserUsage[]> {
    return firstValueFrom(this.http.get<AdminUserUsage[]>(`${this.base}/admin/usage`));
  }

  listUserExercises(userId: string): Promise<string[]> {
    return firstValueFrom(this.http.get<string[]>(`${this.base}/admin/users/${userId}/dashboard/exercises`));
  }

  getUserDashboard(userId: string, exercise: string | null): Promise<DashboardData> {
    const url = exercise
      ? `${this.base}/admin/users/${userId}/dashboard?exercise=${encodeURIComponent(exercise)}`
      : `${this.base}/admin/users/${userId}/dashboard`;
    return firstValueFrom(this.http.get<DashboardData>(url));
  }
}

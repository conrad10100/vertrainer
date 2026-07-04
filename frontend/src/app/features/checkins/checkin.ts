import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Checkin, CreateCheckinRequest } from '../../shared/models/checkin.model';

@Service()
export class CheckinApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  create(req: CreateCheckinRequest): Promise<Checkin> {
    return firstValueFrom(this.http.post<Checkin>(`${this.base}/checkins`, req));
  }
}

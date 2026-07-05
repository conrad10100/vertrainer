import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AdminApi } from '../admin';
import { AdminUserUsage } from '../../../shared/models/admin.model';
import { Dashboard } from '../../dashboard/dashboard/dashboard';

@Component({
  selector: 'app-admin-users',
  imports: [Dashboard],
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.css',
})
export class AdminUsers implements OnInit {
  private readonly adminApi = inject(AdminApi);

  loading = signal(true);
  forbidden = signal(false);
  users = signal<AdminUserUsage[]>([]);
  selectedUser = signal<AdminUserUsage | null>(null);

  async ngOnInit() {
    try {
      this.users.set(await this.adminApi.listUsers());
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 403) {
        this.forbidden.set(true);
      } else {
        console.error(err);
      }
    } finally {
      this.loading.set(false);
    }
  }

  viewUser(user: AdminUserUsage) {
    this.selectedUser.set(user);
  }

  backToList() {
    this.selectedUser.set(null);
  }
}

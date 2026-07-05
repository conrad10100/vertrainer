import { Component, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Supabase } from './core/supabase';
import { APP_VERSION } from './version';
import { AdminApi } from './features/admin/admin';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly supabase = inject(Supabase);
  private readonly router = inject(Router);
  private readonly adminApi = inject(AdminApi);
  protected readonly version = APP_VERSION;
  protected readonly isAdmin = signal(false);

  constructor() {
    // Admin status is derived by whether the admin-only endpoint accepts us --
    // no separate "am I admin" endpoint needed, and nothing admin-specific is
    // hardcoded client-side.
    effect(() => {
      if (this.supabase.session()) {
        this.adminApi.listUsers().then(
          () => this.isAdmin.set(true),
          () => this.isAdmin.set(false)
        );
      } else {
        this.isAdmin.set(false);
      }
    });
  }

  async logout() {
    await this.supabase.signOut();
    this.router.navigate(['/login']);
  }
}

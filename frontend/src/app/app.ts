import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Supabase } from './core/supabase';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly supabase = inject(Supabase);
  private readonly router = inject(Router);

  async logout() {
    await this.supabase.signOut();
    this.router.navigate(['/login']);
  }
}

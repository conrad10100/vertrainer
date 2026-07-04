import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Supabase } from '../../../core/supabase';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly supabase = inject(Supabase);
  private readonly router = inject(Router);

  email = '';
  password = '';
  errorMsg = signal('');
  submitting = signal(false);

  async submit() {
    this.errorMsg.set('');
    this.submitting.set(true);
    try {
      await this.supabase.signIn(this.email, this.password);
      this.router.navigate(['/program']);
    } catch (err) {
      this.errorMsg.set(err instanceof Error ? err.message : 'Login failed.');
    } finally {
      this.submitting.set(false);
    }
  }
}

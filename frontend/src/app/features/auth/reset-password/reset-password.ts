import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Supabase } from '../../../core/supabase';

@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.css',
})
export class ResetPassword {
  private readonly supabase = inject(Supabase);
  private readonly router = inject(Router);

  password = '';
  confirmPassword = '';
  errorMsg = signal('');
  submitting = signal(false);

  async submit() {
    this.errorMsg.set('');
    if (this.password !== this.confirmPassword) {
      this.errorMsg.set('Passwords do not match.');
      return;
    }
    this.submitting.set(true);
    try {
      await this.supabase.updatePassword(this.password);
      this.router.navigate(['/program']);
    } catch (err) {
      this.errorMsg.set(err instanceof Error ? err.message : 'Could not reset password.');
    } finally {
      this.submitting.set(false);
    }
  }
}

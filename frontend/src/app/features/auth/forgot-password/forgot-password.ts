import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Supabase } from '../../../core/supabase';

@Component({
  selector: 'app-forgot-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.css',
})
export class ForgotPassword {
  private readonly supabase = inject(Supabase);

  email = '';
  errorMsg = signal('');
  submitted = signal(false);
  submitting = signal(false);

  async submit() {
    this.errorMsg.set('');
    this.submitting.set(true);
    try {
      await this.supabase.resetPasswordForEmail(this.email);
      this.submitted.set(true);
    } catch (err) {
      this.errorMsg.set(err instanceof Error ? err.message : 'Could not send reset email.');
    } finally {
      this.submitting.set(false);
    }
  }
}

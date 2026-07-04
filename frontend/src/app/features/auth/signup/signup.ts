import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Supabase } from '../../../core/supabase';

@Component({
  selector: 'app-signup',
  imports: [FormsModule, RouterLink],
  templateUrl: './signup.html',
  styleUrl: './signup.css',
})
export class Signup {
  private readonly supabase = inject(Supabase);
  private readonly router = inject(Router);

  email = '';
  password = '';
  errorMsg = signal('');
  submitted = signal(false);
  submitting = signal(false);

  async submit() {
    this.errorMsg.set('');
    this.submitting.set(true);
    try {
      await this.supabase.signUp(this.email, this.password);
      this.submitted.set(true);
      // If email confirmation is disabled in Supabase, signUp already signs
      // the user in; the auth guard will pick up the session automatically.
      this.router.navigate(['/program']);
    } catch (err) {
      this.errorMsg.set(err instanceof Error ? err.message : 'Sign up failed.');
    } finally {
      this.submitting.set(false);
    }
  }
}

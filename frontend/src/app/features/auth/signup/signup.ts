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
      // signUp() only produces an active session immediately when email
      // confirmation is disabled in Supabase. With it enabled (the normal
      // case here), there's no session yet -- navigating to /program would
      // just bounce off the auth guard back to /login with no explanation,
      // so stay put and show the "check your email" message instead.
      if (this.supabase.session()) {
        this.router.navigate(['/program']);
      }
    } catch (err) {
      this.errorMsg.set(err instanceof Error ? err.message : 'Sign up failed.');
    } finally {
      this.submitting.set(false);
    }
  }
}

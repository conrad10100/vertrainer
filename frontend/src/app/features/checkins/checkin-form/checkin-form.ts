import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckinApi } from '../checkin';

@Component({
  selector: 'app-checkin-form',
  imports: [FormsModule],
  templateUrl: './checkin-form.html',
  styleUrl: './checkin-form.css',
})
export class CheckinForm {
  private readonly checkinApi = inject(CheckinApi);

  inches: number | null = null;
  notes = '';

  submitting = signal(false);
  successMsg = signal('');
  errorMsg = signal('');

  async submit() {
    if (this.inches == null) return;
    this.submitting.set(true);
    this.successMsg.set('');
    this.errorMsg.set('');
    try {
      await this.checkinApi.create({ inches: this.inches, notes: this.notes });
      this.successMsg.set('Check-in logged.');
      this.inches = null;
      this.notes = '';
    } catch (err) {
      console.error(err);
      this.errorMsg.set("Couldn't save that check-in — try again.");
    } finally {
      this.submitting.set(false);
    }
  }
}

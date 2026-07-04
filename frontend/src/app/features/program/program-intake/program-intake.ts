import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProgramApi } from '../program';
import { Program } from '../../../shared/models/program.model';

@Component({
  selector: 'app-program-intake',
  imports: [FormsModule],
  templateUrl: './program-intake.html',
  styleUrl: './program-intake.css',
})
export class ProgramIntake {
  private readonly programApi = inject(ProgramApi);

  created = output<Program>();

  currentVertical: number | null = null;
  targetVertical: number | null = null;
  height: number | null = null;
  bodyweight: number | null = null;
  days = 2;
  experience = 'Intermediate';
  notes = '';

  readonly experienceLevels = ['Beginner', 'Intermediate', 'Advanced'];
  readonly dayOptions = [1, 2, 3, 4];

  submitting = signal(false);
  errorMsg = signal('');

  async submit() {
    if (this.currentVertical == null || this.targetVertical == null) return;
    this.submitting.set(true);
    this.errorMsg.set('');
    try {
      const program = await this.programApi.createProgram({
        currentVertical: this.currentVertical,
        targetVertical: this.targetVertical,
        height: this.height,
        bodyweight: this.bodyweight,
        daysPerWeek: this.days,
        experienceLevel: this.experience,
        notes: this.notes,
      });
      this.created.emit(program);
    } catch (err) {
      this.errorMsg.set(
        "Couldn't build your program — the request may have failed or returned an unexpected format. Try again."
      );
      console.error(err);
    } finally {
      this.submitting.set(false);
    }
  }
}

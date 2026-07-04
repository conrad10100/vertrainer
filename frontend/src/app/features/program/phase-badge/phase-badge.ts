import { Component, computed, input } from '@angular/core';

const PHASE_DESCRIPTIONS: Record<string, string> = {
  Accumulation: 'General strength & work capacity — higher volume, moderate loads, building the base.',
  Intensification: 'Maximal strength — heavier loads, lower reps, building the force ceiling.',
  Realization: 'Power & reactive strength — lighter/faster loads, plyometrics, converting strength to jump height.',
};

@Component({
  selector: 'app-phase-badge',
  imports: [],
  templateUrl: './phase-badge.html',
  styleUrl: './phase-badge.css',
})
export class PhaseBadge {
  cycleNumber = input.required<number>();
  phase = input.required<string>();
  deload = input.required<boolean>();

  description = computed(() => PHASE_DESCRIPTIONS[this.phase()] ?? '');
}

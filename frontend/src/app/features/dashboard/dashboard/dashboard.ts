import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { DashboardApi } from '../dashboard';

const DARK_GRID = 'rgba(236, 232, 223, 0.08)';
const BRASS = '#C6963A';
const CHALK = '#ECE8DF';

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, BaseChartDirective],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private readonly dashboardApi = inject(DashboardApi);

  loading = signal(true);
  exerciseNames = signal<string[]>([]);
  selectedExercise = signal<string | null>(null);

  verticalChartData: ChartData<'line'> = { labels: [], datasets: [] };
  liftChartData: ChartData<'line'> = { labels: [], datasets: [] };
  adherenceChartData: ChartData<'bar'> = { labels: [], datasets: [] };

  readonly baseOptions: ChartConfiguration['options'] = {
    responsive: true,
    plugins: { legend: { labels: { color: CHALK } } },
    scales: {
      x: { ticks: { color: CHALK }, grid: { color: DARK_GRID } },
      y: { ticks: { color: CHALK }, grid: { color: DARK_GRID } },
    },
  };

  async ngOnInit() {
    this.exerciseNames.set(await this.dashboardApi.listExerciseNames());
    if (this.exerciseNames().length > 0) {
      this.selectedExercise.set(this.exerciseNames()[0]);
    }
    await this.refresh();
    this.loading.set(false);
  }

  async onExerciseChange() {
    await this.refresh();
  }

  private async refresh() {
    const data = await this.dashboardApi.getDashboard(this.selectedExercise());

    this.verticalChartData = {
      labels: data.verticalJumpHistory.map((p) => new Date(p.recordedAt).toLocaleDateString()),
      datasets: [
        {
          label: 'Vertical jump (in)',
          data: data.verticalJumpHistory.map((p) => p.inches),
          borderColor: BRASS,
          backgroundColor: BRASS,
          tension: 0.25,
        },
      ],
    };

    this.liftChartData = {
      labels: data.liftProgression.map((p) => `Wk ${p.weekNumber}`),
      datasets: [
        {
          label: this.selectedExercise() ?? '',
          data: data.liftProgression.map((p) => p.targetWeight),
          borderColor: BRASS,
          backgroundColor: BRASS,
          tension: 0.25,
        },
      ],
    };

    this.adherenceChartData = {
      labels: data.adherence.map((p) => `Wk ${p.weekNumber}`),
      datasets: [
        {
          label: 'Adherence (% logged)',
          data: data.adherence.map((p) => Math.round(p.loggedFraction * 100)),
          backgroundColor: BRASS,
        },
      ],
    };
  }
}

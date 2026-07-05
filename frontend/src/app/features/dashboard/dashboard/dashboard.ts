import { Component, Input, OnChanges, OnInit, SimpleChanges, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartConfiguration, ChartData } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';
import { DashboardApi } from '../dashboard';
import { AdminApi } from '../../admin/admin';
import { DashboardData } from '../../../shared/models/dashboard.model';

const DARK_GRID = 'rgba(236, 232, 223, 0.08)';
const BRASS = '#C6963A';
const CHALK = '#ECE8DF';

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, BaseChartDirective],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit, OnChanges {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly adminApi = inject(AdminApi);

  // When set (admin viewing another user's progress), data is fetched via the
  // admin-gated endpoints instead of the current user's own.
  @Input() targetUserId: string | null = null;

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
    await this.load();
  }

  async ngOnChanges(changes: SimpleChanges) {
    if (changes['targetUserId'] && !changes['targetUserId'].firstChange) {
      await this.load();
    }
  }

  private async load() {
    this.loading.set(true);
    this.exerciseNames.set(await this.listExerciseNames());
    this.selectedExercise.set(this.exerciseNames().length > 0 ? this.exerciseNames()[0] : null);
    await this.refresh();
    this.loading.set(false);
  }

  async onExerciseChange() {
    await this.refresh();
  }

  private listExerciseNames(): Promise<string[]> {
    return this.targetUserId
      ? this.adminApi.listUserExercises(this.targetUserId)
      : this.dashboardApi.listExerciseNames();
  }

  private fetchDashboard(): Promise<DashboardData> {
    return this.targetUserId
      ? this.adminApi.getUserDashboard(this.targetUserId, this.selectedExercise())
      : this.dashboardApi.getDashboard(this.selectedExercise());
  }

  private async refresh() {
    const data = await this.fetchDashboard();

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

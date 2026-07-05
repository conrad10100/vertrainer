import { Routes } from '@angular/router';
import { authGuard } from './core/auth-guard';
import { Login } from './features/auth/login/login';
import { Signup } from './features/auth/signup/signup';
import { WeekView } from './features/program/week-view/week-view';
import { CheckinForm } from './features/checkins/checkin-form/checkin-form';
import { Dashboard } from './features/dashboard/dashboard/dashboard';
import { AdminUsers } from './features/admin/admin-users/admin-users';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'program' },
  { path: 'login', component: Login },
  { path: 'signup', component: Signup },
  { path: 'program', component: WeekView, canActivate: [authGuard] },
  { path: 'checkins', component: CheckinForm, canActivate: [authGuard] },
  { path: 'dashboard', component: Dashboard, canActivate: [authGuard] },
  { path: 'admin', component: AdminUsers, canActivate: [authGuard] },
];

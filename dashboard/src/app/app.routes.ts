import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'logs',
    loadComponent: () => import('./features/logs/logs.component').then(m => m.LogsComponent)
  },
  {
    path: 'exceptions',
    loadComponent: () => import('./features/exceptions/exceptions.component').then(m => m.ExceptionsComponent)
  },
  {
    path: 'exceptions/:id',
    loadComponent: () => import('./features/exception-detail/exception-detail.component').then(m => m.ExceptionDetailComponent)
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
  }
];

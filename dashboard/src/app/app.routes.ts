import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./features/auth/callback/callback.component').then(m => m.CallbackComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'logs',
    loadComponent: () => import('./features/logs/logs.component').then(m => m.LogsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'exceptions',
    loadComponent: () => import('./features/exceptions/exceptions.component').then(m => m.ExceptionsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'exceptions/:id',
    loadComponent: () => import('./features/exception-detail/exception-detail.component').then(m => m.ExceptionDetailComponent),
    canActivate: [authGuard]
  },
  {
    path: 'settings',
    loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'service-groups',
    loadComponent: () => import('./features/service-groups/service-groups.component').then(m => m.ServiceGroupsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'traces/:traceId',
    loadComponent: () => import('./features/trace-explorer/trace-explorer.component').then(m => m.TraceExplorerComponent),
    canActivate: [authGuard]
  },
  {
    path: 'monitoring',
    loadComponent: () => import('./features/monitoring/monitoring-dashboard.component').then(m => m.MonitoringDashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'monitoring/services',
    loadComponent: () => import('./features/monitoring/monitoring-services.component').then(m => m.MonitoringServicesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'monitoring/services/:id',
    loadComponent: () => import('./features/monitoring/monitoring-service-detail.component').then(m => m.MonitoringServiceDetailComponent),
    canActivate: [authGuard]
  },
  {
    path: 'monitoring/alerts',
    loadComponent: () => import('./features/monitoring/monitoring-alerts.component').then(m => m.MonitoringAlertsComponent),
    canActivate: [authGuard]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];

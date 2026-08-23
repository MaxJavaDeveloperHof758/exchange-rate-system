import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'calculator' },
  {
    path: 'calculator',
    loadComponent: () =>
      import('./features/calculator/calculator.component').then((m) => m.CalculatorComponent),
  },
  {
    path: 'trend',
    loadComponent: () =>
      import('./features/historical-trend/historical-trend.component').then(
        (m) => m.HistoricalTrendComponent,
      ),
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./features/analytics-dashboard/analytics-dashboard.component').then(
        (m) => m.AnalyticsDashboardComponent,
      ),
  },
];

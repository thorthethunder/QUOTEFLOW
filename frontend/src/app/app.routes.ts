import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/landing/landing').then((m) => m.LandingComponent),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register/register').then((m) => m.RegisterComponent),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layout/app-shell/app-shell').then((m) => m.AppShellComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'customers',
        loadComponent: () =>
          import('./features/customers/customer-list/customer-list').then(
            (m) => m.CustomerListComponent,
          ),
      },
      {
        path: 'customers/new',
        loadComponent: () =>
          import('./features/customers/customer-form/customer-form').then(
            (m) => m.CustomerFormComponent,
          ),
      },
      {
        path: 'customers/:id',
        loadComponent: () =>
          import('./features/customers/customer-detail/customer-detail').then(
            (m) => m.CustomerDetailComponent,
          ),
      },
      {
        path: 'customers/:id/edit',
        loadComponent: () =>
          import('./features/customers/customer-form/customer-form').then(
            (m) => m.CustomerFormComponent,
          ),
      },
      {
        path: 'quotations',
        loadComponent: () =>
          import('./features/quotations/quotation-list/quotation-list').then(
            (m) => m.QuotationListComponent,
          ),
      },
      {
        path: 'quotations/new',
        loadComponent: () =>
          import('./features/quotations/quotation-editor/quotation-editor').then(
            (m) => m.QuotationEditorComponent,
          ),
      },
      {
        path: 'quotations/:id',
        loadComponent: () =>
          import('./features/quotations/quotation-detail/quotation-detail').then(
            (m) => m.QuotationDetailComponent,
          ),
      },
      {
        path: 'quotations/:id/edit',
        loadComponent: () =>
          import('./features/quotations/quotation-editor/quotation-editor').then(
            (m) => m.QuotationEditorComponent,
          ),
      },
      {
        path: 'invoices',
        loadComponent: () =>
          import('./features/invoices/invoice-list/invoice-list').then(
            (m) => m.InvoiceListComponent,
          ),
      },
      {
        path: 'invoices/new',
        loadComponent: () =>
          import('./features/invoices/invoice-editor/invoice-editor').then(
            (m) => m.InvoiceEditorComponent,
          ),
      },
      {
        path: 'invoices/:id',
        loadComponent: () =>
          import('./features/invoices/invoice-detail/invoice-detail').then(
            (m) => m.InvoiceDetailComponent,
          ),
      },
      {
        path: 'invoices/:id/edit',
        loadComponent: () =>
          import('./features/invoices/invoice-editor/invoice-editor').then(
            (m) => m.InvoiceEditorComponent,
          ),
      },
      {
        path: 'copilot',
        loadComponent: () =>
          import('./features/copilot/business-copilot-page').then(
            (m) => m.BusinessCopilotPageComponent,
          ),
      },
      {
        path: 'insights',
        loadComponent: () =>
          import('./features/insights/business-insights-page').then(
            (m) => m.BusinessInsightsPageComponent,
          ),
      },
      {
        path: 'knowledge',
        loadComponent: () =>
          import('./features/knowledge/business-knowledge-page').then(
            (m) => m.BusinessKnowledgePageComponent,
          ),
      },
      {
        path: 'workflows',
        loadComponent: () =>
          import('./features/workflows/ai-workflows-page').then(
            (m) => m.AiWorkflowsPageComponent,
          ),
      },
      {
        path: 'ai-usage',
        loadComponent: () =>
          import('./features/ai-usage/ai-usage-page').then(
            (m) => m.AiUsagePageComponent,
          ),
      },
      {
        path: 'plan',
        loadComponent: () =>
          import('./features/plan/plan-page').then((m) => m.PlanPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

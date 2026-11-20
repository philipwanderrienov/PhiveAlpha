import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'overview',
    title: 'Overview | PhiveAlpha',
    loadComponent: () => import('./overview/overview.page').then(page => page.OverviewPage),
  },
  {
    path: 'weekly-top-five',
    title: 'Weekly Top 5 | PhiveAlpha',
    loadComponent: () => import('./weekly-top-five/weekly-top-five.page').then(page => page.WeeklyTopFivePage),
  },
  {
    path: 'stock-analysis',
    title: 'Stock Analysis | PhiveAlpha',
    loadComponent: () => import('./stock-analysis/stock-analysis.page').then(page => page.StockAnalysisPage),
  },
  {
    path: 'news-intelligence',
    title: 'News Intelligence | PhiveAlpha',
    loadComponent: () => import('./news-intelligence/news-intelligence.page').then(page => page.NewsIntelligencePage),
  },
  {
    path: 'paper-trading',
    title: 'Paper Trading | PhiveAlpha',
    loadComponent: () => import('./paper-trading/paper-trading.page').then(page => page.PaperTradingPage),
  },
  { path: '', pathMatch: 'full', redirectTo: 'overview' },
  { path: '**', redirectTo: 'overview' },
];

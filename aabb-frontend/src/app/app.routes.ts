import { Routes } from '@angular/router';

import { exigeLogin } from './core/autenticacao';

export const routes: Routes = [
  {
    path: '',
    canActivate: [exigeLogin],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'ranking' },
      { path: 'ranking', loadComponent: () => import('./features/ranking/ranking').then((m) => m.RankingPage) },
      { path: 'partidas', loadComponent: () => import('./features/partidas/partidas').then((m) => m.PartidasPage) },
      {
        path: 'partidas/:id',
        loadComponent: () => import('./features/partidas/partida-detalhe').then((m) => m.PartidaDetalhePage),
      },
      {
        path: 'presencas',
        loadComponent: () => import('./features/presencas/presencas').then((m) => m.PresencasPage),
      },
      {
        path: 'jogadores',
        loadComponent: () => import('./features/jogadores/jogadores').then((m) => m.JogadoresPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

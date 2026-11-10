import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Autenticacao } from './core/autenticacao';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="sticky top-0 z-10 bg-slate-900 text-white shadow">
      <div class="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <a routerLink="/ranking" class="flex items-center gap-2 text-lg font-black tracking-tight">
          <span class="rounded-lg bg-emerald-500 px-2 py-0.5 text-slate-900">AABB</span>
          <span class="hidden sm:inline">Painel da Pelada</span>
        </a>
        @if (autenticacao.logado()) {
          <div class="flex items-center gap-3 text-sm">
            <span class="hidden text-slate-300 sm:inline">{{ autenticacao.nomeDoUsuario() }}</span>
            <button type="button" class="rounded-lg px-2 py-1 text-slate-300 hover:bg-slate-800" (click)="autenticacao.sair()">
              Sair
            </button>
          </div>
        }
      </div>
      @if (autenticacao.logado()) {
        <nav class="mx-auto flex max-w-5xl gap-1 overflow-x-auto px-2 pb-2 text-sm font-semibold">
          @for (item of menu; track item.rota) {
            <a
              [routerLink]="item.rota"
              routerLinkActive="bg-emerald-500 text-slate-900"
              class="whitespace-nowrap rounded-lg px-3 py-1.5 text-slate-200 hover:bg-slate-800"
            >
              {{ item.nome }}
            </a>
          }
        </nav>
      }
    </header>
    <main class="mx-auto max-w-5xl px-4 py-5">
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly autenticacao = inject(Autenticacao);
  protected readonly menu = [
    { rota: '/ranking', nome: 'Ranking' },
    { rota: '/partidas', nome: 'Partidas' },
    { rota: '/presencas', nome: 'Presenças' },
    { rota: '/jogadores', nome: 'Jogadores' },
  ];
}

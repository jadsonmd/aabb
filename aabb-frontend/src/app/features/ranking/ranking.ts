import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';

import { Api } from '../../core/api';
import { LinhaDoRanking } from '../../core/modelos';

type Coluna = 'skill' | 'gols' | 'canetas' | 'chapeus' | 'titulos' | 'presencas';

/** Leaderboard do ano, ordenável, com destaque para o Top 3 e a zona da "Bola Murcha". */
@Component({
  selector: 'app-ranking',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-4 flex items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-black">Ranking {{ ano() }}</h1>
        <p class="text-sm text-slate-500">Skill atual e números do ano</p>
      </div>
      <select class="campo w-28" [value]="ano()" (change)="mudarAno(+$any($event.target).value)">
        @for (a of anos; track a) {
          <option [value]="a">{{ a }}</option>
        }
      </select>
    </div>

    @if (carregando()) {
      <p class="card text-slate-500">Carregando…</p>
    } @else if (linhas().length === 0) {
      <p class="card text-slate-500">Nenhum jogador cadastrado ainda.</p>
    } @else {
      <div class="card overflow-x-auto p-0">
        <table class="w-full text-sm">
          <thead class="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th class="px-3 py-3">#</th>
              <th class="px-3 py-3">Jogador</th>
              @for (c of colunas; track c.chave) {
                <th class="cursor-pointer px-3 py-3 text-right whitespace-nowrap" (click)="ordenarPor(c.chave)">
                  {{ c.nome }}{{ ordem() === c.chave ? ' ▾' : '' }}
                </th>
              }
            </tr>
          </thead>
          <tbody>
            @for (linha of ordenadas(); track linha.jogadorId; let i = $index; let ultimo = $last) {
              <tr
                class="border-t border-slate-100"
                [class.bg-amber-50]="i < 3"
                [class.bg-rose-50]="i >= ordenadas().length - 3 && ordenadas().length > 6"
              >
                <td class="px-3 py-2.5 font-bold">{{ medalha(i) }}</td>
                <td class="px-3 py-2.5">
                  <div class="font-semibold">{{ linha.apelido }}</div>
                  <div class="text-xs text-slate-500">{{ linha.nome }}</div>
                </td>
                <td class="px-3 py-2.5 text-right font-mono font-bold">{{ linha.skill | number: '1.1-1' }}</td>
                <td class="px-3 py-2.5 text-right">{{ linha.gols }}</td>
                <td class="px-3 py-2.5 text-right">{{ linha.canetas }}</td>
                <td class="px-3 py-2.5 text-right">{{ linha.chapeus }}</td>
                <td class="px-3 py-2.5 text-right">{{ linha.titulos }}</td>
                <td class="px-3 py-2.5 text-right">{{ linha.presencas }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (linhas().length > 6) {
        <p class="mt-2 text-xs text-slate-500">🥇 Top 3 em amarelo · 🎈 zona da Bola Murcha em vermelho</p>
      }
    }
  `,
})
export class RankingPage {
  private readonly api = inject(Api);

  protected readonly anos = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - i);
  protected readonly colunas: { chave: Coluna; nome: string }[] = [
    { chave: 'skill', nome: 'Skill' },
    { chave: 'gols', nome: 'Gols' },
    { chave: 'canetas', nome: 'Canetas' },
    { chave: 'chapeus', nome: 'Chapéus' },
    { chave: 'titulos', nome: 'Títulos' },
    { chave: 'presencas', nome: 'Presenças' },
  ];
  protected readonly ano = signal(new Date().getFullYear());
  protected readonly ordem = signal<Coluna>('skill');
  protected readonly linhas = signal<LinhaDoRanking[]>([]);
  protected readonly carregando = signal(true);
  protected readonly ordenadas = computed(() => {
    const coluna = this.ordem();
    return [...this.linhas()].sort((a, b) => b[coluna] - a[coluna] || b.skill - a.skill);
  });

  constructor() {
    this.carregar();
  }

  protected mudarAno(ano: number): void {
    this.ano.set(ano);
    this.carregar();
  }

  protected ordenarPor(coluna: Coluna): void {
    this.ordem.set(coluna);
  }

  protected medalha(posicao: number): string {
    return ['🥇', '🥈', '🥉'][posicao] ?? String(posicao + 1);
  }

  private carregar(): void {
    this.carregando.set(true);
    this.api.ranking(this.ano()).subscribe({
      next: (linhas) => {
        this.linhas.set(linhas);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }
}

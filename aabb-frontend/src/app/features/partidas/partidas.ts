import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Api } from '../../core/api';
import { formatarData, ResumoDaPartida, StatusPartida } from '../../core/modelos';

@Component({
  selector: 'app-partidas',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-4 text-2xl font-black">Partidas</h1>
    @if (carregando()) {
      <p class="card text-slate-500">Carregando…</p>
    } @else if (partidas().length === 0) {
      <p class="card text-slate-500">Nenhuma partida ainda. A lista abre às 6h dos dias de jogo.</p>
    } @else {
      <ul class="space-y-2">
        @for (p of partidas(); track p.id) {
          <li>
            <a [routerLink]="['/partidas', p.id]" class="card flex items-center justify-between hover:ring-emerald-400">
              <div>
                <div class="font-semibold">{{ data(p.data) }}</div>
                <div class="text-xs text-slate-500">{{ p.confirmados }} confirmados</div>
              </div>
              <div class="text-right">
                @if (p.placarAzul !== null && p.placarAmarelo !== null) {
                  <div class="font-mono font-bold">🔵 {{ p.placarAzul }} × {{ p.placarAmarelo }} 🟡</div>
                }
                <span class="rounded-full px-2 py-0.5 text-xs font-semibold" [class]="cor(p.status)">
                  {{ nomeDoStatus(p.status) }}
                </span>
              </div>
            </a>
          </li>
        }
      </ul>
    }
  `,
})
export class PartidasPage {
  private readonly api = inject(Api);
  protected readonly partidas = signal<ResumoDaPartida[]>([]);
  protected readonly carregando = signal(true);
  protected readonly data = formatarData;

  constructor() {
    this.api.partidas().subscribe({
      next: (partidas) => {
        this.partidas.set(partidas);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  protected nomeDoStatus(status: StatusPartida): string {
    return { ABERTA: 'Lista aberta', SORTEADA: 'Sorteada', FINALIZADA: 'Finalizada', CANCELADA: 'Cancelada' }[status];
  }

  protected cor(status: StatusPartida): string {
    return {
      ABERTA: 'bg-sky-100 text-sky-800',
      SORTEADA: 'bg-amber-100 text-amber-800',
      FINALIZADA: 'bg-emerald-100 text-emerald-800',
      CANCELADA: 'bg-slate-200 text-slate-600',
    }[status];
  }
}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { Api } from '../../core/api';
import { formatarData, PresencasDoJogador } from '../../core/modelos';

/** Presenças computadas no #sortear (membros sorteados, titulares e reservas). */
@Component({
  selector: 'app-presencas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-1 text-2xl font-black">Presenças {{ ano }}</h1>
    <p class="mb-4 text-sm text-slate-500">Computadas automaticamente no sorteio</p>
    @if (carregando()) {
      <p class="card text-slate-500">Carregando…</p>
    } @else {
      <ul class="space-y-2">
        @for (j of presencas(); track j.jogadorId) {
          <li class="card">
            <button type="button" class="flex w-full items-center justify-between text-left" (click)="alternar(j.jogadorId)">
              <span class="font-semibold">{{ j.apelido }}</span>
              <span class="text-sm">
                <span class="font-bold">{{ j.noAno }}</span>
                <span class="text-slate-500"> no ano · {{ j.total }} no total</span>
              </span>
            </button>
            @if (aberto() === j.jogadorId) {
              <div class="mt-3 flex flex-wrap gap-1.5">
                @for (d of j.datas; track d) {
                  <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs">{{ data(d) }}</span>
                } @empty {
                  <span class="text-xs text-slate-500">Nenhuma presença ainda.</span>
                }
              </div>
            }
          </li>
        }
      </ul>
    }
  `,
})
export class PresencasPage {
  private readonly api = inject(Api);
  protected readonly ano = new Date().getFullYear();
  protected readonly presencas = signal<PresencasDoJogador[]>([]);
  protected readonly carregando = signal(true);
  protected readonly aberto = signal<string | null>(null);
  protected readonly data = formatarData;

  constructor() {
    this.api.presencas(this.ano).subscribe({
      next: (p) => {
        this.presencas.set(p);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  protected alternar(id: string): void {
    this.aberto.update((atual) => (atual === id ? null : id));
  }
}

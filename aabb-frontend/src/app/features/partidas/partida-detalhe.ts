import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Api } from '../../core/api';
import {
  DetalheDaPartida,
  Escalado,
  EventoDaPartida,
  formatarData,
  sorteioVigente,
  Time,
  TipoEvento,
} from '../../core/modelos';

/** Pós-jogo: times sorteados, gols aprovados pelo VAR, placar e eventos do histórico. */
@Component({
  selector: 'app-partida-detalhe',
  imports: [DecimalPipe, FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/partidas" class="mb-3 inline-block text-sm font-semibold text-emerald-700">← Partidas</a>
    @if (detalhe(); as d) {
      <h1 class="text-2xl font-black">{{ data(d.partida.data) }}</h1>
      <p class="mb-4 text-sm text-slate-500">
        {{ d.presentes.length }} presenças ·
        @switch (d.partida.resultado) {
          @case ('AZUL') { 🏆 Time Azul venceu }
          @case ('AMARELO') { 🏆 Time Amarelo venceu }
          @case ('EMPATE') { 🤝 Empate }
          @default { resultado ainda não informado no WhatsApp }
        }
      </p>

      @if (sorteio(); as s) {
        <div class="mb-4 grid gap-3 sm:grid-cols-2">
          @for (lado of [{ nome: '🔵 Time Azul', time: s.azul }, { nome: '🟡 Time Amarelo', time: s.amarelo }]; track lado.nome) {
            <section class="card">
              <h2 class="mb-2 flex justify-between font-bold">
                <span>{{ lado.nome }}</span>
                <span class="font-mono text-sm text-slate-500">skill {{ lado.time.somaSkillTitulares | number: '1.1-1' }}</span>
              </h2>
              <ul class="space-y-1 text-sm">
                @for (e of lado.time.titulares; track e.nome) {
                  <li>{{ e.goleiro ? '🧤' : '•' }} {{ nomeEscalado(e) }} {{ golsDe(e) }}</li>
                }
              </ul>
              @if (lado.time.reservas.length) {
                <p class="mt-2 text-xs text-slate-500">
                  Reservas: {{ nomesReservas(lado.time) }}
                </p>
              }
            </section>
          }
        </div>

        @if (d.partida.status !== 'CANCELADA') {
          <section class="card">
            <h2 class="mb-3 font-bold">Pós-jogo</h2>
            <div class="mb-4 flex items-end gap-3">
              <label class="flex-1">
                <span class="rotulo">🔵 Azul</span>
                <input class="campo text-center text-lg font-bold" type="number" min="0" max="99" [(ngModel)]="placarAzul" />
              </label>
              <span class="pb-3 font-bold text-slate-400">×</span>
              <label class="flex-1">
                <span class="rotulo">🟡 Amarelo</span>
                <input class="campo text-center text-lg font-bold" type="number" min="0" max="99" [(ngModel)]="placarAmarelo" />
              </label>
            </div>

            <span class="rotulo">Eventos (só histórico, não alteram skill)</span>
            <div class="mb-2 flex flex-col gap-2 sm:flex-row">
              <select class="campo" [(ngModel)]="jogadorDoEvento">
                <option [ngValue]="null">Jogador…</option>
                @for (e of escalados(); track e.nome) {
                  <option [ngValue]="e">{{ nomeEscalado(e) }}</option>
                }
              </select>
              <select class="campo sm:w-44" [(ngModel)]="tipoDoEvento">
                <option value="ASSISTENCIA">Assistência</option>
                <option value="GOL_CONTRA">Gol contra</option>
              </select>
              <button type="button" class="btn-secundario" [disabled]="!jogadorDoEvento" (click)="adicionarEvento()">+</button>
            </div>
            <ul class="mb-4 space-y-1 text-sm">
              @for (ev of eventos(); track $index) {
                <li class="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-1.5">
                  <span>{{ ev.tipo === 'ASSISTENCIA' ? '🅰️ Assistência' : '🙈 Gol contra' }} · {{ ev.nome }}</span>
                  <button type="button" class="text-slate-400 hover:text-rose-600" (click)="removerEvento($index)">✕</button>
                </li>
              }
            </ul>
            @if (erro()) {
              <p class="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ erro() }}</p>
            }
            @if (salvo()) {
              <p class="mb-3 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">Partida finalizada ✔</p>
            }
            <button type="button" class="btn-primario w-full" [disabled]="salvando()" (click)="finalizar()">
              {{ d.partida.status === 'FINALIZADA' ? 'Salvar alterações' : 'Finalizar partida' }}
            </button>
            <p class="mt-2 text-xs text-slate-500">
              Gols, vitória e empate são computados pelo WhatsApp (#marcou, #timeazulganhou…). Finalizar bloqueia o #ressortear.
            </p>
          </section>
        }
      } @else {
        <p class="card text-slate-500">Ainda não houve sorteio para esta partida.</p>
      }
    } @else {
      <p class="card text-slate-500">Carregando…</p>
    }
  `,
})
export class PartidaDetalhePage {
  private readonly api = inject(Api);

  readonly id = input.required<string>();
  protected readonly detalhe = signal<DetalheDaPartida | null>(null);
  protected readonly sorteio = computed(() => {
    const d = this.detalhe();
    return d ? sorteioVigente(d.partida) : undefined;
  });
  protected readonly escalados = computed(() => {
    const s = this.sorteio();
    return s ? [s.azul, s.amarelo].flatMap((t) => [...t.titulares, ...t.reservas]) : [];
  });
  protected readonly eventos = signal<EventoDaPartida[]>([]);
  protected readonly salvando = signal(false);
  protected readonly salvo = signal(false);
  protected readonly erro = signal('');
  protected readonly data = formatarData;

  protected placarAzul = 0;
  protected placarAmarelo = 0;
  protected jogadorDoEvento: Escalado | null = null;
  protected tipoDoEvento: TipoEvento = 'ASSISTENCIA';

  constructor() {
    effect(() => {
      this.api.partida(this.id()).subscribe((d) => {
        this.placarAzul = d.partida.placarAzul ?? 0;
        this.placarAmarelo = d.partida.placarAmarelo ?? 0;
        this.eventos.set(d.partida.eventos ?? []);
        this.detalhe.set(d);
      });
    });
  }

  protected nomeEscalado(e: Escalado): string {
    return e.tipo === 'CONVIDADO' ? `${e.nome} (convidado)` : e.nome;
  }

  protected nomesReservas(time: Time): string {
    return time.reservas.map((e) => this.nomeEscalado(e)).join(', ');
  }

  protected golsDe(e: Escalado): string {
    const gols = this.detalhe()?.gols.find((g) => g.autor.jogadorId === e.jogadorId)?.quantidade ?? 0;
    return gols ? '⚽'.repeat(Math.min(gols, 5)) + (gols > 5 ? ` ${gols}` : '') : '';
  }

  protected adicionarEvento(): void {
    const jogador = this.jogadorDoEvento;
    if (!jogador) {
      return;
    }
    this.eventos.update((lista) => [
      ...lista,
      { tipo: this.tipoDoEvento, jogadorId: jogador.jogadorId, nome: jogador.nome },
    ]);
    this.jogadorDoEvento = null;
  }

  protected removerEvento(indice: number): void {
    this.eventos.update((lista) => lista.filter((_, i) => i !== indice));
  }

  protected finalizar(): void {
    this.salvando.set(true);
    this.erro.set('');
    this.salvo.set(false);
    this.api.finalizar(this.id(), this.placarAzul, this.placarAmarelo, this.eventos()).subscribe({
      next: (partida) => {
        this.detalhe.update((d) => (d ? { ...d, partida } : d));
        this.salvando.set(false);
        this.salvo.set(true);
      },
      error: (e) => {
        this.erro.set(e.error?.detail ?? 'Não foi possível finalizar a partida.');
        this.salvando.set(false);
      },
    });
  }
}

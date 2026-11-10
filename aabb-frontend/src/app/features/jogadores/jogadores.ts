import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { Api } from '../../core/api';
import { Jogador, JogadorRequest, Posicao, POSICOES } from '../../core/modelos';

const VAZIO: JogadorRequest = { nome: '', apelido: '', whatsapp: '', posicoes: ['L'], skillBase: 5, admin: false };

/** Cadastro de membros: nome, apelido, WhatsApp, posições (a primeira é a principal) e skill base. */
@Component({
  selector: 'app-jogadores',
  imports: [DecimalPipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-1 flex items-center justify-between">
      <h1 class="text-2xl font-black">Jogadores</h1>
      <button type="button" class="btn-primario" (click)="novo()">+ Novo</button>
    </div>
    <p class="mb-4 text-xs text-slate-500">
      Quem manda mensagem no grupo é cadastrado automaticamente (skill 5, Lateral). Ajuste o apelido e as posições
      aqui, e desative quem não joga.
    </p>

    @if (editando() !== undefined) {
      <form class="card mb-4 space-y-3" (ngSubmit)="salvar()">
        <h2 class="font-bold">{{ editando() ? 'Editar jogador' : 'Novo jogador' }}</h2>
        <div class="grid gap-3 sm:grid-cols-2">
          <label><span class="rotulo">Nome</span><input class="campo" name="nome" [(ngModel)]="form.nome" required /></label>
          <label>
            <span class="rotulo">Apelido (nome no bot)</span>
            <input class="campo" name="apelido" [(ngModel)]="form.apelido" required />
          </label>
          <label>
            <span class="rotulo">WhatsApp (DDI + DDD + número)</span>
            <input class="campo" name="whatsapp" inputmode="numeric" placeholder="5511999999999" [(ngModel)]="form.whatsapp" required />
          </label>
          <label>
            <span class="rotulo">Skill base (nota de chegada)</span>
            <input class="campo" name="skillBase" type="number" min="0" max="10" step="0.1" [(ngModel)]="form.skillBase" />
          </label>
          @if (editando()) {
            <label>
              <span class="rotulo">Skill atual (0 a 10)</span>
              <input class="campo" name="skill" type="number" min="0" max="10" step="0.1" [(ngModel)]="form.skill" />
            </label>
          }
          <label>
            <span class="rotulo">Presenças em {{ ano }}</span>
            <input class="campo" name="presencasNoAno" type="number" min="0" max="999" step="1" [(ngModel)]="form.presencasNoAno" />
          </label>
        </div>
        <div>
          <span class="rotulo">Posições (na ordem; a primeira é a principal)</span>
          <div class="flex flex-wrap gap-2">
            @for (p of posicoes; track p.valor) {
              <button
                type="button"
                class="rounded-xl px-3 py-2 text-sm font-semibold ring-1"
                [class]="form.posicoes.includes(p.valor) ? 'bg-emerald-600 text-white ring-emerald-600' : 'bg-white ring-slate-300'"
                (click)="alternarPosicao(p.valor)"
              >
                @if (form.posicoes.indexOf(p.valor) >= 0) {
                  {{ form.posicoes.indexOf(p.valor) + 1 }}.
                }
                {{ p.nome }}
              </button>
            }
          </div>
        </div>
        <label class="flex items-center gap-2 text-sm">
          <input type="checkbox" name="admin" class="size-4 accent-emerald-600" [(ngModel)]="form.admin" />
          Administrador (pode usar #sortear, #ressortear, #abrirlista e #jogocancelado)
        </label>
        <p class="text-xs text-slate-500">
          A skill atual muda sozinha com o VAR e os resultados; alterar aqui é um ajuste manual (fica no histórico).
          As presenças vêm do #sortear; o valor informado corrige o total do ano.
        </p>
        @if (erro()) {
          <p class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ erro() }}</p>
        }
        <div class="flex gap-2">
          <button type="submit" class="btn-primario flex-1" [disabled]="salvando()">Salvar</button>
          <button type="button" class="btn-secundario" (click)="cancelar()">Cancelar</button>
        </div>
      </form>
    }

    <ul class="space-y-2">
      @for (j of jogadores(); track j.id) {
        <li class="card" [class.opacity-50]="!j.ativo">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="font-semibold">
                {{ j.apelido }}
                @if (j.admin) {
                  <span class="ml-1 rounded-full bg-slate-900 px-2 py-0.5 text-xs text-white">admin</span>
                }
              </div>
              <div class="text-xs text-slate-500">
                {{ j.nome }} · {{ j.whatsapp }} · {{ j.posicoes.join(', ') }} · {{ presencas()[j.id] ?? 0 }} presenças em {{ ano }}
              </div>
              @if (j.lids.length) {
                <div class="mt-1 flex flex-wrap gap-1">
                  @for (lid of j.lids; track lid) {
                    <span class="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs">
                      &#64;lid {{ lid }}
                      <button type="button" title="Desvincular" class="text-slate-400 hover:text-rose-600" (click)="desvincular(j, lid)">✕</button>
                    </span>
                  }
                </div>
              }
            </div>
            <div class="text-right">
              <div class="font-mono text-lg font-bold">{{ j.skill | number: '1.1-1' }}</div>
              <div class="mt-1 flex gap-2 text-xs font-semibold">
                <button type="button" class="text-emerald-700" (click)="editar(j)">Editar</button>
                <button type="button" class="text-slate-500" (click)="alternarAtivo(j, !j.ativo)">{{ j.ativo ? 'Desativar' : 'Ativar' }}</button>
              </div>
            </div>
          </div>
        </li>
      } @empty {
        <li class="card text-slate-500">Nenhum jogador cadastrado.</li>
      }
    </ul>
  `,
})
export class JogadoresPage {
  private readonly api = inject(Api);

  protected readonly posicoes = POSICOES;
  protected readonly ano = new Date().getFullYear();
  protected readonly jogadores = signal<Jogador[]>([]);
  /** Presenças no ano por jogador (sorteios + ajustes manuais). */
  protected readonly presencas = signal<Record<string, number>>({});
  /** undefined = formulário fechado; null = novo; Jogador = edição */
  protected readonly editando = signal<Jogador | null | undefined>(undefined);
  protected readonly salvando = signal(false);
  protected readonly erro = signal('');
  protected form: JogadorRequest = { ...VAZIO };

  constructor() {
    this.carregar();
  }

  protected novo(): void {
    this.form = { ...VAZIO, posicoes: [...VAZIO.posicoes], presencasNoAno: 0 };
    this.erro.set('');
    this.editando.set(null);
  }

  protected editar(j: Jogador): void {
    this.form = {
      nome: j.nome,
      apelido: j.apelido,
      whatsapp: j.whatsapp,
      posicoes: [...j.posicoes],
      skillBase: j.skillBase,
      admin: j.admin,
      skill: j.skill,
      presencasNoAno: this.presencas()[j.id] ?? 0,
    };
    this.erro.set('');
    this.editando.set(j);
  }

  protected cancelar(): void {
    this.editando.set(undefined);
  }

  protected alternarPosicao(p: Posicao): void {
    this.form.posicoes = this.form.posicoes.includes(p)
      ? this.form.posicoes.filter((x) => x !== p)
      : [...this.form.posicoes, p];
  }

  protected salvar(): void {
    const jogador = this.editando();
    this.salvando.set(true);
    this.erro.set('');
    const requisicao = jogador ? this.api.atualizarJogador(jogador.id, this.form) : this.api.criarJogador(this.form);
    requisicao.subscribe({
      next: () => {
        this.salvando.set(false);
        this.editando.set(undefined);
        this.carregar();
      },
      error: (e) => {
        this.salvando.set(false);
        this.erro.set(
          e.status === 409
            ? 'Já existe um jogador com esse WhatsApp.'
            : 'Confira os campos: nome, apelido, WhatsApp só com dígitos, ao menos uma posição e skill de 0 a 10.',
        );
      },
    });
  }

  /** Desativado não é reconhecido nos comandos nem recadastrado automaticamente. */
  protected alternarAtivo(j: Jogador, ativo: boolean): void {
    this.api.definirAtivo(j.id, ativo).subscribe(() => this.carregar());
  }

  protected desvincular(j: Jogador, lid: string): void {
    this.api.desvincularLid(j.id, lid).subscribe(() => this.carregar());
  }

  private carregar(): void {
    this.api.jogadores().subscribe((j) => this.jogadores.set(j));
    this.api.presencas(this.ano).subscribe((lista) =>
      this.presencas.set(Object.fromEntries(lista.map((p) => [p.jogadorId, p.noAno]))),
    );
  }
}

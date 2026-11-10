import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  DetalheDaPartida,
  EventoDaPartida,
  Jogador,
  JogadorRequest,
  LinhaDoRanking,
  Partida,
  PresencasDoJogador,
  ResumoDaPartida,
} from './modelos';

/** Cliente da API do painel (/api/painel), servida pelo proxy do Nginx. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/painel';

  ranking(ano: number): Observable<LinhaDoRanking[]> {
    return this.http.get<LinhaDoRanking[]>(`${this.base}/ranking`, { params: { ano } });
  }

  presencas(ano: number): Observable<PresencasDoJogador[]> {
    return this.http.get<PresencasDoJogador[]>(`${this.base}/presencas`, { params: { ano } });
  }

  partidas(): Observable<ResumoDaPartida[]> {
    return this.http.get<ResumoDaPartida[]>(`${this.base}/partidas`);
  }

  partida(id: string): Observable<DetalheDaPartida> {
    return this.http.get<DetalheDaPartida>(`${this.base}/partidas/${id}`);
  }

  finalizar(
    id: string,
    placarAzul: number,
    placarAmarelo: number,
    eventos: EventoDaPartida[],
  ): Observable<Partida> {
    return this.http.post<Partida>(`${this.base}/partidas/${id}/finalizar`, {
      placarAzul,
      placarAmarelo,
      eventos,
    });
  }

  jogadores(): Observable<Jogador[]> {
    return this.http.get<Jogador[]>(`${this.base}/jogadores`);
  }

  criarJogador(jogador: JogadorRequest): Observable<Jogador> {
    return this.http.post<Jogador>(`${this.base}/jogadores`, jogador);
  }

  atualizarJogador(id: string, jogador: JogadorRequest): Observable<Jogador> {
    return this.http.put<Jogador>(`${this.base}/jogadores/${id}`, jogador);
  }

  definirAtivo(id: string, ativo: boolean): Observable<Jogador> {
    return this.http.post<Jogador>(`${this.base}/jogadores/${id}/${ativo ? 'ativar' : 'desativar'}`, {});
  }

  desvincularLid(id: string, lid: string): Observable<Jogador> {
    return this.http.delete<Jogador>(`${this.base}/jogadores/${id}/lids/${lid}`);
  }
}

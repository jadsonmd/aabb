import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Api } from '../../core/api';
import { Jogador } from '../../core/modelos';
import { JogadoresPage } from './jogadores';

const jogador = (apelido: string, ativo: boolean): Jogador => ({
  id: apelido,
  nome: apelido,
  apelido,
  whatsapp: '5548999990000',
  lids: [],
  posicoes: ['L'],
  skillBase: 5,
  skill: 5,
  admin: false,
  ativo,
});

describe('JogadoresPage', () => {
  it('lista os membros com a ação de ativar ou desativar', async () => {
    const api = {
      jogadores: () => of([jogador('Marcos', true), jogador('João', false)]),
      presencas: () => of([{ jogadorId: 'Marcos', apelido: 'Marcos', noAno: 7, total: 7, datas: [] }]),
    };
    await TestBed.configureTestingModule({
      imports: [JogadoresPage],
      providers: [{ provide: Api, useValue: api }],
    }).compileComponents();

    const fixture = TestBed.createComponent(JogadoresPage);
    await fixture.whenStable();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Marcos');
    expect(texto).toContain('Desativar');
    expect(texto).toContain('Ativar');
    expect(texto).not.toContain('Aguardando aprovação');
    expect(texto).toContain('7 presenças');
  });
});

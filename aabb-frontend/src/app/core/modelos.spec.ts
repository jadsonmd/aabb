import { formatarData, Partida, sorteioVigente } from './modelos';

describe('modelos', () => {
  it('formata a data ISO com dia da semana', () => {
    expect(formatarData('2026-11-09')).toMatch(/09\/11\/2026$/);
  });

  it('encontra o sorteio vigente pela versão', () => {
    const partida = { sorteioAtual: 2, sorteios: [{ versao: 1 }, { versao: 2 }] } as unknown as Partida;
    expect(sorteioVigente(partida)?.versao).toBe(2);
  });
});

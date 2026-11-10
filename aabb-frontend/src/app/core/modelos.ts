export type Posicao = 'G' | 'Z' | 'L' | 'M' | 'A';
export type StatusPartida = 'ABERTA' | 'SORTEADA' | 'FINALIZADA' | 'CANCELADA';
export type Resultado = 'AZUL' | 'AMARELO' | 'EMPATE';
export type TipoConfirmado = 'MEMBRO' | 'CONVIDADO';
export type TipoEvento = 'ASSISTENCIA' | 'GOL_CONTRA';

export interface Jogador {
  id: string;
  nome: string;
  apelido: string;
  whatsapp: string;
  lids: string[];
  posicoes: Posicao[];
  skillBase: number;
  skill: number;
  admin: boolean;
  ativo: boolean;
}

export interface JogadorRequest {
  nome: string;
  apelido: string;
  whatsapp: string;
  posicoes: Posicao[];
  skillBase: number;
  admin: boolean;
  /** Skill atual definida pelo admin (ausente = não altera). */
  skill?: number | null;
  /** Total de presenças no ano atual definido pelo admin (ausente = não altera). */
  presencasNoAno?: number | null;
}

export interface LinhaDoRanking {
  jogadorId: string;
  nome: string;
  apelido: string;
  skill: number;
  gols: number;
  canetas: number;
  chapeus: number;
  titulos: number;
  presencas: number;
}

export interface ResumoDaPartida {
  id: string;
  data: string;
  status: StatusPartida;
  confirmados: number;
  resultado: Resultado | null;
  placarAzul: number | null;
  placarAmarelo: number | null;
}

export interface Escalado {
  tipo: TipoConfirmado;
  jogadorId: string | null;
  nome: string;
  goleiro: boolean;
}

export interface Time {
  titulares: Escalado[];
  reservas: Escalado[];
  somaSkillTitulares: number;
}

export interface Sorteio {
  versao: number;
  azul: Time;
  amarelo: Time;
}

export interface EventoDaPartida {
  tipo: TipoEvento;
  jogadorId: string | null;
  nome: string;
}

export interface Partida {
  id: string;
  data: string;
  status: StatusPartida;
  sorteios: Sorteio[];
  sorteioAtual: number;
  resultado: Resultado | null;
  placarAzul: number | null;
  placarAmarelo: number | null;
  eventos: EventoDaPartida[];
}

export interface Gol {
  autor: { jogadorId: string; nome: string };
  quantidade: number;
}

export interface DetalheDaPartida {
  partida: Partida;
  gols: Gol[];
  presentes: string[];
}

export interface PresencasDoJogador {
  jogadorId: string;
  apelido: string;
  noAno: number;
  total: number;
  datas: string[];
}

export const POSICOES: { valor: Posicao; nome: string }[] = [
  { valor: 'G', nome: 'Goleiro' },
  { valor: 'Z', nome: 'Zagueiro' },
  { valor: 'L', nome: 'Lateral' },
  { valor: 'M', nome: 'Meia' },
  { valor: 'A', nome: 'Atacante' },
];

export function sorteioVigente(partida: Partida): Sorteio | undefined {
  return partida.sorteios.find((s) => s.versao === partida.sorteioAtual);
}

/** "2026-11-09" → "seg, 09/11/2026" */
export function formatarData(iso: string): string {
  const [ano, mes, dia] = iso.split('-').map(Number);
  const data = new Date(ano, mes - 1, dia);
  const semana = data.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '');
  return `${semana}, ${String(dia).padStart(2, '0')}/${String(mes).padStart(2, '0')}/${ano}`;
}

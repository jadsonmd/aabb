import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';

import { App } from './app';
import { Autenticacao } from './core/autenticacao';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: OAuthService, useValue: {} },
        { provide: Autenticacao, useValue: { logado: () => true, nomeDoUsuario: () => 'Jadson', sair: () => {} } },
      ],
    }).compileComponents();
  });

  it('mostra o menu do painel para quem está logado', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('AABB');
    expect(html.querySelectorAll('nav a').length).toBe(4);
  });
});

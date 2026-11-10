import { inject, Injectable } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';

/** Keycloak (realm aabb) servido pelo mesmo Nginx do painel, em /auth. */
export function configuracaoKeycloak(origem: string): AuthConfig {
  return {
    issuer: `${origem}/auth/realms/aabb`,
    clientId: 'aabb-painel',
    responseType: 'code',
    redirectUri: `${origem}/`,
    postLogoutRedirectUri: `${origem}/`,
    scope: 'openid profile',
    requireHttps: false,
    showDebugInformation: false,
  };
}

@Injectable({ providedIn: 'root' })
export class Autenticacao {
  private readonly oauth = inject(OAuthService);

  /** Carrega a configuração do Keycloak, processa o retorno do login e agenda a renovação do token. */
  async iniciar(): Promise<void> {
    this.oauth.configure(configuracaoKeycloak(window.location.origin));
    this.oauth.setupAutomaticSilentRefresh();
    await this.oauth.loadDiscoveryDocumentAndTryLogin();
  }

  logado(): boolean {
    return this.oauth.hasValidAccessToken();
  }

  entrar(): void {
    this.oauth.initCodeFlow();
  }

  sair(): void {
    this.oauth.logOut();
  }

  nomeDoUsuario(): string {
    const claims = this.oauth.getIdentityClaims() as { given_name?: string; preferred_username?: string } | null;
    return claims?.given_name ?? claims?.preferred_username ?? '';
  }
}

/** Só entra no painel quem está logado no Keycloak; senão, inicia o login. */
export const exigeLogin: CanActivateFn = () => {
  const autenticacao = inject(Autenticacao);
  if (autenticacao.logado()) {
    return true;
  }
  autenticacao.entrar();
  return false;
};

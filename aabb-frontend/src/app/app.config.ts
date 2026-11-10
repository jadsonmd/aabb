import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';

import { routes } from './app.routes';
import { Autenticacao } from './core/autenticacao';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptorsFromDi()),
    // Envia o access token do Keycloak em toda chamada para a API do backend.
    provideOAuthClient({ resourceServer: { allowedUrls: ['/api'], sendAccessToken: true } }),
    provideAppInitializer(() => inject(Autenticacao).iniciar()),
  ],
};

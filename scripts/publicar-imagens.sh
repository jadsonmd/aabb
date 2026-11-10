#!/bin/sh
# Compila e publica no Docker Hub as imagens do sistema para amd64 e arm64 (Raspberry Pi).
# Pré-requisito: `docker login` com a conta do Docker Hub.
#
# Uso: scripts/publicar-imagens.sh [usuario-docker-hub]
#   Publica <usuario>/aabb-{backend,frontend,keycloak,postgres} com as tags "latest" e o commit atual.
set -eu

USUARIO="${1:-jadsonmd}"
PLATAFORMAS="linux/amd64,linux/arm64"
VERSAO="$(git rev-parse --short HEAD)"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"

if [ -n "$(git -C "$RAIZ" status --porcelain)" ]; then
  echo "Há alterações não commitadas; commite antes de publicar para a tag $VERSAO ser fiel ao código." >&2
  exit 1
fi

# Builds multiplataforma exigem um builder com o driver docker-container.
docker buildx inspect aabb >/dev/null 2>&1 || docker buildx create --name aabb --driver docker-container >/dev/null

publicar() {
  nome="$1"
  contexto="$2"
  echo "==> $USUARIO/$nome ($PLATAFORMAS) tags: latest, $VERSAO"
  docker buildx build --builder aabb --platform "$PLATAFORMAS" \
    -t "$USUARIO/$nome:latest" -t "$USUARIO/$nome:$VERSAO" \
    --push "$RAIZ/$contexto"
}

publicar aabb-backend aabb-backend
publicar aabb-frontend aabb-frontend
publicar aabb-keycloak infra/keycloak
publicar aabb-postgres infra/postgres

echo "Imagens publicadas. No servidor: docker compose -p aabb pull && docker compose -p aabb up -d"

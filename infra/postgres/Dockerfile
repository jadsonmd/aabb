FROM postgres:17-alpine
# Cria o banco do Keycloak na primeira subida (o banco "evolution" é criado pelo próprio container).
COPY 01-keycloak.sh /docker-entrypoint-initdb.d/

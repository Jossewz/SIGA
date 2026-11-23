#!/usr/bin/env bash
# ==============================================================================
# Script de Despliegue Automatizado para SIGA en Azure VM (Ubuntu 24.04 ARM64)
# ==============================================================================
set -e

echo "=== [1/4] Verificando Docker en el servidor ==="
if ! command -v docker &> /dev/null; then
    echo "Instalando Docker..."
    curl -fsSL https://get.docker.com | sudo sh
    sudo usermod -aG docker "$USER"
fi

echo "=== [2/4] Configurando archivo de entorno (.env) ==="
if [ ! -f .env ]; then
    echo "Creando .env a partir de .env.prod.example..."
    cp .env.prod.example .env
fi

echo "=== [3/4] Construyendo imágenes e iniciando servicios ==="
sudo docker compose -f docker-compose.prod.yml up -d --build

echo "=== [4/4] Estado de los servicios ==="
sudo docker compose -f docker-compose.prod.yml ps

echo "=============================================================================="
echo "SIGA desplegado con éxito en producción."
echo "Para ver los logs del backend:"
echo "  sudo docker compose -f docker-compose.prod.yml logs -f app"
echo "=============================================================================="

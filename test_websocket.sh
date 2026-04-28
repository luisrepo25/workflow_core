#!/bin/bash
# Script de prueba para WebSocket del servidor
# Requiere: wscat (npm install -g wscat)

# Conexión a WebSocket
echo "Conectando a WebSocket en ws://localhost:8080/ws/workflow"
echo "Presiona Ctrl+C para salir"
echo ""

wscat --connect ws://localhost:8080/ws/workflow

# Una vez conectado, puedes enviar mensajes STOMP como JSON, por ejemplo:
# {"command":"CONNECT","headers":{"login":"","passcode":""},"body":""}
# Luego suscribirse y enviar mensajes a /app/workflow/{id}/connect, etc.

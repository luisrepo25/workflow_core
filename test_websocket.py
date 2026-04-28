#!/usr/bin/env python3
"""
Script de prueba para WebSocket usando websocket-client
Requisitos: pip install websocket-client
"""

import websocket
import json
import time
from threading import Thread

class WorkflowWebSocketTest:
    def __init__(self, url="ws://localhost:8080/ws/workflow"):
        self.url = url
        self.ws = None
        self.connected = False
        self.workflow_id = "test-workflow-123"
        self.user_id = "test-user-456"
        self.user_name = "Test User"

    def on_message(self, ws, message):
        """Callback cuando se recibe un mensaje"""
        try:
            data = json.loads(message)
            print(f"\n📨 Mensaje recibido:")
            print(json.dumps(data, indent=2, ensure_ascii=False))
        except json.JSONDecodeError:
            print(f"📨 Mensaje (raw): {message}")

    def on_error(self, ws, error):
        """Callback en caso de error"""
        print(f"❌ Error: {error}")

    def on_close(self, ws, close_status_code, close_msg):
        """Callback cuando se cierra la conexión"""
        print("🔌 Conexión cerrada")
        self.connected = False

    def on_open(self, ws):
        """Callback cuando se abre la conexión"""
        print("✅ Conectado a WebSocket")
        self.connected = True

    def connect(self):
        """Conectar al servidor WebSocket"""
        print(f"🔗 Conectando a {self.url}...")
        
        websocket.enableTrace(False)
        self.ws = websocket.WebSocketApp(
            self.url,
            on_open=self.on_open,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close
        )
        
        # Conectar en thread separado
        wst = Thread(target=self.ws.run_forever)
        wst.daemon = True
        wst.start()
        
        # Esperar a que se conecte
        time.sleep(2)

    def send_stomp_message(self, destination, body):
        """Enviar mensaje STOMP"""
        if not self.connected:
            print("⚠️ No conectado al servidor")
            return
        
        # STOMP message format: SEND\nde destination line...
        message = f"SEND\ndestination:{destination}\n\n{json.dumps(body)}\x00"
        self.ws.send(message)
        print(f"📤 Enviado a {destination}")

    def send_command_message(self, destination, body):
        """Enviar mensaje a través de WebSocket"""
        if not self.connected:
            print("⚠️ No conectado al servidor")
            return
        
        self.ws.send(json.dumps(body))
        print(f"📤 Enviado: {json.dumps(body)}")

    def connect_to_workflow(self):
        """Conectarse a un workflow específico"""
        body = {
            "userId": self.user_id,
            "userName": self.user_name,
            "action": "connect"
        }
        destination = f"/app/workflow/{self.workflow_id}/connect"
        self.send_command_message(destination, body)
        time.sleep(1)

    def add_node(self):
        """Agregar un nodo"""
        node = {
            "id": "node-123",
            "tipo": "actividad",
            "nombre": "Actividad de Prueba",
            "posicion": {"x": 100, "y": 100},
            "responsableTipo": "funcionario"
        }
        body = {
            "action": "node_added",
            "userId": self.user_id,
            "userName": self.user_name,
            "nodeId": "node-123",
            "data": node,
            "timestamp": int(time.time() * 1000)
        }
        destination = f"/app/workflow/{self.workflow_id}/node/add"
        self.send_command_message(destination, body)
        time.sleep(1)

    def update_node(self):
        """Actualizar un nodo"""
        node = {
            "id": "node-123",
            "tipo": "actividad",
            "nombre": "Actividad de Prueba Actualizada",
            "posicion": {"x": 150, "y": 150},
            "responsableTipo": "funcionario"
        }
        body = {
            "action": "node_updated",
            "userId": self.user_id,
            "userName": self.user_name,
            "nodeId": "node-123",
            "data": node,
            "timestamp": int(time.time() * 1000)
        }
        destination = f"/app/workflow/{self.workflow_id}/node/update"
        self.send_command_message(destination, body)
        time.sleep(1)

    def add_edge(self):
        """Agregar una arista"""
        edge = {
            "id": "edge-456",
            "from": "node-inicio",
            "to": "node-123",
            "tipo": "secuencial"
        }
        body = {
            "action": "edge_added",
            "userId": self.user_id,
            "userName": self.user_name,
            "edgeId": "edge-456",
            "data": edge,
            "timestamp": int(time.time() * 1000)
        }
        destination = f"/app/workflow/{self.workflow_id}/edge/add"
        self.send_command_message(destination, body)
        time.sleep(1)

    def update_selection(self):
        """Actualizar selección de elemento"""
        body = {
            "userId": self.user_id,
            "nodeId": "node-123",
            "action": "selection_updated",
            "timestamp": int(time.time() * 1000)
        }
        destination = f"/app/workflow/{self.workflow_id}/selection"
        self.send_command_message(destination, body)
        time.sleep(1)

    def get_editing_state(self):
        """Obtener estado de edición"""
        body = {
            "userId": self.user_id,
            "action": "get_state"
        }
        destination = f"/app/workflow/{self.workflow_id}/state"
        self.send_command_message(destination, body)
        time.sleep(1)

    def get_history(self):
        """Obtener historial de cambios"""
        body = {
            "userId": self.user_id,
            "action": "get_history"
        }
        destination = f"/app/workflow/{self.workflow_id}/history"
        self.send_command_message(destination, body)
        time.sleep(1)

    def disconnect_from_workflow(self):
        """Desconectarse del workflow"""
        body = {
            "userId": self.user_id,
            "userName": self.user_name,
            "action": "disconnect"
        }
        destination = f"/app/workflow/{self.workflow_id}/disconnect"
        self.send_command_message(destination, body)
        time.sleep(1)

    def run_test_sequence(self):
        """Ejecutar secuencia de pruebas"""
        print("\n" + "="*60)
        print("🧪 INICIANDO PRUEBAS DE WEBSOCKET")
        print("="*60 + "\n")
        
        # Conectar a WebSocket
        self.connect()
        
        print("\n📌 Conectando a workflow...")
        self.connect_to_workflow()
        
        print("\n📌 Agregando nodo...")
        self.add_node()
        
        print("\n📌 Actualizando nodo...")
        self.update_node()
        
        print("\n📌 Agregando arista...")
        self.add_edge()
        
        print("\n📌 Actualizando selección...")
        self.update_selection()
        
        print("\n📌 Obteniendo estado de edición...")
        self.get_editing_state()
        
        print("\n📌 Obteniendo historial...")
        self.get_history()
        
        print("\n📌 Desconectando...")
        self.disconnect_from_workflow()
        
        print("\n✅ Pruebas completadas")
        
        # Mantener abierto unos segundos más para recibir respuestas
        time.sleep(3)
        
        # Cerrar conexión
        if self.ws:
            self.ws.close()


if __name__ == "__main__":
    print("🚀 Iniciando pruebas de WebSocket para Workflow")
    print("Asegúrate de que el servidor está corriendo en http://localhost:8080")
    print()
    
    test = WorkflowWebSocketTest()
    try:
        test.run_test_sequence()
    except KeyboardInterrupt:
        print("\n\n⛔ Pruebas interrumpidas por usuario")
        if test.ws:
            test.ws.close()
    except Exception as e:
        print(f"\n\n❌ Error durante pruebas: {e}")
        if test.ws:
            test.ws.close()

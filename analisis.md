# 📊 Análisis Técnico — Nuevas Funcionalidades Workflow System

> Plazo: **3 días** | Meta: funcional, no pulido

---

## 🗺️ Estado Actual de los Proyectos

| Proyecto | Stack | Estado |
|---|---|---|
| `workflow_core` | Spring Boot + MongoDB | ✅ Maduro — auth, websockets, motor BPM, archivos básicos |
| `workflow_ia` | FastAPI + Groq + PyTorch | ⚠️ Semi — tiene LLM via Groq, local IA en esqueleto (SmolLM-135M), sin DL real |
| `workflow_notification` | NestJS + Firebase | ✅ Funcional — push notifications |
| `workflow_view` | Angular | ✅ Funcional — diseñadores y funcionarios |
| `workflow_movil` | Flutter + Riverpod | ✅ Base sólida — Firebase, notificaciones push, sin voz |

### Observaciones críticas del código actual:
- `StoredFile` solo guarda `storagePath` y `url` — **NO hay S3 implementado**, es solo metadata en MongoDB
- `ProcessInstance` tiene historial de eventos pero **sin auditoría de archivos** (quién miró, quién editó)
- `workflow_ia` usa **Groq (LLaMA 3.3 70B)** como motor real — la IA local (SmolLM-135M) es un fallback que probablemente falla siempre en producción
- No hay integración de voz en ningún proyecto actualmente

---

## ✅ HACER (viable en 3 días) vs ❌ POSPONER

### ✅ PRIORIDAD ALTA — Hacer en 3 días

| Feature | Proyecto | Esfuerzo |
|---|---|---|
| **S3 ordenado por cliente/trámite** | workflow_core | Medio |
| **Auditoría de archivos** (quién vio, quién editó) | workflow_core | Bajo |
| **Agente IA para identificar política** via texto/prompt | workflow_ia | Medio |
| **Reportes dinámicos** por texto/prompt → JSON/Excel | workflow_ia + workflow_core | Medio |
| **Voz en móvil** para iniciar trámite | workflow_movil | Medio |

### ⚠️ PRIORIDAD MEDIA — Intentar si hay tiempo

| Feature | Proyecto | Esfuerzo |
|---|---|---|
| Motor de enrutamiento inteligente (predicción básica) | workflow_ia | Alto |
| Edición colaborativa de documentos (OnlyOffice o simple) | workflow_core | Alto |
| Privilegios/atributos a nivel de documento | workflow_core | Medio |

### ❌ POSPONER — No viable en 3 días

| Feature | Por qué no |
|---|---|
| Deep Learning real (entrenamiento propio) | Requiere datos, GPU, semanas de entrenamiento |
| Motor de análisis de riesgo con detección de anomalías | Requiere histórico de datos de trámites |
| Edición colaborativa tipo Google Docs | Requiere OT/CRDT, es complejo de integrar |
| KPIs con ML predictivo real | Necesita datos históricos suficientes |

---

## 🛠️ Herramientas y Dependencias por Módulo

---

### 1. 📦 AWS S3 — Almacenamiento ordenado (workflow_core)

**Estructura de carpetas en S3:**
```
s3://bucket/
  clientes/{clienteId}/
    tramites/{processInstanceId}/
      {nodoId}/
        {timestamp}_{nombreOriginal}
```

#### Dependencia Maven a agregar:
```xml
<!-- pom.xml de workflow_core -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.0</version>
</dependency>
```

#### Variables de entorno necesarias:
```properties
AWS_ACCESS_KEY_ID=xxx
AWS_SECRET_ACCESS_KEY=xxx
AWS_REGION=us-east-1
AWS_S3_BUCKET=workflow-tramites
```

#### Cambios en StoredFile.java:
```java
// Campos a agregar:
private String s3Key;        // "clientes/{cid}/tramites/{pid}/{nid}/file.pdf"
private String s3Bucket;     // nombre del bucket
private ObjectId clienteId;  // para organización en S3
```

> **Nota:** Ya tienes `storagePath` y `url` en `StoredFile` — solo hay que migrar a S3 real.

---

### 2. 🔍 Auditoría de Archivos — Quién vio, quién editó (workflow_core)

#### Nueva entidad `FileAuditLog.java`:
```java
@Document(collection = "file_audit_logs")
public class FileAuditLog {
    @Id private ObjectId id;
    private ObjectId fileId;
    private ObjectId userId;
    private ObjectId processInstanceId;
    private String action;  // "VIEW", "EDIT", "DOWNLOAD", "DELETE"
    private Instant timestamp;
    private String ipAddress;
    private String metadata; // JSON extra si se necesita
}
```

**Dependencias:** Ninguna nueva, usa el stack MongoDB existente.

---

### 3. 🤖 Agente IA — Identificar política de negocio (workflow_ia)

**Estrategia:** Usar Groq (que ya funciona) con un prompt especializado.  
**NO necesitas deep learning propio** — LLaMA 3.3 70B via Groq ES deep learning.

#### Nuevo endpoint en workflow_ia:
```python
# POST /agente/identificar-politica
class IdentificarPoliticaRequest(BaseModel):
    descripcion_cliente: str  # texto libre del funcionario
    politicas_disponibles: list[dict]  # lista de workflows activos

# La IA devuelve:
# { "politica_id": "xxx", "politica_nombre": "Licencia de construcción", 
#   "confianza": 0.92, "requisitos": [...], "opcionales": [...] }
```

#### Sistema multi-turno (chatbot de requisitos):
```python
# POST /agente/chat
class ChatRequest(BaseModel):
    session_id: str
    mensaje: str
    politica_id: str
    historial: list[dict]  # mensajes previos
```

**Sin dependencias nuevas** — usa Groq que ya está instalado.

---

### 4. 🎙️ Voz en App Móvil (workflow_movil)

#### Dependencia a agregar en pubspec.yaml:
```yaml
# Reconocimiento de voz
speech_to_text: ^6.6.0

# Opcionalmente para síntesis de voz (TTS)
flutter_tts: ^4.0.2
```

**Implementación:** El usuario habla → `speech_to_text` transcribe → se envía el texto al endpoint `/agente/identificar-politica` de workflow_ia → chatbot de requisitos.

**Flujo:**
```
[Usuario habla] → speech_to_text → texto → POST /agente/identificar-politica
→ IA identifica política → chatbot de requisitos → funcionario confirma → inicia trámite
```

> **NO se inicia el trámite desde el móvil** (regla del negocio). El móvil solo registra la solicitud que un funcionario luego procesa.

---

### 5. 📊 Reportes Dinámicos (workflow_ia + workflow_core)

**Estrategia:** El jefe dice en texto/voz qué quiere → IA traduce a query MongoDB → resultado en JSON/Excel.

#### Dependencia Python para Excel:
```
openpyxl>=3.1,<4.0
```

#### Dependencia Java para Excel (workflow_core):
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

#### Nuevo endpoint:
```python
# POST /reportes/generar
class ReporteRequest(BaseModel):
    descripcion: str  # "quiero los trámites del mes pasado ordenados por estado"
    formato: str      # "json" | "excel" | "word"
```

**Flujo:**
```
texto/voz → IA genera query → workflow_core ejecuta query MongoDB → 
→ genera Excel/JSON → URL de descarga
```

---

### 6. 🧠 Motor de Enrutamiento Inteligente (workflow_ia) — SIMPLIFICADO

> ⚠️ Para 3 días: versión simplificada sin DL real

**Usar heurísticas + Groq para predicción:**
- Calcular tiempo promedio histórico por nodo/departamento
- Detectar trámites "atascados" (superan SLA × 2)
- Sugerir reasignación basada en carga del departamento

#### Dependencias Python:
```
pandas>=2.0,<3.0       # análisis de datos tabulares
scipy>=1.11,<2.0       # estadísticas básicas
```

**Nota:** scikit-learn ya está en requirements.txt — úsalo para clustering de trámites por riesgo.

---

### 7. 🔒 Privilegios a nivel de Documento (workflow_core)

#### Campos a agregar en StoredFile.java:
```java
private List<String> permisosLectura;  // lista de userId o roles
private List<String> permisosEdicion;
private boolean esPublico;
private String clasificacion; // "confidencial" | "interno" | "publico"
```

**Sin dependencias nuevas.**

---

## 📋 Resumen de Dependencias por Proyecto

### workflow_core (pom.xml) — Agregar:
```xml
<!-- AWS S3 -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.0</version>
</dependency>
<!-- Excel Reports -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

### workflow_ia (requirements.txt) — Agregar:
```
openpyxl>=3.1,<4.0
pandas>=2.0,<3.0
scipy>=1.11,<2.0
```
> ⚠️ `torch`, `transformers`, `sentence-transformers` ya están — NO hace falta agregar más para DL

### workflow_movil (pubspec.yaml) — Agregar:
```yaml
speech_to_text: ^6.6.0
flutter_tts: ^4.0.2
```

### workflow_notification — Sin cambios necesarios

### workflow_view — Sin cambios necesarios (consume APIs nuevas automáticamente)

---

## 🗓️ Plan de 3 Días

### Día 1 — Infraestructura Core
- [ ] Integrar AWS S3 en `workflow_core` (S3Service, modificar StoredFile, crear bucket con estructura)
- [ ] Crear `FileAuditLog` entity + endpoint de auditoría
- [ ] Nuevo endpoint en `workflow_ia`: `/agente/identificar-politica`

### Día 2 — IA y Reportes
- [ ] Chatbot de requisitos con historial de sesión en `workflow_ia`
- [ ] Endpoint `/reportes/generar` con salida Excel
- [ ] Motor de enrutamiento simplificado (alertas de SLA, prioridades)

### Día 3 — Móvil y Polish
- [ ] Integrar `speech_to_text` en `workflow_movil`
- [ ] Pantalla de chatbot en la app móvil
- [ ] Conectar todo y pruebas end-to-end

---

## ⚡ Decisiones Arquitectónicas Clave

1. **Groq = tu "Deep Learning"** para el MVP. LLaMA 3.3 70B es un modelo de 70B parámetros — IS deep learning. No necesitas entrenar nada propio en 3 días.

2. **S3 es la prioridad #1** — Tienes la entidad `StoredFile` lista, solo falta el SDK de AWS y la lógica de upload.

3. **Para voz en móvil**, `speech_to_text` de Flutter es la más madura y funciona sin APIs externas en Android/iOS.

4. **Para reportes Excel**, `openpyxl` en Python es la opción más simple y rápida de implementar.

5. **La auditoría** es la feature más fácil y más impactante — solo es una nueva colección MongoDB + interceptor en los endpoints de archivos.

---

## 🚨 Riesgos

| Riesgo | Mitigación |
|---|---|
| S3 requiere cuenta AWS configurada | Tener credenciales listas antes de empezar |
| `speech_to_text` puede ser lento en Android emulador | Probar en dispositivo físico |
| Groq tiene rate limits | Implementar cache de respuestas comunes |
| Motor de enrutamiento sin datos históricos | Usar datos de prueba / mocks para demo |

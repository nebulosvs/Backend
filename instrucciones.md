# Instrucciones de ejecución y pruebas – Banco XYZ

Este documento describe el procedimiento para ejecutar y validar los principales componentes de la solución desarrollada para Banco XYZ.

La solución puede ejecutarse localmente mediante Docker Compose. Las pruebas permiten verificar Spring Batch, OAuth 2.0, API Gateway, microservicios, Backend for Frontend (BFF), Resilience4j y la comunicación asíncrona mediante JMS.

---

## 1. Requisitos previos

Para ejecutar el proyecto localmente se requiere:

- Java 17.
- Maven o Maven Wrapper incluido en el proyecto.
- Docker Desktop.
- Docker Compose.
- Git.
- Postman o una herramienta equivalente para realizar solicitudes HTTP/HTTPS.

Verificar Java:

```powershell
java -version
```

Verificar Docker:

```powershell
docker --version
```

Verificar Docker Compose:

```powershell
docker compose version
```

---

## 2. Obtener el proyecto

Clonar el repositorio:

```bash
git clone <URL_REPOSITORIO>
```

Ingresar al directorio:

```bash
cd <NOMBRE_REPOSITORIO>
```

Si el proyecto ya se encuentra descargado, basta con abrir una terminal desde su directorio raíz.

---

## 3. Compilar el proyecto

Desde la raíz del proyecto ejecutar:

```powershell
.\mvnw.cmd clean package -DskipTests
```

El módulo Spring Batch `xyz` también puede compilarse de forma independiente:

```powershell
cd .\xyz
..\mvnw.cmd clean package -DskipTests
cd ..
```

Una compilación correcta debe finalizar con:

```text
BUILD SUCCESS
```

---

## 4. Construir las imágenes Docker

Con Docker Desktop iniciado, ejecutar desde la raíz:

```powershell
docker compose build
```

Este comando construye las imágenes necesarias para ejecutar la solución.

---

## 5. Iniciar la solución

Ejecutar:

```powershell
docker compose up -d
```

Docker Compose iniciará los componentes respetando sus dependencias.

El flujo general de inicialización es:

```text
H2 Server
    |
    v
Spring Batch
    |
    v
Config Server / Eureka / Auth Server
    |
    v
Microservicios
    |
    v
API Gateway
    |
    v
BFF
```

---

## 6. Comprobar los contenedores

Ejecutar:

```powershell
docker compose ps -a
```

Los servicios principales deben encontrarse en estado:

```text
Up
```

Los servicios que poseen healthcheck deben aparecer como:

```text
healthy
```

El contenedor correspondiente a Spring Batch puede aparecer como:

```text
Exited (0)
```

Esto es esperado.

Spring Batch ejecuta sus Jobs, procesa los datos y finaliza, por lo que no necesita permanecer activo como un microservicio.

---

## 7. Verificar Spring Batch

Para revisar la ejecución del proceso Batch:

```powershell
docker compose logs batch
```

La ejecución debe finalizar sin errores críticos.

Los procesos Batch implementados corresponden a:

1. Procesamiento de transacciones diarias.
2. Cálculo de intereses.
3. Generación de estados de cuenta.

Los datos generados por estos procesos son posteriormente utilizados por los microservicios.

---

# Pruebas de seguridad y microservicios

## 8. Obtener token OAuth 2.0

El Authorization Server se encuentra disponible localmente en:

```http
POST http://localhost:9000/oauth2/token
```

En Postman seleccionar:

```text
Authorization
Type: Basic Auth
```

Utilizar:

```text
Username: cliente-web
Password: web-secret
```

En `Body` seleccionar:

```text
x-www-form-urlencoded
```

Agregar:

```text
grant_type    client_credentials
scope         cuentas.read transacciones.read intereses.read
```

Enviar la solicitud.

La respuesta debe contener un:

```json
{
  "access_token": "...",
  "scope": "cuentas.read transacciones.read intereses.read",
  "token_type": "Bearer"
}
```

Copiar el valor de:

```text
access_token
```

Este token se utilizará para probar los endpoints protegidos.

---

## 9. Probar API Gateway

Las solicitudes externas hacia los microservicios se realizan mediante:

```text
http://localhost:8080
```

En Postman seleccionar:

```text
Authorization
Type: Bearer Token
```

y utilizar el token obtenido anteriormente.

---

## 10. Probar ms-cuentas

Ejecutar:

```http
GET http://localhost:8080/cuentas/estados
```

Resultado esperado:

```text
200 OK
```

También pueden probarse:

```http
GET http://localhost:8080/cuentas
GET http://localhost:8080/cuentas/1
GET http://localhost:8080/cuentas/1/saldo
GET http://localhost:8080/cuentas/1/estado-anual
```

Estas operaciones requieren:

```text
cuentas.read
```

---

## 11. Probar ms-transacciones

Ejecutar:

```http
GET http://localhost:8080/transacciones/diarias
```

Resultado esperado:

```text
200 OK
```

También puede limitarse la cantidad de resultados:

```http
GET http://localhost:8080/transacciones/diarias?limit=5
```

La operación requiere:

```text
transacciones.read
```

---

## 12. Probar ms-intereses

Ejecutar:

```http
GET http://localhost:8080/intereses/cuentas
```

Resultado esperado:

```text
200 OK
```

También puede consultarse una cuenta específica:

```http
GET http://localhost:8080/intereses/cuentas/1
```

La operación requiere:

```text
intereses.read
```

---

# Pruebas de autorización

## 13. Comprobar respuesta 401 Unauthorized

Realizar una solicitud sin enviar token:

```http
GET http://localhost:8080/cuentas/estados
```

No agregar:

```http
Authorization: Bearer <TOKEN>
```

Resultado esperado:

```text
401 Unauthorized
```

Esto demuestra que los recursos se encuentran protegidos.

---

## 14. Comprobar respuesta 403 Forbidden

Obtener un token utilizando:

```text
Username: cliente-web
Password: web-secret
```

El cliente Web no posee el scope:

```text
retiros.write
```

Intentar ejecutar:

```http
POST http://localhost:8080/transacciones/retiros
```

utilizando el token de `cliente-web`.

Resultado esperado:

```text
403 Forbidden
```

Esto demuestra que un cliente autenticado no puede ejecutar operaciones para las cuales no posee autorización.

---

# Pruebas del Backend for Frontend

## 15. Consideraciones para HTTPS

El BFF utiliza HTTPS mediante un certificado configurado en la aplicación.

Dirección local:

```text
https://localhost:8443
```

El certificado utilizado durante el desarrollo es autofirmado.

Por este motivo, para realizar las pruebas con Postman puede ser necesario desactivar temporalmente:

```text
Settings
→ General
→ SSL certificate verification
→ OFF
```

Esta configuración debe utilizarse únicamente en el entorno de desarrollo/pruebas con el certificado autofirmado.

---

## 16. Probar BFF Web

El canal Web utiliza:

```text
Basic Auth

Username: web
Password: web123
```

### Dashboard

Ejecutar:

```http
GET https://localhost:8443/bff/web/dashboard
```

Resultado esperado:

```text
200 OK
```

El BFF obtiene internamente un token OAuth2 y consulta los servicios backend mediante el API Gateway.

### Transacciones

```http
GET https://localhost:8443/bff/web/transacciones
```

### Intereses

```http
GET https://localhost:8443/bff/web/intereses
```

### Estado anual

```http
GET https://localhost:8443/bff/web/cuentas/1/estado-anual
```

---

## 17. Verificar separación entre canales BFF

Intentar acceder a:

```http
GET https://localhost:8443/bff/web/dashboard
```

utilizando:

```text
Username: mobile
Password: mobile123
```

Resultado esperado:

```text
403 Forbidden
```

Esto demuestra que las credenciales de un canal no permiten acceder a los recursos exclusivos de otro canal.

---

## 18. Probar BFF Mobile

Utilizar:

```text
Basic Auth

Username: mobile
Password: mobile123
```

### Home

```http
GET https://localhost:8443/bff/mobile/home?limit=5
```

Resultado esperado:

```text
200 OK
```

### Listado de cuentas

```http
GET https://localhost:8443/bff/mobile/cuentas
```

### Resumen de cuenta

```http
GET https://localhost:8443/bff/mobile/cuentas/1/resumen
```

El BFF Mobile utiliza internamente:

```text
cliente-mobile
```

para obtener el token OAuth2 requerido para acceder a los servicios backend.

---

# Pruebas ATM

## 19. Probar conectividad del BFF ATM

Utilizar:

```text
Basic Auth

Username: atm
Password: atm123
```

Ejecutar:

```http
GET https://localhost:8443/bff/atm/ping
```

---

## 20. Consultar saldo ATM

Para las pruebas realizadas con los datos de ejemplo se utiliza la cuenta cuyo identificador técnico es:

```text
1
```

El `cuentaId` asociado a esta cuenta es:

```text
142
```

Por lo tanto, el PIN utilizado durante las pruebas es:

```text
0142
```

Ejecutar:

```http
GET https://localhost:8443/bff/atm/saldo/1
```

Authorization:

```text
Basic Auth

Username: atm
Password: atm123
```

Agregar el header:

```http
X-ATM-PIN: 0142
```

Resultado esperado:

```text
200 OK
```

La respuesta debe contener la información del saldo de la cuenta.

> Importante: el valor `1` utilizado en `/saldo/1` corresponde al identificador técnico de la cuenta. El valor `142` corresponde al `cuentaId` de negocio utilizado para derivar el PIN.

---

## 21. Probar PIN incorrecto

Ejecutar nuevamente:

```http
GET https://localhost:8443/bff/atm/saldo/1
```

pero utilizando un PIN incorrecto:

```http
X-ATM-PIN: 9999
```

La operación debe ser rechazada.

Esto permite comprobar que conocer el identificador de una cuenta no es suficiente para acceder a las operaciones ATM.

---

# Prueba de retiro y Saga JMS

## 22. Consultar saldo antes del retiro

Antes de realizar el retiro ejecutar:

```http
GET https://localhost:8443/bff/atm/saldo/1
```

Con:

```text
Basic Auth:
atm / atm123
```

Header:

```http
X-ATM-PIN: 0142
```

Registrar el saldo retornado para compararlo después de la operación.

---

## 23. Ejecutar retiro desde ATM

Ejecutar:

```http
POST https://localhost:8443/bff/atm/retiro
```

Authorization:

```text
Basic Auth

Username: atm
Password: atm123
```

Headers:

```http
Content-Type: application/json
X-ATM-PIN: 0142
```

Body:

```json
{
  "id": 1,
  "monto": 100
}
```

La solicitud debe ser aceptada para procesamiento.

El retiro inicia posteriormente el flujo asíncrono entre los microservicios.

---

## 24. Verificar Saga mediante logs

Ejecutar:

```powershell
docker compose logs ms-transacciones
```

y:

```powershell
docker compose logs ms-cuentas
```

Para filtrar únicamente los mensajes asociados a la Saga, en un entorno Linux puede utilizarse:

```bash
docker compose logs ms-transacciones | grep -Ei "retiro|debitar|confirm|compens|saga|jms"
```

```bash
docker compose logs ms-cuentas | grep -Ei "retiro|debitar|confirm|compens|saga|jms"
```

Debe observarse un flujo equivalente a:

```text
ms-transacciones
    |
    | cuentas.debitar
    v
ms-cuentas
    |
    | débito procesado
    v
cuentas.debitar.respuesta
    |
    v
ms-transacciones
    |
    v
CONFIRMADO
```

Los mensajes correspondientes a una misma operación deben compartir el mismo:

```text
eventId
```

Un ejemplo de ejecución correcta es:

```text
SAGA cola=cuentas.debitar
cuentaId=1
monto=100

SAGA cola=cuentas.debitar
resultado=...;OK

SAGA cola=cuentas.debitar.respuesta
estado=CONFIRMADO
```

---

## 25. Comprobar saldo después del retiro

Ejecutar nuevamente:

```http
GET https://localhost:8443/bff/atm/saldo/1
```

utilizando:

```text
Basic Auth:
atm / atm123
```

y:

```http
X-ATM-PIN: 0142
```

El saldo debe haber disminuido de acuerdo con el monto retirado.

Esto permite comprobar el flujo completo:

```text
ATM
 |
 v
BFF
 |
 v
API Gateway
 |
 v
ms-transacciones
 |
 | JMS
 v
ms-cuentas
 |
 | actualización de saldo
 v
JMS respuesta
 |
 v
ms-transacciones
 |
 v
CONFIRMADO
```

---

# Pruebas de Resilience4j

## 26. Circuit Breaker en funcionamiento normal

Obtener un token OAuth2 con los permisos correspondientes y ejecutar:

```http
GET http://localhost:8080/cuentas/salud-dependencia
```

Resultado esperado:

```json
{
  "estado": "OK",
  "circuito": "CLOSED"
}
```

Esto indica que la dependencia se encuentra disponible.

---

## 27. Probar fallback

Ejecutar:

```http
GET http://localhost:8080/cuentas/salud-dependencia?forzarFallo=true
```

La solicitud simula un fallo en la dependencia.

El resultado debe mostrar el mecanismo de fallback y permitir comprobar la apertura del Circuit Breaker:

```json
{
  "estado": "FALLBACK",
  "circuito": "OPEN"
}
```

---

# Comandos de diagnóstico

## 28. Consultar estado de la infraestructura

```powershell
docker compose ps -a
```

---

## 29. Consultar logs

Todos los servicios:

```powershell
docker compose logs
```

Un servicio específico:

```powershell
docker compose logs ms-cuentas
```

Seguimiento en tiempo real:

```powershell
docker compose logs -f ms-cuentas
```

---

## 30. Reiniciar un servicio

Ejemplo:

```powershell
docker compose restart ms-cuentas
```

---

## 31. Detener la solución

Para detener y eliminar los contenedores:

```powershell
docker compose down
```

Para reconstruir completamente la infraestructura:

```powershell
docker compose down
docker compose build
docker compose up -d
```

---

## 32. Validación final

La ejecución puede considerarse correcta cuando se comprueban los siguientes puntos:

- Spring Batch finaliza correctamente.
- H2 se encuentra disponible.
- Config Server se encuentra operativo.
- Eureka registra los servicios.
- Auth Server permite obtener tokens OAuth2.
- API Gateway permite acceder a los servicios autorizados.
- Los scopes producen respuestas `200`, `401` y `403` según corresponda.
- `ms-cuentas` responde correctamente.
- `ms-intereses` responde correctamente.
- `ms-transacciones` responde correctamente.
- BFF Web funciona con sus credenciales.
- BFF Mobile funciona con sus credenciales.
- BFF ATM valida correctamente el PIN.
- El retiro ATM es procesado de forma asíncrona.
- `ms-transacciones` publica el evento JMS.
- `ms-cuentas` procesa el débito.
- La respuesta de la Saga llega a `ms-transacciones`.
- El retiro finaliza en estado `CONFIRMADO`.
- El saldo de la cuenta refleja el retiro.
- Resilience4j activa el fallback ante un fallo controlado.
- La infraestructura completa puede ejecutarse mediante Docker Compose.

Con estas comprobaciones se valida la integración de los principales componentes de la solución.
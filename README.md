# Backend III – Microservicios y Resiliencia con Spring Cloud

Proyecto desarrollado para la actividad de **Semana 8 de Desarrollo Backend III (PBY2203)**.

La solución implementa una arquitectura de microservicios para un sistema bancario, incorporando seguridad con **OAuth 2.0 y JWT**, descubrimiento de servicios con **Eureka**, configuración centralizada mediante **Spring Cloud Config**, resiliencia con **Resilience4j**, comunicación asíncrona mediante **JMS**, persistencia con **H2** y despliegue mediante **Docker y Docker Compose**.

---

## 1. Objetivo

Implementar una arquitectura distribuida basada en microservicios que permita:

- Proteger los servicios mediante OAuth 2.0 y JWT.
- Aplicar autorización mediante scopes.
- Centralizar la configuración de los microservicios.
- Registrar y descubrir servicios mediante Eureka.
- Implementar patrones de resiliencia con Resilience4j.
- Implementar comunicación asíncrona mediante JMS.
- Aplicar una Saga con compensación para el procesamiento de retiros.
- Dockerizar los componentes de la solución.
- Orquestar la infraestructura mediante Docker Compose.

---

## 2. Arquitectura general

La solución está compuesta por los siguientes servicios:

| Servicio | Puerto | Responsabilidad |
|---|---:|---|
| API Gateway | 8080 | Punto de entrada y enrutamiento hacia los microservicios |
| ms-transacciones | 8081 | Gestión de transacciones y Saga de retiros |
| ms-intereses | 8082 | Gestión y consulta de intereses |
| ms-cuentas | 8083 | Gestión de cuentas y procesamiento de débitos |
| Eureka Server | 8761 | Registro y descubrimiento de servicios |
| Config Server | 8888 | Configuración centralizada |
| Auth Server | 9000 | Autenticación OAuth 2.0 y emisión de JWT |
| H2 Server | 9092 | Base de datos compartida |
| Batch (`xyz`) | — | Inicialización y procesamiento de datos |

Flujo general:

```text
                        Cliente
                           |
                           v
                    +-------------+
                    | API Gateway |
                    |    :8080    |
                    +------+------+
                           |
             +-------------+-------------+
             |             |             |
             v             v             v
       ms-cuentas     ms-intereses   ms-transacciones
         :8083           :8082           :8081
             |             |             |
             +-------------+-------------+
                           |
                      H2 Server
                        :9092

       +--------------------------------------+
       | Infraestructura                     |
       |                                      |
       | Config Server :8888                  |
       | Eureka Server :8761                  |
       | Auth Server   :9000                  |
       +--------------------------------------+
```

Todos los componentes desplegados mediante Docker Compose se comunican a través de la red interna:

```text
banco-network
```

---

## 3. Seguridad OAuth 2.0

La solución utiliza **Spring Authorization Server** para emitir tokens JWT mediante el flujo:

```text
client_credentials
```

Los microservicios y el API Gateway funcionan como **OAuth2 Resource Server**.

La autorización se realiza mediante scopes, permitiendo restringir las operaciones disponibles para cada cliente.

### Clientes OAuth2

#### cliente-web

Credenciales:

```text
Client ID: cliente-web
Client Secret: web-secret
```

Scopes:

```text
cuentas.read
transacciones.read
intereses.read
```

Este cliente está orientado a operaciones de consulta.

---

#### cliente-atm

Credenciales:

```text
Client ID: cliente-atm
Client Secret: atm-secret
```

Scopes:

```text
cuentas.read
retiros.write
```

Permite consultar información de cuentas y solicitar retiros.

---

#### cliente-interno

Credenciales:

```text
Client ID: cliente-interno
Client Secret: interno-secret
```

Scope:

```text
servicios.internal
```

Se utiliza para proteger operaciones internas entre servicios.

---

## 4. Obtención de token

Endpoint:

```http
POST http://localhost:9000/oauth2/token
```

Ejemplo para `cliente-web`.

Authorization:

```text
Basic Auth

Username: cliente-web
Password: web-secret
```

Body (`x-www-form-urlencoded`):

```text
grant_type = client_credentials
scope = cuentas.read transacciones.read intereses.read
```

Ejemplo para `cliente-atm`:

```text
Username: cliente-atm
Password: atm-secret

grant_type = client_credentials
scope = cuentas.read retiros.write
```

El token obtenido debe enviarse en las solicitudes protegidas:

```http
Authorization: Bearer <TOKEN>
```

---

## 5. Autorización por scopes

La seguridad diferencia entre autenticación y autorización.

Ejemplos:

| Operación | Scope requerido |
|---|---|
| GET `/cuentas/**` | `cuentas.read` |
| GET `/transacciones/**` | `transacciones.read` |
| GET `/intereses/**` | `intereses.read` |
| POST `/transacciones/retiros` | `retiros.write` |
| `/interno/**` | `servicios.internal` |

Esto permite obtener diferentes respuestas dependiendo de los permisos del cliente:

```text
200 OK
```

Solicitud autenticada y autorizada.

```text
401 Unauthorized
```

Solicitud sin un token válido.

```text
403 Forbidden
```

Token válido, pero sin el scope necesario para acceder al recurso.

Por ejemplo, `cliente-web` puede consultar transacciones, pero no puede ejecutar:

```http
POST /transacciones/retiros
```

debido a que no posee `retiros.write`.

---

## 6. Spring Cloud Config

La configuración de los microservicios se encuentra centralizada mediante **Spring Cloud Config Server**.

Directorio:

```text
config-repo/
```

Se mantienen configuraciones diferenciadas para ejecución local y Docker.

Ejemplo de ejecución local:

```text
http://localhost:9000
http://localhost:8761
```

En Docker se utilizan los nombres de los servicios dentro de la red:

```text
http://auth-server:9000
http://eureka-server:8761
http://config-server:8888
jdbc:h2:tcp://h2:9092/bankxyz
```

Esto permite ejecutar la misma solución correctamente en ambos entornos.

---

## 7. Eureka Server

Los microservicios utilizan **Netflix Eureka** para registro y descubrimiento de servicios.

Dashboard:

```text
http://localhost:8761
```

Dentro del entorno Docker, los servicios utilizan:

```text
http://eureka-server:8761/eureka/
```

---

## 8. Resilience4j

La solución implementa **Circuit Breaker** mediante Resilience4j.

Se utiliza la instancia:

```text
dependencia
```

Ejemplo de configuración:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      dependencia:
        sliding-window-size: 2
        minimum-number-of-calls: 1
        failure-rate-threshold: 50
```

En `ms-cuentas` puede comprobarse mediante:

```http
GET http://localhost:8080/cuentas/salud-dependencia
```

Cuando la dependencia responde correctamente se obtiene:

```json
{
  "estado": "OK",
  "circuito": "CLOSED"
}
```

También es posible provocar deliberadamente un fallo:

```http
GET http://localhost:8080/cuentas/salud-dependencia?forzarFallo=true
```

En este caso Resilience4j activa el mecanismo de fallback y el Circuit Breaker cambia de estado:

```json
{
  "estado": "FALLBACK",
  "circuito": "OPEN"
}
```

De esta forma se evita que un fallo en una dependencia provoque una falla en cascada sobre el resto de la aplicación.

---

## 9. Comunicación asíncrona con JMS

La solución utiliza **JMS** para implementar comunicación asíncrona entre `ms-transacciones` y `ms-cuentas`.

El flujo principal corresponde al procesamiento de retiros.

```text
ms-transacciones
       |
       | cuentas.debitar
       v
     JMS
       |
       v
ms-cuentas
       |
       | cuentas.debitar.respuesta
       v
     JMS
       |
       v
ms-transacciones
```

Las principales colas utilizadas son:

```text
cuentas.debitar
cuentas.debitar.respuesta
cuentas.compensar
```

`ms-transacciones` publica eventos mediante `JmsTemplate`, mientras que los consumidores utilizan `@JmsListener`.

---

## 10. Saga de retiros

Los retiros utilizan un patrón **Saga** basado en eventos JMS.

Endpoint:

```http
POST http://localhost:8080/transacciones/retiros
```

Ejemplo:

```json
{
  "cuentaId": 1,
  "monto": 1000,
  "simularFallo": false,
  "simularDuplicado": false
}
```

La operación requiere:

```text
retiros.write
```

### Retiro exitoso

Flujo:

```text
Solicitud de retiro
       |
       v
PENDIENTE
       |
       | cuentas.debitar
       v
ms-cuentas
       |
       | débito realizado
       v
cuentas.debitar.respuesta
       |
       v
CONFIRMADO
```

El estado final comprobado es:

```text
CONFIRMADO
```

### Compensación

También puede simularse un fallo posterior al débito:

```json
{
  "cuentaId": 1,
  "monto": 500,
  "simularFallo": true,
  "simularDuplicado": false
}
```

En este escenario:

```text
Débito
  |
  v
Fallo simulado
  |
  v
COMPENSANDO
  |
  | cuentas.compensar
  v
Compensación de la operación
  |
  v
COMPENSADO
```

El mecanismo permite revertir el cambio realizado previamente y mantener la consistencia entre los microservicios.

---

## 11. Idempotencia

La comunicación JMS contempla mensajes duplicados.

Los eventos procesados se registran mediante un identificador único:

```text
event_id
```

La tabla:

```text
eventos_procesados
```

utiliza dicho identificador como clave primaria.

Si un mismo evento vuelve a ser recibido, se detecta como:

```text
DUPLICADO
```

evitando ejecutar dos veces la misma operación.

---

## 12. Docker

Cada aplicación cuenta con su correspondiente `Dockerfile`.

Las imágenes utilizan:

```dockerfile
FROM eclipse-temurin:17-jre
```

Los JAR se incorporan utilizando un argumento de construcción:

```dockerfile
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
```

Ejemplo de ejecución:

```dockerfile
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

El proyecto contiene imágenes para los principales componentes:

```text
api-gateway
auth-server
config-server
eureka-server
h2-server
ms-cuentas
ms-intereses
ms-transacciones
xyz
```

---

## 13. Docker Compose

La infraestructura completa se orquesta mediante:

```text
docker-compose.yml
```

Se utiliza una red interna:

```yaml
networks:
  banco-network:
    driver: bridge
```

Los servicios se comunican utilizando sus nombres dentro de Docker, por ejemplo:

```text
auth-server:9000
config-server:8888
eureka-server:8761
h2:9092
ms-cuentas:8083
ms-intereses:8082
ms-transacciones:8081
```

También se utilizan `healthcheck` y `depends_on` para controlar el orden de inicialización.

El flujo de inicio es:

```text
H2
 |
 v
Batch
 |
 v
Config Server / Eureka / Auth Server
 |
 v
Microservicios
 |
 v
API Gateway
```

El proceso Batch finaliza después de inicializar y procesar los datos, por lo que su estado esperado después de una ejecución correcta es:

```text
Exited (0)
```

---

## 14. Compilación

Desde la raíz del proyecto:

```powershell
.\mvnw.cmd clean package -DskipTests
```

El módulo Batch `xyz` se compila de forma independiente:

```powershell
cd .\xyz
..\mvnw.cmd clean package -DskipTests
cd ..
```

---

## 15. Construcción de imágenes Docker

Con Docker Desktop iniciado:

```powershell
docker compose build
```

---

## 16. Ejecución con Docker Compose

Para iniciar la infraestructura:

```powershell
docker compose up -d
```

Para comprobar el estado:

```powershell
docker compose ps -a
```

El resultado esperado es que los servicios permanezcan `Up` y que Batch finalice correctamente con:

```text
Exited (0)
```

Para detener y eliminar los contenedores:

```powershell
docker compose down
```

---

## 17. Endpoints principales

### Cuentas

```http
GET /cuentas/estados
GET /cuentas/origen-config
GET /cuentas/salud-dependencia
```

### Intereses

```http
GET /intereses/cuentas
GET /intereses/origen-config
```

### Transacciones

```http
GET /transacciones
GET /transacciones/origen-config
GET /transacciones/retiros/{id}
POST /transacciones/retiros
```

Todas las solicitudes externas se realizan preferentemente mediante:

```text
http://localhost:8080
```

correspondiente al API Gateway.

---

## 18. Pruebas realizadas

Durante la ejecución de la solución se comprobaron los siguientes escenarios:

### OAuth 2.0

- Obtención de JWT mediante `client_credentials`.
- Acceso autorizado con respuesta `200 OK`.
- Acceso sin autenticación con respuesta `401 Unauthorized`.
- Acceso con token válido pero scope insuficiente con respuesta `403 Forbidden`.
- Separación de permisos entre `cliente-web` y `cliente-atm`.

### Docker y Docker Compose

- Construcción de las imágenes Docker.
- Inicio de los componentes mediante Docker Compose.
- Ejecución de H2 Server.
- Ejecución satisfactoria del proceso Batch.
- Config Server operativo.
- Eureka Server operativo.
- Auth Server operativo.
- Microservicios operativos.
- API Gateway operativo.

### Resilience4j

- Circuit Breaker en estado `CLOSED` durante funcionamiento normal.
- Simulación controlada de fallo.
- Activación de fallback.
- Circuit Breaker en estado `OPEN`.

### JMS y Saga

- Publicación de retiro mediante JMS.
- Procesamiento del débito en `ms-cuentas`.
- Respuesta asíncrona hacia `ms-transacciones`.
- Retiro finalizado en estado `CONFIRMADO`.
- Simulación de fallo posterior.
- Publicación del evento de compensación.
- Retiro finalizado en estado `COMPENSADO`.

---

## 19. Evidencias

Las evidencias de ejecución se encuentran incorporadas en la entrega e incluyen capturas de:

1. Contenedores ejecutándose mediante Docker Compose.
2. Obtención de token OAuth 2.0.
3. Solicitud autorizada con respuesta `200 OK`.
4. Solicitud sin autorización con respuesta `401 Unauthorized`.
5. Solicitud con scope insuficiente con respuesta `403 Forbidden`.
6. Circuit Breaker en funcionamiento normal (`CLOSED`).
7. Activación del fallback y Circuit Breaker (`OPEN`).
8. Saga JMS finalizada en estado `CONFIRMADO`.
9. Saga JMS con compensación finalizada en estado `COMPENSADO`.

---

## 20. Tecnologías utilizadas

- Java 17
- Spring Boot
- Spring Security
- Spring Authorization Server
- OAuth 2.0
- JWT
- Spring Cloud Config
- Netflix Eureka
- Spring Cloud Gateway
- Resilience4j
- JMS
- Spring JMS
- H2 Database
- Spring JDBC
- Spring Batch
- Maven
- Docker
- Docker Compose

---

## 21. Estructura general

```text
Backend/
|
|-- api-gateway/
|-- auth-server/
|-- config-repo/
|-- config-server/
|-- eureka-server/
|-- h2-server/
|-- ms-cuentas/
|-- ms-intereses/
|-- ms-transacciones/
|-- xyz/
|-- docker-compose.yml
|-- pom.xml
|-- mvnw
|-- mvnw.cmd
`-- README.md
```

---

## 22. Resultado

La solución permite ejecutar una arquitectura de microservicios integrada y contenerizada, incorporando autenticación y autorización OAuth 2.0, configuración centralizada, descubrimiento de servicios, resiliencia ante fallos, comunicación asíncrona mediante JMS y una Saga con mecanismo de compensación.

La infraestructura puede levantarse de forma coordinada mediante Docker Compose y los servicios pueden consumirse a través del API Gateway.
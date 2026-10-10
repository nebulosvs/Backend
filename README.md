# Banco XYZ – Evaluación Final Backend III

Proyecto desarrollado para la **Evaluación Final Transversal de Desarrollo Backend III (PBY2203)**.

La solución moderniza parte de la plataforma legacy del Banco XYZ mediante una arquitectura distribuida basada en **Spring Boot, Spring Batch, Spring Cloud y microservicios**, incorporando Backend for Frontend (BFF), seguridad OAuth 2.0 y JWT, resiliencia con Resilience4j, comunicación asíncrona mediante JMS, persistencia en H2, contenerización con Docker y despliegue en AWS EC2.

Además, el proyecto incorpora un flujo de integración y despliegue continuo mediante **GitHub Actions y Docker Hub**.

---

## 1. Objetivo

El objetivo del proyecto es modernizar procesos bancarios originalmente asociados a una plataforma legacy, implementando una arquitectura que permita mejorar la mantenibilidad, seguridad, resiliencia, integración y capacidad de despliegue del sistema.

La solución implementada permite:

- Migrar procesos legacy a Spring Batch.
- Separar funcionalidades mediante microservicios.
- Implementar BFF para los canales Web, Mobile y ATM.
- Proteger los servicios mediante OAuth 2.0 y JWT.
- Aplicar autorización mediante scopes.
- Centralizar configuraciones mediante Spring Cloud Config.
- Registrar y descubrir servicios mediante Eureka.
- Implementar tolerancia a fallos mediante Resilience4j.
- Implementar comunicación asíncrona mediante JMS.
- Aplicar el patrón Saga para el procesamiento distribuido de retiros.
- Mantener idempotencia en el procesamiento de eventos.
- Dockerizar los componentes de la solución.
- Orquestar los servicios mediante Docker Compose.
- Desplegar la infraestructura en AWS EC2.
- Automatizar la construcción y despliegue mediante GitHub Actions.

---

## 2. Arquitectura general

La solución está compuesta por los siguientes componentes:

| Componente | Puerto | Responsabilidad |
|---|---:|---|
| API Gateway | 8080 | Punto de entrada y enrutamiento hacia los microservicios |
| ms-transacciones | 8081 | Consulta de transacciones y procesamiento de retiros |
| ms-intereses | 8082 | Consulta y gestión de información de intereses |
| ms-cuentas | 8083 | Consulta de cuentas y procesamiento de débitos |
| Eureka Server | 8761 | Registro y descubrimiento de servicios |
| Config Server | 8888 | Configuración centralizada |
| Auth Server | 9000 | Autenticación OAuth 2.0 y emisión de JWT |
| H2 Server | 9092 | Persistencia central de datos |
| Spring Batch (`xyz`) | — | Procesamiento e inicialización de datos bancarios |
| BFF (`xyz-bff`) | 8443 | Backend especializado para Web, Mobile y ATM |

### Flujo general

```text
                    Clientes
            Web / Mobile / ATM
                      |
                      v
              +---------------+
              |    xyz-bff    |
              | HTTPS :8443   |
              +-------+-------+
                      |
                 OAuth 2.0
                      |
                      v
              +---------------+
              |  API Gateway  |
              |     :8080     |
              +-------+-------+
                      |
        +-------------+-------------+
        |             |             |
        v             v             v
  ms-cuentas    ms-intereses   ms-transacciones
     :8083          :8082           :8081
        |             |             |
        +-------------+-------------+
                      |
                      v
                  H2 Server
                    :9092
```

La infraestructura adicional está compuesta por:

```text
+--------------------------------------+
| Infraestructura Spring Cloud         |
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

## 3. Spring Batch

El módulo:

```text
xyz
```

implementa los procesos Batch requeridos para migrar el procesamiento legacy hacia Spring Batch.

Los procesos principales corresponden a:

1. Procesamiento de transacciones diarias.
2. Cálculo de intereses.
3. Generación de estados de cuenta.

Los Jobs utilizan Steps para realizar las etapas de lectura, procesamiento y escritura de información.

La solución contempla procesamiento de datos, persistencia de resultados y manejo de errores durante la ejecución.

Dentro de Docker Compose, el proceso Batch se ejecuta después de que H2 se encuentra disponible.

Una vez finalizado correctamente, el estado esperado del contenedor Batch es:

```text
Exited (0)
```

Esto es normal, ya que corresponde a un proceso que ejecuta sus Jobs y termina, a diferencia de los microservicios que permanecen activos.

---

## 4. Microservicios

La solución separa las principales responsabilidades bancarias en diferentes servicios.

### 4.1 ms-cuentas

Responsable de consultar información de cuentas y participar en operaciones distribuidas relacionadas con débitos.

Entre sus endpoints se encuentran:

```http
GET /cuentas
GET /cuentas/{id}
GET /cuentas/{id}/saldo
GET /cuentas/{id}/estado-anual
GET /cuentas/estados
GET /cuentas/origen-config
GET /cuentas/salud-dependencia
GET /interno/ping
```

También contiene el consumidor JMS utilizado por la Saga de retiros.

---

### 4.2 ms-intereses

Responsable de consultar la información asociada al cálculo de intereses.

Endpoints principales:

```http
GET /intereses/cuentas
GET /intereses/cuentas/{id}
GET /intereses/origen-config
GET /interno/ping
```

---

### 4.3 ms-transacciones

Responsable de consultar las transacciones procesadas y gestionar el inicio de retiros.

Endpoints principales:

```http
GET /transacciones
GET /transacciones/diarias
GET /transacciones/diarias?limit={N}
GET /transacciones/origen-config
GET /transacciones/retiros/{id}
POST /transacciones/retiros
GET /interno/ping
```

El servicio participa como coordinador del flujo Saga utilizado para los retiros.

---

## 5. Backend for Frontend

El proyecto incorpora el módulo:

```text
xyz-bff
```

que implementa un Backend for Frontend especializado para tres canales:

- Web
- Mobile
- ATM

El BFF no accede directamente a la base de datos.

Las solicitudes se realizan hacia los servicios backend protegidos mediante OAuth 2.0, utilizando el API Gateway como punto de entrada.

```text
Cliente
   |
   v
BFF
   |
   | OAuth2 Client Credentials
   v
API Gateway
   |
   v
Microservicios
   |
   v
Base de datos
```

El BFF se encuentra protegido mediante HTTPS y utiliza el puerto:

```text
8443
```

---

## 6. BFF Web

El canal Web entrega información más completa para interfaces de escritorio.

Autenticación del canal:

```text
Basic Auth

Usuario: web
Contraseña: web123
```

Endpoints principales:

```http
GET /bff/web/dashboard
GET /bff/web/transacciones
GET /bff/web/intereses
GET /bff/web/cuentas/{id}/estado-anual
```

Internamente utiliza las credenciales OAuth2 correspondientes al cliente Web para comunicarse con los servicios backend.

---

## 7. BFF Mobile

El canal Mobile entrega respuestas orientadas a aplicaciones móviles y permite limitar la cantidad de información solicitada.

Autenticación:

```text
Basic Auth

Usuario: mobile
Contraseña: mobile123
```

Endpoints principales:

```http
GET /bff/mobile/home?limit=5
GET /bff/mobile/cuentas
GET /bff/mobile/cuentas/{id}/resumen
```

El canal utiliza un cliente OAuth2 independiente:

```text
cliente-mobile
```

---

## 8. BFF ATM

El canal ATM está orientado a operaciones críticas de cajeros automáticos.

Autenticación:

```text
Basic Auth

Usuario: atm
Contraseña: atm123
```

Además, las operaciones asociadas a una cuenta requieren el header:

```http
X-ATM-PIN: <PIN>
```

Endpoints:

```http
GET /bff/atm/ping
GET /bff/atm/saldo/{id}
POST /bff/atm/retiro
```

Ejemplo de solicitud de retiro:

```json
{
  "id": 1,
  "monto": 100
}
```

El identificador enviado en las rutas del BFF corresponde al identificador técnico de la cuenta.

El PIN es validado utilizando la información de la cuenta obtenida desde el backend.

---

## 9. Seguridad OAuth 2.0 y JWT

La solución utiliza Spring Authorization Server para emitir tokens JWT mediante el flujo:

```text
client_credentials
```

El API Gateway y los microservicios funcionan como OAuth2 Resource Server.

La autorización se realiza mediante scopes.

### Clientes OAuth2

#### cliente-web

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

---

#### cliente-mobile

```text
Client ID: cliente-mobile
Client Secret: mobile-secret
```

Scopes:

```text
cuentas.read
transacciones.read
intereses.read
```

---

#### cliente-atm

```text
Client ID: cliente-atm
Client Secret: atm-secret
```

Scopes:

```text
cuentas.read
retiros.write
```

---

#### cliente-interno

```text
Client ID: cliente-interno
Client Secret: interno-secret
```

Scope:

```text
servicios.internal
```

Este cliente se utiliza para proteger operaciones internas entre servicios.

---

## 10. Obtención de token

Para ejecución local:

```http
POST http://localhost:9000/oauth2/token
```

Authorization:

```text
Basic Auth
```

Ejemplo para `cliente-web`:

```text
Username: cliente-web
Password: web-secret
```

Body `x-www-form-urlencoded`:

```text
grant_type=client_credentials
scope=cuentas.read transacciones.read intereses.read
```

El token obtenido debe enviarse en las solicitudes protegidas:

```http
Authorization: Bearer <TOKEN>
```

---

## 11. Autorización por scopes

Los principales permisos son:

| Operación | Scope requerido |
|---|---|
| GET `/cuentas/**` | `cuentas.read` |
| GET `/transacciones/**` | `transacciones.read` |
| GET `/intereses/**` | `intereses.read` |
| POST `/transacciones/retiros` | `retiros.write` |
| `/interno/**` | `servicios.internal` |

La separación de permisos permite comprobar diferentes escenarios:

```text
200 OK
```

Solicitud autenticada y autorizada.

```text
401 Unauthorized
```

Solicitud sin credenciales válidas.

```text
403 Forbidden
```

Cliente autenticado, pero sin permisos suficientes para ejecutar la operación.

Por ejemplo, `cliente-web` puede consultar transacciones, pero no puede ejecutar retiros debido a que no posee:

```text
retiros.write
```

---

## 12. Spring Cloud Config

Las configuraciones se centralizan mediante Spring Cloud Config Server.

El repositorio de configuración se encuentra en:

```text
config-repo/
```

La solución diferencia entre direcciones utilizadas en ejecución local y nombres de servicio utilizados dentro de Docker.

Ejemplos dentro de Docker:

```text
http://auth-server:9000
http://eureka-server:8761
http://config-server:8888
jdbc:h2:tcp://h2:9092/bankxyz
```

Esto permite mantener centralizadas las configuraciones necesarias para los microservicios.

---

## 13. Eureka Server

Netflix Eureka permite registrar y descubrir dinámicamente los servicios.

En ejecución local:

```text
http://localhost:8761
```

Dentro de Docker:

```text
http://eureka-server:8761/eureka/
```

Los microservicios pueden registrarse en Eureka y utilizar el descubrimiento de servicios dentro de la arquitectura distribuida.

---

## 14. Resilience4j

La solución implementa Circuit Breaker mediante Resilience4j para controlar fallos en llamadas entre servicios.

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
GET /cuentas/salud-dependencia
```

En funcionamiento normal:

```json
{
  "estado": "OK",
  "circuito": "CLOSED"
}
```

También puede provocarse deliberadamente un fallo:

```http
GET /cuentas/salud-dependencia?forzarFallo=true
```

El mecanismo de fallback permite obtener una respuesta controlada y comprobar la apertura del circuito:

```json
{
  "estado": "FALLBACK",
  "circuito": "OPEN"
}
```

De esta manera se evita que el fallo de una dependencia produzca una falla en cascada sobre el resto de la solución.

---

## 15. Comunicación asíncrona mediante JMS

La solución utiliza JMS para implementar comunicación asíncrona entre:

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

`ms-transacciones` publica los eventos correspondientes al retiro y `ms-cuentas` consume las solicitudes de débito.

Los consumidores utilizan:

```text
@JmsListener
```

mientras que la publicación de mensajes se realiza mediante:

```text
JmsTemplate
```

---

## 16. Saga de retiros

Los retiros utilizan un patrón Saga basado en eventos JMS.

El flujo de un retiro exitoso es:

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
        | débito OK
        v
cuentas.debitar.respuesta
        |
        v
    CONFIRMADO
```

La ejecución realizada en AWS permitió comprobar la trazabilidad completa mediante un mismo `eventId`.

Ejemplo de evidencia obtenida:

```text
ms-transacciones:
SAGA cola=cuentas.debitar
cuentaId=1
monto=100

ms-cuentas:
SAGA cola=cuentas.debitar
resultado=...;6970.00;OK

ms-transacciones:
SAGA cola=cuentas.debitar.respuesta
estado=CONFIRMADO
```

Esto permite comprobar que el retiro no corresponde únicamente a una respuesta HTTP, sino que existe procesamiento asíncrono entre los microservicios.

---

## 17. Compensación e idempotencia

La Saga contempla compensación en caso de producirse un fallo después de realizar un débito.

Flujo:

```text
Débito
  |
  v
Fallo
  |
  v
COMPENSANDO
  |
  | cuentas.compensar
  v
Compensación
  |
  v
COMPENSADO
```

Además, la comunicación JMS contempla el manejo de mensajes duplicados.

Los eventos procesados se registran mediante:

```text
event_id
```

en la tabla:

```text
eventos_procesados
```

Esto permite detectar eventos previamente procesados y evitar que una misma operación sea aplicada más de una vez.

---

## 18. Docker

Los componentes de la solución cuentan con Dockerfiles para generar sus respectivas imágenes.

Entre los componentes contenerizados se encuentran:

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
xyz-bff
```

La contenerización permite mantener un entorno reproducible para la ejecución local y el despliegue en AWS.

---

## 19. Docker Compose

Para desarrollo y ejecución local se utiliza:

```text
docker-compose.yml
```

Para el despliegue basado en imágenes publicadas se utiliza:

```text
docker-compose.prod.yml
```

La solución utiliza la red:

```text
banco-network
```

Dentro de esta red los componentes pueden comunicarse utilizando sus nombres de servicio.

Ejemplos:

```text
auth-server:9000
config-server:8888
eureka-server:8761
h2:9092
ms-cuentas:8083
ms-intereses:8082
ms-transacciones:8081
api-gateway:8080
```

Se utilizan además `healthcheck`, `depends_on` y políticas de reinicio para controlar la inicialización de la infraestructura.

---

## 20. Despliegue en AWS

La solución fue desplegada en una instancia Amazon EC2.

El servidor ejecuta:

- Amazon Linux 2023.
- Docker.
- Docker Compose.
- Las imágenes generadas por el proyecto.
- La infraestructura completa del Banco XYZ.

El despliegue permite ejecutar en AWS:

```text
H2 Server
Spring Batch
Config Server
Eureka Server
Auth Server
ms-cuentas
ms-intereses
ms-transacciones
API Gateway
xyz-bff
```

La configuración de red de AWS permite exponer únicamente los puertos necesarios para las pruebas externas.

Los servicios internos permanecen comunicados mediante la red Docker.

Para evitar depender de una dirección específica, la documentación representa la dirección pública de la instancia como:

```text
<EC2_PUBLIC_IP>
```

Por ejemplo:

```text
http://<EC2_PUBLIC_IP>:9000
http://<EC2_PUBLIC_IP>:8080
https://<EC2_PUBLIC_IP>:8443
```

---

## 21. CI/CD con GitHub Actions

El proyecto implementa un pipeline de integración y despliegue continuo mediante GitHub Actions.

El workflow se ejecuta a partir de cambios enviados a la rama principal.

El flujo general es:

```text
Push a main
     |
     v
GitHub Actions
     |
     +--> Configuración JDK 17
     |
     +--> Compilación Maven
     |
     +--> Construcción de imágenes Docker
     |
     +--> Publicación en Docker Hub
     |
     +--> Conexión SSH con EC2
     |
     +--> docker compose pull
     |
     +--> docker compose up -d
     |
     v
Aplicación actualizada en AWS
```

Las credenciales necesarias para el pipeline se gestionan mediante GitHub Secrets.

Entre ellas:

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
EC2_HOST
EC2_SSH_KEY
USER_SERVER
```

Las imágenes Docker se publican en Docker Hub y posteriormente son descargadas desde la instancia EC2 mediante `docker compose pull`.

De esta manera, el despliegue no requiere compilar manualmente el proyecto dentro del servidor.

---

## 22. Pruebas realizadas

Durante el desarrollo se realizaron pruebas locales, mediante Docker y sobre el entorno desplegado en AWS.

### OAuth 2.0

Se comprobó:

- Obtención de JWT mediante `client_credentials`.
- Acceso autorizado con respuesta `200 OK`.
- Acceso sin autenticación con respuesta `401 Unauthorized`.
- Acceso con token válido pero scope insuficiente con respuesta `403 Forbidden`.
- Separación de permisos entre los diferentes clientes OAuth2.

### Microservicios

Se comprobaron consultas mediante el API Gateway para:

```http
GET /cuentas/estados
GET /transacciones/diarias
GET /intereses/cuentas
```

obteniendo respuestas exitosas.

### Backend for Frontend

Se comprobaron los canales:

```text
Web
Mobile
ATM
```

incluyendo autenticación independiente por canal.

### ATM

Se comprobaron:

- Consulta de saldo.
- Validación de PIN.
- Rechazo de PIN incorrecto.
- Solicitud de retiro.
- Respuesta HTTP `202 Accepted`.
- Actualización posterior del saldo.

### JMS y Saga

Se comprobó:

- Publicación de `cuentas.debitar`.
- Consumo del evento en `ms-cuentas`.
- Procesamiento del débito.
- Publicación de `cuentas.debitar.respuesta`.
- Recepción de la respuesta en `ms-transacciones`.
- Finalización del retiro en estado `CONFIRMADO`.
- Compensación ante fallos controlados.
- Manejo de eventos duplicados.

### Resilience4j

Se comprobó:

- Circuit Breaker en estado `CLOSED`.
- Simulación de fallo.
- Activación del fallback.
- Circuit Breaker en estado `OPEN`.

### Docker

Se comprobó:

- Construcción de imágenes.
- Inicio coordinado mediante Docker Compose.
- Healthchecks.
- Ejecución de Spring Batch.
- Ejecución de los microservicios.
- Ejecución del BFF.
- Ejecución del API Gateway.

### AWS

Se comprobó desde un cliente externo:

- Obtención de token OAuth2 desde Auth Server.
- Consumo de servicios mediante API Gateway.
- Acceso al BFF mediante HTTPS.
- Consulta de saldo ATM.
- Ejecución de retiro ATM.
- Procesamiento JMS/Saga entre microservicios.
- Persistencia del nuevo saldo.

### CI/CD

Se comprobó:

- Ejecución exitosa del workflow.
- Compilación del proyecto.
- Construcción de imágenes.
- Publicación en Docker Hub.
- Transferencia de configuración hacia EC2.
- Actualización automática de los contenedores en AWS.

---

## 23. Tecnologías utilizadas

- Java 17
- Spring Boot
- Spring Batch
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
- Maven
- Docker
- Docker Compose
- Docker Hub
- GitHub
- GitHub Actions
- AWS EC2
- HTTPS / TLS
- Postman

---

## 24. Estructura general del proyecto

```text
Backend/
|
|-- .github/
|   `-- workflows/
|       `-- deploy.yml
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
|-- xyz-bff/
|
|-- docker-compose.yml
|-- docker-compose.prod.yml
|-- pom.xml
|-- mvnw
|-- mvnw.cmd
|-- README.md
|-- instrucciones.md
`-- despliegue.md
```

---

## 25. Ejecución local

Para compilar el proyecto desde la raíz:

```powershell
.\mvnw.cmd clean package -DskipTests
```

El módulo Batch puede compilarse independientemente:

```powershell
cd .\xyz
..\mvnw.cmd clean package -DskipTests
cd ..
```

Construir las imágenes:

```powershell
docker compose build
```

Iniciar la infraestructura:

```powershell
docker compose up -d
```

Comprobar el estado:

```powershell
docker compose ps -a
```

Detener la infraestructura:

```powershell
docker compose down
```

Para instrucciones detalladas de ejecución y pruebas, consultar:

```text
instrucciones.md
```

---

## 26. Documentación adicional

El repositorio incluye documentación complementaria para facilitar la ejecución, validación y despliegue de la solución.

### Instrucciones de ejecución y pruebas

Consultar [`instrucciones.md`](instrucciones.md).

Este documento contiene el paso a paso para ejecutar la solución y comprobar sus principales componentes, incluyendo:

- Spring Batch.
- Microservicios.
- OAuth 2.0 y autorización mediante scopes.
- API Gateway.
- BFF Web, Mobile y ATM.
- Resilience4j.
- JMS y patrón Saga.
- Ejecución mediante Docker Compose.

### Despliegue en AWS

Consultar [`despliegue.md`](despliegue.md).

Este documento describe el proceso utilizado para desplegar la solución en AWS, incluyendo:

- Preparación de la instancia EC2.
- Instalación y configuración de Docker.
- Docker Compose para producción.
- Publicación de imágenes en Docker Hub.
- Configuración de GitHub Secrets.
- Pipeline CI/CD mediante GitHub Actions.
- Despliegue automático hacia EC2.
- Verificación de los servicios desplegados.

---

## 27. Resultado final

La solución desarrollada permite modernizar los principales componentes del sistema bancario legacy mediante una arquitectura distribuida basada en Spring.

Spring Batch permite procesar la información bancaria requerida, mientras que los microservicios separan las responsabilidades de cuentas, intereses y transacciones.

El patrón Backend for Frontend proporciona interfaces específicas para Web, Mobile y ATM, evitando el acceso directo de estos canales a la base de datos y utilizando servicios backend protegidos.

OAuth 2.0, JWT y la autorización mediante scopes permiten separar los permisos de los diferentes clientes y proteger tanto las operaciones externas como internas.

Resilience4j proporciona mecanismos de tolerancia a fallos y JMS permite implementar comunicación asíncrona entre los microservicios. Sobre esta comunicación se implementa una Saga para mantener la consistencia durante el procesamiento distribuido de retiros.

Finalmente, Docker y Docker Compose permiten ejecutar la infraestructura de forma reproducible, mientras que GitHub Actions, Docker Hub y AWS EC2 permiten automatizar la construcción, publicación y despliegue de la solución en un entorno de nube.
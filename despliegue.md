# Despliegue en AWS – Banco XYZ

Este documento describe el procedimiento utilizado para desplegar la solución Banco XYZ en un entorno de nube utilizando **AWS EC2, Docker, Docker Compose, Docker Hub y GitHub Actions**.

La estrategia implementada permite construir las aplicaciones, generar las imágenes Docker, publicarlas en Docker Hub y actualizar automáticamente la infraestructura desplegada en Amazon EC2.

---

## 1. Arquitectura de despliegue

El flujo de despliegue implementado es:

```text
Repositorio GitHub
       |
       | push a main
       v
GitHub Actions
       |
       | Compilación Maven
       v
Construcción imágenes Docker
       |
       v
Docker Hub
       |
       | docker push
       v
AWS EC2
       |
       | docker compose pull
       v
Docker Compose
       |
       v
Infraestructura Banco XYZ
```

La instancia EC2 ejecuta los siguientes componentes:

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

Los componentes se ejecutan como contenedores Docker y se comunican mediante una red interna de Docker Compose.

---

# Preparación de AWS

## 2. Crear instancia EC2

Desde AWS Management Console:

```text
EC2
→ Instances
→ Launch instance
```

La instancia utilizada para el proyecto utiliza:

```text
Sistema operativo: Amazon Linux 2023
```

Durante la creación se debe configurar un Key Pair para permitir posteriormente la conexión mediante SSH.

El archivo de clave privada debe almacenarse de forma segura.

Ejemplo:

```text
banco-xyz.pem
```

La clave privada no debe incorporarse al repositorio Git.

---

## 3. Configurar Security Group

La instancia debe permitir acceso SSH y los puertos externos necesarios para probar la aplicación.

Los principales puertos utilizados externamente son:

| Puerto | Servicio | Uso |
|---:|---|---|
| 22 | SSH | Administración de EC2 |
| 9000 | Auth Server | Obtención de tokens OAuth2 |
| 8080 | API Gateway | Acceso externo a microservicios |
| 8443 | BFF | Acceso HTTPS a Web, Mobile y ATM |

Para un entorno de desarrollo o evaluación, las reglas pueden restringirse a la IP pública del equipo desde el cual se realizarán las pruebas.

No es necesario exponer públicamente los puertos internos de los microservicios.

Por ejemplo, no se requiere acceso público directo a:

```text
8081
8082
8083
8888
8761
9092
61616
```

Los componentes pueden comunicarse internamente mediante Docker.

---

## 4. Conectarse mediante SSH

Desde una terminal:

```bash
ssh -i banco-xyz.pem ec2-user@<EC2_PUBLIC_IP>
```

Donde:

```text
<EC2_PUBLIC_IP>
```

corresponde a la dirección pública asignada a la instancia.

---

# Docker en EC2

## 5. Instalar Docker

Actualizar paquetes:

```bash
sudo dnf update -y
```

Instalar Docker:

```bash
sudo dnf install docker -y
```

Iniciar Docker:

```bash
sudo systemctl start docker
```

Configurar inicio automático:

```bash
sudo systemctl enable docker
```

Agregar el usuario actual al grupo Docker:

```bash
sudo usermod -aG docker ec2-user
```

Cerrar la sesión SSH y volver a ingresar para aplicar el cambio.

Comprobar:

```bash
docker --version
```

---

## 6. Instalar Docker Compose

Comprobar primero si Docker Compose se encuentra disponible:

```bash
docker compose version
```

Si el comando responde correctamente, no se requiere configuración adicional.

La solución utiliza Docker Compose V2 mediante:

```bash
docker compose
```

---

## 7. Crear directorio de despliegue

En EC2 crear:

```bash
mkdir -p /home/ec2-user/banco-xyz
```

Ingresar al directorio:

```bash
cd /home/ec2-user/banco-xyz
```

En este directorio se almacenará la configuración utilizada para levantar la infraestructura.

---

# Docker Hub

## 8. Repositorios de imágenes

Las imágenes Docker generadas por el pipeline se publican en Docker Hub.

Las imágenes utilizadas corresponden a:

```text
banco-xyz-h2
banco-xyz-batch
banco-xyz-config-server
banco-xyz-eureka-server
banco-xyz-auth-server
banco-xyz-ms-transacciones
banco-xyz-ms-intereses
banco-xyz-ms-cuentas
banco-xyz-api-gateway
banco-xyz-bff
```

Cada componente posee su propia imagen.

El formato general es:

```text
<DOCKERHUB_USERNAME>/<NOMBRE_IMAGEN>:latest
```

Ejemplo:

```text
<DOCKERHUB_USERNAME>/banco-xyz-ms-cuentas:latest
```

---

## 9. Autenticación en Docker Hub

Para realizar pruebas manuales puede iniciarse sesión mediante:

```bash
docker login
```

Ingresar las credenciales correspondientes.

Para el pipeline CI/CD no se almacenan las credenciales directamente en el código.

Estas se configuran mediante GitHub Secrets.

---

# Docker Compose para producción

## 10. docker-compose.prod.yml

El proyecto contiene:

```text
docker-compose.prod.yml
```

Este archivo está destinado al despliegue y, a diferencia del Compose utilizado para desarrollo local, utiliza imágenes publicadas en Docker Hub.

Ejemplo conceptual:

```yaml
services:

  ms-cuentas:
    image: ${DOCKERHUB_USERNAME}/banco-xyz-ms-cuentas:latest

  ms-intereses:
    image: ${DOCKERHUB_USERNAME}/banco-xyz-ms-intereses:latest

  ms-transacciones:
    image: ${DOCKERHUB_USERNAME}/banco-xyz-ms-transacciones:latest
```

La variable:

```text
DOCKERHUB_USERNAME
```

permite evitar dejar el nombre del usuario repetido directamente en cada definición.

---

## 11. Validar Docker Compose de producción

Antes de desplegar puede validarse localmente.

En PowerShell:

```powershell
$env:DOCKERHUB_USERNAME="<DOCKERHUB_USERNAME>"
docker compose -f docker-compose.prod.yml config
```

Si la configuración es correcta, Docker Compose mostrará la configuración resuelta sin errores.

Esta validación permite detectar previamente errores de sintaxis o variables faltantes.

---

# Configuración del BFF en Docker

## 12. Comunicación del BFF

En ejecución local, el BFF puede utilizar:

```text
http://localhost:8080
```

como dirección del API Gateway.

Sin embargo, dentro de Docker debe utilizar el nombre del servicio:

```text
http://api-gateway:8080
```

Por este motivo, el despliegue define:

```text
BANK_BACKEND_BASE_URL=http://api-gateway:8080
```

Para obtener tokens OAuth2 desde el Auth Server se utiliza:

```text
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_BANK_AUTH_TOKEN_URI=http://auth-server:9000/oauth2/token
```

Esto permite que el BFF se comunique con los servicios utilizando la red interna de Docker.

---

# GitHub Actions

## 13. Pipeline CI/CD

El proyecto contiene el workflow:

```text
.github/workflows/deploy.yml
```

El pipeline automatiza:

1. Descarga del código fuente.
2. Configuración de Java 17.
3. Compilación Maven.
4. Compilación del módulo Spring Batch.
5. Construcción de las imágenes Docker.
6. Autenticación en Docker Hub.
7. Publicación de las imágenes.
8. Configuración de la conexión SSH.
9. Transferencia de Docker Compose hacia EC2.
10. Actualización de las imágenes en EC2.
11. Inicio de los contenedores.
12. Verificación del estado final.

El flujo general es:

```text
Push a main
     |
     v
GitHub Actions
     |
     v
Maven Build
     |
     v
Docker Build
     |
     v
Docker Push
     |
     v
Docker Hub
     |
     v
SSH EC2
     |
     v
docker compose pull
     |
     v
docker compose up -d
```

---

## 14. Configurar GitHub Secrets

Desde el repositorio GitHub ingresar a:

```text
Settings
→ Secrets and variables
→ Actions
```

Crear los siguientes Repository Secrets:

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
EC2_HOST
EC2_SSH_KEY
USER_SERVER
```

### DOCKERHUB_USERNAME

Contiene el usuario utilizado para publicar las imágenes.

```text
DOCKERHUB_USERNAME=<usuario Docker Hub>
```

---

### DOCKERHUB_TOKEN

Contiene un Access Token generado desde Docker Hub.

No se recomienda utilizar directamente la contraseña de la cuenta.

```text
DOCKERHUB_TOKEN=<token>
```

---

### EC2_HOST

Contiene la IP pública o DNS público de la instancia EC2.

```text
EC2_HOST=<EC2_PUBLIC_IP>
```

---

### USER_SERVER

Para Amazon Linux:

```text
USER_SERVER=ec2-user
```

---

### EC2_SSH_KEY

Contiene el contenido completo de la clave privada utilizada para conectarse a EC2.

Ejemplo de estructura:

```text
-----BEGIN ... PRIVATE KEY-----
...
-----END ... PRIVATE KEY-----
```

La clave se almacena como GitHub Secret y no se incorpora al repositorio.

---

# Funcionamiento del pipeline

## 15. Compilación

GitHub Actions configura Java 17 y ejecuta Maven para compilar el proyecto.

También se compila explícitamente el módulo:

```text
xyz
```

correspondiente a Spring Batch.

La existencia del JAR puede comprobarse dentro de:

```text
xyz/target/
```

---

## 16. Construcción de imágenes

El pipeline genera imágenes para:

```text
h2-server
xyz
config-server
eureka-server
auth-server
ms-transacciones
ms-intereses
ms-cuentas
api-gateway
xyz-bff
```

Las imágenes se etiquetan utilizando:

```text
latest
```

y posteriormente son publicadas en Docker Hub.

---

## 17. Transferencia de configuración

Durante el despliegue se crea el directorio:

```text
/home/ec2-user/banco-xyz
```

El archivo:

```text
docker-compose.prod.yml
```

es transferido al servidor y utilizado como:

```text
docker-compose.yml
```

También se configura en EC2 la variable necesaria para identificar las imágenes publicadas en Docker Hub.

---

## 18. Actualización automática en EC2

Después de conectarse mediante SSH, el pipeline ejecuta el equivalente a:

```bash
cd /home/ec2-user/banco-xyz
docker compose pull
docker compose up -d --remove-orphans
```

`docker compose pull` descarga las versiones más recientes de las imágenes.

Posteriormente:

```bash
docker compose up -d --remove-orphans
```

actualiza los contenedores utilizando las nuevas imágenes.

El pipeline también puede eliminar imágenes antiguas que ya no son necesarias para reducir el espacio utilizado.

---

# Ejecución manual

## 19. Desplegar manualmente desde EC2

En caso de necesitar ejecutar el despliegue manualmente:

```bash
cd /home/ec2-user/banco-xyz
```

Actualizar imágenes:

```bash
docker compose pull
```

Iniciar o actualizar los contenedores:

```bash
docker compose up -d --remove-orphans
```

Comprobar:

```bash
docker compose ps
```

---

## 20. Estado esperado

Los servicios de ejecución continua deben aparecer como:

```text
Up
```

Los servicios con healthcheck deben aparecer como:

```text
Up (...) (healthy)
```

Spring Batch puede no aparecer entre los contenedores activos después de completar su procesamiento.

Esto es esperado, ya que su ejecución finaliza una vez completados los Jobs.

Puede comprobarse mediante:

```bash
docker compose ps -a
```

El resultado esperado para una ejecución Batch correcta es:

```text
Exited (0)
```

---

# Validación del despliegue

## 21. Probar Auth Server en AWS

Desde Postman:

```http
POST http://<EC2_PUBLIC_IP>:9000/oauth2/token
```

Authorization:

```text
Basic Auth

Username: cliente-web
Password: web-secret
```

Body:

```text
x-www-form-urlencoded
```

Valores:

```text
grant_type = client_credentials
scope = cuentas.read transacciones.read intereses.read
```

La respuesta debe entregar:

```text
access_token
```

---

## 22. Probar API Gateway en AWS

Utilizar el token obtenido anteriormente como:

```http
Authorization: Bearer <TOKEN>
```

### Cuentas

```http
GET http://<EC2_PUBLIC_IP>:8080/cuentas/estados
```

Resultado esperado:

```text
200 OK
```

### Transacciones

```http
GET http://<EC2_PUBLIC_IP>:8080/transacciones/diarias
```

Resultado esperado:

```text
200 OK
```

### Intereses

```http
GET http://<EC2_PUBLIC_IP>:8080/intereses/cuentas
```

Resultado esperado:

```text
200 OK
```

Estas pruebas permiten comprobar que el API Gateway puede enrutar correctamente las solicitudes hacia los microservicios desplegados en EC2.

---

# Validación BFF en AWS

## 23. Consideración sobre HTTPS

El BFF se encuentra disponible mediante:

```text
https://<EC2_PUBLIC_IP>:8443
```

El entorno de evaluación utiliza un certificado autofirmado.

Por este motivo, Postman puede requerir:

```text
Settings
→ General
→ SSL certificate verification
→ OFF
```

para realizar las pruebas.

---

## 24. Probar BFF Web

Ejecutar:

```http
GET https://<EC2_PUBLIC_IP>:8443/bff/web/dashboard
```

Authorization:

```text
Basic Auth

Username: web
Password: web123
```

Resultado esperado:

```text
200 OK
```

Esta prueba verifica el flujo:

```text
Cliente Web
    |
    v
BFF Web
    |
    | cliente-web
    v
OAuth2
    |
    v
API Gateway
    |
    v
Microservicios
```

---

## 25. Probar BFF Mobile

Authorization:

```text
Basic Auth

Username: mobile
Password: mobile123
```

Ejemplo:

```http
GET https://<EC2_PUBLIC_IP>:8443/bff/mobile/home?limit=5
```

Resultado esperado:

```text
200 OK
```

También pueden probarse:

```http
GET https://<EC2_PUBLIC_IP>:8443/bff/mobile/cuentas
GET https://<EC2_PUBLIC_IP>:8443/bff/mobile/cuentas/1/resumen
```

---

## 26. Probar BFF ATM

Authorization:

```text
Basic Auth

Username: atm
Password: atm123
```

Para los datos utilizados durante las pruebas:

```text
ID técnico de cuenta: 1
cuentaId: 142
PIN: 0142
```

Consultar saldo:

```http
GET https://<EC2_PUBLIC_IP>:8443/bff/atm/saldo/1
```

Agregar:

```http
X-ATM-PIN: 0142
```

Resultado esperado:

```text
200 OK
```

---

# Validación de retiro en AWS

## 27. Ejecutar retiro ATM

Ejecutar:

```http
POST https://<EC2_PUBLIC_IP>:8443/bff/atm/retiro
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

La solicitud inicia el procesamiento distribuido del retiro.

---

## 28. Verificar JMS y Saga en EC2

Conectarse mediante SSH:

```bash
ssh -i banco-xyz.pem ec2-user@<EC2_PUBLIC_IP>
```

Ingresar al directorio:

```bash
cd /home/ec2-user/banco-xyz
```

Consultar los logs de `ms-transacciones`:

```bash
docker compose logs ms-transacciones | grep -Ei "retiro|debitar|confirm|compens|saga|jms"
```

Consultar los logs de `ms-cuentas`:

```bash
docker compose logs ms-cuentas | grep -Ei "retiro|debitar|confirm|compens|saga|jms"
```

En una ejecución correcta debe observarse primero la publicación desde `ms-transacciones`:

```text
SAGA cola=cuentas.debitar
cuentaId=1
monto=100
```

Posteriormente `ms-cuentas` procesa el débito:

```text
SAGA cola=cuentas.debitar
resultado=...;OK
```

Finalmente `ms-transacciones` recibe la respuesta:

```text
SAGA cola=cuentas.debitar.respuesta
estado=CONFIRMADO
```

Los registros asociados a una misma operación comparten el mismo:

```text
eventId
```

Esto permite mantener trazabilidad sobre el flujo distribuido.

---

## 29. Verificar actualización del saldo

Después del retiro ejecutar nuevamente:

```http
GET https://<EC2_PUBLIC_IP>:8443/bff/atm/saldo/1
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

El saldo debe reflejar el monto retirado.

El flujo completo validado corresponde a:

```text
ATM
 |
 v
BFF ATM
 |
 v
API Gateway
 |
 v
ms-transacciones
 |
 | JMS: cuentas.debitar
 v
ms-cuentas
 |
 | actualiza saldo
 |
 | JMS: cuentas.debitar.respuesta
 v
ms-transacciones
 |
 v
CONFIRMADO
```

---

# Diagnóstico y mantenimiento

## 30. Consultar contenedores

```bash
docker compose ps
```

Incluyendo contenedores finalizados:

```bash
docker compose ps -a
```

---

## 31. Consultar logs

Todos los servicios:

```bash
docker compose logs
```

Un servicio específico:

```bash
docker compose logs ms-cuentas
```

Últimas 150 líneas:

```bash
docker compose logs --tail=150 ms-cuentas
```

Seguimiento en tiempo real:

```bash
docker compose logs -f ms-cuentas
```

---

## 32. Reiniciar un servicio

Ejemplo:

```bash
docker compose restart ms-cuentas
```

Comprobar posteriormente:

```bash
docker compose ps
```

---

## 33. Actualizar manualmente las imágenes

```bash
cd /home/ec2-user/banco-xyz

docker compose pull

docker compose up -d --remove-orphans
```

---

## 34. Detener la infraestructura

```bash
docker compose down
```

Para volver a iniciarla:

```bash
docker compose up -d
```

---

# Seguridad del despliegue

## 35. Gestión de secretos

Las credenciales sensibles utilizadas para el despliegue no deben almacenarse directamente en el repositorio.

GitHub Secrets permite mantener fuera del código información como:

```text
DOCKERHUB_TOKEN
EC2_HOST
EC2_SSH_KEY
```

El archivo de clave privada de EC2 tampoco debe publicarse en GitHub.

---

## 36. Exposición de servicios

Los microservicios no necesitan estar disponibles directamente desde Internet.

El acceso externo principal se realiza mediante:

```text
API Gateway :8080
BFF         :8443
Auth Server :9000
```

Servicios como:

```text
H2
Config Server
ms-cuentas
ms-intereses
ms-transacciones
```

pueden permanecer accesibles únicamente dentro de la red Docker.

Esto reduce la superficie de exposición del sistema.

---

# Verificación final

## 37. Comprobaciones del despliegue

El despliegue puede considerarse exitoso cuando se comprueba:

- La instancia EC2 se encuentra operativa.
- Docker se encuentra instalado y ejecutándose.
- Docker Compose se encuentra disponible.
- Las imágenes se encuentran publicadas en Docker Hub.
- GitHub Actions completa el workflow sin errores.
- Las imágenes son descargadas correctamente desde EC2.
- H2 se encuentra operativo.
- Spring Batch finaliza correctamente.
- Config Server se encuentra operativo.
- Eureka Server se encuentra operativo.
- Auth Server permite obtener tokens OAuth2.
- Los tres microservicios se encuentran operativos.
- API Gateway enruta correctamente las solicitudes.
- El BFF se encuentra disponible mediante HTTPS.
- BFF Web responde correctamente.
- BFF Mobile responde correctamente.
- BFF ATM valida las credenciales y el PIN.
- Las solicitudes protegidas respetan los scopes OAuth2.
- Los retiros pueden iniciarse desde el BFF ATM.
- `ms-transacciones` publica el evento JMS.
- `ms-cuentas` procesa el débito.
- `ms-transacciones` recibe la respuesta de la Saga.
- El retiro finaliza en estado `CONFIRMADO`.
- El saldo refleja correctamente el retiro.

---

## 38. Resultado

El proceso implementado permite desplegar la solución Banco XYZ en AWS utilizando una estrategia automatizada de CI/CD.

GitHub Actions compila las aplicaciones, construye las imágenes Docker y las publica en Docker Hub. Posteriormente se conecta a Amazon EC2 mediante SSH y actualiza la infraestructura utilizando Docker Compose.

El despliegue permite ejecutar de manera integrada Spring Batch, Spring Cloud, OAuth 2.0, API Gateway, los microservicios, el Backend for Frontend y la comunicación asíncrona mediante JMS.

Las pruebas realizadas directamente contra la instancia EC2 permiten verificar que los componentes desplegados se comunican correctamente y que operaciones distribuidas como el retiro ATM pueden ejecutarse completamente en el entorno de nube.
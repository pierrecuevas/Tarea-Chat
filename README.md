# Tarea-Chat
Tarea Chat Computacion en internet

## Integrantes
- [Jose Manuel Rojas]
- [Pierre Andres Cuevas]
- [Daniel Felipe Escobar]

---
## 💡 Resumen del Proyecto

Este proyecto es una **aplicación de chat multifuncional** desarrollada en **Java**. Se basa en una combinación estratégica de protocolos de red para garantizar la calidad y la velocidad de la comunicación:

* **Protocolo TCP:** Se utiliza para la **comunicación de texto fiable** (chats públicos, grupales, privados) y la **transferencia de notas de voz** para asegurar la entrega sin pérdida de datos.
* **Protocolo UDP:** Se implementa para las **llamadas de voz en tiempo real** (VoIP), priorizando la **baja latencia** sobre la fiabilidad estricta, lo que es esencial para una experiencia fluida en tiempo real.

Toda la información del usuario y los **historiales de conversación de texto y audio** se gestionan con **persistencia de datos** en una base de datos **PostgreSQL**.

# TAREA 1
## Flujo de Autenticación
El cliente inicia pidiendo **`register`** (registro) o **`login`** (inicio de sesión).

---

## Comandos y Funcionalidades

Categoría | Comando / Sintaxis | Descripción | Efecto en el Prompt |
| :--- | :--- | :--- | :--- |
| **Mensajería General** | Cualquier texto (sin `/`) | Envía el mensaje a tu canal actual (por defecto, **General**). | Se mantiene. |
| **Gestión de Canales** | `/chat <nombre_grupo>` | Cambia tu contexto al chat del grupo. Recibes el historial reciente. | Cambia a `[nombre_grupo]>` |
| **Gestión de Canales** | `/general` | Vuelve al chat público principal. | Vuelve a `[General]>` |
| **Gestión de Grupos** | `/crear <nombre_grupo>` | Crea un nuevo grupo de chat. | Se mantiene en el canal actual. |
| **Gestión de Grupos** | `/invitar <grupo> <usuario>` | Invita a otro usuario a un grupo del que eres miembro. | Se mantiene. |
| **Gestión de Grupos** | `/salir <grupo>` | Abandona un grupo de chat. | Se mantiene. |
| **Mensajería Privada** | `/msg <usuario> <mensaje>` | Envía un mensaje privado a un usuario específico. | Se mantiene. |
| **Historial** | `/historial <usuario>` | Muestra el historial reciente de tu chat privado con ese usuario. | Se mantiene. |
| **Notas de Voz** | `/grabar` | Inicia la grabación de una nota de voz. | Se mantiene. |
| **Notas de Voz** | `/detener` | Detiene la grabación actual. | Se mantiene. |
| **Notas de Voz** | `/enviar_audio <dest>` | Envía la última nota de voz grabada a un usuario o grupo. | Se mantiene. |
| **Notas de Voz** | `/reproducir <archivo.wav>` | Reproduce una nota de voz recibida o grabada. La descarga si es necesario. | Se mantiene. |
| **Llamadas** | `/llamar <usuario>` | Inicia una llamada de voz con otro usuario. | Se mantiene. |
| **Llamadas** | `/aceptar` | Acepta una llamada entrante. | Se mantiene. |
| **Llamadas** | `/rechazar` | Rechaza una llamada entrante. | Se mantiene. |
| **Llamadas** | `/colgar` | Finaliza la llamada actual o cancela una llamada saliente. | Se mantiene. |

# TAREA 2

## Descripción General

Se realizo el front end para el chat con registro, login y funcionalidades sociales como mensajes públicos, privados y chats grupales. La interacción visual es guiada por iconos y una interfaz moderna.

***

## Instrucciones para Ejecutar el Sistema

### **1. Pre-requisitos**

- Node.js y npm instalados en tu máquina.

### **2. Ejecución de servicios**

- **Backend TCP**: Es un servidor persistente que lleva la lógica central de usuarios, autenticación y chat.
- Es el mismo que usamos para la TAREA 1, hay que ejecutar el Server antes de iniciar con cualquier cosa, tambien estar seguro de que la base de datos en postresql esta bien configurada para el proyecto.

- **Proxy HTTP/WS**: Intermediario que traduce las peticiones HTTP/WebSocket del frontend al backend TCP. Hay que ejecutarlo con Node.js, se ejecuta con Node proxy-node/src/index.js

- **Frontend**: En nuestro caso es mas sencillo correrlo con Live Server, o con NPM run 

**Finalmente**
Abre el navegador en donde dice el proyecto, con Live Server es el puerto 5500.

***

## **Importante:** Como usar
- Primero hay que iniciar sesion, en caso tal de no tener cuenta crear una cuenta dandole a registrar.
- En caso de que ya exista un historial de mensajes (chat grupal/privado o grupos) se mostrara el historial en la zona media de la pagina web.
- En la barra lateral izquierda se encuentran las siguientes opciones:
- **crear grupo**: Aqui podemos crear un grupo y darle un nombre, el grupo lo podemos crear para nosotros mismos solos, como medio para almacenar Texto que consideremos importante, y tambien hay una opcion para crear el grupo con gente. El uso de crear el grupo con gente es para crearlo solo con usuarios que se encuentran activos en ese momento en la app. Al ingresar el nombre de usuario de tu amigo el cual este conectado, la app automaticamente completara el nombre por ti y lo podras seleccionar, para asi evitar errores humanos de sintaxis en los nombres. 
- **lista de chats**: Aqui podemos ver el Chat Grupal (General para todo el mundo), tambien podemos ver los grupos a los cuales pertenecemos, y los chats privados que tenemos con las demas personas.
- Al ingresar a un chat grupal creado por ti u otras personas, tienes la opcion de abandonar el chat. Esta opcion se encuentra encima de la zona de chat, al lado derecho de esa zona.
- En la barra lateral derecha podemos encontrar los usuarios que estan conectados a la pagina web, estos son los usuarios a los cuales podemos invitar a los chats grupales. 

## Flujo de Comunicación

### **1. Login y Registro**

- El usuario ve una pantalla de login/registro.
- Al registrarse/logearse, el frontend envía credenciales al **proxy** vía HTTP.
- El proxy transforma la petición, la envía al backend TCP.
- El backend TCP valida y responde con éxito/fracaso.
- El proxy reenvía la respuesta al frontend, que muestra el resultado.


### **2. Uso de la aplicación**

- Al iniciar sesión, el usuario ve íconos para chats públicos, privados y grupos.
- Las listas de chats y usuarios en línea usan iconos diferenciadores (🌐 público, 👤 privados, 👥 grupos).


#### **a. Mensajería**

- Mensajes se envían del frontend al proxy por HTTP o WebSocket.
- El proxy los reenvía al backend TCP.
- El backend TCP gestiona el almacenamiento y el envío a los destinatarios (broadcast, grupo o privado).
- Las respuestas/flujos de mensaje llegan del backend al proxy y de allí al frontend por un stream/socket abierto.


#### **b. Creación de grupo**

- El usuario presiona el botón "+" en la barra lateral.
- Aparece un modal para ingresar nombre de grupo e invitar usuarios.
- Al crear, se hace una petición al proxy para crear el grupo, luego otra/s para invitar usuarios.
- El backend TCP agrega en la estructura adecuada y notifica a los usuarios.


#### **c. Actualización de listas y usuarios**

- El frontend periódicamente (o vía sockets/eventos) pide las listas de usuarios y chats al proxy, que consulta al backend.
- Todos los clics/interacciones en la interfaz solo disparan lógica en frontend y peticiones API/proxy para mantener sincronía.

***

## Notas Técnicas

- **El frontend NO habla directo con el backend TCP**, siempre pasa por el proxy.
- Todo el renderizado y UI es controlado por JavaScript de forma reactiva según estado y respuestas.
- El historial de chat se mantiene en memoria en frontend; (si quieres persistir debes implementarlo en backend y pedir "historial" al entrar a un chat).
- El diseño es modular: cada componente (Login, ChatList, ChatWindow...) está desacoplado y basado en clases JS ES6.

***

## Resumen Visual

```plaintext
[Usuario]
   ⇅         (Navegador y lógica JS, interfaz por iconos)
[Frontend]
   ⇅         (HTTP/WS, traducción de mensajes)
[Proxy Node.js]
   ⇅         (Protocolo TCP propio)
[Backend TCP]
```
# TAREA 3
# Chat ahora con Llamadas y envio de Mensajes de voz

Este proyecto es una aplicación de chat completa que soporta mensajería de texto (pública, privada y grupal) y llamadas de voz utilizando. La arquitectura combina un servidor backend en Java (usando ZeroC Ice), un cliente web moderno y un proxy Node.js para facilitar la comunicación. Aqui Utilizamos las tareas anteriores y le añadimos el envio de audios y un tipo de llamada que es como un voice chat.

## 📂 Estructura del Proyecto

```
Tarea-Chat/
├── TCP/
│   └── server/          # Backend principal en Java
│       ├── src/         # Código fuente Java (Ice objects, lógica de BD)
├── web-client/          # Frontend Web
│   ├── src/             # Código fuente JS/HTML (Webpack)
│   ├── Chat.ice         # Definición de la interfaz Slice
│   └── package.json     # Dependencias del cliente (Ice.js, etc.)
├── proxy-node/          # Servidor Intermediario
│   ├── src/             # Lógica del proxy (Express, WebRTC signaling)
│   └── package.json     # Dependencias del proxy
├── database_setup.sql   # Script SQL para crear la base de datos PostgreSQL
├── build.gradle         # Build script raíz
└── Correr.txt           # Guía rápida de ejecución original
```

## 🛠️ Explicación de Componentes

### 1. Servidor Java (Backend)
- **Tecnología**: Java 21+, ZeroC Ice 3.7.10, PostgreSQL.
- **Función**: Actúa como el núcleo del sistema. Maneja:
  - Autenticación de usuarios y gestión de sesiones.
  - Lógica de negocio para el chat (mensajes, grupos).
  - Persistencia de datos en PostgreSQL.
  - Comunicación RPC a través de Ice.

### 2. Cliente Web (Frontend)
- **Tecnología**: HTML5, JavaScript, Webpack, Ice for JavaScript.
- **Función**: Interfaz de usuario para el chat.
  - Se conecta al servidor Java mediante Ice (a través de Glacier2 o conexión directa si es posible, en este caso configurado para WebSocket/Ice).
  - Maneja la captura y reproducción de audio para las llamadas.

### 3. Proxy Node.js
- **Tecnología**: Node.js, Express.
- **Función**: Facilita la señalización para WebRTC y sirve como puente para ciertas comunicaciones si es necesario. Resuelve problemas de conectividad directa entre navegadores y el servidor Ice para ciertos flujos de datos o señalización de llamadas.

---

## Flujo de Comunicación

### **1. Login y Registro**

- El usuario ve una pantalla de login/registro.
- Al registrarse/logearse, el frontend envía credenciales al **proxy** vía HTTP.
- El proxy transforma la petición, la envía al backend TCP.
- El backend TCP valida y responde con éxito/fracaso.
- El proxy reenvía la respuesta al frontend, que muestra el resultado.


### **2. Uso de la aplicación**

- Al iniciar sesión, el usuario ve íconos para chats públicos, privados y grupos.
- Las listas de chats y usuarios en línea usan iconos diferenciadores (🌐 público, 👤 privados, 👥 grupos).


#### **a. Mensajería**

- Mensajes se envían del frontend al proxy por HTTP o WebSocket.
- El proxy los reenvía al backend TCP.
- El backend TCP gestiona el almacenamiento y el envío a los destinatarios (broadcast, grupo o privado).
- Las respuestas/flujos de mensaje llegan del backend al proxy y de allí al frontend por un stream/socket abierto.


#### **b. Creación de grupo**

- El usuario presiona el botón "+" en la barra lateral.
- Aparece un modal para ingresar nombre de grupo e invitar usuarios.
- Al crear, se hace una petición al proxy para crear el grupo, luego otra/s para invitar usuarios.
- El backend TCP agrega en la estructura adecuada y notifica a los usuarios.


#### **c. Actualización de listas y usuarios**

- El frontend periódicamente (o vía sockets/eventos) pide las listas de usuarios y chats al proxy, que consulta al backend.
- Todos los clics/interacciones en la interfaz solo disparan lógica en frontend y peticiones API/proxy para mantener sincronía.

#### **d. Notas de Voz (Audios)**

- El usuario graba un audio en el frontend (API MediaRecorder).
- El archivo de audio se envía al **proxy** mediante una petición POST (multipart/form-data).
- El proxy guarda temporalmente el archivo o lo transmite al servidor Java.
- El servidor TCP registra el mensaje con tipo `AUDIO` y la ruta/referencia del archivo.
- Los destinatarios reciben la notificación del nuevo mensaje.
- Al reproducir, el frontend solicita el archivo de audio al proxy/servidor, que lo sirve como recurso estático o stream.

#### **e. Llamadas de Voz (WebRTC)**

- **Inicio**: Un usuario inicia una llamada a otro (privado).
- **Señalización**:
  - El frontend genera una oferta SDP (Session Description Protocol).
  - Envía la oferta al **proxy** vía WebSocket/HTTP.
  - El proxy busca al destinatario y le reenvía la oferta.
- **Respuesta**:
  - El destinatario acepta, genera una respuesta SDP y la envía de vuelta al proxy -> iniciador.
  - Se intercambian candidatos ICE (información de red) a través del proxy para establecer la ruta.
- **Conexión P2P**:
  - Una vez completada la señalización, los navegadores establecen una conexión directa (Peer-to-Peer).
  - El audio fluye directamente entre los usuarios (UDP/TCP) sin pasar por el servidor Java ni el proxy (salvo si se usa TURN, pero en red local es directo).

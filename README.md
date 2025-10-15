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

## Flujo de Autenticación
El cliente inicia pidiendo **`register`** (registro) o **`login`** (inicio de sesión).

---

## Comandos y Funcionalidades

| Categoría | Comando / Sintaxis | Descripción | Efecto en el Prompt |
| :--- | :--- | :--- | :--- |
| **Mensajería General** | Cualquier texto (sin `/`) | Envía el mensaje a tu canal **actual** (por defecto, **General**). | Se mantiene. |
| **Gestión de Canales** | `/chat <nombre_grupo>` | Cambia tu contexto al chat del grupo especificado. Recibes el historial reciente. | Cambia a `[nombre_grupo]>` |
| **Gestión de Canales** | `/general` | Vuelve al chat público principal. | Vuelve a `[General]>` |
| **Gestión de Grupos** | `/crear <nombre_grupo>` | Crea un nuevo grupo de chat. | Se mantiene en el canal actual. |
| **Gestión de Grupos** | `/invitar <nombre_grupo> <usuario>` | Invita a otro usuario a un grupo del que eres miembro. | Se mantiene. |
| **Mensajería Privada** | `/msg <usuario> <mensaje>` | Envía un mensaje privado a un usuario específico. | Se mantiene. |
| **Historial** | `/historial <usuario>` | Muestra los **últimos 15 mensajes** de tu conversación privada con ese usuario. | Se mantiene. |
| **Salida** | `/exit` | Cierra la aplicación cliente. | Cierra la aplicación. |

# Antes de Ejecutar el programa:

# Configuración de la Base de Datos PostgreSQL

Esta configuración garantiza un estado limpio y correcto de la base de datos **`chatdb`** y el usuario **`chatuser`** antes de la primera ejecución del servidor.

---

## Paso 1: Limpieza Total (Opcional, pero Recomendado)

Este paso elimina estructuras anteriores para evitar conflictos. Se realiza dentro de la interfaz de **pgAdmin 4**.

1.  Conéctate a tu servidor en pgAdmin 4.
2.  **Eliminar Base de Datos:** Busca y elimina la base de datos **`chatdb`** (Clic derecho $\to$ `Delete/Drop`).
3.  **Eliminar Usuario:** Busca y elimina el rol de *Login/Group Role* **`chatuser`** (Clic derecho $\to$ `Delete/Drop`).

---

## Paso 2: Creación de Usuario y Base de Datos

Ejecuta el siguiente script SQL en la **Query Tool** conectada al servidor general de PostgreSQL.

```sql
-- Script 1: Creación de Usuario y Base de Datos
CREATE USER chatuser WITH PASSWORD 'chatpassword';
CREATE DATABASE chatdb OWNER chatuser;
```
## Paso 3: Creación de Tablas y Permisos

Importante: Asegúrate de que la Query Tool esté conectada a la nueva base de datos chatdb antes de ejecutar el script.

Ejecuta el siguiente script completo:
```sql
-- Script 2: Creación de Tablas y Asignación de Permisos

-- Tabla de usuarios
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para el historial del chat público
CREATE TABLE public_messages (
    id SERIAL PRIMARY KEY,
    sender_username VARCHAR(50) NOT NULL REFERENCES users(username),
    message_text TEXT NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para los grupos de chat
CREATE TABLE chat_groups (
    group_name VARCHAR(100) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para relacionar usuarios y grupos (quién pertenece a qué grupo)
CREATE TABLE group_members (
    group_name VARCHAR(100) NOT NULL REFERENCES chat_groups(group_name) ON DELETE CASCADE,
    username VARCHAR(50) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_name, username)
);

-- Tabla para el historial de mensajes de grupo
CREATE TABLE group_messages (
    id SERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL REFERENCES chat_groups(group_name) ON DELETE CASCADE,
    sender_username VARCHAR(50) NOT NULL REFERENCES users(username),
    message_text TEXT NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabla para el historial de mensajes privados
CREATE TABLE private_messages (
    id SERIAL PRIMARY KEY,
    sender_username VARCHAR(50) NOT NULL REFERENCES users(username),
    recipient_username VARCHAR(50) NOT NULL REFERENCES users(username),
    message_text TEXT NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- Asignación de permisos definitiva
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO chatuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO chatuser;

-- Permisos por defecto para cualquier tabla o secuencia futura
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO chatuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO chatuser;
```
Una vez ejecutado el Script 2, la base de datos chatdb estará completamente configurada y lista para la aplicación.

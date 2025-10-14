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

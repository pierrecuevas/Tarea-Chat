const { sessions } = require('./login');
const { sendToClient } = require('./messageStream');

function disconnectHandler(req, res) {
  try {
    const authHeader = req.headers.authorization || '';
    const sessionId = authHeader.split(' ')[1];
    const { username } = req.body || {};

    if (!sessionId) {
      return res.status(401).json({ success: false, message: 'No session ID' });
    }

    console.log(`[DISCONNECT] Usuario ${username} (sesión: ${sessionId}) desconectándose...`);

    const session = sessions.get(sessionId);
    
    if (session && session.socket) {
      const tcpSocket = session.socket;
      
      // Crear mensaje de desconexión para backend TCP
      const disconnectMessage = {
        command: 'disconnect',
        username: username,
        session_id: sessionId
      };

      try {
        // Enviar al servidor TCP
        tcpSocket.write(JSON.stringify(disconnectMessage) + '\n');
        console.log(`[DISCONNECT] Mensaje de desconexión enviado al servidor TCP`);
      } catch (err) {
        console.error(`[DISCONNECT] Error escribiendo en socket TCP:`, err);
      }

      // Esperar un poco y cerrar la conexión
      setTimeout(() => {
        try {
          // Notificar al cliente que fue desconectado
          sendToClient(sessionId, { 
            type: 'notification',
            message: 'Te has desconectado del chat'
          });
          
          // Cerrar socket
          tcpSocket.end();
          sessions.delete(sessionId);
          console.log(`[DISCONNECT] Sesión ${sessionId} eliminada`);
        } catch (err) {
          console.error('[DISCONNECT] Error cerrando sesión:', err);
        }
      }, 200);
    }

    res.json({ 
      success: true,
      message: 'Desconectado correctamente'
    });

  } catch (err) {
    console.error('[DISCONNECT] Error:', err);
    res.status(500).json({ success: false, message: err.message });
  }
}

module.exports = { disconnectHandler };

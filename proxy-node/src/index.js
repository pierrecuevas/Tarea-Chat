const express = require('express');
const cors = require('cors');
const loginService = require('./services/login');
const messagesService = require('./services/messages');
const groupsService = require('./services/groups');
const messageStream = require('./services/messageStream');

const app = express();
app.use(cors());
app.use(express.json());

// Auth endpoints
app.post('/login', loginService.loginHandler);
app.post('/register', loginService.registerHandler);

// Message endpoints
app.post('/send-message', messagesService.sendMessageHandler);
app.get('/message-stream', messageStream.messageStreamHandler);

// Group endpoints
app.post('/create-group', groupsService.createGroupHandler);
app.post('/invite-to-group', groupsService.inviteToGroupHandler);

// User endpoints
app.get('/online-users', groupsService.getOnlineUsersHandler);
app.get('/all-users', groupsService.getAllUsersHandler);
app.get('/group-members', groupsService.getGroupMembersHandler);

const port = 3000;
app.listen(port, () => {
  console.log(`Proxy started on http://localhost:${port}`);
});

app.post('/disconnect', (req, res) => {
  try {
    const authHeader = req.headers.authorization || '';
    const sessionId = authHeader.split(' ')[1]; // Obtener token del header
    
    if (!sessionId) {
      return res.status(401).json({ error: 'No session provided' });
    }

    console.log(`[DISCONNECT] Usuario con sesión ${sessionId} se desconectó`);

    // Enviar comando de desconexión al backend TCP
    if (activeSessions.has(sessionId)) {
      const tcpConnection = activeSessions.get(sessionId);
      
      // Enviar comando de desconexión al servidor TCP
      const disconnectMsg = {
        command: 'disconnect',
        session_id: sessionId,
        timestamp: new Date().toISOString()
      };
      
      try {
        tcpConnection.write(JSON.stringify(disconnectMsg) + '\n');
        console.log(`[DISCONNECT] Comando enviado al servidor TCP para sesión: ${sessionId}`);
      } catch (err) {
        console.error(`[DISCONNECT] Error al enviar comando TCP:`, err);
      }
      
      // Remover de sesiones activas
      activeSessions.delete(sessionId);
    }

    // Responder al cliente
    res.json({ 
      status: 'disconnected',
      message: 'Usuario desconectado exitosamente'
    });

  } catch (err) {
    console.error('[DISCONNECT] Error:', err);
    res.status(500).json({ error: 'Error al desconectar' });
  }
});

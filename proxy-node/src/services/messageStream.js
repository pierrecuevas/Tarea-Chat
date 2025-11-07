const { sessions } = require('./login');

// Mapa de sessionId -> array de EventSource responses
const eventStreams = new Map();

function addEventStream(sessionId, res) {
  if (!eventStreams.has(sessionId)) {
    eventStreams.set(sessionId, []);
  }
  eventStreams.get(sessionId).push(res);
  
  // Limpiar cuando se cierra la conexión
  res.on('close', () => {
    removeEventStream(sessionId, res);
  });
}

function removeEventStream(sessionId, res) {
  const streams = eventStreams.get(sessionId);
  if (streams) {
    const index = streams.indexOf(res);
    if (index > -1) {
      streams.splice(index, 1);
    }
    if (streams.length === 0) {
      eventStreams.delete(sessionId);
    }
  }
}

function sendToClient(sessionId, data) {
  const streams = eventStreams.get(sessionId);
  if (streams) {
    streams.forEach(res => {
      try {
        res.write(`data: ${JSON.stringify(data)}\n\n`);
      } catch (err) {
        console.error('Error enviando a cliente:', err);
        removeEventStream(sessionId, res);
      }
    });
  }
}

function setupTCPMessageListener(sessionId, socket) {
  let buffer = '';
  
  socket.on('data', (data) => {
    buffer += data.toString();
    
    // Procesar líneas completas (terminadas en \n)
    const lines = buffer.split('\n');
    buffer = lines.pop() || ''; // Guardar la línea incompleta
    
    lines.forEach(line => {
      if (line.trim()) {
        try {
          const message = JSON.parse(line.trim());
          // Reenviar al cliente a través de SSE
          sendToClient(sessionId, message);
        } catch (err) {
          console.error('Error parseando mensaje TCP:', err, line);
        }
      }
    });
  });
  
  socket.on('error', (err) => {
    console.error('Error en socket TCP:', err);
    sendToClient(sessionId, { type: 'error', message: 'Conexión perdida' });
  });
  
  socket.on('close', () => {
    sendToClient(sessionId, { type: 'error', message: 'Conexión cerrada' });
    eventStreams.delete(sessionId);
  });
}

function messageStreamHandler(req, res) {
  const sessionId = req.headers.authorization?.replace('Bearer ', '');
  if (!sessionId) {
    return res.status(401).json({ success: false, message: 'No session ID' });
  }

  const session = sessions.get(sessionId);
  if (!session || !session.socket) {
    return res.status(401).json({ success: false, message: 'Invalid session' });
  }

  // Configurar SSE
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('Access-Control-Allow-Origin', '*');
  
  // Enviar mensaje inicial
  res.write(`data: ${JSON.stringify({ type: 'connected' })}\n\n`);
  
  // Agregar este stream a la lista
  addEventStream(sessionId, res);
  
  // Configurar listener de mensajes TCP si no está configurado
  if (!session.listenerSetup) {
    setupTCPMessageListener(sessionId, session.socket);
    session.listenerSetup = true;
  }
  
  // Mantener conexión viva
  const keepAlive = setInterval(() => {
    try {
      res.write(`: keepalive\n\n`);
    } catch (err) {
      clearInterval(keepAlive);
      removeEventStream(sessionId, res);
    }
  }, 30000);
  
  req.on('close', () => {
    clearInterval(keepAlive);
    removeEventStream(sessionId, res);
  });
}

module.exports = { messageStreamHandler, sendToClient };


const net = require('net');
const { sessions } = require('./login');

const TCP_SERVER_HOST = 'localhost';
const TCP_SERVER_PORT = 12345;

function readTCPMessage(clientSocket) {
  return new Promise((resolve, reject) => {
    let dataBuffer = "";

    const onData = (data) => {
      dataBuffer += data.toString();
      if (dataBuffer.endsWith('\n')) {
        clientSocket.removeListener('data', onData);
        try {
          const json = JSON.parse(dataBuffer.trim());
          resolve(json);
        } catch (e) {
          reject(new Error('Failed parsing TCP response: ' + dataBuffer));
        }
      }
    };

    clientSocket.on('data', onData);
    
    setTimeout(() => {
      clientSocket.removeListener('data', onData);
      reject(new Error('Timeout waiting for TCP response'));
    }, 5000);
  });
}

function sendTCPMessage(clientSocket, messageJSON) {
  return new Promise((resolve, reject) => {
    clientSocket.write(JSON.stringify(messageJSON) + '\n');
    readTCPMessage(clientSocket)
      .then(resolve)
      .catch(reject);
  });
}

async function createGroupHandler(req, res) {
  const sessionId = req.headers.authorization?.replace('Bearer ', '');
  if (!sessionId) {
    return res.status(401).json({ success: false, message: 'No session ID' });
  }

  const session = sessions.get(sessionId);
  if (!session || !session.socket) {
    return res.status(401).json({ success: false, message: 'Invalid session' });
  }

  const { group_name } = req.body;
  if (!group_name) {
    return res.status(400).json({ success: false, message: 'Missing group name' });
  }

  try {
    // Enviar comando al servidor TCP
    session.socket.write(JSON.stringify({
      command: 'create_group',
      group_name
    }) + '\n');
    
    // El servidor enviará la respuesta a través del stream de mensajes
    // Por ahora respondemos inmediatamente
    res.json({ success: true, message: 'Comando enviado' });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
}

async function getOnlineUsersHandler(req, res) {
  const sessionId = req.headers.authorization?.replace('Bearer ', '');
  if (!sessionId) {
    return res.status(401).json({ success: false, message: 'No session ID' });
  }

  const session = sessions.get(sessionId);
  if (!session) {
    return res.status(401).json({ success: false, message: 'Invalid session' });
  }

  // Obtener lista de usuarios de las sesiones activas
  const users = Array.from(sessions.values())
    .map(s => s.username)
    .filter(u => u);
  
  res.json({ success: true, users });
}

module.exports = { createGroupHandler, getOnlineUsersHandler };


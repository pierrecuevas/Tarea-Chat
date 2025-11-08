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

async function sendMessageHandler(req, res) {
  const sessionId = req.headers.authorization?.replace('Bearer ', '');
  if (!sessionId) {
    return res.status(401).json({ success: false, message: 'No session ID' });
  }

  const session = sessions.get(sessionId);
  if (!session || !session.socket) {
    return res.status(401).json({ success: false, message: 'Invalid session' });
  }

  const { command, text, recipient, group_name } = req.body;
  
  try {
    const message = { command, text };
    if (recipient) message.recipient = recipient;
    if (group_name) message.group_name = group_name;
    
    // Enviar mensaje al servidor TCP
    session.socket.write(JSON.stringify(message) + '\n');
    // No esperamos respuesta, el servidor enviará el mensaje a través del stream
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, message: err.message });
  }
}

module.exports = { sendMessageHandler };


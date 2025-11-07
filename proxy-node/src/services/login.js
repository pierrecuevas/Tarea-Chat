const net = require('net');

const TCP_SERVER_HOST = 'localhost';
const TCP_SERVER_PORT = 12345;

const sessions = new Map();

function generateSessionId() {
  return Math.random().toString(36).substring(2);
}

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
    
    // Timeout para evitar esperar indefinidamente
    setTimeout(() => {
      clientSocket.removeListener('data', onData);
      reject(new Error('Timeout waiting for TCP response'));
    }, 5000);
  });
}

function sendTCPMessage(clientSocket, messageJSON) {
  return new Promise((resolve, reject) => {
    clientSocket.write(JSON.stringify(messageJSON) + '\n');
    // Leer la respuesta después de enviar
    readTCPMessage(clientSocket)
      .then(resolve)
      .catch(reject);
  });
}

async function loginHandler(req, res) {
  const { username, password } = req.body;
  if (!username || !password) return res.status(400).json({ success: false, message: 'Missing username or password' });

  const client = new net.Socket();
  let responseSent = false;
  client.connect(TCP_SERVER_PORT, TCP_SERVER_HOST, async () => {
    try {
      // Primero leer el mensaje inicial del servidor (auth_required)
      await readTCPMessage(client);
      
      // Luego enviar el comando de login y leer la respuesta
      const response = await sendTCPMessage(client, { command: 'login', username, password });
       if (!responseSent) {
        responseSent = true;
        if (response.status === 'ok') {
          const sessionId = generateSessionId();
          sessions.set(sessionId, { socket: client, username, listenerSetup: false });
          res.json({ success: true, sessionId, message: response.message });
        } else {
          client.destroy();
          res.status(401).json({ success: false, message: response.message });
        }
      }
    } catch (err) {
      client.destroy();
      res.status(500).json({ success: false, message: err.message });
    }
  });

  client.on('error', (err) => {
    if (!responseSent) {
      responseSent = true;
      res.status(500).json({ success: false, message: 'TCP connection error: ' + err.message });
    }
  });
}

async function registerHandler(req, res) {
  const { username, password } = req.body;
  if (!username || !password) return res.status(400).json({ success: false, message: 'Missing username or password' });

  const client = new net.Socket();
  let responseSent = false;
  
  client.connect(TCP_SERVER_PORT, TCP_SERVER_HOST, async () => {
    try {
      // Primero leer el mensaje inicial del servidor (auth_required)
      await readTCPMessage(client);
      
      // Luego enviar el comando de register y leer la respuesta
      const response = await sendTCPMessage(client, { command: 'register', username, password });
      if (!responseSent) {
        responseSent = true;
        if (response.status === 'ok') {
          const sessionId = generateSessionId();
          sessions.set(sessionId, { socket: client, username, listenerSetup: false });
          res.json({ success: true, sessionId, message: response.message });
        } else {
          client.destroy();
          res.status(400).json({ success: false, message: response.message });
        }
      }
    } catch (err) {
      client.destroy();
      res.status(500).json({ success: false, message: err.message });
    }
  });

  client.on('error', (err) => {
    if (!responseSent) {
      responseSent = true;
      res.status(500).json({ success: false, message: 'TCP connection error: ' + err.message });
    }
  });
}

module.exports = { loginHandler, registerHandler, sessions };

const net = require('net');
const { createTCPParser } = require('../utils/tcpParser');

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
  
  if (!username || !password) {
    return res.status(400).json({ success: false, message: 'Faltan usuario o contraseña' });
  }

  const client = new net.Socket();
  const parser = createTCPParser();
  let responseSent = false;
  
  // Timeout general para toda la conexión
  const connectionTimeout = setTimeout(() => {
    if (!responseSent) {
      responseSent = true;
      client.destroy();
      console.error(`[LOGIN] Timeout en conexión TCP para ${username}`);
      res.status(500).json({ success: false, message: 'Tiempo de conexión agotado' });
    }
  }, 10000);

  client.connect(TCP_SERVER_PORT, TCP_SERVER_HOST, async () => {
    try {
      console.log(`[LOGIN] Conectando ${username}...`);
      
      // Leer mensaje inicial del servidor
      const initMsg = await parser.readMessage(client, 3000);
      console.log(`[LOGIN] Mensaje inicial recibido:`, initMsg);
      
      // Enviar credenciales
      client.write(JSON.stringify({ command: 'login', username, password }) + '\n');
      console.log(`[LOGIN] Credenciales enviadas para ${username}`);
      
      // Leer respuesta de login
      const response = await parser.readMessage(client, 5000);
      console.log(`[LOGIN] Respuesta recibida:`, response);
      
      if (!responseSent) {
        responseSent = true;
        clearTimeout(connectionTimeout);
        
        // Validar respuesta
        if (response.status === 'ok') {
          const sessionId = generateSessionId();
          sessions.set(sessionId, { socket: client, username, listenerSetup: false });
          console.log(`[LOGIN] ✓ Login exitoso para ${username}, sesión: ${sessionId}`);
          res.json({ 
            success: true, 
            sessionId, 
            message: response.message 
          });
        } else {
          client.destroy();
          sessions.delete(sessionId);
          console.log(`[LOGIN] ✗ Login rechazado para ${username}: ${response.message}`);
          res.status(401).json({ 
            success: false, 
            message: response.message || 'Credenciales inválidas' 
          });
        }
      }
    } catch (err) {
      if (!responseSent) {
        responseSent = true;
        clearTimeout(connectionTimeout);
        client.destroy();
        console.error(`[LOGIN] Error durante login de ${username}:`, err.message);
        res.status(500).json({ 
          success: false, 
          message: 'Error en servidor: ' + err.message 
        });
      }
    }
  });

  client.on('error', (err) => {
    if (!responseSent) {
      responseSent = true;
      clearTimeout(connectionTimeout);
      console.error(`[LOGIN] Error TCP para ${username}:`, err.message);
      res.status(500).json({ 
        success: false, 
        message: 'Error de conexión TCP: ' + err.message 
      });
    }
  });

  client.on('close', () => {
    console.log(`[LOGIN] Conexión TCP cerrada para ${username}`);
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

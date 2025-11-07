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

// User endpoints
app.get('/online-users', groupsService.getOnlineUsersHandler);

const port = 3000;
app.listen(port, () => {
  console.log(`Proxy started on http://localhost:${port}`);
});

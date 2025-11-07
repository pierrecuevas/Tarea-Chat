import LoginForm from './components/LoginForm.js';
import Header from './components/Header.js';
import ChatList from './components/ChatList.js';
import OnlineUsersList from './components/OnlineUsersList.js';
import ChatWindow from './components/ChatWindow.js';
import MessageForm from './components/MessageForm.js';
import { createGroup, sendMessage, getOnlineUsers } from './api/proxyClient.js';
import { connectMessageStream } from './api/messageStream.js';

// Containers
const loginContainer = document.getElementById('loginContainer');
const appContainer = document.getElementById('appContainer');
const headerContainer = document.getElementById('headerContainer');
const chatListContainer = document.getElementById('chatListContainer');
const onlineUsersContainer = document.getElementById('onlineUsersContainer');
const chatWindowContainer = document.getElementById('chatWindowContainer');
const messageFormContainer = document.getElementById('messageFormContainer');

// State
let currentUsername = null;
let sessionId = null;
let chatWindow = null;
let messageForm = null;
let chatList = null;
let onlineUsersList = null;
let header = null;
let messageStream = null;
let currentChatType = 'general';
let currentChatName = 'General';
let processingHistory = false;
let historyBuffer = [];

// Asegurar que el login esté visible al inicio
loginContainer.style.display = 'block';
appContainer.style.display = 'none';

// Crear formulario login
new LoginForm(loginContainer, async (username) => {
  currentUsername = username;
  window.currentUsername = username;
  sessionId = localStorage.getItem('sessionId');
  
  // Ocultar login y mostrar app
  loginContainer.style.display = 'none';
  appContainer.style.display = 'flex';
  
  // Inicializar componentes
  initializeApp();
  
  // Conectar al stream de mensajes
  startMessageStream();
  
  // Iniciar polling de usuarios online
  startOnlineUsersPolling();
});

function initializeApp() {
  // Header
  header = new Header(headerContainer, currentUsername);
  
  // Chat List (izquierda)
  chatList = new ChatList(chatListContainer, (type, name) => {
    currentChatType = type;
    currentChatName = name;
    
    // Limpiar mensajes actuales y cargar historial del chat seleccionado
    chatWindow.chatWindow.innerHTML = '';
    loadChatHistory(type, name);
    
    chatWindow.setCurrentChat(type, name);
    messageForm.setCurrentChat({ type, name });
    
    // Si es un chat privado nuevo, agregarlo a la lista
    if (type === 'private') {
      chatList.addPrivateChat(name);
    }
  });
  
  // Conectar el formulario de grupo con la función de crear
  const groupForm = chatListContainer.querySelector('#groupForm');
  if (groupForm) {
    groupForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const groupName = document.getElementById('groupNameInput').value.trim();
      if (groupName) {
        try {
          await createGroup(groupName, sessionId);
          // El grupo se agregará cuando llegue la notificación del servidor
          const modal = chatListContainer.querySelector('#groupFormModal');
          if (modal) modal.style.display = 'none';
          groupForm.reset();
        } catch (error) {
          console.error('Error creando grupo:', error);
          chatWindow.addSystemMessage('Error al crear el grupo');
        }
      }
    });
  }
  
  // Online Users List (derecha)
  onlineUsersList = new OnlineUsersList(onlineUsersContainer, (username) => {
    // Al hacer clic en un usuario, abrir chat privado
    chatList.addPrivateChat(username);
    chatList.selectChat('private', username);
    chatWindow.setCurrentChat('private', username);
    messageForm.setCurrentChat({ type: 'private', name: username });
  });
  
  // Chat Window (centro)
  chatWindow = new ChatWindow(chatWindowContainer);
  
  // Message Form (centro, abajo)
  messageForm = new MessageForm(messageFormContainer, null);
  messageForm.onMessageSent = async (message) => {
    try {
      await sendMessage(message, sessionId);
      // El mensaje se mostrará cuando llegue del servidor a través del stream
    } catch (error) {
      console.error('Error enviando mensaje:', error);
      chatWindow.addSystemMessage('Error al enviar el mensaje');
    }
  };
  
  // Seleccionar chat General por defecto
  setTimeout(() => {
    currentChatType = 'general';
    currentChatName = 'General';
    chatWindow.setCurrentChat('general', 'General');
    messageForm.setCurrentChat({ type: 'general', name: 'General' });
    chatList.selectChat('general', 'General');
  }, 100);
}

function startMessageStream() {
  connectMessageStream(sessionId, (message) => {
    processIncomingMessage(message);
  });
}

// Almacenar mensajes por chat para el historial
const messageHistory = {
  general: [],
  private: {},
  group: {}
};

function processIncomingMessage(message) {
  console.log('Mensaje recibido:', message);
  
  // Procesar según el tipo de mensaje
  if (message.type === 'notification') {
    const notificationText = message.message || '';
    
    // Detectar si es parte del historial inicial
    if (notificationText.includes('--- Últimos 10 mensajes del chat general')) {
      processingHistory = true;
      // Limpiar historial anterior y preparar para nuevo historial
      messageHistory.general = [];
      chatWindow.addSystemMessage(notificationText);
    } else if (notificationText.includes('--- Conversación con')) {
      processingHistory = true;
      const match = notificationText.match(/Conversación con (\w+)/);
      if (match) {
        const partner = match[1];
        chatList.addPrivateChat(partner);
        if (!messageHistory.private[partner]) {
          messageHistory.private[partner] = [];
        }
      }
      chatWindow.addSystemMessage(notificationText);
    } else if (notificationText.includes('--- Mensajes de')) {
      processingHistory = true;
      const match = notificationText.match(/Mensajes de (.+?) ---/);
      if (match) {
        const groupName = match[1].trim();
        if (!chatListContainer.querySelector(`[data-type="group"][data-name="${groupName}"]`)) {
          chatList.addGroupChat(groupName);
        }
        if (!messageHistory.group[groupName]) {
          messageHistory.group[groupName] = [];
        }
      }
      chatWindow.addSystemMessage(notificationText);
    } else if (notificationText.includes('¡Bienvenido!') || notificationText.includes('------------------------------------')) {
      processingHistory = false;
      chatWindow.addSystemMessage(notificationText);
    } else {
      // Detectar creación de grupo exitosa
      if (notificationText.includes('creado') || notificationText.includes('Grupo')) {
        const groupMatch = notificationText.match(/grupo ['"](.+?)['"]/i) || 
                          notificationText.match(/Grupo ['"](.+?)['"]/i) ||
                          notificationText.match(/grupo (.+?) (creado|se ha)/i);
        if (groupMatch) {
          const groupName = groupMatch[1].trim();
          if (!chatListContainer.querySelector(`[data-type="group"][data-name="${groupName}"]`)) {
            chatList.addGroupChat(groupName);
          }
        }
      }
      chatWindow.addSystemMessage(notificationText);
    }
  } else if (message.type === 'chat') {
    const subType = message.sub_type;
    const sender = message.sender;
    const text = message.text;
    const group = message.group;
    
    // Guardar mensaje en el historial correspondiente
    if (subType === 'public') {
      messageHistory.general.push({ sender, text, sub_type: subType });
      // Mostrar si estamos en General
      if (currentChatType === 'general' && currentChatName === 'General') {
        chatWindow.addMessage({ sender, text, sub_type: subType });
      }
    } else if (subType === 'private_from' || subType === 'private_to') {
      const otherUser = subType === 'private_from' ? sender : message.party;
      chatList.addPrivateChat(otherUser);
      
      if (!messageHistory.private[otherUser]) {
        messageHistory.private[otherUser] = [];
      }
      messageHistory.private[otherUser].push({ sender, text, sub_type: subType });
      
      // Mostrar si estamos en ese chat privado
      if (currentChatType === 'private' && currentChatName === otherUser) {
        chatWindow.addMessage({ sender, text, sub_type: subType });
      }
    } else if (subType === 'group') {
      if (group) {
        if (!chatListContainer.querySelector(`[data-type="group"][data-name="${group}"]`)) {
          chatList.addGroupChat(group);
        }
        
        if (!messageHistory.group[group]) {
          messageHistory.group[group] = [];
        }
        messageHistory.group[group].push({ sender, text, sub_type: subType, group });
        
        // Mostrar si estamos en ese grupo
        if (currentChatType === 'group' && currentChatName === group) {
          chatWindow.addMessage({ sender, text, sub_type: subType, group });
        }
      }
    }
    
    // Si estamos procesando historial, mostrar todos los mensajes del historial
    if (processingHistory) {
      if (subType === 'public') {
        // Mostrar mensajes públicos en General si estamos viendo General
        if (currentChatType === 'general' && currentChatName === 'General') {
          chatWindow.addMessage({ sender, text, sub_type: subType });
        }
      } else if (subType?.includes('private')) {
        const otherUser = subType === 'private_from' ? sender : message.party;
        // Solo mostrar si estamos viendo ese chat privado
        if (currentChatType === 'private' && currentChatName === otherUser) {
          chatWindow.addMessage({ sender, text, sub_type: subType });
        }
      } else if (subType === 'group' && group) {
        // Solo mostrar si estamos viendo ese grupo
        if (currentChatType === 'group' && currentChatName === group) {
          chatWindow.addMessage({ sender, text, sub_type: subType, group });
        }
      }
    }
  } else if (message.type === 'connected') {
    console.log('Conectado al stream de mensajes');
  } else if (message.type === 'error') {
    console.error('Error en stream:', message.message);
    chatWindow.addSystemMessage('Error de conexión: ' + message.message);
  }
}

// Función para cargar historial cuando se cambia de chat
function loadChatHistory(type, name) {
  // Limpiar mensajes actuales y cargar historial
  const welcome = chatWindow.chatWindow.querySelector('.welcome-message');
  if (welcome) {
    welcome.remove();
  }
  
  // Cargar mensajes del historial según el tipo
  if (type === 'general') {
    messageHistory.general.forEach(msg => {
      chatWindow.addMessage(msg);
    });
  } else if (type === 'private') {
    if (messageHistory.private[name]) {
      messageHistory.private[name].forEach(msg => {
        chatWindow.addMessage(msg);
      });
    }
  } else if (type === 'group') {
    if (messageHistory.group[name]) {
      messageHistory.group[name].forEach(msg => {
        chatWindow.addMessage(msg);
      });
    }
  }
}

async function startOnlineUsersPolling() {
  const updateUsers = async () => {
    try {
      const response = await getOnlineUsers(sessionId);
      if (response && response.users) {
        onlineUsersList.updateUsers(response.users, currentUsername);
      }
    } catch (error) {
      console.error('Error obteniendo usuarios online:', error);
    }
  };
  
  // Actualizar inmediatamente
  await updateUsers();
  
  // Actualizar cada 5 segundos
  setInterval(updateUsers, 5000);
}

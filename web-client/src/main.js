import LoginForm from './components/LoginForm.js';
import Header from './components/Header.js';
import ChatList from './components/ChatList.js';
import OnlineUsersList from './components/OnlineUsersList.js';
import ChatWindow from './components/ChatWindow.js';
import MessageForm from './components/MessageForm.js';
import { createGroup, sendMessage, getOnlineUsers, getAllUsers, getGroupMembers, inviteToGroup } from './api/ProxyClient.js';
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
let allUsersList = [];
let invitedUsers = [];

// Historial de mensajes
const messageHistory = {
  general: [],
  private: {},
  group: {}
};

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
    
    // Limpiar y cargar historial del chat seleccionado
    chatWindow.chatWindow.innerHTML = '';
    loadChatHistory(type, name);
    
    chatWindow.setCurrentChat(type, name);
    messageForm.setCurrentChat({ type, name });
    
    // Si es un chat privado nuevo, agregarlo a la lista
    if (type === 'private') {
      chatList.addPrivateChat(name);
    }
  });
  
  // Online Users List (derecha)
  onlineUsersList = new OnlineUsersList(onlineUsersContainer, (username) => {
    // Al hacer clic en un usuario, abrir chat privado
    currentChatType = 'private';
    currentChatName = username;
    chatList.addPrivateChat(username);
    chatList.selectChat('private', username);
    chatWindow.chatWindow.innerHTML = '';
    loadChatHistory('private', username);
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
    } catch (error) {
      console.error('Error enviando mensaje:', error);
      chatWindow.addSystemMessage('Error al enviar el mensaje');
    }
  };
  
  // Configurar autocompletado en formulario de grupo
  setupUserAutocomplete();
  
  // Conectar formulario de grupo
  const groupForm = chatListContainer.querySelector('#groupForm');
  if (groupForm) {
    groupForm.addEventListener('submit', handleCreateGroup);
  }
  
  // Seleccionar chat General por defecto
  setTimeout(() => {
    currentChatType = 'general';
    currentChatName = 'General';
    chatWindow.setCurrentChat('general', 'General');
    messageForm.setCurrentChat({ type: 'general', name: 'General' });
    chatList.selectChat('general', 'General');
  }, 100);
}

async function handleCreateGroup(e) {
  e.preventDefault();
  const groupName = document.getElementById('groupNameInput').value.trim();
  
  if (!groupName) {
    alert('Por favor ingresa un nombre para el grupo');
    return;
  }
  
  try {
    await createGroup(groupName, sessionId);
    
    // Invitar usuarios si hay alguno
    if (invitedUsers.length > 0) {
      setTimeout(async () => {
        for (const user of invitedUsers) {
          try {
            await inviteToGroup(groupName, user, sessionId);
          } catch (err) {
            console.error(`Error invitando a ${user}:`, err);
          }
        }
      }, 500);
    }
    
    // Cerrar modal y limpiar
    const modal = chatListContainer.querySelector('#groupFormModal');
    if (modal) modal.style.display = 'none';
    this.reset();
    invitedUsers = [];
    updateInvitedUsersList();
    
  } catch (error) {
    console.error('Error creando grupo:', error);
    alert('Error al crear el grupo');
  }
}

function startMessageStream() {
  connectMessageStream(sessionId, (message) => {
    processIncomingMessage(message);
  });
}

function processIncomingMessage(message) {
  console.log('Mensaje recibido:', message);
  
  if (message.type === 'notification') {
    const notificationText = message.message || '';
    
    // Detectar historial del general
    if (notificationText.includes('--- Últimos')) {
      chatWindow.addSystemMessage(notificationText);
    }
    // Otros mensajes de notificación
    else {
      chatWindow.addSystemMessage(notificationText);
    }
    
  } else if (message.type === 'chat') {
    const { sender, text, sub_type, sent_at, group, party } = message;
    
    const messageWithTime = { 
      sender, 
      text, 
      sub_type,
      sent_at: sent_at || new Date().toISOString(),
      group,
      party
    };
    
    // Guardar en historial y ordenar por fecha
    if (sub_type === 'public') {
      messageHistory.general.push(messageWithTime);
      messageHistory.general.sort((a, b) => new Date(a.sent_at) - new Date(b.sent_at));
      
      if (currentChatType === 'general') {
        chatWindow.addMessage(messageWithTime);
      }
      
    } else if (sub_type === 'private_from' || sub_type === 'private_to') {
      const otherUser = sub_type === 'private_from' ? sender : party;
      chatList.addPrivateChat(otherUser);
      
      if (!messageHistory.private[otherUser]) {
        messageHistory.private[otherUser] = [];
      }
      messageHistory.private[otherUser].push(messageWithTime);
      messageHistory.private[otherUser].sort((a, b) => new Date(a.sent_at) - new Date(b.sent_at));
      
      if (currentChatType === 'private' && currentChatName === otherUser) {
        chatWindow.addMessage(messageWithTime);
      }
      
    } else if (sub_type === 'group' && group) {
      if (!messageHistory.group[group]) {
        messageHistory.group[group] = [];
      }
      messageHistory.group[group].push(messageWithTime);
      messageHistory.group[group].sort((a, b) => new Date(a.sent_at) - new Date(b.sent_at));
      
      if (!chatListContainer.querySelector(`[data-type="group"][data-name="${group}"]`)) {
        chatList.addGroupChat(group);
      }
      
      if (currentChatType === 'group' && currentChatName === group) {
        chatWindow.addMessage(messageWithTime);
      }
    }
    
  } else if (message.type === 'all_users') {
    if (message.users && Array.isArray(message.users)) {
      allUsersList = message.users;
    }
  }
}

function loadChatHistory(type, name) {
  if (type === 'general' && messageHistory.general.length > 0) {
    messageHistory.general.forEach(msg => {
      chatWindow.addMessage(msg);
    });
  } else if (type === 'private' && messageHistory.private[name]) {
    messageHistory.private[name].forEach(msg => {
      chatWindow.addMessage(msg);
    });
  } else if (type === 'group' && messageHistory.group[name]) {
    messageHistory.group[name].forEach(msg => {
      chatWindow.addMessage(msg);
    });
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
  
  await updateUsers();
  setInterval(updateUsers, 5000);
}

function setupUserAutocomplete() {
  const inviteInput = document.getElementById('inviteUserInput');
  const suggestionsDiv = document.getElementById('userSuggestions');
  
  if (!inviteInput || !suggestionsDiv) return;
  
  inviteInput.addEventListener('input', (e) => {
    const query = e.target.value.trim().toLowerCase();
    
    if (query.length === 0) {
      suggestionsDiv.innerHTML = '';
      suggestionsDiv.style.display = 'none';
      return;
    }
    
    if (!allUsersList || allUsersList.length === 0) {
      suggestionsDiv.innerHTML = '<div class="suggestion-item">No hay usuarios disponibles</div>';
      suggestionsDiv.style.display = 'block';
      return;
    }
    
    const filtered = allUsersList.filter(user => 
      user.toLowerCase().includes(query) && 
      user !== currentUsername &&
      !invitedUsers.includes(user)
    );
    
    suggestionsDiv.innerHTML = '';
    filtered.slice(0, 5).forEach(username => {
      const item = document.createElement('div');
      item.className = 'suggestion-item';
      item.textContent = username;
      item.addEventListener('click', () => {
        if (!invitedUsers.includes(username)) {
          invitedUsers.push(username);
          updateInvitedUsersList();
          inviteInput.value = '';
          suggestionsDiv.innerHTML = '';
        }
      });
      suggestionsDiv.appendChild(item);
    });
    suggestionsDiv.style.display = 'block';
  });
  
  document.addEventListener('click', (e) => {
    if (!inviteInput.contains(e.target) && !suggestionsDiv.contains(e.target)) {
      suggestionsDiv.style.display = 'none';
    }
  });
}

function updateInvitedUsersList() {
  const invitedDiv = document.getElementById('invitedUsers');
  if (!invitedDiv) return;
  
  invitedDiv.innerHTML = '';
  if (invitedUsers.length === 0) return;
  
  invitedUsers.forEach(username => {
    const tag = document.createElement('div');
    tag.className = 'invited-user-tag';
    tag.innerHTML = `
      <span>${username}</span>
      <button type="button" class="remove-invite">&times;</button>
    `;
    tag.querySelector('.remove-invite').addEventListener('click', (e) => {
      e.preventDefault();
      invitedUsers = invitedUsers.filter(u => u !== username);
      updateInvitedUsersList();
    });
    invitedDiv.appendChild(tag);
  });
}
// Detectar cierre de pestaña y desconectar
window.addEventListener('beforeunload', async () => {
  if (sessionId) {
    // Enviar desconexión de forma síncrona (no es async aquí)
    navigator.sendBeacon('http://localhost:3000/disconnect', 
      JSON.stringify({ 
        headers: { 'Authorization': `Bearer ${sessionId}` }
      })
    );
    console.log('Desconexión enviada al cerrar pestaña');
  }
});


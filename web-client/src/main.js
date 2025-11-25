import LoginForm from './components/LoginForm.js';
import Header from './components/Header.js';
import ChatList from './components/ChatList.js';
import OnlineUsersList from './components/OnlineUsersList.js';
import ChatWindow from './components/ChatWindow.js';
import MessageForm from './components/MessageForm.js';
import { createGroup, sendMessage, getOnlineUsers, getAllUsers, getGroupMembers, inviteToGroup, getChatHistory, leaveGroup } from './api/ProxyClient.js';
import { connectMessageStream } from './api/messageStream.js';
import { VoiceChatClient } from './voice-chat.js';
import AudioPlayer from './components/AudioPlayer.js';
import CallControls from './components/CallControls.js';


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
let voiceChatClient = null;
let callControls = null;

// Historial de mensajes
const messageHistory = {
  general: [],
  private: {},
  group: {}
};

// Contadores de mensajes cargados para paginación
const loadedCounts = {
  general: 0,
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
  }, sessionId);

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

  // Configurar callback para cargar más mensajes
  chatWindow.setOnLoadMore(async () => {
    await loadMoreMessages(currentChatType, currentChatName);
  });

  // Configurar callback para salir del grupo
  chatWindow.setOnLeaveGroup(async () => {
    if (currentChatType === 'group') {
      if (confirm(`¿Estás seguro de que quieres salir del grupo "${currentChatName}"?`)) {
        try {
          await leaveGroup(currentChatName, sessionId);
          // Remover el grupo de la lista
          const groupItem = chatListContainer.querySelector(`[data-type="group"][data-name="${currentChatName}"]`);
          if (groupItem) {
            groupItem.remove();
          }
          // Cambiar al chat general
          currentChatType = 'general';
          currentChatName = 'General';
          chatWindow.setCurrentChat('general', 'General');
          messageForm.setCurrentChat({ type: 'general', name: 'General' });
          chatList.selectChat('general', 'General');
          loadChatHistory('general', 'General');
        } catch (error) {
          console.error('Error saliendo del grupo:', error);
          chatWindow.addSystemMessage('Error al salir del grupo');
        }
      }
    }
  });

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

  // Inicializar Voice Chat
  voiceChatClient = new VoiceChatClient();

  // Wait for Ice.js and Chat.js to load before initializing
  const checkIceLoaded = setInterval(async () => {
    if (window.Ice && window.demo) {
      clearInterval(checkIceLoaded);
      const success = await voiceChatClient.initialize();
      if (!success) {
        console.warn('Voice chat initialization failed - voice features may not work');
      }
    }
  }, 100);

  // Timeout after 5 seconds
  setTimeout(() => {
    clearInterval(checkIceLoaded);
    if (!voiceChatClient.proxy) {
      console.error('Ice.js or Chat.js failed to load within 5 seconds');
    }
  }, 5000);


  // Voice message recording (microphone button)
  messageForm.onVoiceRecord = async () => {
    if (!voiceChatClient.isRecording) {
      const recipient = currentChatType === 'private' ? currentChatName :
        currentChatType === 'group' ? currentChatName : 'general';

      await voiceChatClient.startRecordingMessage(recipient);
      const btn = messageFormContainer.querySelector('#voiceButton');
      if (btn) {
        btn.classList.add('recording');
        btn.title = 'Detener grabación';
      }
    } else {
      await voiceChatClient.stopRecordingMessage();
      const btn = messageFormContainer.querySelector('#voiceButton');
      if (btn) {
        btn.classList.remove('recording');
        btn.title = 'Grabar nota de voz';
      }
      chatWindow.addSystemMessage('Nota de voz enviada');
    }
  };

  // Initialize Call Controls
  const callControlsContainer = document.createElement('div');
  callControlsContainer.id = 'callControlsContainer';
  headerContainer.appendChild(callControlsContainer);

  callControls = new CallControls(callControlsContainer);

  callControls.onCallInitiated = async () => {
    if (currentChatType === 'private') {
      await voiceChatClient.startCall(currentChatName);
      callControls.showActiveCall(currentChatName);
    } else {
      chatWindow.addSystemMessage('Las llamadas solo están disponibles en chats privados');
    }
  };

  callControls.onCallEnded = async () => {
    await voiceChatClient.endCall();
  };

  callControls.onCallAccepted = async (caller) => {
    await voiceChatClient.acceptCall(caller);
  };

  callControls.onCallRejected = async (caller) => {
    await voiceChatClient.rejectCall(caller);
  };

  // Configurar autocompletado en formulario de grupo
  setupUserAutocomplete();

  // Conectar formulario de grupo
  const groupForm = chatListContainer.querySelector('#groupForm');
  if (groupForm) {
    groupForm.addEventListener('submit', handleCreateGroup);
  }

  // Cargar grupos del usuario después de que llegue el historial inicial
  setTimeout(async () => {
    try {
      // Solicitar grupos del usuario al servidor
      // Los grupos se cargarán automáticamente cuando lleguen los mensajes del historial
      // Pero también podemos cargarlos explícitamente si es necesario
    } catch (error) {
      console.error('Error cargando grupos:', error);
    }
  }, 2000);

  // Seleccionar chat General por defecto y cargar historial
  setTimeout(() => {
    currentChatType = 'general';
    currentChatName = 'General';
    chatWindow.setCurrentChat('general', 'General');
    messageForm.setCurrentChat({ type: 'general', name: 'General' });
    chatList.selectChat('general', 'General');
    // Esperar un poco para que lleguen los mensajes del historial inicial
    setTimeout(() => {
      loadChatHistory('general', 'General');
    }, 1000);
  }, 500);
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

    // Detectar notificaciones del historial inicial de grupos
    if (notificationText.includes('--- Mensajes de ')) {
      // Extraer el nombre del grupo de la notificación
      const match = notificationText.match(/--- Mensajes de (.+) ---/);
      if (match && match[1]) {
        const groupName = match[1];
        // Agregar el grupo a la lista si no existe
        if (!chatListContainer.querySelector(`[data-type="group"][data-name="${groupName}"]`)) {
          chatList.addGroupChat(groupName);
        }
      }
      return;
    }

    // Detectar notificaciones del historial inicial
    if (notificationText.includes('--- Mensajes') || notificationText.includes('--- Conversación') || notificationText.includes('--- Últimos')) {
      // No mostrar estas notificaciones, solo guardarlas como referencia
      // Los mensajes reales vendrán después
      return;
    }
    // Mostrar otras notificaciones
    else if (!notificationText.includes('¡Bienvenido!') && !notificationText.includes('------------------------------------')) {
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

    // Guardar en historial SIEMPRE, incluso si no está en el chat actual
    if (sub_type === 'public') {
      // Evitar duplicados
      const exists = messageHistory.general.some(m =>
        m.sender === messageWithTime.sender &&
        m.text === messageWithTime.text &&
        m.sent_at === messageWithTime.sent_at
      );
      if (!exists) {
        messageHistory.general.push(messageWithTime);
        messageHistory.general.sort((a, b) => new Date(a.sent_at) - new Date(b.sent_at));
      }

      // Mostrar si estamos en el chat general
      if (currentChatType === 'general' && currentChatName === 'General') {
        chatWindow.addMessage(messageWithTime);
      }

    } else if (sub_type === 'private_from' || sub_type === 'private_to') {
      const otherUser = sub_type === 'private_from' ? sender : party;
      chatList.addPrivateChat(otherUser);

      if (!messageHistory.private[otherUser]) {
        messageHistory.private[otherUser] = [];
      }

      // Evitar duplicados
      const exists = messageHistory.private[otherUser].some(m =>
        m.sender === messageWithTime.sender &&
        m.text === messageWithTime.text &&
        m.sent_at === messageWithTime.sent_at
      );
      if (!exists) {
        messageHistory.private[otherUser].push(messageWithTime);
        messageHistory.private[otherUser].sort((a, b) => new Date(a.sent_at) - new Date(b.sent_at));
      }

      // Mostrar si estamos en ese chat privado
      if (currentChatType === 'private' && currentChatName === otherUser) {
        chatWindow.addMessage(messageWithTime);
      }

    } else if (sub_type === 'group' && group) {
      if (!messageHistory.group[group]) {
        messageHistory.group[group] = [];
      }

      // Evitar duplicados
      const exists = messageHistory.group[group].some(m =>
        m.sender === messageWithTime.sender &&
        m.text === messageWithTime.text &&
        m.sent_at === messageWithTime.sent_at
      );
      if (!exists) {
        messageHistory.group[group].push(messageWithTime);
        messageHistory.group[group].sort((a, b) => new Date(a.sent_at) - new Date(b.sent_at));
      }

      if (!chatListContainer.querySelector(`[data-type="group"][data-name="${group}"]`)) {
        chatList.addGroupChat(group);
      }

      // Mostrar si estamos en ese grupo
      if (currentChatType === 'group' && currentChatName === group) {
        chatWindow.addMessage(messageWithTime);
      }
    }

  } else if (message.type === 'all_users') {
    if (message.users && Array.isArray(message.users)) {
      allUsersList = message.users;
    }
  } else if (message.type === 'chat_history_response') {
    // Los mensajes del historial ya se procesaron en loadChatHistory
    // Este tipo de mensaje se maneja directamente en loadChatHistory
  }
}

async function loadChatHistory(type, name) {
  // Limpiar la ventana de chat primero
  chatWindow.chatWindow.innerHTML = '';

  // Primero mostrar los mensajes que ya tenemos en memoria
  // Los mensajes ya están ordenados cronológicamente (más antiguos primero)
  if (type === 'general' && messageHistory.general.length > 0) {
    messageHistory.general.forEach(msg => {
      chatWindow.addMessage(msg);
    });
    // Actualizar contador
    loadedCounts.general = messageHistory.general.length;
  } else if (type === 'private' && messageHistory.private[name] && messageHistory.private[name].length > 0) {
    messageHistory.private[name].forEach(msg => {
      chatWindow.addMessage(msg);
    });
    // Actualizar contador
    loadedCounts.private[name] = messageHistory.private[name].length;
  } else if (type === 'group' && messageHistory.group[name] && messageHistory.group[name].length > 0) {
    messageHistory.group[name].forEach(msg => {
      chatWindow.addMessage(msg);
    });
    // Actualizar contador
    loadedCounts.group[name] = messageHistory.group[name].length;
  }

  // Si no hay mensajes en memoria, solicitar al servidor
  let shouldLoadMore = false;
  if (type === 'general') {
    shouldLoadMore = messageHistory.general.length === 0;
  } else if (type === 'private') {
    shouldLoadMore = !messageHistory.private[name] || messageHistory.private[name].length === 0;
  } else if (type === 'group') {
    shouldLoadMore = !messageHistory.group[name] || messageHistory.group[name].length === 0;
  }

  if (shouldLoadMore) {
    try {
      // Solicitar los primeros 50 mensajes
      const response = await getChatHistory(type, name, 50, 0, sessionId);
      if (response && response.messages && Array.isArray(response.messages)) {
        // Procesar cada mensaje
        response.messages.forEach(msg => {
          // Solo procesar mensajes de tipo "chat", ignorar notificaciones
          if (msg.type === 'chat') {
            processIncomingMessage(msg);
          }
        });

        // Actualizar contador
        if (type === 'general') {
          loadedCounts.general = response.messages.filter(m => m.type === 'chat').length;
        } else if (type === 'private') {
          loadedCounts.private[name] = response.messages.filter(m => m.type === 'chat').length;
        } else if (type === 'group') {
          loadedCounts.group[name] = response.messages.filter(m => m.type === 'chat').length;
        }

        // Volver a cargar el historial para mostrarlos
        setTimeout(() => {
          chatWindow.chatWindow.innerHTML = '';
          // Mostrar mensajes en orden cronológico (más antiguos primero, más recientes al final)
          if (type === 'general' && messageHistory.general.length > 0) {
            messageHistory.general.forEach(msg => {
              chatWindow.addMessage(msg);
            });
          } else if (type === 'private' && messageHistory.private[name] && messageHistory.private[name].length > 0) {
            messageHistory.private[name].forEach(msg => {
              chatWindow.addMessage(msg);
            });
          } else if (type === 'group' && messageHistory.group[name] && messageHistory.group[name].length > 0) {
            messageHistory.group[name].forEach(msg => {
              chatWindow.addMessage(msg);
            });
          }

          // Mostrar botón de cargar más si hay más mensajes
          if (response.messages.filter(m => m.type === 'chat').length >= 50) {
            chatWindow.showLoadMoreButton();
          }
        }, 200);
      }
    } catch (error) {
      console.error('Error cargando historial:', error);
    }
  }
}


async function loadMoreMessages(type, name) {
  try {
    let currentCount = 0;
    if (type === 'general') {
      currentCount = loadedCounts.general;
    } else if (type === 'private') {
      currentCount = loadedCounts.private[name] || 0;
    } else if (type === 'group') {
      currentCount = loadedCounts.group[name] || 0;
    }

    // Cargar 50 mensajes más
    const response = await getChatHistory(type, name, 50, currentCount, sessionId);
    if (response.messages && Array.isArray(response.messages) && response.messages.length > 0) {
      // Guardar el scroll actual
      const scrollHeight = chatWindow.chatWindow.scrollHeight;
      const scrollTop = chatWindow.chatWindow.scrollTop;

      // Procesar y agregar mensajes al inicio
      response.messages.forEach(msg => {
        if (msg.type === 'chat') {
          processIncomingMessage(msg);

          // Agregar mensaje al inicio de la ventana
          const messageWithTime = {
            sender: msg.sender,
            text: msg.text,
            sub_type: msg.sub_type,
            sent_at: msg.sent_at || new Date().toISOString(),
            group: msg.group,
            party: msg.party
          };
          chatWindow.addMessage(messageWithTime, true);
        }
      });

      // Actualizar contador
      if (type === 'general') {
        loadedCounts.general += response.messages.length;
      } else if (type === 'private') {
        if (!loadedCounts.private[name]) {
          loadedCounts.private[name] = 0;
        }
        loadedCounts.private[name] += response.messages.length;
      } else if (type === 'group') {
        if (!loadedCounts.group[name]) {
          loadedCounts.group[name] = 0;
        }
        loadedCounts.group[name] += response.messages.length;
      }

      // Restaurar scroll para mantener la posición
      const newScrollHeight = chatWindow.chatWindow.scrollHeight;
      chatWindow.chatWindow.scrollTop = scrollTop + (newScrollHeight - scrollHeight);

      // Si no hay más mensajes, ocultar el botón
      if (response.messages.length < 50) {
        chatWindow.hideLoadMoreButton();
      }
    } else {
      // No hay más mensajes
      chatWindow.hideLoadMoreButton();
    }
  } catch (error) {
    console.error('Error cargando más mensajes:', error);
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

let messageStreamConnection = null;

// Detectar cierre de pestaña
window.addEventListener('beforeunload', async () => {
  if (sessionId) {
    try {
      // Cerrar conexión SSE/stream
      if (messageStreamConnection) {
        messageStreamConnection.close();
      }

      // Enviar desconexión al backend
      await fetch('http://localhost:3000/disconnect', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${sessionId}`
        },
        body: JSON.stringify({ username: currentUsername })
      });
    } catch (err) {
      console.error('Error al desconectar:', err);
    }
  }
});


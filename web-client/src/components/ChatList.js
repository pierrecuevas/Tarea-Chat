export default class ChatList {
  constructor(container, onChatSelect) {
    this.container = container;
    this.onChatSelect = onChatSelect;
    this.chats = [];
    this.selectedChat = null;
    this.render();
  }

  render() {
    this.container.innerHTML = `
      <div class="chat-list-header">
        <h2>💬 Chats</h2>
        <button id="newGroupBtn" class="icon-btn" title="Crear grupo">+</button>
      </div>
      <div class="chat-list-content" id="chatListContent">
        <div class="chat-item active" data-type="general" data-name="General">
          <div class="chat-icon">🌐</div>
          <div class="chat-info">
            <div class="chat-name">General</div>
            <div class="chat-preview">Chat público</div>
          </div>
        </div>
      </div>
      <div id="groupFormModal" class="modal">
        <div class="modal-content">
          <span class="close-modal">&times;</span>
          <h3>Crear Nuevo Grupo</h3>
          <form id="groupForm">
            <input type="text" id="groupNameInput" placeholder="Nombre del grupo" required />
            <div class="invite-section">
              <label>Invitar usuarios (opcional):</label>
              <input type="text" id="inviteUserInput" placeholder="Escribe para buscar usuarios..." autocomplete="off" />
              <div id="userSuggestions" class="user-suggestions"></div>
              <div id="invitedUsers" class="invited-users-list"></div>
            </div>
            <button type="submit">Crear</button>
          </form>
        </div>
      </div>
    `;

    this.setupEventListeners();
  }

  setupEventListeners() {
    // Seleccionar chat
    this.container.querySelectorAll('.chat-item').forEach(item => {
      item.addEventListener('click', () => {
        const type = item.dataset.type;
        const name = item.dataset.name;
        this.selectChat(type, name, item);
      });
    });

    // Botón crear grupo
    const newGroupBtn = this.container.querySelector('#newGroupBtn');
    const modal = this.container.querySelector('#groupFormModal');
    const closeModal = this.container.querySelector('.close-modal');
    const groupForm = this.container.querySelector('#groupForm');

    newGroupBtn.addEventListener('click', () => {
      modal.style.display = 'block';
    });

    closeModal.addEventListener('click', () => {
      modal.style.display = 'none';
    });

    window.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.style.display = 'none';
      }
    });

    groupForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const groupName = document.getElementById('groupNameInput').value.trim();
      if (groupName) {
        // El grupo se agregará cuando se cree exitosamente
        // Por ahora solo cerramos el modal
        modal.style.display = 'none';
        groupForm.reset();
      }
    });
  }

  selectChat(type, name, element) {
    // Remover active de todos
    this.container.querySelectorAll('.chat-item').forEach(item => {
      item.classList.remove('active');
    });
    
    // Agregar active al seleccionado
    if (element) {
      element.classList.add('active');
    }
    
    this.selectedChat = { type, name };
    if (this.onChatSelect) {
      this.onChatSelect(type, name);
    }
  }

  addGroupChat(groupName) {
    const chatListContent = this.container.querySelector('#chatListContent');
    const chatItem = document.createElement('div');
    chatItem.className = 'chat-item';
    chatItem.dataset.type = 'group';
    chatItem.dataset.name = groupName;
    chatItem.innerHTML = `
      <div class="chat-icon">👥</div>
      <div class="chat-info">
        <div class="chat-name">${groupName}</div>
        <div class="chat-preview">Grupo</div>
      </div>
    `;
    chatItem.addEventListener('click', () => {
      this.selectChat('group', groupName, chatItem);
    });
    chatListContent.appendChild(chatItem);
  }

  addPrivateChat(username) {
    // Verificar si ya existe
    const existing = this.container.querySelector(`[data-type="private"][data-name="${username}"]`);
    if (existing) return;

    const chatListContent = this.container.querySelector('#chatListContent');
    const chatItem = document.createElement('div');
    chatItem.className = 'chat-item';
    chatItem.dataset.type = 'private';
    chatItem.dataset.name = username;
    chatItem.innerHTML = `
      <div class="chat-icon">👤</div>
      <div class="chat-info">
        <div class="chat-name">${username}</div>
        <div class="chat-preview">Chat privado</div>
      </div>
    `;
    chatItem.addEventListener('click', () => {
      this.selectChat('private', username, chatItem);
    });
    chatListContent.appendChild(chatItem);
  }

  getSelectedChat() {
    return this.selectedChat;
  }
}


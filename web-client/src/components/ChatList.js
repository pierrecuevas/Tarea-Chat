import { createGroup, getAllUsers, inviteToGroup } from '../api/ProxyClient.js';

export default class ChatList {
  constructor(container, onChatSelect, sessionId) {
    this.container = container;
    this.onChatSelect = onChatSelect;
    this.sessionId = sessionId;
    this.selectedChat = null;
    this.allUsers = [];
    this.invitedUsers = [];
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
      <div id="groupFormModal" class="modal" style="display: none;">
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
          <div id="groupStatus" style="margin-top: 12px; color: #ff6b6b; text-align: center;"></div>
        </div>
      </div>
    `;
    this.setupEventListeners();
  }

  async setupEventListeners() {
    this.container.querySelectorAll('.chat-item').forEach(item => {
      item.addEventListener('click', () => {
        const type = item.dataset.type;
        const name = item.dataset.name;
        this.selectChat(type, name, item);
      });
    });

    const newGroupBtn = this.container.querySelector('#newGroupBtn');
    const modal = this.container.querySelector('#groupFormModal');
    const closeModal = this.container.querySelector('.close-modal');
    const groupForm = this.container.querySelector('#groupForm');
    const groupStatus = this.container.querySelector('#groupStatus');

    newGroupBtn.addEventListener('click', async () => {
      modal.style.display = 'block';
      groupStatus.textContent = '';
      this.invitedUsers = [];
      this.container.querySelector('#groupNameInput').value = '';
      this.container.querySelector('#inviteUserInput').value = '';
      this.updateInvitedUsersList();

      // Fetch user list for suggestions
      const response = await getAllUsers(this.sessionId);
      this.allUsers = response.users || [];
      this.setupUserAutocomplete();
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
      const groupName = this.container.querySelector('#groupNameInput').value.trim();
      if (!groupName) {
        groupStatus.textContent = 'Por favor ingresa un nombre para el grupo';
        return;
      }
      try {
        groupStatus.textContent = 'Creando grupo...';
        await createGroup(groupName, this.sessionId);
        for (const user of this.invitedUsers) {
          await inviteToGroup(groupName, user, this.sessionId);
        }
        this.addGroupChat(groupName);
        modal.style.display = 'none';
        groupForm.reset();
        this.invitedUsers = [];
        this.updateInvitedUsersList();
        groupStatus.textContent = '';
      } catch (err) {
        groupStatus.textContent = `Error: ${err.message}`;
      }
    });
  }

  setupUserAutocomplete() {
    const inviteInput = this.container.querySelector('#inviteUserInput');
    const suggestionsDiv = this.container.querySelector('#userSuggestions');
    
    if (!inviteInput || !suggestionsDiv) return;

    // Remover listeners anteriores si existen
    const newInviteInput = inviteInput.cloneNode(true);
    inviteInput.parentNode.replaceChild(newInviteInput, inviteInput);
    const newInviteInputRef = newInviteInput;

    newInviteInputRef.addEventListener('input', (e) => {
      const query = e.target.value.trim().toLowerCase();
      if (query.length === 0) {
        suggestionsDiv.innerHTML = '';
        suggestionsDiv.style.display = 'none';
        return;
      }
      if (!this.allUsers || this.allUsers.length === 0) {
        suggestionsDiv.innerHTML = '<div class="suggestion-item">No hay usuarios disponibles</div>';
        suggestionsDiv.style.display = 'block';
        return;
      }
      const filtered = this.allUsers.filter(user =>
        user.toLowerCase().includes(query) &&
        !this.invitedUsers.includes(user) &&
        user !== window.currentUsername
      );
      suggestionsDiv.innerHTML = '';
      if (filtered.length === 0) {
        suggestionsDiv.innerHTML = '<div class="suggestion-item">No se encontraron usuarios</div>';
        suggestionsDiv.style.display = 'block';
        return;
      }
      filtered.slice(0, 5).forEach(username => {
        const item = document.createElement('div');
        item.className = 'suggestion-item';
        item.textContent = username;
        item.style.cursor = 'pointer';
        item.addEventListener('click', () => {
          if (!this.invitedUsers.includes(username)) {
            this.invitedUsers.push(username);
            this.updateInvitedUsersList();
            newInviteInputRef.value = '';
            suggestionsDiv.innerHTML = '';
            suggestionsDiv.style.display = 'none';
          }
        });
        suggestionsDiv.appendChild(item);
      });
      suggestionsDiv.style.display = 'block';
    });

    document.addEventListener('click', (e) => {
      if (!newInviteInputRef.contains(e.target) && !suggestionsDiv.contains(e.target)) {
        suggestionsDiv.style.display = 'none';
      }
    });
  }

  updateInvitedUsersList() {
    const invitedDiv = this.container.querySelector('#invitedUsers');
    if (!invitedDiv) return;

    invitedDiv.innerHTML = '';
    if (this.invitedUsers.length === 0) return;

    this.invitedUsers.forEach(username => {
      const tag = document.createElement('div');
      tag.className = 'invited-user-tag';
      tag.innerHTML = `
        <span>${username}</span>
        <button type="button" class="remove-invite">&times;</button>
      `;
      tag.querySelector('.remove-invite').addEventListener('click', (e) => {
        e.preventDefault();
        this.invitedUsers = this.invitedUsers.filter(u => u !== username);
        this.updateInvitedUsersList();
      });
      invitedDiv.appendChild(tag);
    });
  }

  selectChat(type, name, element) {
    this.container.querySelectorAll('.chat-item').forEach(item => {
      item.classList.remove('active');
    });
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

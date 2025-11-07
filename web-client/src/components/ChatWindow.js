export default class ChatWindow {
  constructor(container) {
    this.container = container;
    this.currentChat = null;
    this.render();
  }

  render() {
    this.container.innerHTML = `
      <div class="chat-window-header" id="chatHeader">
        <div class="chat-title">
          <span class="chat-title-icon">💬</span>
          <span id="chatTitle">Selecciona un chat</span>
        </div>
      </div>
      <div id="chatWindow" class="chat-messages">
        <div class="welcome-message">
          <div class="welcome-icon">👋</div>
          <p>¡Bienvenido al Chat Futuro!</p>
          <p class="welcome-subtitle">Selecciona un chat para comenzar</p>
        </div>
      </div>
    `;
    this.chatWindow = this.container.querySelector('#chatWindow');
    this.chatTitle = this.container.querySelector('#chatTitle');
  }

  setCurrentChat(type, name) {
    this.currentChat = { type, name, username: null };
    const icon = type === 'group' ? '👥' : type === 'private' ? '👤' : '🌐';
    this.chatTitle.textContent = name;
    this.chatTitle.parentElement.querySelector('.chat-title-icon').textContent = icon;
    
    // NO limpiar mensajes, solo actualizar el título
    // Los mensajes se mantienen para que el usuario pueda ver el historial
  }

  addMessage(message) {
    // Remover mensaje de bienvenida si existe
    const welcome = this.chatWindow.querySelector('.welcome-message');
    if (welcome) {
      welcome.remove();
    }

    const msgElem = document.createElement('div');
    msgElem.className = 'message';
    
    // Determinar si el mensaje es propio
    // Para mensajes públicos, comparar el sender con el usuario actual
    // Para mensajes privados, usar sub_type 'private_to'
    // Para mensajes de grupo, comparar sender con usuario actual
    const isOwn = 
      (message.sub_type === 'public' && message.sender === window.currentUsername) ||
      (message.sub_type === 'private_to') ||
      (message.sub_type === 'group' && message.sender === window.currentUsername) ||
      (message.sub_type?.includes('to') && message.sub_type !== 'private_from');
    
    msgElem.classList.add(isOwn ? 'message-own' : 'message-other');
    
    msgElem.innerHTML = `
      <div class="message-header">
        <span class="message-sender">${message.sender || 'Sistema'}</span>
        <span class="message-time">${new Date().toLocaleTimeString()}</span>
      </div>
      <div class="message-content">${this.escapeHtml(message.text || message.message || '')}</div>
    `;
    
    this.chatWindow.appendChild(msgElem);
    this.chatWindow.scrollTop = this.chatWindow.scrollHeight;
  }

  addSystemMessage(text) {
    const welcome = this.chatWindow.querySelector('.welcome-message');
    if (welcome) {
      welcome.remove();
    }

    const sysMsg = document.createElement('div');
    sysMsg.className = 'message message-system';
    sysMsg.innerHTML = `
      <div class="message-content">${this.escapeHtml(text)}</div>
    `;
    this.chatWindow.appendChild(sysMsg);
    this.chatWindow.scrollTop = this.chatWindow.scrollHeight;
  }

  escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  clear() {
    this.chatWindow.innerHTML = '';
  }
}

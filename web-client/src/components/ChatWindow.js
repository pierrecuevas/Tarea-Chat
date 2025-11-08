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
    const isOwn = 
      (message.sub_type === 'public' && message.sender === window.currentUsername) ||
      (message.sub_type === 'private_to') ||
      (message.sub_type === 'group' && message.sender === window.currentUsername) ||
      (message.sub_type?.includes('to') && message.sub_type !== 'private_from');
    
    msgElem.classList.add(isOwn ? 'message-own' : 'message-other');
    
    // **FORMATO DE HORA REAL (CORRECCIÓN)**
    let timeDisplay = new Date().toLocaleTimeString('es-CO', { 
      hour: '2-digit', 
      minute: '2-digit',
      hour12: true
    });
    
    if (message.sent_at) {
      try {
        const sentDate = new Date(message.sent_at);
        // Validar que sea fecha válida
        if (!isNaN(sentDate.getTime())) {
          timeDisplay = sentDate.toLocaleTimeString('es-CO', { 
            hour: '2-digit', 
            minute: '2-digit',
            hour12: true
          });
        }
      } catch (e) {
        console.error('Error parseando fecha:', e);
      }
    }
    
    msgElem.innerHTML = `
      <div class="message-header">
        <span class="message-sender">${message.sender || 'Sistema'}</span>
        <span class="message-time">${timeDisplay}</span>
      </div>
      <div class="message-content">${this.escapeHtml(message.text || message.message || '')}</div>
    `;
    
    // **AGREGAR AL FINAL (NO AL INICIO)** para orden cronológico correcto
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

export default class ChatWindow {
  constructor(container) {
    this.container = container;
    this.currentChat = null;
    this.onLoadMore = null;
    this.isLoadingMore = false;
    this.render();
    this.setupScrollListener();
  }

  render() {
    this.container.innerHTML = `
      <div class="chat-window-header" id="chatHeader">
        <div class="chat-title">
          <span class="chat-title-icon">💬</span>
          <span id="chatTitle">Selecciona un chat</span>
        </div>
        <button id="leaveGroupBtn" class="leave-group-btn" style="display: none;" title="Salir del grupo">
          🚪
        </button>
      </div>
      <div id="chatWindow" class="chat-messages">
        <div id="loadMoreButton" class="load-more-button" style="display: none;">
          <button id="loadMoreBtn">Cargar más mensajes</button>
        </div>
        <div class="welcome-message">
          <div class="welcome-icon">👋</div>
          <p>¡Bienvenido al Chat de los Indigentes!</p>
          <p class="welcome-subtitle">Selecciona un chat para comenzar</p>
        </div>
      </div>
    `;
    this.chatWindow = this.container.querySelector('#chatWindow');
    this.chatTitle = this.container.querySelector('#chatTitle');
    this.loadMoreButton = this.container.querySelector('#loadMoreButton');
    this.loadMoreBtn = this.container.querySelector('#loadMoreBtn');
    this.leaveGroupBtn = this.container.querySelector('#leaveGroupBtn');
    this.onLeaveGroup = null;
    
    // Configurar el botón de cargar más
    if (this.loadMoreBtn) {
      this.loadMoreBtn.addEventListener('click', () => {
        if (this.onLoadMore && !this.isLoadingMore) {
          this.isLoadingMore = true;
          this.loadMoreBtn.textContent = 'Cargando...';
          this.onLoadMore().finally(() => {
            this.isLoadingMore = false;
            this.loadMoreBtn.textContent = 'Cargar más mensajes';
          });
        }
      });
    }
    
    // Configurar el botón de salir del grupo
    if (this.leaveGroupBtn) {
      this.leaveGroupBtn.addEventListener('click', () => {
        if (this.onLeaveGroup) {
          this.onLeaveGroup();
        }
      });
    }
  }
  
  setupScrollListener() {
    if (this.chatWindow) {
      this.chatWindow.addEventListener('scroll', () => {
        // Mostrar botón de cargar más cuando se hace scroll hacia arriba
        if (this.chatWindow.scrollTop < 100 && !this.isLoadingMore) {
          this.showLoadMoreButton();
        } else {
          this.hideLoadMoreButton();
        }
      });
    }
  }
  
  showLoadMoreButton() {
    if (this.loadMoreButton) {
      this.loadMoreButton.style.display = 'block';
    }
  }
  
  hideLoadMoreButton() {
    if (this.loadMoreButton) {
      this.loadMoreButton.style.display = 'none';
    }
  }
  
  setOnLoadMore(callback) {
    this.onLoadMore = callback;
  }

  setCurrentChat(type, name) {
    this.currentChat = { type, name, username: null };
    const icon = type === 'group' ? '👥' : type === 'private' ? '👤' : '🌐';
    this.chatTitle.textContent = name;
    this.chatTitle.parentElement.querySelector('.chat-title-icon').textContent = icon;
    
    // Mostrar/ocultar botón de salir del grupo
    if (this.leaveGroupBtn) {
      if (type === 'group') {
        this.leaveGroupBtn.style.display = 'block';
      } else {
        this.leaveGroupBtn.style.display = 'none';
      }
    }
  }
  
  setOnLeaveGroup(callback) {
    this.onLeaveGroup = callback;
  }

  addMessage(message, prepend = false) {
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
    
    // **FORMATO DE FECHA Y HORA**
    let timeDisplay = new Date().toLocaleString('es-CO', { 
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit', 
      minute: '2-digit',
      hour12: true
    });
    
    if (message.sent_at) {
      try {
        const sentDate = new Date(message.sent_at);
        // Validar que sea fecha válida
        if (!isNaN(sentDate.getTime())) {
          const today = new Date();
          const isToday = sentDate.toDateString() === today.toDateString();
          
          if (isToday) {
            // Si es hoy, solo mostrar la hora
            timeDisplay = sentDate.toLocaleTimeString('es-CO', { 
              hour: '2-digit', 
              minute: '2-digit',
              hour12: true
            });
          } else {
            // Si es otro día, mostrar fecha y hora
            timeDisplay = sentDate.toLocaleString('es-CO', { 
              day: '2-digit',
              month: '2-digit',
              year: 'numeric',
              hour: '2-digit', 
              minute: '2-digit',
              hour12: true
            });
          }
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
    
    // Si prepend es true, agregar al inicio (para cargar más mensajes)
    if (prepend) {
      const loadMoreButton = this.chatWindow.querySelector('#loadMoreButton');
      if (loadMoreButton) {
        this.chatWindow.insertBefore(msgElem, loadMoreButton.nextSibling);
      } else {
        this.chatWindow.insertBefore(msgElem, this.chatWindow.firstChild);
      }
    } else {
      // **AGREGAR AL FINAL (NO AL INICIO)** para orden cronológico correcto
      this.chatWindow.appendChild(msgElem);
      this.chatWindow.scrollTop = this.chatWindow.scrollHeight;
    }
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

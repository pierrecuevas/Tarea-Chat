export default class MessageForm {
  constructor(container, currentChat) {
    this.container = container;
    this.onMessageSent = null;
    this.currentChat = currentChat || null;
    this.render();
  }

  setCurrentChat(chat) {
    this.currentChat = chat;
    this.updatePlaceholder();
  }

  updatePlaceholder() {
    const input = this.container.querySelector('#messageText');
    const sendButton = this.container.querySelector('#sendButton');
    
    if (this.currentChat) {
      // Habilitar input y botón
      input.disabled = false;
      if (sendButton) sendButton.disabled = false;
      
      if (this.currentChat.type === 'group') {
        input.placeholder = `Escribe un mensaje a ${this.currentChat.name}...`;
      } else if (this.currentChat.type === 'private') {
        input.placeholder = `Escribe un mensaje a ${this.currentChat.name}...`;
      } else {
        input.placeholder = 'Escribe un mensaje público...';
      }
    } else {
      input.placeholder = 'Selecciona un chat para enviar un mensaje...';
      input.disabled = true;
      if (sendButton) sendButton.disabled = true;
    }
  }

  render() {
    this.container.innerHTML = `
      <form id="messageForm" class="message-form">
        <div class="message-input-container">
          <input 
            type="text" 
            id="messageText" 
            placeholder="Selecciona un chat para enviar un mensaje..." 
            required 
            autocomplete="off"
          />
          <button type="submit" class="send-button" id="sendButton">
            <span class="send-icon">🚀</span>
          </button>
        </div>
      </form>
    `;
    
    const form = this.container.querySelector('#messageForm');
    const input = this.container.querySelector('#messageText');
    const sendButton = this.container.querySelector('#sendButton');
    
    if (!this.currentChat) {
      input.disabled = true;
      sendButton.disabled = true;
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (!this.currentChat) return;
      
      const text = input.value.trim();
      if (!text) return;

      const message = { 
        command: this.getCommandForChat(),
        text 
      };
      
      if (this.currentChat.type === 'private') {
        message.recipient = this.currentChat.name;
      } else if (this.currentChat.type === 'group') {
        message.group_name = this.currentChat.name;
      }

      try {
        if (this.onMessageSent) {
          this.onMessageSent(message);
        }
        input.value = '';
      } catch (err) {
        console.error('Error enviando mensaje:', err);
      }
    });

    // Enter para enviar, Shift+Enter para nueva línea (si fuera textarea)
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        form.dispatchEvent(new Event('submit'));
      }
    });
  }

  getCommandForChat() {
    if (!this.currentChat) return 'public_message';
    switch (this.currentChat.type) {
      case 'private':
        return 'private_message';
      case 'group':
        return 'group_message';
      default:
        return 'public_message';
    }
  }
}

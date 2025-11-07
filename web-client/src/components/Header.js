export default class Header {
  constructor(container, username) {
    this.container = container;
    this.username = username;
    this.render();
  }

  render() {
    this.container.innerHTML = `
      <div class="header-content">
        <div class="header-logo">
          <div class="logo-icon">💬</div>
          <h1>Chat Futuro</h1>
        </div>
        <div class="header-user">
          <div class="user-avatar">${this.username.charAt(0).toUpperCase()}</div>
          <div class="user-info">
            <span class="user-name">${this.username}</span>
            <span class="user-status">● En línea</span>
          </div>
        </div>
      </div>
    `;
  }

  updateUsername(username) {
    this.username = username;
    this.render();
  }
}


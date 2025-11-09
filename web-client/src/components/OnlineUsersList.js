export default class OnlineUsersList {
  constructor(container, onUserClick) {
    this.container = container;
    this.onUserClick = onUserClick;
    this.users = [];
    this.render();
  }

  render() {
    this.container.innerHTML = `
      <div class="online-users-header">
        <h2>👥 Usuarios Online</h2>
        <span class="online-count" id="onlineCount">0</span>
      </div>
      <div class="online-users-content" id="onlineUsersContent">
        <div class="no-users">No hay usuarios online</div>
      </div>
    `;
  }

  updateUsers(users, currentUsername) {
    this.users = users.filter(u => u !== currentUsername);
    const content = this.container.querySelector('#onlineUsersContent');
    const countElement = this.container.querySelector('#onlineCount');
    
    countElement.textContent = this.users.length;

    if (this.users.length === 0) {
      content.innerHTML = '<div class="no-users">No hay otros usuarios online</div>';
      return;
    }

    content.innerHTML = '';
    this.users.forEach(username => {
      const userItem = document.createElement('div');
      userItem.className = 'user-item';
      userItem.innerHTML = `
        <div class="user-avatar-small">${username.charAt(0).toUpperCase()}</div>
        <div class="user-name-small">${username}</div>
        <div class="status-indicator"></div>
      `;
      userItem.addEventListener('click', () => {
        if (this.onUserClick) {
          this.onUserClick(username);
        }
      });
      content.appendChild(userItem);
    });
  }

  addUser(username) {
    if (!this.users.includes(username)) {
      this.users.push(username);
      this.updateUsers(this.users, '');
    }
  }

  removeUser(username) {
    this.users = this.users.filter(u => u !== username);
    this.updateUsers(this.users, '');
  }
}


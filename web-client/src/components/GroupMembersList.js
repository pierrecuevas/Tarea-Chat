export default class GroupMembersList {
  constructor(container, onlineUsers) {
    this.container = container;
    this.onlineUsers = onlineUsers || [];
    this.members = [];
    this.render();
  }

  render() {
    this.container.innerHTML = `
      <div class="group-members-header">
        <h2>👥 Miembros</h2>
        <span class="members-count" id="membersCount">0</span>
      </div>
      <div class="group-members-content" id="groupMembersContent">
        <div class="no-members">No hay miembros</div>
      </div>
    `;
  }

  updateMembers(members, onlineUsers) {
    this.members = members || [];
    this.onlineUsers = onlineUsers || [];
    const content = this.container.querySelector('#groupMembersContent');
    const countElement = this.container.querySelector('#membersCount');
    
    countElement.textContent = this.members.length;

    if (this.members.length === 0) {
      content.innerHTML = '<div class="no-members">No hay miembros en el grupo</div>';
      return;
    }

    content.innerHTML = '';
    this.members.forEach(username => {
      const isOnline = this.onlineUsers.includes(username);
      const memberItem = document.createElement('div');
      memberItem.className = 'member-item';
      memberItem.innerHTML = `
        <div class="user-avatar-small">${username.charAt(0).toUpperCase()}</div>
        <div class="user-name-small">${username}</div>
        <div class="status-indicator ${isOnline ? 'online' : 'offline'}"></div>
        <span class="status-text">${isOnline ? 'En línea' : 'Desconectado'}</span>
      `;
      content.appendChild(memberItem);
    });
  }

  setOnlineUsers(onlineUsers) {
    this.onlineUsers = onlineUsers || [];
    // Actualizar estado de los miembros existentes
    this.updateMembers(this.members, this.onlineUsers);
  }
}


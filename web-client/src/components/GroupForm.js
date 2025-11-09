import { createGroup, getAllUsers, inviteToGroup } from '../api/ProxyClient.js';

export default class GroupForm {
  constructor(container, sessionId) {
    this.container = container;
    this.sessionId = sessionId;
    this.onGroupCreated = null;
    this.allUsers = [];
    this.selectedUsers = [];
    this.render();
    this.loadUsers();
  }

  async loadUsers() {
    try {
      const response = await getAllUsers(this.sessionId);
      this.allUsers = response.users || [];
      this.updateUsersList();
    } catch (err) {
      console.error('Error cargando usuarios:', err);
      this.allUsers = [];
      this.updateUsersList();
    }
  }

  updateUsersList() {
    const usersList = this.container.querySelector('#usersList');
    if (!usersList) return;

    if (this.allUsers.length === 0) {
      usersList.innerHTML = '<p>No hay usuarios disponibles</p>';
      return;
    }

    usersList.innerHTML = this.allUsers.map(user => `
      <label style="display: flex; align-items: center; margin: 8px 0; cursor: pointer;">
        <input type="checkbox" value="${user}" class="user-checkbox" />
        <span style="margin-left: 8px;">${user}</span>
      </label>
    `).join('');

    // Agregar event listeners a los checkboxes
    this.container.querySelectorAll('.user-checkbox').forEach(checkbox => {
      checkbox.addEventListener('change', (e) => {
        if (e.target.checked) {
          this.selectedUsers.push(e.target.value);
        } else {
          this.selectedUsers = this.selectedUsers.filter(u => u !== e.target.value);
        }
      });
    });
  }

  render() {
    this.container.innerHTML = `
      <div style="position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); background: #1a1a2e; border: 2px solid #16213e; border-radius: 8px; padding: 20px; width: 90%; max-width: 400px; z-index: 1000;">
        <h2 style="color: #00d4ff; margin-top: 0;">Crear Nuevo Grupo</h2>
        
        <form id="groupForm" style="display: flex; flex-direction: column; gap: 12px;">
          <input 
            type="text" 
            id="groupNameInput"
            placeholder="Nombre del grupo" 
            style="padding: 10px; border: 1px solid #16213e; background: #0f3460; color: white; border-radius: 4px;"
            required 
          />
          
          <div style="border: 1px solid #16213e; border-radius: 4px; padding: 10px; background: #0f3460; max-height: 200px; overflow-y: auto;">
            <label style="color: #00d4ff; display: block; margin-bottom: 8px; font-weight: bold;">
              Invitar usuarios (opcional):
            </label>
            <div id="usersList" style="color: #aaa;"></div>
          </div>
          
          <button 
            type="submit" 
            style="padding: 10px; background: #00d4ff; color: #000; border: none; border-radius: 4px; cursor: pointer; font-weight: bold;"
          >
            Crear Grupo
          </button>
        </form>
        
        <div id="groupStatus" style="color: #ff6b6b; margin-top: 12px; text-align: center;"></div>
      </div>
    `;

    this.container.querySelector('#groupForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const groupName = this.container.querySelector('#groupNameInput').value.trim();
      const statusDiv = this.container.querySelector('#groupStatus');

      if (!groupName) {
        statusDiv.textContent = 'Por favor ingresa un nombre para el grupo';
        statusDiv.style.color = '#ff6b6b';
        return;
      }

      try {
        statusDiv.textContent = 'Creando grupo...';
        statusDiv.style.color = '#00d4ff';

        // Crear grupo
        await createGroup(groupName, this.sessionId);

        // Invitar usuarios seleccionados
        for (const user of this.selectedUsers) {
          try {
            await inviteToGroup(groupName, user, this.sessionId);
          } catch (err) {
            console.error(`Error invitando ${user}:`, err);
          }
        }

        statusDiv.textContent = `✓ Grupo "${groupName}" creado exitosamente`;
        statusDiv.style.color = '#00ff88';

        if (this.onGroupCreated) {
          this.onGroupCreated(groupName);
        }

        // Limpiar formulario
        setTimeout(() => {
          this.container.innerHTML = '';
        }, 2000);
      } catch (err) {
        statusDiv.textContent = `Error: ${err.message}`;
        statusDiv.style.color = '#ff6b6b';
      }
    });
  }
}

import { createGroup } from '../api/proxyClient.js';

export default class GroupForm {
  constructor(container) {
    this.container = container;
    this.onGroupCreated = null;
    this.render();
  }

  render() {
    this.container.innerHTML = `
      <form id="groupForm">
        <input type="text" placeholder="Nombre del grupo" required />
        <button type="submit">Crear Grupo</button>
      </form>
      <div id="groupStatus"></div>
    `;
    this.container.querySelector('#groupForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const groupName = e.target[0].value.trim();
      if (!groupName) return;

      try {
        await createGroup(groupName);
        document.getElementById('groupStatus').textContent = 'Grupo creado con éxito';
        if (this.onGroupCreated) this.onGroupCreated(groupName);
      } catch {
        document.getElementById('groupStatus').textContent = 'Error creando grupo';
      }
    });
  }
}

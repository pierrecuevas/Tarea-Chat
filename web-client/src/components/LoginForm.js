export default class LoginForm {
  constructor(container, onSuccess) {
    this.container = container;
    this.onSuccess = onSuccess;
    this.isLogin = true;
    this.render();
  }

  render() {
    this.container.innerHTML = `
      <button id="toggleSwitch">
        ${this.isLogin ? '¿No tienes cuenta? Regístrate' : '¿Ya tienes cuenta? Inicia sesión'}
      </button>
      <form id="authForm">
        <input type="text" placeholder="Usuario" required />
        <input type="password" placeholder="Contraseña" required />
        <button type="submit">${this.isLogin ? 'Iniciar Sesión' : 'Registrarse'}</button>
      </form>
      <div id="authMessage"></div>
    `;

    this.container.querySelector('#toggleSwitch').onclick = () => {
      this.isLogin = !this.isLogin;
      this.render();
    };

    this.container.querySelector('#authForm').onsubmit = async (e) => {
      e.preventDefault();
      const username = e.target[0].value.trim();
      const password = e.target[1].value.trim();
      const endpoint = this.isLogin ? 'login' : 'register';

      try {
        const res = await fetch(`http://localhost:3000/${endpoint}`, {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({ username, password }),
        });
        const json = await res.json();

        if (json.success) {
          localStorage.setItem('sessionId', json.sessionId);
          document.getElementById('authMessage').textContent = json.message;
          this.onSuccess(username);
        } else {
          document.getElementById('authMessage').textContent = `Error: ${json.message}`;
        }
      } catch {
        document.getElementById('authMessage').textContent = 'Error de conexión';
      }
    };
  }
}

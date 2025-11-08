const BASE_URL = 'http://localhost:3000';

export async function createGroup(groupName, sessionId) {
  const res = await fetch(`${BASE_URL}/create-group`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${sessionId}`
    },
    body: JSON.stringify({ group_name: groupName }),
  });
  if (!res.ok) throw new Error('Error creando grupo');
  return res.json();
}

export async function sendMessage(message, sessionId) {
  const res = await fetch(`${BASE_URL}/send-message`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${sessionId}`
    },
    body: JSON.stringify(message),
  });
  if (!res.ok) throw new Error('Error enviando mensaje');
  return res.json();
}

export async function getOnlineUsers(sessionId) {
  const res = await fetch(`${BASE_URL}/online-users`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${sessionId}`
    },
  });
  if (!res.ok) throw new Error('Error obteniendo usuarios online');
  return res.json();
}

export async function getAllUsers(sessionId) {
  const res = await fetch(`${BASE_URL}/all-users`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${sessionId}`
    },
  });
  if (!res.ok) throw new Error('Error obteniendo todos los usuarios');
  return res.json();
}

export async function getGroupMembers(groupName, sessionId) {
  const res = await fetch(`${BASE_URL}/group-members?group_name=${encodeURIComponent(groupName)}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${sessionId}`
    },
  });
  if (!res.ok) throw new Error('Error obteniendo miembros del grupo');
  return res.json();
}

export async function inviteToGroup(groupName, username, sessionId) {
  const res = await fetch(`${BASE_URL}/invite-to-group`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${sessionId}`
    },
    body: JSON.stringify({ group_name: groupName, user_to_invite: username }),
  });
  if (!res.ok) throw new Error('Error invitando usuario');
  return res.json();
}



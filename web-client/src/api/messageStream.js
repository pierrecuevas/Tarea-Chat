const BASE_URL = 'http://localhost:3000';

export function connectMessageStream(sessionId, onMessage) {
  let reader = null;
  let isClosed = false;

  function connect() {
    if (isClosed) return;

    fetch(`${BASE_URL}/message-stream`, {
      headers: {
        'Authorization': `Bearer ${sessionId}`
      }
    }).then(response => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      function readStream() {
        if (isClosed) {
          reader?.cancel();
          return;
        }

        reader.read().then(({ done, value }) => {
          if (done) {
            console.log('Stream cerrado, reconectando...');
            // Reintentar conexión después de 2 segundos
            setTimeout(() => {
              if (!isClosed) {
                connect();
              }
            }, 2000);
            return;
          }

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          lines.forEach(line => {
            line = line.trim();
            if (line.startsWith('data: ')) {
              try {
                const data = JSON.parse(line.substring(6));
                if (onMessage) {
                  onMessage(data);
                }
              } catch (err) {
                console.error('Error parseando mensaje:', err, line);
              }
            } else if (line && !line.startsWith(':') && line !== '') {
              // Intentar parsear como JSON directo (por si acaso)
              try {
                const data = JSON.parse(line);
                if (onMessage) {
                  onMessage(data);
                }
              } catch (err) {
                // Ignorar si no es JSON válido
              }
            }
          });

          readStream();
        }).catch(err => {
          console.error('Error leyendo stream:', err);
          if (!isClosed) {
            // Reintentar conexión después de 3 segundos
            setTimeout(() => {
              connect();
            }, 3000);
          }
        });
      }

      readStream();
    }).catch(err => {
      console.error('Error conectando al stream:', err);
      if (!isClosed) {
        // Reintentar después de 3 segundos
        setTimeout(() => {
          connect();
        }, 3000);
      }
    });
  }

  connect();
  
  return {
    close: () => {
      isClosed = true;
      reader?.cancel();
    }
  };
}


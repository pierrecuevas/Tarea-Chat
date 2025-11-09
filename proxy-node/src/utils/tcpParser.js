function createTCPParser() {
    return {
      buffer: '',
      
      readMessage(clientSocket, timeout = 5000) {
        return new Promise((resolve, reject) => {
          const onData = (data) => {
            this.buffer += data.toString();
            
            // Buscar líneas completas terminadas con \n
            const lines = this.buffer.split('\n');
            
            for (let i = 0; i < lines.length - 1; i++) {
              const line = lines[i].trim();
              if (line) {
                try {
                  const json = JSON.parse(line);
                  // JSON válido encontrado
                  clientSocket.removeListener('data', onData);
                  clearTimeout(timer);
                  
                  // Guardar líneas restantes en buffer
                  this.buffer = lines.slice(i + 1).join('\n');
                  
                  resolve(json);
                  return;
                } catch (e) {
                  // Línea no es JSON válido, ignorar y continuar
                  console.warn(`[TCPParser] Línea no-JSON recibida: ${line}`);
                }
              }
            }
            
            // Guardar última línea incompleta
            this.buffer = lines[lines.length - 1];
          };
  
          const timer = setTimeout(() => {
            clientSocket.removeListener('data', onData);
            reject(new Error(`Timeout esperando respuesta TCP (${timeout}ms)`));
          }, timeout);
  
          clientSocket.on('data', onData);
        });
      }
    };
  }
  
  module.exports = { createTCPParser };
  
import javax.sound.sampled.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;

public class AudioService {

    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
    public static final int CALL_BUFFER_SIZE = 2048; // Búfer más grande para capturar más audio
    
    private static final String CLIENT_DOWNLOAD_PATH = "client_downloads/";
    private static final String CLIENT_RECORDING_PATH = "client_recordings/";

    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
    private final AtomicInteger audioCounter = new AtomicInteger(1);

    public AudioService() {
        new File(CLIENT_DOWNLOAD_PATH).mkdirs();
        new File(CLIENT_RECORDING_PATH).mkdirs();
    }

    public String startRecording(String username) {
        if (isRecording) {
            System.out.println(">> Ya estás grabando un audio.");
            return null;
        }
        try {
            // Re-inicializar el micrófono para grabación de archivos
            TargetDataLine fileMicrophone = AudioSystem.getTargetDataLine(FORMAT);
            fileMicrophone.open(FORMAT);
            fileMicrophone.start();
            isRecording = true;
            String filePath = String.format("%s%s_audio_%d.wav", CLIENT_RECORDING_PATH, username, audioCounter.getAndIncrement());
            File wavFile = new File(filePath);
            recordingThread = new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(fileMicrophone)) {
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);
                } catch (IOException e) {
                    System.err.println("Error al guardar el archivo de audio: " + e.getMessage());
                }
            });
            recordingThread.start();
            System.out.println(">> Grabación iniciada. El audio se guardará en: " + filePath);
            this.microphone = fileMicrophone; // Guardar referencia para detener
            return filePath;
        } catch (LineUnavailableException e) {
            System.err.println("Error: Micrófono no disponible. " + e.getMessage());
            return null;
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            System.out.println(">> No hay ninguna grabación activa para detener.");
            return;
        }
        isRecording = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        recordingThread = null;
        System.out.println(">> Grabación detenida.");
    }
    
    public void saveDownloadedAudio(String fileName, InputStream inStream, long fileSize) {
        File targetFile = new File(CLIENT_DOWNLOAD_PATH + fileName);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            System.out.println("\n>> Descargando " + fileName + "...");
            // Leer exactamente 'fileSize' bytes del stream
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;
            while (totalBytesRead < fileSize && (bytesRead = inStream.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
             if (totalBytesRead == fileSize) {
                System.out.println(">> Descarga completa: " + targetFile.getPath());
                System.out.println(">> Puedes reproducirlo ahora con: /reproducir " + fileName);
            } else {
                System.err.println("Error de descarga: El tamaño del archivo no coincide.");
                targetFile.delete();
            }
        } catch (IOException e) {
            System.err.println("Error al guardar el archivo descargado: " + e.getMessage());
        }
    }

    public boolean isAudioDownloaded(String fileName) {
        File downloadedFile = new File(CLIENT_DOWNLOAD_PATH + fileName);
        File recordedFile = new File(CLIENT_RECORDING_PATH + fileName);
        return downloadedFile.exists() || recordedFile.exists();
    }
    
    public void playAudio(String fileName) {
        File audioFile = new File(CLIENT_DOWNLOAD_PATH + fileName);
        if (!audioFile.exists()) {
             audioFile = new File(CLIENT_RECORDING_PATH + fileName);
        }
        
        if (!audioFile.exists()) {
            System.out.println(">> El archivo '" + fileName + "' no se encuentra localmente.");
            return;
        }

        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile)) {
            Clip clip = AudioSystem.getClip();
            
            // Usamos un CountDownLatch para esperar a que el listener nos avise que terminó.
            CountDownLatch sync = new CountDownLatch(1);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    sync.countDown();
                }
            });
            
            clip.open(audioStream);
            System.out.println("\n>> Reproduciendo " + fileName + "... (la consola no responderá hasta que termine)");
            clip.start();
            
            // El hilo actual se bloquea aquí hasta que sync.countDown() es llamado.
            sync.await(); 
            
            System.out.println(">> Reproducción finalizada.");
            clip.close();
            
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | InterruptedException e) {
            System.err.println("Error al reproducir el audio: " + e.getMessage());
        }
    }

    public byte[] captureAudioForCall() {
        if (microphone == null || !microphone.isOpen()) {
            try {
                microphone = AudioSystem.getTargetDataLine(FORMAT);
                microphone.open(FORMAT, CALL_BUFFER_SIZE);
                microphone.start();
            } catch (LineUnavailableException e) { return null; }
        }
        byte[] buffer = new byte[CALL_BUFFER_SIZE];
        int bytesRead = microphone.read(buffer, 0, buffer.length);

        if (bytesRead > 0) {
            // CAMBIO CRÍTICO: Devuelve una copia del array con el tamaño exacto de los datos leídos.
            // Esto evita enviar bytes vacíos o "basura".
            return Arrays.copyOf(buffer, bytesRead);
        }
        return null;
    }

    public void playAudioFromCall(byte[] audioData, int length) {
        if (speaker == null || !speaker.isOpen()) {
            try {
                speaker = AudioSystem.getSourceDataLine(FORMAT);
                speaker.open(FORMAT, CALL_BUFFER_SIZE);
                speaker.start();
            } catch (LineUnavailableException e) { return; }
        }
        speaker.write(audioData, 0, length);
    }

    public void stopCallAudio() {
        if (microphone != null) {
            microphone.stop();
            microphone.close();
            microphone = null;
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
            speaker = null;
        }
    }
}
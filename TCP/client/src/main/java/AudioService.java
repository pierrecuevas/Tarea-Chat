import javax.sound.sampled.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages all audio-related operations for the client, including recording,
 * playing, and saving downloaded audio files.
 */
public class AudioService {

    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, true);
    private static final String CLIENT_DOWNLOAD_PATH = "client_downloads/";
    private static final String CLIENT_RECORDING_PATH = "client_recordings/";

    private TargetDataLine microphone;
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
            microphone = AudioSystem.getTargetDataLine(FORMAT);
            microphone.open(FORMAT);
            microphone.start();
            isRecording = true;
            String filePath = String.format("%s%s_audio_%d.wav", CLIENT_RECORDING_PATH, username, audioCounter.getAndIncrement());
            File wavFile = new File(filePath);
            recordingThread = new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(microphone)) {
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);
                } catch (IOException e) {
                    System.err.println("Error al guardar el archivo de audio: " + e.getMessage());
                }
            });
            recordingThread.start();
            System.out.println(">> Grabación iniciada. El audio se guardará en: " + filePath);
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
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;
            System.out.println("\n>> Descargando " + fileName + "...");
            while (totalBytesRead < fileSize && (bytesRead = inStream.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            if (totalBytesRead == fileSize) {
                System.out.println(">> Descarga completa: " + targetFile.getPath());
                playAudio(fileName);
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

    /**
     * Plays an audio file and BLOCKS the console until playback is finished.
     */
    public void playAudio(String fileName) {
        File audioFile = new File(CLIENT_DOWNLOAD_PATH + fileName);
        if (!audioFile.exists()) {
             audioFile = new File(CLIENT_RECORDING_PATH + fileName);
        }
        
        if (!audioFile.exists()) {
            System.out.println(">> El archivo '" + fileName + "' no se encuentra localmente.");
            return;
        }

        // We can't use try-with-resources here because the clip must remain open
        // while it plays asynchronously. We will close it manually in the listener.
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            Clip clip = AudioSystem.getClip();

            // Use a semaphore to wait for playback to finish
            final Object lock = new Object();

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    // This block is executed when the clip finishes playing
                    synchronized (lock) {
                        lock.notifyAll(); // Notify the main thread to continue
                    }
                }
            });
            
            clip.open(audioStream);
            System.out.println("\n>> Reproduciendo " + fileName + "... (la consola esperará)");
            clip.start();

            synchronized (lock) {
                try {
                    lock.wait(); // Block the thread until notified by the listener
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
            }
            
            // Clean up resources after playback is complete
            clip.close();
            audioStream.close();
            System.out.println(">> Reproducción finalizada.");

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Error al reproducir el audio: " + e.getMessage());
        }
    }
}


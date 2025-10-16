import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

public class AudioService {

    private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, true);
    private static final String CLIENT_DOWNLOAD_PATH = "client_downloads/";
    private static final String CLIENT_RECORDING_PATH = "client_recordings/";

    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private Thread recordingThread;
    private volatile boolean isRecording = false;
    private final AtomicInteger audioCounter = new AtomicInteger(1);
    private Clip ringtoneClip;

    public AudioService() {
        new File(CLIENT_DOWNLOAD_PATH).mkdirs();
        new File(CLIENT_RECORDING_PATH).mkdirs();
    }

    // --- Métodos para Notas de Voz (Archivos) ---
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

        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
             Clip clip = AudioSystem.getClip()) {
            clip.open(audioStream);
            System.out.println("\n>> Reproduciendo " + fileName + "...");
            clip.start();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    event.getLine().close();
                    System.out.println(">> Reproducción finalizada.");
                }
            });
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Error al reproducir el audio: " + e.getMessage());
        }
    }

    // --- Métodos para Llamadas (Streaming de bytes) ---
    public void playRingtone() {
        if (ringtoneClip != null && ringtoneClip.isOpen()) return;
        try {
            ringtoneClip = AudioSystem.getClip();
            AudioFormat format = new AudioFormat(44100, 8, 1, true, false);
            byte[] tone = new byte[44100]; // 1 segundo de tono a 440Hz
            for (int i = 0; i < tone.length; i++) {
                double angle = i / (44100.0 / 440.0) * 2.0 * Math.PI;
                tone[i] = (byte) (Math.sin(angle) * 100);
            }
            try (AudioInputStream toneStream = new AudioInputStream(new ByteArrayInputStream(tone), format, tone.length)) {
                ringtoneClip.open(toneStream);
                ringtoneClip.loop(Clip.LOOP_CONTINUOUSLY);
            }
        } catch (Exception e) {
            System.err.println("Error al reproducir el tono de llamada: " + e.getMessage());
        }
    }

    public void stopRingtone() {
        if (ringtoneClip != null && ringtoneClip.isOpen()) {
            ringtoneClip.stop();
            ringtoneClip.close();
        }
    }

    public byte[] captureAudioForCall() {
        if (microphone == null || !microphone.isOpen()) {
            try {
                microphone = AudioSystem.getTargetDataLine(FORMAT);
                microphone.open(FORMAT, 1024);
                microphone.start();
            } catch (LineUnavailableException e) { return null; }
        }
        byte[] buffer = new byte[1024];
        int bytesRead = microphone.read(buffer, 0, buffer.length);
        return (bytesRead > 0) ? buffer : null;
    }

    public void playAudioFromCall(byte[] audioData, int length) {
        if (speaker == null || !speaker.isOpen()) {
            try {
                speaker = AudioSystem.getSourceDataLine(FORMAT);
                speaker.open(FORMAT, 1024);
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


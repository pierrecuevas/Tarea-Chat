export default class AudioPlayer {
    constructor(container, audioUrl, messageInfo) {
        this.container = container;
        this.audioUrl = audioUrl;
        this.messageInfo = messageInfo;
        this.isPlaying = false;
        this.audio = null;
        this.render();
    }

    render() {
        const playerDiv = document.createElement('div');
        playerDiv.className = 'audio-player';

        // Provide defaults for missing data
        const duration = this.messageInfo?.duration || 0;
        const timestamp = this.messageInfo?.sent_at || this.messageInfo?.timestamp || new Date().toISOString();

        playerDiv.innerHTML = `
            <div class="audio-player-content">
                <button class="audio-play-btn" id="playBtn">
                    <span class="play-icon">▶️</span>
                </button>
                <div class="audio-info">
                    <div class="audio-waveform">
                        <div class="waveform-bar"></div>
                        <div class="waveform-bar"></div>
                        <div class="waveform-bar"></div>
                        <div class="waveform-bar"></div>
                        <div class="waveform-bar"></div>
                    </div>
                    <div class="audio-duration">${this.formatDuration(duration)}</div>
                </div>
            </div>
            <div class="audio-timestamp">${this.formatTimestamp(timestamp)}</div>
        `;

        this.container.appendChild(playerDiv);

        // Setup audio element
        this.audio = new Audio(this.audioUrl);
        console.log('AudioPlayer created with URL:', this.audioUrl);

        // Add error listener
        this.audio.addEventListener('error', (e) => {
            console.error('Audio loading error:', e);
            console.error('Audio error code:', this.audio.error?.code);
            console.error('Audio error message:', this.audio.error?.message);
        });

        // Setup play button
        const playBtn = playerDiv.querySelector('#playBtn');
        playBtn.addEventListener('click', () => this.togglePlay());

        // Update button when audio ends
        this.audio.addEventListener('ended', () => {
            this.isPlaying = false;
            playBtn.querySelector('.play-icon').textContent = '▶️';
        });
    }

    togglePlay() {
        const playBtn = this.container.querySelector('#playBtn .play-icon');

        if (this.isPlaying) {
            this.audio.pause();
            playBtn.textContent = '▶️';
        } else {
            console.log('Attempting to play audio from:', this.audioUrl);
            this.audio.play().catch(err => {
                console.error('Error playing audio:', err);
                console.error('Audio URL:', this.audioUrl);
                alert('No se pudo reproducir el audio. Verifica que el archivo existe.');
            });
            playBtn.textContent = '⏸️';
        }

        this.isPlaying = !this.isPlaying;
    }

    formatDuration(ms) {
        const seconds = Math.floor(ms / 1000);
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
}

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
                    <div class="audio-duration">${this.formatDuration(this.messageInfo.duration)}</div>
                </div>
            </div>
            <div class="audio-timestamp">${this.formatTimestamp(this.messageInfo.timestamp)}</div>
        `;

        this.container.appendChild(playerDiv);

        // Setup audio element
        this.audio = new Audio(this.audioUrl);

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
            this.audio.play();
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

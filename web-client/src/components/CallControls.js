export default class CallControls {
    constructor(container) {
        this.container = container;
        this.onCallInitiated = null;
        this.onCallEnded = null;
        this.onCallAccepted = null;
        this.onCallRejected = null;
        this.isInCall = false;
        this.incomingCaller = null;
        this.render();
    }

    render() {
        this.container.innerHTML = `
            <!-- Call Button -->
            <div id="callButtonContainer" class="call-button-container">
                <button id="callButton" class="call-button" title="Iniciar llamada">
                    📞
                </button>
            </div>

            <!-- Incoming Call Modal -->
            <div id="incomingCallModal" class="call-modal" style="display: none;">
                <div class="call-modal-content">
                    <h3>Llamada entrante</h3>
                    <p id="callerName">Usuario desconocido</p>
                    <div class="call-modal-buttons">
                        <button id="acceptCallBtn" class="accept-call-btn">✅ Aceptar</button>
                        <button id="rejectCallBtn" class="reject-call-btn">❌ Rechazar</button>
                    </div>
                </div>
            </div>

            <!-- Active Call Controls -->
            <div id="activeCallControls" class="active-call-controls" style="display: none;">
                <div class="call-info">
                    <span id="callParticipant">En llamada</span>
                    <span id="callDuration">00:00</span>
                </div>
                <div class="call-actions">
                    <button id="muteBtn" class="call-action-btn">
                        🔊 Silenciar
                    </button>
                    <button id="endCallBtn" class="end-call-btn">
                        📵 Colgar
                    </button>
                </div>
            </div>
        `;

        this.setupEventListeners();
    }

    setupEventListeners() {
        // Call button
        const callBtn = this.container.querySelector('#callButton');
        callBtn.addEventListener('click', () => {
            if (this.onCallInitiated) {
                this.onCallInitiated();
            }
        });

        // Accept call
        const acceptBtn = this.container.querySelector('#acceptCallBtn');
        acceptBtn.addEventListener('click', () => {
            if (this.onCallAccepted && this.incomingCaller) {
                this.onCallAccepted(this.incomingCaller);
                this.hideIncomingCallModal();
                this.showActiveCall(this.incomingCaller);
            }
        });

        // Reject call
        const rejectBtn = this.container.querySelector('#rejectCallBtn');
        rejectBtn.addEventListener('click', () => {
            if (this.onCallRejected && this.incomingCaller) {
                this.onCallRejected(this.incomingCaller);
                this.hideIncomingCallModal();
            }
        });

        // End call
        const endBtn = this.container.querySelector('#endCallBtn');
        endBtn.addEventListener('click', () => {
            if (this.onCallEnded) {
                this.onCallEnded();
                this.hideActiveCall();
            }
        });

        // Mute button
        const muteBtn = this.container.querySelector('#muteBtn');
        muteBtn.addEventListener('click', () => {
            // TODO: Implement mute functionality
            const isMuted = muteBtn.textContent.includes('Silenciar');
            muteBtn.textContent = isMuted ? '🔇 Activar' : '🔊 Silenciar';
        });
    }

    showIncomingCall(caller) {
        this.incomingCaller = caller;
        const modal = this.container.querySelector('#incomingCallModal');
        const callerName = this.container.querySelector('#callerName');
        callerName.textContent = caller;
        modal.style.display = 'flex';
    }

    hideIncomingCallModal() {
        const modal = this.container.querySelector('#incomingCallModal');
        modal.style.display = 'none';
        this.incomingCaller = null;
    }

    showActiveCall(participant) {
        this.isInCall = true;
        const controls = this.container.querySelector('#activeCallControls');
        const participantName = this.container.querySelector('#callParticipant');
        participantName.textContent = `En llamada con ${participant}`;
        controls.style.display = 'flex';

        // Hide call button
        const callBtn = this.container.querySelector('#callButtonContainer');
        callBtn.style.display = 'none';

        // Start call duration timer
        this.startCallTimer();
    }

    hideActiveCall() {
        this.isInCall = false;
        const controls = this.container.querySelector('#activeCallControls');
        controls.style.display = 'none';

        // Show call button
        const callBtn = this.container.querySelector('#callButtonContainer');
        callBtn.style.display = 'block';

        // Stop timer
        if (this.callTimer) {
            clearInterval(this.callTimer);
        }
    }

    startCallTimer() {
        let seconds = 0;
        const durationEl = this.container.querySelector('#callDuration');

        this.callTimer = setInterval(() => {
            seconds++;
            const mins = Math.floor(seconds / 60);
            const secs = seconds % 60;
            durationEl.textContent = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
        }, 1000);
    }
}

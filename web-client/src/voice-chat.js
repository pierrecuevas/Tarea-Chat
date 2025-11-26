export class VoiceChatClient {
    constructor() {
        this.communicator = null;
        this.proxy = null;
        this.mediaRecorder = null;
        this.mediaStream = null;
        this.isRecording = false;
        this.isInCall = false;
        this.recordingMode = 'message'; // 'message' or 'call'
        this.audioChunks = [];
        this.currentRecipient = null;

        // WebRTC properties
        this.peerConnection = null;
        this.localStream = null;
        this.remoteAudio = null;
        this.onSignalingMessage = null; // Callback for sending signaling messages
    }

    async initialize() {
        try {
            // Wait for Ice library to load
            const Ice = window.Ice;
            if (!Ice) {
                throw new Error("Ice library not found! Make sure Ice.js is loaded.");
            }

            // Initialize ICE communicator with WebSocket protocol
            const initData = new Ice.InitializationData();
            initData.properties = Ice.createProperties();
            initData.properties.setProperty("Ice.Default.Protocol", "ws");

            this.communicator = Ice.initialize(initData);

            // Create proxy to the server - use current hostname for network access
            const serverHost = window.location.hostname;
            const proxyString = `VoiceChat:ws -h ${serverHost} -p 10000`;
            console.log("Connecting to:", proxyString);
            const base = this.communicator.stringToProxy(proxyString);

            // Wait for demo module to load
            const demo = window.demo;
            if (!demo || !demo.VoiceChatPrx) {
                throw new Error("Generated Chat.js module not found! Make sure Chat.js is loaded.");
            }

            // Cast to VoiceChat interface
            this.proxy = await demo.VoiceChatPrx.checkedCast(base);
            if (!this.proxy) {
                throw new Error("Invalid proxy - server may not be running or endpoint is incorrect");
            }

            console.log("Voice Chat connected successfully!");
            return true;

        } catch (e) {
            console.error("Error initializing Voice Chat:", e);
            return false;
        }
    }

    async startRecordingMessage(recipient) {
        if (this.isRecording) return;

        this.recordingMode = 'message';
        this.currentRecipient = recipient;
        this.audioChunks = [];

        try {
            this.mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
            this.mediaRecorder = new MediaRecorder(this.mediaStream, {
                mimeType: 'audio/webm;codecs=opus'
            });
            this.isRecording = true;

            this.mediaRecorder.ondataavailable = (event) => {
                if (event.data.size > 0) {
                    this.audioChunks.push(event.data);
                }
            };

            this.mediaRecorder.onstop = async () => {
                await this.sendVoiceMessage();
            };

            this.mediaRecorder.start();
            console.log("Recording voice message started");

        } catch (e) {
            console.error("Error starting voice message recording:", e);
            this.isRecording = false;
        }
    }

    async stopRecordingMessage() {
        if (this.mediaRecorder && this.isRecording && this.recordingMode === 'message') {
            this.mediaRecorder.stop();
            if (this.mediaStream) {
                this.mediaStream.getTracks().forEach(track => track.stop());
            }
            this.isRecording = false;
            console.log("Recording voice message stopped");
        }
    }

    async sendVoiceMessage() {
        if (this.audioChunks.length === 0 || !this.proxy) return;

        try {
            // Combine all chunks into a single blob
            const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm;codecs=opus' });
            const buffer = await audioBlob.arrayBuffer();
            const bytes = new Uint8Array(buffer);

            // Calculate duration (approximate)
            const duration = this.audioChunks.length * 100; // Rough estimate

            // Create message info using the proper Ice class
            const demo = window.demo;
            const info = new demo.VoiceMessageInfo(
                window.currentUsername || 'unknown',
                this.currentRecipient || 'general',
                new Date().toISOString(),
                duration
            );

            // Send to server
            const filename = await this.proxy.sendVoiceMessage(bytes, info);
            console.log("Voice message sent:", filename);

            // Clear chunks
            this.audioChunks = [];

            return filename;

        } catch (e) {
            console.error("Error sending voice message:", e);
        }
    }

    async startCall(recipient, isInitiator = true) {
        if (this.isInCall || this.isRecording) return;

        this.currentRecipient = recipient;
        this.isInCall = true;

        try {
            // Initiate call on server (for notification)
            if (isInitiator) {
                await this.proxy.initiateCall(recipient);
            }

            // Import WebRTC helper
            const { WebRTCMethods } = await import('./webrtc-helper.js');

            // Initialize WebRTC peer connection
            await WebRTCMethods.initializePeerConnection(this, recipient);

            // If we're the initiator, create and send offer
            if (isInitiator) {
                await WebRTCMethods.createOffer(this, recipient);
            }

            console.log(`📞 WebRTC call ${isInitiator ? 'initiated' : 'accepted'} with:`, recipient);

        } catch (e) {
            console.error("Error starting WebRTC call:", e);
            this.isInCall = false;
            // Show error to user via alert or console (since we don't have direct access to chatWindow here)
            alert("Error al iniciar llamada: " + e.message);
        }
    }

    async endCall() {
        if (!this.isInCall) return;

        try {
            if (this.currentRecipient) {
                await this.proxy.endCall(this.currentRecipient);
            }

            // Close WebRTC connection
            const { WebRTCMethods } = await import('./webrtc-helper.js');
            WebRTCMethods.closeConnection(this);

            this.isInCall = false;
            this.currentRecipient = null;
            console.log("📞 Call ended");

        } catch (e) {
            console.error("Error ending call:", e);
        }
    }

    async acceptCall(caller) {
        try {
            await this.proxy.acceptCall(caller);
            // Start call as non-initiator (will wait for offer)
            await this.startCall(caller, false);
        } catch (e) {
            console.error("Error accepting call:", e);
        }
    }

    // WebRTC Signaling Handlers
    async handleWebRTCOffer(offer, from) {
        try {
            const { WebRTCMethods } = await import('./webrtc-helper.js');
            await WebRTCMethods.handleOffer(this, offer, from);
            console.log('📞 Handled WebRTC offer from:', from);
        } catch (e) {
            console.error('Error handling WebRTC offer:', e);
        }
    }

    async handleWebRTCAnswer(answer) {
        try {
            const { WebRTCMethods } = await import('./webrtc-helper.js');
            await WebRTCMethods.handleAnswer(this, answer);
            console.log('📞 Handled WebRTC answer');
        } catch (e) {
            console.error('Error handling WebRTC answer:', e);
        }
    }

    async handleICECandidate(candidate) {
        try {
            const { WebRTCMethods } = await import('./webrtc-helper.js');
            await WebRTCMethods.handleIceCandidate(this, candidate);
        } catch (e) {
            console.error('Error handling ICE candidate:', e);
        }
    }

    async playAudioPacket(base64Data) {
        // NOTE: Real-time audio playback of WebM chunks is not possible
        // WebM requires complete container headers to decode
        // Solutions: WebRTC, PCM raw audio, or accumulate & play at end
        console.log('📞 Call audio packet received (playback not implemented - see call_audio_limitation.md)');
    }

    async rejectCall(caller) {
        try {
            await this.proxy.rejectCall(caller);
        } catch (e) {
            console.error("Error rejecting call:", e);
        }
    }
}

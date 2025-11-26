// WebRTC helper methods for VoiceChatClient
// These methods handle peer-to-peer audio connections

export const WebRTCMethods = {
    /**
     * Initialize WebRTC peer connection
     */
    async initializePeerConnection(client, recipient) {
        // Check if mediaDevices is supported
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            throw new Error("Tu navegador no permite acceso al micrófono en este sitio. Asegúrate de usar HTTPS o habilitar 'Insecure origins treated as secure' en chrome://flags.");
        }

        // Create peer connection with Google's public STUN server
        client.peerConnection = new RTCPeerConnection({
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' }
            ]
        });

        // Get microphone access
        client.localStream = await navigator.mediaDevices.getUserMedia({
            audio: {
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: true
            }
        });

        // Add local audio tracks to peer connection
        client.localStream.getTracks().forEach(track => {
            client.peerConnection.addTrack(track, client.localStream);
        });

        // Handle incoming remote audio
        client.peerConnection.ontrack = (event) => {
            console.log('📞 Received remote audio track');
            if (!client.remoteAudio) {
                client.remoteAudio = new Audio();
                client.remoteAudio.autoplay = true;
            }
            client.remoteAudio.srcObject = event.streams[0];
        };

        // Handle ICE candidates
        client.peerConnection.onicecandidate = (event) => {
            if (event.candidate && client.onSignalingMessage) {
                client.onSignalingMessage({
                    type: 'ice_candidate',
                    candidate: event.candidate,
                    to: recipient
                });
            }
        };

        // Handle connection state changes
        client.peerConnection.onconnectionstatechange = () => {
            console.log('WebRTC connection state:', client.peerConnection.connectionState);
            if (client.peerConnection.connectionState === 'connected') {
                console.log('✅ WebRTC peer connection established');
            } else if (client.peerConnection.connectionState === 'failed') {
                console.error('❌ WebRTC connection failed');
            }
        };
    },

    /**
     * Create and send WebRTC offer
     */
    async createOffer(client, recipient) {
        const offer = await client.peerConnection.createOffer();
        await client.peerConnection.setLocalDescription(offer);

        if (client.onSignalingMessage) {
            client.onSignalingMessage({
                type: 'webrtc_offer',
                sdp: offer,
                to: recipient
            });
        }
    },

    /**
     * Handle incoming WebRTC offer
     */
    async handleOffer(client, offer, from) {
        await client.peerConnection.setRemoteDescription(new RTCSessionDescription(offer));
        const answer = await client.peerConnection.createAnswer();
        await client.peerConnection.setLocalDescription(answer);

        if (client.onSignalingMessage) {
            client.onSignalingMessage({
                type: 'webrtc_answer',
                sdp: answer,
                to: from
            });
        }
    },

    /**
     * Handle incoming WebRTC answer
     */
    async handleAnswer(client, answer) {
        await client.peerConnection.setRemoteDescription(new RTCSessionDescription(answer));
    },

    /**
     * Handle incoming ICE candidate
     */
    async handleIceCandidate(client, candidate) {
        try {
            await client.peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
        } catch (e) {
            console.error('Error adding ICE candidate:', e);
        }
    },

    /**
     * Close WebRTC connection
     */
    closeConnection(client) {
        if (client.localStream) {
            client.localStream.getTracks().forEach(track => track.stop());
            client.localStream = null;
        }

        if (client.remoteAudio) {
            client.remoteAudio.srcObject = null;
            client.remoteAudio = null;
        }

        if (client.peerConnection) {
            client.peerConnection.close();
            client.peerConnection = null;
        }
    }
};

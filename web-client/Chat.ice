module demo
{
    sequence<byte> AudioData;

    struct VoiceMessageInfo
    {
        string sender;
        string recipient;
        string timestamp;
        int duration;
    }

    interface VoiceChat
    {
        // Send audio data for real-time streaming (calls)
        void sendAudio(AudioData data);
        
        // Send a complete voice message
        string sendVoiceMessage(AudioData data, VoiceMessageInfo info);
        
        // Call signaling
        void initiateCall(string recipient);
        void acceptCall(string caller);
        void rejectCall(string caller);
        void endCall(string participant);
    }
}
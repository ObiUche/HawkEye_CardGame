import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
    constructor() {
        this.client = null;
        this.sessionId = null;
        this.onGestureCallback = null;
        this.isConnected = false;
    }

    connect(sessionId, onGestureCallback) {
        this.sessionId = sessionId;
        this.onGestureCallback = onGestureCallback;

        this.client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            debug: (str) => {
                console.log("STOMP: " + str);
            },
        });

        this.client.onConnect = (frame) => {
            console.log("✅ WebSocket connected: " + frame);
            this.isConnected = true;

            // Register session with backend
            this.client.publish({
                destination: '/app/tensorflow/gesture.register',
                body: JSON.stringify({ sessionId: this.sessionId })
            });

            // Subscribe to gesture updates
            this.client.subscribe(`/topic/gesture/${this.sessionId}`, (message) => {
                try {
                    const gestureData = JSON.parse(message.body);
                    console.log('📨 Received gesture:', gestureData);
                    if (this.onGestureCallback) {
                        this.onGestureCallback(gestureData);
                    }
                } catch (error) {
                    console.error('❌ Error parsing gesture message', error);
                }
            });

            // Subscribe to game updates
            this.client.subscribe('/topic/game-updates', (message) => {
                try {
                    const gameData = JSON.parse(message.body);
                    console.log('🎮 Received game update:', gameData);
                    if (this.onGestureCallback) {
                        this.onGestureCallback(gameData);
                    }
                } catch (error) {
                    console.error('❌ Error parsing game update', error);
                }
            });
        };

        this.client.onStompError = (frame) => {
            console.error("❌ Websocket Error: " + frame.headers['message']);
            this.isConnected = false;
        };

        this.client.onWebSocketClose = () => {
            console.log("🔌 Websocket connection closed");
            this.isConnected = false;
        };

        this.client.activate();
    }

    disconnect() {
        if (this.client && this.isConnected) {
            // Unregister session
            this.client.publish({
                destination: '/app/tensorflow/gesture.unregister',
                body: JSON.stringify({ sessionId: this.sessionId })
            });
            
            this.client.deactivate();
            this.isConnected = false;
        }
    }

    startRecognition(cameraIndex = 0) {
        if (this.client && this.isConnected) {
            this.client.publish({
                destination: '/app/tensorflow/gesture.start',
                body: JSON.stringify({
                    sessionId: this.sessionId,
                    cameraIndex: cameraIndex
                })
            });
            console.log('🚀 Started gesture recognition');
        } else {
            console.error("❌ Websocket not connected");
        }
    }

    stopRecognition() {
        if (this.client && this.isConnected) {
            this.client.publish({
                destination: '/app/tensorflow/gesture.stop',
                body: JSON.stringify({ sessionId: this.sessionId })
            });
            console.log('🛑 Stopped gesture recognition');
        }
    }

    sendGesture(gesture, gameId = null) {
        if (this.client && this.isConnected) {
            const message = {
                sessionId: this.sessionId,
                gesture: gesture,
                gameId: gameId,
                timestamp: Date.now()
            };
            
            console.log('✋ Sending gesture to backend:', message);
            
            this.client.publish({
                destination: '/app/tensorflow/gesture.detect',
                body: JSON.stringify(message)
            });
        } else {
            console.error('❌ WebSocket not connected, cannot send gesture:', gesture);
        }
    }

    isWebSocketConnected() {
        return this.isConnected;
    }
}

export const webSocketService = new WebSocketService();
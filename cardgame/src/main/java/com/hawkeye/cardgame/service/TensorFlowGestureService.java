package com.hawkeye.cardgame.service;

import com.hawkeye.cardgame.model.GestureMessage;
import com.hawkeye.cardgame.model.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class TensorFlowGestureService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private GameService gameService; // ADD THIS

    private Set<String> activeSessions = new HashSet<>();

    public void registerSession(String sessionId) {
        activeSessions.add(sessionId);
        System.out.println("✅ Session registered: " + sessionId);
        
        // Send confirmation to the client
        GestureMessage response = new GestureMessage();
        response.setSessionId(sessionId);
        response.setGesture("connected");
        response.setMessage("WebSocket connection established");
        
        messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, response);
    }

    public void unregisterSession(String sessionId) {
        activeSessions.remove(sessionId);
        System.out.println("❌ Session unregistered: " + sessionId);
    }

    public void processGestureFromFrontend(String sessionId, String gesture, String gameId) {
        if (!activeSessions.contains(sessionId)) {
            System.out.println("⚠️  Unknown session: " + sessionId);
            return;
        }

        System.out.println("🎯 Processing gesture: " + gesture + " for session: " + sessionId);

        // 🎯 PROCESS GAME LOGIC IF WE HAVE A VALID GESTURE AND GAME
        if (gameId != null && !gameId.isEmpty()) {
            if ("higher".equals(gesture) || "lower".equals(gesture)) {
                try {
                    System.out.println("🃏 Processing game guess via gesture for game: " + gameId);
                    
                    // PROCESS THE ACTUAL GAME LOGIC
                    GameState updatedGameState = gameService.makeGuess(gameId, gesture);
                    
                    // Create response with FULL GAME STATE
                    GestureMessage response = new GestureMessage();
                    response.setSessionId(sessionId);
                    response.setGesture(gesture);
                    response.setGameId(gameId);
                    response.setMessage(updatedGameState.getMessage());
                    
                    // ADD THE CARD DATA
                    response.setCurrentCard(updatedGameState.getCurrentCard());
                    response.setNextCard(updatedGameState.getNextCard());
                    response.setScore(updatedGameState.getScore());
                    response.setGameOver(updatedGameState.isGameOver());
                    
                    // Send to the specific session
                    messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, response);
                    
                    System.out.println("✅ Game processed via gesture. Score: " + updatedGameState.getScore());
                    
                } catch (Exception e) {
                    System.out.println("❌ Error processing gesture as game guess: " + e.getMessage());
                    
                    GestureMessage errorResponse = new GestureMessage();
                    errorResponse.setSessionId(sessionId);
                    errorResponse.setGesture("error");
                    errorResponse.setMessage("Error: " + e.getMessage());
                    messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, errorResponse);
                }
            } 
            // 🆕 HANDLE RESET GESTURE
            else if ("reset".equals(gesture)) {
                try {
                    System.out.println("🔄 Processing reset gesture for game: " + gameId);
                    
                    // Start a new game
                    GameState newGameState = gameService.startNewGame();
                    
                    // Create response with new game state
                    GestureMessage response = new GestureMessage();
                    response.setSessionId(sessionId);
                    response.setGesture("reset");
                    response.setGameId(newGameState.getGameId());
                    response.setMessage("Game reset! New game started.");
                    
                    // ADD THE NEW CARD DATA
                    response.setCurrentCard(newGameState.getCurrentCard());
                    response.setNextCard(newGameState.getNextCard());
                    response.setScore(newGameState.getScore());
                    response.setGameOver(newGameState.isGameOver());
                    
                    // Send to the specific session
                    messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, response);
                    
                    System.out.println("✅ Game reset via gesture. New game ID: " + newGameState.getGameId());
                    
                } catch (Exception e) {
                    System.out.println("❌ Error resetting game via gesture: " + e.getMessage());
                    
                    GestureMessage errorResponse = new GestureMessage();
                    errorResponse.setSessionId(sessionId);
                    errorResponse.setGesture("error");
                    errorResponse.setMessage("Error resetting game: " + e.getMessage());
                    messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, errorResponse);
                }
            }
        } else {
            // Just send gesture confirmation (no game involved or invalid gesture)
            GestureMessage response = new GestureMessage();
            response.setSessionId(sessionId);
            response.setGesture(gesture);
            response.setGameId(gameId);
            response.setMessage("Gesture detected: " + gesture);
            
            messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, response);
        }
    }

    public void startRecognition(String sessionId, int cameraIndex) {
        if (activeSessions.contains(sessionId)) {
            GestureMessage response = new GestureMessage();
            response.setSessionId(sessionId);
            response.setGesture("started");
            response.setMessage("Gesture recognition started with camera " + cameraIndex);
            
            messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, response);
            System.out.println("🚀 Gesture recognition started for session: " + sessionId);
        }
    }

    public void stopRecognition(String sessionId) {
        if (activeSessions.contains(sessionId)) {
            GestureMessage response = new GestureMessage();
            response.setSessionId(sessionId);
            response.setGesture("stopped");
            response.setMessage("Gesture recognition stopped");
            
            messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, response);
            System.out.println("🛑 Gesture recognition stopped for session: " + sessionId);
        }
    }
}
package com.hawkeye.cardgame.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hawkeye.cardgame.model.GameState;
import com.hawkeye.cardgame.service.GameService;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class GameController {

	@Autowired
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/start")
    public ResponseEntity<GameState> startGame() {
        try {
            GameState gameState = gameService.startNewGame();
            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{gameId}/guess")
    public ResponseEntity<GameState> makeGuess(
    		@PathVariable("gameId") String gameId, 
    		@RequestBody Map<String, String> requestMap) {
        try {
            System.out.println("🎯 DEBUG: Request map received: " + requestMap);
            String guess = requestMap.get("guess");
            System.out.println("🎯 DEBUG: Extracted guess: " + guess);
            
            if (guess == null) {
                throw new IllegalArgumentException("Guess parameter is required");
            }
            
            GameState gameState = gameService.makeGuess(gameId, guess);
            System.out.println("✅ DEBUG: Guess processed successfully");
            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
            System.err.println("❌ Error making guess: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameState> getGameState(@PathVariable String gameId) {
        try {
            GameState gameState = gameService.getGameState(gameId);
            return ResponseEntity.ok(gameState);
        } catch (Exception e) {
        	e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
    }

    public static class GuessRequest {
    	
        private String guess;

        public String getGuess() { return guess; }
        public void setGuess(String guess) { this.guess = guess; }
    }
}
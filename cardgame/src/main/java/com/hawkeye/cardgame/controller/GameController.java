package com.hawkeye.cardgame.controller;

import com.hawkeye.cardgame.model.GameState;
import com.hawkeye.cardgame.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "http://localhost:3000")
public class GameController {
	
	@Autowired
	private GameService gameService;
	
	@PostMapping("/start")
	public GameState stateGame() {
		return gameService.startNewGame();
	}
	
	@PostMapping("/{gameId}/guess")
	public GameState makeGuess(
			@PathVariable String gameId,
			@RequestBody GuessRequest request) {
		return gameService.makeGuess(gameId, request.getGuess());
		
	}
	
	@GetMapping("/{gameId}")
	public GameState getGameState(@PathVariable String gameId) {
		return gameService.getGameState(gameId);
	}
	
	public static class GuessRequest{
		private String guess;
		
		public String getGuess() {
			return guess;
		}
		public void setGuess(String guess) {
			this.guess = guess;
		}
	}
	

}

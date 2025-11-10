package com.hawkeye.cardgame.service;

import com.hawkeye.cardgame.model.Card;
import com.hawkeye.cardgame.model.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class GameService {
	
	private final Map<String , GameState> activeGames = new HashMap<>();
	
	
	@Autowired
	private CardService cardService;
	
	public GameState startNewGame() {
		List<Card> deck = cardService.createDeck();
		deck = cardService.shuffleDeck(deck);
		
		Card currentCard = cardService.drawCard(deck);
		Card nextCard = cardService.drawCard(deck);
	
		
		GameState gameState = new GameState();
		gameState.setGameId(UUID.randomUUID().toString());
		gameState.setDeck(deck);
		gameState.setCurrentCard(currentCard);
		gameState.setNextCard(nextCard);
		gameState.setScore(0);
		gameState.setGameOver(false);
		
		activeGames.put(gameState.getGameId(), gameState);
		return gameState;
	}
	
	public GameState makeGuess(String gameId, String guess) {
		GameState gameState = activeGames.get(gameId);
		if(gameState == null || gameState.isGameOver()) {
			return null;
		}
		
		Card currentCard = gameState.getCurrentCard();
		Card nextCard = gameState.getNextCard();
		
		boolean isCorrect = false;
		
		if("higher".equals(guess)) {
			isCorrect = nextCard.getValue() > currentCard.getValue();
		} else if("lower".equals(guess)) {
			isCorrect = nextCard.getValue() < currentCard.getValue();
		} else if("equals".equals(guess)) {
			isCorrect = nextCard.getValue() == currentCard.getValue();
		}
		
		if (isCorrect) {
			gameState.setScore(gameState.getScore() + 1);
			gameState.setCurrentCard(nextCard);
			gameState.setNextCard(cardService.drawCard(gameState.getDeck()));
			
			if(gameState.getNextCard() == null) {
				gameState.setGameOver(true);
				gameState.setMessage("You've drawn all cards! Amazing");
			}
		} else {
			gameState.setGameOver(true);
			gameState.setMessage("Wrong guess! Game over. ");
		}
		
		return gameState;
		
	}
	
	public GameState getGameState(String gameId) {
		return activeGames.get(gameId);
	}

}

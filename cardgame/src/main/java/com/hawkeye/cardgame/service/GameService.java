package com.hawkeye.cardgame.service;

import com.hawkeye.cardgame.model.Card;
import com.hawkeye.cardgame.model.GameState;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class GameService {

    private final Map<String, GameState> activeGames = new HashMap<>();

    public GameState startNewGame() {
        String gameId = UUID.randomUUID().toString();
        
        Card currentCard = drawRandomCard();
        Card nextCard = drawRandomCard();
        
        GameState gameState = new GameState(gameId, currentCard, nextCard, 0, false, "Game started! Make your guess.");
        activeGames.put(gameId, gameState);
        
        return gameState;
    }

    
    
    public GameState makeGuess(String gameId, String guess) {
        GameState gameState = activeGames.get(gameId);
        if (gameState == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }

        if (gameState.isGameOver()) {
            return gameState;
        }

        if (guess == null || (!guess.equalsIgnoreCase("higher") && !guess.equalsIgnoreCase("lower"))) {
            throw new IllegalArgumentException("Guess must be 'higher' or 'lower'");
        }

        Card currentCard = gameState.getCurrentCard();
        Card nextCard = gameState.getNextCard();

        boolean isCorrect = false;

        if ("higher".equalsIgnoreCase(guess)) {
            isCorrect = nextCard.getValue() > currentCard.getValue();
        } else if ("lower".equalsIgnoreCase(guess)) {
            isCorrect = nextCard.getValue() < currentCard.getValue();
        }

        if (isCorrect) {
            int newScore = gameState.getScore() + 1;
            Card newNextCard = drawRandomCard();
            // Update the existing game state instead of creating a new one
            gameState.setCurrentCard(nextCard);
            gameState.setNextCard(newNextCard);
            gameState.setScore(newScore);
            gameState.setMessage("Correct! Your score: " + newScore);
        } else {
            gameState.setGameOver(true);
            gameState.setMessage("Game Over! Final score: " + gameState.getScore());
        }

        activeGames.put(gameId, gameState);
        return gameState;
    }

    public GameState getGameState(String gameId) {
        GameState gameState = activeGames.get(gameId);
        if (gameState == null) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }
        return gameState;
    }

    private Card drawRandomCard() {
        String[] suits = {"HEARTS", "DIAMONDS", "CLUBS", "SPADES"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        
        String suit = suits[(int) (Math.random() * suits.length)];
        String rank = ranks[(int) (Math.random() * ranks.length)];
        int value = getCardValue(rank);
        
        return new Card(suit, rank, value);
    }

    private int getCardValue(String rank) {
        switch (rank) {
            case "A": return 14;
            case "K": return 13;
            case "Q": return 12;
            case "J": return 11;
            default: return Integer.parseInt(rank);
        }
    }
}
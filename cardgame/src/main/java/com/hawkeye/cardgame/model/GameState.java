package com.hawkeye.cardgame.model;

import java.util.List;

public class GameState {
	private String gameId;
	private List<Card> deck;
	private Card currentCard;
	private Card nextCard;
	private int score;
	private boolean gameOver;
	private String message;
	
	public String getGameId() {
		return gameId;
	}
	
	public void setGameId(String gameId) {
		this.gameId = gameId;
	}
	
	public List<Card> getDeck(){return deck;}
	
	public void setDeck(List<Card> deck){
		this.deck = deck;
	}
	
	public Card getCurrentCard() {
		return currentCard;
	}
	public void setCurrentCard(Card currentCard) {
		this.currentCard = currentCard;
		
	}

	public Card getNextCard() {
		return nextCard;
	}

	public void setNextCard(Card nextCard) {
		this.nextCard = nextCard;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public boolean isGameOver() {
		return gameOver;
	}

	public void setGameOver(boolean gameOver) {
		this.gameOver = gameOver;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

}

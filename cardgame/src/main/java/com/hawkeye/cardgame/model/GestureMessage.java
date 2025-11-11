package com.hawkeye.cardgame.model;

import java.time.LocalDateTime;

public class GestureMessage {
    private String sessionId;
    private String gesture;
    private String message;
    private LocalDateTime timestamp;
    private String gameId;
    
    // 🃏 ADD THESE FIELDS
    private Card currentCard;
    private Card nextCard;
    private Integer score;
    private Boolean gameOver;

    // Constructors
    public GestureMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public GestureMessage(String sessionId, String gesture, String message) {
        this();
        this.sessionId = sessionId;
        this.gesture = gesture;
        this.message = message;
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getGesture() { return gesture; }
    public void setGesture(String gesture) { this.gesture = gesture; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    // 🃏 ADD GETTERS AND SETTERS FOR NEW FIELDS
    public Card getCurrentCard() { return currentCard; }
    public void setCurrentCard(Card currentCard) { this.currentCard = currentCard; }

    public Card getNextCard() { return nextCard; }
    public void setNextCard(Card nextCard) { this.nextCard = nextCard; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Boolean getGameOver() { return gameOver; }
    public void setGameOver(Boolean gameOver) { this.gameOver = gameOver; }

    @Override
    public String toString() {
        return "GestureMessage{" +
                "sessionId='" + sessionId + '\'' +
                ", gesture='" + gesture + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", gameId='" + gameId + '\'' +
                ", currentCard=" + currentCard +
                ", nextCard=" + nextCard +
                ", score=" + score +
                ", gameOver=" + gameOver +
                '}';
    }
}
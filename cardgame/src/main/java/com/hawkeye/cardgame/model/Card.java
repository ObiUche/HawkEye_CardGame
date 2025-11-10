package com.hawkeye.cardgame.model;



public class Card {
	private String suit;
	private String rank;
	private Integer value;
	
	public Card() {}
	
	public Card(String suit,
			String rank,
			Integer value) {
		this.suit = suit;
		this.rank = rank;
		this.value = value;
	}

	public String getSuit() {
		return suit;
	}

	public void setSuit(String suit) {
		this.suit = suit;
	}

	public String getRank() {
		return rank;
	}

	public void setRank(String rank) {
		this.rank = rank;
	}

	public Integer getValue() {
		return value;
	}

	public void setValue(Integer value) {
		this.value = value;
	}
	
	
	

}

package com.hawkeye.cardgame.service;

import com.hawkeye.cardgame.model.Card;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class CardService {
	
	public List<Card> createDeck(){
		List<Card> deck = new ArrayList<>();
		String[] suits = {"HEARTS","DIAMONDS","CLUBS","SPADES"};
		String[] ranks = {"2","3","4","5","6","7","8","9","10","J","Q","K","A"};
		
		for(String suit: suits) {
			for(int i = 0; i < ranks.length; i ++) {
				deck.add(new Card(suit,ranks[i], i +2));
			}
		}
		return deck;
	}
	
	public List<Card> shuffleDeck(List<Card> deck){
		Collections.shuffle(deck);
		
		return deck;
	}
	
	public Card drawCard(List<Card> deck) {
		if(deck == null || deck.isEmpty()) return null;
		return deck.remove(0);
	}

}

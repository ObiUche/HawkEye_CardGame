import React from 'react';
import './Card.css';

const Card = ({ suit, rank, value, isHidden = false }) => {
    if (isHidden) {
        return <div className="card hidden">?</div>;
    }

    if (!suit || !rank) {
        return <div className="card empty">No Card</div>;
    }

    const getSuitSymbol = (suit) => {
        switch(suit) {
            case 'HEARTS': return '♥';
            case 'DIAMONDS': return '♦';
            case 'CLUBS': return '♣';
            case 'SPADES': return '♠';
            default: return suit;
        }
    };

    const isRed = suit === 'HEARTS' || suit === 'DIAMONDS';

    return (
        <div className={`card ${isRed ? 'red' : 'black'}`}>
            <div className="card-top">
                <span className="rank">{rank}</span>
                <span className="suit">{getSuitSymbol(suit)}</span>
            </div>
            <div className="card-center">
                <span className="suit-large">{getSuitSymbol(suit)}</span>
            </div>
            <div className="card-bottom">
                <span className="rank">{rank}</span>
                <span className="suit">{getSuitSymbol(suit)}</span>
            </div>
        </div>
    );
};

export default Card;
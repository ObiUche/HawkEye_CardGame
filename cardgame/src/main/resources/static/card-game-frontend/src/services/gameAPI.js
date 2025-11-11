const API_BASE = "http://localhost:8080/api/game";

export const gameAPI = {
    startGame: async () => {
        try{
            const response = await fetch(`${API_BASE}/start`, {
                method: 'POST',
                headers:{
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok){
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch (error) {
            console.error('Error starting game:', error);
            throw error;
        }
    },
    makeGuess: async (gameId, guess) => {
        try {
            const response = await fetch(`${API_BASE}/${gameId}/guess` , {
                method: 'POST',
                headers:{
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ guess },)
            });

            if( !response.ok ) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            return await response.json();
        } catch(error) {
            console.error('Error making guess:', error);
            throw error;
        }
    },
    getGameState: async (gameId) => {
        try{
            const response = await fetch(`${API_BASE}/${gameId}`);

            if (!response.ok){
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error){
            console.error("Error getting game state", error);
            throw error;
        }
    },
};
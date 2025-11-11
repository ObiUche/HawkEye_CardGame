import React from 'react';
import LiveGestureGame from './components/LiveGestureGame';

import './App.css';

function App() {
  return (
    <div className="App">
      {/* You can choose which component to use: */}
      
      {/* Option 1: Just the game with manual controls */}
      {/* <LiveGestureGame /> */}
      
      {/* Option 2: Just the gesture detector */}
      {/* <GestureDetector /> */}
      
      {/* Option 3: Both components (if you want separate pages) */}
      <div>
        <h1 style={{ textAlign: 'center', color: '#2c3e50' }}>HawkEye Card Game</h1>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <LiveGestureGame />
        </div>
      </div>
    </div>
  );
}

export default App;
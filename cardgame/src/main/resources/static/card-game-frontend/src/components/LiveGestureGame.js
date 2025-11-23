import React, { useState, useEffect, useRef } from 'react';
import { webSocketService } from '../services/websocketService';
import { gameAPI } from '../services/gameAPI';
import Card from './Card';
import Webcam from 'react-webcam';
import { Hands, HAND_CONNECTIONS } from '@mediapipe/hands';
import { Camera } from '@mediapipe/camera_utils';
import { drawConnectors, drawLandmarks } from '@mediapipe/drawing_utils';
import './LiveGestureGame.css';

const LiveGestureGame = () => {
  const [gameState, setGameState] = useState(null);
  const [gesture, setGesture] = useState('none');
  const [isDetecting, setIsDetecting] = useState(false);
  const [message, setMessage] = useState('Start a new game to begin!');
  const [sessionId] = useState(`user-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`);
  const [cameraAvailable, setCameraAvailable] = useState(true);
  const [debugInfo, setDebugInfo] = useState('');
  const [wsConnected, setWsConnected] = useState(false);
  
  const [lastGestureTime, setLastGestureTime] = useState(0);
  const [lastResetTime, setLastResetTime] = useState(0);
  const [cardUpdateDelay, setCardUpdateDelay] = useState(false);
  const [globalCooldownUntil, setGlobalCooldownUntil] = useState(0); // NEW: Global cooldown state
  
  const webcamRef = useRef(null);
  const canvasRef = useRef(null);
  const gameIdRef = useRef(null);
  const detectionRef = useRef(false);
  const gestureHistoryRef = useRef([]);
  const handsRef = useRef(null);
  const cameraRef = useRef(null);

  // Initialize MediaPipe Hands
  useEffect(() => {
    const initializeMediaPipe = async () => {
      try {
        console.log('🚀 Initializing MediaPipe Hands...');
        
        const hands = new Hands({
          locateFile: (file) => {
            return `https://cdn.jsdelivr.net/npm/@mediapipe/hands/${file}`;
          }
        });

        hands.setOptions({
          selfieMode: false,
          maxNumHands: 2,
          modelComplexity: 1,
          minDetectionConfidence: 0.5,
          minTrackingConfidence: 0.5
        });

        hands.onResults((results) => {
          if (detectionRef.current) {
            handleHandResults(results);
          }
        });

        handsRef.current = hands;
        console.log('✅ MediaPipe Hands initialized');
        setMessage('Model loaded! Start a new game and enable detection.');
      } catch (error) {
        console.error('❌ Error initializing MediaPipe:', error);
        setMessage('Error loading hand detection model. Please refresh the page.');
      }
    };

    initializeMediaPipe();

    return () => {
      if (cameraRef.current) {
        cameraRef.current.stop();
      }
    };
  }, []);

  // Initialize WebSocket connection
  useEffect(() => {
    const handleGestureUpdate = (gestureData) => {
      console.log('📨 WebSocket message received:', gestureData);
      
      if (gestureData.gesture) {
        setGesture(gestureData.gesture);
        
        if (gestureData.gesture === 'reset') {
          console.log('🔄 Reset gesture received, updating game state');
          if (gestureData.currentCard && gestureData.nextCard) {
            setGameState({
              gameId: gestureData.gameId,
              currentCard: gestureData.currentCard,
              nextCard: gestureData.nextCard,
              score: gestureData.score || 0,
              gameOver: gestureData.gameOver || false,
              message: gestureData.message
            });
            gameIdRef.current = gestureData.gameId;
          }
        }
        else if (gestureData.currentCard && gestureData.nextCard) {
          console.log('🃏 Game state update received');
          
          setCardUpdateDelay(true);
          setTimeout(() => {
            setGameState({
              gameId: gestureData.gameId,
              currentCard: gestureData.currentCard,
              nextCard: gestureData.nextCard,
              score: gestureData.score || 0,
              gameOver: gestureData.gameOver || false,
              message: gestureData.message
            });
            setCardUpdateDelay(false);
          }, 500);
        }
      }
      
      if (gestureData.message) {
        setMessage(gestureData.message);
      }
    };

    webSocketService.connect(sessionId, handleGestureUpdate);
    
    const checkConnection = setInterval(() => {
      setWsConnected(webSocketService.isWebSocketConnected());
    }, 1000);
    
    return () => {
      clearInterval(checkConnection);
      webSocketService.disconnect();
    };
  }, [sessionId]);

  const startNewGame = async () => {
    try {
      setMessage('Starting new game...');
      const newGameState = await gameAPI.startGame();
      setGameState(newGameState);
      gameIdRef.current = newGameState.gameId;
      setMessage('Game started! Click "Start Detection" and show your hand(s).');
      
    } catch (error) {
      setMessage('Error starting game: ' + error.message);
      console.error('Error starting game:', error);
    }
  };

  const makeGuess = async (guess) => {
    if (!gameState || gameState.gameOver || !gameIdRef.current) return;
    
    try {
      setMessage(`Processing ${guess} guess...`);
      console.log('🎯 Making API call with gameId:', gameIdRef.current, 'guess:', guess);
      
      const updatedGameState = await gameAPI.makeGuess(gameIdRef.current, guess);
      
      console.log('🃏 API Response:', updatedGameState);
      
      setCardUpdateDelay(true);
      setTimeout(() => {
        setGameState(updatedGameState);
        setCardUpdateDelay(false);
        
        if (updatedGameState.message) {
          setMessage(updatedGameState.message);
        } else {
          setMessage(`Guess processed! Score: ${updatedGameState.score}`);
        }
        
        if (updatedGameState.gameOver) {
          console.log('🎮 Game over!');
          stopGestureDetection();
        }
      }, 500);
      
    } catch (error) {
      console.error('❌ Error in makeGuess:', error);
      setMessage('Error making guess: ' + error.message);
    }
  };

  const restartGame = () => {
    setGameState(null);
    setGesture('none');
    gestureHistoryRef.current = [];
    gameIdRef.current = null;
    setGlobalCooldownUntil(0); // Reset cooldown on restart
    setMessage('Start a new game to begin!');
    startNewGame();
  };

  // Count extended fingers for reset detection
  const countExtendedFingers = (landmarks) => {
    const fingerTips = [4, 8, 12, 16, 20];
    const fingerJoints = [2, 5, 9, 13, 17];
    
    let extendedCount = 0;
    
    fingerTips.forEach((tipIndex, i) => {
      const tip = landmarks[tipIndex];
      const joint = landmarks[fingerJoints[i]];
      
      if (i === 0) {
        const thumbExtended = tip.x > joint.x;
        if (thumbExtended) extendedCount++;
      } else {
        const fingerExtended = tip.y < joint.y;
        if (fingerExtended) extendedCount++;
      }
    });
    
    return extendedCount;
  };

  const classifyGesture = (results) => {
    if (!results.multiHandLandmarks || results.multiHandLandmarks.length === 0) {
      return 'none';
    }
    
    const hands = results.multiHandLandmarks;
    const handedness = results.multiHandedness;
    
    if (hands.length === 1) {
      const handLabel = handedness[0].label;
      console.log(`Detected single hand: ${handLabel}`);
      
      if (handLabel === 'Left') {
        return 'higher';
      } else if (handLabel === 'Right') {
        return 'lower';
      }
    }
    
    if (hands.length === 2) {
      const fingers1 = countExtendedFingers(hands[0]);
      const fingers2 = countExtendedFingers(hands[1]);
      
      if (fingers1 >= 4 && fingers2 >= 4) {
        return 'reset';
      }
      
      const hand1Label = handedness[1].label;
      const hand2Label = handedness[0].label;
      
      console.log(`Detected hands: ${hand1Label} and ${hand2Label}`);
      
      if (hand1Label !== hand2Label) {
        return 'reset';
      }
      else if (hand1Label === 'Left') {
        return 'higher';
      } else {
        return 'lower';
      }
    }
    
    return 'none';
  };

  const getStableGesture = (currentGesture) => {
    const newHistory = [...gestureHistoryRef.current, currentGesture].slice(-5);
    gestureHistoryRef.current = newHistory;
    
    const gestureCounts = {};
    newHistory.forEach(g => {
      gestureCounts[g] = (gestureCounts[g] || 0) + 1;
    });
    
    for (const [gest, count] of Object.entries(gestureCounts)) {
      if (count >= 3 && gest !== 'none') {
        return gest;
      }
    }
    
    return 'none';
  };

 const handleHandResults = (results) => {
  const now = Date.now();
  
  console.log('🔍 ENTER handleHandResults - cooldownUntil:', globalCooldownUntil, 'now:', now);
  
  // Check global cooldown first
  if (now < globalCooldownUntil) {
    const remainingCooldown = Math.ceil((globalCooldownUntil - now) / 1000);
    console.log('⏸️  IN COOLDOWN - returning early');
    setDebugInfo(`⏳ Global cooldown: ${remainingCooldown}s remaining`);
    return;
  }
  
  console.log('✅ NOT in cooldown, proceeding...');
  
  const canvas = canvasRef.current;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  
  let hasHands = results.multiHandLandmarks && results.multiHandLandmarks.length > 0;
  console.log('✋ Has hands detected:', hasHands);
  
  if (results.multiHandLandmarks) {
    results.multiHandLandmarks.forEach((landmarks, index) => {
      const handedness = results.multiHandedness[index];
      const color = handedness.label === 'Left' ? '#4ecdc4' : '#ff6b6b';
      
      drawLandmarks(ctx, landmarks, {
        color: color,
        fillColor: color,
        lineWidth: 2,
        radius: 3
      });
      
      drawConnectors(ctx, landmarks, HAND_CONNECTIONS, {
        color: color,
        lineWidth: 4
      });
    });
  }
  
  if (hasHands) {
    const rawGesture = classifyGesture(results);
    console.log('🎭 Raw gesture:', rawGesture);
    
    const stableGesture = getStableGesture(rawGesture);
    console.log('🎯 Stable gesture:', stableGesture);
    
    if (stableGesture !== gesture) {
      setGesture(stableGesture);
    }
    
    console.log('🆔 gameIdRef.current:', gameIdRef.current);
    console.log('❓ Conditions - stableGesture !== "none":', stableGesture !== 'none');
    console.log('❓ Conditions - gameIdRef.current exists:', !!gameIdRef.current);
    
    // CRITICAL: Check if we meet the conditions to send a gesture
    if (stableGesture !== 'none' && gameIdRef.current) {
      console.log('🚨 ALL CONDITIONS MET - READY TO SEND GESTURE AND SET COOLDOWN');
      
      const timeSinceLastGesture = now - lastGestureTime;
      const timeSinceLastReset = now - lastResetTime;
      const gestureCooldown = stableGesture === 'reset' ? 2000 : 800;
      const timeSinceLastRelevant = stableGesture === 'reset' ? timeSinceLastReset : timeSinceLastGesture;
      
      console.log('⏰ Timing - timeSinceLastRelevant:', timeSinceLastRelevant, 'gestureCooldown:', gestureCooldown);
      console.log('⏰ Timing - condition met:', timeSinceLastRelevant === 0 || timeSinceLastRelevant > gestureCooldown);
      
      if (timeSinceLastRelevant === 0 || timeSinceLastRelevant > gestureCooldown) {
        console.log('🚀 SENDING GESTURE:', stableGesture);
        webSocketService.sendGesture(stableGesture, gameIdRef.current);
        
        console.log('⏰ SETTING COOLDOWN UNTIL:', now + 5000);
        setGlobalCooldownUntil(now + 5000);
        
        if (stableGesture === 'reset') {
          setLastResetTime(now);
        } else {
          setLastGestureTime(now);
        }
        
        setDebugInfo(`✅ ${stableGesture.toUpperCase()} sent! (5s cooldown)`);
        console.log('✅ Cooldown should be set now');
        
        setTimeout(() => {
          gestureHistoryRef.current = [];
        }, 200);
      } else {
        console.log('⏳ Waiting for gesture-specific cooldown');
        const remaining = Math.ceil((gestureCooldown - timeSinceLastRelevant) / 1000);
        setDebugInfo(`⏳ ${stableGesture.toUpperCase()} ready in ${remaining}s`);
      }
    } else {
      if (stableGesture === 'none') {
        console.log('❌ Stable gesture is "none" - not sending');
      } else if (!gameIdRef.current) {
        console.log('❌ No game ID - not sending');
      }
      
      const handInfo = results.multiHandedness ? results.multiHandedness.map(hand => hand.label).join(' & ') : 'none';
      setDebugInfo(`Hands: ${handInfo} | Gesture: ${stableGesture}`);
    }
  } else {
    gestureHistoryRef.current = [];
    setGesture('none');
    setDebugInfo('No hands detected - show your hand(s) to the camera');
  }
  
  console.log('🔚 EXIT handleHandResults');
};

  const startGestureDetection = () => {
    if (!handsRef.current) {
      setMessage('Hand detection model still loading. Please wait...');
      return;
    }
    if (!cameraAvailable) {
      setMessage('Camera not available. Please allow camera access.');
      return;
    }
    if (!wsConnected) {
      setMessage('WebSocket not connected. Please wait for connection...');
      return;
    }
    
    const video = webcamRef.current.video;
    if (!video) {
      setMessage('Camera not ready. Please wait...');
      return;
    }
    
    detectionRef.current = true;
    setIsDetecting(true);
    setLastGestureTime(0);
    setLastResetTime(0);
    setGlobalCooldownUntil(0); // Reset cooldown when starting detection
    gestureHistoryRef.current = [];
    setMessage('Detection started! Show your hand(s) to the camera.');
    webSocketService.startRecognition(0);
    
    cameraRef.current = new Camera(video, {
      onFrame: async () => {
        if (detectionRef.current && handsRef.current) {
          await handsRef.current.send({ image: video });
        }
      },
      width: 640,
      height: 480
    });
    cameraRef.current.start();
  };

  const stopGestureDetection = () => {
    detectionRef.current = false;
    setIsDetecting(false);
    setGesture('none');
    gestureHistoryRef.current = [];
    setGlobalCooldownUntil(0); // Reset cooldown when stopping
    setMessage('Gesture detection stopped.');
    setDebugInfo('');
    webSocketService.stopRecognition();
    
    if (cameraRef.current) {
      cameraRef.current.stop();
    }
  };

  const handleCameraError = () => {
    setCameraAvailable(false);
    setMessage('Camera not available. Please check permissions and refresh.');
  };

  const handleCameraStart = () => {
    setCameraAvailable(true);
    console.log('Camera started successfully');
  };

  return (
    <div className="live-gesture-game">
      <div className="game-header">
        <h1>🎮 Hand Gesture Card Game</h1>
        <p>Use hand gestures to play Higher or Lower!</p>
      </div>

      <div className="main-container">
        <div className="game-content">
          <div className="connection-status">
            <div className={`status-indicator ${wsConnected ? 'connected' : 'disconnected'}`}>
              WebSocket: {wsConnected ? '✅ Connected' : '❌ Disconnected'}
            </div>
            <div className={`status-indicator ${handsRef.current ? 'connected' : 'disconnected'}`}>
              Hand Model: {handsRef.current ? '✅ Loaded' : '❌ Loading...'}
            </div>
            <div className="status-indicator connected">
              Detection: MediaPipe (5s Cooldown)
            </div>
          </div>

          <div className="game-stats">
            <div className="stat">
              <span className="stat-label">Score:</span>
              <span className="stat-value">{gameState?.score || 0}</span>
            </div>
            <div className="stat">
              <span className="stat-label">Status:</span>
              <span className={`stat-value ${gameState?.gameOver ? 'game-over' : 'game-active'}`}>
                {gameState?.gameOver ? 'Game Over' : 'Active'}
              </span>
            </div>
            <div className="stat">
              <span className="stat-label">Detection:</span>
              <span className={`stat-value ${isDetecting ? 'game-active' : 'game-over'}`}>
                {isDetecting ? '🔴 Active' : '⚪ Inactive'}
              </span>
            </div>
          </div>

          <div className="cards-container">
            <div className="card-section">
              <h3>Current Card</h3>
              {gameState?.currentCard ? (
                <Card 
                  suit={gameState.currentCard.suit} 
                  rank={gameState.currentCard.rank}
                  value={gameState.currentCard.value}
                />
              ) : (
                <div className="card-placeholder">No card</div>
              )}
            </div>

            <div className="vs-separator">
              <div className="vs-circle">VS</div>
            </div>

            <div className="card-section">
              <h3>Next Card</h3>
              {gameState?.nextCard ? (
                <Card 
                  suit={gameState.nextCard.suit} 
                  rank={gameState.nextCard.rank}
                  value={gameState.nextCard.value}
                  isHidden={!gameState.gameOver}
                />
              ) : (
                <div className="card-placeholder">No card</div>
              )}
            </div>
          </div>

          <div className="gesture-status">
            <div className={`gesture-indicator ${gesture}`}>
              <span className="gesture-label">
                {gesture === 'higher' ? '👉 HIGHER' : 
                 gesture === 'lower' ? '👈 LOWER' : 
                 gesture === 'reset' ? '👐 RESET' :
                 '🤔 NO GESTURE'}
              </span>
              {gestureHistoryRef.current.length > 0 && (
                <span className="stability-indicator">
                  Stability: {Math.min(gestureHistoryRef.current.length, 5)}/5
                </span>
              )}
            </div>
          </div>

          <div className="game-message">
            {message}
            {cardUpdateDelay && <span className="update-delay"> (Updating...)</span>}
          </div>

          {debugInfo && (
            <div className="debug-info">
              <small>{debugInfo}</small>
            </div>
          )}

          <div className="game-controls">
            {!gameState ? (
              <button className="btn btn-primary" onClick={startNewGame}>
                🎮 Start New Game
              </button>
            ) : gameState.gameOver ? (
              <button className="btn btn-secondary" onClick={restartGame}>
                🔄 Play Again
              </button>
            ) : (
              <div className="gesture-controls">
                <button 
                  className={`btn ${isDetecting ? 'btn-warning' : 'btn-success'}`}
                  onClick={isDetecting ? stopGestureDetection : startGestureDetection}
                  disabled={!handsRef.current || !cameraAvailable || !wsConnected || cardUpdateDelay}
                >
                  {!handsRef.current ? '🤖 Loading Model...' : 
                   !cameraAvailable ? '📷 No Camera' :
                   !wsConnected ? '🔌 Connecting...' :
                   cardUpdateDelay ? '⏳ Updating...' :
                   isDetecting ? '🛑 Stop Detection' : '🎯 Start Detection'}
                </button>
                
                <div className="manual-controls">
                  <button className="btn btn-higher" onClick={() => makeGuess('higher')} disabled={cardUpdateDelay}>
                    👉 Guess Higher
                  </button>
                  <button className="btn btn-lower" onClick={() => makeGuess('lower')} disabled={cardUpdateDelay}>
                    👈 Guess Lower
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        <div className="camera-content">
          <div className="camera-section">
            <h3>🎥 Hand Camera</h3>
            {cameraAvailable ? (
              <div className="camera-container">
                <Webcam
                  ref={webcamRef}
                  audio={false}
                  screenshotFormat="image/jpeg"
                  onUserMediaError={handleCameraError}
                  onUserMedia={handleCameraStart}
                  videoConstraints={{
                    width: { ideal: 640 },
                    height: { ideal: 480 },
                    facingMode: "user",
                    aspectRatio: { ideal: 1.7777777778 }
                  }}
                  style={{ 
                    width: '100%', 
                    height: '300px',
                    borderRadius: '10px',
                    border: '3px solid #3498db',
                    transform: 'scaleX(-1)',
                    display: cameraAvailable ? 'block' : 'none'
                  }}
                  forceScreenshotSourceSize={true}
                />
                <canvas
                  ref={canvasRef}
                  className="hand-canvas"
                  width={640}
                  height={480}
                  style={{
                    transform: 'scaleX(-1)',
                  }}
                />
                <div className="camera-overlay">
                  <div className={`detection-status ${isDetecting ? 'active' : 'inactive'}`}>
                    {isDetecting ? '🔴 Live (5s Cooldown)' : '⚪ Ready'}
                  </div>
                </div>
              </div>
            ) : (
              <div className="camera-placeholder">
                📷 Camera not available
                <br />
                <span>Please allow camera access and refresh</span>
              </div>
            )}

            <div className="gesture-instructions compact">
              <h4>Gesture Controls (5s Cooldown):</h4>
              <div className="instruction-item">
                <span className="gesture-demo" style={{color: '#4ecdc4'}}>👉</span>
                <span><strong>RIGHT HAND</strong> for "HIGHER"</span>
              </div>
              <div className="instruction-item">
                <span className="gesture-demo" style={{color: '#ff6b6b'}}>👈</span>
                <span><strong>LEFT HAND</strong> for "LOWER"</span>
              </div>
              <div className="instruction-item">
                <span className="gesture-demo">👐</span>
                <span><strong>BOTH HANDS OPEN</strong> for "RESET"</span>
              </div>
              <div className="instruction-item">
                <span className="gesture-demo">⏱️</span>
                <span><strong>5-second cooldown</strong> after any gesture</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LiveGestureGame;
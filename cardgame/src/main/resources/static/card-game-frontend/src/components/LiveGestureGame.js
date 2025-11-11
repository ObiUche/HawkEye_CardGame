import React, { useState, useEffect, useRef } from 'react';
import { webSocketService } from '../services/websocketService';
import { gameAPI } from '../services/gameAPI';
import Card from './Card';
import Webcam from 'react-webcam';
import * as tf from '@tensorflow/tfjs';
import * as handpose from '@tensorflow-models/handpose';
import './LiveGestureGame.css';

const LiveGestureGame = () => {
  const [gameState, setGameState] = useState(null);
  const [gesture, setGesture] = useState('none');
  const [isDetecting, setIsDetecting] = useState(false);
  const [message, setMessage] = useState('Start a new game to begin!');
  const [sessionId] = useState(`user-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`);
  const [model, setModel] = useState(null);
  const [cameraAvailable, setCameraAvailable] = useState(true);
  const [debugInfo, setDebugInfo] = useState('');
  const [wsConnected, setWsConnected] = useState(false);
  
  // Enhanced gesture detection states
  const [lastGestureTime, setLastGestureTime] = useState(0);
  
  const webcamRef = useRef(null);
  const canvasRef = useRef(null);
  const gameIdRef = useRef(null);
  const detectionRef = useRef(false);
  const gestureHistoryRef = useRef([]);

  // Load HandPose model
  useEffect(() => {
    const loadModel = async () => {
      try {
        console.log('🚀 Loading TensorFlow.js...');
        await tf.ready();
        console.log('✅ TensorFlow.js loaded');
        
        console.log('🚀 Loading HandPose model...');
        const handposeModel = await handpose.load();
        setModel(handposeModel);
        console.log('✅ HandPose model loaded');
        setMessage('Model loaded! Start a new game and enable detection.');
      } catch (error) {
        console.error('❌ Error loading models:', error);
        setMessage('Error loading AI model. Please refresh the page.');
      }
    };
    loadModel();
  }, []);

  // Initialize WebSocket connection
  useEffect(() => {
    const handleGestureUpdate = (gestureData) => {
      console.log('📨 WebSocket message received:', gestureData);
      
      if (gestureData.gesture) {
        setGesture(gestureData.gesture);
        
        // Handle reset gesture
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
        // Handle game gestures (higher/lower)
        else if (gestureData.currentCard && gestureData.nextCard) {
          console.log('🃏 Game state update received');
          setGameState({
            gameId: gestureData.gameId,
            currentCard: gestureData.currentCard,
            nextCard: gestureData.nextCard,
            score: gestureData.score || 0,
            gameOver: gestureData.gameOver || false,
            message: gestureData.message
          });
        }
      }
      
      if (gestureData.message) {
        setMessage(gestureData.message);
      }
    };

    webSocketService.connect(sessionId, handleGestureUpdate);
    
    // Check WebSocket connection status
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
      setMessage('Game started! Click "Start Detection" and show your hand.');
      
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
      setGameState(updatedGameState);
      
      if (updatedGameState.message) {
        setMessage(updatedGameState.message);
      } else {
        setMessage(`Guess processed! Score: ${updatedGameState.score}`);
      }
      
      // If game over, stop detection
      if (updatedGameState.gameOver) {
        console.log('🎮 Game over!');
        stopGestureDetection();
      }
      
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
    setMessage('Start a new game to begin!');
    startNewGame();
  };

  // Fixed gesture stabilization
  const getStableGesture = (currentGesture) => {
    // Add current gesture to history (keep last 5 frames)
    const newHistory = [...gestureHistoryRef.current, currentGesture].slice(-5);
    gestureHistoryRef.current = newHistory;
    
    // Require at least 3 out of 5 frames to have the same gesture
    const gestureCounts = {};
    newHistory.forEach(g => {
      gestureCounts[g] = (gestureCounts[g] || 0) + 1;
    });
    
    // Find the most frequent gesture that appears at least 3 times
    for (const [gest, count] of Object.entries(gestureCounts)) {
      if (count >= 3 && gest !== 'none') {
        return gest;
      }
    }
    
    return 'none';
  };

  const startGestureDetection = () => {
    if (!model) {
      setMessage('AI model still loading. Please wait...');
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
    
    detectionRef.current = true;
    setIsDetecting(true);
    setLastGestureTime(0); // Reset cooldown
    gestureHistoryRef.current = []; // Clear gesture history
    setMessage('Detection started! Show your hand to the camera.');
    webSocketService.startRecognition(0);
    detectGestures();
  };

  const stopGestureDetection = () => {
    detectionRef.current = false;
    setIsDetecting(false);
    setGesture('none');
    gestureHistoryRef.current = [];
    setMessage('Gesture detection stopped.');
    setDebugInfo('');
    webSocketService.stopRecognition();
  };

  const detectGestures = async () => {
    if (!detectionRef.current || !model || !webcamRef.current) return;

    try {
      const video = webcamRef.current.video;
      if (!video || video.readyState !== 4) {
        // Use setTimeout instead of requestAnimationFrame for frame rate control
        setTimeout(detectGestures, 100); // 10fps instead of 60fps
        return;
      }

      // Get hand predictions
      const predictions = await model.estimateHands(video, false);
      
      // Clear canvas
      const canvas = canvasRef.current;
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      if (predictions.length > 0) {
        const hand = predictions[0];
        const rawGesture = classifyGesture(hand);
        
        // Apply gesture stabilization
        const stableGesture = getStableGesture(rawGesture);
        
        // Only update displayed gesture if it's stable
        if (stableGesture !== gesture) {
          setGesture(stableGesture);
        }
        
        drawHand(hand.landmarks, ctx);
        
        // Enhanced cooldown system
        const now = Date.now();
        const timeSinceLastGesture = now - lastGestureTime;
        
        // Only process gestures that are stable and not 'none'
        if (stableGesture !== 'none' && gameIdRef.current && (lastGestureTime === 0 || timeSinceLastGesture > 800)) {
          console.log(`🎯 Sending stable gesture: ${stableGesture} for game: ${gameIdRef.current}`);
          webSocketService.sendGesture(stableGesture, gameIdRef.current);
          setLastGestureTime(now);
          
          setDebugInfo(`✅ ${stableGesture.toUpperCase()} gesture sent! (Stable)`);
        } else if (stableGesture !== 'none' && stableGesture !== gesture) {
          // Show when gesture is stabilizing
          setDebugInfo(`🔄 Stabilizing: ${rawGesture} → ${stableGesture}`);
        } else if (stableGesture !== 'none') {
          // Show when gesture is detected but waiting for cooldown
          const remainingCooldown = Math.ceil((800 - timeSinceLastGesture) / 1000);
          setDebugInfo(`⏳ ${stableGesture.toUpperCase()} ready in ${remainingCooldown}s`);
        } else {
          setDebugInfo(`Hand detected! Raw: ${rawGesture} | Stable: ${stableGesture}`);
        }
      } else {
        // No hand detected - reset gesture history
        gestureHistoryRef.current = [];
        setGesture('none');
        setDebugInfo('No hand detected - show your hand to the camera');
      }

      // Continue detection with controlled frame rate (10fps)
      if (detectionRef.current) {
        setTimeout(detectGestures, 100); // 100ms = 10fps
      }
    } catch (error) {
      console.error('Error in gesture detection:', error);
      setDebugInfo('Detection error: ' + error.message);
      
      // Continue with error recovery
      if (detectionRef.current) {
        setTimeout(detectGestures, 100);
      }
    }
  };

  const countExtendedFingers = (hand) => {
    const landmarks = hand.landmarks;
    const fingerTips = [4, 8, 12, 16, 20];
    const fingerJoints = [2, 5, 9, 13, 17];
    
    let extendedCount = 0;
    
    fingerTips.forEach((tipIndex, i) => {
      const tip = landmarks[tipIndex];
      const joint = landmarks[fingerJoints[i]];
      
      if (i === 0) {
        // Thumb extension check
        const thumbExtended = tip[0] > joint[0];
        if (thumbExtended) extendedCount++;
      } else {
        // For other fingers
        const fingerExtended = tip[1] < joint[1];
        if (fingerExtended) extendedCount++;
      }
    });
    
    return extendedCount;
  };

  const classifyGesture = (hand) => {
    const extendedFingers = countExtendedFingers(hand);
    
    // Reduced logging to prevent console spam
    if (Math.random() < 0.1) { // Only log 10% of the time
      console.log(`Extended fingers: ${extendedFingers}`);
    }
    
    if (extendedFingers >= 4) {
      return 'higher';
    } else if (extendedFingers <= 1) {
      return 'lower';
    } else if (extendedFingers === 2) {
      return 'reset';
    } else {
      return 'none';
    }
  };

  const drawHand = (landmarks, ctx) => {
    if (!landmarks) return;
    
    ctx.fillStyle = '#00ff00';
    ctx.strokeStyle = '#00ff00';
    ctx.lineWidth = 3;

    landmarks.forEach((point) => {
      ctx.beginPath();
      ctx.arc(point[0], point[1], 4, 0, 2 * Math.PI);
      ctx.fill();
    });

    const connections = [
      [0, 1, 2, 3, 4],
      [0, 5, 6, 7, 8],
      [0, 9, 10, 11, 12],
      [0, 13, 14, 15, 16],
      [0, 17, 18, 19, 20]
    ];

    connections.forEach(finger => {
      for (let i = 0; i < finger.length - 1; i++) {
        const start = landmarks[finger[i]];
        const end = landmarks[finger[i + 1]];
        ctx.beginPath();
        ctx.moveTo(start[0], start[1]);
        ctx.lineTo(end[0], end[1]);
        ctx.stroke();
      }
    });
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
        {/* Left Side: Game Cards and Controls */}
        <div className="game-content">
          {/* Connection Status */}
          <div className="connection-status">
            <div className={`status-indicator ${wsConnected ? 'connected' : 'disconnected'}`}>
              WebSocket: {wsConnected ? '✅ Connected' : '❌ Disconnected'}
            </div>
            <div className={`status-indicator ${model ? 'connected' : 'disconnected'}`}>
              AI Model: {model ? '✅ Loaded' : '❌ Loading...'}
            </div>
            <div className="status-indicator connected">
              Detection Rate: 10fps (Stable)
            </div>
          </div>

          {/* Game Stats */}
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

          {/* Cards Display */}
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

          {/* Gesture Status */}
          <div className="gesture-status">
            <div className={`gesture-indicator ${gesture}`}>
              <span className="gesture-label">
                {gesture === 'higher' ? '👆 HIGHER' : 
                 gesture === 'lower' ? '👇 LOWER' : 
                 gesture === 'reset' ? '✌️ RESET' :
                 '🤔 NO GESTURE'}
              </span>
              {gestureHistoryRef.current.length > 0 && (
                <span className="stability-indicator">
                  Stability: {Math.min(gestureHistoryRef.current.length, 5)}/5
                </span>
              )}
            </div>
          </div>

          {/* Game Message */}
          <div className="game-message">
            {message}
          </div>

          {/* Debug Info */}
          {debugInfo && (
            <div className="debug-info">
              <small>{debugInfo}</small>
            </div>
          )}

          {/* Controls */}
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
                  disabled={!model || !cameraAvailable || !wsConnected}
                >
                  {!model ? '🤖 Loading AI...' : 
                   !cameraAvailable ? '📷 No Camera' :
                   !wsConnected ? '🔌 Connecting...' :
                   isDetecting ? '🛑 Stop Detection' : '🎯 Start Detection'}
                </button>
                
                <div className="manual-controls">
                  <button className="btn btn-higher" onClick={() => makeGuess('higher')}>
                    👆 Guess Higher
                  </button>
                  <button className="btn btn-lower" onClick={() => makeGuess('lower')}>
                    👇 Guess Lower
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right Side: Camera Feed */}
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
                    transform: 'scaleX(-1)'
                  }}
                />
                <div className="camera-overlay">
                  <div className={`detection-status ${isDetecting ? 'active' : 'inactive'}`}>
                    {isDetecting ? '🔴 Live (10fps)' : '⚪ Ready'}
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

            {/* Gesture Instructions */}
            <div className="gesture-instructions compact">
              <h4>Gesture Controls:</h4>
              <div className="instruction-item">
                <span className="gesture-demo">👋</span>
                <span><strong>OPEN PALM</strong> for "HIGHER"</span>
              </div>
              <div className="instruction-item">
                <span className="gesture-demo">✊</span>
                <span><strong>CLOSED FIST</strong> for "LOWER"</span>
              </div>
              <div className="instruction-item">
                <span className="gesture-demo">✌️</span>
                <span><strong>TWO FINGERS</strong> for "RESET"</span>
              </div>
              <div className="instruction-item">
                <span className="gesture-demo">⏱️</span>
                <span>0.8s cooldown + stabilization</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LiveGestureGame;
package com.hawkeye.cardgame.controller;

import com.hawkeye.cardgame.service.TensorFlowGestureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class TensorFlowGestureController {  // Fixed class name

    @Autowired
    private TensorFlowGestureService gestureService;

    // This receives gestures from TensorFlow.js frontend
    @MessageMapping("/tensorflow/gesture.detect")
    public void handleTensorFlowGesture(@Payload Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        String gesture = (String) payload.get("gesture");
        String gameId = (String) payload.get("gameId");

        System.out.println("🎯 Received TensorFlow gesture: " + gesture + " from session: " + sessionId);

        gestureService.processGestureFromFrontend(sessionId, gesture, gameId);
    }

    // Register session when frontend connects
    @MessageMapping("/tensorflow/gesture.register")
    public void registerGestureSession(@Payload Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        gestureService.registerSession(sessionId);
    }

    // Unregister session when frontend disconnects
    @MessageMapping("/tensorflow/gesture.unregister")
    public void unregisterGestureSession(@Payload Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        gestureService.unregisterSession(sessionId);
    }

    // Add start/stop recognition methods
    @MessageMapping("/tensorflow/gesture.start")
    public void startGestureRecognition(@Payload Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        Integer cameraIndex = (Integer) payload.get("cameraIndex");
        if (cameraIndex == null) cameraIndex = 0;
        
        gestureService.startRecognition(sessionId, cameraIndex);
    }

    @MessageMapping("/tensorflow/gesture.stop")
    public void stopGestureRecognition(@Payload Map<String, Object> payload) {
        String sessionId = (String) payload.get("sessionId");
        gestureService.stopRecognition(sessionId);
    }
}
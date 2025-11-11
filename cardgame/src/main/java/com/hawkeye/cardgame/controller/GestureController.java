package com.hawkeye.cardgame.controller;

import com.hawkeye.cardgame.model.GestureMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class GestureController {

    private final SimpMessagingTemplate messagingTemplate;

    public GestureController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/gesture.register")
    @SendTo("/topic/gesture-updates")
    public String registerSession(@RequestBody String sessionId) {
        System.out.println("Session registered: " + sessionId);
        return "Session " + sessionId + " registered";
    }

    @MessageMapping("/gesture.start")
    public void startRecognition(@RequestBody GestureMessage message) {
        System.out.println("Starting gesture recognition for session: " + message.getSessionId());
        messagingTemplate.convertAndSend("/topic/gesture/" + message.getSessionId(), 
            new GestureMessage(message.getSessionId(), "started", "Gesture recognition started"));
    }

    @MessageMapping("/gesture.stop")
    public void stopRecognition(@RequestBody GestureMessage message) {
        System.out.println("Stopping gesture recognition for session: " + message.getSessionId());
        messagingTemplate.convertAndSend("/topic/gesture/" + message.getSessionId(), 
            new GestureMessage(message.getSessionId(), "stopped", "Gesture recognition stopped"));
    }

    @MessageMapping("/gesture.detect")
    public void handleGesture(@RequestBody GestureMessage message) {
        System.out.println("Gesture detected: " + message.getGesture() + " for session: " + message.getSessionId());
        messagingTemplate.convertAndSend("/topic/gesture/" + message.getSessionId(), message);
    }
}
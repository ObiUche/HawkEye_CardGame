package com.hawkeye.cardgame.controller;

import com.hawkeye.cardgame.service.LiveGestureRecognitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class LiveGestureController {
	
	@Autowired
	private LiveGestureRecognitionService gestureService;
	
	@MessageMapping("/gesture.start")
	public void startGestureRecognition(@Payload Map<String , Object> payload) {
		String sessionId = (String) payload.get("sessionId");
		int cameraIndex = payload.get("cameraIndex") != null ? (Integer) payload.get("cameraIndex") : 0;
		
		gestureService.startLiveRecogntion(sessionId, cameraIndex);
	}
	
	@MessageMapping("/gesture.stop")
	public void stopGestureRecognition(@Payload Map<String, Object> payload) {
		String sessionId = (String) payload.get("sessionId");
		gestureService.stopLiveRecognition(sessionId);
	}

}

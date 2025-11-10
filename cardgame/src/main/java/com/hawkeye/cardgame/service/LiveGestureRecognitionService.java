package com.hawkeye.cardgame.service;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;


@Service
public class LiveGestureRecognitionService {
	
	private final SimpMessagingTemplate messagingTemplate;
	private final ScheduledExecutorService scheduler;
	private final Map<String, VideoCapture> userSessions;
	private final Map<String, Boolean> sessionStatus;
	
	public LiveGestureRecognitionService(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
		this.scheduler = Executors.newScheduledThreadPool(10);
		this.userSessions = new ConcurrentHashMap<>();
		this.sessionStatus = new ConcurrentHashMap<>();
	}
	
	@PostConstruct
	public void initialise() {
		nu.pattern.OpenCV.loadLocally();
		System.out.println("Live Gesture Recogntion Service on");
		
	}
	
	@PreDestroy
	public void cleanup() {
		scheduler.shutdown();
		userSessions.values().forEach(VideoCapture::release);
		userSessions.clear();
	}
	
	public void startLiveRecogntion(String sessionId, int cameraIndex) {
		if(sessionStatus.getOrDefault(sessionId,false)) {
			return; // already running
		}
		VideoCapture capture = new VideoCapture(cameraIndex);
		if(!capture.isOpened()) {
			messagingTemplate.convertAndSend("/topic/gesture/" + sessionId,
					createGestureResponse("error", "Could not open camera"));
			return;
		}
		userSessions.put(sessionId, capture);
		sessionStatus.put(sessionId, true);
		
		// Start processing frames
		scheduler.scheduleAtFixedRate(() -> {
			processFrame(sessionId);
		}, 0, 100, TimeUnit.MILLISECONDS); // 10 FPS
		
		messagingTemplate.convertAndSend("/topic/gesture/" + sessionId, 
				createGestureResponse("started", "Live recognition started"));
	}
	
	public void stopLiveRecognition(String sessionId) {
		sessionStatus.put(sessionId, false);
		VideoCapture capture = userSessions.remove(sessionId);
		if (capture != null) {
			capture.release();
		}
		
		messagingTemplate.convertAndSend("/topic/gesture/" + sessionId,
				createGestureResponse("stopeed", "Live recognition stopped"));
	}
	
	private void processFrame(String sessionId) {
		if(!sessionStatus.getOrDefault(sessionId, false)) {
			return;
		}
		
		VideoCapture capture = userSessions.get(sessionId);
		if(capture == null || !capture.isOpened()) {
			return;
		}
		
		Mat frame = new Mat();
		if(capture.read(frame) && !frame.empty()) {
			String gesture = analyseFrame(frame);
		}
	}
	
	private String analyseFrame(Mat frame) {
		
		// Resize frame for faster processing
		Mat resized = new Mat();
		Imgproc.resize(frame, resized, new Size(640, 480));
		
		//Convert to grayscale
		Mat grey = new Mat();
		Imgproc.cvtColor(resized, grey, Imgproc.COLOR_BGR2GRAY);
		
		// Apply Gaussian blue
		Mat blurred = new Mat();
		Imgproc.GaussianBlur(grey, blurred, new Size(15,15), 0);
		
		//Threshold
		Mat thresholded = new Mat();
		Imgproc.threshold(blurred, thresholded, 60, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
		
		//Find contours
		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierachy = new Mat();
		Imgproc.findContours(thresholded,contours, hierachy, Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
		
		if( contours.isEmpty()) {
			return "none";
		}
		
		//find largest contour
		MatOfPoint largestContour = contours.stream()
				.max(Comparator.comparingDouble(Imgproc::contourArea))
				.orElse(null);
		if(largestContour == null || Imgproc.contourArea(largestContour)< 1000) {
			return "none";
		}
		
		return classifyHandGesture(largestContour);
		
		
	}
	
	private String classifyHandGesture(MatOfPoint contour) {
		// Calculate bounding rect
		Rect boundingRect = Imgproc.boundingRect(contour);
		
		//Calculate aspect ratio
		double aspectRatio = (double) boundingRect.width / boundingRect.height;
		
		//Calculate extent (area ratio)
		
		double contourArea = Imgproc.contourArea(contour);
		double rectArea = boundingRect.width * boundingRect.height;
		double extent = contourArea / rectArea;
		
		
		// Calculate convex hull defects for finger detection
		MatOfInt hull = new MatOfInt();
		Imgproc.convexHull(contour, hull);
		
		MatOfInt4 defects = new MatOfInt4();
		Imgproc.convexityDefects(contour, hull, defects);
		
		int fingerCount = countFingersFromDefects(defects, contour);
		
		// classify based on multiple features
		
		if(fingerCount >= 4 && aspectRatio > 0.8) {
			return "higher"; // open palm
		} else if ( fingerCount <= 2 && extent > 0.7) {
			return "lower"; // closed fist
		} else if (fingerCount == 0 && aspectRatio < 1.2) {
			return "lower" ; // fist
		} else {
			return "uncertain";
		}
	}
	
	private int countFingersFromDefects(MatOfInt4 defects, MatOfPoint contour) {
		if(defects.empty()) {
			return 0;
		}
		
		int fingerCount = 0;
		Point[] contourPoint = contour.toArray();
		int[] defectsArray = defects.toArray();
		
		for ( int i = 0; i< defectsArray.length ; i += 4) {
			int startIdx = defectsArray[i];
			int endIdx = defectsArray[ i + 1];
			int defectsIdx = defectsArray[i + 2];
			double depth = defectsArray[ i + 3] / 256.0;
			
			if(depth > 20) {
				fingerCount++;
			}
		}
		
		return Math.min(fingerCount + 1,  5);
	}
	
	private Map<String, String> createGestureResponse(String gesture , String message){
		Map<String, String> response = new HashMap<>();
		response.put("gesture", gesture);
		response.put("message", message);
		response.put("timestamp", String.valueOf(System.currentTimeMillis()));
		return response;
		
	}
	
	private String getGestureMessage(String gesture) {
		switch(gesture){
		case "higher": return "Palm detected - HIGHER";
		case "lower" : return "Fist detected - LOWER";
		case "uncertain" : return "Show clear palm or fist";
		case "none" : return "Non hand detected";
		default: return "Analysing ...";
			
		}
	}

}

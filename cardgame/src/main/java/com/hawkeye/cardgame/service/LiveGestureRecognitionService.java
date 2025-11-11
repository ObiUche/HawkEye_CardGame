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


public class LiveGestureRecognitionService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduler;
    private final Map<String, VideoCapture> userSessions;
    private final Map<String, Boolean> sessionStatus;
    private final Map<String, ScheduledFuture<?>> sessionTasks;
    private final Map<String, Mat> backgroundModels;
    private final Map<String, Long> lastGestureTime;

    // Gesture state machine
    private final Map<String, GestureState> gestureStates;

    public LiveGestureRecognitionService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.userSessions = new ConcurrentHashMap<>();
        this.sessionStatus = new ConcurrentHashMap<>();
        this.sessionTasks = new ConcurrentHashMap<>();
        this.backgroundModels = new ConcurrentHashMap<>();
        this.lastGestureTime = new ConcurrentHashMap<>();
        this.gestureStates = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void initialise() {
        nu.pattern.OpenCV.loadLocally();
        System.out.println("🤖 Robust Gesture Recognition Service Started");
        System.out.println("🔧 Using Hybrid Detection: Motion + Color + Position");
    }

    @PreDestroy
    public void cleanup() {
        sessionTasks.values().forEach(task -> task.cancel(true));
        scheduler.shutdown();
        userSessions.values().forEach(capture -> {
            if (capture != null && capture.isOpened()) {
                capture.release();
            }
        });
        userSessions.clear();
        sessionStatus.clear();
        sessionTasks.clear();
        backgroundModels.clear();
        lastGestureTime.clear();
        gestureStates.clear();
    }

    public void startLiveRecognition(String sessionId, int cameraIndex) {
        if (sessionStatus.getOrDefault(sessionId, false)) {
            stopLiveRecognition(sessionId);
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        System.out.println("🚀 Starting robust gesture recognition: " + sessionId);

        VideoCapture capture = initializeCamera(cameraIndex);
        if (capture == null || !capture.isOpened()) {
            messagingTemplate.convertAndSend("/topic/gesture/" + sessionId,
                    createGestureResponse("error", "Camera failed"));
            return;
        }

        // Initialize state
        gestureStates.put(sessionId, new GestureState());
        lastGestureTime.put(sessionId, System.currentTimeMillis());

        // Capture initial background
        Mat background = new Mat();
        if (capture.read(background) && !background.empty()) {
            backgroundModels.put(sessionId, background);
            System.out.println("✅ Background model captured");
        }

        userSessions.put(sessionId, capture);
        sessionStatus.put(sessionId, true);

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            processFrameRobust(sessionId);
        }, 0, 150, TimeUnit.MILLISECONDS);

        sessionTasks.put(sessionId, task);
        messagingTemplate.convertAndSend("/topic/gesture/" + sessionId,
                createGestureResponse("calibrating", "Calibrating... Show your hand"));
    }

    private VideoCapture initializeCamera(int cameraIndex) {
        // Simple camera initialization
        VideoCapture capture = new VideoCapture(cameraIndex);
        capture.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH, 640);
        capture.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, 480);
        capture.set(org.opencv.videoio.Videoio.CAP_PROP_FPS, 30);

        try { Thread.sleep(2000); } catch (InterruptedException e) { }

        if (capture.isOpened()) {
            System.out.println("✅ Camera initialized: " + cameraIndex);
            return capture;
        } else {
            if (capture != null) capture.release();
            return null;
        }
    }

    public void stopLiveRecognition(String sessionId) {
        System.out.println("🛑 Stopping recognition: " + sessionId);
        ScheduledFuture<?> task = sessionTasks.remove(sessionId);
        if (task != null) task.cancel(true);
        sessionStatus.put(sessionId, false);
        VideoCapture capture = userSessions.remove(sessionId);
        if (capture != null) capture.release();
        backgroundModels.remove(sessionId);
        lastGestureTime.remove(sessionId);
        gestureStates.remove(sessionId);

        messagingTemplate.convertAndSend("/topic/gesture/" + sessionId,
                createGestureResponse("stopped", "Recognition stopped"));
    }

    private void processFrameRobust(String sessionId) {
        if (!sessionStatus.getOrDefault(sessionId, false)) return;

        VideoCapture capture = userSessions.get(sessionId);
        if (capture == null || !capture.isOpened()) {
            stopLiveRecognition(sessionId);
            return;
        }

        Mat frame = new Mat();
        try {
            if (capture.read(frame) && !frame.empty()) {
                GestureResult result = hybridGestureDetection(frame, sessionId);
                sendGestureUpdate(sessionId, result);
            }
        } catch (Exception e) {
            System.err.println("Frame processing error: " + e.getMessage());
        }
    }

    private GestureResult hybridGestureDetection(Mat frame, String sessionId) {
        GestureResult result = new GestureResult();
        GestureState state = gestureStates.get(sessionId);

        try {
            Mat resized = new Mat();
            Imgproc.resize(frame, resized, new Size(320, 240));

            // METHOD 1: Motion Detection (Most reliable)
            MotionResult motion = detectMotion(resized, sessionId);
            if (motion.hasMotion) {
                result.confidence += 0.4;
                result.motionCenter = motion.center;
                result.motionArea = motion.area;
                System.out.println("📈 Motion detected: " + motion.area);
            }

            // METHOD 2: Color-Based Detection
            ColorResult color = detectColor(resized);
            if (color.hasColor) {
                result.confidence += 0.3;
                result.colorCenter = color.center;
                System.out.println("🎨 Color object detected");
            }

            // METHOD 3: Brightness-Based Detection (for white background)
            BrightnessResult brightness = detectBrightness(resized);
            if (brightness.hasBrightObject) {
                result.confidence += 0.3;
                result.brightnessCenter = brightness.center;
                System.out.println("💡 Brightness object detected");
            }

            // Combine all detections
            if (result.confidence > 0.5) {
                Point finalCenter = calculateFinalCenter(result);
                String gesture = classifyGestureByPosition(finalCenter, resized.size(), state);
                result.gesture = gesture;
                result.objectDetected = true;
                result.finalCenter = finalCenter;

                System.out.println("🎯 Final gesture: " + gesture + " (confidence: " + result.confidence + ")");
            }

        } catch (Exception e) {
            System.err.println("Hybrid detection error: " + e.getMessage());
        }

        return result;
    }

    private MotionResult detectMotion(Mat frame, String sessionId) {
        MotionResult result = new MotionResult();

        try {
            Mat currentGray = new Mat();
            Imgproc.cvtColor(frame, currentGray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(currentGray, currentGray, new Size(21, 21), 0);

            Mat background = backgroundModels.get(sessionId);
            if (background != null) {
                Mat bgGray = new Mat();
                Imgproc.cvtColor(background, bgGray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.GaussianBlur(bgGray, bgGray, new Size(21, 21), 0);

                // Compute absolute difference
                Mat diff = new Mat();
                Core.absdiff(bgGray, currentGray, diff);

                // Threshold
                Imgproc.threshold(diff, diff, 25, 255, Imgproc.THRESH_BINARY);

                // Clean up
                Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
                Imgproc.morphologyEx(diff, diff, Imgproc.MORPH_OPEN, kernel);
                Imgproc.morphologyEx(diff, diff, Imgproc.MORPH_CLOSE, kernel);

                // Find contours
                List<MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                Imgproc.findContours(diff, contours, hierarchy,
                        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                if (!contours.isEmpty()) {
                    // Find largest moving object
                    MatOfPoint largest = contours.stream()
                            .max(Comparator.comparingDouble(Imgproc::contourArea))
                            .orElse(null);

                    if (largest != null) {
                        double area = Imgproc.contourArea(largest);
                        if (area > 500) { // Filter small noise
                            Rect rect = Imgproc.boundingRect(largest);
                            result.hasMotion = true;
                            result.center = new Point(rect.x + rect.width/2, rect.y + rect.height/2);
                            result.area = area;
                        }
                    }
                }
            }

            // Update background occasionally
            if (Math.random() < 0.01) { // 1% chance to update background
                backgroundModels.put(sessionId, frame.clone());
            }

        } catch (Exception e) {
            System.err.println("Motion detection error: " + e.getMessage());
        }

        return result;
    }

    private ColorResult detectColor(Mat frame) {
        ColorResult result = new ColorResult();

        try {
            // Convert to multiple color spaces
            Mat hsv = new Mat();
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

            Mat lab = new Mat();
            Imgproc.cvtColor(frame, lab, Imgproc.COLOR_BGR2Lab);

            // Detect skin-like colors (broad range)
            Mat skinMask1 = new Mat();
            Core.inRange(hsv, new Scalar(0, 20, 70), new Scalar(25, 255, 255), skinMask1);

            Mat skinMask2 = new Mat();
            Core.inRange(lab, new Scalar(0, 130, 120), new Scalar(255, 170, 150), skinMask2);

            // Combine masks
            Mat combined = new Mat();
            Core.bitwise_or(skinMask1, skinMask2, combined);

            // Clean up
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            Imgproc.morphologyEx(combined, combined, Imgproc.MORPH_OPEN, kernel);

            // Find largest color blob
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(combined, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            if (!contours.isEmpty()) {
                MatOfPoint largest = contours.stream()
                        .max(Comparator.comparingDouble(Imgproc::contourArea))
                        .orElse(null);

                if (largest != null && Imgproc.contourArea(largest) > 1000) {
                    Rect rect = Imgproc.boundingRect(largest);
                    result.hasColor = true;
                    result.center = new Point(rect.x + rect.width/2, rect.y + rect.height/2);
                }
            }

        } catch (Exception e) {
            System.err.println("Color detection error: " + e.getMessage());
        }

        return result;
    }

    private BrightnessResult detectBrightness(Mat frame) {
        BrightnessResult result = new BrightnessResult();

        try {
            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

            // For white background, detect dark objects (hands)
            Mat binary = new Mat();
            Imgproc.threshold(gray, binary, 100, 255, Imgproc.THRESH_BINARY_INV);

            // Clean up
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel);

            // Find largest dark object
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(binary, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            if (!contours.isEmpty()) {
                MatOfPoint largest = contours.stream()
                        .max(Comparator.comparingDouble(Imgproc::contourArea))
                        .orElse(null);

                if (largest != null && Imgproc.contourArea(largest) > 1000) {
                    Rect rect = Imgproc.boundingRect(largest);
                    result.hasBrightObject = true;
                    result.center = new Point(rect.x + rect.width/2, rect.y + rect.height/2);
                }
            }

        } catch (Exception e) {
            System.err.println("Brightness detection error: " + e.getMessage());
        }

        return result;
    }

    private Point calculateFinalCenter(GestureResult result) {
        List<Point> centers = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        if (result.motionCenter != null) {
            centers.add(result.motionCenter);
            weights.add(0.4);
        }
        if (result.colorCenter != null) {
            centers.add(result.colorCenter);
            weights.add(0.3);
        }
        if (result.brightnessCenter != null) {
            centers.add(result.brightnessCenter);
            weights.add(0.3);
        }

        if (centers.isEmpty()) return new Point(0, 0);

        // Weighted average
        double totalX = 0, totalY = 0, totalWeight = 0;
        for (int i = 0; i < centers.size(); i++) {
            Point center = centers.get(i);
            double weight = weights.get(i);
            totalX += center.x * weight;
            totalY += center.y * weight;
            totalWeight += weight;
        }

        return new Point(totalX / totalWeight, totalY / totalWeight);
    }

    private String classifyGestureByPosition(Point center, Size frameSize, GestureState state) {
        double frameHeight = frameSize.height;
        double frameWidth = frameSize.width;

        // Divide frame into 3 horizontal zones
        double zoneHeight = frameHeight / 3;

        if (center.y < zoneHeight) {
            return "higher"; // Top zone
        } else if (center.y > 2 * zoneHeight) {
            return "lower";  // Bottom zone
        } else {
            return "neutral"; // Middle zone
        }
    }

    private void sendGestureUpdate(String sessionId, GestureResult result) {
        String message;
        String gesture = result.gesture;

        if (result.objectDetected) {
            if ("higher".equals(gesture)) {
                message = "👆 UP DETECTED - HIGHER";
            } else if ("lower".equals(gesture)) {
                message = "👇 DOWN DETECTED - LOWER";
            } else {
                message = "◎ OBJECT DETECTED - NEUTRAL";
                gesture = "none";
            }
        } else {
            message = "❌ NO OBJECT - Show your hand";
            gesture = "none";
        }

        // Only send if we have a meaningful gesture
        if (!"none".equals(gesture)) {
            messagingTemplate.convertAndSend("/topic/gesture/" + sessionId,
                    createGestureResponse(gesture, message));
            System.out.println("🎯 GESTURE: " + gesture);
        }
    }

    private Map<String, String> createGestureResponse(String gesture, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("gesture", gesture);
        response.put("message", message);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return response;
    }

    // Helper classes
    private static class GestureState {
        String lastGesture = "none";
        long lastGestureTime = 0;
        Point lastPosition = new Point(0, 0);
    }

    private static class GestureResult {
        boolean objectDetected = false;
        String gesture = "none";
        double confidence = 0.0;
        Point motionCenter = null;
        Point colorCenter = null;
        Point brightnessCenter = null;
        Point finalCenter = null;
        double motionArea = 0;
    }

    private static class MotionResult {
        boolean hasMotion = false;
        Point center = null;
        double area = 0;
    }

    private static class ColorResult {
        boolean hasColor = false;
        Point center = null;
    }

    private static class BrightnessResult {
        boolean hasBrightObject = false;
        Point center = null;
    }
}
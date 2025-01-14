package com.socialmedia.socialmedia.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmedia.socialmedia.dto.FriendDTO;
import com.socialmedia.socialmedia.dto.websocket.WebsocketMessage;
import com.socialmedia.socialmedia.dto.websocket.candidate.Candidate;
import com.socialmedia.socialmedia.dto.websocket.notification.Notification;
import com.socialmedia.socialmedia.dto.websocket.sdp.SDP;
import com.socialmedia.socialmedia.dto.websocket.text.SocketMessage;
import com.socialmedia.socialmedia.entities.User;
import com.socialmedia.socialmedia.services.impl.UserServiceImpl;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocketHandler extends TextWebSocketHandler {
    UserServiceImpl userService;
    Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public SocketHandler(UserServiceImpl userService) {
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String[] ticket = (String[]) session.getAttributes().get("user");
        System.out.println("Connection Established: " + ticket[0]);
        sessions.put(Long.valueOf(ticket[0]), session);
        long userId = Long.parseLong(ticket[0]);
        User user = userService.findUserById(userId);
        userService.login(user);
        notifyOnlineFriends(userId, "user-connected");
    }

    private void notifyOnlineFriends(long userId, String event) {
        Set<FriendDTO> friendList = userService.getUserFriends(userId);
        List<Long> pIds = friendList.stream().filter(FriendDTO::isOnline).map(FriendDTO::id).toList();
        System.out.println(pIds);
        for (Long id : pIds) {
            // TODO: Client should translate the person's id to a friend's id.
            Notification notification = new Notification(event, userId, id);
            if (sessions.containsKey(id))
                try {
                    System.out.println("Sending notification to " + id);
                    sessions.get(id).sendMessage(new TextMessage(new ObjectMapper().writeValueAsString(notification)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,@NonNull CloseStatus status) {
        String[] ticket = (String[]) session.getAttributes().get("user");
        long userId = Long.parseLong(ticket[0]);
        System.out.println("Connection Closed: " + userId);
        sessions.remove(userId);
        User user = userService.findUserById(userId);
        userService.logout(user);
        notifyOnlineFriends(userId, "user-disconnected");
    }

    @Override
    public void handleMessage(@NonNull WebSocketSession session,@NonNull WebSocketMessage<?> message) throws IOException {
        WebsocketMessage msg = parseMessage(message);
        WebSocketSession recipientSession = sessions.get(msg.getTo());
        if (recipientSession != null && recipientSession.isOpen())
            recipientSession.sendMessage(message);
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session,@NonNull TextMessage msg){
        System.out.println(msg);
    }

    private WebsocketMessage parseMessage(WebSocketMessage<?> payload) throws JsonProcessingException {
        TextMessage textMessage = (TextMessage) payload;
        // Get the JSON string from the SocketMessage
        String jsonPayload = textMessage.getPayload();
        // Create an ObjectMapper
        ObjectMapper mapper = new ObjectMapper();
        // Parse the JSON string into a WebsocketMessage object
        System.out.println(jsonPayload);
        JsonNode node = mapper.readTree(jsonPayload);
        String type = node.get("event").asText();
        return switch (type) {
            case "ice-candidate" -> mapper.readValue(jsonPayload, Candidate.class);
            case "text" -> mapper.readValue(jsonPayload, SocketMessage.class);
            default -> mapper.readValue(jsonPayload, SDP.class);
        };
    }
}

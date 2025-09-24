package io.vliet.plusmin.controller

import io.vliet.plusmin.service.PlusMinWebSocketHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notifications")
class WebSocketController {

    @Autowired
    private lateinit var webSocketHandler: PlusMinWebSocketHandler

    @PostMapping("/test")
    fun sendTestNotification(authentication: Authentication): ResponseEntity<String> {
        val gebruikerEmail = authentication.name
        webSocketHandler.sendToUser(gebruikerEmail, "notification", "Test notificatie!")
        return ResponseEntity.ok("Notificatie verzonden")
    }
}
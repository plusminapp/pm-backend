package io.vliet.plusmin.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class PlusMinWebSocketHandler : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(PlusMinWebSocketHandler::class.java)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val objectMapper = ObjectMapper()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val gebruikerEmail = session.principal?.name
        logger.info("WebSocket verbinding gestart voor gebruiker: $gebruikerEmail")

        if (gebruikerEmail != null) {
            sessions[gebruikerEmail] = session
            sendMessage(session, "connected", "WebSocket verbinding succesvol")
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val gebruikerEmail = session.principal?.name
        logger.info("WebSocket bericht ontvangen van $gebruikerEmail: ${message.payload}")

        // Echo bericht terug voor test
        sendMessage(session, "echo", message.payload)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val gebruikerEmail = session.principal?.name
        logger.info("WebSocket verbinding gesloten voor gebruiker: $gebruikerEmail")

        if (gebruikerEmail != null) {
            sessions.remove(gebruikerEmail)
        }
    }

    fun sendToUser(gebruikerEmail: String, type: String, data: Any) {
        sessions[gebruikerEmail]?.let { session ->
            if (session.isOpen) {
                sendMessage(session, type, data)
            }
        }
    }

    private fun sendMessage(session: WebSocketSession, type: String, data: Any) {
        try {
            val message = mapOf("type" to type, "data" to data)
            val json = objectMapper.writeValueAsString(message)
            session.sendMessage(TextMessage(json))
        } catch (e: Exception) {
            logger.error("Fout bij versturen WebSocket bericht", e)
        }
    }
}
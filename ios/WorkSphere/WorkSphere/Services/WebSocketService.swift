import Foundation
import Combine

/// Manages WebSocket connection to the Netty gateway for real-time messaging.
/// Connects to ws://{host}:8090/ws?userId={id} or ws://{host}:8090/ws?token={jwt}
/// Falls back gracefully — messaging works via REST polling when disconnected.
@Observable
final class WebSocketService {
    static let shared = WebSocketService()

    private(set) var isConnected = false
    private(set) var connectionCount = 0

    private var webSocket: URLSessionWebSocketTask?
    private var session: URLSession?
    private var subscribedConversations: Set<Int64> = []
    private var reconnectTask: Task<Void, Never>?

    // Callbacks for incoming messages by conversation ID
    private var messageHandlers: [Int64: (MessageDto) -> Void] = [:]
    private var typingHandlers: [Int64: (Int64) -> Void] = [:]
    // Global handler for any new message (used by conversation list for unread updates)
    var onAnyMessage: ((Int64, MessageDto?) -> Void)?

    private let decoder = FlexibleDecoder()

    func connect() {
        guard webSocket == nil else { return }

        let api = APIClient.shared
        let baseURL = api.baseURL.replacingOccurrences(of: "/api", with: "")
            .replacingOccurrences(of: "http://", with: "ws://")
            .replacingOccurrences(of: "https://", with: "wss://")
        // Use port 8090 for the gateway
        let host = baseURL.components(separatedBy: ":").prefix(2).joined(separator: ":")

        var urlString = "\(host):8090/ws"
        if let token = api.token {
            urlString += "?token=\(token)"
        } else if let debugId = api.debugUserId {
            urlString += "?userId=\(debugId)"
        } else {
            return // No auth
        }

        guard let url = URL(string: urlString) else { return }

        session = URLSession(configuration: .default)
        webSocket = session?.webSocketTask(with: url)
        webSocket?.resume()

        receiveLoop()
    }

    func disconnect() {
        reconnectTask?.cancel()
        reconnectTask = nil
        webSocket?.cancel(with: .normalClosure, reason: nil)
        webSocket = nil
        session = nil
        isConnected = false
        subscribedConversations.removeAll()
    }

    func subscribe(conversationId: Int64, onMessage: @escaping (MessageDto) -> Void, onTyping: ((Int64) -> Void)? = nil) {
        messageHandlers[conversationId] = onMessage
        if let onTyping { typingHandlers[conversationId] = onTyping }

        if subscribedConversations.insert(conversationId).inserted {
            sendJSON(["type": "SUBSCRIBE", "conversationId": conversationId])
        }
    }

    func unsubscribe(conversationId: Int64) {
        messageHandlers.removeValue(forKey: conversationId)
        typingHandlers.removeValue(forKey: conversationId)
        subscribedConversations.remove(conversationId)
    }

    func sendMessage(conversationId: Int64, content: String) {
        sendJSON(["type": "SEND", "conversationId": conversationId, "content": content])
    }

    func sendTyping(conversationId: Int64) {
        sendJSON(["type": "TYPING", "conversationId": conversationId])
    }

    // MARK: - Private

    private func sendJSON(_ dict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let str = String(data: data, encoding: .utf8) else { return }
        webSocket?.send(.string(str)) { [weak self] error in
            if let error {
                print("WS send error: \(error.localizedDescription)")
                self?.handleDisconnect()
            }
        }
    }

    private func receiveLoop() {
        webSocket?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleMessage(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleMessage(text)
                    }
                @unknown default:
                    break
                }
                self.receiveLoop() // Continue listening

            case .failure(let error):
                print("WS receive error: \(error.localizedDescription)")
                self.handleDisconnect()
            }
        }
    }

    private func handleMessage(_ text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return }

        switch type {
        case "CONNECTED":
            DispatchQueue.main.async {
                self.isConnected = true
                if let count = json["connections"] as? Int {
                    self.connectionCount = count
                }
            }
            // Re-subscribe to all conversations
            for convId in subscribedConversations {
                sendJSON(["type": "SUBSCRIBE", "conversationId": convId])
            }

        case "MESSAGE":
            // Parse conversationId using NSNumber for large int safety
            guard let convIdRaw = json["conversationId"],
                  let convId = (convIdRaw as? NSNumber)?.int64Value else { return }

            // Try to decode MessageDto; fire handlers regardless
            var msg: MessageDto? = nil
            if let msgData = json["data"],
               let msgJSON = try? JSONSerialization.data(withJSONObject: msgData) {
                msg = try? decoder.decode(MessageDto.self, from: msgJSON)
            }

            DispatchQueue.main.async {
                if let msg {
                    self.messageHandlers[convId]?(msg)
                }
                // Always fire onAnyMessage so views can reload from REST
                self.onAnyMessage?(convId, msg)
            }

        case "TYPING":
            guard let convIdRaw = json["conversationId"],
                  let convId = (convIdRaw as? NSNumber)?.int64Value,
                  let userIdRaw = json["userId"],
                  let userId = (userIdRaw as? NSNumber)?.int64Value else { return }
            DispatchQueue.main.async {
                self.typingHandlers[convId]?(userId)
            }

        case "ERROR":
            print("WS error: \(json["message"] ?? "unknown")")

        default:
            break
        }
    }

    private func handleDisconnect() {
        DispatchQueue.main.async {
            self.isConnected = false
            self.webSocket = nil
            self.session = nil
        }
        // Auto-reconnect after delay
        reconnectTask?.cancel()
        reconnectTask = Task {
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled else { return }
            await MainActor.run { self.connect() }
        }
    }
}

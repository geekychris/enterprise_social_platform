import Foundation

@Observable
final class APIClient {
    static let shared = APIClient()

    var baseURL = "http://localhost:8080/api"
    var token: String?
    var debugUserId: Int64?
    var tenantId: String?

    private let session: URLSession
    private let decoder: JSONDecoder

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        self.session = URLSession(configuration: config)
        self.decoder = FlexibleDecoder()
    }

    // MARK: - Request Building

    private func request(_ path: String, method: String = "GET", body: (any Encodable)? = nil, contentType: String = "application/json") throws -> URLRequest {
        guard let url = URL(string: baseURL + path) else {
            throw APIError.invalidURL
        }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue(contentType, forHTTPHeaderField: "Content-Type")

        if let debugId = debugUserId {
            req.setValue(String(debugId), forHTTPHeaderField: "X-Debug-User-Id")
        } else if let token {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        if let tenantId {
            req.setValue(tenantId, forHTTPHeaderField: "X-Tenant-Id")
        }

        if let body {
            req.httpBody = try JSONEncoder().encode(body)
        }
        return req
    }

    // MARK: - Generic Requests

    func get<T: Decodable>(_ path: String) async throws -> T {
        let req = try request(path)
        let (data, response) = try await session.data(for: req)
        try checkResponse(response, data: data)
        return try decoder.decode(T.self, from: data)
    }

    func post<T: Decodable>(_ path: String, body: (any Encodable)? = nil) async throws -> T {
        let req = try request(path, method: "POST", body: body)
        let (data, response) = try await session.data(for: req)
        try checkResponse(response, data: data)
        return try decoder.decode(T.self, from: data)
    }

    func postVoid(_ path: String, body: (any Encodable)? = nil) async throws {
        let req = try request(path, method: "POST", body: body)
        let (data, response) = try await session.data(for: req)
        try checkResponse(response, data: data)
    }

    func put<T: Decodable>(_ path: String, body: (any Encodable)? = nil) async throws -> T {
        let req = try request(path, method: "PUT", body: body)
        let (data, response) = try await session.data(for: req)
        try checkResponse(response, data: data)
        return try decoder.decode(T.self, from: data)
    }

    func putVoid(_ path: String, body: (any Encodable)? = nil) async throws {
        let req = try request(path, method: "PUT", body: body)
        let (data, response) = try await session.data(for: req)
        try checkResponse(response, data: data)
    }

    func delete(_ path: String) async throws {
        let req = try request(path, method: "DELETE")
        let (data, response) = try await session.data(for: req)
        try checkResponse(response, data: data)
    }

    // MARK: - File Upload

    func upload(data fileData: Data, filename: String, mimeType: String) async throws -> UploadResponse {
        guard let url = URL(string: baseURL + "/attachments/upload") else { throw APIError.invalidURL }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"

        let boundary = UUID().uuidString
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let debugId = debugUserId {
            req.setValue(String(debugId), forHTTPHeaderField: "X-Debug-User-Id")
        } else if let token {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let tenantId {
            req.setValue(tenantId, forHTTPHeaderField: "X-Tenant-Id")
        }

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body

        let (responseData, response) = try await session.data(for: req)
        try checkResponse(response, data: responseData)
        return try decoder.decode(UploadResponse.self, from: responseData)
    }

    // MARK: - SSE Streaming (for AI)

    func streamAI(context: String, contextId: Int64?, question: String, onToken: @escaping (String) -> Void) async throws {
        guard let url = URL(string: baseURL + "/ai/ask") else { throw APIError.invalidURL }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let debugId = debugUserId {
            req.setValue(String(debugId), forHTTPHeaderField: "X-Debug-User-Id")
        } else if let token {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let tenantId {
            req.setValue(tenantId, forHTTPHeaderField: "X-Tenant-Id")
        }
        req.timeoutInterval = 120

        struct AIRequest: Codable {
            let context: String
            let contextId: Int64?
            let question: String
        }
        req.httpBody = try JSONEncoder().encode(AIRequest(context: context, contextId: contextId, question: question))

        let (bytes, response) = try await session.bytes(for: req)
        try checkResponse(response)

        var currentEvent = ""
        for try await line in bytes.lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("event:") {
                currentEvent = String(trimmed.dropFirst(6)).trimmingCharacters(in: .whitespaces)
            } else if trimmed.hasPrefix("data:") {
                let data = String(trimmed.dropFirst(5))
                if currentEvent == "token" {
                    let decoded = data.replacingOccurrences(of: "⏎", with: "\n")
                    await MainActor.run { onToken(decoded) }
                } else if currentEvent == "done" {
                    break
                } else if currentEvent == "error" {
                    throw APIError.serverError(data)
                }
                currentEvent = ""
            }
        }
    }

    // MARK: - Helpers

    private func checkResponse(_ response: URLResponse, data: Data? = nil) throws {
        guard let http = response as? HTTPURLResponse else { return }
        if http.statusCode == 401 {
            throw APIError.unauthorized
        }
        guard (200...299).contains(http.statusCode) else {
            var detail = ""
            if let data, let body = String(data: data, encoding: .utf8), !body.isEmpty {
                detail = " - \(body.prefix(500))"
            }
            throw APIError.httpError(http.statusCode, detail)
        }
    }
}

enum APIError: LocalizedError {
    case invalidURL
    case unauthorized
    case httpError(Int, String)
    case serverError(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid URL"
        case .unauthorized: return "Session expired. Please log in again."
        case .httpError(let code, let detail): return "Server error (\(code))\(detail)"
        case .serverError(let msg): return msg
        }
    }
}

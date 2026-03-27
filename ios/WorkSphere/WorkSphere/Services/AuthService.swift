import Foundation
import SwiftUI

@Observable
final class AuthService {
    static let shared = AuthService()

    var isAuthenticated = false
    var userId: Int64?
    var username: String?
    var isAdmin = false
    var debugMode = false

    private let api = APIClient.shared

    init() {
        // Restore from UserDefaults
        if let token = UserDefaults.standard.string(forKey: "auth_token") {
            api.token = token
            let stored = UserDefaults.standard.object(forKey: "auth_userId")
            if let num = stored as? Int64 { userId = num }
            else if let num = stored as? Int { userId = Int64(num) }
            else if let str = stored as? String { userId = Int64(str) }
            username = UserDefaults.standard.string(forKey: "auth_username")
            isAdmin = UserDefaults.standard.bool(forKey: "auth_isAdmin")
            if userId != nil { isAuthenticated = true }
        }
        let debugStored = UserDefaults.standard.object(forKey: "debug_userId")
        var debugId: Int64?
        if let num = debugStored as? Int64 { debugId = num }
        else if let num = debugStored as? Int, num > 0 { debugId = Int64(num) }
        else if let str = debugStored as? String { debugId = Int64(str) }
        if let debugId, debugId > 0 {
            api.debugUserId = debugId
            userId = debugId
            debugMode = true
            isAuthenticated = true
        }
    }

    func login(username: String, password: String) async throws {
        let response: LoginResponse = try await api.post("/auth/login", body: LoginRequest(username: username, password: password))
        let uid = response.userId
        await MainActor.run {
            api.token = response.token
            api.debugUserId = nil
            self.userId = uid
            self.username = response.username
            self.isAdmin = response.admin
            self.isAuthenticated = true
            self.debugMode = false
            persist(token: response.token, userId: uid, username: response.username, isAdmin: response.admin)
        }
    }

    func register(username: String, displayName: String, email: String, password: String, bio: String?) async throws {
        let response: LoginResponse = try await api.post("/auth/register", body: RegisterRequest(
            username: username, displayName: displayName, email: email, password: password, bio: bio
        ))
        let uid = response.userId
        await MainActor.run {
            api.token = response.token
            api.debugUserId = nil
            self.userId = uid
            self.username = response.username
            self.isAdmin = response.admin
            self.isAuthenticated = true
            self.debugMode = false
            persist(token: response.token, userId: uid, username: response.username, isAdmin: response.admin)
        }
    }

    func loginDebug(userId: Int64) {
        api.token = nil
        api.debugUserId = userId
        self.userId = userId
        self.debugMode = true
        self.isAuthenticated = true
        UserDefaults.standard.set(userId, forKey: "debug_userId")
    }

    func logout() {
        api.token = nil
        api.debugUserId = nil
        userId = nil
        username = nil
        isAdmin = false
        isAuthenticated = false
        debugMode = false
        UserDefaults.standard.removeObject(forKey: "auth_token")
        UserDefaults.standard.removeObject(forKey: "auth_userId")
        UserDefaults.standard.removeObject(forKey: "auth_username")
        UserDefaults.standard.removeObject(forKey: "auth_isAdmin")
        UserDefaults.standard.removeObject(forKey: "debug_userId")
    }

    func configureServer(url: String) {
        api.baseURL = url + "/api"
    }

    private func persist(token: String, userId: Int64, username: String, isAdmin: Bool) {
        UserDefaults.standard.set(token, forKey: "auth_token")
        UserDefaults.standard.set(String(userId), forKey: "auth_userId")
        UserDefaults.standard.set(username, forKey: "auth_username")
        UserDefaults.standard.set(isAdmin, forKey: "auth_isAdmin")
    }
}

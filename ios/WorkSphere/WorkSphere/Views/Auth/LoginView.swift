import SwiftUI

struct LoginView: View {
    @Environment(AuthService.self) private var auth
    @State private var mode: AuthMode = .login
    @State private var username = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var email = ""
    @State private var bio = ""
    @State private var debugUserId = ""
    @State private var serverURL = "http://localhost:8080"
    @State private var error: String?
    @State private var loading = false
    @State private var showServerConfig = false
    @State private var showDebugConsole = false
    @State private var debugLog: [String] = []
    @State private var pingStatus: String?

    static let buildNumber = 2

    enum AuthMode { case login, register, debug }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                // Logo
                VStack(spacing: 4) {
                    Image(systemName: "globe.americas.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.blue)
                    Text("WorkSphere")
                        .font(.title.bold())
                    Text("Enterprise Social Platform")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("Build #\(Self.buildNumber)")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
                .padding(.top, 16)

                // Ping status
                if let pingStatus {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(pingStatus == "Connected" ? .green : .red)
                            .frame(width: 8, height: 8)
                        Text(pingStatus)
                            .font(.caption2)
                            .foregroundStyle(pingStatus == "Connected" ? .green : .red)
                    }
                }

                // Mode picker
                Picker("Mode", selection: $mode) {
                    Text("Login").tag(AuthMode.login)
                    Text("Register").tag(AuthMode.register)
                    Text("Debug").tag(AuthMode.debug)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)

                // Forms
                VStack(spacing: 12) {
                    switch mode {
                    case .login:
                        TextField("Username", text: $username)
                            .textContentType(.username)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        SecureField("Password", text: $password)
                            .textContentType(.password)

                    case .register:
                        TextField("Username", text: $username)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        TextField("Display Name", text: $displayName)
                        TextField("Email", text: $email)
                            .textContentType(.emailAddress)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        SecureField("Password", text: $password)
                        TextField("Bio (optional)", text: $bio, axis: .vertical)
                            .lineLimit(2)

                    case .debug:
                        TextField("User ID (large number from DB)", text: $debugUserId)
                            .keyboardType(.numberPad)
                        Text("Use a valid user ID from the database")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .textFieldStyle(.roundedBorder)
                .font(.subheadline)
                .padding(.horizontal)

                if let error {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .padding(.horizontal)
                        .multilineTextAlignment(.center)
                }

                // Submit
                Button(action: submit) {
                    if loading {
                        ProgressView().tint(.white)
                    } else {
                        Text(mode == .login ? "Sign In" : mode == .register ? "Create Account" : "Debug Login")
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.regular)
                .disabled(loading)
                .padding(.horizontal)

                // Ping + Server config
                HStack(spacing: 16) {
                    Button("Ping Server") { ping() }
                        .font(.caption)
                        .foregroundStyle(.blue)

                    Button(showServerConfig ? "Hide Server" : "Server Settings") {
                        showServerConfig.toggle()
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)

                    Button(showDebugConsole ? "Hide Log" : "Debug Log") {
                        showDebugConsole.toggle()
                    }
                    .font(.caption)
                    .foregroundStyle(.orange)
                }

                if showServerConfig {
                    HStack {
                        TextField("Server URL", text: $serverURL)
                            .textFieldStyle(.roundedBorder)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .font(.caption)
                        Button("Set") {
                            auth.configureServer(url: serverURL)
                            log("Server set to: \(serverURL)")
                        }
                        .buttonStyle(.bordered)
                        .controlSize(.small)
                    }
                    .padding(.horizontal)
                }

                if showDebugConsole {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text("Debug Console")
                                .font(.caption.bold())
                            Spacer()
                            Button("Clear") { debugLog.removeAll() }
                                .font(.caption2)
                        }
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 1) {
                                ForEach(Array(debugLog.enumerated()), id: \.offset) { _, entry in
                                    Text(entry)
                                        .font(.system(.caption2, design: .monospaced))
                                        .foregroundStyle(.green)
                                        .textSelection(.enabled)
                                }
                            }
                        }
                        .frame(maxHeight: 200)
                    }
                    .padding(8)
                    .background(.black)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .padding(.horizontal)
                }

                Spacer()
            }
        }
        .onAppear {
            serverURL = String(APIClient.shared.baseURL.dropLast(4))
            ping()
        }
    }

    private func log(_ msg: String) {
        let ts = Date().formatted(.dateTime.hour().minute().second())
        debugLog.append("[\(ts)] \(msg)")
    }

    private func ping() {
        pingStatus = nil
        log("Pinging \(APIClient.shared.baseURL)...")
        Task {
            do {
                let url = URL(string: APIClient.shared.baseURL.replacingOccurrences(of: "/api", with: "/actuator/health"))!
                let (data, response) = try await URLSession.shared.data(from: url)
                let http = response as? HTTPURLResponse
                let body = String(data: data, encoding: .utf8) ?? "(empty)"
                log("Ping response: \(http?.statusCode ?? 0) - \(body)")
                await MainActor.run {
                    pingStatus = http?.statusCode == 200 ? "Connected" : "Error \(http?.statusCode ?? 0)"
                }
            } catch {
                log("Ping failed: \(error.localizedDescription)")
                await MainActor.run {
                    pingStatus = "Unreachable: \(error.localizedDescription)"
                }
            }
        }
    }

    private func submit() {
        error = nil
        loading = true
        log("Attempting \(mode) ...")
        Task {
            do {
                switch mode {
                case .login:
                    log("POST /auth/login username=\(username)")
                    try await auth.login(username: username, password: password)
                    log("Login success, userId=\(auth.userId ?? 0)")
                case .register:
                    log("POST /auth/register username=\(username) email=\(email)")
                    try await auth.register(username: username, displayName: displayName, email: email, password: password, bio: bio.isEmpty ? nil : bio)
                    log("Register success, userId=\(auth.userId ?? 0)")
                case .debug:
                    guard let id = Int64(debugUserId) else {
                        error = "Enter a valid user ID"
                        log("Invalid debug user ID: \(debugUserId)")
                        loading = false
                        return
                    }
                    auth.loginDebug(userId: id)
                    log("Debug login userId=\(id)")
                }
            } catch let apiError as APIError {
                self.error = apiError.localizedDescription
                log("ERROR (API): \(apiError.localizedDescription)")
            } catch let decodingError as DecodingError {
                self.error = "Decoding error: \(decodingError.localizedDescription)"
                log("ERROR (Decode): \(decodingError)")
            } catch {
                self.error = "\(error)"
                log("ERROR: \(error)")
            }
            loading = false
        }
    }
}

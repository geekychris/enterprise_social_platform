import SwiftUI
import Speech
import AVFoundation

/// A microphone button that does speech-to-text and appends to a bound text string.
struct SpeechButton: View {
    @Binding var text: String
    @State private var isRecording = false
    @State private var speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    @State private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    @State private var recognitionTask: SFSpeechRecognitionTask?
    @State private var audioEngine = AVAudioEngine()
    @State private var authorized = false
    @State private var showPermissionAlert = false

    var body: some View {
        Button {
            if isRecording {
                stopRecording()
            } else {
                startRecording()
            }
        } label: {
            Image(systemName: isRecording ? "mic.fill" : "mic")
                .font(.body)
                .foregroundStyle(isRecording ? .red : .secondary)
                .symbolEffect(.pulse, isActive: isRecording)
        }
        .buttonStyle(.plain)
        .padding(6)
        .background(isRecording ? Color.red.opacity(0.1) : Color.clear)
        .clipShape(Circle())
        .alert("Speech Recognition", isPresented: $showPermissionAlert) {
            Button("OK") {}
        } message: {
            Text("Please enable Speech Recognition and Microphone access in Settings.")
        }
    }

    private func startRecording() {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else {
                    showPermissionAlert = true
                    return
                }
                AVAudioApplication.requestRecordPermission { granted in
                    DispatchQueue.main.async {
                        guard granted else {
                            showPermissionAlert = true
                            return
                        }
                        beginSession()
                    }
                }
            }
        }
    }

    private func beginSession() {
        guard let speechRecognizer, speechRecognizer.isAvailable else { return }

        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest else { return }
        recognitionRequest.shouldReportPartialResults = true

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)

        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            recognitionRequest.append(buffer)
        }

        recognitionTask = speechRecognizer.recognitionTask(with: recognitionRequest) { result, error in
            if let result {
                let transcript = result.bestTranscription.formattedString
                DispatchQueue.main.async {
                    // Replace from last speech session marker
                    if let range = text.range(of: "🎤", options: .backwards) {
                        text = String(text[text.startIndex..<range.lowerBound]) + transcript
                    }
                }
            }
            if error != nil || (result?.isFinal == true) {
                DispatchQueue.main.async {
                    // Remove marker and finalize
                    text = text.replacingOccurrences(of: "🎤", with: "")
                    stopRecording()
                }
            }
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            // Add a marker so we know where speech text starts
            if !text.isEmpty && !text.hasSuffix(" ") {
                text += " "
            }
            text += "🎤"
            isRecording = true
        } catch {
            stopRecording()
        }
    }

    private func stopRecording() {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask?.cancel()
        recognitionTask = nil
        isRecording = false
        // Clean up marker
        text = text.replacingOccurrences(of: "🎤", with: "")
    }
}

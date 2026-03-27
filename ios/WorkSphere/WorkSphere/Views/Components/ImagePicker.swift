import SwiftUI
import PhotosUI
import UIKit

/// A picker that supports both camera and photo library.
struct ImagePickerButton: View {
    let label: String
    let currentURL: String?
    var size: CGFloat = 64
    var onPicked: (Data, String, String) -> Void // (data, filename, mimeType)

    @State private var showOptions = false
    @State private var showCamera = false
    @State private var showPhotoPicker = false
    @State private var selectedItem: PhotosPickerItem?

    var body: some View {
        Button { showOptions = true } label: {
            HStack(spacing: 12) {
                if let url = currentURL, let imgURL = URL(string: url) {
                    AsyncImage(url: imgURL) { img in
                        img.resizable().scaledToFill()
                    } placeholder: {
                        Circle().fill(.gray.opacity(0.2))
                    }
                    .frame(width: size, height: size)
                    .clipShape(RoundedRectangle(cornerRadius: size > 50 ? 12 : size / 2))
                } else {
                    RoundedRectangle(cornerRadius: size > 50 ? 12 : size / 2)
                        .fill(.gray.opacity(0.15))
                        .frame(width: size, height: size)
                        .overlay {
                            Image(systemName: "camera")
                                .foregroundStyle(.secondary)
                        }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(label)
                        .font(.caption.bold())
                        .foregroundStyle(.primary)
                    Text("Tap to change")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .confirmationDialog("Choose Image", isPresented: $showOptions) {
            Button("Take Photo") { showCamera = true }
            Button("Choose from Library") { showPhotoPicker = true }
            Button("Cancel", role: .cancel) {}
        }
        .photosPicker(isPresented: $showPhotoPicker, selection: $selectedItem, matching: .images)
        .onChange(of: selectedItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    onPicked(data, "photo.jpg", "image/jpeg")
                }
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraView { data in
                onPicked(data, "camera.jpg", "image/jpeg")
            }
            .ignoresSafeArea()
        }
    }
}

/// UIImagePickerController wrapper for camera
struct CameraView: UIViewControllerRepresentable {
    var onCapture: (Data) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture, dismiss: dismiss)
    }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (Data) -> Void
        let dismiss: DismissAction

        init(onCapture: @escaping (Data) -> Void, dismiss: DismissAction) {
            self.onCapture = onCapture
            self.dismiss = dismiss
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage, let data = image.jpegData(compressionQuality: 0.8) {
                onCapture(data)
            }
            dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            dismiss()
        }
    }
}

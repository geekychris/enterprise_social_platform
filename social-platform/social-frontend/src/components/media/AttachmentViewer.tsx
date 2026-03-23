import { useState } from 'react';
import type { AttachmentDto } from '../../api/types';

const VIDEO_EXTENSIONS = ['.mov', '.mp4', '.webm', '.avi', '.mkv'];

function isVideoFile(attachment: AttachmentDto): boolean {
  if (attachment.mediaType?.startsWith('video/')) return true;
  const url = attachment.fileUrl?.toLowerCase() ?? '';
  return VIDEO_EXTENSIONS.some((ext) => url.endsWith(ext));
}

function getVideoMimeType(attachment: AttachmentDto): string {
  if (attachment.mediaType?.startsWith('video/')) return attachment.mediaType;
  const url = attachment.fileUrl?.toLowerCase() ?? '';
  if (url.endsWith('.mp4')) return 'video/mp4';
  if (url.endsWith('.webm')) return 'video/webm';
  if (url.endsWith('.mov')) return 'video/quicktime';
  if (url.endsWith('.avi')) return 'video/x-msvideo';
  if (url.endsWith('.mkv')) return 'video/x-matroska';
  return 'video/mp4';
}

function getImageGridClass(count: number): string {
  if (count === 1) return 'grid-cols-1';
  if (count === 2) return 'grid-cols-2';
  return 'grid-cols-2';
}

function getImageItemClass(count: number, index: number): string {
  if (count === 1) return 'max-h-96';
  if (count === 3 && index === 0) return 'row-span-2 h-full';
  if (count >= 4 && index < 2) return 'h-48';
  return 'h-48';
}

interface Props {
  attachments: AttachmentDto[];
}

export default function AttachmentViewer({ attachments }: Props) {
  const [fullscreen, setFullscreen] = useState<string | null>(null);

  if (!attachments.length) return null;

  const images = attachments.filter(
    (a) => a.mediaType?.startsWith('image/') && !isVideoFile(a),
  );
  const videos = attachments.filter((a) => isVideoFile(a));
  const others = attachments.filter(
    (a) => !a.mediaType?.startsWith('image/') && !isVideoFile(a),
  );

  return (
    <>
      {/* Image gallery */}
      {images.length > 0 && (
        <div
          className={`grid gap-1 rounded-lg overflow-hidden ${getImageGridClass(images.length)}`}
        >
          {images.map((img, i) => (
            <img
              key={img.id}
              src={img.fileUrl}
              alt=""
              className={`w-full object-cover cursor-pointer hover:opacity-95 transition-opacity ${getImageItemClass(images.length, i)}`}
              onClick={() => setFullscreen(img.fileUrl)}
            />
          ))}
        </div>
      )}

      {/* Videos */}
      {videos.map((vid) => (
        <video
          key={vid.id}
          controls
          className="w-full rounded-lg max-h-96"
          preload="metadata"
        >
          <source src={vid.fileUrl} type={getVideoMimeType(vid)} />
          Your browser does not support the video tag.
        </video>
      ))}

      {/* Other files */}
      {others.map((file) => (
        <a
          key={file.id}
          href={file.fileUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
        >
          <svg className="w-5 h-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          <span className="text-sm text-primary-500 font-medium">
            Download ({(file.fileSize / 1024).toFixed(0)} KB)
          </span>
        </a>
      ))}

      {/* Fullscreen overlay */}
      {fullscreen && (
        <div
          className="fixed inset-0 bg-black/90 z-50 flex items-center justify-center cursor-pointer"
          onClick={() => setFullscreen(null)}
        >
          <img
            src={fullscreen}
            alt=""
            className="max-w-[90vw] max-h-[90vh] object-contain"
          />
          <button className="absolute top-4 right-4 text-white text-2xl font-bold hover:opacity-80">
            ✕
          </button>
        </div>
      )}
    </>
  );
}
